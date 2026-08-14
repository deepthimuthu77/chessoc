package com.chessoc.server.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UciTest {

    @Test
    void classifiesMoveForms() {
        assertThat(Uci.of("e2e4").isPromotion()).isFalse();
        assertThat(Uci.of("e2e4").isDrop()).isFalse();
        assertThat(Uci.of("a7a8q").isPromotion()).isTrue();
        assertThat(Uci.of("P@e4").isDrop()).isTrue();
    }

    @Test
    void rejectsMalformed() {
        assertThatThrownBy(() -> Uci.of("e2e9")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Uci.of("e2e4k")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Uci.of("K@e4")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Uci.of("")).isInstanceOf(IllegalArgumentException.class);
    }
}
