package com.chessoc.server.match.state;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Variant;
import com.chessoc.server.match.TimeControl;
import com.chessoc.server.protocol.GameResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

/** One four-seat bughouse room with two boards serialized by the same match lock. */
public final class BughouseMatch {

    public enum SeatPosition {
        A_WHITE("aw", "A", Side.WHITE, Side.BLACK, "blue"),
        A_BLACK("ab", "A", Side.BLACK, Side.WHITE, "red"),
        B_WHITE("bw", "B", Side.WHITE, Side.BLACK, "red"),
        B_BLACK("bb", "B", Side.BLACK, Side.WHITE, "blue");

        private final String code;
        private final String board;
        private final Side side;
        private final Side partnerSide;
        private final String team;

        SeatPosition(String code, String board, Side side, Side partnerSide, String team) {
            this.code = code;
            this.board = board;
            this.side = side;
            this.partnerSide = partnerSide;
            this.team = team;
        }

        public String code() {
            return code;
        }

        public static SeatPosition fromCode(String code) {
            return java.util.Arrays.stream(values())
                    .filter(seat -> seat.code.equalsIgnoreCase(code))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("unknown bughouse seat: " + code));
        }
    }

    public record Seat(String roomId, String gameId, String partnerGameId, String board,
                       String color, String partnerColor, String team, String seat,
                       int seatsFilled, int humansJoined, int humanTarget,
                       List<String> humanSeats, List<String> availableSeats) {
    }

    private record Slot(MatchGame game, MatchGame partner, SeatPosition position) {
    }

    private final String roomId;
    private final MatchGame boardA;
    private final MatchGame boardB;
    private final Map<String, Slot> slots = new LinkedHashMap<>();
    private final BiConsumer<MatchGame, GameResult> completion;
    private final Set<String> humanIds = new java.util.HashSet<>();
    private final Set<SeatPosition> humanSeats;
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
        this(roomId, matches, start, timeControl, creatorId, creatorSide,
                defaultHumanSeats(creatorSide, humanTarget), botMover, completion);
    }

    public BughouseMatch(String roomId, MatchService matches, Fen start, TimeControl timeControl,
                         String creatorId, Side creatorSide, Set<String> humanSeatCodes,
                         MatchGame.AiMover botMover, BiConsumer<MatchGame, GameResult> completion) {
        SeatPosition creatorSeat = creatorSide == Side.WHITE ? SeatPosition.A_WHITE : SeatPosition.A_BLACK;
        this.humanSeats = parseHumanSeats(humanSeatCodes, creatorSeat);
        this.humanTarget = this.humanSeats.size();
        if (humanTarget < 4 && botMover == null) {
            throw new IllegalArgumentException("AI seats require a bughouse mover");
        }
        this.roomId = roomId;
        this.completion = completion;
        this.botMover = botMover;
        ReentrantLock matchLock = new ReentrantLock();
        boardA = matches.createHeld(new GameSpec(roomId + "-a", Variant.BUGHOUSE, start,
                null, null, timeControl, false), matchLock);
        boardB = matches.createHeld(new GameSpec(roomId + "-b", Variant.BUGHOUSE, start,
                null, null, timeControl, false), matchLock);
        wireBoards();
        claimAt(creatorId, creatorSeat, false);
        humanIds.add(creatorId);
        fillConfiguredBots();
        startIfReady();
    }

    private BughouseMatch(LiveRoomState state, MatchService matches, TimeControl timeControl,
                          MatchGame.AiMover botMover, BiConsumer<MatchGame, GameResult> completion) {
        if (state.boards().size() != 2) {
            throw new IllegalArgumentException("bughouse recovery needs two boards");
        }
        this.roomId = state.roomId();
        this.completion = completion;
        this.botMover = botMover;
        this.humanSeats = parseHumanSeats(Set.copyOf(state.humanSeats()), null);
        this.humanTarget = state.humanTarget();
        LiveRoomState.BoardState a = state.boards().stream()
                .filter(board -> board.gameId().endsWith("-a")).findFirst().orElseThrow();
        LiveRoomState.BoardState b = state.boards().stream()
                .filter(board -> board.gameId().endsWith("-b")).findFirst().orElseThrow();
        ReentrantLock matchLock = new ReentrantLock();
        boardA = matches.restoreHeld(spec(a, timeControl, state.rated()), matchLock, a, state.savedAtEpochMs());
        boardB = matches.restoreHeld(spec(b, timeControl, state.rated()), matchLock, b, state.savedAtEpochMs());
        wireBoards();
        rebuildSlot(a.whiteId(), SeatPosition.A_WHITE);
        rebuildSlot(a.blackId(), SeatPosition.A_BLACK);
        rebuildSlot(b.whiteId(), SeatPosition.B_WHITE);
        rebuildSlot(b.blackId(), SeatPosition.B_BLACK);
    }

    public static BughouseMatch restore(LiveRoomState state, MatchService matches, TimeControl timeControl,
                                         MatchGame.AiMover botMover,
                                         BiConsumer<MatchGame, GameResult> completion) {
        return new BughouseMatch(state, matches, timeControl, botMover, completion);
    }

    public synchronized Seat join(String playerId) {
        return join(playerId, null);
    }

    public synchronized Seat join(String playerId, String requestedSeat) {
        Slot existing = slots.get(playerId);
        if (existing != null) {
            return seat(existing);
        }
        boolean exactSeat = requestedSeat != null && !requestedSeat.isBlank();
        SeatPosition position = exactSeat ? SeatPosition.fromCode(requestedSeat) : null;
        if (exactSeat && !humanSeats.contains(position)) {
            throw new IllegalArgumentException("seat " + position.code + " is configured for AI");
        }
        if (exactSeat && seatOccupied(position)) {
            throw new IllegalArgumentException("seat " + position.code + " is already occupied");
        }
        if (humanIds.size() >= humanTarget) {
            return spectatorSeat();
        }
        if (!exactSeat) {
            position = firstAvailableHumanSeat();
        }
        if (!humanSeats.contains(position)) {
            throw new IllegalArgumentException("seat " + position.code + " is configured for AI");
        }
        if (seatOccupied(position)) {
            throw new IllegalArgumentException("seat " + position.code + " is already occupied");
        }
        claimAt(playerId, position, false);
        humanIds.add(playerId);
        startIfReady();
        return seat(slots.get(playerId));
    }

    public void onStateChange(Runnable listener) {
        boardA.onStateChange(listener);
        boardB.onStateChange(listener);
    }

    public void resumeRecovered() {
        boardA.resumeRecovered();
        boardB.resumeRecovered();
    }

    public LiveRoomState recoveryState(long now) {
        TimeControl tc = boardA.timeControl();
        return new LiveRoomState(roomId, "bughouse", tc.baseMs(), tc.incrementMs(),
                tc.delayType().name(), tc.delayMs(), false, humanTarget, humanSeatCodes(), now,
                List.of(boardA.recoveryState(), boardB.recoveryState()));
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

    public synchronized List<String> humanSeatCodes() {
        return humanSeats.stream().sorted(Comparator.comparingInt(Enum::ordinal))
                .map(SeatPosition::code).toList();
    }

    public synchronized List<String> availableHumanSeatCodes() {
        return humanSeats.stream().filter(seat -> !seatOccupied(seat))
                .sorted(Comparator.comparingInt(Enum::ordinal)).map(SeatPosition::code).toList();
    }

    public synchronized boolean isParticipant(String playerId) {
        return slots.containsKey(playerId);
    }

    public boolean containsGame(String gameId) {
        return boardA.gameId().equals(gameId) || boardB.gameId().equals(gameId);
    }

    private void wireBoards() {
        boardA.onCapture((mover, piece) -> boardB.injectReserve(mover.opponent(), piece));
        boardB.onCapture((mover, piece) -> boardA.injectReserve(mover.opponent(), piece));
        boardA.onComplete(result -> complete(boardA, boardB, result));
        boardB.onComplete(result -> complete(boardB, boardA, result));
    }

    private void rebuildSlot(String playerId, SeatPosition position) {
        if (playerId == null || playerId.isBlank()) {
            return;
        }
        slots.put(playerId, slot(position));
        if (playerId.startsWith("engine-")) {
            game(position).setAi(position.side, botMover);
        } else {
            humanIds.add(playerId);
        }
    }

    private void claimAt(String playerId, SeatPosition position, boolean ai) {
        MatchGame game = game(position);
        game.assignSeat(position.side, playerId);
        slots.put(playerId, slot(position));
        if (ai) {
            game.setAi(position.side, botMover);
        }
    }

    private Slot slot(SeatPosition position) {
        return new Slot(game(position), partner(position), position);
    }

    private MatchGame game(SeatPosition position) {
        return "A".equals(position.board) ? boardA : boardB;
    }

    private MatchGame partner(SeatPosition position) {
        return "A".equals(position.board) ? boardB : boardA;
    }

    private void fillConfiguredBots() {
        for (SeatPosition position : SeatPosition.values()) {
            if (!humanSeats.contains(position)) {
                claimAt("engine-" + position.code, position, true);
            }
        }
    }

    private void startIfReady() {
        if (slots.size() == 4) {
            boardA.start();
            boardB.start();
        }
    }

    private SeatPosition firstAvailableHumanSeat() {
        return humanSeats.stream().filter(seat -> !seatOccupied(seat))
                .min(Comparator.comparingInt(Enum::ordinal))
                .orElseThrow(() -> new IllegalStateException("bughouse room has no open human seat"));
    }

    private boolean seatOccupied(SeatPosition position) {
        return slots.values().stream().anyMatch(slot -> slot.position == position);
    }

    private Seat seat(Slot slot) {
        SeatPosition position = slot.position;
        return new Seat(roomId, slot.game.gameId(), slot.partner.gameId(), position.board,
                position.side.name().toLowerCase(), position.partnerSide.name().toLowerCase(),
                position.team, position.code, slots.size(), humanIds.size(), humanTarget,
                humanSeatCodes(), availableHumanSeatCodes());
    }

    private Seat spectatorSeat() {
        return new Seat(roomId, boardA.gameId(), boardB.gameId(), "spectator", "spectator",
                "spectator", "spectator", "spectator", slots.size(), humanIds.size(), humanTarget,
                humanSeatCodes(), availableHumanSeatCodes());
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

    private static GameSpec spec(LiveRoomState.BoardState board, TimeControl timeControl, boolean rated) {
        return new GameSpec(board.gameId(), Variant.BUGHOUSE, Fen.parse(board.initialFen()),
                board.whiteId(), board.blackId(), timeControl, rated);
    }

    private static Set<String> defaultHumanSeats(Side creatorSide, int humanTarget) {
        if (humanTarget < 1 || humanTarget > 4) {
            throw new IllegalArgumentException("bughouse human seats must be between 1 and 4");
        }
        SeatPosition creator = creatorSide == Side.WHITE ? SeatPosition.A_WHITE : SeatPosition.A_BLACK;
        List<SeatPosition> ordered = new ArrayList<>(List.of(SeatPosition.values()));
        ordered.remove(creator);
        ordered.add(0, creator);
        return ordered.stream().limit(humanTarget).map(SeatPosition::code)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private static Set<SeatPosition> parseHumanSeats(Set<String> codes, SeatPosition required) {
        if (codes == null || codes.isEmpty() || codes.size() > 4) {
            throw new IllegalArgumentException("select between one and four human seats");
        }
        Set<SeatPosition> seats = EnumSet.noneOf(SeatPosition.class);
        codes.forEach(code -> seats.add(SeatPosition.fromCode(code)));
        if (required != null && !seats.contains(required)) {
            throw new IllegalArgumentException("creator seat must be human");
        }
        return seats;
    }
}
