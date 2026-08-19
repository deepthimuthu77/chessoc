package com.chessoc.server.protocol;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Uci;
import java.util.List;
import java.util.Optional;

/**
 * The authoritative game projection the client renders. Version never decreases. Captured pieces are
 * engine-sourced (from each move's capturedDropType), never recomputed by the client.
 */
public record GameSnapshot(
        String gameId,
        long version,
        Fen initialFen,
        Fen fen,
        Side sideToMove,
        List<Uci> moves,
        ClockSample clockSample,
        Optional<GameResult> result,
        String whitePlayerId,
        String blackPlayerId,
        boolean inCheck,
        List<String> capturedByWhite,
        List<String> capturedByBlack) {

    public GameSnapshot {
        moves = moves == null ? List.of() : List.copyOf(moves);
        capturedByWhite = capturedByWhite == null ? List.of() : List.copyOf(capturedByWhite);
        capturedByBlack = capturedByBlack == null ? List.of() : List.copyOf(capturedByBlack);
    }
}
