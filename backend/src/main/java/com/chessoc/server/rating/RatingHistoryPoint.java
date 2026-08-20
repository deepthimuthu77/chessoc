package com.chessoc.server.rating;

/** One post-game rating sample in a pool, for the profile history chart. */
public record RatingHistoryPoint(long at, int rating) {
}
