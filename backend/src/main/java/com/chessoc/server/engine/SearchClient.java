package com.chessoc.server.engine;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Variant;

/** The search tier: best move / evaluation for Human-vs-AI and analysis. */
public interface SearchClient {

    SearchResult bestMove(Variant variant, Fen position, SearchBudget budget);
}
