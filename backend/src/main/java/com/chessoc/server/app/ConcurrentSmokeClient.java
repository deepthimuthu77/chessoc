package com.chessoc.server.app;

import com.chessoc.server.capability.Capability;
import com.chessoc.server.capability.CapabilityKind;
import com.chessoc.server.protocol.Disposition;
import com.chessoc.server.protocol.Envelope;
import com.chessoc.server.protocol.GameSnapshot;
import com.chessoc.server.protocol.MessageType;
import com.chessoc.server.protocol.Messages;
import com.chessoc.server.protocol.ProtocolCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/**
 * Live concurrent regression harness. Each human seat has a distinct guest session and WSS client.
 * Creates two AI rooms per variant plus human/human standard, crazyhouse and four-human Bughouse.
 * Measures live-feed GET latency and SSE delivery while every board plays at least four plies.
 *
 * Run: java -cp "build/install/chessoc-server/lib/*"
 * com.chessoc.server.app.ConcurrentSmokeClient https://host/api wss://host [plies=4] [copies=2]
 * [timeoutSeconds=60]. Local development also accepts http://host:8080 ws://host:8081.
 * TLS uses normal certificate validation; install the local CA in the JVM truststore for LAN TLS.
 */
public final class ConcurrentSmokeClient {
    private final String httpBase;
    private final String wsBase;
    private final int targetPlies;
    private final int copies;
    private final int timeoutSeconds;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProtocolCodec codec = new ProtocolCodec();
    private final List<Player> players = new ArrayList<>();
    private final Map<String, Board> boards = new ConcurrentHashMap<>();
    private final Set<String> rooms = ConcurrentHashMap.newKeySet();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final AtomicBoolean playing = new AtomicBoolean();
    private final AtomicInteger accepted = new AtomicInteger();

    private ConcurrentSmokeClient(String httpBase, String wsBase, int targetPlies, int copies, int timeoutSeconds) {
        this.httpBase = httpBase.replaceAll("/+$", "");
        this.wsBase = wsBase.replaceAll("/+$", "");
        if (targetPlies < 2 || copies < 1 || timeoutSeconds < 1) {
            throw new IllegalArgumentException("plies >= 2, copies >= 1 and positive timeout required");
        }
        this.targetPlies = targetPlies;
        this.copies = copies;
        this.timeoutSeconds = timeoutSeconds;
    }

    public static void main(String[] args) throws Exception {
        new ConcurrentSmokeClient(args.length > 0 ? args[0] : "https://localhost/api",
                args.length > 1 ? args[1] : "wss://localhost",
                args.length > 2 ? Integer.parseInt(args[2]) : 4,
                args.length > 3 ? Integer.parseInt(args[3]) : 2,
                args.length > 4 ? Integer.parseInt(args[4]) : 60).run();
    }

    private static final class Board {
        final String label;
        final AtomicInteger plies = new AtomicInteger();
        Board(String label) { this.label = label; }
    }

