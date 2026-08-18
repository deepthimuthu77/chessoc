package com.chessoc.server.session;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Maps a live session to its account. The fingerprint is never identity; the account is. */
public final class SessionRegistry {

    private final Map<String, String> playerBySession = new ConcurrentHashMap<>();

    public void register(String sessionId, String playerId) {
        playerBySession.put(sessionId, playerId);
    }

    public Optional<String> playerId(String sessionId) {
        return Optional.ofNullable(playerBySession.get(sessionId));
    }

    public void remove(String sessionId) {
        playerBySession.remove(sessionId);
    }
}
