package com.chessoc.server.transport.ws;

import com.chessoc.server.session.AuthException;
import com.chessoc.server.transport.Connection;
import com.chessoc.server.transport.LiveConnection;
import com.chessoc.server.transport.LiveGateway;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

/**
 * The live transport (§5). A client connects with {@code ?ticket=&gameId=}; the ticket is exchanged
 * for a bound session and the socket becomes that session's link to its game. TLS is terminated at the
 * edge in this dev server.
 */
public final class WebSocketLiveServer extends WebSocketServer {

    private final LiveGateway gateway;
    private final Map<WebSocket, LiveConnection> live = new ConcurrentHashMap<>();

    public WebSocketLiveServer(InetSocketAddress address, LiveGateway gateway) {
        super(address);
        this.gateway = gateway;
        setReuseAddr(true);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        Map<String, String> query = parseQuery(handshake.getResourceDescriptor());
        String ticket = query.get("ticket");
        String gameId = query.get("gameId");
        if (ticket == null || gameId == null) {
            conn.close(1008, "ticket and gameId required");
            return;
        }
        try {
            live.put(conn, gateway.bind(ticket, gameId, new SocketSink(conn)));
        } catch (AuthException e) {
            conn.close(1008, "auth failed");
        } catch (RuntimeException e) {
            conn.close(1011, "bind failed");
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        LiveConnection connection = live.get(conn);
        if (connection != null) {
            connection.onMessage(message);
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        live.remove(conn);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn != null) {
            live.remove(conn);
        }
    }

    @Override
    public void onStart() {
        // no-op
    }

    private static Map<String, String> parseQuery(String resource) {
        Map<String, String> out = new HashMap<>();
        int q = resource.indexOf('?');
        if (q < 0) {
            return out;
        }
        for (String pair : resource.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                out.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    private record SocketSink(WebSocket socket) implements Connection {
        @Override
        public void send(String text) {
            if (socket.isOpen()) {
                socket.send(text);
            }
        }

        @Override
        public void close() {
            socket.close();
        }
    }
}
