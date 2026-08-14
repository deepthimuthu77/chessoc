package com.chessoc.server.capability;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** The action kinds a capability can authorise (frontend action catalog v1). */
public enum CapabilityKind {
    MOVE("move"),
    RESIGN("resign"),
    OFFER_DRAW("offer_draw"),
    RESPOND_DRAW("respond_draw"),
    REMATCH("rematch");

    private final String wire;

    CapabilityKind(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static CapabilityKind fromWire(String s) {
        for (CapabilityKind k : values()) {
            if (k.wire.equals(s)) {
                return k;
            }
        }
        throw new IllegalArgumentException("unknown capability kind: " + s);
    }
}
