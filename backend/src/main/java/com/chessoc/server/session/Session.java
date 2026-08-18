package com.chessoc.server.session;

/** An authenticated live connection: an account-scoped session bound to one connection. */
public record Session(String sessionId, String playerId, String connectionId) {
}
