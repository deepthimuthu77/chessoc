package com.chessoc.server.match.state;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Terminal;
import com.chessoc.server.domain.Uci;
import com.chessoc.server.domain.Variant;
import com.chessoc.server.engine.ApplyResult;
import com.chessoc.server.engine.StatusResult;
import com.chessoc.server.engine.ValidatorClient;
import java.util.ArrayList;
import java.util.List;

/** A deterministic validator stand-in keyed by ply. It scripts replies; it does not know chess. */
final class ScriptedEngine implements ValidatorClient {

    final List<List<Uci>> legalByPly = new ArrayList<>();
    final List<ApplyResult> applyByPly = new ArrayList<>();
    boolean canMate = true;
    int applyCalls;

    @Override
    public ApplyResult apply(Variant variant, Fen start, List<Uci> history, Uci move) {
        applyCalls++;
        return applyByPly.get(history.size());
    }

    @Override
    public List<Uci> legalMoves(Variant variant, Fen start, List<Uci> history) {
        int ply = history.size();
        return ply < legalByPly.size() ? legalByPly.get(ply) : List.of();
    }

    @Override
    public StatusResult status(Variant variant, Fen position) {
        return new StatusResult(Terminal.NONE, false, position.sideToMove());
    }

    @Override
    public boolean canMate(Variant variant, Fen position, Side side) {
        return canMate;
    }
}
