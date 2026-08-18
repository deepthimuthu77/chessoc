package com.chessoc.server.match.state;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Uci;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Postgres-backed persistence. Stores the resulting per-ply FEN (reserve included) so drop-variant
 * state is reconstructable. Match State is the only writer. Uses a plain connection per call; a pool
 * is a drop-in improvement.
 */
public final class PostgresMatchRepository implements MatchRepository {

    private final String url;
    private final String user;
    private final String password;

    public PostgresMatchRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
        initSchema();
    }

    private void initSchema() {
        String ddl = """
                CREATE TABLE IF NOT EXISTS match_plies (
                    game_id  TEXT   NOT NULL,
                    version  BIGINT NOT NULL,
                    fen      TEXT   NOT NULL,
                    uci      TEXT   NOT NULL,
                    PRIMARY KEY (game_id, version)
                )""";
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute(ddl);
        } catch (SQLException e) {
            throw new IllegalStateException("schema init failed", e);
        }
    }

    @Override
    public void append(String gameId, PersistedPly ply) {
        String sql = "INSERT INTO match_plies(game_id, version, fen, uci) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (game_id, version) DO NOTHING";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, gameId);
            ps.setLong(2, ply.version());
            ps.setString(3, ply.fen().raw());
            ps.setString(4, ply.move().value());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("append failed", e);
        }
    }

    @Override
    public List<PersistedPly> plies(String gameId) {
        String sql = "SELECT version, fen, uci FROM match_plies WHERE game_id = ? ORDER BY version";
        List<PersistedPly> plies = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, gameId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    plies.add(new PersistedPly(rs.getLong("version"),
                            Fen.parse(rs.getString("fen")), Uci.of(rs.getString("uci"))));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("query failed", e);
        }
        return plies;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}
