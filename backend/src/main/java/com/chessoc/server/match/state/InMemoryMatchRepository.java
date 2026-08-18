package com.chessoc.server.match.state;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory repository for dev and tests; the JDBC/Postgres implementation slots in behind the same API. */
public final class InMemoryMatchRepository implements MatchRepository {

    private final Map<String, List<PersistedPly>> byGame = new ConcurrentHashMap<>();

    @Override
    public void append(String gameId, PersistedPly ply) {
        byGame.computeIfAbsent(gameId, k -> new CopyOnWriteArrayList<>()).add(ply);
    }

    @Override
    public List<PersistedPly> plies(String gameId) {
        return List.copyOf(byGame.getOrDefault(gameId, List.of()));
    }
}
