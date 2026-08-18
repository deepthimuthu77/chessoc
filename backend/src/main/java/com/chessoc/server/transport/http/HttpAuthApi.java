package com.chessoc.server.transport.http;

import com.chessoc.server.domain.Fen;
import com.chessoc.server.domain.Variant;
import com.chessoc.server.match.TimeControl;
import com.chessoc.server.match.state.GameSpec;
import com.chessoc.server.match.state.MatchService;
import com.chessoc.server.protocol.ProtocolMapper;
import com.chessoc.server.session.AuthException;
import com.chessoc.server.session.SessionRegistry;
import com.chessoc.server.session.SessionToken;
import com.chessoc.server.session.TicketService;
import com.chessoc.server.session.TokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;

/**
 * Stateless request/response endpoints (§5): login, single-use live-connection ticket, and dev game
 * creation. Login here is a dev stand-in; production swaps in OAuth / email+password behind the same
 * token issuance.
 */
public final class HttpAuthApi {

    private static final String STANDARD_START =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private final HttpServer server;
    private final TokenService tokens;
    private final TicketService tickets;
    private final SessionRegistry sessions;
    private final MatchService matches;
    private final long tokenTtlMs;
    private final LongSupplier clock;
    private final ObjectMapper mapper = ProtocolMapper.create();

    public HttpAuthApi(int port, TokenService tokens, TicketService tickets, SessionRegistry sessions,
                       MatchService matches, long tokenTtlMs, LongSupplier clock) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.tokens = tokens;
        this.tickets = tickets;
        this.sessions = sessions;
        this.matches = matches;
        this.tokenTtlMs = tokenTtlMs;
        this.clock = clock;
        server.createContext("/login", this::login);
        server.createContext("/ticket", this::ticket);
        server.createContext("/game", this::game);
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

    private void login(HttpExchange exchange) throws IOException {
        if (!post(exchange)) {
            return;
        }
        String playerId = read(exchange).path("playerId").asText();
        if (playerId.isBlank()) {
            send(exchange, 400, Map.of("error", "playerId required"));
            return;
        }
        String sessionId = UUID.randomUUID().toString();
        sessions.register(sessionId, playerId);
        String token = tokens.issue(playerId, sessionId, clock.getAsLong() + tokenTtlMs);
        send(exchange, 200, Map.of("sessionId", sessionId, "token", token));
    }

    private void ticket(HttpExchange exchange) throws IOException {
        if (!post(exchange)) {
            return;
        }
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            send(exchange, 401, Map.of("error", "missing bearer token"));
            return;
        }
        try {
            SessionToken token = tokens.verify(auth.substring(7), clock.getAsLong());
            send(exchange, 200, Map.of("ticket", tickets.issue(token.sessionId())));
        } catch (AuthException e) {
            send(exchange, 401, Map.of("error", e.getMessage()));
        }
    }

    private void game(HttpExchange exchange) throws IOException {
        if (!post(exchange)) {
            return;
        }
        JsonNode body = read(exchange);
        String whiteId = body.path("whiteId").asText();
        String blackId = body.path("blackId").asText();
        if (whiteId.isBlank() || blackId.isBlank()) {
            send(exchange, 400, Map.of("error", "whiteId and blackId required"));
            return;
        }
        Variant variant = Variant.valueOf(body.path("variant").asText("standard").toUpperCase());
        String gameId = UUID.randomUUID().toString();
        matches.create(new GameSpec(gameId, variant, Fen.parse(STANDARD_START), whiteId, blackId,
                TimeControl.fischer(180_000, 2_000), true));
        send(exchange, 200, Map.of("gameId", gameId));
    }

    private boolean post(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            send(exchange, 405, Map.of("error", "method not allowed"));
            return false;
        }
        return true;
    }

    private JsonNode read(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        return body.length == 0 ? mapper.createObjectNode() : mapper.readTree(body);
    }

    private void send(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(payload);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
