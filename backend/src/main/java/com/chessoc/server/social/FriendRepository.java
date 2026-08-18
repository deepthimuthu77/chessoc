package com.chessoc.server.social;

import java.util.List;

/** Mutual friendships. {@code add} links both directions (no request/accept flow in v1). */
public interface FriendRepository {

    void add(String accountId, String friendId);

    List<String> friends(String accountId);

    boolean areFriends(String accountId, String friendId);
}
