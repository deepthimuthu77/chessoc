package com.chessoc.server.social;

import java.util.List;

/** Mutual friendships with a directed request/accept flow. {@code add} links both directions. */
public interface FriendRepository {

    void add(String accountId, String friendId);

    List<String> friends(String accountId);

    boolean areFriends(String accountId, String friendId);

    /** Record a pending friend request from → to (idempotent). */
    void addRequest(String fromId, String toId);

    void removeRequest(String fromId, String toId);

    boolean hasRequest(String fromId, String toId);

    /** Accounts who have a pending request out to {@code accountId}. */
    List<String> incomingRequests(String accountId);

    /** Accounts {@code accountId} has a pending request out to. */
    List<String> outgoingRequests(String accountId);
}
