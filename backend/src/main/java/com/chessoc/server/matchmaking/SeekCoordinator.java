package com.chessoc.server.matchmaking;

import com.chessoc.server.domain.Variant;
import com.chessoc.server.match.TimeControl;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns queue pairings into real games. HTTP has no push channel, so a paired player who was already
 * waiting discovers the game by polling {@link #poll}; the second seeker learns it from {@link #seek}.
 */
public final class SeekCoordinator {

    /** Creates a game with both seats assigned; returns its id. Satisfied by {@code Lobby::createPaired}. */
    public interface GameFactory {
        String create(String whiteId, String blackId, Variant variant, TimeControl timeControl, boolean rated);
    }

    private final MatchmakingService matchmaking;
    private final GameFactory games;
    private final Map<String, String> resolved = new ConcurrentHashMap<>();

    public SeekCoordinator(MatchmakingService matchmaking, GameFactory games) {
        this.matchmaking = matchmaking;
        this.games = games;
    }

    /** Enqueue a seek; returns the game id if it paired immediately, else null (now waiting). */
    public String seek(String playerId, Variant variant, TimeControl timeControl) {
        return matchmaking.enqueue(new Seek(playerId, variant, timeControl))
                .map(p -> {
                    String gameId = games.create(p.whiteId(), p.blackId(), p.variant(), p.timeControl(), p.rated());
                    resolved.put(p.whiteId(), gameId);
                    resolved.put(p.blackId(), gameId);
                    return gameId;
                })
                .orElse(null);
    }

    /** A waiting player's poll: the game id once paired (consumed once), else null. */
    public String poll(String playerId) {
        return resolved.remove(playerId);
    }

    public void cancel(String playerId) {
        matchmaking.cancel(playerId);
        resolved.remove(playerId);
    }
}
