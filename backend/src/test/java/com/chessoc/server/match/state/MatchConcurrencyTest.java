package com.chessoc.server.match.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessoc.server.capability.Capability;
import com.chessoc.server.capability.CapabilityKind;
import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.PieceType;
import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Terminal;
import com.chessoc.server.domain.Uci;
import com.chessoc.server.domain.Variant;
import com.chessoc.server.engine.ApplyResult;
import com.chessoc.server.engine.StatusResult;
import com.chessoc.server.engine.ValidatorClient;
import com.chessoc.server.match.TimeControl;
import com.chessoc.server.protocol.GameResult;
import com.chessoc.server.protocol.Messages;
import com.chessoc.server.session.Session;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(12)
class MatchConcurrencyTest {
    private static final Fen START = Fen.parse("8/8/8/8/8/8/4P3/4K2k w - - 0 1");
    private final ScheduledThreadPoolExecutor timers = new ScheduledThreadPoolExecutor(1);
    private final SystemScheduler scheduler = new SystemScheduler(timers);
    private final ThreadPoolExecutor ai = SystemScheduler.newAiExecutor(1);
    private final InMemoryMatchRepository repository = new InMemoryMatchRepository();
    private final AtomicInteger applies = new AtomicInteger();
    private final AtomicInteger searches = new AtomicInteger();
    private final CountDownLatch searchEntered = new CountDownLatch(1);
    private final CountDownLatch releaseSearch = new CountDownLatch(1);
    private final CountDownLatch retryEntered = new CountDownLatch(1);
    private final CountDownLatch releaseRetry = new CountDownLatch(1);
    private final CountDownLatch applyEntered = new CountDownLatch(1);
    private final CountDownLatch releaseApply = new CountDownLatch(1);
    private volatile boolean holdApply;
    private volatile boolean capture;

    private final ValidatorClient engine = new ValidatorClient() {
        @Override
        public ApplyResult apply(Variant variant, Fen start, List<Uci> history, Uci move) {
            applies.incrementAndGet();
            if (holdApply) {
                applyEntered.countDown();
                await(releaseApply);
            }
            Fen next = Fen.parse(start.raw().replace(" w ", " b "));
            return ApplyResult.legal(next, Terminal.NONE, false, Side.BLACK,
                    capture ? Optional.of(PieceType.KNIGHT) : Optional.empty());
        }

        @Override
        public List<Uci> legalMoves(Variant variant, Fen start, List<Uci> history) {
            return List.of(Uci.of("e2e4"));
        }

        @Override
        public StatusResult status(Variant variant, Fen position) {
            return new StatusResult(Terminal.NONE, false, position.sideToMove());
        }

        @Override
        public boolean canMate(Variant variant, Fen position, Side side) {
            return true;
        }
    };

    private MatchService service() {
        return new MatchService(engine, repository, scheduler, System::currentTimeMillis,
                () -> UUID.randomUUID().toString(), ai);
    }

    private MatchGame game(String id, long timeMs) {
        return service().create(new GameSpec(id, Variant.STANDARD, START, id + "w", id + "b",
                TimeControl.fischer(timeMs, 0), false));
    }

    private Uci heldSearch(Variant variant, Fen fen) {
        int call = searches.incrementAndGet();
        if (call == 1) {
            searchEntered.countDown();
            await(releaseSearch);
        } else {
            retryEntered.countDown();
            await(releaseRetry);
        }
        return Uci.of("e2e4");
    }

