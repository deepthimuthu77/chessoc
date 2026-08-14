package com.chessoc.server.match;

/** Matchmaking speed bucket, keyed off estimated duration (§13.1). */
public enum SpeedBucket {
    BULLET,
    BLITZ,
    RAPID,
    CLASSICAL;

    public static SpeedBucket of(TimeControl tc) {
        long est = tc.estimatedDurationMs();
        if (est < 180_000) {
            return BULLET;      // < 3m
        }
        if (est < 480_000) {
            return BLITZ;       // 3–8m
        }
        if (est <= 1_500_000) {
            return RAPID;       // up to 25m
        }
        return CLASSICAL;       // > 25m
    }
}
