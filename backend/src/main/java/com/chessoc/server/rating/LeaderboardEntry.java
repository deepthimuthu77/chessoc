package com.chessoc.server.rating;

/** A leaderboard line: display name + rating for a pool. */
public record LeaderboardEntry(String accountId, String username, int rating, int games) {
}
