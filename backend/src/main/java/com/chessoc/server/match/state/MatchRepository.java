package com.chessoc.server.match.state;

import java.util.List;

/** Persistence of committed game state. The only module that touches durable storage (§4.3). */
public interface MatchRepository {

    void append(String gameId, PersistedPly ply);

    List<PersistedPly> plies(String gameId);
}
