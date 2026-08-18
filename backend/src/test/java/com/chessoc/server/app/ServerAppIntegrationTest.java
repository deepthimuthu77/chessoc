package com.chessoc.server.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessoc.server.engine.adapter.UnavailableEngine;
import com.chessoc.server.protocol.Envelope;
import com.chessoc.server.protocol.MessageType;
import com.chessoc.server.protocol.ProtocolCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.Test;

/** Full stack: HTTP login/ticket/create-game, then a live WSS connection receiving its first snapshot. */
class ServerAppIntegrationTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ProtocolCodec codec = new ProtocolCodec();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void connectsAndReceivesSnapshot() throws Exception {
        ServerConfig config = new ServerConfig(0, 0,
                "secret".getBytes(StandardCharsets.UTF_8),
                Duration.ofHours(1).toMillis(), Duration.ofMinutes(1).toMillis(),
                "", 0,
                List.of(), List.of(), 1, 1, Duration.ofSeconds(1), Duration.ofSeconds(1),
                "", "", "");
        ServerApp app = new ServerApp(config, new UnavailableEngine());
        app.start();
        for (int i = 0; i < 100 && app.wsPort() == 0; i++) {
            Thread.sleep(20);
        }

        try {
            String token = post(app.httpPort(), "/login", "{\"playerId\":\"bob\"}", null).path("token").asText();
            String ticket = post(app.httpPort(), "/ticket", "{}", token).path("ticket").asText();
            String gameId = post(app.httpPort(), "/game",
                    "{\"whiteId\":\"alice\",\"blackId\":\"bob\"}", null).path("gameId").asText();

            CountDownLatch received = new CountDownLatch(1);
            AtomicReference<String> firstMessage = new AtomicReference<>();
            URI uri = URI.create("ws://localhost:" + app.wsPort()
                    + "/game?ticket=" + ticket + "&gameId=" + gameId);
            WebSocketClient client = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                }

                @Override
                public void onMessage(String message) {
                    firstMessage.compareAndSet(null, message);
                    received.countDown();
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                }

                @Override
                public void onError(Exception ex) {
                }
            };
            client.connectBlocking(5, TimeUnit.SECONDS);

            assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
            Envelope envelope = codec.decode(firstMessage.get());
            assertThat(envelope.type()).isEqualTo(MessageType.SNAPSHOT);
            client.closeBlocking();
        } finally {
            app.stop();
        }
    }

    private JsonNode post(int port, String path, String json, String bearer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(response.body());
    }
}
