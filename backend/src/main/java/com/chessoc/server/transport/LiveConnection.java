package com.chessoc.server.transport;

import com.chessoc.server.match.state.MatchGame;
import com.chessoc.server.match.state.OutboundSink;
import com.chessoc.server.protocol.Envelope;
import com.chessoc.server.protocol.IncompatibleProtocolVersionException;
import com.chessoc.server.protocol.MessageType;
import com.chessoc.server.protocol.Messages;
import com.chessoc.server.protocol.ProtocolCodec;
import com.chessoc.server.protocol.ProtocolException;
import com.chessoc.server.session.Session;

/**
 * Binds one authenticated session to one game over a {@link Connection}. Decodes inbound envelopes and
 * routes them to the game; serves as the game's {@link OutboundSink} for outbound messages. A malformed
 * or bad-version message is answered with an error and never corrupts state.
 */
public final class LiveConnection implements OutboundSink {

    private final Session session;
    private final Connection connection;
    private final ProtocolCodec codec;
    private final MatchGame game;

    public LiveConnection(Session session, Connection connection, ProtocolCodec codec, MatchGame game) {
        this.session = session;
        this.connection = connection;
        this.codec = codec;
        this.game = game;
        game.subscribe(session, this);
    }

    public void onMessage(String json) {
        Envelope envelope;
        try {
            envelope = codec.decode(json);
        } catch (IncompatibleProtocolVersionException e) {
            send(MessageType.ERROR, new Messages.Error("incompatible_version", e.getMessage()));
            return;
        } catch (ProtocolException e) {
            send(MessageType.ERROR, new Messages.Error("bad_message", e.getMessage()));
            return;
        }

        switch (envelope.type()) {
            case MessageType.INTENT -> game.submit(session, codec.payload(envelope, Messages.Intent.class));
            case MessageType.RESYNC -> {
                Messages.Resync resync = codec.payload(envelope, Messages.Resync.class);
                game.resync(session.playerId(), resync.lastKnownVersion());
            }
            case MessageType.PING -> send(MessageType.PONG, new Messages.Pong());
            case MessageType.CHAT -> game.chat(session, codec.payload(envelope, Messages.ChatIn.class).text());
            default -> send(MessageType.ERROR, new Messages.Error("unsupported_type", envelope.type()));
        }
    }

    @Override
    public void send(String type, Object payload) {
        connection.send(codec.encode(type, payload));
    }
}
