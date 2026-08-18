package com.chessoc.server.match.state;

import com.chessoc.server.session.Session;

/** A player or spectator receiving a match's broadcasts. */
public record Subscriber(Session session, OutboundSink sink) {
}
