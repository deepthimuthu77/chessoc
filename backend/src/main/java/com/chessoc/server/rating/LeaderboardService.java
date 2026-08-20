package com.chessoc.server.rating;

import com.chessoc.server.account.Account;
import com.chessoc.server.account.AccountRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Reads leaderboards and decorates rating rows with display names. */
public final class LeaderboardService {

    private final RatingRepository ratings;
    private final AccountRepository accounts;

    public LeaderboardService(RatingRepository ratings, AccountRepository accounts) {
        this.ratings = ratings;
        this.accounts = accounts;
    }

    public List<LeaderboardEntry> global(String variant, String speed, int limit) {
        return decorate(ratings.top(variant, speed, limit));
    }

    public List<LeaderboardEntry> among(List<String> accountIds, String variant, String speed) {
        return decorate(ratings.among(accountIds, variant, speed));
    }

    public List<PoolRating> pools(String accountId) {
        return ratings.forAccount(accountId);
    }

    public List<RatingHistoryPoint> history(String accountId, String variant, String speed) {
        return ratings.history(accountId, variant, speed, 500);
    }

    private List<LeaderboardEntry> decorate(List<RatingRow> rows) {
        Map<String, String> names = accounts.byIds(rows.stream().map(RatingRow::accountId).toList()).stream()
                .collect(Collectors.toMap(Account::id, Account::username));
        return rows.stream()
                .map(r -> new LeaderboardEntry(r.accountId(), names.getOrDefault(r.accountId(), "unknown"),
                        (int) Math.round(r.rating().rating()), r.games()))
                .toList();
    }
}
