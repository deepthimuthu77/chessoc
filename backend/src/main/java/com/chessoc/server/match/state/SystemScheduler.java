package com.chessoc.server.match.state;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Real scheduler backed by a shared {@link ScheduledExecutorService}. */
public final class SystemScheduler implements Scheduler {

    private final ScheduledExecutorService executor;

    public SystemScheduler(ScheduledExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public Cancellable schedule(Runnable task, long delayMs) {
        ScheduledFuture<?> future = executor.schedule(task, Math.max(0, delayMs), TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }
}
