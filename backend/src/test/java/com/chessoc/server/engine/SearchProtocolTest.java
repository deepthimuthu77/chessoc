package com.chessoc.server.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessoc.server.domain.Uci;
import org.junit.jupiter.api.Test;

class SearchProtocolTest {

    private final SearchProtocol proto = new SearchProtocol();

    @Test
    void encodesGoFromBudget() {
        assertThat(proto.go(SearchBudget.movetime(1000))).isEqualTo("go movetime 1000");
        assertThat(proto.go(SearchBudget.depth(20))).isEqualTo("go depth 20");
        assertThat(proto.go(SearchBudget.nodes(500000))).isEqualTo("go nodes 500000");
    }

    @Test
    void parsesCentipawnReply() {
        String json = "{\"bestMove\":\"e2e4\",\"score\":{\"cp\":31},\"pv\":[\"e2e4\",\"e7e5\"],\"depth\":22,\"ponder\":\"e7e5\"}";
        SearchResult r = proto.parse(json);
        assertThat(r.bestMove()).isEqualTo(Uci.of("e2e4"));
        assertThat(r.score()).isEqualTo(new Score.Cp(31));
        assertThat(r.pv()).containsExactly(Uci.of("e2e4"), Uci.of("e7e5"));
        assertThat(r.depth()).isEqualTo(22);
        assertThat(r.ponder()).contains(Uci.of("e7e5"));
    }

    @Test
    void parsesMateReply() {
        SearchResult r = proto.parse("{\"bestMove\":\"h5f7\",\"score\":{\"mate\":2},\"pv\":[\"h5f7\"],\"depth\":10}");
        assertThat(r.score()).isEqualTo(new Score.Mate(2));
        assertThat(r.ponder()).isEmpty();
    }
}
