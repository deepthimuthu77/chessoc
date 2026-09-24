package com.chessoc.server.match.state;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryLiveRoomRepository implements LiveRoomRepository {

    private final Map<String, LiveRoomState> rooms = new ConcurrentHashMap<>();

    @Override
    public void save(LiveRoomState room) {
        rooms.put(room.roomId(), room);
    }

    @Override
    public void delete(String roomId) {
        rooms.remove(roomId);
    }

    @Override
    public List<LiveRoomState> loadAll() {
        return List.copyOf(rooms.values());
    }
}
