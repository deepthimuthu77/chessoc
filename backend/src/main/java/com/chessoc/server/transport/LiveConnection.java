package com.chessoc.server.transport;

import com.chessoc.server.match.state.MatchGame;
import com.chessoc.server.match.state.OutboundSink;
import com.chessoc.server.protocol.Envelope;
import com.chessoc.server.protocol.IncompatibleProtocolVersionException;
import com.chessoc.server.protocol.MessageType;
import com.chessoc.server.protocol.Messages;
import com.chessoc.server.protocol.ProtocolCodec;
import com.chessoc.server.protocol.ProtocolException;
import com.chessoc.server.protocol.Disposition;
import com.chessoc.server.session.RateLimiter;
import com.chessoc.server.session.Session;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Binds one authenticated session to one game over a {@link Connection}. Decodes inbound envelopes and
 * routes them to the game; serves as the game's {@link OutboundSink} for outbound messages. A malformed
 * or bad-version message is answered with an error and never corrupts state. Intents pass through a
 * per-session anti-automation gate (§11).
 */
public final class LiveConnection implements OutboundSink {

    private final Session session;
    private final Connection connection;
    private final ProtocolCodec codec;
    private final MatchGame game;
    private final RateLimiter intentLimiter;
    private final LongSupplier clock;

    /** Convenience for tests: no rate limiting. */
    public LiveConnection(Session session, Connection connection, ProtocolCodec codec, MatchGame game) {
        this(session, connection, codec, game, new RateLimiter(0), System::currentTimeMillis);
    }

    public LiveConnection(Session session, Connection connection, ProtocolCodec codec, MatchGame game,
                          RateLimiter intentLimiter, LongSupplier clock) {
        this.session = session;
        this.connection = connection;
        this.codec = codec;
        this.game = game;
        this.intentLimiter = intentLimiter;
        this.clock = clock;
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
            case MessageType.INTENT -> {
                Messages.Intent intent = codec.payload(envelope, Messages.Intent.class);
                if (intentLimiter.allow(session.sessionId(), clock.getAsLong())) {
                    game.submit(session, intent);
                } else {
                    send(MessageType.RESULT, new Messages.Result(Disposition.REJECTED, intent.intentId(),
                            Optional.of("rate_limited"), Optional.empty(), Optional.empty()));
                }
            }
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
