package com.chessoc.server.social;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PresenceTest {

    @Test
    void onlineExpiresAfterTheTtlWithoutAHeartbeat() {
        AtomicLong now = new AtomicLong(0);
        Presence presence = new Presence(now::get);

        presence.online("a");
        assertThat(presence.isOnline("a")).isTrue();

        now.set(89_000); // just inside the 90s window
        assertThat(presence.isOnline("a")).isTrue();

        now.set(91_000); // past the window → offline
        assertThat(presence.isOnline("a")).isFalse();

        presence.online("a"); // a heartbeat brings them back
        assertThat(presence.isOnline("a")).isTrue();
    }

    @Test
    void unknownAccountIsOffline() {
        Presence presence = new Presence(() -> 0L);
        assertThat(presence.isOnline("ghost")).isFalse();
    }
}
