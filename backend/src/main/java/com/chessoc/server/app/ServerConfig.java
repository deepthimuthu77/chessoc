package com.chessoc.server.app;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Server configuration. Engine commands are empty by default — auth and transport still start, but
 * chess calls fail until a validator binary is wired in.
 */
public record ServerConfig(
        int httpPort,
        int wsPort,
        byte[] tokenSecret,
        long tokenTtlMs,
        long ticketTtlMs,
        List<String> validatorCommand,
        List<String> searchCommand,
        int validatorWorkers,
        int searchWorkers,
        Duration validatorTimeout,
        Duration searchTimeout) {

    public static ServerConfig defaults() {
        return new ServerConfig(
                8080, 8081,
                "dev-secret-change-me".getBytes(StandardCharsets.UTF_8),
                Duration.ofHours(12).toMillis(),
                Duration.ofSeconds(30).toMillis(),
                List.of(), List.of(),
                8, 2,
                Duration.ofSeconds(2), Duration.ofSeconds(30));
    }

    public static ServerConfig fromEnv() {
        ServerConfig d = defaults();
        return new ServerConfig(
                intEnv("CHESSOC_HTTP_PORT", d.httpPort()),
                intEnv("CHESSOC_WS_PORT", d.wsPort()),
                envOr("CHESSOC_TOKEN_SECRET", "dev-secret-change-me").getBytes(StandardCharsets.UTF_8),
                d.tokenTtlMs(), d.ticketTtlMs(),
                words(System.getenv("CHESSOC_VALIDATOR_CMD")),
                words(System.getenv("CHESSOC_SEARCH_CMD")),
                d.validatorWorkers(), d.searchWorkers(),
                d.validatorTimeout(), d.searchTimeout());
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
