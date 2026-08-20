package com.chessoc.server.social;

import com.chessoc.server.db.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Postgres-backed mutual friendships (one row per direction). */
public final class PostgresFriendRepository implements FriendRepository {

    private final Db db;

    public PostgresFriendRepository(Db db) {
        this.db = db;
        db.exec("""
                CREATE TABLE IF NOT EXISTS friends (
                    account_id TEXT NOT NULL,
                    friend_id  TEXT NOT NULL,
                    PRIMARY KEY (account_id, friend_id)
                )""");
        db.exec("""
                CREATE TABLE IF NOT EXISTS friend_requests (
                    from_id TEXT NOT NULL,
                    to_id   TEXT NOT NULL,
                    PRIMARY KEY (from_id, to_id)
                )""");
    }

    @Override
    public void add(String accountId, String friendId) {
        if (accountId.equals(friendId)) {
            return;
        }
        insert(accountId, friendId);
        insert(friendId, accountId);
    }

    private void insert(String a, String b) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO friends(account_id, friend_id) VALUES (?,?) ON CONFLICT DO NOTHING")) {
            ps.setString(1, a);
            ps.setString(2, b);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("friend add failed", e);
        }
    }

    @Override
    public List<String> friends(String accountId) {
        List<String> out = new ArrayList<>();
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT friend_id FROM friends WHERE account_id=?")) {
            ps.setString(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("friends query failed", e);
        }
        return out;
    }

    @Override
    public boolean areFriends(String accountId, String friendId) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM friends WHERE account_id=? AND friend_id=?")) {
            ps.setString(1, accountId);
            ps.setString(2, friendId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("areFriends failed", e);
        }
    }

    @Override
    public void addRequest(String fromId, String toId) {
        if (fromId.equals(toId)) {
            return;
        }
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO friend_requests(from_id, to_id) VALUES (?,?) ON CONFLICT DO NOTHING")) {
            ps.setString(1, fromId);
            ps.setString(2, toId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("friend request failed", e);
        }
    }

    @Override
    public void removeRequest(String fromId, String toId) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM friend_requests WHERE from_id=? AND to_id=?")) {
            ps.setString(1, fromId);
            ps.setString(2, toId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("friend request delete failed", e);
        }
    }

    @Override
    public boolean hasRequest(String fromId, String toId) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM friend_requests WHERE from_id=? AND to_id=?")) {
            ps.setString(1, fromId);
            ps.setString(2, toId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("hasRequest failed", e);
        }
    }

    @Override
    public List<String> incomingRequests(String accountId) {
        return requestColumn("SELECT from_id FROM friend_requests WHERE to_id=?", accountId);
    }

    @Override
    public List<String> outgoingRequests(String accountId) {
        return requestColumn("SELECT to_id FROM friend_requests WHERE from_id=?", accountId);
    }

    private List<String> requestColumn(String sql, String id) {
        List<String> out = new ArrayList<>();
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("friend requests query failed", e);
        }
        return out;
    }
}
