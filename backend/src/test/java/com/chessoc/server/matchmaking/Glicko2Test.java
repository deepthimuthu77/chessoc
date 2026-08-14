package com.chessoc.server.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class Glicko2Test {

    // Glickman's worked example: player (1500, 200, 0.06), tau = 0.5, vs three opponents.
    @Test
    void matchesGlickmanWorkedExample() {
        Glicko2 system = new Glicko2(0.5);
        Rating player = new Rating(1500, 200, 0.06);
        Rating r = system.update(player, List.of(
                new Glicko2.Game(new Rating(1400, 30, 0.06), 1.0),
                new Glicko2.Game(new Rating(1550, 100, 0.06), 0.0),
                new Glicko2.Game(new Rating(1700, 300, 0.06), 0.0)));

        assertThat(r.rating()).isCloseTo(1464.06, org.assertj.core.data.Offset.offset(0.5));
        assertThat(r.deviation()).isCloseTo(151.52, org.assertj.core.data.Offset.offset(0.5));
        assertThat(r.volatility()).isCloseTo(0.05999, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void deviationGrowsOnSkippedPeriod() {
        Glicko2 system = new Glicko2();
        Rating r = system.update(new Rating(1500, 200, 0.06), List.of());
        assertThat(r.rating()).isEqualTo(1500);
        assertThat(r.deviation()).isGreaterThan(200);
    }

    @Test
    void winRaisesLossLowers() {
        Glicko2 system = new Glicko2();
        Rating base = Rating.provisional();
        Rating opponent = new Rating(1500, 50, 0.06);
        Rating won = system.update(base, List.of(new Glicko2.Game(opponent, 1.0)));
        Rating lost = system.update(base, List.of(new Glicko2.Game(opponent, 0.0)));
        assertThat(won.rating()).isGreaterThan(base.rating());
        assertThat(lost.rating()).isLessThan(base.rating());
    }
}
