package com.chessoc.server.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** How the server resolved a submitted intent. */
public enum Disposition {
    ACCEPTED,
    REJECTED,
    STALE;

    @JsonValue
    public String wire() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static Disposition fromWire(String s) {
        return valueOf(s.toUpperCase());
    }
}
