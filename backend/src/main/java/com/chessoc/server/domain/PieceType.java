package com.chessoc.server.domain;

/** Piece kinds that can enter a hand as a drop. A king is never captured, so never dropped. */
public enum PieceType {
    PAWN('p'),
    KNIGHT('n'),
    BISHOP('b'),
    ROOK('r'),
    QUEEN('q');

    private final char symbol;

    PieceType(char symbol) {
        this.symbol = symbol;
    }

    public char symbol() {
        return symbol;
    }

    public static PieceType fromSymbol(char c) {
        return switch (Character.toLowerCase(c)) {
            case 'p' -> PAWN;
            case 'n' -> KNIGHT;
            case 'b' -> BISHOP;
            case 'r' -> ROOK;
            case 'q' -> QUEEN;
            default -> throw new IllegalArgumentException("no drop piece for: " + c);
        };
    }
}
