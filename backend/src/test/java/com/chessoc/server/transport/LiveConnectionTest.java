package com.chessoc.server.transport;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessoc.server.capability.Capability;
import com.chessoc.server.capability.CapabilityKind;
import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Terminal;
import com.chessoc.server.domain.Uci;
import com.chessoc.server.domain.Variant;
import com.chessoc.server.engine.ApplyResult;
import com.chessoc.server.engine.StatusResult;
import com.chessoc.server.engine.ValidatorClient;
import com.chessoc.server.match.TimeControl;
import com.chessoc.server.match.state.GameSpec;
import com.chessoc.server.match.state.InMemoryMatchRepository;
import com.chessoc.server.match.state.MatchGame;
import com.chessoc.server.match.state.MatchService;
import com.chessoc.server.match.state.Scheduler;
import com.chessoc.server.protocol.Disposition;
import com.chessoc.server.protocol.Envelope;
import com.chessoc.server.protocol.MessageType;
import com.chessoc.server.protocol.Messages;
import com.chessoc.server.protocol.ProtocolCodec;
import com.chessoc.server.session.RateLimiter;
import com.chessoc.server.session.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LiveConnectionTest {

    private static final String INIT = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String E4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
    private static final String E5 = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2";

    private final ProtocolCodec codec = new ProtocolCodec();

    private static final class ScriptedValidator implements ValidatorClient {
        final List<List<Uci>> legal = new ArrayList<>();
        final List<ApplyResult> applied = new ArrayList<>();

        @Override
        public ApplyResult apply(Variant v, Fen s, List<Uci> h, Uci m) {
            return applied.get(h.size());
        }

        @Override
        public List<Uci> legalMoves(Variant v, Fen s, List<Uci> h) {
            return h.size() < legal.size() ? legal.get(h.size()) : List.of();
        }

        @Override
        public StatusResult status(Variant v, Fen p) {
            return new StatusResult(Terminal.NONE, false, p.sideToMove());
        }

        @Override
        public boolean canMate(Variant v, Fen p, Side side) {
            return true;
        }
    }

    private static final class CapturingConnection implements Connection {
        final List<String> sent = new ArrayList<>();

        @Override
        public void send(String text) {
            sent.add(text);
        }

        @Override
        public void close() {
        }
    }

    private Envelope lastEnvelope(CapturingConnection conn, String type) {
        for (int i = conn.sent.size() - 1; i >= 0; i--) {
            Envelope env = codec.decode(conn.sent.get(i));
            if (env.type().equals(type)) {
                return env;
            }
        }
        throw new AssertionError("no message of type " + type);
    }

    @Test
    void malformedPayloadsReturnErrorsAndTheConnectionStillAnswersPing() {
        ScriptedValidator engine = new ScriptedValidator();
        engine.legal.add(List.of(Uci.of("e2e4")));
        Scheduler noop = (task, delay) -> () -> {};
        MatchService service = new MatchService(engine, new InMemoryMatchRepository(), noop, () -> 0L);
        MatchGame game = service.create(new GameSpec("g", Variant.STANDARD, Fen.parse(INIT),
                "white", "black", TimeControl.fischer(300_000, 0), false));
        CapturingConnection conn = new CapturingConnection();
        LiveConnection live = new LiveConnection(new Session("s", "white", "c"), conn, codec, game);
        for (String frame : List.of("null", "", "[]", "{\"v\":[1,0],\"type\":\"intent\",\"payload\":[]}",
                "{\"v\":[1,0],\"type\":\"intent\",\"payload\":{}}")) {
            live.onMessage(frame);
            assertThat(lastEnvelope(conn, MessageType.ERROR)).isNotNull();
        }
        live.onMessage(codec.encode(MessageType.PING, new Messages.Ping()));
        assertThat(lastEnvelope(conn, MessageType.PONG)).isNotNull();
        assertThat(codec.payload(lastEnvelope(conn, MessageType.SNAPSHOT), Messages.Snapshot.class)
                .snapshot().version()).isZero();
    }

    @Test
    void routesAnIntentAndBroadcastsOverTheWire() {
        ScriptedValidator engine = new ScriptedValidator();
        engine.legal.add(List.of(Uci.of("e2e4")));
        engine.legal.add(List.of(Uci.of("e7e5")));
        engine.applied.add(ApplyResult.legal(Fen.parse(E4), Terminal.NONE, false, Side.BLACK, Optional.empty()));
        engine.applied.add(ApplyResult.legal(Fen.parse(E5), Terminal.NONE, false, Side.WHITE, Optional.empty()));

        AtomicInteger seq = new AtomicInteger();
        Scheduler noop = (task, delay) -> () -> {
        };
        MatchService service = new MatchService(engine, new InMemoryMatchRepository(), noop,
                () -> 0L, () -> "k" + seq.incrementAndGet());
        MatchGame game = service.create(new GameSpec("g1", Variant.STANDARD, Fen.parse(INIT),
                "pW", "pB", TimeControl.fischer(180_000, 0), true));

        CapturingConnection whiteConn = new CapturingConnection();
        CapturingConnection blackConn = new CapturingConnection();
        LiveConnection whiteLive =
                new LiveConnection(new Session("sW", "pW", "cW"), whiteConn, codec, game);
        new LiveConnection(new Session("sB", "pB", "cB"), blackConn, codec, game);

        Messages.Snapshot whiteSnap = codec.payload(lastEnvelope(whiteConn, MessageType.SNAPSHOT), Messages.Snapshot.class);
        Capability move = whiteSnap.capabilities().stream()
                .filter(c -> c.kind() == CapabilityKind.MOVE && c.moveUci().equals(Uci.of("e2e4")))
                .findFirst().orElseThrow();

        whiteLive.onMessage(codec.encode(MessageType.INTENT,
                new Messages.Intent("i1", move.id(), Map.of(), 0)));

        Messages.Snapshot blackSnap = codec.payload(lastEnvelope(blackConn, MessageType.SNAPSHOT), Messages.Snapshot.class);
        assertThat(blackSnap.snapshot().version()).isEqualTo(1);
        assertThat(blackSnap.snapshot().sideToMove()).isEqualTo(Side.BLACK);
        assertThat(blackSnap.capabilities())
                .anyMatch(c -> c.kind() == CapabilityKind.MOVE && c.moveUci().equals(Uci.of("e7e5")));

        Messages.Result result = codec.payload(lastEnvelope(whiteConn, MessageType.RESULT), Messages.Result.class);
        assertThat(result.disposition()).isEqualTo(Disposition.ACCEPTED);
    }

    @Test
    void rateLimitsRapidIntents() {
        ScriptedValidator engine = new ScriptedValidator();
        engine.legal.add(List.of(Uci.of("e2e4")));
        engine.legal.add(List.of(Uci.of("e7e5")));
        engine.applied.add(ApplyResult.legal(Fen.parse(E4), Terminal.NONE, false, Side.BLACK, Optional.empty()));
        engine.applied.add(ApplyResult.legal(Fen.parse(E5), Terminal.NONE, false, Side.WHITE, Optional.empty()));

        Scheduler noop = (task, delay) -> () -> {
        };
        MatchService service = new MatchService(engine, new InMemoryMatchRepository(), noop, () -> 0L, () -> "k");
        MatchGame game = service.create(new GameSpec("g1", Variant.STANDARD, Fen.parse(INIT),
                "pW", "pB", TimeControl.fischer(180_000, 0), true));

        CapturingConnection conn = new CapturingConnection();
        // 1s min interval, clock frozen at 0 → the second intent is always inside the window.
        LiveConnection live = new LiveConnection(new Session("sW", "pW", "cW"), conn, codec, game,
                new RateLimiter(1000), () -> 0L);

        Messages.Snapshot snap = codec.payload(lastEnvelope(conn, MessageType.SNAPSHOT), Messages.Snapshot.class);
        Capability move = snap.capabilities().stream()
                .filter(c -> c.kind() == CapabilityKind.MOVE && c.moveUci().equals(Uci.of("e2e4")))
                .findFirst().orElseThrow();
        String intent = codec.encode(MessageType.INTENT, new Messages.Intent("i1", move.id(), Map.of(), 0));

        live.onMessage(intent);
        live.onMessage(intent);

        Messages.Result result = codec.payload(lastEnvelope(conn, MessageType.RESULT), Messages.Result.class);
        assertThat(result.disposition()).isEqualTo(Disposition.REJECTED);
        assertThat(result.reason()).contains("rate_limited");
    }

    @Test
    void answersPingAndRejectsGarbage() {
        ScriptedValidator engine = new ScriptedValidator();
        engine.legal.add(List.of(Uci.of("e2e4")));
        Scheduler noop = (task, delay) -> () -> {
        };
        MatchService service = new MatchService(engine, new InMemoryMatchRepository(), noop,
                () -> 0L, () -> "k");
        MatchGame game = service.create(new GameSpec("g1", Variant.STANDARD, Fen.parse(INIT),
                "pW", "pB", TimeControl.fischer(180_000, 0), true));

        CapturingConnection conn = new CapturingConnection();
        LiveConnection live = new LiveConnection(new Session("sW", "pW", "cW"), conn, codec, game);

        live.onMessage(codec.encode(MessageType.PING, new Messages.Ping()));
        assertThat(lastEnvelope(conn, MessageType.PONG)).isNotNull();

        live.onMessage("not json");
        Messages.Error error = codec.payload(lastEnvelope(conn, MessageType.ERROR), Messages.Error.class);
        assertThat(error.code()).isEqualTo("bad_message");
    }
}
