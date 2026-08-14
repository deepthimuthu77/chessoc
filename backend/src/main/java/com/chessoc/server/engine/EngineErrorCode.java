package com.chessoc.server.engine;

/**
 * Engine failure taxonomy (§5). The three cases drive different Adapter handling:
 * reject-and-log, terminate-pipeline, restart-worker.
 */
public enum EngineErrorCode {
    /** Well-formed request, move not legal in this position. */
    ILLEGAL_MOVE("illegal_move"),
    /** Unparseable position / move / hand / variant. */
    MALFORMED_REQUEST("malformed_request"),
    /** Engine fault — the Adapter synthesises this from timeout or non-zero exit. */
    INTERNAL_ERROR("internal_error");

    private final String wire;

    EngineErrorCode(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static EngineErrorCode fromWire(String s) {
        for (EngineErrorCode c : values()) {
            if (c.wire.equals(s)) {
                return c;
            }
        }
        throw new IllegalArgumentException("unknown engine error: " + s);
    }
}
