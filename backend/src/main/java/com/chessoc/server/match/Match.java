package com.chessoc.server.match;

import com.chessoc.server.domain.Variant;
import java.util.List;

/**
 * The top-level unit of persistence, sharding, and session affinity: 1..N boards and 2 or 4 players
 * (2 boards for bughouse). Cross-board events stay in-process on the owning node.
 */
public record Match(String id, Variant variant, List<Board> boards, List<String> playerIds, boolean rated) {

    public Match {
        boards = List.copyOf(boards);
        playerIds = List.copyOf(playerIds);
    }

    public boolean isBughouse() {
        return variant == Variant.BUGHOUSE;
    }
}
