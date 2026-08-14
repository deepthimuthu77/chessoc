package com.chessoc.server.protocol;

import com.chessoc.server.domain.Side;
import java.util.Optional;

/** The decided outcome of a game. Winner is absent for draws and aborts. */
public record GameResult(ResultType type, Optional<Side> winner, String reason) {

    public enum ResultType {
        WIN,
        DRAW,
        ABORTED
    }
}
