package com.chessoc.server.transport.http;

import com.chessoc.server.account.Account;
import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Variant;
import com.chessoc.server.games.GameRecord;
import com.chessoc.server.match.TimeControl;
import com.chessoc.server.match.state.Lobby;
import com.chessoc.server.match.state.MatchGame;
import com.chessoc.server.match.state.PersistedPly;
import com.chessoc.server.protocol.ProtocolMapper;
import com.chessoc.server.rating.LeaderboardEntry;
import com.chessoc.server.rating.PoolRating;
import com.chessoc.server.rating.RatingHistoryPoint;
import com.chessoc.server.session.AuthException;
import com.chessoc.server.session.SessionToken;
import com.chessoc.server.social.FriendService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * The product HTTP surface (§5): auth (register / login / guest), live-connection ticket, game
 * create + join, and the social layer (friends, leaderboard, profile). CORS is open for the browser.
 */
public final class HttpAuthApi {

    private final HttpServer server;
    private final ApiServices s;
    private final FriendService friendService;

    public HttpAuthApi(int port, ApiServices services) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.s = services;
        this.friendService = new FriendService(services.friends());
        route("/login", this::guestLogin);
        route("/auth/register", this::register);
        route("/auth/login", this::login);
        route("/me", this::me);
        route("/ticket", this::ticket);
        route("/join", this::join);
        route("/play/create", this::playCreate);
        route("/seek/cancel", this::seekCancel);
        route("/seek/status", this::seekStatus);
        route("/seek", this::seek);
        route("/friends/add", this::friendsAdd);
        route("/friends/requests", this::friendRequests);
        route("/friends/accept", this::friendAccept);
        route("/friends/decline", this::friendDecline);
        route("/friends", this::friendsList);
        route("/leaderboard/friends", this::leaderboardFriends);
        route("/leaderboard", this::leaderboard);
        route("/profile", this::profile);
        route("/ratings/history", this::ratingsHistory);
        route("/games/mine", this::gamesMine);
        route("/games/active", this::gamesActive);
        route("/games/live", this::gamesLive);
        route("/game/moves", this::gameMoves);
        route("/account/password", this::changePassword);
        route("/account/delete", this::deleteAccount);
        server.setExecutor(Executors.newCachedThreadPool());
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    // ---- auth ----

    private void guestLogin(HttpExchange x) throws IOException {
        if (notPost(x)) {
            return;
        }
        String playerId = "guest-" + UUID.randomUUID().toString().substring(0, 8);
        send(x, 200, issueSession(playerId, "Guest", null));
    }

    private void register(HttpExchange x) throws IOException {
        if (notPost(x)) {
            return;
        }
        JsonNode b = read(x);
        try {
            Account a = s.accountService().register(
                    b.path("username").asText(), b.path("email").asText(), b.path("password").asText());
            s.presence().online(a.id());
            send(x, 200, issueSession(a.id(), a.username(), a.email()));
        } catch (AuthException | IllegalStateException e) {
            send(x, 400, Map.of("error", e.getMessage()));
        }
    }

