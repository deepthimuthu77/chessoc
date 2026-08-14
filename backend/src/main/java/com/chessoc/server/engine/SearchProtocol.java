package com.chessoc.server.engine;

import com.chessoc.server.domain.Uci;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Search wire codec. Encodes the {@code go} command and parses the requested JSON search reply
 * ({@code {"bestMove","score":{cp|mate},"pv":[...],"depth"}}) rather than scraping info lines.
 */
public final class SearchProtocol {

    public String go(SearchBudget budget) {
        return "go " + budget.uci();
    }

    public SearchResult parse(String json) {
        JsonNode n = EngineJson.read(json);
        Uci best = Uci.of(EngineJson.required(n, "bestMove"));
        JsonNode s = n.path("score");
        Score score = s.hasNonNull("mate")
                ? new Score.Mate(s.get("mate").asInt())
                : new Score.Cp(s.path("cp").asInt());
        List<Uci> pv = new ArrayList<>();
        for (JsonNode m : n.path("pv")) {
            pv.add(Uci.of(m.asText()));
        }
        Optional<Uci> ponder = n.hasNonNull("ponder")
                ? Optional.of(Uci.of(n.get("ponder").asText()))
                : Optional.empty();
        return new SearchResult(best, score, pv, n.path("depth").asInt(0), ponder);
    }
}
