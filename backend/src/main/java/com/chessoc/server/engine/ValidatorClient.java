package com.chessoc.server.engine;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Uci;
import com.chessoc.server.domain.Variant;
import java.util.List;

/**
 * The validator tier: legality, move application, legal-move generation, terminal detection.
 * Stateless — every call carries the full position. Match State is the only caller.
 */
public interface ValidatorClient {

    /** Validate and apply {@code move}; history is the move-list form used for threefold. */
    ApplyResult apply(Variant variant, Fen start, List<Uci> history, Uci move);

    /** The exact set of legal moves (promotions and drops enumerated as distinct UCIs). */
    List<Uci> legalMoves(Variant variant, Fen start, List<Uci> history);

    /** Terminal / check status without applying a move. */
    StatusResult status(Variant variant, Fen position);

    /** Whether {@code side} has sufficient mating material — for timeout adjudication (FIDE 6.9). */
    boolean canMate(Variant variant, Fen position, Side side);
}
