package com.chessoc.server.games;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory game metadata for dev and tests. */
public final class InMemoryGamesRepository implements GamesRepository {

    private final List<GameRecord> games = new CopyOnWriteArrayList<>();

    @Override
    public void record(GameRecord game) {
        games.add(game);
    }

    @Override
    public List<GameRecord> forAccount(String accountId, int limit) {
        return games.stream()
                .filter(g -> g.whiteId().equals(accountId) || accountId.equals(g.blackId()))
                .sorted(Comparator.comparingLong(GameRecord::endedAtEpochMs).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<GameRecord> find(String gameId) {
        return games.stream().filter(g -> g.id().equals(gameId)).findFirst();
    }

    @Override
    public void reassign(String oldId, String newId) {
        for (int i = 0; i < games.size(); i++) {
            GameRecord g = games.get(i);
            if (!g.whiteId().equals(oldId) && !oldId.equals(g.blackId()) && !oldId.equals(g.winnerAccountId())) {
                continue;
            }
            games.set(i, new GameRecord(g.id(),
                    g.whiteId().equals(oldId) ? newId : g.whiteId(),
                    oldId.equals(g.blackId()) ? newId : g.blackId(),
                    g.variant(), g.speed(), g.rated(), g.resultType(),
                    oldId.equals(g.winnerAccountId()) ? newId : g.winnerAccountId(),
                    g.reason(), g.endedAtEpochMs()));
        }
    }

    @Override
    public PlayerStats statsFor(String accountId) {
        int wins = 0;
        int losses = 0;
        int draws = 0;
        for (GameRecord g : games) {
            if (!g.whiteId().equals(accountId) && !accountId.equals(g.blackId())) {
                continue;
            }
            if ("DRAW".equals(g.resultType())) {
                draws++;
            } else if (accountId.equals(g.winnerAccountId())) {
                wins++;
            } else if ("WIN".equals(g.resultType())) {
                losses++;
            }
        }
        return new PlayerStats(wins, losses, draws);
    }
}
