package com.chessoc.server.rating;

/** An account's rating in one pool, for a profile breakdown. */
public record PoolRating(String variant, String speed, int rating, int games) {
}
