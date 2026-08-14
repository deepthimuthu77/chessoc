package com.chessoc.server.match;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Variant;

/** A single board within a match. Its FEN carries any drop reserve in the {@code [...]} field. */
public record Board(String id, Variant variant, Fen fen) {
}
