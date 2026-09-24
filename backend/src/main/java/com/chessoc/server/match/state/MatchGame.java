package com.chessoc.server.match.state;

import com.chessoc.server.capability.Capability;
import com.chessoc.server.capability.CapabilityKind;
import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.PieceType;
import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Uci;
import com.chessoc.server.domain.Variant;
import com.chessoc.server.engine.ApplyResult;
import com.chessoc.server.engine.ValidatorClient;
import com.chessoc.server.match.Clock;
import com.chessoc.server.match.Reserve;
import com.chessoc.server.match.TimeControl;
import com.chessoc.server.protocol.ClockSample;
import com.chessoc.server.protocol.Disposition;
import com.chessoc.server.protocol.GameResult;
import com.chessoc.server.protocol.GameSnapshot;
import com.chessoc.server.protocol.MessageType;
import com.chessoc.server.protocol.Messages;
import com.chessoc.server.session.Session;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * One authoritative game, driven by a single writer (serialized by a lock — the mailbox/thread variant
 * is interchangeable). It runs the pre-commit gate, owns the clocks, issues capabilities from engine
 * legal moves, and never applies a move itself: it commits exactly what the engine returns.
 */
public final class MatchGame {

    // Capabilities are version-bound (replaced on every new snapshot), so a generous expiry is safe;
    // it must outlast a player's think time.
    private static final long CAPABILITY_TTL_MS = 3_600_000;
    private static final long AI_MOVE_DELAY_MS = 400;

    private final ValidatorClient engine;
    private final MatchRepository repository;
    private final Scheduler scheduler;
    private final LongSupplier clock;
    private final Supplier<String> ids;

    private final String gameId;
    private final Variant variant;
    private final Fen initialFen;
    private String whiteId;
    private String blackId;

    private final ReentrantLock lock;
    private final List<Uci> moves = new ArrayList<>();
    private final EnumMap<Side, Clock> clocks = new EnumMap<>(Side.class);
    private final Map<String, Subscriber> subscribers = new LinkedHashMap<>();
    private final Map<String, Capability> currentCaps = new HashMap<>();
    private final Map<String, Messages.Result> resultByIntent = new HashMap<>();
    private final List<String> capturedByWhite = new ArrayList<>();
    private final List<String> capturedByBlack = new ArrayList<>();
    private final java.util.Set<String> rematchRequests = new java.util.HashSet<>();
    private RematchFactory rematchFactory;
    private final EnumMap<Side, AiMover> aiMovers = new EnumMap<>(Side.class);
    private java.util.function.BiConsumer<Side, PieceType> captureListener;

    private final boolean rated;
    private final TimeControl timeControl;
    private java.util.function.Consumer<GameResult> completionListener;

    private Fen currentFen;
    private long version;
    private long turnStartMs;
    private GameResult result;
    private boolean inCheck;
    private String pendingDrawOfferBy;
    private Scheduler.Cancellable flagTimer;
    private boolean startHeld;
    private boolean started;

    public MatchGame(GameSpec spec, ValidatorClient engine, MatchRepository repository,
                     Scheduler scheduler, LongSupplier clock, Supplier<String> ids) {
        this(spec, engine, repository, scheduler, clock, ids, new ReentrantLock(), false);
    }

    MatchGame(GameSpec spec, ValidatorClient engine, MatchRepository repository,
              Scheduler scheduler, LongSupplier clock, Supplier<String> ids,
              ReentrantLock lock, boolean startHeld) {
        this.engine = engine;
        this.repository = repository;
        this.scheduler = scheduler;
        this.clock = clock;
        this.ids = ids;
        this.lock = lock;
        this.startHeld = startHeld;
        this.gameId = spec.gameId();
        this.variant = spec.variant();
        this.initialFen = spec.initialFen();
        this.currentFen = spec.initialFen();
        this.whiteId = spec.whiteId();
        this.blackId = spec.blackId();
        this.rated = spec.rated();
        TimeControl tc = spec.timeControl();
        this.timeControl = tc;
        clocks.put(Side.WHITE, Clock.start(tc));
        clocks.put(Side.BLACK, Clock.start(tc));
        this.turnStartMs = clock.getAsLong();
        this.started = !startHeld && whiteId != null && blackId != null;
        if (started) {
            rescheduleFlag();
        }
    }

