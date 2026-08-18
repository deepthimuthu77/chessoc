package com.chessoc.server.match.state;

/** Schedules a deferred task (flag timers). Abstracted so tests drive time deterministically. */
public interface Scheduler {

    Cancellable schedule(Runnable task, long delayMs);

    interface Cancellable {
        void cancel();
    }
}
