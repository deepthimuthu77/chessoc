package com.chessoc.server.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.PieceType;
import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Terminal;
import com.chessoc.server.domain.Uci;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidatorProtocolTest {

    private final ValidatorProtocol proto = new ValidatorProtocol();
    private static final Fen START =
            Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

    @Test
    void buildsMoveListPositionLine() {
        String req = proto.applyRequest(START, List.of(Uci.of("e2e4"), Uci.of("e7e5")), Uci.of("g1f3"));
        assertThat(req).isEqualTo("position fen " + START.raw() + " moves e2e4 e7e5\napply g1f3");
    }

    @Test
    void omitsMovesWhenHistoryEmpty() {
        assertThat(proto.position(START, List.of())).isEqualTo("position fen " + START.raw());
    }

    @Test
    void parsesLegalApplyWithCapture() {
        String json = """
                {"legal":true,"fen":"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                 "terminal":"none","inCheck":false,"sideToMove":"b","capturedDropType":"n"}""";
        ApplyResult r = proto.parseApply(json);
        assertThat(r.legal()).isTrue();
        assertThat(r.terminal()).isEqualTo(Terminal.NONE);
        assertThat(r.sideToMove()).contains(Side.BLACK);
        assertThat(r.capturedDropType()).contains(PieceType.KNIGHT);
    }

    @Test
    void parsesIllegalApplyWithReason() {
        ApplyResult r = proto.parseApply("{\"legal\":false,\"reason\":\"pinned\"}");
        assertThat(r.legal()).isFalse();
        assertThat(r.resultingPosition()).isEmpty();
        assertThat(r.reason()).contains("pinned");
    }

    @Test
    void parsesCheckmateTerminal() {
        String json = """
                {"legal":true,"fen":"8/8/8/8/8/8/8/8 b - - 0 1","terminal":"checkmate",
                 "inCheck":true,"sideToMove":"b","capturedDropType":null}""";
        ApplyResult r = proto.parseApply(json);
        assertThat(r.terminal()).isEqualTo(Terminal.CHECKMATE);
        assertThat(r.inCheck()).isTrue();
        assertThat(r.capturedDropType()).isEmpty();
    }

    @Test
    void parsesLegalMoves() {
        List<Uci> moves = proto.parseLegalMoves("{\"moves\":[\"e2e4\",\"e2e3\",\"a7a8q\"]}");
        assertThat(moves).containsExactly(Uci.of("e2e4"), Uci.of("e2e3"), Uci.of("a7a8q"));
    }

    @Test
    void parsesCanMate() {
        assertThat(proto.parseCanMate("{\"canMate\":true}")).isTrue();
        assertThat(proto.parseCanMate("{\"canMate\":false}")).isFalse();
    }

    @Test
    void parsesRealEngineLegalApply() {
        // vector64 shape: ok + legal, extra fields tolerated.
        String json = "{\"ok\":true,\"legal\":true,"
                + "\"fen\":\"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1\","
                + "\"sideToMove\":\"b\",\"inCheck\":false,\"terminal\":\"none\",\"capturedDropType\":null}";
        ApplyResult r = proto.parseApply(json);
        assertThat(r.legal()).isTrue();
        assertThat(r.sideToMove()).contains(Side.BLACK);
    }

    @Test
    void treatsRealEngineIllegalErrorAsIllegalNotFault() {
        // vector64 reports illegal via the error field; it must not raise.
        ApplyResult r = proto.parseApply(
                "{\"ok\":false,\"error\":\"illegal_move\",\"reason\":\"not legal in this position\"}");
        assertThat(r.legal()).isFalse();
        assertThat(r.reason()).contains("not legal in this position");
    }

    @Test
    void malformedErrorStillRaises() {
        assertThatThrownBy(() -> proto.parseApply("{\"ok\":false,\"error\":\"malformed_request\",\"reason\":\"bad fen\"}"))
                .isInstanceOf(EngineException.class)
                .satisfies(e -> assertThat(((EngineException) e).code()).isEqualTo(EngineErrorCode.MALFORMED_REQUEST));
    }

    @Test
    void raisesTypedErrorReplies() {
        assertThatThrownBy(() -> proto.parseApply("{\"error\":\"malformed_request\",\"message\":\"bad fen\"}"))
                .isInstanceOf(EngineException.class)
                .satisfies(e -> assertThat(((EngineException) e).code()).isEqualTo(EngineErrorCode.MALFORMED_REQUEST));
    }

    @Test
    void unparseableReplyIsInternalError() {
        assertThatThrownBy(() -> proto.parseStatus("garbage"))
                .isInstanceOf(EngineException.class)
                .satisfies(e -> assertThat(((EngineException) e).code()).isEqualTo(EngineErrorCode.INTERNAL_ERROR));
    }
}
