package com.chessoc.server.session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anti-automation gate: a hard minimum interval between submitted actions per key (§11). This detects
 * bots only; it is deliberately not move-quality analysis. Tuned to not punish legitimate bullet play.
 */
public final class RateLimiter {

    private final long minIntervalMs;
    private final Map<String, Long> lastActionAt = new ConcurrentHashMap<>();

    public RateLimiter(long minIntervalMs) {
        this.minIntervalMs = minIntervalMs;
    }

    /** True if the action is allowed now; records the time atomically per key. */
    public boolean allow(String key, long nowEpochMs) {
        boolean[] allowed = {false};
        lastActionAt.compute(key, (k, previous) -> {
            if (previous == null || nowEpochMs - previous >= minIntervalMs) {
                allowed[0] = true;
                return nowEpochMs;
            }
            return previous;
        });
        return allowed[0];
    }

    public void forget(String key) {
        lastActionAt.remove(key);
    }
}
