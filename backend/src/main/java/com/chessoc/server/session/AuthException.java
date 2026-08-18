package com.chessoc.server.session;

/** Authentication or authorisation failure; unauthenticated traffic is rejected. */
public class AuthException extends RuntimeException {

    public AuthException(String message) {
        super(message);
    }
}
