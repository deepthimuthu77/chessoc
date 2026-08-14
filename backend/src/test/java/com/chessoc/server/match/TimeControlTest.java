package com.chessoc.server.match;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TimeControlTest {

    @Test
    void bucketsByEstimatedDuration() {
        assertThat(TimeControl.fischer(60_000, 0).bucket()).isEqualTo(SpeedBucket.BULLET);
        assertThat(TimeControl.fischer(180_000, 0).bucket()).isEqualTo(SpeedBucket.BLITZ);
        assertThat(TimeControl.fischer(600_000, 0).bucket()).isEqualTo(SpeedBucket.RAPID);
        assertThat(TimeControl.fischer(1_800_000, 0).bucket()).isEqualTo(SpeedBucket.CLASSICAL);
    }

    @Test
    void incrementCountsTowardEstimate() {
        // 2m base + 40×1s increment = 160s -> still bullet just under 3m.
        assertThat(TimeControl.fischer(120_000, 1_000).bucket()).isEqualTo(SpeedBucket.BULLET);
        // 2m base + 40×3s = 240s -> blitz.
        assertThat(TimeControl.fischer(120_000, 3_000).bucket()).isEqualTo(SpeedBucket.BLITZ);
    }
}
