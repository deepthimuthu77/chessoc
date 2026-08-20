package com.chessoc.server.social;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory friendships for dev and tests. */
public final class InMemoryFriendRepository implements FriendRepository {

    private final Map<String, Set<String>> edges = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> outgoing = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> incoming = new ConcurrentHashMap<>();

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

    @Override
    public void addRequest(String fromId, String toId) {
        if (fromId.equals(toId)) {
            return;
        }
        outgoing.computeIfAbsent(fromId, k -> ConcurrentHashMap.newKeySet()).add(toId);
        incoming.computeIfAbsent(toId, k -> ConcurrentHashMap.newKeySet()).add(fromId);
    }

    @Override
    public void removeRequest(String fromId, String toId) {
        Set<String> out = outgoing.get(fromId);
        if (out != null) {
            out.remove(toId);
        }
        Set<String> in = incoming.get(toId);
        if (in != null) {
            in.remove(fromId);
        }
    }

    @Override
    public boolean hasRequest(String fromId, String toId) {
        return outgoing.getOrDefault(fromId, Set.of()).contains(toId);
    }

    @Override
    public List<String> incomingRequests(String accountId) {
        return List.copyOf(incoming.getOrDefault(accountId, Set.of()));
    }

    @Override
    public List<String> outgoingRequests(String accountId) {
        return List.copyOf(outgoing.getOrDefault(accountId, Set.of()));
    }
}
