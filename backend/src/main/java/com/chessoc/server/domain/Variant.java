package com.chessoc.server.domain;

/** Supported chess variants. */
public enum Variant {
    STANDARD,
    CRAZYHOUSE,
    BUGHOUSE;

    /** Drop variants carry a hand reserve in the FEN {@code [...]} field. */
    public boolean isDrop() {
        return this == CRAZYHOUSE || this == BUGHOUSE;
    }
}
