package com.chessoc.server.protocol;

import com.chessoc.server.domain.Side;

/** A canonical server clock reading. The client interpolates the display only. */
public record ClockSample(
        long whiteRemainingMs,
        long blackRemainingMs,
        Side activeSide,
        long serverTimestamp) {
}
