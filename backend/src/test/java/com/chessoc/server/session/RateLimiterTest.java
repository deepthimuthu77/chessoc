package com.chessoc.server.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

    @Test
    void blocksActionsInsideTheWindow() {
        RateLimiter limiter = new RateLimiter(100);
        assertThat(limiter.allow("s", 0)).isTrue();
        assertThat(limiter.allow("s", 50)).isFalse();
        assertThat(limiter.allow("s", 100)).isTrue();
    }

    @Test
    void keysAreIndependent() {
        RateLimiter limiter = new RateLimiter(100);
        assertThat(limiter.allow("a", 0)).isTrue();
        assertThat(limiter.allow("b", 0)).isTrue();
    }
}
