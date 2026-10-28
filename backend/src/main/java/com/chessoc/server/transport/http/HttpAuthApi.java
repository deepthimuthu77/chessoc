package com.chessoc.server.transport.http;

import com.chessoc.server.account.Account;
import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Side;
import com.chessoc.server.domain.Variant;
import com.chessoc.server.engine.Score;
import com.chessoc.server.engine.SearchBudget;
import com.chessoc.server.engine.SearchResult;
import com.chessoc.server.domain.Uci;
import com.chessoc.server.games.GameRecord;
import com.chessoc.server.match.SpeedBucket;
import com.chessoc.server.match.TimeControl;
import com.chessoc.server.match.state.Lobby;
import com.chessoc.server.match.state.MatchGame;
import com.chessoc.server.match.state.PersistedPly;
import com.chessoc.server.protocol.ProtocolMapper;
import com.chessoc.server.protocol.GameSnapshot;
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
import java.net.InetAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The product HTTP surface (§5): auth (register / login / guest), live-connection ticket, game
 * create + join, and the social layer (friends, leaderboard, profile). CORS is open for the browser.
 */
public final class HttpAuthApi {

    private final HttpServer server;
    private final ApiServices s;
    private final FriendService friendService;
    private final AtomicInteger liveViewers = new AtomicInteger();
    private final ScheduledExecutorService liveFeed = Executors.newSingleThreadScheduledExecutor();
    private volatile String liveJson = "{\"games\":[]}";
    private String liveSignature = "";
    private long livePublishedAt;
    private record CachedName(String name, long expiresAt) {}
    private final Map<String, CachedName> playerNames = new ConcurrentHashMap<>();
    private final Map<String, MatchGame> observedGames = new ConcurrentHashMap<>();
    private final Map<String, Long> endedGames = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    public HttpAuthApi(int port, ApiServices services) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.s = services;
        this.friendService = new FriendService(services.friends());
        route("/internal/tls-allow", this::tlsAllow);
        route("/login", this::guestLogin);
        route("/auth/register", this::register);
        route("/auth/upgrade", this::upgrade);
        route("/auth/login", this::login);
        route("/me", this::me);
        route("/heartbeat", this::heartbeat);
        route("/ticket", this::ticket);
        route("/join", this::join);
        route("/play/create", this::playCreate);
        route("/play/rematch", this::replayRoom);
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
        route("/games/live/events", this::liveEvents);
        route("/games/open", this::gamesOpen);
        route("/game/moves", this::gameMoves);
        route("/analyze", this::analyze);
        route("/account/password", this::changePassword);
        route("/account/delete", this::deleteAccount);
        server.setExecutor(Executors.newCachedThreadPool());
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void start() {
        server.start();
        liveFeed.scheduleWithFixedDelay(() -> {
            if (liveViewers.get() == 0) return;
            try {
                Map<String, Object> payload = liveGamesPayload(true);
                String signature = feedSignature(payload);
                long now = System.currentTimeMillis();
                if (!signature.equals(liveSignature) || now - livePublishedAt >= 5000) {
                    liveJson = mapper.writeValueAsString(payload);
                    liveSignature = signature;
                    livePublishedAt = now;
                }
            } catch (RuntimeException | IOException e) {
                System.err.println("live feed refresh failed: " + e.getMessage());
            }
        }, 0, 250, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        running = false;
        liveFeed.shutdownNow();
        server.stop(0);
    }

    private void tlsAllow(HttpExchange x) throws IOException {
        String domain = query(x).getOrDefault("domain", "");
        if (isLocalTlsName(domain)) {
            send(x, 200, Map.of("allowed", true));
        } else {
            send(x, 403, Map.of("allowed", false));
        }
    }

    private static boolean isLocalTlsName(String domain) {
        if ("localhost".equalsIgnoreCase(domain)) {
            return true;
        }
        if (!domain.matches("[0-9.]+") && !domain.matches("[0-9a-fA-F:]+")) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(domain);
            byte[] raw = address.getAddress();
            boolean uniqueLocalV6 = raw.length == 16 && (raw[0] & 0xfe) == 0xfc;
            return address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || uniqueLocalV6;
        } catch (IllegalArgumentException | IOException e) {
            return false;
        }
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

    /** Turn a guest session into a real account, carrying the guest's games and ratings across. */
    private void upgrade(HttpExchange x) throws IOException {
        SessionToken token = bearer(x);
        if (token == null) {
            return;
        }
        String guestId = token.playerId();
        if (s.accounts().findById(guestId).isPresent()) {
            send(x, 400, Map.of("error", "already a registered account"));
            return;
        }
        JsonNode b = read(x);
        try {
            Account a = s.accountService().register(
                    b.path("username").asText(), b.path("email").asText(), b.path("password").asText());
            s.games().reassign(guestId, a.id());
            s.leaderboard().reassign(guestId, a.id());
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

    private void heartbeat(HttpExchange x) throws IOException {
        SessionToken token = bearer(x);
        if (token == null) {
            return;
        }
        s.presence().online(token.playerId());
        send(x, 200, Map.of("ok", true));
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
        JsonNode body = read(x);
        String room = body.path("room").asText();
        if (room.isBlank()) {
            send(x, 400, Map.of("error", "room required"));
            return;
        }
        Lobby.Seat seat;
        try {
            seat = s.lobby().join(room, token.playerId(), body.path("seat").asText(null));
        } catch (IllegalArgumentException | IllegalStateException e) {
            send(x, 409, Map.of("error", e.getMessage()));
            return;
        }
        s.presence().enterGame(token.playerId(), seat.gameId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomId", seat.roomId());
        payload.put("gameId", seat.gameId());
        payload.put("partnerGameId", seat.partnerGameId());
        payload.put("board", seat.board());
        payload.put("color", seat.color());
        payload.put("partnerColor", seat.partnerColor());
        payload.put("team", seat.team());
        payload.put("seat", seat.seat());
        payload.put("seatsFilled", seat.seatsFilled());
        payload.put("humansJoined", seat.humansJoined());
        payload.put("humanTarget", seat.humanTarget());
        payload.put("variant", seat.variant());
        payload.put("humanSeats", seat.humanSeats());
        payload.put("availableSeats", seat.availableSeats());
        s.matches().get(seat.gameId()).ifPresent(game -> {
            payload.put("whiteName", displayName(game.whiteId()));
            payload.put("blackName", displayName(game.blackId()));
        });
        s.matches().get(seat.partnerGameId()).ifPresent(game -> {
            payload.put("partnerWhiteName", displayName(game.whiteId()));
            payload.put("partnerBlackName", displayName(game.blackId()));
        });
        send(x, 200, payload);
    }

    private void playCreate(HttpExchange x) throws IOException {
        SessionToken token = bearer(x);
        if (token == null) {
            return;
        }
        JsonNode b = read(x);
        if (!Set.of("easy", "medium", "hard").contains(b.path("aiLevel").asText("medium"))) {
            send(x, 400, Map.of("error", "AI level must be easy, medium or hard"));
            return;
        }
        Variant variant = Variant.valueOf(b.path("variant").asText("standard").toUpperCase());
        long baseMs = Math.max(1, Math.min(180, b.path("minutes").asInt(5))) * 60_000L;
        long incMs = Math.max(0, Math.min(180, b.path("increment").asInt(0))) * 1_000L;
        TimeControl tc = TimeControl.fischer(baseMs, incMs);
        String gameId;
        if (variant == Variant.BUGHOUSE) {
            Side humanColor = "black".equalsIgnoreCase(b.path("color").asText("white")) ? Side.BLACK : Side.WHITE;
            int humanPlayers = Math.max(1, Math.min(4, b.path("humanPlayers").asInt(4)));
            Set<String> humanSeats = new LinkedHashSet<>();
            if (b.path("humanSeats").isArray()) {
                b.path("humanSeats").forEach(node -> humanSeats.add(node.asText()));
            }
            try {
                gameId = humanSeats.isEmpty()
                        ? s.lobby().createBughouse(token.playerId(), tc, humanColor, humanPlayers)
                        : s.lobby().createBughouse(token.playerId(), tc, humanColor, humanSeats);
            } catch (IllegalArgumentException e) {
                send(x, 400, Map.of("error", e.getMessage()));
                return;
            }
        } else if (b.path("vsEngine").asBoolean(false)) {
            Side humanColor = "black".equalsIgnoreCase(b.path("color").asText("white")) ? Side.BLACK : Side.WHITE;
            gameId = s.lobby().createVsEngine(token.playerId(), variant, tc, humanColor);
        } else {
            boolean rated = variant != Variant.BUGHOUSE && b.path("rated").asBoolean(true);
            gameId = s.lobby().create(token.playerId(), variant, tc, rated);
        }
        s.lobby().configureAiLevel(gameId, b.path("aiLevel").asText("medium"));
        send(x, 200, Map.of("gameId", gameId));
    }

    private void replayRoom(HttpExchange x) throws IOException {
        if (notPost(x)) return;
        SessionToken token = bearer(x);
        if (token == null) return;
        try {
            String gameId = s.lobby().replay(read(x).path("room").asText(), token.playerId());
            send(x, 200, Map.of("gameId", gameId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            send(x, 409, Map.of("error", e.getMessage()));
        }
    }

    // ---- quick match ----

    private void seek(HttpExchange x) throws IOException {
        SessionToken token = bearer(x);
        if (token == null) {
            return;
        }
        JsonNode b = read(x);
        Variant variant = Variant.valueOf(b.path("variant").asText("standard").toUpperCase());
        if (variant == Variant.BUGHOUSE) {
            send(x, 400, Map.of("error", "use a four-player room for bughouse"));
            return;
        }
        long baseMs = Math.max(1, Math.min(180, b.path("minutes").asInt(5))) * 60_000L;
        long incMs = Math.max(0, Math.min(180, b.path("increment").asInt(0))) * 1_000L;
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
                    : opponentId.startsWith(Lobby.ENGINE_ID) ? "Computer"
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
                    : opponentId.startsWith(Lobby.ENGINE_ID) ? "Computer"
                    : s.accounts().findById(opponentId).map(Account::username).orElse("guest");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("gameId", g.gameId());
            row.put("color", me.equals(g.whiteId()) ? "white" : "black");
            row.put("opponent", opponent);
            row.put("variant", s.lobby().isBughouseGame(g.gameId())
                    ? "bughouse" : g.variant().name().toLowerCase());
            row.put("minutes", (int) (g.timeControl().baseMs() / 60_000));
            row.put("increment", (int) (g.timeControl().incrementMs() / 1_000));
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
        String roomId = s.lobby().bughouseRoomId(gameId);
        if (roomId.equals(gameId) && (gameId.endsWith("-a") || gameId.endsWith("-b"))) {
            roomId = gameId.substring(0, gameId.length() - 2);
        }
        boolean bughouse = s.lobby().isBughouseGame(gameId)
                || s.games().find(roomId + "-a").map(g -> "bughouse".equals(g.variant())).orElse(false);
        List<String> ids = bughouse ? List.of(roomId + "-a", roomId + "-b") : List.of(gameId);
        List<Map<String, Object>> boards = new ArrayList<>();
        String variant = bughouse ? "bughouse" : s.matches().get(gameId)
                .map(g -> g.variant().name().toLowerCase())
                .orElseGet(() -> s.games().find(gameId).map(GameRecord::variant).orElse("standard"));
        if (!bughouse && s.matches().get(gameId).isEmpty() && s.games().find(gameId).isEmpty()) {
            send(x, 404, Map.of("error", "Game not found"));
            return;
        }
        for (int index = 0; index < ids.size(); index++) {
            String id = ids.get(index);
            String initial = s.matches().get(id).map(g -> g.currentSnapshot().initialFen().raw())
                    .orElse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                            + ("standard".equals(variant) ? "" : "[]") + " w KQkq - 0 1");
            boards.add(Map.of("gameId", id, "label", bughouse ? index == 0 ? "A" : "B" : "",
                    "initialFen", initial, "plies", reviewPlies(id)));
        }
        send(x, 200, Map.of("gameId", gameId, "roomId", roomId, "variant", variant,
                "initialFen", boards.get(0).get("initialFen"), "plies", boards.get(0).get("plies"), "boards", boards));
    }

    private List<Map<String, Object>> reviewPlies(String gameId) {
        List<Map<String, Object>> plies = new ArrayList<>();
        for (PersistedPly p : s.matchRepo().plies(gameId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("version", p.version());
            row.put("uci", p.move().value());
            row.put("fen", p.fen().raw());
            plies.add(row);
        }
        return plies;
    }

    private void analyze(HttpExchange x) throws IOException {
        Map<String, String> q = query(x);
        String fen = q.get("fen");
        if (fen == null || fen.isBlank()) {
            send(x, 400, Map.of("error", "fen required"));
            return;
        }
        Variant variant = Variant.valueOf(q.getOrDefault("variant", "standard").toUpperCase());
        try {
            SearchResult r = s.analysis().bestMove(variant, Fen.parse(fen), SearchBudget.movetime(500));
            Map<String, Object> score = r.score() instanceof Score.Mate m
                    ? Map.of("type", "mate", "value", m.movesToMate())
                    : Map.of("type", "cp", "value", ((Score.Cp) r.score()).centipawns());
            send(x, 200, Map.of(
                    "bestMove", r.bestMove().value(),
                    "score", score,
                    "depth", r.depth(),
                    "pv", r.pv().stream().map(Uci::value).toList()));
        } catch (RuntimeException e) {
            send(x, 503, Map.of("error", "analysis unavailable"));
        }
    }

    private void gamesLive(HttpExchange x) throws IOException {
        send(x, 200, liveGamesPayload());
    }

    private void liveEvents(HttpExchange x) throws IOException {
        if (!"GET".equals(x.getRequestMethod())) {
            send(x, 405, Map.of("error", "method not allowed"));
            return;
        }
        if (liveViewers.incrementAndGet() > 64) {
            liveViewers.decrementAndGet();
            send(x, 503, Map.of("error", "Live feed is busy; retry shortly"));
            return;
        }
        if (liveViewers.get() == 1) liveJson = mapper.writeValueAsString(liveGamesPayload(true));
        x.getResponseHeaders().set("Content-Type", "text/event-stream");
        x.getResponseHeaders().set("Cache-Control", "no-cache, no-transform");
        x.sendResponseHeaders(200, 0);
        try (OutputStream output = x.getResponseBody()) {
            String sent = null;
            while (running) {
                String latest = liveJson;
                if (sent != latest) {
                    output.write(("event: games\ndata: " + latest + "\n\n").getBytes(StandardCharsets.UTF_8));
                    output.flush();
                    sent = latest;
                }
                try { Thread.sleep(250); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (IOException ignored) {
            // Browser closed its stream.
        } finally {
            liveViewers.decrementAndGet();
            x.close();
        }
    }

    @SuppressWarnings("unchecked")
    private String feedSignature(Map<String, Object> payload) {
        StringBuilder key = new StringBuilder();
        for (Map<String, Object> game : (List<Map<String, Object>>) payload.get("games")) {
            key.append(game.get("gameId")).append(':').append(game.get("spectators"))
                    .append(game.get("live")).append(game.get("result"));
            for (Map<String, Object> board : (List<Map<String, Object>>) game.get("boards")) {
                key.append(board.get("white")).append(board.get("black"))
                        .append(board.get("fen")).append(board.get("moveCount"));
            }
        }
        return key.toString();
    }

    private Map<String, Object> liveGamesPayload() {
        return liveGamesPayload(false);
    }

    private Map<String, Object> liveGamesPayload(boolean includeEnded) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<MatchGame> candidates = new ArrayList<>(s.matches().liveGames());
        long now = System.currentTimeMillis();
        for (MatchGame game : candidates) observedGames.put(game.gameId(), game);
        for (Map.Entry<String, MatchGame> entry : observedGames.entrySet()) {
            if (!entry.getValue().isOver()) continue;
            long at = endedGames.computeIfAbsent(entry.getKey(), id -> now);
            if (now - at > 60000 || endedGames.size() > 64) {
                observedGames.remove(entry.getKey());
                endedGames.remove(entry.getKey());
            } else if (includeEnded) candidates.add(entry.getValue());
        }
        for (MatchGame g : candidates) {
            if (g.variant() == Variant.BUGHOUSE && !s.lobby().isPrimaryBughouseBoard(g.gameId())) {
                continue;
            }
            List<MatchGame> boards = g.variant() == Variant.BUGHOUSE
                    ? s.lobby().bughouseBoards(g.gameId()) : List.of(g);
            if (boards.isEmpty() || boards.stream().anyMatch(board -> board.blackId() == null)) {
                continue; // still waiting for an opponent
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("gameId", s.lobby().bughouseRoomId(g.gameId()));
            row.put("white", displayName(g.whiteId()));
            row.put("black", displayName(g.blackId()));
            row.put("variant", g.variant().name().toLowerCase());
            row.put("speed", SpeedBucket.of(g.timeControl()).name().toLowerCase());
            row.put("minutes", (int) (g.timeControl().baseMs() / 60_000));
            row.put("increment", (int) (g.timeControl().incrementMs() / 1_000));
            row.put("rated", g.rated());
            GameSnapshot primary = g.currentSnapshot();
            row.put("live", primary.result().isEmpty());
            primary.result().ifPresent(result -> {
                row.put("result", result.reason());
                row.put("winner", result.winner().map(side -> side == Side.WHITE
                        ? displayName(g.whiteId()) : displayName(g.blackId())).orElse(""));
            });
            Set<String> players = new java.util.HashSet<>();
            Set<String> viewers = new java.util.HashSet<>();
            for (MatchGame board : boards) {
                players.add(board.whiteId());
                players.add(board.blackId());
                viewers.addAll(board.viewerIds());
            }
            viewers.removeAll(players);
            row.put("spectators", viewers.size());
            List<Map<String, Object>> boardRows = new ArrayList<>();
            for (int index = 0; index < boards.size(); index++) {
                boardRows.add(liveBoard(boards.get(index), boards.size() == 1 ? "" : index == 0 ? "A" : "B"));
            }
            row.put("boards", boardRows);
            row.put("moveCount", boardRows.stream().mapToInt(board -> (int) board.get("moveCount")).sum());
            out.add(row);
        }
        return Map.of("games", out);
    }

    private Map<String, Object> liveBoard(MatchGame game, String label) {
        GameSnapshot snapshot = game.currentSnapshot();
        Map<String, Object> board = new LinkedHashMap<>();
        board.put("gameId", game.gameId());
        board.put("label", label);
        board.put("white", displayName(game.whiteId()));
        board.put("black", displayName(game.blackId()));
        board.put("fen", snapshot.fen().raw());
        board.put("sideToMove", snapshot.sideToMove().name().toLowerCase());
        board.put("moveCount", snapshot.moves().size());
        board.put("lastMove", snapshot.moves().isEmpty()
                ? "" : snapshot.moves().get(snapshot.moves().size() - 1).value());
        board.put("whiteRemainingMs", snapshot.clockSample().whiteRemainingMs());
        board.put("blackRemainingMs", snapshot.clockSample().blackRemainingMs());
        board.put("serverTimestamp", snapshot.clockSample().serverTimestamp());
        board.put("inCheck", snapshot.inCheck());
        return board;
    }

    private void gamesOpen(HttpExchange x) throws IOException {
        SessionToken token = bearer(x);
        if (token == null) {
            return;
        }
        String me = token.playerId();
        List<Map<String, Object>> out = new ArrayList<>();
        for (MatchGame g : s.matches().liveGames()) {
            if (g.variant() == Variant.BUGHOUSE && !s.lobby().isPrimaryBughouseBoard(g.gameId())) {
                continue;
            }
            if (g.variant() == Variant.BUGHOUSE) {
                int seats = s.lobby().bughouseSeatsFilled(g.gameId());
                if (seats >= 4 || s.lobby().isBughouseParticipant(g.gameId(), me)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("gameId", s.lobby().bughouseRoomId(g.gameId()));
                row.put("creator", displayName(g.whiteId()));
                row.put("variant", "bughouse");
                row.put("rated", false);
                row.put("minutes", (int) (g.timeControl().baseMs() / 60_000));
                row.put("increment", (int) (g.timeControl().incrementMs() / 1_000));
                row.put("seatsFilled", seats);
                row.put("humansJoined", s.lobby().bughouseHumansJoined(g.gameId()));
                row.put("humanTarget", s.lobby().bughouseHumanTarget(g.gameId()));
                out.add(row);
                continue;
            }
            if (g.blackId() != null || Lobby.ENGINE_ID.equals(g.whiteId()) || me.equals(g.whiteId())) {
                continue; // seat taken, an engine game, or the viewer's own challenge
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("gameId", g.gameId());
            row.put("creator", displayName(g.whiteId()));
            row.put("variant", g.variant().name().toLowerCase());
            row.put("rated", g.rated());
            row.put("minutes", (int) (g.timeControl().baseMs() / 60_000));
            row.put("increment", (int) (g.timeControl().incrementMs() / 1_000));
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
        if (id.startsWith(Lobby.ENGINE_ID)) {
            return "Computer";
        }
        if (id.startsWith("guest-")) return "Guest " + id.substring(6, Math.min(12, id.length()));
        long now = System.currentTimeMillis();
        CachedName cached = playerNames.get(id);
        if (cached != null && cached.expiresAt() > now) return cached.name();
        String name = s.accounts().findById(id).map(Account::username).orElse("Guest");
        if (playerNames.size() > 10000) playerNames.clear();
        playerNames.put(id, new CachedName(name, now + 30000));
        return name;
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
            SessionToken token = s.tokens().verify(auth.substring(7), s.clock().getAsLong());
            s.sessions().register(token.sessionId(), token.playerId());
            return token;
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
