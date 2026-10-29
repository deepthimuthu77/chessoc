package com.chessoc.server.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessoc.server.domain.Variant;
import com.chessoc.server.match.TimeControl;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MatchmakingServiceTest {

    private final TimeControl blitz = TimeControl.fischer(300_000, 0);

    private MatchmakingService service() {
        return new MatchmakingService(() -> true, () -> "CODE1234");
    }

    @Test
    void pairsTwoSeekersInSameBucket() {
        MatchmakingService mm = service();
        assertThat(mm.enqueue(new Seek("p1", Variant.STANDARD, blitz))).isEmpty();
        Optional<Pairing> pairing = mm.enqueue(new Seek("p2", Variant.STANDARD, blitz));

        assertThat(pairing).isPresent();
        assertThat(pairing.get().whiteId()).isEqualTo("p1"); // firstIsWhite = true
        assertThat(pairing.get().blackId()).isEqualTo("p2");
        assertThat(pairing.get().rated()).isTrue();
    }

    @Test
    void sameSpeedWithDifferentTimeControlsDoesNotPair() {
        MatchmakingService mm = service();
        mm.enqueue(new Seek("p1", Variant.STANDARD, blitz));
        assertThat(mm.enqueue(new Seek("p2", Variant.STANDARD, TimeControl.fischer(180_000, 2_000)))).isEmpty();
    }

    @Test
    void pairingSurvivesRetriesAndCancellationReturnsAlreadyMatchedGame() {
        SeekCoordinator coordinator = new SeekCoordinator(service(), (w, b, v, tc, rated) -> "paired-game");
        assertThat(coordinator.seek("p1", Variant.STANDARD, blitz)).isNull();
        assertThat(coordinator.seek("p2", Variant.STANDARD, blitz)).isEqualTo("paired-game");
        assertThat(coordinator.poll("p1")).isEqualTo("paired-game");
        assertThat(coordinator.poll("p1")).isEqualTo("paired-game");
        assertThat(coordinator.cancel("p1")).isEqualTo("paired-game");
        assertThat(coordinator.poll("p1")).isNull();
    }

    @Test
    void doesNotPairAcrossBuckets() {
        MatchmakingService mm = service();
        mm.enqueue(new Seek("p1", Variant.STANDARD, TimeControl.fischer(60_000, 0))); // bullet
        Optional<Pairing> pairing = mm.enqueue(new Seek("p2", Variant.STANDARD, blitz)); // blitz
        assertThat(pairing).isEmpty();
    }

    @Test
    void bughouseIsUnrated() {
        MatchmakingService mm = service();
        mm.enqueue(new Seek("p1", Variant.BUGHOUSE, blitz));
        Pairing pairing = mm.enqueue(new Seek("p2", Variant.BUGHOUSE, blitz)).orElseThrow();
        assertThat(pairing.rated()).isFalse();
    }

    @Test
    void friendChallengeAcceptPairs() {
        MatchmakingService mm = service();
        MatchmakingService.Challenge challenge = mm.createChallenge("host", Variant.CRAZYHOUSE, blitz);
        Pairing pairing = mm.accept(challenge.code(), "guest").orElseThrow();

        assertThat(pairing.whiteId()).isEqualTo("host");
        assertThat(pairing.blackId()).isEqualTo("guest");
    }

    @Test
    void cannotAcceptOwnChallenge() {
        MatchmakingService mm = service();
        MatchmakingService.Challenge challenge = mm.createChallenge("host", Variant.STANDARD, blitz);
        assertThat(mm.accept(challenge.code(), "host")).isEmpty();
    }
}
