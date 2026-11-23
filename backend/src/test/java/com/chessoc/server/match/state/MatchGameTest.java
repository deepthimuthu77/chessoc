package com.chessoc.server.match.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessoc.server.capability.Capability;
import com.chessoc.server.capability.CapabilityKind;
import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Terminal;
import com.chessoc.server.domain.Uci;
import com.chessoc.server.domain.Variant;
import com.chessoc.server.engine.ApplyResult;
import com.chessoc.server.match.TimeControl;
import com.chessoc.server.protocol.Disposition;
import com.chessoc.server.protocol.GameResult;
import com.chessoc.server.protocol.GameSnapshot;
import com.chessoc.server.protocol.MessageType;
import com.chessoc.server.protocol.Messages;
import com.chessoc.server.session.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MatchGameTest {

    private static final String INIT = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String F3 = "rnbqkbnr/pppppppp/8/8/8/5P2/PPPPP1PP/RNBQKBNR b KQkq - 0 1";
    private static final String E5 = "rnbqkbnr/pppp1ppp/8/4p3/8/5P2/PPPPP1PP/RNBQKBNR w KQkq e6 0 2";
    private static final String G4 = "rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq g3 0 2";
    private static final String MATE = "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3";

    private final Session white = new Session("sW", "pW", "cW");
    private final Session black = new Session("sB", "pB", "cB");
    private final CapturingSink wSink = new CapturingSink();
    private final CapturingSink bSink = new CapturingSink();

    private ScriptedEngine foolsMateEngine() {
        ScriptedEngine e = new ScriptedEngine();
        e.legalByPly.add(List.of(Uci.of("f2f3")));
        e.legalByPly.add(List.of(Uci.of("e7e5")));
        e.legalByPly.add(List.of(Uci.of("g2g4")));
        e.legalByPly.add(List.of(Uci.of("d8h4")));
        e.applyByPly.add(ApplyResult.legal(Fen.parse(F3), Terminal.NONE, false, Side.BLACK, Optional.empty()));
        e.applyByPly.add(ApplyResult.legal(Fen.parse(E5), Terminal.NONE, false, Side.WHITE, Optional.empty()));
        e.applyByPly.add(ApplyResult.legal(Fen.parse(G4), Terminal.NONE, false, Side.BLACK, Optional.empty()));
        e.applyByPly.add(ApplyResult.legal(Fen.parse(MATE), Terminal.CHECKMATE, true, Side.WHITE, Optional.empty()));
        return e;
    }

    private MatchGame newGame(ScriptedEngine engine, MatchRepository repo, ManualScheduler scheduler) {
        AtomicInteger seq = new AtomicInteger();
        MatchService svc = new MatchService(engine, repo, scheduler, () -> 0L, () -> "k" + seq.incrementAndGet());
        GameSpec spec = new GameSpec("g1", Variant.STANDARD, Fen.parse(INIT), "pW", "pB",
                TimeControl.fischer(180_000, 2_000), true);
        MatchGame game = svc.create(spec);
        game.subscribe(white, wSink);
        game.subscribe(black, bSink);
        return game;
    }

    private void play(MatchGame game, Session mover, CapturingSink sink, String uci, String intentId) {
        Capability cap = sink.moveCapability(Uci.of(uci)).orElseThrow(() -> new AssertionError("no cap " + uci));
        game.submit(mover, new Messages.Intent(intentId, cap.id(), Map.of(), 0));
    }

    @Test
    void playsAScriptedGameToCheckmate() {
        ScriptedEngine engine = foolsMateEngine();
        InMemoryMatchRepository repo = new InMemoryMatchRepository();
        MatchGame game = newGame(engine, repo, new ManualScheduler());

        play(game, white, wSink, "f2f3", "i1");
        assertThat(((Messages.Result) wSink.last().payload()).disposition()).isEqualTo(Disposition.ACCEPTED);

        play(game, black, bSink, "e7e5", "i2");
        play(game, white, wSink, "g2g4", "i3");
        play(game, black, bSink, "d8h4", "i4");

        GameSnapshot snap = game.currentSnapshot();
        assertThat(snap.moves()).hasSize(4);
        assertThat(snap.result()).isPresent();
        assertThat(snap.result().get().type()).isEqualTo(GameResult.ResultType.WIN);
        assertThat(snap.result().get().winner()).contains(Side.BLACK);
        assertThat(repo.plies("g1")).hasSize(4);
        assertThat(repo.plies("g1").get(3).fen()).isEqualTo(Fen.parse(MATE));
        assertThat(wSink.capability(CapabilityKind.REMATCH)).isPresent();
    }

    @Test
    void chatIsMaskedIsolatedAndRateLimitedAcrossReconnectsWithoutChangingTheBoard() {
        java.util.concurrent.atomic.AtomicLong time = new java.util.concurrent.atomic.AtomicLong(1000);
        MatchService service = new MatchService(foolsMateEngine(), new InMemoryMatchRepository(),
                new ManualScheduler(), time::get);
        MatchGame first = service.create(new GameSpec("chat-one", Variant.STANDARD, Fen.parse(INIT),
                "pW", "pB", TimeControl.fischer(180000, 2000), false));
        MatchGame second = service.create(new GameSpec("chat-two", Variant.STANDARD, Fen.parse(INIT),
                "pW", "pB", TimeControl.fischer(180000, 2000), false));
        CapturingSink otherRoom = new CapturingSink();
        CapturingSink viewer = new CapturingSink();
        first.subscribe(white, wSink);
        first.subscribe(black, bSink);
        first.subscribe(new Session("sV", "pV", "cV"), viewer);
        second.subscribe(white, otherRoom);
        int otherCount = otherRoom.messages.size();
        first.chat(white, "SHIT! 😀");
        assertThat((Messages.Chat) bSink.last().payload()).isEqualTo(new Messages.Chat("white", "****! 😀", 1000));
        assertThat(viewer.last().payload()).isEqualTo(bSink.last().payload());
        assertThat(otherRoom.messages).hasSize(otherCount);
        int delivered = bSink.messages.size();
        first.chat(white, "flood");
        assertThat(bSink.messages).hasSize(delivered);
        assertThat(wSink.last().payload()).isInstanceOf(Messages.ChatRejected.class);
        first.unsubscribe(white.playerId(), wSink);
        first.subscribe(new Session("new-session", "pW", "new-connection"), wSink);
        first.chat(white, "still too soon");
        assertThat(wSink.last().payload()).isInstanceOf(Messages.ChatRejected.class);
        first.chat(black, "other player can reply");
        assertThat(((Messages.Chat) wSink.last().payload()).color()).isEqualTo("black");
        time.addAndGet(750);
        first.chat(white, "ready again");
        assertThat(((Messages.Chat) bSink.last().payload()).text()).isEqualTo("ready again");
        assertThat(first.currentSnapshot().version()).isZero();
        assertThat(first.currentSnapshot().moves()).isEmpty();
    }

    @Test
    void disconnectRemovesViewerButDoesNotRemoveItsReplacementConnection() {
        MatchGame game = newGame(foolsMateEngine(), new InMemoryMatchRepository(), new ManualScheduler());
        Session viewer = new Session("viewer-session", "viewer", "old-connection");
        CapturingSink oldSink = new CapturingSink();
        CapturingSink replacement = new CapturingSink();
        game.subscribe(viewer, oldSink);
        assertThat(game.spectatorCount()).isEqualTo(1);
        game.subscribe(new Session("viewer-session", "viewer", "new-connection"), replacement);
        game.unsubscribe("viewer", oldSink);
        assertThat(game.viewerIds()).contains("viewer");
        game.unsubscribe("viewer", replacement);
        assertThat(game.spectatorCount()).isZero();
        assertThat(game.viewerIds()).doesNotContain("viewer");
    }

    @Test
    void finalClockKeepsElapsedThinkingTimeWhenGameIsAborted() {
        java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong(1000);
        MatchService service = new MatchService(foolsMateEngine(), new InMemoryMatchRepository(),
                new ManualScheduler(), now::get);
        MatchGame game = service.create(new GameSpec("clock-game", Variant.STANDARD, Fen.parse(INIT),
                "pW", "pB", TimeControl.fischer(180000, 2000), false));
        game.subscribe(white, wSink);
        now.addAndGet(4000);
        Capability abort = wSink.capability(CapabilityKind.ABORT).orElseThrow();
        game.submit(white, new Messages.Intent("abort", abort.id(), Map.of(), now.get()));
        assertThat(game.currentSnapshot().clockSample().whiteRemainingMs()).isEqualTo(176000);
        now.addAndGet(5000);
        assertThat(game.currentSnapshot().clockSample().whiteRemainingMs()).isEqualTo(176000);
    }

    @Test
    void doesNotAdvanceProjectionOnUnknownCapability() {
        ScriptedEngine engine = foolsMateEngine();
        MatchGame game = newGame(engine, new InMemoryMatchRepository(), new ManualScheduler());

        game.submit(white, new Messages.Intent("i1", "does-not-exist", Map.of(), 0));

        Messages.Result r = (Messages.Result) wSink.last().payload();
        assertThat(r.disposition()).isEqualTo(Disposition.REJECTED);
        assertThat(r.reason()).contains("unknown_or_spent_capability");
        assertThat(game.currentSnapshot().version()).isZero();
    }

    @Test
    void rejectsUsingAnotherSessionsCapability() {
        ScriptedEngine engine = foolsMateEngine();
        MatchGame game = newGame(engine, new InMemoryMatchRepository(), new ManualScheduler());
        play(game, white, wSink, "f2f3", "i1"); // now black has e7e5

        Capability blackCap = bSink.moveCapability(Uci.of("e7e5")).orElseThrow();
        game.submit(white, new Messages.Intent("x", blackCap.id(), Map.of(), 0));

        Messages.Result r = (Messages.Result) wSink.last().payload();
        assertThat(r.disposition()).isEqualTo(Disposition.REJECTED);
        assertThat(r.reason()).contains("capability_session_mismatch");
    }

    @Test
    void resignEndsTheGame() {
        ScriptedEngine engine = foolsMateEngine();
        MatchGame game = newGame(engine, new InMemoryMatchRepository(), new ManualScheduler());

        // Resign is only offered once both sides have moved; before that it is abort.
        play(game, white, wSink, "f2f3", "i1");
        play(game, black, bSink, "e7e5", "i2");

        Capability resign = wSink.capability(CapabilityKind.RESIGN).orElseThrow();
        game.submit(white, new Messages.Intent("r1", resign.id(), Map.of(), 0));

        GameResult result = game.currentSnapshot().result().orElseThrow();
        assertThat(result.type()).isEqualTo(GameResult.ResultType.WIN);
        assertThat(result.winner()).contains(Side.BLACK);
        assertThat(result.reason()).isEqualTo("resign");
    }

    @Test
    void abortBeforeFirstMovesEndsWithNoResult() {
        ScriptedEngine engine = foolsMateEngine();
        MatchGame game = newGame(engine, new InMemoryMatchRepository(), new ManualScheduler());

        // Abort is offered up front; resign is not yet available.
        assertThat(wSink.capability(CapabilityKind.RESIGN)).isEmpty();
        Capability abort = wSink.capability(CapabilityKind.ABORT).orElseThrow();
        game.submit(white, new Messages.Intent("a1", abort.id(), Map.of(), 0));

        GameResult result = game.currentSnapshot().result().orElseThrow();
        assertThat(result.type()).isEqualTo(GameResult.ResultType.ABORTED);
        assertThat(result.reason()).isEqualTo("abort");
    }

    @Test
    void abortIsUnavailableAfterBothSidesMove() {
        ScriptedEngine engine = foolsMateEngine();
        MatchGame game = newGame(engine, new InMemoryMatchRepository(), new ManualScheduler());

        play(game, white, wSink, "f2f3", "i1");
        play(game, black, bSink, "e7e5", "i2");

        assertThat(wSink.capability(CapabilityKind.ABORT)).isEmpty();
        assertThat(wSink.capability(CapabilityKind.RESIGN)).isPresent();
    }

    @Test
    void humanRematchWaitsForBothPlayersAndSwapsColors() {
        MatchGame game = newGame(foolsMateEngine(), new InMemoryMatchRepository(), new ManualScheduler());
        List<String> rematchSeats = new ArrayList<>();
        game.onRematch((newWhite, newBlack, variant, timeControl, rated) -> {
            rematchSeats.add(newWhite);
            rematchSeats.add(newBlack);
            return "g2";
        });

        Capability abort = wSink.capability(CapabilityKind.ABORT).orElseThrow();
        game.submit(white, new Messages.Intent("abort", abort.id(), Map.of(), 0));

        Capability whiteRematch = wSink.capability(CapabilityKind.REMATCH).orElseThrow();
        game.submit(white, new Messages.Intent("white-rematch", whiteRematch.id(), Map.of(), 0));
        assertThat(rematchSeats).isEmpty();

        Capability blackRematch = bSink.capability(CapabilityKind.REMATCH).orElseThrow();
        game.submit(black, new Messages.Intent("black-rematch", blackRematch.id(), Map.of(), 0));

        assertThat(rematchSeats).containsExactly("pB", "pW");
        assertThat(wSink.messages).anyMatch(message -> MessageType.REMATCH_READY.equals(message.type()));
        assertThat(bSink.messages).anyMatch(message -> MessageType.REMATCH_READY.equals(message.type()));
    }

    @Test
    void drawOfferAcceptedDrawsTheGame() {
        ScriptedEngine engine = foolsMateEngine();
        MatchGame game = newGame(engine, new InMemoryMatchRepository(), new ManualScheduler());

        Capability offer = wSink.capability(CapabilityKind.OFFER_DRAW).orElseThrow();
        game.submit(white, new Messages.Intent("d1", offer.id(), Map.of(), 0));

        Capability accept = bSink.latestCapabilities().stream()
                .filter(c -> c.kind() == CapabilityKind.RESPOND_DRAW && "true".equals(c.params().get("accept")))
                .findFirst().orElseThrow();
        game.submit(black, new Messages.Intent("d2", accept.id(), Map.of(), 0));

        assertThat(game.currentSnapshot().result().orElseThrow().type()).isEqualTo(GameResult.ResultType.DRAW);
    }

    @Test
    void resyncReturnsFullSnapshot() {
        ScriptedEngine engine = foolsMateEngine();
        MatchGame game = newGame(engine, new InMemoryMatchRepository(), new ManualScheduler());

        game.resync("pW", 0);

        assertThat(wSink.last().type()).isEqualTo(MessageType.RESYNC_OK);
        assertThat(((Messages.ResyncOk) wSink.last().payload()).snapshot().gameId()).isEqualTo("g1");
    }

    @Test
    void duplicateIntentIsAppliedOnce() {
        ScriptedEngine engine = foolsMateEngine();
        MatchGame game = newGame(engine, new InMemoryMatchRepository(), new ManualScheduler());

        Capability cap = wSink.moveCapability(Uci.of("f2f3")).orElseThrow();
        Messages.Intent intent = new Messages.Intent("i1", cap.id(), Map.of(), 0);
        game.submit(white, intent);
        game.submit(white, intent);

        assertThat(engine.applyCalls).isEqualTo(1);
        assertThat(game.currentSnapshot().version()).isEqualTo(1);
    }

    @Test
    void flagTimeoutIsAWinWhenOpponentCanMate() {
        ScriptedEngine engine = foolsMateEngine();
        engine.canMate = true;
        ManualScheduler scheduler = new ManualScheduler();
        MatchGame game = newGame(engine, new InMemoryMatchRepository(), scheduler);

        scheduler.runAll(); // white's flag fires

        GameResult result = game.currentSnapshot().result().orElseThrow();
        assertThat(result.type()).isEqualTo(GameResult.ResultType.WIN);
        assertThat(result.winner()).contains(Side.BLACK);
        assertThat(result.reason()).isEqualTo("timeout");
    }

    @Test
    void flagTimeoutIsADrawWhenOpponentCannotMate() {
        ScriptedEngine engine = foolsMateEngine();
        engine.canMate = false;
        ManualScheduler scheduler = new ManualScheduler();
        MatchGame game = newGame(engine, new InMemoryMatchRepository(), scheduler);

        scheduler.runAll();

        assertThat(game.currentSnapshot().result().orElseThrow().type()).isEqualTo(GameResult.ResultType.DRAW);
    }
}
