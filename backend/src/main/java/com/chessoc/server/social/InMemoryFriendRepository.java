package com.chessoc.server.social;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory friendships for dev and tests. */
public final class InMemoryFriendRepository implements FriendRepository {

    private final Map<String, Set<String>> edges = new ConcurrentHashMap<>();

    @Override
    public void add(String accountId, String friendId) {
        if (accountId.equals(friendId)) {
            return;
        }
        edges.computeIfAbsent(accountId, k -> ConcurrentHashMap.newKeySet()).add(friendId);
        edges.computeIfAbsent(friendId, k -> ConcurrentHashMap.newKeySet()).add(accountId);
    }

    @Override
    public List<String> friends(String accountId) {
        return List.copyOf(edges.getOrDefault(accountId, Set.of()));
    }

    @Override
    public boolean areFriends(String accountId, String friendId) {
        return edges.getOrDefault(accountId, Set.of()).contains(friendId);
    }
}
