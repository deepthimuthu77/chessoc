package com.chessoc.server.match;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClockTest {

    @Test
    void suddenDeathJustSubtracts() {
        Clock c = Clock.start(TimeControl.sudden(180_000)).afterMove(5_000);
        assertThat(c.remainingMs()).isEqualTo(175_000);
        assertThat(c.flagged()).isFalse();
    }

    @Test
    void fischerAddsIncrement() {
        Clock c = Clock.start(TimeControl.fischer(180_000, 2_000)).afterMove(5_000);
        assertThat(c.remainingMs()).isEqualTo(177_000); // 180000 - 5000 + 2000
    }

    @Test
    void flaggingGrantsNoIncrement() {
        Clock c = Clock.start(TimeControl.fischer(3_000, 2_000)).afterMove(4_000);
        assertThat(c.flagged()).isTrue();
        assertThat(c.remainingMs()).isEqualTo(-1_000);
    }

    @Test
    void lagIsCreditedButCapped() {
        Clock c = Clock.start(TimeControl.sudden(60_000)).afterMove(5_000, 1_000);
        assertThat(c.remainingMs()).isEqualTo(55_200); // credit capped at 200ms
    }

    @Test
    void bronsteinGivesBackMinOfSpentAndDelay() {
        Clock quick = Clock.start(TimeControl.bronstein(60_000, 3_000)).afterMove(1_000);
        assertThat(quick.remainingMs()).isEqualTo(60_000); // spent 1000, delay 3000 -> full refund
        Clock slow = Clock.start(TimeControl.bronstein(60_000, 3_000)).afterMove(5_000);
        assertThat(slow.remainingMs()).isEqualTo(58_000); // 60000 - 5000 + 3000
    }
}