    @AfterEach
    void close() throws Exception {
        releaseSearch.countDown();
        releaseRetry.countDown();
        releaseApply.countDown();
        ai.shutdownNow();
        timers.shutdownNow();
        assertThat(ai.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void snapshotsAnotherGameMovesAndClockProgressWhileSearchIsHeld() throws Exception {
        MatchGame searching = game("ai", 30_000);
        searching.setAi(Side.WHITE, this::heldSearch);
        await(searchEntered);

        // Repeated scheduling triggers may not queue duplicates while the search is in flight.
        for (int i = 0; i < 5; i++) {
            searching.setAi(Side.WHITE, this::heldSearch);
        }
        CountDownLatch tick = new CountDownLatch(1);
        scheduler.schedule(tick::countDown, 1);
        await(tick);
        var readers = Executors.newSingleThreadExecutor();
        try {
            assertThat(readers.submit(searching::currentSnapshot).get(500, TimeUnit.MILLISECONDS)
                    .moves()).isEmpty();
            MatchGame human = game("human", 30_000);
            readers.submit(() -> move(human, "humanw")).get(500, TimeUnit.MILLISECONDS);
            assertThat(human.currentSnapshot().moves()).hasSize(1);
            MatchGame timed = game("clock", 100);
            CountDownLatch flagged = new CountDownLatch(1);
            timed.onComplete(result -> flagged.countDown());
            await(flagged);
            assertThat(timed.currentSnapshot().result().orElseThrow().reason()).isEqualTo("timeout");
            assertThat(searches.get()).isEqualTo(1);
            assertThat(ai.getQueue()).isEmpty();
        } finally {
            readers.shutdownNow();
        }
    }

    @Test
    void partnerCaptureProceedsAndInvalidatesSearchIncludingReserve() throws Exception {
        capture = true;
        BughouseMatch match = new BughouseMatch("bug", service(), START.withReserve(""),
                TimeControl.fischer(30_000, 0), "aw", (game, result) -> {});
        match.join("ab");
        match.join("bw");
        match.join("bb");
        match.boardA().setAi(Side.WHITE, this::heldSearch);
        await(searchEntered);

        var players = Executors.newSingleThreadExecutor();
        try {
            players.submit(() -> move(match.boardB(), "bw")).get(500, TimeUnit.MILLISECONDS);
        } finally {
            players.shutdownNow();
        }
        assertThat(match.boardB().currentSnapshot().moves()).hasSize(1);
        assertThat(match.boardA().currentSnapshot().fen().reserve()).contains("n");
        assertThat(match.boardA().currentSnapshot().version()).isEqualTo(1);
        releaseSearch.countDown();
        await(retryEntered);
        // The original search did not even reach apply; retry starts from the new pocket.
        assertThat(applies.get()).isEqualTo(1);
        assertThat(repository.plies("bug-a")).isEmpty();
        releaseRetry.countDown();
        eventually(() -> match.boardA().currentSnapshot().moves().size() == 1);
        assertThat(match.boardA().currentSnapshot().fen().reserve()).contains("n");
        assertThat(repository.plies("bug-a")).hasSize(1);
        assertThat(searches.get()).isEqualTo(2);
    }

    @Test
    void reserveVersionChangeDoesNotDisableClockOrExtendItsDeadline() {
        MatchGame game = service().create(new GameSpec("pocket-clock", Variant.BUGHOUSE,
                START.withReserve(""), "w", "b", TimeControl.fischer(650, 0), false));
        game.setAi(Side.WHITE, this::heldSearch);
        await(searchEntered);
        CountDownLatch flagged = new CountDownLatch(1);
        game.onComplete(result -> flagged.countDown());
        game.injectReserve(Side.WHITE, PieceType.KNIGHT);
        await(flagged);
        assertThat(game.currentSnapshot().result().orElseThrow().reason()).isEqualTo("timeout");
        releaseSearch.countDown();
        drainAi();
        assertThat(repository.plies("pocket-clock")).isEmpty();
        assertThat(applies.get()).isZero();
    }

    @Test
    void abortDuringSearchPreventsCommitAndRetry() {
        MatchGame game = game("abort", 30_000);
        game.setAi(Side.WHITE, this::heldSearch);
        await(searchEntered);
        action(game, "abortb", CapabilityKind.ABORT);
        releaseSearch.countDown();
        drainAi();
        assertThat(game.currentSnapshot().result().orElseThrow().reason()).isEqualTo("abort");
        assertThat(game.currentSnapshot().moves()).isEmpty();
        assertThat(applies.get()).isZero();
        assertThat(searches.get()).isEqualTo(1);
    }

    @Test
    void reserveChangeDuringValidationIsRecheckedBeforeCommit() {
        holdApply = true;
        MatchGame game = service().create(new GameSpec("validation", Variant.BUGHOUSE,
                START.withReserve(""), "w", "b", TimeControl.fischer(30_000, 0), false));
        game.setAi(Side.WHITE, (variant, fen) -> Uci.of("e2e4"));
        await(applyEntered);
        game.injectReserve(Side.WHITE, PieceType.QUEEN);
        holdApply = false;
        releaseApply.countDown();
        eventually(() -> game.currentSnapshot().moves().size() == 1);
        assertThat(applies.get()).isEqualTo(2);
        assertThat(game.currentSnapshot().fen().reserve()).contains("Q");
        assertThat(repository.plies("validation")).hasSize(1);
    }

    @Test
    void transientSearchFailureRetriesOnceWithoutDuplicates() {
        MatchGame game = game("retry", 30_000);
        game.setAi(Side.WHITE, (variant, fen) -> {
            if (searches.incrementAndGet() == 1) {
                throw new IllegalStateException("temporary search failure");
            }
            return Uci.of("e2e4");
        });
        eventually(() -> game.currentSnapshot().moves().size() == 1);
        assertThat(searches.get()).isEqualTo(2);
        assertThat(applies.get()).isEqualTo(1);
    }

    @Test
    void fullQueueRejectsWithoutRunningSearchOnSchedulerAndRetries() throws Exception {
        ai.execute(() -> await(releaseSearch));
        for (int i = 0; i < 16; i++) {
            ai.execute(() -> {});
        }
        MatchGame game = game("busy", 30_000);
        game.setAi(Side.WHITE, (variant, fen) -> {
            assertThat(Thread.currentThread().getName()).startsWith("match-ai-");
            searches.incrementAndGet();
            return Uci.of("e2e4");
        });
        CountDownLatch schedulerFree = new CountDownLatch(1);
        scheduler.schedule(schedulerFree::countDown, 500);
        await(schedulerFree);
        assertThat(searches.get()).isZero();
        releaseSearch.countDown();
        eventually(() -> game.currentSnapshot().moves().size() == 1);
        assertThat(searches.get()).isEqualTo(1);
    }

    private void drainAi() {
        try {
            ai.submit(() -> {}).get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void move(MatchGame game, String player) {
        action(game, player, CapabilityKind.MOVE);
    }

    private static void action(MatchGame game, String player, CapabilityKind kind) {
        CapturingSink sink = new CapturingSink();
        Session session = new Session(player + "-session", player, player + "-conn");
        game.subscribe(session, sink);
        Capability cap = sink.capability(kind).orElseThrow();
        game.submit(session, new Messages.Intent(UUID.randomUUID().toString(), cap.id(), Map.of(), 0));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static void eventually(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
