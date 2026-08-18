package com.chessoc.server.app;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Server configuration. The engine is reached either over TCP (an engine service) or by spawning a
 * local binary; with neither set, auth and transport start but chess calls fail. A blank DB URL keeps
 * state in memory.
 */
public record ServerConfig(
        int httpPort,
        int wsPort,
        byte[] tokenSecret,
        long tokenTtlMs,
        long ticketTtlMs,
        String engineHost,
        int enginePort,
        List<String> validatorCommand,
        List<String> searchCommand,
        int validatorWorkers,
        int searchWorkers,
        Duration validatorTimeout,
        Duration searchTimeout,
        String dbUrl,
        String dbUser,
        String dbPassword) {

    public static ServerConfig defaults() {
        return new ServerConfig(
                8080, 8081,
                "dev-secret-change-me".getBytes(StandardCharsets.UTF_8),
                Duration.ofHours(12).toMillis(),
                Duration.ofSeconds(30).toMillis(),
                "", 0,
                List.of(), List.of(),
                8, 2,
                Duration.ofSeconds(2), Duration.ofSeconds(30),
                "", "", "");
    }

    public static ServerConfig fromEnv() {
        ServerConfig d = defaults();
        return new ServerConfig(
                intEnv("CHESSOC_HTTP_PORT", d.httpPort()),
                intEnv("CHESSOC_WS_PORT", d.wsPort()),
                envOr("CHESSOC_TOKEN_SECRET", "dev-secret-change-me").getBytes(StandardCharsets.UTF_8),
                d.tokenTtlMs(), d.ticketTtlMs(),
                envOr("CHESSOC_ENGINE_HOST", ""),
                intEnv("CHESSOC_ENGINE_PORT", 0),
                words(System.getenv("CHESSOC_VALIDATOR_CMD")),
                words(System.getenv("CHESSOC_SEARCH_CMD")),
                intEnv("CHESSOC_VALIDATOR_WORKERS", d.validatorWorkers()),
                intEnv("CHESSOC_SEARCH_WORKERS", d.searchWorkers()),
                d.validatorTimeout(), d.searchTimeout(),
                envOr("CHESSOC_DB_URL", ""),
                envOr("CHESSOC_DB_USER", ""),
                envOr("CHESSOC_DB_PASSWORD", ""));
    }

    private static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }

    private static int intEnv(String key, int fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : Integer.parseInt(v);
    }

    private static List<String> words(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.trim().split("\\s+"));
    }
}
