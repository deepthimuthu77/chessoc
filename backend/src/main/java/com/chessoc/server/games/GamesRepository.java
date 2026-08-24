package com.chessoc.server.games;

import java.util.List;
import java.util.Optional;

/** Persistence of finished-game metadata (history, profile stats). */
public interface GamesRepository {

    void record(GameRecord game);

    List<GameRecord> forAccount(String accountId, int limit);

    Optional<GameRecord> find(String gameId);

    PlayerStats statsFor(String accountId);

    /** Move a player's game rows to a new id (guest → account upgrade). */
    void reassign(String oldId, String newId);
}
