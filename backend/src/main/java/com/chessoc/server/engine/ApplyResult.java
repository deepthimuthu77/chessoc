package com.chessoc.server.engine;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.PieceType;
import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Terminal;
import java.util.Optional;

/**
 * Result of validating and applying one move. On success the server commits exactly
 * {@code resultingPosition}. {@code capturedDropType} is promotion-reverted and, for bughouse, routed
 * by the server to the partner board's reserve.
 */
public record ApplyResult(
        boolean legal,
        Optional<Fen> resultingPosition,
        Terminal terminal,
        boolean inCheck,
        Optional<Side> sideToMove,
        Optional<PieceType> capturedDropType,
        Optional<String> reason) {

    public static ApplyResult legal(
            Fen resultingPosition,
            Terminal terminal,
            boolean inCheck,
            Side sideToMove,
            Optional<PieceType> capturedDropType) {
        return new ApplyResult(true, Optional.of(resultingPosition), terminal, inCheck,
                Optional.of(sideToMove), capturedDropType, Optional.empty());
    }

    public static ApplyResult illegal(String reason) {
        return new ApplyResult(false, Optional.empty(), Terminal.NONE, false,
                Optional.empty(), Optional.empty(), Optional.of(reason));
    }
}
