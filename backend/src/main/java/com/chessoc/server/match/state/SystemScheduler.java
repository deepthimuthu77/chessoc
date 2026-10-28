package com.chessoc.server.match.state;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/** Real scheduler backed by a shared {@link ScheduledExecutorService}. */
public final class SystemScheduler implements Scheduler {

    private final ScheduledExecutorService executor;

    public SystemScheduler(ScheduledExecutorService executor) {
        this.executor = executor;
        if (executor instanceof ScheduledThreadPoolExecutor scheduled) {
            scheduled.setRemoveOnCancelPolicy(true);
        }
    }

    /** Search saturation must reject, never run expensive work on the clock/caller thread. */
    public static ThreadPoolExecutor newAiExecutor(int workers) {
        if (workers < 1) {
            throw new IllegalArgumentException("AI workers must be positive");
        }
        AtomicInteger sequence = new AtomicInteger();
        return new ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(16, workers * 8)), task -> {
                    Thread thread = new Thread(task, "match-ai-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    // Compatibility for embedders using the original constructors. Production owns its executor.
    static ThreadPoolExecutor sharedAiExecutor() {
        return SharedAi.EXECUTOR;
    }

    private static final class SharedAi {
        private static final ThreadPoolExecutor EXECUTOR = newAiExecutor(1);
    }

    @Override
    public Cancellable schedule(Runnable task, long delayMs) {
        ScheduledFuture<?> future = executor.schedule(task, Math.max(0, delayMs), TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }
}
