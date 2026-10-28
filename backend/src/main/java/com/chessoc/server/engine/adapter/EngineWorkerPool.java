package com.chessoc.server.engine.adapter;

import com.chessoc.server.engine.EngineErrorCode;
import com.chessoc.server.engine.EngineException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.List;
import java.util.function.Supplier;

/**
 * A pool of single-request workers for one tier. A faulted worker is evicted and replaced, keeping the
 * pool at its configured size.
 */
public final class EngineWorkerPool implements AutoCloseable {

    private final Supplier<EngineWorker> factory;
    private final BlockingQueue<EngineWorker> idle;
    private final Set<EngineWorker> workers = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Duration acquireTimeout;
    private volatile boolean closed;

    public EngineWorkerPool(int size, Supplier<EngineWorker> factory) {
        this(size, factory, Duration.ofSeconds(2));
    }

    public EngineWorkerPool(int size, Supplier<EngineWorker> factory, Duration acquireTimeout) {
        if (size < 1 || acquireTimeout.isNegative() || acquireTimeout.isZero()) {
            throw new IllegalArgumentException("positive pool size and acquire timeout required");
        }
        this.factory = factory;
        this.acquireTimeout = acquireTimeout;
        this.idle = new LinkedBlockingQueue<>(size);
        try {
            for (int i = 0; i < size; i++) {
                EngineWorker worker = factory.get();
                workers.add(worker);
                idle.add(worker);
            }
        } catch (RuntimeException failure) {
            close();
            throw failure;
        }
    }

    public EngineWorker acquire() {
        if (closed) {
            throw new EngineException(EngineErrorCode.INTERNAL_ERROR, "worker pool closed");
        }
        try {
            EngineWorker worker = idle.poll(acquireTimeout.toNanos(), TimeUnit.NANOSECONDS);
            if (closed) {
                throw new EngineException(EngineErrorCode.INTERNAL_ERROR, "worker pool closed");
            }
            if (worker == null) {
                throw new EngineException(EngineErrorCode.INTERNAL_ERROR,
                        "timeout acquiring engine worker after " + acquireTimeout.toMillis() + "ms");
            }
            return worker;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EngineException(EngineErrorCode.INTERNAL_ERROR, "interrupted acquiring worker");
        }
    }

    public synchronized void release(EngineWorker worker) {
        if (closed) {
            return; // close() already disposed of leased workers too
        }
        if (!workers.contains(worker) || idle.contains(worker) || !idle.offer(worker)) {
            throw new IllegalStateException("worker is not leased from this pool");
        }
    }

    /** Discard a faulted worker and top the pool back up with a fresh one. */
    public void evict(EngineWorker worker) {
        synchronized (this) {
            if (!workers.remove(worker) || closed) {
                return;
            }
            idle.remove(worker);
        }
        closeWorker(worker);
        EngineWorker replacement = factory.get();
        synchronized (this) {
            if (!closed) {
                workers.add(replacement);
                idle.add(replacement);
                return;
            }
        }
        closeWorker(replacement);
    }

    public int available() {
        return idle.size();
    }

    @Override
    public void close() {
        List<EngineWorker> closing;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            closing = List.copyOf(workers);
            workers.clear();
            idle.clear();
        }
        closing.forEach(EngineWorkerPool::closeWorker);
    }

    private static void closeWorker(EngineWorker worker) {
        try {
            worker.close();
        } catch (RuntimeException ignored) {
            // best effort
        }
    }
}
