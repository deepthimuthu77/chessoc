package com.chessoc.server.match.state;

import java.util.List;

/** Durable checkpoint for one standard, crazyhouse, or two-board bughouse room. */
public record LiveRoomState(
        String roomId,
        String variant,
        long baseMs,
        long incrementMs,
        String delayType,
        long delayMs,
        boolean rated,
        int humanTarget,
        List<String> humanSeats,
        long savedAtEpochMs,
        List<BoardState> boards,
        String aiLevel) {

    public LiveRoomState(String roomId, String variant, long baseMs, long incrementMs, String delayType,
                         long delayMs, boolean rated, int humanTarget, List<String> humanSeats,
                         long savedAtEpochMs, List<BoardState> boards) {
        this(roomId, variant, baseMs, incrementMs, delayType, delayMs, rated, humanTarget, humanSeats,
                savedAtEpochMs, boards, "medium");
    }

    public record BoardState(
            String gameId,
            String initialFen,
            String currentFen,
            String whiteId,
            String blackId,
            long version,
            List<String> moves,
            long whiteRemainingMs,
            long blackRemainingMs,
            boolean started,
            boolean inCheck,
            String pendingDrawOfferBy,
            List<String> capturedByWhite,
            List<String> capturedByBlack) {
    }
}
