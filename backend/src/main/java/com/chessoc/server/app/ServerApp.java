package com.chessoc.server.app;

import com.chessoc.server.account.AccountRepository;
import com.chessoc.server.account.AccountService;
import com.chessoc.server.account.InMemoryAccountRepository;
import com.chessoc.server.account.PostgresAccountRepository;
import com.chessoc.server.db.Db;
import com.chessoc.server.engine.EngineAdapter;
import com.chessoc.server.games.GamesRepository;
import com.chessoc.server.games.InMemoryGamesRepository;
import com.chessoc.server.games.PostgresGamesRepository;
import com.chessoc.server.match.TimeControl;
import com.chessoc.server.match.state.InMemoryMatchRepository;
import com.chessoc.server.match.state.InMemoryLiveRoomRepository;
import com.chessoc.server.match.state.LiveRoomRepository;
import com.chessoc.server.match.state.Lobby;
import com.chessoc.server.match.state.MatchRepository;
import com.chessoc.server.match.state.MatchService;
import com.chessoc.server.match.state.PostgresMatchRepository;
import com.chessoc.server.match.state.PostgresLiveRoomRepository;
import com.chessoc.server.match.state.Scheduler;
import com.chessoc.server.match.state.SystemScheduler;
import com.chessoc.server.matchmaking.MatchmakingService;
import com.chessoc.server.matchmaking.SeekCoordinator;
import com.chessoc.server.protocol.ProtocolCodec;
import com.chessoc.server.rating.InMemoryRatingRepository;
import com.chessoc.server.rating.LeaderboardService;
import com.chessoc.server.rating.PostgresRatingRepository;
import com.chessoc.server.rating.RatingRepository;
import com.chessoc.server.rating.RatingService;
import com.chessoc.server.session.SessionRegistry;
import com.chessoc.server.session.TicketService;
import com.chessoc.server.session.TokenService;
import com.chessoc.server.social.FriendRepository;
import com.chessoc.server.social.InMemoryFriendRepository;
import com.chessoc.server.social.PostgresFriendRepository;
import com.chessoc.server.social.Presence;
import com.chessoc.server.transport.LiveGateway;
import com.chessoc.server.transport.http.ApiServices;
import com.chessoc.server.transport.http.HttpAuthApi;
import com.chessoc.server.transport.ws.WebSocketLiveServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Assembles the module graph and owns the process lifecycle. */
public final class ServerApp {

    private final EngineAdapter engine;
    private final ScheduledExecutorService schedulerExecutor;
    private final ThreadPoolExecutor aiExecutor;
    private final HttpAuthApi http;
    private final WebSocketLiveServer ws;
    private final Db db;

    public ServerApp(ServerConfig config, EngineAdapter engine) throws IOException {
        this.engine = engine;
        this.schedulerExecutor = Executors.newScheduledThreadPool(2);
        this.aiExecutor = SystemScheduler.newAiExecutor(config.searchWorkers());
        Scheduler scheduler = new SystemScheduler(schedulerExecutor);
        LongSupplier clock = System::currentTimeMillis;

        boolean useDb = !config.dbUrl().isBlank();
        this.db = useDb ? new Db(config.dbUrl(), config.dbUser(), config.dbPassword()) : null;

        MatchRepository matchRepo = useDb
                ? new PostgresMatchRepository(db)
                : new InMemoryMatchRepository();
        LiveRoomRepository liveRooms = useDb
                ? new PostgresLiveRoomRepository(db)
                : new InMemoryLiveRoomRepository();
        AccountRepository accounts = useDb ? new PostgresAccountRepository(db) : new InMemoryAccountRepository();
        RatingRepository ratings = useDb ? new PostgresRatingRepository(db) : new InMemoryRatingRepository();
        FriendRepository friends = useDb ? new PostgresFriendRepository(db) : new InMemoryFriendRepository();
        GamesRepository games = useDb ? new PostgresGamesRepository(db) : new InMemoryGamesRepository();

        MatchService matches = new MatchService(engine, matchRepo, scheduler, clock,
                () -> UUID.randomUUID().toString(), aiExecutor);
        RatingService ratingService = new RatingService(ratings, clock);
        LeaderboardService leaderboard = new LeaderboardService(ratings, accounts);
        AccountService accountService = new AccountService(accounts);
        Presence presence = new Presence(clock);
        Lobby lobby = new Lobby(matches, ratingService, games, engine,
                TimeControl.fischer(600_000, 5_000), clock, liveRooms);
        SeekCoordinator seeks = new SeekCoordinator(new MatchmakingService(), lobby::createPaired);

        TokenService tokens = new TokenService(config.tokenSecret());
        TicketService tickets = new TicketService(clock, config.ticketTtlMs());
        SessionRegistry sessions = new SessionRegistry();
        ProtocolCodec codec = new ProtocolCodec();
        LiveGateway gateway = new LiveGateway(tickets, sessions, matches, codec);

        ApiServices api = new ApiServices(tokens, tickets, sessions, lobby, accountService, accounts,
                leaderboard, friends, games, presence, matches, matchRepo, seeks, engine,
                config.tokenTtlMs(), clock);
        this.http = new HttpAuthApi(config.httpPort(), api);
        this.ws = new WebSocketLiveServer(new InetSocketAddress(config.wsPort()), gateway);
    }

    public int httpPort() {
        return http.port();
    }

    public int wsPort() {
        return ws.getPort();
    }

    public void start() {
        http.start();
        ws.start();
    }

    public void stop() throws InterruptedException {
        ws.stop();
        http.stop();
        aiExecutor.shutdownNow();
        schedulerExecutor.shutdownNow();
        engine.shutdown();
        aiExecutor.awaitTermination(2, TimeUnit.SECONDS);
        if (db != null) {
            db.close();
        }
    }
}
