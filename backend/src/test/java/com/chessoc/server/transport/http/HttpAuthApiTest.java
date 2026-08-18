package com.chessoc.server.transport.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessoc.server.engine.adapter.UnavailableEngine;
import com.chessoc.server.match.state.InMemoryMatchRepository;
import com.chessoc.server.match.state.MatchService;
import com.chessoc.server.match.state.Scheduler;
import com.chessoc.server.session.SessionRegistry;
import com.chessoc.server.session.TicketService;
import com.chessoc.server.session.TokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpAuthApiTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private HttpAuthApi api;

    @BeforeEach
    void start() throws IOException {
        Scheduler noop = (task, delay) -> () -> {
        };
        MatchService matches = new MatchService(new UnavailableEngine(), new InMemoryMatchRepository(),
                noop, () -> 0L);
        api = new HttpAuthApi(0,
                new TokenService("secret".getBytes(StandardCharsets.UTF_8)),
                new TicketService(() -> 0L, 60_000),
                new SessionRegistry(), matches, 3_600_000, () -> 0L);
        api.start();
    }

    @AfterEach
    void stop() {
        api.stop();
    }

    private JsonNode post(String path, String json, String bearer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + api.port() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    @Test
    void loginTicketAndGameFlow() throws Exception {
        JsonNode login = post("/login", "{\"playerId\":\"alice\"}", null);
        assertThat(login.path("token").asText()).isNotBlank();
        assertThat(login.path("sessionId").asText()).isNotBlank();

        JsonNode ticket = post("/ticket", "{}", login.path("token").asText());
        assertThat(ticket.path("ticket").asText()).isNotBlank();

        JsonNode game = post("/game", "{\"whiteId\":\"alice\",\"blackId\":\"bob\"}", null);
        assertThat(game.path("gameId").asText()).isNotBlank();
    }
}
