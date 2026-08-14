package com.chessoc.server.engine;

/** A search evaluation: either a centipawn score or a forced mate distance. Side-relative. */
public sealed interface Score permits Score.Cp, Score.Mate {

    record Cp(int centipawns) implements Score {
    }

    record Mate(int movesToMate) implements Score {
    }
}
