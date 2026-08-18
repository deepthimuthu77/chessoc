package com.chessoc.server.engine.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Uci;
import com.chessoc.server.domain.Variant;
import com.chessoc.server.engine.ApplyResult;
import com.chessoc.server.engine.EngineErrorCode;
import com.chessoc.server.engine.EngineException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class PooledEngineAdapterTest {

    private static final Fen START =
            Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    private PooledEngineAdapter adapter(int size, Function<String, String> responder) {
        EngineWorkerPool pool = new EngineWorkerPool(size, () -> new FakeEngineWorker(responder));
        return new PooledEngineAdapter(pool, pool, TIMEOUT, TIMEOUT);
    }

    @Test
    void appliesAndParsesLegalMove() {
        AtomicReference<String> seen = new AtomicReference<>();
        PooledEngineAdapter adapter = adapter(1, payload -> {
            seen.set(payload);
            return "{\"legal\":true,\"fen\":\"" + START.raw()
                    + "\",\"terminal\":\"none\",\"inCheck\":false,\"sideToMove\":\"b\"}";
        });

        ApplyResult r = adapter.apply(Variant.STANDARD, START, List.of(), Uci.of("e2e4"));

        assertThat(r.legal()).isTrue();
        assertThat(r.sideToMove()).contains(Side.BLACK);
        assertThat(seen.get()).contains("setoption name UCI_Variant value standard");
        assertThat(seen.get()).endsWith("apply e2e4");
    }

    @Test
    void bughouseSetsVariantDrawRules() {
        AtomicReference<String> seen = new AtomicReference<>();
        PooledEngineAdapter adapter = adapter(1, payload -> {
            seen.set(payload);
            return "{\"moves\":[]}";
        });

        adapter.legalMoves(Variant.BUGHOUSE, START, List.of());

        assertThat(seen.get()).contains("setoption name DrawRules value variant");
    }

    @Test
    void internalErrorEvictsWorkerButKeepsPoolFull() {
        EngineWorkerPool pool = new EngineWorkerPool(2, () -> new FakeEngineWorker(payload -> {
            throw new EngineException(EngineErrorCode.INTERNAL_ERROR, "boom");
        }));
        PooledEngineAdapter adapter = new PooledEngineAdapter(pool, pool, TIMEOUT, TIMEOUT);

        assertThatThrownBy(() -> adapter.status(Variant.STANDARD, START))
                .isInstanceOf(EngineException.class);
        assertThat(pool.available()).isEqualTo(2); // faulted worker replaced
    }

    @Test
    void malformedReplyKeepsWorker() {
        EngineWorkerPool pool = new EngineWorkerPool(1, () -> new FakeEngineWorker(
                payload -> "{\"error\":\"malformed_request\",\"message\":\"bad\"}"));
        PooledEngineAdapter adapter = new PooledEngineAdapter(pool, pool, TIMEOUT, TIMEOUT);

        assertThatThrownBy(() -> adapter.status(Variant.STANDARD, START))
                .isInstanceOf(EngineException.class)
                .satisfies(e -> assertThat(((EngineException) e).code()).isEqualTo(EngineErrorCode.MALFORMED_REQUEST));
        assertThat(pool.available()).isEqualTo(1); // healthy worker returned
    }
}
