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
import com.chessoc.server.matchmaking.MatchmakingService;
import com.chessoc.server.matchmaking.SeekCoordinator;
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
        InMemoryMatchRepository matchRepo = new InMemoryMatchRepository();
        MatchService matches = new MatchService(engine, matchRepo, noop, () -> 0L);
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
                matches, matchRepo,
                new SeekCoordinator(new MatchmakingService(), lobby::createPaired), engine, 3_600_000, () -> 0L);
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

    private JsonNode get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + api.port() + path))
                .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    @Test
    void invalidRequestsHaveJsonErrorsInsteadOfDroppedConnections() throws Exception {
        String token = post("/login", "{}", null).path("token").asText();
        for (String body : new String[] {"{", "{\"variant\":\"unknown\"}"}) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + api.port() + "/play/create"))
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(mapper.readTree(response.body()).path("error").asText()).isNotBlank();
        }
        HttpResponse<String> wrongMethod = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + api.port() + "/play/create"))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(wrongMethod.statusCode()).isEqualTo(405);
    }

    @Test
    void guestUpgradeDoesNotOrphanAnActiveSeat() throws Exception {
        String token = post("/login", "{}", null).path("token").asText();
        post("/play/create", "{}", token);
        HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + api.port() + "/auth/upgrade"))
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"upgrade\",\"email\":\"u@x.com\",\"password\":\"secret1\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(409);
    }

    @Test
    void newGuestHasAProfileBeforePlayingAnyGames() throws Exception {
        JsonNode guest = post("/login", "{}", null);
        String id = guest.path("account").path("id").asText();
        JsonNode profile = get("/profile?id=" + id);
        assertThat(profile.path("account").path("id").asText()).isEqualTo(id);
        assertThat(profile.path("stats").path("wins").asInt()).isZero();
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

        String room = post("/play/create", "{\"minutes\":5}", aliceToken)
                .path("gameId").asText();
        JsonNode a = post("/join", "{\"room\":\"" + room + "\"}", aliceToken);
        JsonNode b = post("/join", "{\"room\":\"" + room + "\"}", bobToken);
        assertThat(a.path("color").asText()).isEqualTo("white");
        assertThat(b.path("color").asText()).isEqualTo("black");
        assertThat(b.path("gameId").asText()).isEqualTo(a.path("gameId").asText());
    }

    @Test
    void liveGamesExposeBoardSnapshotsAndAggregateBughouse() throws Exception {
        String aliceToken = post("/auth/register",
                "{\"username\":\"livealice\",\"email\":\"live-a@x.com\",\"password\":\"secret1\"}", null)
                .path("token").asText();
        String bobToken = post("/auth/register",
                "{\"username\":\"livebob\",\"email\":\"live-b@x.com\",\"password\":\"secret1\"}", null)
                .path("token").asText();

        String standardRoom = post("/play/create",
                "{\"variant\":\"standard\",\"minutes\":3,\"increment\":2}", aliceToken)
                .path("gameId").asText();
        post("/join", "{\"room\":\"" + standardRoom + "\"}", bobToken);
        String bughouseRoom = post("/play/create",
                "{\"variant\":\"bughouse\",\"minutes\":5,\"humanPlayers\":1}", aliceToken)
                .path("gameId").asText();

        JsonNode games = get("/games/live").path("games");
        JsonNode standard = findGame(games, standardRoom);
        assertThat(standard.path("variant").asText()).isEqualTo("standard");
        assertThat(standard.path("speed").asText()).isEqualTo("blitz");
        assertThat(standard.path("minutes").asInt()).isEqualTo(3);
        assertThat(standard.path("increment").asInt()).isEqualTo(2);
        assertThat(standard.path("boards")).hasSize(1);
        assertThat(standard.path("boards").get(0).path("fen").asText()).isNotBlank();
        assertThat(standard.path("boards").get(0).path("moveCount").asInt()).isZero();
        assertThat(standard.path("boards").get(0).path("lastMove").asText()).isEmpty();
        assertThat(standard.path("boards").get(0).path("whiteRemainingMs").asLong()).isEqualTo(180_000);

        JsonNode bughouse = findGame(games, bughouseRoom);
        assertThat(bughouse.path("variant").asText()).isEqualTo("bughouse");
        assertThat(bughouse.path("boards")).hasSize(2);
        assertThat(bughouse.path("boards").get(0).path("label").asText()).isEqualTo("A");
        assertThat(bughouse.path("boards").get(1).path("label").asText()).isEqualTo("B");
    }

    private static JsonNode findGame(JsonNode games, String gameId) {
        for (JsonNode game : games) {
            if (gameId.equals(game.path("gameId").asText())) {
                return game;
            }
        }
        throw new AssertionError("live game not found: " + gameId);
    }

    @Test
    void reviewKeepsVariantInitialPositionAndBothBughouseBoards() throws Exception {
        String token = post("/login", "{}", null).path("token").asText();
        String crazyhouse = post("/play/create", "{\"variant\":\"crazyhouse\",\"vsEngine\":true}", token)
                .path("gameId").asText();
        JsonNode review = get("/game/moves?id=" + crazyhouse);
        assertThat(review.path("variant").asText()).isEqualTo("crazyhouse");
        assertThat(review.path("initialFen").asText()).contains("[] w");
        String bughouse = post("/play/create", "{\"variant\":\"bughouse\",\"humanPlayers\":1}", token)
                .path("gameId").asText();
        JsonNode paired = get("/game/moves?id=" + bughouse);
        assertThat(paired.path("boards")).hasSize(2);
        assertThat(paired.path("boards").get(0).path("gameId").asText()).isEqualTo(bughouse + "-a");
        assertThat(paired.path("boards").get(1).path("gameId").asText()).isEqualTo(bughouse + "-b");
    }

    @Test
    void customTimeIsExposedAndInvalidDifficultyIsRejected() throws Exception {
        String token = post("/login", "{}", null).path("token").asText();
        String room = post("/play/create", "{\"minutes\":17,\"increment\":9,\"vsEngine\":true,\"aiLevel\":\"easy\"}", token)
                .path("gameId").asText();
        JsonNode game = findGame(get("/games/live").path("games"), room);
        assertThat(game.path("minutes").asInt()).isEqualTo(17);
        assertThat(game.path("increment").asInt()).isEqualTo(9);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + api.port() + "/play/create"))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"aiLevel\":\"invalid\"}")).build();
        assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(400);
    }

    @Test
    void liveEventsStreamsNamedGameEvents() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + api.port() + "/games/live/events"))
                .timeout(java.time.Duration.ofSeconds(3)).GET().build();
        HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).isEqualTo("text/event-stream");
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(response.body()))) {
            assertThat(reader.readLine()).isEqualTo("event: games");
            assertThat(reader.readLine()).startsWith("data: {\"games\":");
        }
    }

    @Test
    void addsFriendAndListsIt() throws Exception {
        JsonNode alice = post("/auth/register",
                "{\"username\":\"alice2\",\"email\":\"a2@x.com\",\"password\":\"secret1\"}", null);
        String aliceToken = alice.path("token").asText();
        String aliceId = alice.path("account").path("id").asText();
        String bobToken = post("/auth/register",
                "{\"username\":\"bob2\",\"email\":\"b2@x.com\",\"password\":\"secret1\"}", null)
                .path("token").asText();

        // A request alone does not befriend; bob must accept.
        post("/friends/add", "{\"username\":\"bob2\"}", aliceToken);
        assertThat(friendsOf(aliceToken).path("friends")).isEmpty();

        post("/friends/accept", "{\"id\":\"" + aliceId + "\"}", bobToken);
        assertThat(friendsOf(aliceToken).path("friends").get(0).path("username").asText()).isEqualTo("bob2");
    }

    private JsonNode friendsOf(String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + api.port() + "/friends"))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        return mapper.readTree(client.send(req, HttpResponse.BodyHandlers.ofString()).body());
    }
}
