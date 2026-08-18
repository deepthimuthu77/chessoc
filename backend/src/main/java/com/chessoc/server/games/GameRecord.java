package com.chessoc.server.games;

/** A finished game's metadata. {@code winnerAccountId} is empty for a draw. */
public record GameRecord(
        String id,
        String whiteId,
        String blackId,
        String variant,
        String speed,
        boolean rated,
        String resultType,
        String winnerAccountId,
        String reason,
        long endedAtEpochMs) {
}