    public String gameId() {
        return gameId;
    }

    public boolean isOver() {
        return result != null;
    }

    public String whiteId() {
        return whiteId;
    }

    public String blackId() {
        return blackId;
    }

    public Variant variant() {
        return variant;
    }

    public TimeControl timeControl() {
        return timeControl;
    }

    public boolean rated() {
        return rated;
    }

    /** Fired once when the game ends (terminal, resign, draw, timeout). */
    public void onComplete(java.util.function.Consumer<GameResult> listener) {
        this.completionListener = listener;
    }

    public void onCapture(java.util.function.BiConsumer<Side, PieceType> listener) {
        this.captureListener = listener;
    }

    /** Creates the rematch game (colours swapped) once both players accept. */
    public interface RematchFactory {
        String create(String whiteId, String blackId, Variant variant, TimeControl timeControl, boolean rated);
    }

    public void onRematch(RematchFactory factory) {
        this.rematchFactory = factory;
    }

    /** Picks the engine's reply for an AI game. */
    public interface AiMover {
        com.chessoc.server.domain.Uci bestMove(Variant variant, Fen position);
    }

    /** Mark one side as engine-controlled; the server plays its moves (and moves first if it's White). */
    public void setAi(Side side, AiMover mover) {
        lock.lock();
        try {
            aiMovers.put(side, mover);
            maybeScheduleAi();
        } finally {
            lock.unlock();
        }
    }

    /** Broadcast a chat line to all subscribers, tagged with the sender's colour. */
    public void chat(Session session, String text) {
        lock.lock();
        try {
            if (text == null || text.isBlank()) {
                return;
            }
            String body = text.strip();
            if (body.length() > 500) {
                body = body.substring(0, 500);
            }
            Side side = playerSide(session.playerId());
            String color = side == null ? "spectator" : side.name().toLowerCase();
            Messages.Chat message = new Messages.Chat(color, body, now());
            for (Subscriber sub : subscribers.values()) {
                sub.sink().send(MessageType.CHAT, message);
            }
        } finally {
            lock.unlock();
        }
    }

    /** Resolve the caller's colour, claiming the open Black seat on first distinct joiner. */
    public String seatFor(String playerId) {
        lock.lock();
        try {
            if (playerId.equals(whiteId)) {
                return "white";
            }
            if (whiteId == null) {
                whiteId = playerId;
                startIfReady();
                return "white";
            }
            if (blackId == null) {
                blackId = playerId;
                startIfReady();
                return "black";
            }
            return playerId.equals(blackId) ? "black" : "spectator";
        } finally {
            lock.unlock();
        }
    }

