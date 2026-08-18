package com.chessoc.server.engine.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Uci;
import com.chessoc.server.domain.Variant;
import com.chessoc.server.engine.ApplyResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exercises the real process I/O path against a subprocess stand-in. */
class ProcessEngineWorkerTest {

    private static final Fen START =
            Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

    private static List<String> fakeEngineCommand() {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return List.of(java, "-cp", System.getProperty("java.class.path"),
                "com.chessoc.server.engine.adapter.FakeEngineMain");
    }

    @Test
    void appliesMoveOverRealProcess() {
        List<String> cmd = fakeEngineCommand();
        try (EngineWorkerPool pool = new EngineWorkerPool(1, () -> new ProcessEngineWorker(cmd))) {
            PooledEngineAdapter adapter =
                    new PooledEngineAdapter(pool, pool, Duration.ofSeconds(10), Duration.ofSeconds(10));

            ApplyResult first = adapter.apply(Variant.STANDARD, START, List.of(), Uci.of("e2e4"));
            assertThat(first.legal()).isTrue();
            assertThat(first.resultingPosition()).isPresent();

            // worker is reused for a second request
            List<Uci> moves = adapter.legalMoves(Variant.STANDARD, START, List.of());
            assertThat(moves).contains(Uci.of("e2e4"), Uci.of("d2d4"));
        }
    }
}
