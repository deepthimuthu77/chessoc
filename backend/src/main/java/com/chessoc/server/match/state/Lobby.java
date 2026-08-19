package com.chessoc.server.match.state;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Variant;
import com.chessoc.server.games.GameRecord;
import com.chessoc.server.games.GamesRepository;
import com.chessoc.server.match.SpeedBucket;
import com.chessoc.server.match.TimeControl;
import com.chessoc.server.protocol.GameResult;
import com.chessoc.server.rating.RatingService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Creates games and seats players by room name (casual) or by an explicit rated invite. On completion
 * it records the game and, when rated, applies the Glicko-2 update to both players.
 */
public final class Lobby {

    private static final String STANDARD_START =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String CRAZYHOUSE_START =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR[] w KQkq - 0 1";

    public record Seat(String gameId, String color) {
    }

    private final MatchService matches;
    private final RatingService ratings;
    private final GamesRepository games;
    private final TimeControl casualTimeControl;
    private final LongSupplier clock;
    private final Map<String, String> gameByRoom = new ConcurrentHashMap<>();

    public Lobby(MatchService matches, RatingService ratings, GamesRepository games,
                 TimeControl casualTimeControl, LongSupplier clock) {
        this.matches = matches;
        this.ratings = ratings;
        this.games = games;
        this.casualTimeControl = casualTimeControl;
        this.clock = clock;
    }

    /** Join a casual room by name: first joiner is White, next distinct joiner claims Black. */
    public synchronized Seat join(String room, String playerId) {
        String gameId = gameByRoom.get(room);
        if (gameId == null) {
            gameId = spawn(UUID.randomUUID().toString(), playerId, Variant.STANDARD, casualTimeControl, false);
            gameByRoom.put(room, gameId);
            return new Seat(gameId, "white");
        }
        return new Seat(gameId, matches.get(gameId).orElseThrow().seatFor(playerId));
    }

    /** Create a game with a known creator as White and an open Black seat; returns its id (also its room). */
    public synchronized String create(String creatorId, Variant variant, TimeControl timeControl, boolean rated) {
        String gameId = spawn(UUID.randomUUID().toString(), creatorId, variant, timeControl, rated);
        gameByRoom.put(gameId, gameId);
        return gameId;
    }

    private String spawn(String gameId, String whiteId, Variant variant, TimeControl tc, boolean rated) {
        Fen start = Fen.parse(variant == Variant.STANDARD ? STANDARD_START : CRAZYHOUSE_START);
        MatchGame game = matches.create(new GameSpec(gameId, variant, start, whiteId, null, tc, rated));
        game.onComplete(result -> onComplete(game, result));
        game.onRematch(this::createRematch);
        return gameId;
    }

    /** Create the rematch game with both seats pre-assigned; returns its id (also its room). */
    public synchronized String createRematch(String whiteId, String blackId, Variant variant,
                                             TimeControl tc, boolean rated) {
        String gameId = UUID.randomUUID().toString();
        Fen start = Fen.parse(variant == Variant.STANDARD ? STANDARD_START : CRAZYHOUSE_START);
        MatchGame game = matches.create(new GameSpec(gameId, variant, start, whiteId, blackId, tc, rated));
        game.onComplete(result -> onComplete(game, result));
        game.onRematch(this::createRematch);
        gameByRoom.put(gameId, gameId);
        return gameId;
    }

    private void onComplete(MatchGame game, GameResult result) {
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
}
