package com.chessoc.server.match.state;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Variant;
import com.chessoc.server.engine.SearchBudget;
import com.chessoc.server.engine.SearchClient;
import com.chessoc.server.games.GameRecord;
import com.chessoc.server.games.GamesRepository;
import com.chessoc.server.match.DelayType;
import com.chessoc.server.match.SpeedBucket;
import com.chessoc.server.match.TimeControl;
import com.chessoc.server.protocol.GameResult;
import com.chessoc.server.rating.RatingService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Creates, restores, and seats players in standard, crazyhouse, and bughouse rooms. */
public final class Lobby {
    private static final String STANDARD_START =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String CRAZYHOUSE_START =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR[] w KQkq - 0 1";
    public static final String ENGINE_ID = "engine";

    public record Seat(String roomId, String gameId, String partnerGameId, String board,
                       String color, String partnerColor, String team, String seat,
                       int seatsFilled, int humansJoined, int humanTarget, String variant,
                       List<String> humanSeats, List<String> availableSeats) {
    }

    private final MatchService matches;
    private final RatingService ratings;
    private final GamesRepository games;
    private final SearchClient engine;
    private final TimeControl casualTimeControl;
    private final LongSupplier clock;
    private final LiveRoomRepository liveRooms;
    private final Map<String, String> gameByRoom = new ConcurrentHashMap<>();
    private final Map<String, BughouseMatch> bughouseByRoom = new ConcurrentHashMap<>();

    public Lobby(MatchService matches, RatingService ratings, GamesRepository games, SearchClient engine,
                 TimeControl casualTimeControl, LongSupplier clock) {
        this(matches, ratings, games, engine, casualTimeControl, clock,
                new InMemoryLiveRoomRepository());
    }

    public Lobby(MatchService matches, RatingService ratings, GamesRepository games, SearchClient engine,
                 TimeControl casualTimeControl, LongSupplier clock, LiveRoomRepository liveRooms) {
        this.matches = matches;
        this.ratings = ratings;
        this.games = games;
        this.engine = engine;
        this.casualTimeControl = casualTimeControl;
        this.clock = clock;
        this.liveRooms = liveRooms;
        restoreLiveRooms();
    }

    public synchronized String createVsEngine(String humanId, Variant variant, TimeControl tc, Side humanColor) {
        if (variant == Variant.BUGHOUSE) {
            return createBughouse(humanId, tc, humanColor, 1);
        }
        String gameId = UUID.randomUUID().toString();
        Fen start = Fen.parse(variant == Variant.STANDARD ? STANDARD_START : CRAZYHOUSE_START);
        String whiteId = humanColor == Side.WHITE ? humanId : ENGINE_ID;
        String blackId = humanColor == Side.WHITE ? ENGINE_ID : humanId;
        Side aiSide = humanColor == Side.WHITE ? Side.BLACK : Side.WHITE;
        MatchGame game = matches.create(new GameSpec(gameId, variant, start, whiteId, blackId, tc, false));
        gameByRoom.put(gameId, gameId);
        wireStandard(gameId, game);
        game.setAi(aiSide, aiMover());
        persistStandard(gameId, game);
        return gameId;
    }

    public synchronized Seat join(String room, String playerId) {
        return join(room, playerId, null);
    }

    public synchronized Seat join(String room, String playerId, String requestedSeat) {
        BughouseMatch bughouse = bughouseByRoom.get(room);
        if (bughouse != null) {
            BughouseMatch.Seat seat = bughouse.join(playerId, requestedSeat);
            persistBughouse(bughouse);
            return new Seat(seat.roomId(), seat.gameId(), seat.partnerGameId(), seat.board(),
                    seat.color(), seat.partnerColor(), seat.team(), seat.seat(), seat.seatsFilled(),
                    seat.humansJoined(), seat.humanTarget(), "bughouse", seat.humanSeats(),
                    seat.availableSeats());
        }
        String gameId = gameByRoom.get(room);
        if (gameId == null) {
            throw new IllegalArgumentException("room not found");
        }
        MatchGame game = matches.get(gameId).orElseThrow();
        Seat seat = standardSeat(room, gameId, game.seatFor(playerId));
        persistStandard(room, game);
        return seat;
    }

