package com.chessoc.server.engine;

import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Terminal;

/** Terminal / check status of a position with no move applied (§2.3). */
public record StatusResult(Terminal terminal, boolean inCheck, Side sideToMove) {
}
