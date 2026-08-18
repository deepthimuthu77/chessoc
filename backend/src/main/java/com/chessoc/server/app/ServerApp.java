package com.chessoc.server.app;

import com.chessoc.server.engine.EngineAdapter;
import com.chessoc.server.match.state.InMemoryMatchRepository;
import com.chessoc.server.match.state.MatchRepository;
import com.chessoc.server.match.state.MatchService;
import com.chessoc.server.match.state.Scheduler;
import com.chessoc.server.match.state.SystemScheduler;
import com.chessoc.server.matchmaking.MatchmakingService;
import com.chessoc.server.protocol.ProtocolCodec;
import com.chessoc.server.session.SessionRegistry;
import com.chessoc.server.session.TicketService;
import com.chessoc.server.session.TokenService;
import com.chessoc.server.transport.LiveGateway;
import com.chessoc.server.transport.http.HttpAuthApi;
import com.chessoc.server.transport.ws.WebSocketLiveServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.LongSupplier;

/** Assembles the module graph and owns the process lifecycle. */
public final class ServerApp {

    private final EngineAdapter engine;
    private final ScheduledExecutorService schedulerExecutor;
    private final HttpAuthApi http;
    private final WebSocketLiveServer ws;
    private final MatchmakingService matchmaking = new MatchmakingService();

    public ServerApp(ServerConfig config, EngineAdapter engine) throws IOException {
        this.engine = engine;
        this.schedulerExecutor = Executors.newScheduledThreadPool(2);
        Scheduler scheduler = new SystemScheduler(schedulerExecutor);
        LongSupplier clock = System::currentTimeMillis;

        MatchRepository repository = new InMemoryMatchRepository();
        MatchService matches = new MatchService(engine, repository, scheduler, clock);
        TokenService tokens = new TokenService(config.tokenSecret());
        TicketService tickets = new TicketService(clock, config.ticketTtlMs());
        SessionRegistry sessions = new SessionRegistry();
        ProtocolCodec codec = new ProtocolCodec();
        LiveGateway gateway = new LiveGateway(tickets, sessions, matches, codec);

        this.http = new HttpAuthApi(config.httpPort(), tokens, tickets, sessions, matches,
                config.tokenTtlMs(), clock);
        this.ws = new WebSocketLiveServer(new InetSocketAddress(config.wsPort()), gateway);
    }

    public MatchmakingService matchmaking() {
        return matchmaking;
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
        schedulerExecutor.shutdownNow();
        engine.shutdown();
    }
}
