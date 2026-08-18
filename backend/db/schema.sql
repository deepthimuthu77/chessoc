-- Match State persistence. The application also creates this table on startup (idempotent);
-- this script seeds it when Postgres initialises a fresh volume.
CREATE TABLE IF NOT EXISTS match_plies (
    game_id  TEXT   NOT NULL,
    version  BIGINT NOT NULL,
    fen      TEXT   NOT NULL,
    uci      TEXT   NOT NULL,
    PRIMARY KEY (game_id, version)
);
