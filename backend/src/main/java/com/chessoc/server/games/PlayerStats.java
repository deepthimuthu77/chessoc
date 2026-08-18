package com.chessoc.server.games;

/** Win/loss/draw tallies for a profile. */
public record PlayerStats(int wins, int losses, int draws) {

    public int games() {
        return wins + losses + draws;
    }
}
