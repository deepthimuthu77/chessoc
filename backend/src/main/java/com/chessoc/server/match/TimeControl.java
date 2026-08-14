package com.chessoc.server.match;

/** A time control: base time plus a per-move increment or delay. */
public record TimeControl(long baseMs, long incrementMs, DelayType delayType, long delayMs) {

    public static TimeControl sudden(long baseMs) {
        return new TimeControl(baseMs, 0, DelayType.NONE, 0);
    }

    public static TimeControl fischer(long baseMs, long incrementMs) {
        return new TimeControl(baseMs, incrementMs, DelayType.FISCHER, 0);
    }

    public static TimeControl bronstein(long baseMs, long delayMs) {
        return new TimeControl(baseMs, 0, DelayType.BRONSTEIN, delayMs);
    }

    /** Estimated game length for bucketing: base + 40 × per-move add (§13.1). */
    public long estimatedDurationMs() {
        return baseMs + 40 * (incrementMs + delayMs);
    }

    public SpeedBucket bucket() {
        return SpeedBucket.of(this);
    }
}
