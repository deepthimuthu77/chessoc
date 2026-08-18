package com.chessoc.server.match.state;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Uci;

/** One committed ply: the move and the resulting FEN (reserve included, for drop reconstruction). */
public record PersistedPly(long version, Fen fen, Uci move) {
}
