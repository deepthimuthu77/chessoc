package com.chessoc.server.engine;

import com.chessoc.server.domain.Side;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Shared parsing for engine JSON replies. A reply we cannot read is an engine fault. */
final class EngineJson {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private EngineJson() {
    }

    static JsonNode read(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new EngineException(EngineErrorCode.INTERNAL_ERROR, "unparseable reply: " + json);
        }
    }

    static String required(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            throw new EngineException(EngineErrorCode.INTERNAL_ERROR, "reply missing '" + field + "'");
        }
        return v.asText();
    }

    /** Accepts FEN ("w"/"b") and long ("white"/"black") side tokens. */
    static Side side(String token) {
        return switch (token) {
            case "w", "white" -> Side.WHITE;
            case "b", "black" -> Side.BLACK;
            default -> throw new EngineException(EngineErrorCode.INTERNAL_ERROR, "bad side token: " + token);
        };
    }
}
