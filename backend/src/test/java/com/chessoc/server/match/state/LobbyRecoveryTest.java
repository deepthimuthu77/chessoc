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
import com.chessoc.server.protocol.Messages;
import com.chessoc.server.rating.InMemoryRatingRepository;
import com.chessoc.server.rating.RatingService;
import com.chessoc.server.session.Session;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LobbyRecoveryTest {
    private static final String AFTER_E4 =
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1";

    @Test
    void restoresRoomSeatsPositionAndVersion() {
        ScriptedEngine engine = new ScriptedEngine();
        engine.legalByPly.add(List.of(Uci.of("e2e4")));
        engine.applyByPly.add(ApplyResult.legal(Fen.parse(AFTER_E4), Terminal.NONE,
                false, Side.BLACK, Optional.empty()));
        InMemoryLiveRoomRepository liveRooms = new InMemoryLiveRoomRepository();
        AtomicInteger ids = new AtomicInteger();
        MatchService firstMatches = new MatchService(engine, new InMemoryMatchRepository(),
                new ManualScheduler(), () -> 1_000L, () -> "id-" + ids.incrementAndGet());
        Lobby first = lobby(firstMatches, liveRooms);
        String room = first.create("alice", Variant.STANDARD, TimeControl.fischer(300_000, 0), false);
        first.join(room, "bob");
        MatchGame game = firstMatches.get(room).orElseThrow();
        CapturingSink sink = new CapturingSink();
        Session alice = new Session("session", "alice", "client");
        game.subscribe(alice, sink);
        Capability move = sink.capability(CapabilityKind.MOVE).orElseThrow();
        game.submit(alice, new Messages.Intent("move", move.id(), Map.of(), 1_000L));

        MatchService restoredMatches = new MatchService(engine, new InMemoryMatchRepository(),
                new ManualScheduler(), () -> 1_000L, () -> "restored-" + ids.incrementAndGet());
        Lobby restored = lobby(restoredMatches, liveRooms);
        Lobby.Seat restoredSeat = restored.join(room, "alice");
        MatchGame restoredGame = restoredMatches.get(restoredSeat.gameId()).orElseThrow();

        assertThat(restoredSeat.color()).isEqualTo("white");
        assertThat(restoredGame.currentSnapshot().version()).isEqualTo(1);
        assertThat(restoredGame.currentSnapshot().fen().raw()).isEqualTo(AFTER_E4);
        assertThat(restoredGame.currentSnapshot().blackPlayerId()).isEqualTo("bob");
    }

    private static Lobby lobby(MatchService matches, LiveRoomRepository rooms) {
        return new Lobby(matches, new RatingService(new InMemoryRatingRepository()),
                new InMemoryGamesRepository(), (variant, fen, budget) -> new SearchResult(
                        Uci.of("e2e4"), new Score.Cp(0), List.of(), 1, Optional.empty()),
                TimeControl.fischer(300_000, 0),
                () -> 1_000L, rooms);
    }
}
