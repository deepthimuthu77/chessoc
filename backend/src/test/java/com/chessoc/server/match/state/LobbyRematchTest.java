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
import com.chessoc.server.engine.Score;
import com.chessoc.server.engine.SearchResult;
import com.chessoc.server.games.InMemoryGamesRepository;
import com.chessoc.server.match.TimeControl;
import com.chessoc.server.protocol.MessageType;
import com.chessoc.server.protocol.Messages;
import com.chessoc.server.rating.InMemoryRatingRepository;
import com.chessoc.server.rating.RatingService;
import com.chessoc.server.session.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LobbyRematchTest {

    private static final String AFTER_E4 =
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1";

    @Test
    void computerRematchStartsImmediatelyWithColorsSwappedAndEngineWired() {
        ScriptedEngine validator = new ScriptedEngine();
        validator.legalByPly.add(List.of(Uci.of("e2e4")));
        validator.applyByPly.add(ApplyResult.legal(Fen.parse(AFTER_E4), Terminal.NONE,
                false, Side.BLACK, Optional.empty()));
        DelayScheduler scheduler = new DelayScheduler();
        AtomicInteger ids = new AtomicInteger();
        MatchService matches = new MatchService(validator, new InMemoryMatchRepository(), scheduler,
                () -> 1_000L, () -> "cap-" + ids.incrementAndGet());
        Lobby lobby = new Lobby(matches, new RatingService(new InMemoryRatingRepository()),
                new InMemoryGamesRepository(), (variant, fen, budget) -> new SearchResult(
                        Uci.of("e2e4"), new Score.Cp(0), List.of(), 1, Optional.empty()),
                TimeControl.fischer(300_000, 0), () -> 1_000L,
                new InMemoryLiveRoomRepository());

        String firstId = lobby.createVsEngine("alice", Variant.STANDARD,
                TimeControl.fischer(300_000, 0), Side.WHITE);
        MatchGame first = matches.get(firstId).orElseThrow();
        CapturingSink sink = new CapturingSink();
        Session alice = new Session("session", "alice", "client");
        first.subscribe(alice, sink);

        submit(first, alice, sink.capability(CapabilityKind.ABORT).orElseThrow(), "abort");
        submit(first, alice, sink.capability(CapabilityKind.REMATCH).orElseThrow(), "rematch");

        String secondId = sink.messages.stream()
                .filter(message -> MessageType.REMATCH_READY.equals(message.type()))
                .map(message -> ((Messages.RematchReady) message.payload()).gameId())
                .findFirst().orElseThrow();
        MatchGame second = matches.get(secondId).orElseThrow();
        assertThat(second.whiteId()).isEqualTo(Lobby.ENGINE_ID);
        assertThat(second.blackId()).isEqualTo("alice");

        scheduler.run(400);
        assertThat(second.currentSnapshot().moves()).containsExactly(Uci.of("e2e4"));
    }

    @Test
    void computerOpponentDoesNotExposeAnUnanswerableDrawOffer() {
        ScriptedEngine validator = new ScriptedEngine();
        MatchService matches = new MatchService(validator, new InMemoryMatchRepository(),
                new DelayScheduler(), () -> 1_000L, () -> "cap");
        MatchGame game = matches.create(new GameSpec("game", Variant.STANDARD,
                Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"),
                "alice", Lobby.ENGINE_ID, TimeControl.fischer(300_000, 0), false));
        game.setAi(Side.BLACK, (variant, fen) -> Uci.of("e7e5"));
        CapturingSink sink = new CapturingSink();
        game.subscribe(new Session("session", "alice", "client"), sink);

        assertThat(sink.capability(CapabilityKind.OFFER_DRAW)).isEmpty();
    }

    private static void submit(MatchGame game, Session session, Capability capability, String intentId) {
        game.submit(session, new Messages.Intent(intentId, capability.id(), Map.of(), 1_000L));
    }

    private static final class DelayScheduler implements Scheduler {
        private record Scheduled(Runnable task, long delayMs) {
        }

        private final List<Scheduled> tasks = new ArrayList<>();

        @Override
        public Cancellable schedule(Runnable task, long delayMs) {
            Scheduled scheduled = new Scheduled(task, delayMs);
            tasks.add(scheduled);
            return () -> tasks.remove(scheduled);
        }

        void run(long delayMs) {
            tasks.stream().filter(task -> task.delayMs() == delayMs).findFirst().orElseThrow().task().run();
        }
    }
}
