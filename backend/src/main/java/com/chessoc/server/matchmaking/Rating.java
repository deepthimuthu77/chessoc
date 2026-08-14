package com.chessoc.server.matchmaking;

/** A Glicko-2 rating: strength, deviation (uncertainty), and volatility. Tracked per variant × speed. */
public record Rating(double rating, double deviation, double volatility) {

    public static Rating provisional() {
        return new Rating(1500.0, 350.0, 0.06);
    }
}
