package com.chessoc.server.matchmaking;

import java.util.List;

/**
 * Glicko-2 rating update (Glickman). Ratings map to the internal (μ, φ) scale, are updated against a
 * rating period's games, and map back. τ constrains volatility change.
 */
public final class Glicko2 {

    private static final double SCALE = 173.7178;
    private static final double CONVERGENCE = 1e-6;

    private final double tau;

    public Glicko2() {
        this(0.5);
    }

    public Glicko2(double tau) {
        this.tau = tau;
    }

    /** One opponent faced in the rating period and the score against them (1 win, 0.5 draw, 0 loss). */
    public record Game(Rating opponent, double score) {
    }

    public Rating update(Rating player, List<Game> games) {
        if (games.isEmpty()) {
            return skippedPeriod(player);
        }

        double mu = (player.rating() - 1500.0) / SCALE;
        double phi = player.deviation() / SCALE;

        double vInv = 0.0;
        double deltaSum = 0.0;
        for (Game game : games) {
            double muJ = (game.opponent().rating() - 1500.0) / SCALE;
            double phiJ = game.opponent().deviation() / SCALE;
            double g = g(phiJ);
            double e = e(mu, muJ, g);
            vInv += g * g * e * (1 - e);
            deltaSum += g * (game.score() - e);
        }
        double v = 1.0 / vInv;
        double delta = v * deltaSum;

        double newVolatility = newVolatility(phi, v, delta, player.volatility());
        double phiStar = Math.sqrt(phi * phi + newVolatility * newVolatility);
        double newPhi = 1.0 / Math.sqrt(1.0 / (phiStar * phiStar) + 1.0 / v);
        double newMu = mu + newPhi * newPhi * deltaSum;

        return new Rating(newMu * SCALE + 1500.0, newPhi * SCALE, newVolatility);
    }

    /** With no games, only uncertainty grows: φ' = sqrt(φ² + σ²). */
    private Rating skippedPeriod(Rating player) {
        double phi = player.deviation() / SCALE;
        double phiStar = Math.sqrt(phi * phi + player.volatility() * player.volatility());
        return new Rating(player.rating(), phiStar * SCALE, player.volatility());
    }

    private static double g(double phi) {
        return 1.0 / Math.sqrt(1.0 + 3.0 * phi * phi / (Math.PI * Math.PI));
    }

    private static double e(double mu, double muJ, double g) {
        return 1.0 / (1.0 + Math.exp(-g * (mu - muJ)));
    }

    /** Illinois-algorithm root find for the new volatility σ'. */
    private double newVolatility(double phi, double v, double delta, double volatility) {
        double a = Math.log(volatility * volatility);
        double d2 = delta * delta;
        double phi2 = phi * phi;

        double big = a;
        double small;
        if (d2 > phi2 + v) {
            small = Math.log(d2 - phi2 - v);
        } else {
            int k = 1;
            while (f(a - k * tau, d2, phi2, v, a) < 0) {
                k++;
            }
            small = a - k * tau;
        }

        double fBig = f(big, d2, phi2, v, a);
        double fSmall = f(small, d2, phi2, v, a);
        while (Math.abs(big - small) > CONVERGENCE) {
            double mid = small + (small - big) * fSmall / (fBig - fSmall);
            double fMid = f(mid, d2, phi2, v, a);
            if (fMid * fSmall < 0) {
                big = small;
                fBig = fSmall;
            } else {
                fBig /= 2.0;
            }
            small = mid;
            fSmall = fMid;
        }
        return Math.exp(small / 2.0);
    }

    private double f(double x, double d2, double phi2, double v, double a) {
        double ex = Math.exp(x);
        double num = ex * (d2 - phi2 - v - ex);
        double den = 2.0 * (phi2 + v + ex) * (phi2 + v + ex);
        return num / den - (x - a) / (tau * tau);
    }
}
