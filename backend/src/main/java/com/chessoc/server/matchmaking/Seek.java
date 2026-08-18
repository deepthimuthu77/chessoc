package com.chessoc.server.matchmaking;

import com.chessoc.server.domain.Variant;
import com.chessoc.server.match.SpeedBucket;
import com.chessoc.server.match.TimeControl;

/** A player waiting in the queue for a given variant and time control. */
public record Seek(String playerId, Variant variant, TimeControl timeControl) {

    /** Pairing bucket: same variant and speed only. */
    public Bucket bucket() {
        return new Bucket(variant, timeControl.bucket());
    }

    public record Bucket(Variant variant, SpeedBucket speed) {
    }
}
