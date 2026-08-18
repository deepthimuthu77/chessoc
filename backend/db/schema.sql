-- Match State persistence and the account/social tables. The application also creates these on
-- startup (idempotent); this script seeds them when Postgres initialises a fresh volume.

CREATE TABLE IF NOT EXISTS match_plies (
    game_id  TEXT   NOT NULL,
    version  BIGINT NOT NULL,
    fen      TEXT   NOT NULL,
    uci      TEXT   NOT NULL,
    PRIMARY KEY (game_id, version)
);

CREATE TABLE IF NOT EXISTS accounts (
    id            TEXT   PRIMARY KEY,
    username      TEXT   NOT NULL UNIQUE,
    email         TEXT   NOT NULL UNIQUE,
    password_hash TEXT   NOT NULL,
    created_at    BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS ratings (
    account_id TEXT             NOT NULL,
    variant    TEXT             NOT NULL,
    speed      TEXT             NOT NULL,
    rating     DOUBLE PRECISION NOT NULL,
    deviation  DOUBLE PRECISION NOT NULL,
    volatility DOUBLE PRECISION NOT NULL,
    games      INTEGER          NOT NULL,
    PRIMARY KEY (account_id, variant, speed)
);

CREATE TABLE IF NOT EXISTS friends (
    account_id TEXT NOT NULL,
    friend_id  TEXT NOT NULL,
    PRIMARY KEY (account_id, friend_id)
);

CREATE TABLE IF NOT EXISTS games (
    id                TEXT    PRIMARY KEY,
    white_id          TEXT    NOT NULL,
    black_id          TEXT,
    variant           TEXT    NOT NULL,
    speed             TEXT    NOT NULL,
    rated             BOOLEAN NOT NULL,
    result_type       TEXT    NOT NULL,
    winner_account_id TEXT,
    reason            TEXT,
    ended_at          BIGINT  NOT NULL
);
