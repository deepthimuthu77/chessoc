package com.chessoc.server.protocol;

import com.fasterxml.jackson.databind.JsonNode;

/** Decoded wire envelope: {@code { "v":[major,minor], "type":..., "payload":{...} }}. */
public record Envelope(ProtocolVersion version, String type, JsonNode payload) {
}
