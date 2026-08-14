package com.chessoc.server.capability;

import com.chessoc.server.domain.Uci;
import java.util.Map;

/**
 * A single-use, version-bound authorisation for exactly one action instance. Promotion targets are
 * distinct capabilities (one UCI each). {@code signature} is opaque — only the issuing server reads it.
 */
public record Capability(
        String id,
        String sessionId,
        String gameId,
        long gameVersion,
        CapabilityKind kind,
        Map<String, String> params,
        long expiresAtEpochMs,
        String signature) {

    public Capability {
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public boolean isExpired(long nowEpochMs) {
        return nowEpochMs >= expiresAtEpochMs;
    }

    /** An intent's capability must reference the current projection version exactly. */
    public boolean boundToVersion(long projectionVersion) {
        return gameVersion == projectionVersion;
    }

    public boolean usableAt(long nowEpochMs, long projectionVersion) {
        return !isExpired(nowEpochMs) && boundToVersion(projectionVersion);
    }

    /** The exact UCI a MOVE capability permits. */
    public Uci moveUci() {
        if (kind != CapabilityKind.MOVE) {
            throw new IllegalStateException("not a move capability: " + kind);
        }
        return Uci.of(params.get("uci"));
    }
}
