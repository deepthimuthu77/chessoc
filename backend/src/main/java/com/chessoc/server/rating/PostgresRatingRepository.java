package com.chessoc.server.rating;

import com.chessoc.server.db.Db;
import com.chessoc.server.matchmaking.Rating;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Postgres-backed ratings. */
public final class PostgresRatingRepository implements RatingRepository {

    private final Db db;

    public PostgresRatingRepository(Db db) {
        this.db = db;
        db.exec("""
                CREATE TABLE IF NOT EXISTS ratings (
                    account_id TEXT             NOT NULL,
                    variant    TEXT             NOT NULL,
                    speed      TEXT             NOT NULL,
                    rating     DOUBLE PRECISION NOT NULL,
                    deviation  DOUBLE PRECISION NOT NULL,
                    volatility DOUBLE PRECISION NOT NULL,
                    games      INTEGER          NOT NULL,
                    PRIMARY KEY (account_id, variant, speed)
                )""");
    }

    @Override
    public RatingRow get(String accountId, String variant, String speed) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT rating, deviation, volatility, games FROM ratings "
                             + "WHERE account_id=? AND variant=? AND speed=?")) {
            ps.setString(1, accountId);
            ps.setString(2, variant);
            ps.setString(3, speed);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new RatingRow(accountId,
                            new Rating(rs.getDouble(1), rs.getDouble(2), rs.getDouble(3)), rs.getInt(4));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("rating get failed", e);
        }
        return new RatingRow(accountId, Rating.provisional(), 0);
    }

    @Override
    public void save(String accountId, String variant, String speed, RatingRow row) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO ratings(account_id, variant, speed, rating, deviation, volatility, games) "
                             + "VALUES (?,?,?,?,?,?,?) "
                             + "ON CONFLICT (account_id, variant, speed) DO UPDATE SET "
                             + "rating=EXCLUDED.rating, deviation=EXCLUDED.deviation, "
                             + "volatility=EXCLUDED.volatility, games=EXCLUDED.games")) {
            ps.setString(1, accountId);
            ps.setString(2, variant);
            ps.setString(3, speed);
            ps.setDouble(4, row.rating().rating());
            ps.setDouble(5, row.rating().deviation());
            ps.setDouble(6, row.rating().volatility());
            ps.setInt(7, row.games());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("rating save failed", e);
        }
    }

    @Override
    public List<RatingRow> top(String variant, String speed, int limit) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT account_id, rating, deviation, volatility, games FROM ratings "
                             + "WHERE variant=? AND speed=? ORDER BY rating DESC LIMIT ?")) {
            ps.setString(1, variant);
            ps.setString(2, speed);
            ps.setInt(3, limit);
            return readRows(ps);
        } catch (SQLException e) {
            throw new IllegalStateException("rating top failed", e);
        }
    }

    @Override
    public List<RatingRow> among(List<String> accountIds, String variant, String speed) {
        if (accountIds.isEmpty()) {
            return List.of();
        }
        String in = String.join(",", accountIds.stream().map(x -> "?").toList());
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT account_id, rating, deviation, volatility, games FROM ratings "
                             + "WHERE variant=? AND speed=? AND account_id IN (" + in + ") ORDER BY rating DESC")) {
            ps.setString(1, variant);
            ps.setString(2, speed);
            for (int i = 0; i < accountIds.size(); i++) {
                ps.setString(3 + i, accountIds.get(i));
            }
            return readRows(ps);
        } catch (SQLException e) {
            throw new IllegalStateException("rating among failed", e);
        }
    }

    private static List<RatingRow> readRows(PreparedStatement ps) throws SQLException {
        List<RatingRow> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new RatingRow(rs.getString(1),
                        new Rating(rs.getDouble(2), rs.getDouble(3), rs.getDouble(4)), rs.getInt(5)));
            }
        }
        return out;
    }
}
