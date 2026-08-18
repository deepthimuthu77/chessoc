package com.chessoc.server.match.state;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Variant;
import com.chessoc.server.match.TimeControl;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps a room name to a game: the first joiner opens it as White, the next distinct joiner claims
 * Black, anyone after that spectates. Minimal seating so two clients can share a room by name.
 */
public final class Lobby {

    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    public record Seat(String gameId, String color) {
    }

    private final MatchService matches;
    private final TimeControl timeControl;
    private final Map<String, String> gameByRoom = new ConcurrentHashMap<>();

    public Lobby(MatchService matches, TimeControl timeControl) {
        this.matches = matches;
        this.timeControl = timeControl;
    }

    public synchronized Seat join(String room, String playerId) {
        String gameId = gameByRoom.get(room);
        if (gameId == null) {
            gameId = UUID.randomUUID().toString();
            matches.create(new GameSpec(gameId, Variant.STANDARD, Fen.parse(START),
                    playerId, null, timeControl, false));
            gameByRoom.put(room, gameId);
            return new Seat(gameId, "white");
        }
        MatchGame game = matches.get(gameId).orElseThrow();
        return new Seat(gameId, game.seatFor(playerId));
    }
}
