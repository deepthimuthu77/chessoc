package com.chessoc.server.match.state;

import com.chessoc.server.db.Db;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** PostgreSQL checkpoints for unfinished rooms. */
public final class PostgresLiveRoomRepository implements LiveRoomRepository {

    private final Db db;
    private final ObjectMapper mapper = new ObjectMapper();

    public PostgresLiveRoomRepository(Db db) {
        this.db = db;
        db.exec("""
                CREATE TABLE IF NOT EXISTS live_rooms (
                    room_id    TEXT   PRIMARY KEY,
                    state_json TEXT   NOT NULL,
                    updated_at BIGINT NOT NULL
                )""");
    }

    @Override
    public void save(LiveRoomState room) {
        String sql = "INSERT INTO live_rooms(room_id, state_json, updated_at) VALUES (?, ?, ?) "
                + "ON CONFLICT (room_id) DO UPDATE SET state_json = EXCLUDED.state_json, "
                + "updated_at = EXCLUDED.updated_at";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, room.roomId());
            ps.setString(2, mapper.writeValueAsString(room));
            ps.setLong(3, room.savedAtEpochMs());
            ps.executeUpdate();
        } catch (SQLException | JsonProcessingException e) {
            throw new IllegalStateException("live room save failed", e);
        }
    }

    @Override
    public void delete(String roomId) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM live_rooms WHERE room_id = ?")) {
            ps.setString(1, roomId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("live room delete failed", e);
        }
    }

    @Override
    public List<LiveRoomState> loadAll() {
        List<LiveRoomState> rooms = new ArrayList<>();
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT state_json FROM live_rooms ORDER BY updated_at");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rooms.add(mapper.readValue(rs.getString(1), LiveRoomState.class));
            }
        } catch (SQLException | JsonProcessingException e) {
            throw new IllegalStateException("live room load failed", e);
        }
        return rooms;
    }
}