    private void login(HttpExchange x) throws IOException {
        if (notPost(x)) {
            return;
        }
        JsonNode b = read(x);
        try {
            Account a = s.accountService().authenticate(b.path("email").asText(), b.path("password").asText());
            s.presence().online(a.id());
            send(x, 200, issueSession(a.id(), a.username(), a.email()));
        } catch (AuthException e) {
            send(x, 401, Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> issueSession(String accountId, String username, String email) {
        String sessionId = UUID.randomUUID().toString();
        s.sessions().register(sessionId, accountId);
        String token = s.tokens().issue(accountId, sessionId, s.clock().getAsLong() + s.tokenTtlMs());
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("id", accountId);
        account.put("username", username);
        account.put("email", email == null ? "" : email);
        return Map.of("token", token, "account", account);
    }

    private void me(HttpExchange x) throws IOException {
        SessionToken token = bearer(x);
        if (token == null) {
            return;
        }
        s.presence().online(token.playerId());
        Account a = s.accounts().findById(token.playerId()).orElse(null);
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("id", token.playerId());
        account.put("username", a == null ? "Guest" : a.username());
        account.put("email", a == null ? "" : a.email());
        account.put("guest", a == null);
        send(x, 200, Map.of("account", account));
    }

    private void ticket(HttpExchange x) throws IOException {
        SessionToken token = bearer(x);
        if (token == null) {
            return;
        }
        send(x, 200, Map.of("ticket", s.tickets().issue(token.sessionId())));
    }

    // ---- play ----

    private void join(HttpExchange x) throws IOException {
        SessionToken token = bearer(x);
        if (token == null) {
            return;
        }
        String room = read(x).path("room").asText();
        if (room.isBlank()) {
            send(x, 400, Map.of("error", "room required"));
            return;
        }
        Lobby.Seat seat = s.lobby().join(room, token.playerId());
        s.presence().enterGame(token.playerId(), seat.gameId());
        send(x, 200, Map.of("gameId", seat.gameId(), "color", seat.color()));
    }

    private void playCreate(HttpExchange x) throws IOException {
        SessionToken token = bearer(x);
        if (token == null) {
            return;
        }
        JsonNode b = read(x);
        Variant variant = Variant.valueOf(b.path("variant").asText("standard").toUpperCase());
        long baseMs = Math.max(1, b.path("minutes").asInt(5)) * 60_000L;
        long incMs = Math.max(0, b.path("increment").asInt(0)) * 1_000L;
        TimeControl tc = TimeControl.fischer(baseMs, incMs);
        String gameId;
        if (b.path("vsEngine").asBoolean(false)) {
            Side humanColor = "black".equalsIgnoreCase(b.path("color").asText("white")) ? Side.BLACK : Side.WHITE;
            gameId = s.lobby().createVsEngine(token.playerId(), variant, tc, humanColor);
        } else {
            gameId = s.lobby().create(token.playerId(), variant, tc, b.path("rated").asBoolean(true));
        }
        send(x, 200, Map.of("gameId", gameId));
    }

    // ---- quick match ----

    private void seek(HttpExchange x) throws IOException {
        SessionToken token = bearer(x);
        if (token == null) {
            return;
        }
        JsonNode b = read(x);
        Variant variant = Variant.valueOf(b.path("variant").asText("standard").toUpperCase());
        long baseMs = Math.max(1, b.path("minutes").asInt(5)) * 60_000L;
        long incMs = Math.max(0, b.path("increment").asInt(0)) * 1_000L;
        String gameId = s.seeks().seek(token.playerId(), variant, TimeControl.fischer(baseMs, incMs));
        send(x, 200, gameId == null ? Map.of("status", "waiting")
                : Map.of("status", "matched", "gameId", gameId));
    }

    private void seekStatus(HttpExchange x) throws IOException {
        SessionToken token = bearer(x);
        if (token == null) {
            return;
        }
        String gameId = s.seeks().poll(token.playerId());
        send(x, 200, gameId == null ? Map.of("status", "waiting")
                : Map.of("status", "matched", "gameId", gameId));
    }

    private void seekCancel(HttpExchange x) throws IOException {
        SessionToken token = bearer(x);
        if (token == null) {
            return;
        }
        s.seeks().cancel(token.playerId());
        send(x, 200, Map.of("ok", true));
    }

    // ---- social ----

    private void friendsAdd(HttpExchange x) throws IOException {
        SessionToken token = bearer(x);
        if (token == null) {
            return;
        }
        String username = read(x).path("username").asText();
        Account friend = s.accounts().findByUsername(username).orElse(null);
        if (friend == null) {
            send(x, 404, Map.of("error", "no user named " + username));
            return;
        }
        FriendService.Outcome outcome = friendService.request(token.playerId(), friend.id());
        send(x, 200, Map.of("ok", true, "friendId", friend.id(),
                "status", outcome.name().toLowerCase()));
    }

    private void friendRequests(HttpExchange x) throws IOException {
        SessionToken token = bearer(x);
        if (token == null) {
            return;
        }
        send(x, 200, Map.of(
                "incoming", friendCards(s.friends().incomingRequests(token.playerId())),
                "outgoing", friendCards(s.friends().outgoingRequests(token.playerId()))));
    }

    private List<Map<String, Object>> friendCards(List<String> ids) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Account a : s.accounts().byIds(ids)) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("id", a.id());
            f.put("username", a.username());
            out.add(f);
        }
        return out;
    }

    private void friendAccept(HttpExchange x) throws IOException {
        SessionToken token = bearer(x);
        if (token == null) {
            return;
        }
        String fromId = read(x).path("id").asText();
        boolean accepted = friendService.accept(token.playerId(), fromId);
        send(x, 200, Map.of("ok", accepted));
    }

    private void friendDecline(HttpExchange x) throws IOException {
        SessionToken token = bearer(x);
        if (token == null) {
            return;
        }
        friendService.decline(token.playerId(), read(x).path("id").asText());
        send(x, 200, Map.of("ok", true));
    }

