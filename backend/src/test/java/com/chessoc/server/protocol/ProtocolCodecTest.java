package com.chessoc.server.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chessoc.server.capability.Capability;
import com.chessoc.server.capability.CapabilityKind;
import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Uci;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProtocolCodecTest {

    private final ProtocolCodec codec = new ProtocolCodec();

    @Test
    void encodesVersionedEnvelope() {
        String json = codec.encode(MessageType.PONG, new Messages.Pong());
        Envelope env = codec.decode(json);
        assertThat(env.version()).isEqualTo(ProtocolVersion.CURRENT);
        assertThat(env.type()).isEqualTo("pong");
    }

    @Test
    void refusesIncompatibleMajor() {
        String json = "{\"v\":[2,0],\"type\":\"ping\",\"payload\":{}}";
        assertThatThrownBy(() -> codec.decode(json))
                .isInstanceOf(IncompatibleProtocolVersionException.class);
    }

    @Test
    void acceptsHigherMinorSameMajor() {
        String json = "{\"v\":[1,5],\"type\":\"ping\",\"payload\":{}}";
        assertThat(codec.decode(json).type()).isEqualTo("ping");
    }

    @Test
    void rejectsMalformedEnvelope() {
        assertThatThrownBy(() -> codec.decode("{\"type\":\"ping\"}"))
                .isInstanceOf(ProtocolException.class);
        assertThatThrownBy(() -> codec.decode("not json"))
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    void roundTripsSnapshotWithCapability() {
        Fen start = Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        GameSnapshot snap = new GameSnapshot(
                "g1", 1, start, start, Side.WHITE, List.of(Uci.of("e2e4")),
                new ClockSample(180000, 180000, Side.WHITE, 1000),
                Optional.empty(), "wp", "bp", false, List.of(), List.of());
        Capability cap = new Capability(
                "c1", "s1", "g1", 1, CapabilityKind.MOVE,
                Map.of("uci", "e2e4"), 5000, "sig");

        String json = codec.encode(MessageType.SNAPSHOT, new Messages.Snapshot(snap, List.of(cap)));
        Messages.Snapshot decoded = codec.payload(codec.decode(json), Messages.Snapshot.class);

        assertThat(decoded.snapshot().fen()).isEqualTo(start);
        assertThat(decoded.snapshot().sideToMove()).isEqualTo(Side.WHITE);
        assertThat(decoded.snapshot().moves()).containsExactly(Uci.of("e2e4"));
        assertThat(decoded.capabilities()).hasSize(1);
        assertThat(decoded.capabilities().get(0).kind()).isEqualTo(CapabilityKind.MOVE);
        assertThat(decoded.capabilities().get(0).moveUci()).isEqualTo(Uci.of("e2e4"));
    }

    @Test
    void roundTripsResultDisposition() {
        Messages.Result rejected = new Messages.Result(
                Disposition.REJECTED, "i1", Optional.of("not your turn"),
                Optional.empty(), Optional.empty());
        String json = codec.encode(MessageType.RESULT, rejected);
        assertThat(json).contains("\"rejected\"");

        Messages.Result decoded = codec.payload(codec.decode(json), Messages.Result.class);
        assertThat(decoded.disposition()).isEqualTo(Disposition.REJECTED);
        assertThat(decoded.reason()).contains("not your turn");
        assertThat(decoded.snapshot()).isEmpty();
    }
}
