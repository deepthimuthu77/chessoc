package com.chessoc.server.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FenTest {

    private static final String START =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    @Test
    void parsesStandardFields() {
        Fen fen = Fen.parse(START);
        assertThat(fen.sideToMove()).isEqualTo(Side.WHITE);
        assertThat(fen.castling()).isEqualTo("KQkq");
        assertThat(fen.enPassant()).isEqualTo("-");
        assertThat(fen.halfmoveClock()).isZero();
        assertThat(fen.fullmoveNumber()).isEqualTo(1);
        assertThat(fen.hasReserve()).isFalse();
    }

    @Test
    void reemitsVerbatim() {
        assertThat(Fen.parse(START).raw()).isEqualTo(START);
    }

    @Test
    void preservesPromotedMarkerAndReserveThroughRoundTrip() {
        // ~ on a promoted queen, and a crazyhouse reserve, must survive persistence.
        String s = "r1bqk2r/ppp2ppp/2n5/8/8/2Q~5/PPPP1PPP/RNB1KBNR[Pn] b KQkq - 4 8";
        Fen fen = Fen.parse(s);
        assertThat(fen.raw()).isEqualTo(s);
        assertThat(fen.board()).contains("Q~");
        assertThat(fen.reserve()).contains("Pn");
    }

    @Test
    void emptyReserveIsStillAReserve() {
        Fen fen = Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR[] w KQkq - 0 1");
        assertThat(fen.hasReserve()).isTrue();
        assertThat(fen.reserve()).contains("");
    }

    @Test
    void standardVariantRejectsReserveBracket() {
        Fen fen = Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR[] w KQkq - 0 1");
        assertThatThrownBy(() -> fen.requireConsistentWith(Variant.STANDARD))
                .isInstanceOf(IllegalArgumentException.class);
        fen.requireConsistentWith(Variant.CRAZYHOUSE); // no throw
    }

    @Test
    void rejectsWrongFieldCount() {
        assertThatThrownBy(() -> Fen.parse("8/8/8/8 w - -"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
