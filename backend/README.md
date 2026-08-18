# chessoc-server

Java Game Server for chessoc — authoritative orchestration (sessions, clocks, turns, matchmaking,
persistence, broadcast). It computes **no** chess semantics; all chess truth is delegated to the
vector64 engine. See `doc/BACKEND_ARCHITECTURE.md` and `doc/ENGINE_INTEGRATION.md`.

## Build & run

```bash
./gradlew build
./gradlew run
```

Java 17, Gradle wrapper pinned. `run` starts HTTP (8080) and WSS (8081). With no engine configured
the auth + transport layers come up but chess calls fail by design — point it at a validator binary:

```bash
CHESSOC_VALIDATOR_CMD="/path/to/ChessEngine-validator" ./gradlew run
```

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
  Adapter: single-request worker pool, process line-protocol worker with timeout + eviction.
- `match` — server clocks, time-control buckets, drop-variant `Reserve`, `Match` aggregate.
- `match.state` — the authoritative single-writer game: pre-commit gate, capability issuance from
  engine legal moves, flag timers, terminal/resign/draw/timeout resolution, per-ply persistence,
  broadcast.
- `matchmaking` — Glicko-2 ratings; queue + friend challenges bucketed by variant × speed.
- `session` — HMAC session tokens, single-use tickets, rate limiter, session registry.
- `transport` — `LiveConnection` (wire ↔ match), HTTP auth API, WebSocket live server, app gateway.
- `app` — config, module assembly, entry point.

## Status

Runnable end-to-end for standard/crazyhouse single-board games (auth → ticket → WSS → play), covered
by 72 tests including a full-stack HTTP+WSS integration test and a real-subprocess engine test.

Interfaced with in-memory/dev implementations, not yet the production integration: Postgres
(`MatchRepository`), Redis presence/affinity, the vector64 binary (`EngineAdapter`), OAuth/password
login, and bughouse's two-board hand coupling. Remaining phases in `doc/BACKEND_ARCHITECTURE.md §16`.