    public synchronized String create(String creatorId, Variant variant, TimeControl timeControl, boolean rated) {
        if (variant == Variant.BUGHOUSE) {
            return createBughouse(creatorId, timeControl, Side.WHITE, 4);
        }
        String gameId = spawn(UUID.randomUUID().toString(), creatorId, variant, timeControl, rated);
        gameByRoom.put(gameId, gameId);
        MatchGame game = matches.get(gameId).orElseThrow();
        wireStandard(gameId, game);
        persistStandard(gameId, game);
        return gameId;
    }

    private String spawn(String gameId, String whiteId, Variant variant, TimeControl tc, boolean rated) {
        Fen start = Fen.parse(variant == Variant.STANDARD ? STANDARD_START : CRAZYHOUSE_START);
        return matches.create(new GameSpec(gameId, variant, start, whiteId, null, tc, rated)).gameId();
    }

    public synchronized String createPaired(String whiteId, String blackId, Variant variant,
                                            TimeControl tc, boolean rated) {
        if (variant == Variant.BUGHOUSE) {
            throw new IllegalArgumentException("bughouse matchmaking requires four players");
        }
        String gameId = UUID.randomUUID().toString();
        Fen start = Fen.parse(variant == Variant.STANDARD ? STANDARD_START : CRAZYHOUSE_START);
        MatchGame game = matches.create(new GameSpec(gameId, variant, start, whiteId, blackId, tc, rated));
        gameByRoom.put(gameId, gameId);
        wireStandard(gameId, game);
        wireEngineSeat(game);
        persistStandard(gameId, game);
        return gameId;
    }

    public synchronized String createBughouse(String creatorId, TimeControl timeControl,
                                              Side creatorColor, int humanPlayers) {
        return createBughouse(creatorId, timeControl, creatorColor, null, humanPlayers);
    }

    public synchronized String createBughouse(String creatorId, TimeControl timeControl,
                                              Side creatorColor, Set<String> humanSeats) {
        return createBughouse(creatorId, timeControl, creatorColor, humanSeats, humanSeats.size());
    }

    private String createBughouse(String creatorId, TimeControl timeControl, Side creatorColor,
                                  Set<String> humanSeats, int humanPlayers) {
        String roomId = UUID.randomUUID().toString();
        BughouseMatch match = humanSeats == null
                ? new BughouseMatch(roomId, matches, Fen.parse(CRAZYHOUSE_START), timeControl,
                        creatorId, creatorColor, humanPlayers,
                        humanPlayers < 4 ? aiMover() : null, this::onComplete)
                : new BughouseMatch(roomId, matches, Fen.parse(CRAZYHOUSE_START), timeControl,
                        creatorId, creatorColor, humanSeats,
                        humanSeats.size() < 4 ? aiMover() : null, this::onComplete);
        indexBughouse(match);
        match.onStateChange(() -> persistBughouse(match));
        persistBughouse(match);
        return roomId;
    }

    private void wireStandard(String roomId, MatchGame game) {
        game.onComplete(result -> {
            liveRooms.delete(roomId);
            onComplete(game, result);
        });
        game.onRematch(this::createPaired);
        game.onStateChange(() -> persistStandard(roomId, game));
    }

    private void wireEngineSeat(MatchGame game) {
        if (ENGINE_ID.equals(game.whiteId())) {
            game.setAi(Side.WHITE, aiMover());
        }
        if (ENGINE_ID.equals(game.blackId())) {
            game.setAi(Side.BLACK, aiMover());
        }
    }

    private void restoreLiveRooms() {
        for (LiveRoomState state : liveRooms.loadAll()) {
            TimeControl tc = timeControl(state);
            if ("bughouse".equalsIgnoreCase(state.variant())) {
                BughouseMatch match = BughouseMatch.restore(state, matches, tc, aiMover(), (game, result) -> {
                    liveRooms.delete(state.roomId());
                    onComplete(game, result);
                });
                indexBughouse(match);
                match.onStateChange(() -> persistBughouse(match));
                match.resumeRecovered();
                persistBughouse(match);
                continue;
            }
            if (state.boards().size() != 1) {
                throw new IllegalStateException("room " + state.roomId() + " has an invalid board count");
            }
            LiveRoomState.BoardState board = state.boards().get(0);
            Variant variant = Variant.valueOf(state.variant().toUpperCase());
            MatchGame game = matches.restore(new GameSpec(board.gameId(), variant,
                    Fen.parse(board.initialFen()), board.whiteId(), board.blackId(), tc, state.rated()),
                    board, state.savedAtEpochMs());
            gameByRoom.put(state.roomId(), game.gameId());
            wireStandard(state.roomId(), game);
            wireEngineSeat(game);
            game.resumeRecovered();
            persistStandard(state.roomId(), game);
        }
    }

