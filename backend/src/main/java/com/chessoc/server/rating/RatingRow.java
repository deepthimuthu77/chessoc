package com.chessoc.server.rating;

import com.chessoc.server.matchmaking.Rating;

/** An account's rating in one (variant, speed) pool, with games played. */
public record RatingRow(String accountId, Rating rating, int games) {
}
