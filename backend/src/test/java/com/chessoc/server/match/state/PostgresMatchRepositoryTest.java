package com.chessoc.server.match.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Uci;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Runs only when a test database is provided (e.g. against the compose Postgres):
 * {@code CHESSOC_TEST_DB_URL / _USER / _PASSWORD}.
 */
@EnabledIfEnvironmentVariable(named = "CHESSOC_TEST_DB_URL", matches = ".+")
class PostgresMatchRepositoryTest {

    private PostgresMatchRepository repo() {
        return new PostgresMatchRepository(
                System.getenv("CHESSOC_TEST_DB_URL"),
                System.getenv().getOrDefault("CHESSOC_TEST_DB_USER", "chessoc"),
                System.getenv().getOrDefault("CHESSOC_TEST_DB_PASSWORD", "chessoc"));
    }

    @Test
    void persistsAndReadsPliesInOrder() {
        PostgresMatchRepository repo = repo();
        String gameId = "g-" + UUID.randomUUID();
        Fen fen = Fen.parse("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1");

        repo.append(gameId, new PersistedPly(1, fen, Uci.of("e2e4")));
        repo.append(gameId, new PersistedPly(2, fen, Uci.of("e7e5")));
        repo.append(gameId, new PersistedPly(1, fen, Uci.of("e2e4"))); // duplicate ignored

        List<PersistedPly> plies = repo.plies(gameId);
        assertThat(plies).hasSize(2);
        assertThat(plies.get(0).version()).isEqualTo(1);
        assertThat(plies.get(1).move()).isEqualTo(Uci.of("e7e5"));
    }
}
