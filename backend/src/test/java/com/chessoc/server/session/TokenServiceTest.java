package com.chessoc.server.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TokenServiceTest {

    private final TokenService service = new TokenService("test-secret".getBytes(StandardCharsets.UTF_8));

    @Test
    void issuesAndVerifies() {
        String token = service.issue("player-1", "session-1", 10_000);
        SessionToken verified = service.verify(token, 5_000);
        assertThat(verified.playerId()).isEqualTo("player-1");
        assertThat(verified.sessionId()).isEqualTo("session-1");
    }

    @Test
    void rejectsExpired() {
        String token = service.issue("player-1", "session-1", 10_000);
        assertThatThrownBy(() -> service.verify(token, 10_000)).isInstanceOf(AuthException.class);
    }

    @Test
    void rejectsTampering() {
        String token = service.issue("player-1", "session-1", 10_000);
        String forged = token.substring(0, token.indexOf('.')) + ".AAAA";
        assertThatThrownBy(() -> service.verify(forged, 1_000)).isInstanceOf(AuthException.class);
    }

    @Test
    void rejectsForeignSecret() {
        String token = service.issue("player-1", "session-1", 10_000);
        TokenService other = new TokenService("other-secret".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> other.verify(token, 1_000)).isInstanceOf(AuthException.class);
    }
}
