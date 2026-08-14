package com.chessoc.server.engine;

import com.chessoc.server.domain.Uci;
import java.util.List;
import java.util.Optional;

/** A search reply: best move, evaluation, principal variation, reached depth, optional ponder. */
public record SearchResult(Uci bestMove, Score score, List<Uci> pv, int depth, Optional<Uci> ponder) {

    public SearchResult {
        pv = pv == null ? List.of() : List.copyOf(pv);
    }
}
