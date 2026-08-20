package com.chessoc.server.rating;

import com.chessoc.server.domain.Side;
import com.chessoc.server.matchmaking.Glicko2;
import com.chessoc.server.matchmaking.Rating;
import com.chessoc.server.protocol.GameResult;
import java.util.List;
import java.util.function.LongSupplier;

/** Applies a rated game's outcome to both players' Glicko-2 ratings and records history samples. */
public final class RatingService {

    private static final int HISTORY_LIMIT = 500;

    private final RatingRepository repo;
    private final LongSupplier clock;
    private final Glicko2 glicko = new Glicko2();

    public RatingService(RatingRepository repo) {
        this(repo, System::currentTimeMillis);
    }

    public RatingService(RatingRepository repo, LongSupplier clock) {
        this.repo = repo;
        this.clock = clock;
    }

    public void applyResult(String whiteId, String blackId, String variant, String speed, GameResult result) {
        if (result.type() == GameResult.ResultType.ABORTED) {
            return;
        }
        double whiteScore = switch (result.type()) {
            case WIN -> result.winner().map(w -> w == Side.WHITE ? 1.0 : 0.0).orElse(0.5);
            case DRAW, ABORTED -> 0.5;
        };

        RatingRow white = repo.get(whiteId, variant, speed);
        RatingRow black = repo.get(blackId, variant, speed);
        Rating newWhite = glicko.update(white.rating(), List.of(new Glicko2.Game(black.rating(), whiteScore)));
        Rating newBlack = glicko.update(black.rating(), List.of(new Glicko2.Game(white.rating(), 1.0 - whiteScore)));

        repo.save(whiteId, variant, speed, new RatingRow(whiteId, newWhite, white.games() + 1));
        repo.save(blackId, variant, speed, new RatingRow(blackId, newBlack, black.games() + 1));

        long at = clock.getAsLong();
        repo.appendHistory(whiteId, variant, speed, (int) Math.round(newWhite.rating()), at);
        repo.appendHistory(blackId, variant, speed, (int) Math.round(newBlack.rating()), at);
    }

    public List<RatingHistoryPoint> history(String accountId, String variant, String speed) {
        return repo.history(accountId, variant, speed, HISTORY_LIMIT);
    }
}
