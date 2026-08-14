package com.chessoc.server.match;

import com.chessoc.server.domain.PieceType;
import com.chessoc.server.domain.Side;
import java.util.EnumMap;
import java.util.Map;

/**
 * Pieces-in-hand for a drop-variant board, mirroring the FEN {@code [...]} field (uppercase = White,
 * lowercase = Black). Match State is the sole mutator — for bughouse it routes a captured piece here
 * on the partner board.
 */
public final class Reserve {

    private static final PieceType[] ENCODE_ORDER = {
            PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN
    };

    private final Map<Side, Map<PieceType, Integer>> counts = new EnumMap<>(Side.class);

    public Reserve() {
        counts.put(Side.WHITE, new EnumMap<>(PieceType.class));
        counts.put(Side.BLACK, new EnumMap<>(PieceType.class));
    }

    public static Reserve parse(String bracketContent) {
        Reserve r = new Reserve();
        for (int i = 0; i < bracketContent.length(); i++) {
            char c = bracketContent.charAt(i);
            Side side = Character.isUpperCase(c) ? Side.WHITE : Side.BLACK;
            r.add(side, PieceType.fromSymbol(c));
        }
        return r;
    }

    public void add(Side side, PieceType piece) {
        counts.get(side).merge(piece, 1, Integer::sum);
    }

    public void remove(Side side, PieceType piece) {
        int n = count(side, piece);
        if (n <= 0) {
            throw new IllegalStateException(side + " has no " + piece + " to drop");
        }
        if (n == 1) {
            counts.get(side).remove(piece);
        } else {
            counts.get(side).put(piece, n - 1);
        }
    }

    public int count(Side side, PieceType piece) {
        return counts.get(side).getOrDefault(piece, 0);
    }

    public boolean isEmpty() {
        return counts.get(Side.WHITE).isEmpty() && counts.get(Side.BLACK).isEmpty();
    }

    /** Canonical bracket content: White pieces (Q,R,B,N,P) then Black, high value first. */
    public String encode() {
        StringBuilder sb = new StringBuilder();
        for (Side side : new Side[] {Side.WHITE, Side.BLACK}) {
            for (PieceType piece : ENCODE_ORDER) {
                char symbol = side == Side.WHITE
                        ? Character.toUpperCase(piece.symbol())
                        : piece.symbol();
                sb.append(String.valueOf(symbol).repeat(count(side, piece)));
            }
        }
        return sb.toString();
    }
}