    private void persistStandard(String roomId, MatchGame game) {
        if (game.isOver()) {
            liveRooms.delete(roomId);
            return;
        }
        TimeControl tc = game.timeControl();
        liveRooms.save(new LiveRoomState(roomId, game.variant().name().toLowerCase(),
                tc.baseMs(), tc.incrementMs(), tc.delayType().name(), tc.delayMs(), game.rated(),
                2, List.of(), clock.getAsLong(), List.of(game.recoveryState())));
    }

    private void persistBughouse(BughouseMatch match) {
        if (match.boardA().isOver() || match.boardB().isOver()) {
            liveRooms.delete(match.roomId());
        } else {
            liveRooms.save(match.recoveryState(clock.getAsLong()));
        }
    }

    private void indexBughouse(BughouseMatch match) {
        bughouseByRoom.put(match.roomId(), match);
        bughouseByRoom.put(match.boardA().gameId(), match);
        bughouseByRoom.put(match.boardB().gameId(), match);
    }

    private MatchGame.AiMover aiMover() {
        return (variant, fen) -> engine.bestMove(variant, fen, SearchBudget.movetime(600)).bestMove();
    }

    private static TimeControl timeControl(LiveRoomState state) {
        return new TimeControl(state.baseMs(), state.incrementMs(),
                DelayType.valueOf(state.delayType()), state.delayMs());
    }

    private void onComplete(MatchGame game, GameResult result) {
        if (result.type() == GameResult.ResultType.ABORTED) {
            return;
        }
        String white = game.whiteId();
        String black = game.blackId();
        String variant = game.variant().name().toLowerCase();
        String speed = SpeedBucket.of(game.timeControl()).name().toLowerCase();
        String winner = "";
        if (result.type() == GameResult.ResultType.WIN && result.winner().isPresent()) {
            winner = result.winner().get() == Side.WHITE ? white : (black == null ? "" : black);
        }
        games.record(new GameRecord(game.gameId(), white, black, variant, speed, game.rated(),
                result.type().name(), winner, result.reason(), clock.getAsLong()));
        if (game.rated() && black != null) {
            ratings.applyResult(white, black, variant, speed, result);
        }
    }

    public boolean isPrimaryBughouseBoard(String gameId) {
        BughouseMatch match = bughouseByRoom.get(gameId);
        return match != null && match.boardA().gameId().equals(gameId);
    }

    public boolean isBughouseGame(String gameId) {
        return bughouseByRoom.containsKey(gameId);
    }

    public int bughouseSeatsFilled(String gameId) {
        BughouseMatch match = bughouseByRoom.get(gameId);
        return match == null ? 0 : match.seatsFilled();
    }

    public int bughouseHumansJoined(String gameId) {
        BughouseMatch match = bughouseByRoom.get(gameId);
        return match == null ? 0 : match.humansJoined();
    }

    public int bughouseHumanTarget(String gameId) {
        BughouseMatch match = bughouseByRoom.get(gameId);
        return match == null ? 0 : match.humanTarget();
    }

    public boolean isBughouseParticipant(String gameId, String playerId) {
        BughouseMatch match = bughouseByRoom.get(gameId);
        return match != null && match.isParticipant(playerId);
    }

    public String bughouseRoomId(String gameId) {
        BughouseMatch match = bughouseByRoom.get(gameId);
        return match == null ? gameId : match.roomId();
    }

    private Seat standardSeat(String room, String gameId, String color) {
        MatchGame game = matches.get(gameId).orElseThrow();
        int occupied = game.blackId() == null ? 1 : 2;
        return new Seat(room, gameId, "", "A", color, "", "", color,
                occupied, occupied, 2, game.variant().name().toLowerCase(), List.of(), List.of());
    }
}