    private void friendsList(HttpExchange x) throws IOException {
        SessionToken token = bearer(x);
        if (token == null) {
            return;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Account a : s.accounts().byIds(s.friends().friends(token.playerId()))) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("id", a.id());
            f.put("username", a.username());
            f.put("online", s.presence().isOnline(a.id()));
            out.add(f);
        }
        send(x, 200, Map.of("friends", out));
    }

    private void leaderboard(HttpExchange x) throws IOException {
        Map<String, String> q = query(x);
        String variant = q.getOrDefault("variant", "standard");
        String speed = q.getOrDefault("speed", "blitz");
        int limit = parseInt(q.get("limit"), 50);
        send(x, 200, board(variant, speed, s.leaderboard().global(variant, speed, limit)));
    }

    private void leaderboardFriends(HttpExchange x) throws IOException {
        SessionToken token = bearer(x);
        if (token == null) {
            return;
        }
        Map<String, String> q = query(x);
        String variant = q.getOrDefault("variant", "standard");
        String speed = q.getOrDefault("speed", "blitz");
        List<String> ids = new ArrayList<>(s.friends().friends(token.playerId()));
        ids.add(token.playerId());
        send(x, 200, board(variant, speed, s.leaderboard().among(ids, variant, speed)));
    }

    private Map<String, Object> board(String variant, String speed, List<LeaderboardEntry> entries) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int rank = 1;
        for (LeaderboardEntry e : entries) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", rank++);
            row.put("accountId", e.accountId());
            row.put("username", e.username());
            row.put("rating", e.rating());
            row.put("games", e.games());
            rows.add(row);
        }
        return Map.of("variant", variant, "speed", speed, "entries", rows);
    }

    private void profile(HttpExchange x) throws IOException {
        Map<String, String> q = query(x);
        Account a = (q.containsKey("id") ? s.accounts().findById(q.get("id"))
                : s.accounts().findByUsername(q.getOrDefault("username", ""))).orElse(null);
        if (a == null) {
            send(x, 404, Map.of("error", "no such player"));
            return;
        }
        var stats = s.games().statsFor(a.id());
        List<LeaderboardEntry> mine = s.leaderboard().among(List.of(a.id()), "standard", "blitz");
        int rating = mine.isEmpty() ? 1500 : mine.get(0).rating();
        List<Map<String, Object>> pools = new ArrayList<>();
        for (PoolRating pr : s.leaderboard().pools(a.id())) {
            Map<String, Object> pool = new LinkedHashMap<>();
            pool.put("variant", pr.variant());
            pool.put("speed", pr.speed());
            pool.put("rating", pr.rating());
            pool.put("games", pr.games());
            pools.add(pool);
        }
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("id", a.id());
        account.put("username", a.username());
        send(x, 200, Map.of(
                "account", account,
                "rating", rating,
                "stats", Map.of("wins", stats.wins(), "losses", stats.losses(), "draws", stats.draws()),
                "pools", pools));
    }

    private void ratingsHistory(HttpExchange x) throws IOException {
        Map<String, String> q = query(x);
        String id = q.get("id");
        if (id == null) {
            send(x, 400, Map.of("error", "id required"));
            return;
        }
        String variant = q.getOrDefault("variant", "standard");
        String speed = q.getOrDefault("speed", "blitz");
        List<Map<String, Object>> points = new ArrayList<>();
        for (RatingHistoryPoint p : s.leaderboard().history(id, variant, speed)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("at", p.at());
            row.put("rating", p.rating());
            points.add(row);
        }
        send(x, 200, Map.of("variant", variant, "speed", speed, "points", points));
    }

    private void gamesMine(HttpExchange x) throws IOException {
        SessionToken token = bearer(x);
        if (token == null) {
            return;
        }
        String me = token.playerId();
        List<Map<String, Object>> out = new ArrayList<>();
        for (GameRecord g : s.games().forAccount(me, 25)) {
            String opponentId = me.equals(g.whiteId()) ? g.blackId() : g.whiteId();
            String opponent = opponentId == null ? "?"
                    : Lobby.ENGINE_ID.equals(opponentId) ? "Computer"
                    : s.accounts().findById(opponentId).map(Account::username).orElse("guest");
            String result = "DRAW".equals(g.resultType()) ? "draw"
                    : me.equals(g.winnerAccountId()) ? "win" : "loss";
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("gameId", g.id());
            row.put("opponent", opponent);
            row.put("result", result);
            row.put("reason", g.reason());
            row.put("rated", g.rated());
            row.put("variant", g.variant());
            row.put("endedAt", g.endedAtEpochMs());
            out.add(row);
        }
        send(x, 200, Map.of("games", out));
    }

    private void gamesActive(HttpExchange x) throws IOException {
        SessionToken token = bearer(x);
        if (token == null) {
            return;
        }
        String me = token.playerId();
        List<Map<String, Object>> out = new ArrayList<>();
        for (MatchGame g : s.matches().activeFor(me)) {
            String opponentId = me.equals(g.whiteId()) ? g.blackId() : g.whiteId();
            String opponent = opponentId == null ? "waiting"
                    : Lobby.ENGINE_ID.equals(opponentId) ? "Computer"
                    : s.accounts().findById(opponentId).map(Account::username).orElse("guest");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("gameId", g.gameId());
            row.put("color", me.equals(g.whiteId()) ? "white" : "black");
            row.put("opponent", opponent);
            out.add(row);
        }
        send(x, 200, Map.of("games", out));
    }

    private void gameMoves(HttpExchange x) throws IOException {
        String gameId = query(x).get("id");
        if (gameId == null) {
            send(x, 400, Map.of("error", "id required"));
            return;
        }
        List<Map<String, Object>> plies = new ArrayList<>();
        for (PersistedPly p : s.matchRepo().plies(gameId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("version", p.version());
            row.put("uci", p.move().value());
            row.put("fen", p.fen().raw());
            plies.add(row);
        }
        send(x, 200, Map.of("gameId", gameId, "plies", plies));
    }

    private void gamesLive(HttpExchange x) throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        for (MatchGame g : s.matches().liveGames()) {
            if (g.blackId() == null) {
                continue; // still waiting for an opponent
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("gameId", g.gameId());
            row.put("white", displayName(g.whiteId()));
            row.put("black", displayName(g.blackId()));
            row.put("variant", g.variant().name().toLowerCase());
            out.add(row);
        }
        send(x, 200, Map.of("games", out));
    }

    private void changePassword(HttpExchange x) throws IOException {
        SessionToken token = bearer(x);
        if (token == null) {
            return;
        }
        JsonNode b = read(x);
        try {
            s.accountService().changePassword(token.playerId(),
                    b.path("oldPassword").asText(), b.path("newPassword").asText());
            send(x, 200, Map.of("ok", true));
        } catch (AuthException e) {
            send(x, 400, Map.of("error", e.getMessage()));
        }
    }

    private void deleteAccount(HttpExchange x) throws IOException {
        SessionToken token = bearer(x);
        if (token == null) {
            return;
        }
        try {
            s.accountService().deleteAccount(token.playerId(), read(x).path("password").asText());
            send(x, 200, Map.of("ok", true));
        } catch (AuthException e) {
            send(x, 400, Map.of("error", e.getMessage()));
        }
    }

    private String displayName(String id) {
        if (id == null) {
            return "waiting";
        }
        if (Lobby.ENGINE_ID.equals(id)) {
            return "Computer";
        }
        return s.accounts().findById(id).map(Account::username).orElse("guest");
    }

    // ---- plumbing ----

    private final ObjectMapper mapper = ProtocolMapper.create();

    private void route(String path, HttpHandler handler) {
        server.createContext(path, exchange -> {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            handler.handle(exchange);
        });
    }

    private SessionToken bearer(HttpExchange x) throws IOException {
        String auth = x.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            send(x, 401, Map.of("error", "missing bearer token"));
            return null;
        }
        try {
            return s.tokens().verify(auth.substring(7), s.clock().getAsLong());
        } catch (AuthException e) {
            send(x, 401, Map.of("error", e.getMessage()));
            return null;
        }
    }

    private boolean notPost(HttpExchange x) throws IOException {
        if (!"POST".equals(x.getRequestMethod())) {
            send(x, 405, Map.of("error", "method not allowed"));
            return true;
        }
        return false;
    }

    private JsonNode read(HttpExchange x) throws IOException {
        byte[] body = x.getRequestBody().readAllBytes();
        return body.length == 0 ? mapper.createObjectNode() : mapper.readTree(body);
    }

    private Map<String, String> query(HttpExchange x) {
        Map<String, String> out = new HashMap<>();
        String raw = x.getRequestURI().getRawQuery();
        if (raw == null) {
            return out;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                out.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    private static int parseInt(String v, int fallback) {
        try {
            return v == null ? fallback : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void send(HttpExchange x, int status, Object payload) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(payload);
        x.getResponseHeaders().add("Content-Type", "application/json");
        x.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = x.getResponseBody()) {
            out.write(bytes);
        }
    }
}
