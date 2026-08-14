package com.chessoc.server.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chessoc.server.domain.PieceType;
import com.chessoc.server.domain.Side;
import org.junit.jupiter.api.Test;

class ReserveTest {

    @Test
    void parsesColourByCase() {
        Reserve r = Reserve.parse("PPnn");
        assertThat(r.count(Side.WHITE, PieceType.PAWN)).isEqualTo(2);
        assertThat(r.count(Side.BLACK, PieceType.KNIGHT)).isEqualTo(2);
    }

    @Test
    void encodesCanonicallyRegardlessOfInputOrder() {
        assertThat(Reserve.parse("nPnP").encode()).isEqualTo("PPnn");
    }

    @Test
    void addAndRemoveTrackCounts() {
        Reserve r = new Reserve();
        r.add(Side.WHITE, PieceType.QUEEN);
        r.add(Side.WHITE, PieceType.QUEEN);
        r.remove(Side.WHITE, PieceType.QUEEN);
        assertThat(r.count(Side.WHITE, PieceType.QUEEN)).isEqualTo(1);
        assertThat(r.encode()).isEqualTo("Q");
    }

    @Test
    void removingAbsentPieceFails() {
        assertThatThrownBy(() -> new Reserve().remove(Side.BLACK, PieceType.ROOK))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void emptyReserveEncodesEmpty() {
        assertThat(new Reserve().encode()).isEmpty();
        assertThat(new Reserve().isEmpty()).isTrue();
    }
}
