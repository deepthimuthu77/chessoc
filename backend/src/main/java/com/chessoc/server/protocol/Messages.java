package com.chessoc.server.protocol;

import com.chessoc.server.capability.Capability;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Payload records for the live message catalog (§8.2). Envelope framing is in {@link ProtocolCodec}. */
public final class Messages {

    private Messages() {
    }

    // ---- client -> server ----

    public record Auth(String ticket, Optional<String> reconnectToken, Optional<Long> lastKnownVersion) {
    }

    public record Intent(String intentId, String capabilityId, Map<String, String> params, long clientTimestamp) {
        public Intent {
            params = params == null ? Map.of() : Map.copyOf(params);
        }
    }

    public record Resync(String gameId, long lastKnownVersion) {
    }

    public record Ping() {
    }

    // ---- server -> client ----

    public record AuthOk(String sessionId, String playerId, String connectionId) {
    }

    public record AuthErr(String reason) {
    }

    public record Snapshot(GameSnapshot snapshot, List<Capability> capabilities) {
    }

    public record Capabilities(List<Capability> capabilities) {
    }

    public record Receipt(String intentId, long serverTimestamp) {
    }

    public record Result(
            Disposition disposition,
            String intentId,
            Optional<String> reason,
            Optional<GameSnapshot> snapshot,
            Optional<List<Capability>> capabilities) {
    }

    public record ResyncOk(GameSnapshot snapshot, List<Capability> capabilities) {
    }

    public record Error(String code, String message) {
    }

    public record Pong() {
    }
}
