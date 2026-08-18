package com.chessoc.server.engine.adapter;

import java.time.Duration;

/** One engine process (or a stand-in). Serves a single request at a time; not multiplexed. */
public interface EngineWorker extends AutoCloseable {

    /** Send the request lines, return the reply line. Exceeding {@code timeout} is an internal fault. */
    String request(String payload, Duration timeout);

    boolean isReady();

    @Override
    void close();
}
