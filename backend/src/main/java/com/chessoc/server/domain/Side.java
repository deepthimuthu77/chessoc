package com.chessoc.server.domain;

/** The two players' colours. */
public enum Side {
    WHITE,
    BLACK;

    public Side opponent() {
        return this == WHITE ? BLACK : WHITE;
    }

    /** FEN side-to-move token ("w"/"b"). */
    public static Side fromFen(String token) {
        return switch (token) {
            case "w" -> WHITE;
            case "b" -> BLACK;
            default -> throw new IllegalArgumentException("bad side token: " + token);
        };
    }

    public String toFen() {
        return this == WHITE ? "w" : "b";
    }
}
