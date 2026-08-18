# chessoc-server

Java Game Server for chessoc — authoritative orchestration (sessions, clocks, turns, matchmaking,
persistence, broadcast). It computes **no** chess semantics; all chess truth is delegated to the
vector64 engine. See `doc/BACKEND_ARCHITECTURE.md` and `doc/ENGINE_INTEGRATION.md`.

## Run in Docker (postgres + engine + server)

```bash
docker compose up --build
```

Brings up Postgres, the engine service, and the server (HTTP 8080, WSS 8081). The engine service is a
thin TCP front over the validator line protocol: with no binary it runs a correct dev engine
(python-chess, standard + crazyhouse); to use the real vector64 binary, mount it and set `ENGINE_CMD`
in `docker-compose.yml`.

Smoke-test a live move end-to-end (login → game → WSS → engine → persisted):

```bash
java -cp "build/install/chessoc-server/lib/*" com.chessoc.server.app.SmokeClient
```

### Use the real vector64 binary

The engine binary is native and cannot run inside the Linux image, so it runs on the host behind the
engine bridge; the containerized server reaches it over `host.docker.internal`:

```bash
# 1. host: expose the binary over TCP (one engine process per connection)
java -cp "build/install/chessoc-server/lib/*" \
  com.chessoc.server.app.EngineBridge 9200 /path/to/ChessEngine-all

# 2. stack: point the server at the bridge
docker compose -f docker-compose.yml -f docker-compose.binary.yml up -d server
```

Fully containerizing the real engine instead needs a Linux build of it, run in the `engine` service
with `ENGINE_CMD` (which proxies the same line protocol to the binary).

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

Runnable end-to-end in Docker for standard/crazyhouse single-board games: auth → ticket → WSS → the
engine validates/applies the move → Postgres persists the ply. Verified against both the real vector64
binary (`ChessEngine-all`, via the engine bridge) and the python-chess dev engine. Covered by tests
including a full-stack HTTP+WSS integration test and real process/TCP engine tests (the Postgres JDBC
test runs when `CHESSOC_TEST_DB_URL` is set).

Not yet the production integration: the vector64 binary (the engine service proxies to it via
`ENGINE_CMD`; the default dev engine is python-chess), Redis presence/affinity, OAuth/password login
(dev `/login` stand-in), and bughouse's two-board hand coupling. Remaining phases in
`doc/BACKEND_ARCHITECTURE.md §16`.
