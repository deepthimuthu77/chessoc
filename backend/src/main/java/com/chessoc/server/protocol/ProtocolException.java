package com.chessoc.server.protocol;

/** A wire message the server cannot process; drop it and surface a non-fatal error. */
public class ProtocolException extends RuntimeException {

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
