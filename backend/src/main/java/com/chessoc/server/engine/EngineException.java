package com.chessoc.server.engine;

/** A typed engine failure. The code determines whether the Adapter logs, aborts, or restarts. */
public class EngineException extends RuntimeException {

    private final EngineErrorCode code;

    public EngineException(EngineErrorCode code, String message) {
        super(code.wire() + ": " + message);
        this.code = code;
    }

    public EngineErrorCode code() {
        return code;
    }
}