    private void run() throws Exception {
        var probes = Executors.newScheduledThreadPool(2);
        var latencies = new java.util.concurrent.CopyOnWriteArrayList<Long>();
        var sseStream = new AtomicReference<InputStream>();
        var sseFirst = new CountDownLatch(1);
        var sseEvents = new AtomicInteger();
        AtomicBoolean closing = new AtomicBoolean();
        try {
            for (String variant : List.of("standard", "crazyhouse", "bughouse")) {
                for (int copy = 0; copy < copies; copy++) {
                    createRoom(variant, true, variant + "-AI-" + (copy + 1));
                }
                createRoom(variant, false, variant + "-human");
            }
            System.out.printf("Ready: %d rooms, %d boards, %d distinct human seats, transport=%s%n",
                    rooms.size(), boards.size(), players.stream().filter(p -> p.canPlay).count(),
                    URI.create(wsBase).getScheme());
            probes.scheduleWithFixedDelay(() -> {
                try {
                    long start = System.nanoTime();
                    request("/games/live", null, null);
                    latencies.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
                } catch (Exception e) {
                    if (!closing.get()) failure.compareAndSet(null, e);
                }
            }, 0, 100, TimeUnit.MILLISECONDS);
            probes.execute(() -> {
                long start = System.nanoTime();
                try {
                    var response = http.send(HttpRequest.newBuilder(URI.create(httpBase + "/games/live/events"))
                                    .timeout(Duration.ofSeconds(timeoutSeconds)).GET().build(),
                            HttpResponse.BodyHandlers.ofInputStream());
                    try (InputStream stream = response.body()) {
                        sseStream.set(stream);
                        if (response.statusCode() != 200) {
                            throw new IllegalStateException("SSE status " + response.statusCode());
                        }
                        var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                        for (String line; !closing.get() && (line = reader.readLine()) != null;) {
                            if (line.startsWith("data:")) {
                                mapper.readTree(line.substring(5).strip());
                                if (sseEvents.incrementAndGet() == 1) {
                                    System.out.println("SSE first_event_ms="
                                            + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
                                    sseFirst.countDown();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    if (!closing.get()) failure.compareAndSet(null, e);
                }
            });
            long started = System.nanoTime();
            playing.set(true);
            players.forEach(Player::playLatest);
            long deadline = started + TimeUnit.SECONDS.toNanos(timeoutSeconds);
            while (boards.values().stream().anyMatch(board -> board.plies.get() < targetPlies)
                    && System.nanoTime() < deadline && failure.get() == null) {
                Thread.sleep(20);
            }
            if (failure.get() != null) throw new IllegalStateException("Concurrent smoke failed", failure.get());
            List<String> stalled = boards.entrySet().stream()
                    .filter(entry -> entry.getValue().plies.get() < targetPlies)
                    .map(entry -> entry.getValue().label + "=" + entry.getValue().plies.get()).toList();
            if (!stalled.isEmpty()) throw new IllegalStateException("Boards stalled: " + stalled);
            if (!sseFirst.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("No SSE data received");
            // Verify persisted moves for every board independently, rather than accepting any AI reply.
            for (String room : rooms) {
                JsonNode review = request("/game/moves?id=" + encode(room), null, null);
                for (JsonNode board : review.path("boards")) {
                    if (board.path("plies").size() < targetPlies) {
                        throw new IllegalStateException("Missing persisted moves for " + board.path("gameId"));
                    }
                }
            }
            List<Long> sorted = latencies.stream().sorted().toList();
            if (sorted.isEmpty()) throw new IllegalStateException("No feed latency samples");
            System.out.println("feed_ms=" + latencies + " p50=" + percentile(sorted, .50)
                    + " p95=" + percentile(sorted, .95) + " max=" + sorted.get(sorted.size() - 1)
                    + " SSE_events=" + sseEvents.get());
            boards.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getValue().label))
                    .forEach(entry -> System.out.println(entry.getValue().label + " game=" + entry.getKey()
                            + " plies=" + entry.getValue().plies.get()));
            System.out.println("CONCURRENT SMOKE OK accepted_human_moves=" + accepted.get()
                    + " elapsed_ms=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        } finally {
            closing.set(true);
            playing.set(false);
            for (Player player : players) {
                player.close();
            }
            InputStream stream = sseStream.get();
            if (stream != null) stream.close();
            probes.shutdownNow();
            for (Player player : players) {
                player.closeBlocking();
            }
        }
    }

    private void createRoom(String variant, boolean bots, String label) throws Exception {
        String creator = login();
        Map<String, Object> options = Map.of("variant", variant, "vsEngine", bots,
                "humanPlayers", bots ? 1 : 4, "minutes", 5, "increment", 0,
                "color", "white", "aiLevel", "hard", "rated", false);
        String room = required(request("/play/create", options, creator), "gameId");
        rooms.add(room);
        join(creator, room, label, true);
        if (!bots) {
            int humans = variant.equals("bughouse") ? 4 : 2;
            for (int seat = 1; seat < humans; seat++) {
                join(login(), room, label, true);
            }
        }
    }

    private String login() throws Exception {
        return required(request("/login", Map.of(), null), "token");
    }

    private void join(String token, String room, String label, boolean canPlay) throws Exception {
        JsonNode seat = request("/join", Map.of("room", room), token);
        String game = required(seat, "gameId");
        connect(token, game, label + (seat.path("board").asText("").equals("B") ? "-B" : "-A"), canPlay);
        String partner = seat.path("partnerGameId").asText("");
        // One observer per board is enough. A fresh ticket is used for every socket.
        if (!partner.isEmpty() && !boards.containsKey(partner)) {
            connect(token, partner, label + "-B", false);
        }
    }

    private void connect(String token, String game, String label, boolean canPlay) throws Exception {
        boards.computeIfAbsent(game, ignored -> new Board(label));
        String ticket = required(request("/ticket", Map.of(), token), "ticket");
        Player player = new Player(URI.create(wsBase + "/game?ticket=" + encode(ticket)
                + "&gameId=" + encode(game)), game, canPlay);
        players.add(player);
        if (!player.connectBlocking(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Cannot connect " + game);
        }
        if (!player.initial.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("No initial snapshot " + game, failure.get());
        }
    }

    private final class Player extends WebSocketClient {
        private final String game;
        private final boolean canPlay;
        private final CountDownLatch initial = new CountDownLatch(1);
        private GameSnapshot latest;
        private List<Capability> caps = List.of();
        private String pending;
        private long sentVersion = -1;
        private long pendingVersion = -1;

        Player(URI uri, String game, boolean canPlay) {
            super(uri);
            this.game = game;
            this.canPlay = canPlay;
        }

        @Override public void onOpen(ServerHandshake handshake) {}

        @Override public synchronized void onMessage(String message) {
            try {
                Envelope env = codec.decode(message);
                if (MessageType.SNAPSHOT.equals(env.type())) {
                    Messages.Snapshot snapshot = codec.payload(env, Messages.Snapshot.class);
                    update(snapshot.snapshot(), snapshot.capabilities());
                } else if (MessageType.RESYNC_OK.equals(env.type())) {
                    Messages.ResyncOk snapshot = codec.payload(env, Messages.ResyncOk.class);
                    update(snapshot.snapshot(), snapshot.capabilities());
                } else if (MessageType.RESULT.equals(env.type())) {
                    Messages.Result result = codec.payload(env, Messages.Result.class);
                    if (result.intentId().equals(pending)) {
                        pending = null;
                        if (result.disposition() == Disposition.ACCEPTED) {
                            accepted.incrementAndGet();
                        } else if (latest != null && latest.version() > pendingVersion) {
                            // A partner capture can revoke the capability between snapshot and intent.
                            playLatest();
                            return;
                        } else if (result.disposition() != Disposition.STALE) {
                            throw new IllegalStateException(game + " move rejected: " + result.reason());
                        }
                    }
                    result.snapshot().ifPresent(snapshot -> update(snapshot,
                            result.capabilities().orElse(List.of())));
                } else if (MessageType.ERROR.equals(env.type()) || MessageType.AUTH_ERR.equals(env.type())) {
                    throw new IllegalStateException("Socket error: " + message);
                }
            } catch (Exception e) {
                failure.compareAndSet(null, e);
                initial.countDown();
            }
        }

        private synchronized void update(GameSnapshot snapshot, List<Capability> capabilities) {
            if (latest != null && snapshot.version() < latest.version()) return;
            latest = snapshot;
            caps = capabilities;
            boards.get(game).plies.accumulateAndGet(snapshot.moves().size(), Math::max);
            initial.countDown();
            playLatest();
        }

        synchronized void playLatest() {
            if (!playing.get() || !canPlay || pending != null || latest == null
                    || latest.result().isPresent() || latest.moves().size() >= targetPlies
                    || sentVersion == latest.version()) return;
            caps.stream().filter(cap -> cap.kind() == CapabilityKind.MOVE)
                    .sorted(Comparator.comparing(cap -> cap.moveUci().value())).findFirst().ifPresent(cap -> {
                        sentVersion = latest.version();
                        pendingVersion = latest.version();
                        pending = java.util.UUID.randomUUID().toString();
                        send(codec.encode(MessageType.INTENT,
                                new Messages.Intent(pending, cap.id(), Map.of(), System.currentTimeMillis())));
                    });
        }

        @Override public void onClose(int code, String reason, boolean remote) {
            if (playing.get() && boards.get(game).plies.get() < targetPlies) {
                failure.compareAndSet(null, new IllegalStateException(game + " socket closed: " + reason));
            }
        }
        @Override public void onError(Exception ex) {
            failure.compareAndSet(null, ex);
            initial.countDown();
        }
    }

    private JsonNode request(String path, Object body, String bearer) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(httpBase + path)).timeout(Duration.ofSeconds(5));
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        if (body == null) {
            request.GET();
        } else {
            request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        }
        var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException(path + " status=" + response.statusCode() + " " + response.body());
        }
        return mapper.readTree(response.body());
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isEmpty()) throw new IllegalStateException("Missing " + field + ": " + node);
        return value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static long percentile(List<Long> values, double quantile) {
        return values.get(Math.max(0, (int) Math.ceil(values.size() * quantile) - 1));
    }
}
