package com.chessoc.server.transport.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessoc.server.account.AccountService;
import com.chessoc.server.account.InMemoryAccountRepository;
import com.chessoc.server.engine.adapter.UnavailableEngine;
import com.chessoc.server.games.InMemoryGamesRepository;
import com.chessoc.server.match.TimeControl;
import com.chessoc.server.match.state.InMemoryMatchRepository;
import com.chessoc.server.match.state.Lobby;
import com.chessoc.server.match.state.MatchService;
import com.chessoc.server.match.state.Scheduler;
import com.chessoc.server.rating.InMemoryRatingRepository;
import com.chessoc.server.rating.LeaderboardService;
import com.chessoc.server.rating.RatingService;
import com.chessoc.server.session.SessionRegistry;
import com.chessoc.server.session.TicketService;
import com.chessoc.server.session.TokenService;
import com.chessoc.server.social.InMemoryFriendRepository;
import com.chessoc.server.social.Presence;
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
        UnavailableEngine engine = new UnavailableEngine();
        MatchService matches = new MatchService(engine, new InMemoryMatchRepository(), noop, () -> 0L);
        InMemoryAccountRepository accounts = new InMemoryAccountRepository();
        InMemoryRatingRepository ratings = new InMemoryRatingRepository();
        InMemoryFriendRepository friends = new InMemoryFriendRepository();
        InMemoryGamesRepository games = new InMemoryGamesRepository();
        Lobby lobby = new Lobby(matches, new RatingService(ratings), games, engine,
                TimeControl.fischer(300_000, 0), () -> 0L);

        ApiServices svc = new ApiServices(
                new TokenService("secret".getBytes(StandardCharsets.UTF_8)),
                new TicketService(() -> 0L, 60_000),
                new SessionRegistry(), lobby, new AccountService(accounts), accounts,
                new LeaderboardService(ratings, accounts), friends, games, new Presence(),
                3_600_000, () -> 0L);
        api = new HttpAuthApi(0, svc);
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
    void registerLoginJoinAndCreate() throws Exception {
        JsonNode alice = post("/auth/register",
                "{\"username\":\"alice\",\"email\":\"alice@x.com\",\"password\":\"secret1\"}", null);
        assertThat(alice.path("token").asText()).isNotBlank();
        String aliceToken = alice.path("token").asText();

        JsonNode login = post("/auth/login", "{\"email\":\"alice@x.com\",\"password\":\"secret1\"}", null);
        assertThat(login.path("account").path("username").asText()).isEqualTo("alice");

        String bobToken = post("/auth/register",
                "{\"username\":\"bob\",\"email\":\"bob@x.com\",\"password\":\"secret1\"}", null)
                .path("token").asText();

        JsonNode a = post("/join", "{\"room\":\"r1\"}", aliceToken);
        JsonNode b = post("/join", "{\"room\":\"r1\"}", bobToken);
        assertThat(a.path("color").asText()).isEqualTo("white");
        assertThat(b.path("color").asText()).isEqualTo("black");
        assertThat(b.path("gameId").asText()).isEqualTo(a.path("gameId").asText());

        assertThat(post("/play/create", "{\"minutes\":5}", aliceToken).path("gameId").asText()).isNotBlank();
    }

    @Test
    void addsFriendAndListsIt() throws Exception {
        String aliceToken = post("/auth/register",
                "{\"username\":\"alice2\",\"email\":\"a2@x.com\",\"password\":\"secret1\"}", null)
                .path("token").asText();
        post("/auth/register", "{\"username\":\"bob2\",\"email\":\"b2@x.com\",\"password\":\"secret1\"}", null);

        post("/friends/add", "{\"username\":\"bob2\"}", aliceToken);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + api.port() + "/friends"))
                .header("Authorization", "Bearer " + aliceToken)
                .GET().build();
        JsonNode friends = mapper.readTree(client.send(req, HttpResponse.BodyHandlers.ofString()).body());
        assertThat(friends.path("friends").get(0).path("username").asText()).isEqualTo("bob2");
    }
}
