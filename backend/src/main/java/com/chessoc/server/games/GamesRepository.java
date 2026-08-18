package com.chessoc.server.games;

import java.util.List;

/** Persistence of finished-game metadata (history, profile stats). */
public interface GamesRepository {

    void record(GameRecord game);

    List<GameRecord> forAccount(String accountId, int limit);

    PlayerStats statsFor(String accountId);
}
