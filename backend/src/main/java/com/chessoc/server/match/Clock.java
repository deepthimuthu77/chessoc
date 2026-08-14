package com.chessoc.server.match;

/**
 * One side's server-authoritative clock. Immutable; a move produces a new clock. The server is the
 * sole timekeeper — the client never adjudicates a flag.
 */
public record Clock(long remainingMs, TimeControl control) {

    /** Measured lag credited per move is capped so it cannot be gamed (§12.2). */
    public static final long LAG_CAP_MS = 200;

    public static Clock start(TimeControl control) {
        return new Clock(control.baseMs(), control);
    }

    public boolean flagged() {
        return remainingMs <= 0;
    }

    public Clock afterMove(long elapsedMs) {
        return afterMove(elapsedMs, 0);
    }

    /** New clock after the side spends {@code elapsedMs}, crediting up to {@link #LAG_CAP_MS} of lag. */
    public Clock afterMove(long elapsedMs, long lagMs) {
        long credited = Math.min(Math.max(lagMs, 0), LAG_CAP_MS);
        long spent = Math.max(0, elapsedMs - credited);
        long afterSpent = remainingMs - spent;
        if (afterSpent <= 0) {
            return new Clock(afterSpent, control); // flagged — no time granted back
        }
        long bonus = switch (control.delayType()) {
            case NONE -> 0;
            case FISCHER -> control.incrementMs();
            case BRONSTEIN -> Math.min(spent, control.delayMs());
        };
        return new Clock(afterSpent + bonus, control);
    }
}
