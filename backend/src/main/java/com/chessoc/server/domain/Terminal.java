package com.chessoc.server.domain;

/** Engine-reported terminal status of a position. The server records it, never derives it. */
public enum Terminal {
    NONE,
    CHECKMATE,
    STALEMATE,
    DRAW_FIFTY_MOVE,
    DRAW_THREEFOLD,
    DRAW_INSUFFICIENT_MATERIAL;

    public boolean isTerminal() {
        return this != NONE;
    }

    public boolean isDraw() {
        return switch (this) {
            case STALEMATE, DRAW_FIFTY_MOVE, DRAW_THREEFOLD, DRAW_INSUFFICIENT_MATERIAL -> true;
            case NONE, CHECKMATE -> false;
        };
    }

    public String toWire() {
        return name().toLowerCase();
    }

    public static Terminal fromWire(String s) {
        return switch (s) {
            case "none" -> NONE;
            case "checkmate" -> CHECKMATE;
            case "stalemate" -> STALEMATE;
            case "draw_fifty_move" -> DRAW_FIFTY_MOVE;
            case "draw_threefold" -> DRAW_THREEFOLD;
            case "draw_insufficient_material" -> DRAW_INSUFFICIENT_MATERIAL;
            default -> throw new IllegalArgumentException("unknown terminal: " + s);
        };
    }
}
