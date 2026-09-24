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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BughouseMatchTest {

    private static final String START =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR[] w KQkq - 0 1";
    private static final String AFTER_CAPTURE =
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR[] b KQkq - 0 1";

    @Test
    void couplesSeatsReserveTransferAndResultAcrossBothBoards() {
        ValidatorClient engine = new ValidatorClient() {
            @Override
            public ApplyResult apply(Variant variant, Fen start, List<Uci> history, Uci move) {
                return ApplyResult.legal(Fen.parse(AFTER_CAPTURE), Terminal.CHECKMATE,
                        true, Side.BLACK, Optional.of(PieceType.KNIGHT));
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
        AtomicInteger ids = new AtomicInteger();
        MatchService service = new MatchService(engine, new InMemoryMatchRepository(),
                new ManualScheduler(), () -> 0L, () -> "id-" + ids.incrementAndGet());
        List<GameResult> persisted = new ArrayList<>();
        BughouseMatch match = new BughouseMatch("room", service, Fen.parse(START),
                TimeControl.fischer(300_000, 0), "p1", (game, result) -> persisted.add(result));

        CapturingSink sink = new CapturingSink();
        match.boardA().subscribe(new Session("s1", "p1", "c1"), sink);
        assertThat(sink.capability(CapabilityKind.MOVE)).isEmpty();

        assertThat(match.join("p2").color()).isEqualTo("black");
        assertThat(match.join("p3").board()).isEqualTo("B");
        BughouseMatch.Seat fourth = match.join("p4");
        assertThat(fourth.partnerColor()).isEqualTo("white");
        assertThat(match.seatsFilled()).isEqualTo(4);

        Capability move = sink.moveCapability(Uci.of("e2e4")).orElseThrow();
        match.boardA().submit(new Session("s1", "p1", "c1"),
                new Messages.Intent("move-1", move.id(), Map.of(), 0));

        assertThat(match.boardB().currentSnapshot().fen().reserve()).contains("n");
        assertThat(match.boardA().currentSnapshot().result().orElseThrow().winner()).contains(Side.WHITE);
        assertThat(match.boardB().currentSnapshot().result().orElseThrow().winner()).contains(Side.BLACK);
        assertThat(persisted).hasSize(2);
    }

    @Test
    void supportsEveryHumanAndAiSeatMixWithCreatorColor() {
        ValidatorClient engine = new ValidatorClient() {
            @Override
            public ApplyResult apply(Variant variant, Fen start, List<Uci> history, Uci move) {
                return ApplyResult.legal(start, Terminal.NONE, false, start.sideToMove(), Optional.empty());
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
        AtomicInteger ids = new AtomicInteger();
        MatchService service = new MatchService(engine, new InMemoryMatchRepository(),
                new ManualScheduler(), () -> 0L, () -> "seat-id-" + ids.incrementAndGet());

        for (int humans = 1; humans <= 4; humans++) {
            MatchGame.AiMover mover = humans < 4 ? (variant, fen) -> Uci.of("e2e4") : null;
            BughouseMatch match = new BughouseMatch("room-" + humans, service, Fen.parse(START),
                    TimeControl.fischer(300_000, 0), "p1", Side.BLACK, humans, mover, (game, result) -> {});

            BughouseMatch.Seat creator = match.join("p1");
            assertThat(creator.color()).isEqualTo("black");
            assertThat(creator.team()).isEqualTo("red");
            for (int player = 2; player <= humans; player++) {
                assertThat(match.join("p" + player).color()).isNotEqualTo("spectator");
            }

            assertThat(match.humansJoined()).isEqualTo(humans);
            assertThat(match.seatsFilled()).isEqualTo(4);
            assertThat(List.of(
                    match.boardA().currentSnapshot().whitePlayerId(),
                    match.boardA().currentSnapshot().blackPlayerId(),
                    match.boardB().currentSnapshot().whitePlayerId(),
                    match.boardB().currentSnapshot().blackPlayerId()))
                    .filteredOn(id -> id.startsWith("engine-"))
                    .hasSize(4 - humans);
        }
    }
}
