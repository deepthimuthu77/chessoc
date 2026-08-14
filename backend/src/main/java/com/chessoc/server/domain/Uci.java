package com.chessoc.server.domain;

import java.util.regex.Pattern;

/** A move in UCI form: normal ({@code e2e4}), promotion ({@code e7e8q}), or drop ({@code P@e4}). */
public record Uci(String value) {

    private static final Pattern NORMAL = Pattern.compile("[a-h][1-8][a-h][1-8][qrbn]?");
    private static final Pattern DROP = Pattern.compile("[PNBRQ]@[a-h][1-8]");

    public Uci {
        if (value == null || !(NORMAL.matcher(value).matches() || DROP.matcher(value).matches())) {
            throw new IllegalArgumentException("malformed UCI: " + value);
        }
    }

    public static Uci of(String value) {
        return new Uci(value);
    }

    public boolean isDrop() {
        return value.charAt(1) == '@';
    }

    public boolean isPromotion() {
        return !isDrop() && value.length() == 5;
    }

    @Override
    public String toString() {
        return value;
    }
}
