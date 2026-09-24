package com.chessoc.server.domain;

import java.util.Optional;

/**
 * Canonical persisted position:
 * {@code <board>[<reserve>] <stm> <castling> <ep> <halfmove> <fullmove>}.
 *
 * <p>Stored and re-emitted verbatim so the promoted-piece {@code ~} marker and the {@code [...]}
 * reserve survive persistence. This type reads the structure; it never interprets chess.
 */
public record Fen(
        String raw,
        String board,
        Optional<String> reserve,
        Side sideToMove,
        String castling,
        String enPassant,
        int halfmoveClock,
        int fullmoveNumber) {

    public static Fen parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("empty FEN");
        }
        String trimmed = raw.trim();
        String[] f = trimmed.split(" ");
        if (f.length != 6) {
            throw new IllegalArgumentException("FEN needs 6 fields: " + raw);
        }

        String placement = f[0];
        String board = placement;
        Optional<String> reserve = Optional.empty();
        int bracket = placement.indexOf('[');
        if (bracket >= 0) {
            if (!placement.endsWith("]")) {
                throw new IllegalArgumentException("unterminated reserve: " + raw);
            }
            board = placement.substring(0, bracket);
            reserve = Optional.of(placement.substring(bracket + 1, placement.length() - 1));
        }

        int half;
        int full;
        try {
            half = Integer.parseInt(f[4]);
            full = Integer.parseInt(f[5]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad clock fields: " + raw, e);
        }

        return new Fen(trimmed, board, reserve, Side.fromFen(f[1]), f[2], f[3], half, full);
    }

    public boolean hasReserve() {
        return reserve.isPresent();
    }

    public Fen withReserve(String newReserve) {
        return parse(board + "[" + newReserve + "] " + sideToMove.toFen() + " " + castling + " "
                + enPassant + " " + halfmoveClock + " " + fullmoveNumber);
    }

    /** A standard-variant position must not carry a reserve bracket; a drop variant reads it as its hand. */
    public void requireConsistentWith(Variant variant) {
        if (!variant.isDrop() && hasReserve()) {
            throw new IllegalArgumentException(variant + " FEN must not carry a [reserve]: " + raw);
        }
    }

    @Override
    public String toString() {
        return raw;
    }
}
