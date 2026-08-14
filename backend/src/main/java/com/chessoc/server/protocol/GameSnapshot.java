package com.chessoc.server.protocol;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Uci;
import java.util.List;
import java.util.Optional;

/** The authoritative game projection the client renders. Version never decreases. */
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
        String blackPlayerId) {

    public GameSnapshot {
        moves = moves == null ? List.of() : List.copyOf(moves);
    }
}
