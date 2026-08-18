package com.chessoc.server.engine.adapter;

import java.time.Duration;
import java.util.function.Function;

/** In-JVM worker whose reply is computed from the request payload. */
final class FakeEngineWorker implements EngineWorker {

    private final Function<String, String> responder;
    private boolean closed;

    FakeEngineWorker(Function<String, String> responder) {
        this.responder = responder;
    }

    @Override
    public String request(String payload, Duration timeout) {
        return responder.apply(payload);
    }

    @Override
    public boolean isReady() {
        return !closed;
    }

    @Override
    public void close() {
        closed = true;
    }
}
