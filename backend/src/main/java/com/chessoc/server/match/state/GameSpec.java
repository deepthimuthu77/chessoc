package com.chessoc.server.match.state;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Variant;
import com.chessoc.server.match.TimeControl;

/** Everything needed to start one game. */
public record GameSpec(
        String gameId,
        Variant variant,
        Fen initialFen,
        String whiteId,
        String blackId,
        TimeControl timeControl,
        boolean rated) {
}
