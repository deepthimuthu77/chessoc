package com.chessoc.server.engine.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chessoc.server.engine.EngineException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(5)
class EngineWorkerPoolTest {
    @Test
    void exhaustedPoolFailsWithinAcquireTimeoutAndRemainsUsable() {
        try (var pool = new EngineWorkerPool(1, Worker::new, Duration.ofMillis(60))) {
            EngineWorker leased = pool.acquire();
            long start = System.nanoTime();
            assertThatThrownBy(pool::acquire).isInstanceOf(EngineException.class)
                    .hasMessageContaining("timeout acquiring engine worker");
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
            pool.release(leased);
            assertThat(pool.acquire()).isSameAs(leased);
        }
    }

    @Test
    void shutdownClosesLeasedWorkersAndLateReleaseCannotResurrectPool() {
        var pool = new EngineWorkerPool(2, Worker::new);
        Worker first = (Worker) pool.acquire();
        Worker second = (Worker) pool.acquire();
        pool.close();
        pool.release(first);
        pool.evict(second);
        pool.close();
        assertThat(first.closed.get()).isEqualTo(1);
        assertThat(second.closed.get()).isEqualTo(1);
        assertThat(pool.available()).isZero();
        assertThatThrownBy(pool::acquire).hasMessageContaining("pool closed");
    }

    @Test
    void replacementCreatedDuringShutdownIsClosedInsteadOfLeaked() throws Exception {
        var replacing = new CountDownLatch(1);
        var releaseFactory = new CountDownLatch(1);
        var created = new AtomicInteger();
        Worker original = new Worker();
        Worker replacement = new Worker();
        var pool = new EngineWorkerPool(1, () -> {
            if (created.incrementAndGet() == 1) {
                return original;
            }
            replacing.countDown();
            try {
                if (!releaseFactory.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("replacement not released");
                }
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            return replacement;
        });
        var background = Executors.newSingleThreadExecutor();
        try {
            EngineWorker leased = pool.acquire();
            var eviction = background.submit(() -> pool.evict(leased));
            assertThat(replacing.await(1, TimeUnit.SECONDS)).isTrue();
            pool.close();
            releaseFactory.countDown();
            eviction.get(1, TimeUnit.SECONDS);
            assertThat(replacement.closed.get()).isEqualTo(1);
            assertThat(pool.available()).isZero();
        } finally {
            releaseFactory.countDown();
            pool.close();
            background.shutdownNow();
        }
    }

    @Test
    void invalidSizingFailsImmediately() {
        assertThatThrownBy(() -> new EngineWorkerPool(0, Worker::new))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EngineWorkerPool(1, Worker::new, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class Worker implements EngineWorker {
        final AtomicInteger closed = new AtomicInteger();

        public String request(String payload, Duration timeout) { return "{}"; }
        public boolean isReady() { return closed.get() == 0; }
        public void close() { closed.incrementAndGet(); }
    }
}
