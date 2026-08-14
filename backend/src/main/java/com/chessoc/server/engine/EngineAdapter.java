package com.chessoc.server.engine;

/**
 * The only module that talks to the engine pool (§4.4) — the system's single network boundary.
 * Combines both tiers and owns worker lifecycle. Knows no game rules; pure protocol translation.
 */
public interface EngineAdapter extends ValidatorClient, SearchClient {

    /** Cheap readiness probe; a failing worker is evicted and respawned. */
    boolean isReady();

    void shutdown();
}