    public void assignSeat(Side side, String playerId) {
        lock.lock();
        try {
            String current = side == Side.WHITE ? whiteId : blackId;
            if (current != null && !current.equals(playerId)) {
                throw new IllegalStateException(side + " seat already occupied");
            }
            if (side == Side.WHITE) {
                whiteId = playerId;
            } else {
                blackId = playerId;
            }
            startIfReady();
            if (!started) {
                broadcastAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public void start() {
        lock.lock();
        try {
            startHeld = false;
            startIfReady();
        } finally {
            lock.unlock();
        }
    }

    public void injectReserve(Side recipient, PieceType piece) {
        lock.lock();
        try {
            if (variant != Variant.BUGHOUSE || result != null) {
                return;
            }
            Reserve reserve = Reserve.parse(currentFen.reserve().orElse(""));
            reserve.add(recipient, piece);
            currentFen = currentFen.withReserve(reserve.encode());
            version++;
            reissueAllCaps();
            broadcastAll();
        } finally {
            lock.unlock();
        }
    }

    public void finishFromPartner(GameResult partnerResult) {
        lock.lock();
        try {
            if (result != null) {
                return;
            }
            endGame(partnerResult);
            reissueAllCaps();
            broadcastAll();
        } finally {
            lock.unlock();
        }
    }

    public void subscribe(Session session, OutboundSink sink) {
        lock.lock();
        try {
            subscribers.put(session.playerId(), new Subscriber(session, sink));
            List<Capability> caps = ensureCapsFor(session.playerId());
            sink.send(MessageType.SNAPSHOT, new Messages.Snapshot(snapshot(), caps));
        } finally {
            lock.unlock();
        }
    }

    /** Subscribed viewers who are neither player (spectators currently connected). */
    public int spectatorCount() {
        lock.lock();
        try {
            return (int) subscribers.keySet().stream()
                    .filter(id -> !id.equals(whiteId) && !id.equals(blackId))
                    .count();
        } finally {
            lock.unlock();
        }
    }

    public void resync(String playerId, long lastKnownVersion) {
        lock.lock();
        try {
            Subscriber sub = subscribers.get(playerId);
            if (sub == null) {
                return;
            }
            List<Capability> caps = ensureCapsFor(playerId);
            sub.sink().send(MessageType.RESYNC_OK, new Messages.ResyncOk(snapshot(), caps));
        } finally {
            lock.unlock();
        }
    }

    public void submit(Session session, Messages.Intent intent) {
        lock.lock();
        try {
            Messages.Result cached = resultByIntent.get(intent.intentId());
            if (cached != null) {
                sendTo(session.playerId(), MessageType.RESULT, cached); // idempotent replay
                return;
            }
            sendTo(session.playerId(), MessageType.RECEIPT,
                    new Messages.Receipt(intent.intentId(), now()));

            Messages.Result outcome = evaluate(session, intent);
            resultByIntent.put(intent.intentId(), outcome);
            sendTo(session.playerId(), MessageType.RESULT, outcome);
        } finally {
            lock.unlock();
        }
    }

    /** Test/inspection hook — snapshot under the lock. */
    public GameSnapshot currentSnapshot() {
        lock.lock();
        try {
            return snapshot();
        } finally {
            lock.unlock();
        }
    }

    private Messages.Result evaluate(Session session, Messages.Intent intent) {
        Capability cap = currentCaps.get(intent.capabilityId());
        if (cap == null) {
            return reject(intent, "unknown_or_spent_capability");
        }
        if (!cap.sessionId().equals(session.sessionId())) {
            return reject(intent, "capability_session_mismatch");
        }
        if (!cap.boundToVersion(version)) {
            return stale(intent, session.playerId());
        }
        if (cap.isExpired(now())) {
            return reject(intent, "capability_expired");
        }
        return switch (cap.kind()) {
            case MOVE -> move(session, intent, cap);
            case RESIGN -> resign(session, intent);
            case ABORT -> abort(session, intent);
            case OFFER_DRAW -> offerDraw(session, intent);
            case RESPOND_DRAW -> respondDraw(session, intent, cap);
            case REMATCH -> rematch(session, intent);
        };
    }

    private Messages.Result rematch(Session session, Messages.Intent intent) {
        if (playerSide(session.playerId()) == null || result == null || rematchFactory == null) {
            return reject(intent, "rematch_unavailable");
        }
        rematchRequests.add(session.playerId());
        if (blackId != null && rematchRequests.contains(whiteId) && rematchRequests.contains(blackId)) {
            String newGameId = rematchFactory.create(blackId, whiteId, variant, timeControl, rated); // swap colours
            rematchRequests.clear();
            for (Subscriber sub : subscribers.values()) {
                sub.sink().send(MessageType.REMATCH_READY, new Messages.RematchReady(newGameId));
            }
        }
        return accepted(intent, session.playerId());
    }

    private Messages.Result move(Session session, Messages.Intent intent, Capability cap) {
        Side mover = playerSide(session.playerId());
        if (mover == null) {
            return reject(intent, "not_a_player");
        }
        if (mover != sideToMove()) {
            return reject(intent, "not_your_turn");
        }
        Uci uci = cap.moveUci();
        Fen base = variant == Variant.BUGHOUSE ? currentFen : initialFen;
        List<Uci> history = variant == Variant.BUGHOUSE ? List.of() : moves;
        ApplyResult applied = engine.apply(variant, base, history, uci);
        if (!applied.legal()) {
            return reject(intent, applied.reason().orElse("illegal_move"));
        }
        commitMove(mover, uci, applied);
        broadcastExcept(session.playerId());
        return accepted(intent, session.playerId());
    }

    private void commitMove(Side mover, Uci uci, ApplyResult applied) {
        long now = now();
        clocks.put(mover, clocks.get(mover).afterMove(now - turnStartMs));
        currentFen = applied.resultingPosition().orElseThrow();
        moves.add(uci);
        version++;
        inCheck = applied.inCheck();
        applied.capturedDropType().ifPresent(pt -> {
            // the mover captured an opponent piece; render it in the opponent's colour
            if (mover == Side.WHITE) {
                capturedByWhite.add(String.valueOf(pt.symbol()));
            } else {
                capturedByBlack.add(String.valueOf(Character.toUpperCase(pt.symbol())));
            }
            if (captureListener != null) {
                captureListener.accept(mover, pt);
            }
        });
        repository.append(gameId, new PersistedPly(version, currentFen, uci));

        if (clocks.get(mover).flagged()) {
            endByTimeout(mover);
        } else if (applied.terminal().isTerminal()) {
            endByTerminal(applied, mover);
        } else {
            turnStartMs = now;
            rescheduleFlag();
        }
        reissueAllCaps();
        maybeScheduleAi();
    }

    private void maybeScheduleAi() {
        if (started && result == null && aiMovers.containsKey(sideToMove())) {
            scheduler.schedule(this::playAi, AI_MOVE_DELAY_MS);
        }
    }

    private void playAi() {
        lock.lock();
        try {
            Side aiSide = sideToMove();
            AiMover aiMover = aiMovers.get(aiSide);
            if (result != null || aiMover == null) {
                return;
            }
            Uci uci = aiMover.bestMove(variant, currentFen);
            Fen base = variant == Variant.BUGHOUSE ? currentFen : initialFen;
            List<Uci> history = variant == Variant.BUGHOUSE ? List.of() : moves;
            ApplyResult applied = engine.apply(variant, base, history, uci);
            if (!applied.legal()) {
                return;
            }
            commitMove(aiSide, uci, applied);
            broadcastAll();
        } catch (RuntimeException e) {
            System.err.println("ai move failed for " + gameId + ": " + e);
        } finally {
            lock.unlock();
        }
    }

    private Messages.Result resign(Session session, Messages.Intent intent) {
        Side side = playerSide(session.playerId());
        if (side == null) {
            return reject(intent, "not_a_player");
        }
        endGame(new GameResult(GameResult.ResultType.WIN, Optional.of(side.opponent()), "resign"));
        reissueAllCaps();
        broadcastExcept(session.playerId());
        return accepted(intent, session.playerId());
    }

    /** End with no result before either side has committed to the game; no rating change. */
    private Messages.Result abort(Session session, Messages.Intent intent) {
        if (playerSide(session.playerId()) == null) {
            return reject(intent, "not_a_player");
        }
        if (moves.size() >= 2) {
            return reject(intent, "abort_unavailable");
        }
        endGame(new GameResult(GameResult.ResultType.ABORTED, Optional.empty(), "abort"));
        reissueAllCaps();
        broadcastExcept(session.playerId());
        return accepted(intent, session.playerId());
    }

    private Messages.Result offerDraw(Session session, Messages.Intent intent) {
        if (playerSide(session.playerId()) == null) {
            return reject(intent, "not_a_player");
        }
        pendingDrawOfferBy = session.playerId();
        reissueAllCaps();
        broadcastExcept(session.playerId());
        return accepted(intent, session.playerId());
    }

    private Messages.Result respondDraw(Session session, Messages.Intent intent, Capability cap) {
        if (pendingDrawOfferBy == null) {
            return reject(intent, "no_pending_draw_offer");
        }
        if (Boolean.parseBoolean(cap.params().getOrDefault("accept", "false"))) {
            endGame(new GameResult(GameResult.ResultType.DRAW, Optional.empty(), "agreement"));
        } else {
            pendingDrawOfferBy = null;
        }
        reissueAllCaps();
        broadcastExcept(session.playerId());
        return accepted(intent, session.playerId());
    }

    private void endByTerminal(ApplyResult applied, Side mover) {
        GameResult r = switch (applied.terminal()) {
            case CHECKMATE -> new GameResult(GameResult.ResultType.WIN, Optional.of(mover), "checkmate");
            default -> new GameResult(GameResult.ResultType.DRAW, Optional.empty(),
                    applied.terminal().toWire());
        };
        endGame(r);
    }

    private void endByTimeout(Side loser) {
        Side winner = loser.opponent();
        boolean winnerCanMate = engine.canMate(variant, currentFen, winner);
        GameResult r = winnerCanMate
                ? new GameResult(GameResult.ResultType.WIN, Optional.of(winner), "timeout")
                : new GameResult(GameResult.ResultType.DRAW, Optional.empty(), "timeout_insufficient_material");
        endGame(r);
    }

    private void endGame(GameResult r) {
        result = r;
        pendingDrawOfferBy = null;
        cancelFlag();
        if (completionListener != null) {
            try {
                completionListener.accept(r); // rating/history persistence must not abort completion
            } catch (RuntimeException e) {
                System.err.println("game completion listener failed for " + gameId + ": " + e);
                e.printStackTrace();
            }
        }
    }

    private void reissueAllCaps() {
        currentCaps.clear();
        for (String playerId : seatedPlayers()) {
            if (subscribers.containsKey(playerId)) {
                buildCapsFor(playerId);
            }
        }
    }

    private List<String> seatedPlayers() {
        if (whiteId == null) {
            return blackId == null ? List.of() : List.of(blackId);
        }
        return blackId == null ? List.of(whiteId) : List.of(whiteId, blackId);
    }

    private List<Capability> ensureCapsFor(String playerId) {
        List<Capability> existing = capsFor(playerId);
        if (!existing.isEmpty() || playerSide(playerId) == null) {
            return existing;
        }
        return buildCapsFor(playerId);
    }

    private List<Capability> buildCapsFor(String playerId) {
        Subscriber sub = subscribers.get(playerId);
        Side side = playerSide(playerId);
        if (sub == null || side == null) {
            return List.of();
        }
        String sessionId = sub.session().sessionId();
        long expiresAt = now() + CAPABILITY_TTL_MS;
        List<Capability> caps = new ArrayList<>();

        if (result != null) {
            if (variant != Variant.BUGHOUSE) {
                caps.add(cap(sessionId, CapabilityKind.REMATCH,
                        Map.of("opponentId", opponentOf(playerId)), expiresAt));
            }
        } else if (started) {
            if (side == sideToMove()) {
                Fen base = variant == Variant.BUGHOUSE ? currentFen : initialFen;
                List<Uci> history = variant == Variant.BUGHOUSE ? List.of() : moves;
                for (Uci uci : engine.legalMoves(variant, base, history)) {
                    caps.add(cap(sessionId, CapabilityKind.MOVE, Map.of("uci", uci.value()), expiresAt));
                }
            }
            if (moves.size() < 2) {
                caps.add(cap(sessionId, CapabilityKind.ABORT, Map.of(), expiresAt));
            } else {
                caps.add(cap(sessionId, CapabilityKind.RESIGN, Map.of(), expiresAt));
            }
            if (variant != Variant.BUGHOUSE) {
                if (pendingDrawOfferBy == null) {
                    caps.add(cap(sessionId, CapabilityKind.OFFER_DRAW, Map.of(), expiresAt));
                } else if (!playerId.equals(pendingDrawOfferBy)) {
                    caps.add(cap(sessionId, CapabilityKind.RESPOND_DRAW, Map.of("accept", "true"), expiresAt));
                    caps.add(cap(sessionId, CapabilityKind.RESPOND_DRAW, Map.of("accept", "false"), expiresAt));
                }
            }
        }
        caps.forEach(c -> currentCaps.put(c.id(), c));
        return caps;
    }

    private Capability cap(String sessionId, CapabilityKind kind, Map<String, String> params, long expiresAt) {
        return new Capability(ids.get(), sessionId, gameId, version, kind, params, expiresAt, ids.get());
    }

    private List<Capability> capsFor(String playerId) {
        Subscriber sub = subscribers.get(playerId);
        if (sub == null) {
            return List.of();
        }
        String sessionId = sub.session().sessionId();
        List<Capability> caps = new ArrayList<>();
        for (Capability c : currentCaps.values()) {
            if (c.sessionId().equals(sessionId)) {
                caps.add(c);
            }
        }
        return caps;
    }

    private Messages.Result accepted(Messages.Intent intent, String playerId) {
        return new Messages.Result(Disposition.ACCEPTED, intent.intentId(), Optional.empty(),
                Optional.of(snapshot()), Optional.of(capsFor(playerId)));
    }

    private Messages.Result reject(Messages.Intent intent, String reason) {
        return new Messages.Result(Disposition.REJECTED, intent.intentId(), Optional.of(reason),
                Optional.empty(), Optional.empty());
    }

    private Messages.Result stale(Messages.Intent intent, String playerId) {
        return new Messages.Result(Disposition.STALE, intent.intentId(), Optional.empty(),
                Optional.of(snapshot()), Optional.of(capsFor(playerId)));
    }

    private void broadcastExcept(String moverId) {
        for (Subscriber sub : subscribers.values()) {
            if (!sub.session().playerId().equals(moverId)) {
                sub.sink().send(MessageType.SNAPSHOT,
                        new Messages.Snapshot(snapshot(), capsFor(sub.session().playerId())));
            }
        }
    }

    private void sendTo(String playerId, String type, Object payload) {
        Subscriber sub = subscribers.get(playerId);
        if (sub != null) {
            sub.sink().send(type, payload);
        }
    }

    private void rescheduleFlag() {
        cancelFlag();
        if (result != null || !started) {
            return;
        }
        Side side = sideToMove();
        long atVersion = version;
        flagTimer = scheduler.schedule(() -> onFlag(side, atVersion), clocks.get(side).remainingMs());
    }

    private void cancelFlag() {
        if (flagTimer != null) {
            flagTimer.cancel();
            flagTimer = null;
        }
    }

    private void onFlag(Side side, long atVersion) {
        lock.lock();
        try {
            if (result != null || version != atVersion || sideToMove() != side) {
                return;
            }
            clocks.put(side, new Clock(0, clocks.get(side).control()));
            endByTimeout(side);
            reissueAllCaps();
            broadcastAll();
        } finally {
            lock.unlock();
        }
    }

    private void broadcastAll() {
        for (Subscriber sub : subscribers.values()) {
            sub.sink().send(MessageType.SNAPSHOT,
                    new Messages.Snapshot(snapshot(), capsFor(sub.session().playerId())));
        }
    }

    private GameSnapshot snapshot() {
        long now = now();
        boolean live = result == null && started;
        long white = clocks.get(Side.WHITE).remainingMs()
                - (live && sideToMove() == Side.WHITE ? now - turnStartMs : 0);
        long black = clocks.get(Side.BLACK).remainingMs()
                - (live && sideToMove() == Side.BLACK ? now - turnStartMs : 0);
        ClockSample sample = new ClockSample(white, black, sideToMove(), now);
        return new GameSnapshot(gameId, version, initialFen, currentFen, sideToMove(),
                List.copyOf(moves), sample, Optional.ofNullable(result), whiteId == null ? "" : whiteId,
                blackId == null ? "" : blackId, inCheck, List.copyOf(capturedByWhite), List.copyOf(capturedByBlack));
    }

    private Side sideToMove() {
        return currentFen.sideToMove();
    }

    private Side playerSide(String playerId) {
        if (playerId.equals(whiteId)) {
            return Side.WHITE;
        }
        if (playerId.equals(blackId)) {
            return Side.BLACK;
        }
        return null;
    }

    private String opponentOf(String playerId) {
        String opponent = playerId.equals(whiteId) ? blackId : whiteId;
        return opponent == null ? "" : opponent;
    }

    private void startIfReady() {
        if (started || startHeld || result != null || whiteId == null || blackId == null) {
            return;
        }
        started = true;
        turnStartMs = now();
        rescheduleFlag();
        reissueAllCaps();
        broadcastAll();
        maybeScheduleAi();
    }

    private long now() {
        return clock.getAsLong();
    }
}
