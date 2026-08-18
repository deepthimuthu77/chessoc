package com.chessoc.server.transport;

/** A raw bidirectional text link to one client. Implemented by the WSS server (or an in-memory pipe). */
public interface Connection {

    void send(String text);

    void close();
}
