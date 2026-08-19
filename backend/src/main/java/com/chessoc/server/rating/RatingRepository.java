package com.chessoc.server.rating;

import java.util.List;

/** Persistence of per-(account × variant × speed) ratings. */
public interface RatingRepository {

    /** The account's standing in a pool, provisional if never rated. */
    RatingRow get(String accountId, String variant, String speed);

    void save(String accountId, String variant, String speed, RatingRow row);

    /** Highest-rated accounts in a pool. */
    List<RatingRow> top(String variant, String speed, int limit);

    /** Ratings for a specific set of accounts in a pool (friends board). */
    List<RatingRow> among(List<String> accountIds, String variant, String speed);

    /** All pools an account has a rating in (profile breakdown). */
    List<PoolRating> forAccount(String accountId);
}
