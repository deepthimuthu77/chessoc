package com.chessoc.server.account;

/** A registered user. {@code id} is the stable identity used everywhere else (player, rating, friend). */
public record Account(String id, String username, String email, String passwordHash, long createdAtEpochMs) {
}
