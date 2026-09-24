package com.chessoc.server.match.state;

import java.util.List;

/** Atomic persistence for recoverable live rooms. */
public interface LiveRoomRepository {

    void save(LiveRoomState room);

    void delete(String roomId);

    List<LiveRoomState> loadAll();
}
