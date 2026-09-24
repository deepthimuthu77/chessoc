package com.chessoc.server.app;

import com.chessoc.server.capability.Capability;
import com.chessoc.server.capability.CapabilityKind;
import com.chessoc.server.protocol.Disposition;
import com.chessoc.server.protocol.Envelope;
import com.chessoc.server.protocol.MessageType;
import com.chessoc.server.protocol.Messages;
import com.chessoc.server.protocol.ProtocolCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/**
 * End-to-end smoke check: log in, create a game, connect over WSS, and play the first legal move the
 * engine offers. Proves server + engine + persistence are wired together. Run against a live stack.
 */
public final class SmokeClient {

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProtocolCodec codec = new ProtocolCodec();
    private final String httpBase;
    private final String wsBase;

    private SmokeClient(String httpBase, String wsBase) {
        this.httpBase = httpBase;
        this.wsBase = wsBase;
    }

    public static void main(String[] args) throws Exception {
        String httpBase = args.length > 0 ? args[0] : "http://localhost:8080";
        String wsBase = args.length > 1 ? args[1] : "ws://localhost:8081";
        boolean ok = new SmokeClient(httpBase, wsBase).run();
        System.out.println(ok ? "SMOKE OK" : "SMOKE FAILED");
        System.exit(ok ? 0 : 1);
    }

    private boolean run() throws Exception {
        String token = post("/login", "{}", null).path("token").asText();
        String gameId = post("/play/create", "{\"minutes\":5,\"vsEngine\":true}", token)
                .path("gameId").asText();
        String ticket = post("/ticket", "{}", token).path("ticket").asText();

        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean accepted = new AtomicBoolean();
        URI uri = URI.create(wsBase + "/game?ticket=" + ticket + "&gameId=" + gameId);

        WebSocketClient client = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
            }

            @Override
            public void onMessage(String message) {
                Envelope env = codec.decode(message);
                if (env.type().equals(MessageType.SNAPSHOT)) {
                    Messages.Snapshot snap = codec.payload(env, Messages.Snapshot.class);
                    snap.capabilities().stream()
                            .filter(c -> c.kind() == CapabilityKind.MOVE)
                            .findFirst()
                            .ifPresent(this::playMove);
                } else if (env.type().equals(MessageType.RESULT)) {
                    Messages.Result result = codec.payload(env, Messages.Result.class);
                    if (result.disposition() == Disposition.ACCEPTED) {
                        result.snapshot().ifPresent(s ->
                                System.out.println("move accepted -> version " + s.version() + " fen " + s.fen()));
                        accepted.set(true);
                    } else {
                        System.out.println("move " + result.disposition() + ": " + result.reason().orElse(""));
                    }
                    done.countDown();
                }
            }

            private void playMove(Capability cap) {
                System.out.println("playing " + cap.moveUci());
                send(codec.encode(MessageType.INTENT, new Messages.Intent("smoke-1", cap.id(), Map.of(), 0)));
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
            }

            @Override
            public void onError(Exception ex) {
                done.countDown();
            }
        };

        client.connectBlocking(5, TimeUnit.SECONDS);
        boolean finished = done.await(10, TimeUnit.SECONDS);
        client.closeBlocking();
        return finished && accepted.get();
    }

    private JsonNode post(String path, String json, String bearer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(httpBase + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(response.body());
    }
}
