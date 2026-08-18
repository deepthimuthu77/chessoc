package com.chessoc.server.matchmaking;

import com.chessoc.server.domain.Variant;
import com.chessoc.server.match.TimeControl;

/** Two paired players with assigned colours, ready to become a game. */
public record Pairing(String whiteId, String blackId, Variant variant, TimeControl timeControl, boolean rated) {
}
