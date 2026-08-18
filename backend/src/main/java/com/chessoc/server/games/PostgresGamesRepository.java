package com.chessoc.server.games;

import com.chessoc.server.db.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Postgres-backed game metadata. */
public final class PostgresGamesRepository implements GamesRepository {

    private final Db db;

    public PostgresGamesRepository(Db db) {
        this.db = db;
        db.exec("""
                CREATE TABLE IF NOT EXISTS games (
                    id                TEXT    PRIMARY KEY,
                    white_id          TEXT    NOT NULL,
                    black_id          TEXT,
                    variant           TEXT    NOT NULL,
                    speed             TEXT    NOT NULL,
                    rated             BOOLEAN NOT NULL,
                    result_type       TEXT    NOT NULL,
                    winner_account_id TEXT,
                    reason            TEXT,
                    ended_at          BIGINT  NOT NULL
                )""");
    }

    @Override
    public void record(GameRecord g) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO games(id, white_id, black_id, variant, speed, rated, result_type, "
                             + "winner_account_id, reason, ended_at) VALUES (?,?,?,?,?,?,?,?,?,?) "
                             + "ON CONFLICT (id) DO NOTHING")) {
            ps.setString(1, g.id());
            ps.setString(2, g.whiteId());
            ps.setString(3, g.blackId());
            ps.setString(4, g.variant());
            ps.setString(5, g.speed());
            ps.setBoolean(6, g.rated());
            ps.setString(7, g.resultType());
            ps.setString(8, g.winnerAccountId().isEmpty() ? null : g.winnerAccountId());
            ps.setString(9, g.reason());
            ps.setLong(10, g.endedAtEpochMs());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("game record failed", e);
        }
    }

    @Override
    public List<GameRecord> forAccount(String accountId, int limit) {
        List<GameRecord> out = new ArrayList<>();
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM games WHERE white_id=? OR black_id=? ORDER BY ended_at DESC LIMIT ?")) {
            ps.setString(1, accountId);
            ps.setString(2, accountId);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(read(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("games query failed", e);
        }
        return out;
    }

    @Override
    public PlayerStats statsFor(String accountId) {
        String sql = """
                SELECT
                  COUNT(*) FILTER (WHERE winner_account_id = ?) AS wins,
                  COUNT(*) FILTER (WHERE result_type = 'DRAW') AS draws,
                  COUNT(*) FILTER (WHERE result_type = 'WIN' AND winner_account_id <> ?) AS losses
                FROM games WHERE white_id = ? OR black_id = ?""";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, accountId);
            ps.setString(2, accountId);
            ps.setString(3, accountId);
            ps.setString(4, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new PlayerStats(rs.getInt("wins"), rs.getInt("losses"), rs.getInt("draws"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("stats query failed", e);
        }
    }

    private static GameRecord read(ResultSet rs) throws SQLException {
        String winner = rs.getString("winner_account_id");
        return new GameRecord(rs.getString("id"), rs.getString("white_id"), rs.getString("black_id"),
                rs.getString("variant"), rs.getString("speed"), rs.getBoolean("rated"),
                rs.getString("result_type"), winner == null ? "" : winner,
                rs.getString("reason"), rs.getLong("ended_at"));
    }
}
