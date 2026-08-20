package com.chessoc.server.social;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory presence for a single node: who is online (by last-seen TTL) and who is in a game. Clients
 * refresh their last-seen with a heartbeat; a stale entry counts as offline. Redis-backed cross-node
 * presence (§10) is the production upgrade.
 */
public final class Presence {

    /** A player is online if seen within this window. Clients heartbeat well inside it. */
    private static final long TTL_MS = 90_000;

    private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();
    private final Map<String, String> inGame = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public Presence() {
        this(System::currentTimeMillis);
    }

    public Presence(LongSupplier clock) {
        this.clock = clock;
    }

    /** Mark the player seen now (login, navigation, or heartbeat). */
    public void online(String accountId) {
        lastSeen.put(accountId, clock.getAsLong());
    }

    public void offline(String accountId) {
        lastSeen.remove(accountId);
        inGame.remove(accountId);
    }

    public void enterGame(String accountId, String gameId) {
        inGame.put(accountId, gameId);
    }

    public boolean isOnline(String accountId) {
        Long seen = lastSeen.get(accountId);
        return seen != null && clock.getAsLong() - seen < TTL_MS;
    }

    public String gameOf(String accountId) {
        return inGame.get(accountId);
    }
}
