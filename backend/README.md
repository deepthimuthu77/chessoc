# chessoc-server

Java Game Server for chessoc — authoritative orchestration (sessions, clocks, turns, matchmaking,
persistence, broadcast). It computes **no** chess semantics; all chess truth is delegated to the
vector64 engine. See `doc/BACKEND_ARCHITECTURE.md` and `doc/ENGINE_INTEGRATION.md`.

## Run in Docker (postgres + engine + server)

```bash
docker compose up --build
```

Brings up Postgres, the server (HTTP 8080, WSS 8081), and a production web build
(`../web`, Next.js standalone) on host port 3001. The server image bundles the official vector-64
v1.1.1 Linux binaries: lean validators and the full search engine with embedded NNUE. Docker downloads
the release assets and notices and verifies pinned SHA-256 hashes during the build. Both amd64
(x86-64-v2) and arm64 are supported. The optional Python engine is available under the `dev-engine`
Compose profile. Set `CHESSOC_HTTP_HOST_PORT` to change the direct API host port if 8080 is occupied.

Caddy serves the complete app on both `http://<host-ip>` and `https://<host-ip>`. With no DNS name,
HTTPS uses Caddy's local CA and creates a certificate for the requested LAN IP. After the first HTTPS
request, install `caddy-data/caddy/pki/authorities/local/root.crt` as a trusted root certificate on
each demo device; keep that private CA directory out of source control.

The web container is the production build; for local UI iteration run the dev server instead:

```bash
cd ../web && npm run dev   # http://localhost:3000
```

Smoke-test a live move end-to-end (login → game → WSS → engine → persisted):

```bash
java -cp "build/install/chessoc-server/lib/*" com.chessoc.server.app.SmokeClient
```

Exercise simultaneous human and AI matches in all three variants through Caddy:

```bash
docker compose exec -T server java -cp '/opt/chessoc/lib/*' com.chessoc.server.app.ConcurrentSmokeClient http://caddy/api ws://caddy 4 2 75
```

This creates nine rooms (12 boards), drives legal human moves over WebSocket, checks persisted
plies, and measures the public live feed while the real engine searches. The arguments are the
target plies per board, AI rooms per variant, and timeout in seconds. AI searches use two bounded
workers by default (`CHESSOC_SEARCH_WORKERS`), with one engine thread per search; clock timers and
room snapshots remain independent of search work. The Watch page receives shared SSE updates,
with periodic clock synchronization and a polling fallback.

### Use the real vector64 binary

The default stack already runs the real Linux engine. To use a separate Windows or local build,
run it on the host behind the engine bridge; the containerized server reaches it over
`host.docker.internal`:

```bash
# 1. host: expose the binary over TCP (one engine process per connection)
java -cp "build/install/chessoc-server/lib/*" \
  com.chessoc.server.app.EngineBridge 9200 /path/to/ChessEngine-all

# 2. stack: point the server at the bridge
docker compose -f docker-compose.yml -f docker-compose.binary.yml up -d server
```

The TCP overlay takes precedence over the bundled process commands.

The downloaded Windows v1.1.1 full engine is stored locally at
`engine/bin/v1.1.1/STK-Vector-64-all.exe` (ignored by Git). Its official SHA-256 is
`98641ff620a66514f5ebad5cedb27a756bd6dc5a90f1e2104642352bbfedc6ab`.
Release assets and source: https://github.com/fcmv/Vector-64/releases/tag/v1.1.1.

## Build & run (local)

```bash
./gradlew build
./gradlew run
```

Java 17, Gradle wrapper pinned. Engine selection: `CHESSOC_ENGINE_HOST`/`CHESSOC_ENGINE_PORT` (TCP
service) or `CHESSOC_VALIDATOR_CMD` (spawned binary); neither → chess calls fail by design.
Persistence: `CHESSOC_DB_URL`/`_USER`/`_PASSWORD` (Postgres) or in-memory when unset.

Play path: `POST /login {playerId}` → token · `POST /ticket` (Bearer token) → single-use ticket ·
`POST /game {whiteId,blackId}` → gameId · connect `ws://host:8081/game?ticket=&gameId=`.

## Layout

Modular monolith; layer boundaries are interfaces (contract-first) so any module can be promoted to
its own service later without rewriting callers.

- `domain` — value types: `Side`, `Variant`, `PieceType`, `Terminal`, `Uci`, `Fen` (canonical wire
  format, stored verbatim so `~` and `[...]` survive persistence).
- `protocol` — versioned envelope + message catalog (§8), codec with major-version refusal.
- `capability` — single-use, version-bound action authorisations (§7).
- `engine` / `engine.adapter` — `EngineAdapter` contract, validator/search wire codecs, and the real
  Adapter: single-request worker pool, process + TCP line-protocol workers with timeout + eviction.
- `match` — server clocks, time-control buckets, drop-variant `Reserve`, `Match` aggregate.
- `match.state` — the authoritative single-writer game: pre-commit gate, capability issuance from
  engine legal moves, flag timers, terminal/resign/draw/timeout resolution, per-ply persistence,
  broadcast.
- `matchmaking` — Glicko-2 ratings; queue + friend challenges bucketed by variant × speed.
- `session` — HMAC session tokens, single-use tickets, rate limiter, session registry.
- `transport` — `LiveConnection` (wire ↔ match), HTTP auth API, WebSocket live server, app gateway.
- `app` — config, module assembly, entry point.

## Status

Runnable end-to-end in Docker for standard, crazyhouse, and coupled two-board bughouse games. Live
room checkpoints, seats, positions, reserves, clocks, and pending draw state survive server restarts
when Postgres is configured. The stack has been exercised with both the real vector64 binary
(`ChessEngine-all`, via the engine bridge) and the in-container python-chess development engine.
