package com.chessoc.server.rating;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessoc.server.domain.Side;
import com.chessoc.server.protocol.GameResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RatingServiceTest {

    @Test
    void appliesResultAndRecordsHistoryForBothPlayers() {
        InMemoryRatingRepository repo = new InMemoryRatingRepository();
        RatingService service = new RatingService(repo, () -> 1000L);

        service.applyResult("white", "black", "standard", "blitz",
                new GameResult(GameResult.ResultType.WIN, Optional.of(Side.WHITE), "checkmate"));

        // Winner rated up, loser down from the provisional 1500.
        assertThat(repo.get("white", "standard", "blitz").rating().rating()).isGreaterThan(1500);
        assertThat(repo.get("black", "standard", "blitz").rating().rating()).isLessThan(1500);

        List<RatingHistoryPoint> whiteHistory = service.history("white", "standard", "blitz");
        assertThat(whiteHistory).hasSize(1);
        assertThat(whiteHistory.get(0).at()).isEqualTo(1000L);
        assertThat(whiteHistory.get(0).rating()).isGreaterThan(1500);
        assertThat(service.history("black", "standard", "blitz")).hasSize(1);
    }

    @Test
    void abortedGamesDoNotTouchRatingsOrHistory() {
        InMemoryRatingRepository repo = new InMemoryRatingRepository();
        RatingService service = new RatingService(repo, () -> 1L);

        service.applyResult("white", "black", "standard", "blitz",
                new GameResult(GameResult.ResultType.ABORTED, Optional.empty(), "abort"));

        assertThat(service.history("white", "standard", "blitz")).isEmpty();
        assertThat(repo.get("white", "standard", "blitz").games()).isZero();
    }
}
