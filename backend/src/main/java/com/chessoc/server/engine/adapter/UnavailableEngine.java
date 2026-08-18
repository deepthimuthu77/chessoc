package com.chessoc.server.engine.adapter;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Uci;
import com.chessoc.server.domain.Variant;
import com.chessoc.server.engine.ApplyResult;
import com.chessoc.server.engine.EngineAdapter;
import com.chessoc.server.engine.EngineErrorCode;
import com.chessoc.server.engine.EngineException;
import com.chessoc.server.engine.SearchBudget;
import com.chessoc.server.engine.SearchResult;
import com.chessoc.server.engine.StatusResult;
import java.util.List;

/**
 * Stand-in used when no engine binary is configured. Auth and transport come up, but any chess call
 * fails loudly — there is no Java-side rules fallback by design.
 */
public final class UnavailableEngine implements EngineAdapter {

    @Override
    public ApplyResult apply(Variant variant, Fen start, List<Uci> history, Uci move) {
        throw unavailable();
    }

    @Override
    public List<Uci> legalMoves(Variant variant, Fen start, List<Uci> history) {
        throw unavailable();
    }

    @Override
    public StatusResult status(Variant variant, Fen position) {
        throw unavailable();
    }

    @Override
    public boolean canMate(Variant variant, Fen position, Side side) {
        throw unavailable();
    }

    @Override
    public SearchResult bestMove(Variant variant, Fen position, SearchBudget budget) {
        throw unavailable();
    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public void shutdown() {
        // nothing to release
    }

    private EngineException unavailable() {
        return new EngineException(EngineErrorCode.INTERNAL_ERROR, "engine not configured");
    }
}
