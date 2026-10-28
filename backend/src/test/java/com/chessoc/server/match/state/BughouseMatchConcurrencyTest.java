package com.chessoc.server.match.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Terminal;
import com.chessoc.server.domain.Uci;
import com.chessoc.server.engine.ApplyResult;
import com.chessoc.server.match.TimeControl;
import com.chessoc.server.protocol.GameResult;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BughouseMatchConcurrencyTest {
    private static final Fen START = Fen.parse(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR[] w KQkq - 0 1");
    private static final Fen AFTER_MOVE = Fen.parse(
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR[] b KQkq - 0 1");

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void joinAndSnapshotDoNotDeadlockWithAiCompletion(boolean recovered) throws Exception {
        CountDownLatch committing = new CountDownLatch(1);
        CountDownLatch releaseCommit = new CountDownLatch(1);
        var timers = Executors.newSingleThreadScheduledExecutor(BughouseMatchConcurrencyTest::daemonThread);
        var ai = Executors.newSingleThreadExecutor(BughouseMatchConcurrencyTest::daemonThread);
        InMemoryMatchRepository stored = new InMemoryMatchRepository();
        MatchRepository repository = new MatchRepository() {
            @Override
            public void append(String gameId, PersistedPly ply) {
                stored.append(gameId, ply);
                // AI commit holds the shared board lock immediately before room completion.
                committing.countDown();
                await(releaseCommit);
            }

            @Override
            public List<PersistedPly> plies(String gameId) {
                return stored.plies(gameId);
            }
        };
        ScriptedEngine engine = new ScriptedEngine();
        engine.legalByPly.add(List.of(Uci.of("e2e4")));
        engine.applyByPly.add(ApplyResult.legal(AFTER_MOVE, Terminal.CHECKMATE,
                true, Side.BLACK, Optional.empty()));
        List<GameResult> completed = new CopyOnWriteArrayList<>();
        List<LiveRoomState> checkpoints = new CopyOnWriteArrayList<>();
        try {
            MatchService service = new MatchService(engine, repository, new SystemScheduler(timers),
                    () -> 0L, () -> UUID.randomUUID().toString(), ai);
            TimeControl timeControl = TimeControl.fischer(300_000, 0);
            MatchGame.AiMover mover = (variant, position) -> Uci.of("e2e4");
            BughouseMatch initial = new BughouseMatch("lock-room", service, START, timeControl,
                    "creator", Side.BLACK, Set.of("ab", "bb"), mover,
                    (game, result) -> completed.add(result));
            BughouseMatch match = recovered
                    ? BughouseMatch.restore(initial.recoveryState(0), service, timeControl, mover,
                            (game, result) -> completed.add(result))
                    : initial;
            if (recovered) {
                match.resumeRecovered();
            }
            match.onStateChange(() -> checkpoints.add(match.recoveryState(0)));

            // Start A separately to force the overlap with the last human's join on B.
            match.boardA().start();
            await(committing);
            FutureTask<BughouseMatch.Seat> join = new FutureTask<>(() -> match.join("invitee", "bb"));
            Thread joining = daemonThread(join);
            joining.start();
            awaitLockWait(joining);
            FutureTask<LiveRoomState> snapshot = new FutureTask<>(() -> match.recoveryState(0));
            Thread snapshotting = daemonThread(snapshot);
            snapshotting.start();
            awaitLockWait(snapshotting);

            releaseCommit.countDown();

            assertThat(join.get(3, TimeUnit.SECONDS).seat()).isEqualTo("bb");
            LiveRoomState checkpoint = snapshot.get(3, TimeUnit.SECONDS);
            ai.submit(() -> {}).get(3, TimeUnit.SECONDS);
            assertThat(checkpoint.boards()).hasSize(2);
            assertThat(checkpoint.boards().get(0).moves()).containsExactly("e2e4");
            assertThat(checkpoints).isNotEmpty();
            assertThat(completed).hasSize(2);
            assertThat(match.boardA().currentSnapshot().result().orElseThrow().winner()).contains(Side.WHITE);
            assertThat(match.boardB().currentSnapshot().result().orElseThrow().winner()).contains(Side.BLACK);
            assertThat(match.seatsFilled()).isEqualTo(4);
            assertThat(match.humansJoined()).isEqualTo(2);
            assertThat(match.humanSeatCodes()).containsExactly("ab", "bb");
            assertThat(match.availableHumanSeatCodes()).isEmpty();
            assertThat(match.isParticipant("invitee")).isTrue();
            assertThat(repository.plies(match.boardA().gameId())).hasSize(1);
        } finally {
            releaseCommit.countDown();
            timers.shutdownNow();
            ai.shutdownNow();
            timers.awaitTermination(1, TimeUnit.SECONDS);
            ai.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    private static Thread daemonThread(Runnable task) {
        Thread thread = new Thread(task, "bughouse-lock-test");
        // A regression leaves blocked threads; daemon workers keep a failing test bounded.
        thread.setDaemon(true);
        return thread;
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static void awaitLockWait(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (thread.getState() != Thread.State.WAITING
                && thread.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertThat(thread.getState()).isIn(Thread.State.WAITING, Thread.State.BLOCKED);
    }
}
