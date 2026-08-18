package com.chessoc.server.social;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory presence for a single node: who is online and who is in a game. Redis-backed cross-node
 * presence (§10) is the production upgrade.
 */
public final class Presence {

    private final Set<String> online = ConcurrentHashMap.newKeySet();
    private final Map<String, String> inGame = new ConcurrentHashMap<>();

    public void online(String accountId) {
        online.add(accountId);
    }

    public void offline(String accountId) {
        online.remove(accountId);
        inGame.remove(accountId);
    }

    public void enterGame(String accountId, String gameId) {
        inGame.put(accountId, gameId);
    }

    public boolean isOnline(String accountId) {
        return online.contains(accountId);
    }

    public String gameOf(String accountId) {
        return inGame.get(accountId);
    }
}
