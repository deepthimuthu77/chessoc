package com.chessoc.server.social;

/** Request → accept/decline friendship flow over a {@link FriendRepository}. */
public final class FriendService {

    public enum Outcome {
        /** Request recorded, awaiting the other side. */
        REQUESTED,
        /** Became friends now (the other side had already requested). */
        FRIENDS,
        ALREADY_FRIENDS,
        /** No-op (e.g. self). */
        NONE
    }

    private final FriendRepository repo;

    public FriendService(FriendRepository repo) {
        this.repo = repo;
    }

    /** Send a request from → to; auto-accepts if {@code to} already requested {@code from}. */
    public Outcome request(String fromId, String toId) {
        if (fromId.equals(toId)) {
            return Outcome.NONE;
        }
        if (repo.areFriends(fromId, toId)) {
            return Outcome.ALREADY_FRIENDS;
        }
        if (repo.hasRequest(toId, fromId)) {
            befriend(fromId, toId);
            return Outcome.FRIENDS;
        }
        repo.addRequest(fromId, toId);
        return Outcome.REQUESTED;
    }

    /** Accept {@code fromId}'s pending request to {@code accountId}. Returns false if there was none. */
    public boolean accept(String accountId, String fromId) {
        if (!repo.hasRequest(fromId, accountId)) {
            return false;
        }
        befriend(accountId, fromId);
        return true;
    }

    public void decline(String accountId, String fromId) {
        repo.removeRequest(fromId, accountId);
    }

    private void befriend(String a, String b) {
        repo.add(a, b);
        repo.removeRequest(a, b);
        repo.removeRequest(b, a);
    }
}
