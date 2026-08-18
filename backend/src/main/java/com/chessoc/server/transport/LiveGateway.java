package com.chessoc.server.transport;

import com.chessoc.server.match.state.MatchGame;
import com.chessoc.server.match.state.MatchService;
import com.chessoc.server.protocol.ProtocolCodec;
import com.chessoc.server.session.AuthException;
import com.chessoc.server.session.Session;
import com.chessoc.server.session.SessionRegistry;
import com.chessoc.server.session.TicketService;
import java.util.UUID;
import java.util.function.Supplier;

/** Turns an authenticated ticket + game id into a bound {@link LiveConnection}. */
public final class LiveGateway {

    private final TicketService tickets;
    private final SessionRegistry sessions;
    private final MatchService matches;
    private final ProtocolCodec codec;
    private final Supplier<String> connectionIds;

    public LiveGateway(TicketService tickets, SessionRegistry sessions, MatchService matches, ProtocolCodec codec) {
        this(tickets, sessions, matches, codec, () -> UUID.randomUUID().toString());
    }

    public LiveGateway(TicketService tickets, SessionRegistry sessions, MatchService matches,
                       ProtocolCodec codec, Supplier<String> connectionIds) {
        this.tickets = tickets;
        this.sessions = sessions;
        this.matches = matches;
        this.codec = codec;
        this.connectionIds = connectionIds;
    }

    public LiveConnection bind(String ticket, String gameId, Connection connection) {
        String sessionId = tickets.consume(ticket);
        String playerId = sessions.playerId(sessionId)
                .orElseThrow(() -> new AuthException("unknown session"));
        MatchGame game = matches.get(gameId)
                .orElseThrow(() -> new IllegalArgumentException("no such game: " + gameId));
        Session session = new Session(sessionId, playerId, connectionIds.get());
        return new LiveConnection(session, connection, codec, game);
    }
}
