package com.chessoc.server.engine.adapter;

import com.chessoc.server.engine.EngineErrorCode;
import com.chessoc.server.engine.EngineException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

/**
 * A pool of single-request workers for one tier. A faulted worker is evicted and replaced, keeping the
 * pool at its configured size.
 */
public final class EngineWorkerPool implements AutoCloseable {

    private final Supplier<EngineWorker> factory;
    private final BlockingQueue<EngineWorker> idle = new LinkedBlockingQueue<>();

    public EngineWorkerPool(int size, Supplier<EngineWorker> factory) {
        this.factory = factory;
        for (int i = 0; i < size; i++) {
            idle.add(factory.get());
        }
    }

    public EngineWorker acquire() {
        try {
            return idle.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EngineException(EngineErrorCode.INTERNAL_ERROR, "interrupted acquiring worker");
        }
    }

    public void release(EngineWorker worker) {
        idle.add(worker);
    }

    /** Discard a faulted worker and top the pool back up with a fresh one. */
    public void evict(EngineWorker worker) {
        try {
            worker.close();
        } catch (RuntimeException ignored) {
            // already broken
        }
        idle.add(factory.get());
    }

    public int available() {
        return idle.size();
    }

    @Override
    public void close() {
        for (EngineWorker worker : idle) {
            try {
                worker.close();
            } catch (RuntimeException ignored) {
                // best effort
            }
        }
        idle.clear();
    }
}
