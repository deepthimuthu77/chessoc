# chessoc-server

Java Game Server for chessoc — authoritative orchestration (sessions, clocks, turns, matchmaking,
persistence, broadcast). It computes **no** chess semantics; all chess truth is delegated to the
vector64 engine. See `doc/BACKEND_ARCHITECTURE.md` and `doc/ENGINE_INTEGRATION.md`.

## Build

```bash
./gradlew build
./gradlew test
```

Java 17, Gradle wrapper pinned. No other setup.

## Layout

Modular monolith; layer boundaries are interfaces (contract-first) so any module can be promoted to
its own service later without rewriting callers.

- `domain` — value types: `Side`, `Variant`, `PieceType`, `Terminal`, `Uci`, `Fen` (canonical wire
  format `<board>[<reserve>] <stm> <castling> <ep> <halfmove> <fullmove>`, stored verbatim so `~`
  and `[...]` survive persistence).
- `protocol` — versioned envelope + message catalog (§8), codec with major-version refusal, domain
  wire binding.
- `capability` — single-use, version-bound action authorisations (§7).
- `engine` — Engine Adapter contract: `ValidatorClient` / `SearchClient` / `EngineAdapter`, DTOs,
  error taxonomy, and the line-based validator + JSON search wire codecs.
- `match` — server-authoritative `Clock`, `TimeControl` / `SpeedBucket`, drop-variant `Reserve`,
  `Board` / `Match` aggregate.
- `matchmaking` — Glicko-2 (`Rating`, `Glicko2`), per variant × speed.

## Status

Phase 1–2 core: protocol/capability/engine contracts and the pure, tested pieces of match and
rating. Not yet wired: WSS/HTTPS transport, the Match actor + pre-commit gate, engine process pool,
Postgres/Redis, auth. Tracked as the remaining phases in `doc/BACKEND_ARCHITECTURE.md §16`.
