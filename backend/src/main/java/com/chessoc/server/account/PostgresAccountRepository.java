package com.chessoc.server.account;

import com.chessoc.server.db.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Postgres-backed accounts. */
public final class PostgresAccountRepository implements AccountRepository {

    private final Db db;

    public PostgresAccountRepository(Db db) {
        this.db = db;
        db.exec("""
                CREATE TABLE IF NOT EXISTS accounts (
                    id            TEXT   PRIMARY KEY,
                    username      TEXT   NOT NULL UNIQUE,
                    email         TEXT   NOT NULL UNIQUE,
                    password_hash TEXT   NOT NULL,
                    created_at    BIGINT NOT NULL
                )""");
    }

    @Override
    public void create(Account a) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO accounts(id, username, email, password_hash, created_at) VALUES (?,?,?,?,?)")) {
            ps.setString(1, a.id());
            ps.setString(2, a.username());
            ps.setString(3, a.email());
            ps.setString(4, a.passwordHash());
            ps.setLong(5, a.createdAtEpochMs());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("username or email already taken", e);
        }
    }

    @Override
    public Optional<Account> findById(String id) {
        return queryOne("SELECT * FROM accounts WHERE id = ?", id);
    }

    @Override
    public Optional<Account> findByEmail(String email) {
        return queryOne("SELECT * FROM accounts WHERE lower(email) = lower(?)", email);
    }

    @Override
    public Optional<Account> findByUsername(String username) {
        return queryOne("SELECT * FROM accounts WHERE lower(username) = lower(?)", username);
    }

    @Override
    public void updatePassword(String id, String passwordHash) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("UPDATE accounts SET password_hash=? WHERE id=?")) {
            ps.setString(1, passwordHash);
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("password update failed", e);
        }
    }

    @Override
    public void delete(String id) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM accounts WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("account delete failed", e);
        }
    }

    @Override
    public List<Account> byIds(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String in = String.join(",", ids.stream().map(x -> "?").toList());
        List<Account> out = new ArrayList<>();
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM accounts WHERE id IN (" + in + ")")) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setString(i + 1, ids.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(read(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("byIds failed", e);
        }
        return out;
    }

    private Optional<Account> queryOne(String sql, String arg) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, arg);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(read(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("query failed", e);
        }
    }

    private static Account read(ResultSet rs) throws SQLException {
        return new Account(rs.getString("id"), rs.getString("username"), rs.getString("email"),
                rs.getString("password_hash"), rs.getLong("created_at"));
    }
}
