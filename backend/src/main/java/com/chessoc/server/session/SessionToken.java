package com.chessoc.server.session;

/** The verified contents of a session token. */
public record SessionToken(String playerId, String sessionId, long expiresAtEpochMs) {
}
