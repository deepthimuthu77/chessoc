package com.chessoc.server.match.state;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Variant;
import com.chessoc.server.match.TimeControl;
import com.chessoc.server.protocol.GameResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

/** One four-player bughouse room with two boards serialized by the same match lock. */
public final class BughouseMatch {

    public record Seat(String roomId, String gameId, String partnerGameId, String board,
                       String color, String partnerColor, String team, int seatsFilled,
                       int humansJoined, int humanTarget) {
    }

    private record Slot(MatchGame game, Side side, MatchGame partner, Side partnerSide,
                        String board, String team) {
    }

    private final String roomId;
    private final MatchGame boardA;
    private final MatchGame boardB;
    private final Map<String, Slot> slots = new LinkedHashMap<>();
    private final BiConsumer<MatchGame, GameResult> completion;
    private final Set<String> humanIds = new HashSet<>();
    private final int humanTarget;
    private final MatchGame.AiMover botMover;
    private boolean ended;

    public BughouseMatch(String roomId, MatchService matches, Fen start, TimeControl timeControl,
                         String creatorId, BiConsumer<MatchGame, GameResult> completion) {
        this(roomId, matches, start, timeControl, creatorId, Side.WHITE, 4, null, completion);
    }

    public BughouseMatch(String roomId, MatchService matches, Fen start, TimeControl timeControl,
                         String creatorId, Side creatorSide, int humanTarget, MatchGame.AiMover botMover,
                         BiConsumer<MatchGame, GameResult> completion) {
        if (humanTarget < 1 || humanTarget > 4 || (humanTarget < 4 && botMover == null)) {
            throw new IllegalArgumentException("bughouse human seats must be between 1 and 4");
        }
        this.roomId = roomId;
        this.completion = completion;
        this.humanTarget = humanTarget;
        this.botMover = botMover;
        ReentrantLock matchLock = new ReentrantLock();
        boardA = matches.createHeld(new GameSpec(roomId + "-a", Variant.BUGHOUSE, start,
                null, null, timeControl, false), matchLock);
        boardB = matches.createHeld(new GameSpec(roomId + "-b", Variant.BUGHOUSE, start,
                null, null, timeControl, false), matchLock);
        boardA.onCapture((mover, piece) -> boardB.injectReserve(mover.opponent(), piece));
        boardB.onCapture((mover, piece) -> boardA.injectReserve(mover.opponent(), piece));
        boardA.onComplete(result -> complete(boardA, boardB, result));
        boardB.onComplete(result -> complete(boardB, boardA, result));
        if (creatorSide == Side.WHITE) {
            claim(creatorId, boardA, Side.WHITE, boardB, Side.BLACK, "A", "blue");
        } else {
            claim(creatorId, boardA, Side.BLACK, boardB, Side.WHITE, "A", "red");
        }
        humanIds.add(creatorId);
        fillBotsAndStartIfReady();
    }

    public synchronized Seat join(String playerId) {
        Slot existing = slots.get(playerId);
        if (existing != null) {
            return seat(playerId, existing);
        }
        if (humanIds.size() >= humanTarget || slots.size() >= 4) {
            return new Seat(roomId, boardA.gameId(), boardB.gameId(), "spectator",
                    "spectator", "spectator", "spectator", slots.size(),
                    humanIds.size(), humanTarget);
        }
        claimNext(playerId, false, null);
        humanIds.add(playerId);
        fillBotsAndStartIfReady();
        return seat(playerId, slots.get(playerId));
    }

    public String roomId() {
        return roomId;
    }

    public MatchGame boardA() {
        return boardA;
    }

    public MatchGame boardB() {
        return boardB;
    }

    public synchronized int seatsFilled() {
        return slots.size();
    }

    public synchronized int humansJoined() {
        return humanIds.size();
    }

    public int humanTarget() {
        return humanTarget;
    }

    public synchronized boolean isParticipant(String playerId) {
        return slots.containsKey(playerId);
    }

    public boolean containsGame(String gameId) {
        return boardA.gameId().equals(gameId) || boardB.gameId().equals(gameId);
    }

    private void claim(String playerId, MatchGame game, Side side, MatchGame partner,
                       Side partnerSide, String board, String team) {
        game.assignSeat(side, playerId);
        slots.put(playerId, new Slot(game, side, partner, partnerSide, board, team));
    }

    private Seat seat(String playerId, Slot slot) {
        return new Seat(roomId, slot.game().gameId(), slot.partner().gameId(), slot.board(),
                slot.side().name().toLowerCase(), slot.partnerSide().name().toLowerCase(),
                slot.team(), slots.size(), humanIds.size(), humanTarget);
    }

    private void fillBotsAndStartIfReady() {
        if (humanIds.size() < humanTarget) {
            return;
        }
        while (slots.size() < 4) {
            claimNext("engine-" + slots.size(), true, botMover);
        }
        boardA.start();
        boardB.start();
    }

    private void claimNext(String playerId, boolean ai, MatchGame.AiMover mover) {
        if (boardA.whiteId() == null) {
            claim(playerId, boardA, Side.WHITE, boardB, Side.BLACK, "A", "blue");
            if (ai) boardA.setAi(Side.WHITE, mover);
        } else if (boardA.blackId() == null) {
            claim(playerId, boardA, Side.BLACK, boardB, Side.WHITE, "A", "red");
            if (ai) boardA.setAi(Side.BLACK, mover);
        } else if (boardB.whiteId() == null) {
            claim(playerId, boardB, Side.WHITE, boardA, Side.BLACK, "B", "red");
            if (ai) boardB.setAi(Side.WHITE, mover);
        } else if (boardB.blackId() == null) {
            claim(playerId, boardB, Side.BLACK, boardA, Side.WHITE, "B", "blue");
            if (ai) boardB.setAi(Side.BLACK, mover);
        } else {
            throw new IllegalStateException("bughouse room is full");
        }
    }

    private synchronized void complete(MatchGame source, MatchGame partner, GameResult result) {
        if (ended) {
            return;
        }
        ended = true;
        GameResult partnerResult = result.type() == GameResult.ResultType.WIN
                ? new GameResult(GameResult.ResultType.WIN,
                        Optional.of(result.winner().orElseThrow().opponent()), "partner_" + result.reason())
                : new GameResult(result.type(), Optional.empty(), "partner_" + result.reason());
        partner.finishFromPartner(partnerResult);
        completion.accept(source, result);
        completion.accept(partner, partnerResult);
    }
}
