package com.chessoc.server.match.state;

/** Where the Match writes messages for one subscriber. The transport encodes and delivers them. */
public interface OutboundSink {

    void send(String type, Object payload);
}
