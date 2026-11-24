# Chessoc Backend / Platform Architecture

Status: **v1 design — core and v1 scope decided.**
Companion documents: [ARCHITECTURE.md](ARCHITECTURE.md) (frontend contract), [ENGINE_INTEGRATION.md](ENGINE_INTEGRATION.md) (what the engine must expose).

---

## 1. Vision

A strictly layered, **server-authoritative** multiplayer chess platform. Every component has a
single responsibility and communicates only through well-defined interfaces.

The architecture emphasizes: separation of concerns, zero-trust networking, engine isolation,
passive clients, modularity, recoverability, and horizontal scalability.

Three independently replaceable systems:

```
Frontend clients (Next.js web  +  Python/pygame desktop)
        │
        ▼
Java Game Server        (authoritative; modular monolith)
        │
        ▼
vector64 Chess Engine   (all chess truth; pooled workers)
```

---

## 2. Core Philosophy

- **The server is the only authority.** Clients render state and send intents; they make no game decisions.
- **The Java server computes no chess semantics.** It orchestrates (sessions, clocks, turns,
  matchmaking, persistence, broadcast) and delegates **100% of chess truth** — legality, move
  application, variant rules, search, evaluation — to the engine. There is no second rules
  implementation in Java to drift out of sync.
- **The engine knows only chess.** It has no concept of networking, players, rooms, clocks,
  accounts, matchmaking, or bans.
- Each component is independently replaceable behind its interface.

---

## 3. Deployment Topology — Dedicated-Server Modular Monolith

Industry-standard shape for real-time game backends and the pattern lichess converged on:
one authoritative game-server process with layered **in-process modules**, and exactly **one
real network boundary** — the engine pool.

```
Web + Python clients
   │   wss  (live game protocol)     +     https (auth, matchmaking, lobby, history)
   ▼
┌───────────────────────────── Java Game Server (single deployable) ─────────────────────────────┐
│  Session Layer      WS/HTTP endpoints · token validation · rate limiting · packet gating        │
│  Matchmaking        queue · friend challenges · pairing · rating                                │
│  Match State        AUTHORITATIVE · sole DB owner · clocks · pre-commit gate · sole engine caller│
│       └─ Match aggregate = 1..N boards + hands + coupled clocks/win  (bughouse fits here)        │
│  Engine Adapter     owns engine process lifecycle · translates to/from engine protocol          │
└─────────────────────────────────────────┬───────────────────────────────────────────────────────┘
                                           │  Line Protocol (TCP / Stdin-Stdout) ← engine boundary
                                           ▼
┌──────────────────── vector64 Engine Pool ────────────────────┐
│  Validator tier   legality / move-gen / move application      │   cheap · always-on · many workers
│  Search tier      NNUE search · eval · analysis (HvAI / bots) │   heavy · queued · autoscaled
└───────────────────────────────────────────────────────────────┘

Redis     → presence · live-session cache · reconnection state        (never authoritative)
Postgres  → owned solely by Match State                               (single source of persistence)
```

**Why in-process modules, not RPC between every layer:** the four layers are always called in
sequence and tightly coupled; splitting them into separate processes would be a *distributed
monolith* — paying serialization + distributed-failure + tracing cost for zero independence
benefit. The layer boundaries are enforced as **interfaces** (contract-first service definitions
used in-process), so any module can be promoted to its own service later if a profiler demands it,
without rewriting callers.

**Why the engine is the one carved-out service:** it is CPU-heavy, crash-prone, needs an
independent scaling axis, and benefits from process isolation. It communicates over a lean
line-based protocol (TCP socket via `TcpEngineWorker` or spawned process stdin/stdout via
`ProcessEngineWorker`), keeping the server free of heavy C++ dependencies.

---

## 4. Module Responsibilities

### 4.1 Session Layer  (was "Client Handler")
- Terminates `wss` (live protocol) and `https` (request/response) — see §5.
- Validates the session token on every connection and request; rejects unauthenticated traffic.
- Enforces rate limits / IP throttling and v1 anti-cheat (§11).
- **Never** reads the database directly; reaches game state only through Match State.

### 4.2 Matchmaking
- Queue with time-control + variant buckets; pairing.
- **Ratings: Glicko-2, tracked separately per (variant × speed bucket)** — e.g. standard-blitz,
  crazyhouse-bullet are independent ratings with their own rating deviation. **Bughouse is unrated in
  v1** (per-individual rating of a 2v2 team game is deferred rather than approximated).
- **Friend challenges / private rooms** (direct pairing, invite code or friend target,
  accept/decline) — first-class in v1 because variants (bughouse/crazyhouse) are social.
- Presence (online / in-game) feeding the friends list.

### 4.3 Match State  (was "Game State Handler" + "Sanity Layer")
- **The single source of truth** for live and persisted game state.
- **Sole owner of Postgres.** No other module touches the DB. Row-Level Security scopes rows by
  match/account *inside* its own queries — RLS is tenant isolation, not a substitute for the module
  boundary.
- Owns official **clocks** and turn ordering.
- **Pre-commit gate (the former "Sanity Layer," relocated here):** before committing any move it
  calls the engine validator, checks the response is consistent with current authoritative state
  (correct turn, matching version, monotonic move sequence — guards against stale/race requests),
  and only then commits and broadcasts. On any inconsistency the pipeline terminates; nothing is
  persisted or forwarded.
- **Never applies a move itself** — it asks the engine for legality + the resulting position and
  commits exactly what the engine returns (§6).

### 4.4 Engine Adapter
- Owns the engine process lifecycle (spawn, health-check, timeout, restart) for both tiers, using
  **one request per worker process** (the engine has process-global state and cannot be multiplexed).
- Runs the right binary per tier: `ChessEngine-validator` (validator), `ChessEngine-variants`
  (search). The standard tournament binaries have no variant support and are never used for drops.
- Translates Match State requests into the engine protocol and responses back into internal
  representations. **Knows nothing about game rules** — pure protocol translation. The validator
  surface is line-based JSON; search is UCI text (JSON reply requested).
- **Hard-fault handling:** the engine cannot self-report a crash/hang, so the Adapter treats
  **timeout or non-zero exit as `internal_error`** → evict and respawn the worker.
- Is the only module that talks to the engine, using line-protocol JSON over TCP sockets or spawned process pipes.

---

## 5. Transport

Two transports over one TLS stack, **unified for web and Python** so cross-platform play works:

- **`wss` (WebSocket over TLS)** — the live game protocol: intents, snapshots, capabilities,
  clock samples, resync. Persistent per active session. Browsers cannot open raw TCP, and the
  Python client uses a WS library, so WebSocket is the common denominator. *(The earlier bespoke
  TLS-over-TCP protocol is dropped.)*
- **`https`** — stateless request/response: auth, matchmaking, lobby/room listing, history.

A client may hold a `wss` session and issue `https` calls concurrently. The wire contract (envelope,
message catalog, capability model) is defined by the frontend contract in
[ARCHITECTURE.md §7–§8](ARCHITECTURE.md) and is authoritative on the server side.

*Future:* WebTransport/QUIC is a possible latency optimization, not needed for v1.

---

## 6. Chess-Truth Ownership (Decision: single-source vector64)

All chess semantics live in the engine. To apply a move, Match State sends
`position + side-to-move + hand-state + variant + candidate move` to the **validator tier**, which
returns **legal/illegal + the resulting position + terminal status** (check / checkmate / stalemate
/ draw conditions). Match State commits exactly that. Consequences:

- One rules implementation → the server can never believe a move legal that the engine rejects.
- Variant rules (crazyhouse/bughouse drops) are handled by the same code the AI plays with.
- HvH move validation depends on the engine service being up — mitigated by the **always-on
  validator tier** (cheap, no NNUE, no search; horizontally trivial to scale).

Engine requirements to support this are specified in [ENGINE_INTEGRATION.md](ENGINE_INTEGRATION.md).

**Per-mode engine use:**
- **Human vs Human** — validator tier only (legality + move application + terminal detection). No search.
- **Human vs AI** — validator tier for the human's move; search tier for the engine's reply.
- **Analysis** — search tier: best move, eval, principal variation.

**Variant AI scope:** standard-chess AI uses NNUE at full strength. **Crazyhouse-vs-AI ships in v1 as
legal-but-weak** (NNUE is off for variants — classical eval only; the AI may hang forced mates);
strength work is on the roadmap. **Bughouse supports mixed human and AI seating**, with computer
seats operated by server bot movers (`AiMover`) querying the engine search tier per board.

---

## 7. Game & Match State Model

### 7.1 Match aggregate
A **Match** is the top-level unit, not a single board. It contains:
- **1..N boards** (1 for standard/crazyhouse; 2 for bughouse),
- **2 or 4 players**,
- per-board **clocks** (with coupled win/flag rules across boards for bughouse),
- a **hand inventory** per player (captured pieces available to drop) for drop variants.

The Match is the **unit of sharding and session affinity**: all boards and players of a match live
on one server node so cross-board events (a capture on board A feeding board B's hand) are
in-process and instant. This also answers the previously-undesigned routing question — the gateway
pins a match's sessions to its owning node.

### 7.2 Canonical persisted representation
- **FEN (+ hand extension for drop variants) + move history.** Plain FEN already encodes
  side-to-move, castling, en passant, and the halfmove clock (so turn / fifty-move come free); the
  move list covers threefold. For crazyhouse/bughouse the position must additionally carry the
  **pieces-in-hand** inventory. **Format (confirmed): `<board>[<reserve>] <stm> <castling> <ep>
  <halfmove> <fullmove>`** — the crazyhouse `[...]` reserve carries the hand for *both* crazyhouse and
  bughouse (there is no separate hand list; for bughouse Match State populates the bracket). A trailing
  `~` marks promoted pieces and **must survive persistence** or promotion-revert breaks on reload.
- **Drop-variant reconstruction:** per-board `startpos + moves` replay cannot rebuild bughouse reserves
  (they are fed from the partner board), so for drop variants Match State **persists the resulting
  per-ply FEN (reserve included)**, not just the move.
- **Bitboards are never persisted** — the engine reconstructs them per request.
- Every accepted move is **reproducible** from the canonical representation. AI moves are reproducible
  because the **chosen move is recorded** and replayed through the deterministic validator — search
  itself need not be deterministic, so the AI runs full-strength multithread.

---

## 8. Identity, Accounts & Auth (Decision: accounts from day 1)

Real accounts exist from v1 because variants are social (friends, challenges, ratings).

- **Credential model:** login (OAuth via Google/GitHub **and** email+password) → server-issued
  **signed session token**, presented on both the `https` calls and the `wss` upgrade. Identity is
  uniform across web and Python → cross-play is trivial.
- **Web:** HttpOnly/Secure/SameSite session cookie; short-lived single-use ticket to open `wss`
  (per frontend contract §9).
- **Python:** token in a secure-store abstraction (OS keychain / encrypted-file fallback). The
  **hardware fingerprint is Python-only and demoted to an anti-abuse signal** — it is never the
  credential and never the ban key.
- **Bans** target the **account**, not a spoofable fingerprint. Evasion costs a fresh verified
  email; mitigate with email verification + per-IP signup rate limits.

---

## 9. Reconnection & Recovery

Two-tier timer (unchanged, good):
- **Grace period** (~30–60s): connection dropped, game stays live, opponent sees "reconnecting,"
  no forfeit.
- **Forfeit threshold** (~2–3 min): unresumed → auto-forfeit (bughouse: applies per the coupled
  team rules).

On reconnect the client sends `resync`; Match State replies with the full latest committed snapshot
(from its state + Redis cache) and the client replaces its projection wholesale.

- **Both players disconnected simultaneously:** the Match actor **pauses all clocks** for the grace
  window; if neither side resumes by the forfeit threshold, the match is **aborted** (or **drawn** if
  rated). No one loses on time while nobody is present.
- **Spectator disconnect:** trivial — the subscriber is dropped; game state is unaffected.
- **Server-restart / node-failure recovery:** live matches are **rehydrated from Redis + Postgres**
  onto a (possibly new) node; the gateway registry (§12.4) is updated and clients reconnect via their
  existing `resync` path.

---

## 10. Redis Philosophy

Redis is **not authoritative** and **not an inter-module message bus for game state**. It is used
for: live-session cache, reconnection state, and **presence fan-out**. Because match affinity keeps
cross-board bughouse events in-process, the only pub/sub need is **cross-node friend presence** —
an explicit, scoped exception to the "no message bus" rule, not a general channel. Match State
remains the source of truth; Redis is a cache/coordination layer.

---

## 11. Zero Trust, Logging, Anti-Cheat, Security

- **Zero trust:** every request is authenticated, every state transition verified, every packet
  gated. Clients cannot fabricate board state, clocks, turns, or victories.
- **Replay / idempotency:** each session carries **monotonic move sequence numbers**; a resent move
  (e.g. on reconnect) is deduplicated, never double-applied. `intentId` dedup is honored end-to-end.
- **Logging (auditable):** connections, disconnects, moves, state transitions, illegal requests,
  bans, recoveries, account/session events. Every accepted move is reproducible; every state
  transition is logged.
- **Anti-cheat v1 — scope boundary:** automation/bot detection only, via a hard minimum time
  between submitted actions. **Out of scope for v1:** engine-correlation of move quality (human
  consulting an external engine). Documented limitation, not a guarantee.
  - *Tuning note:* the rate limit must not punish legitimate bullet/blitz players who move
    sub-second — threshold tuned empirically.
- Rate limiting and IP throttling enforced at the Session Layer.

---

## 12. Match Concurrency & Bughouse Semantics

### 12.1 Match actor
Each live Match runs as a **single-threaded actor with a mailbox**. All intents from all players
(2 or 4) and all clock/flag events for all boards are processed **sequentially by one writer**. This
guarantees consistent hand transfers and deterministic flag-race resolution, and matches the
affinity model (whole Match on one node). It is the concurrency backbone for standard and bughouse
play alike.

**Cross-platform / multi-machine play is supported by construction:** each player is an independent
authenticated `wss` session; the actor is agnostic to client type or location. Four players on four
different machines — mixed web and Python — playing bughouse works, provided all four sessions are
routed to the Match's owning node (session affinity; see §12.4).

### 12.2 Clocks (server-authoritative)
- N clocks per Match (2 per board). On each committed move, decrement the moving side by
  `serverReceiveTs − turnStartTs`, apply increment/delay per the time control, then (re)schedule a
  **flag timer** for the new active side at `now + remaining`.
- Flags fire from the timer **even with no move submitted** — required because "sitting" (waiting for
  a partner feed) is legitimate bughouse strategy and players intentionally burn clock.
- **Lag compensation:** credit each player's measured round-trip latency, **capped at ~200ms/move**.
- **Disconnect:** the disconnected player's clock keeps running (a player can flag while disconnected).

### 12.3 Win / draw semantics
- **Standard / crazyhouse:** terminal status (mate/stalemate/draws) is engine-reported per §6; the
  server records it.
- **Bughouse (real-bughouse draw rules, per board):**
  - The Match ends the instant **any** of the 4 players is checkmated or flags — that player's team
    loses and **both boards stop**.
  - Simultaneous/near-simultaneous terminals resolve by **earliest authoritative server timestamp**
    (deterministic under single-actor processing).
  - **No threefold or fifty-move draws** (the partner board keeps state moving — real bughouse has
    none); the server sends the engine `DrawRules=variant` for bughouse boards. **Stalemate and
    insufficient material are still detected** per board. A board draw **settles that board**; the
    Match result is then decided by the other board; if both boards draw, the Match is drawn.
  - **Hand transfer:** the engine derives **nothing** from a bughouse capture. It reports
    `capturedDropType` (already promotion-reverted — a captured promoted piece re-enters as a pawn),
    and **Match State is the sole mutator of bughouse reserves**, routing that piece into the partner
    board's reserve (carried in that board's FEN `[...]` field). This is why there is no separate
    hand-injection call.

### 12.4 Routing & session affinity (match-aware gateway, v1)
Built in v1 (not deferred). A **gateway/directory** fronts a pool of game-server nodes:
- **Match registry** (`match_id → node`) held in Redis (coordination, not game state — consistent
  with §10). Matchmaking/friend-challenge creation assigns the new Match to a node (load-based) and
  writes the registry entry.
- **Directory, not proxy:** clients resolve the owning node over `https` (getting node address +
  a short-lived `wss` ticket) and connect **directly** to that node — no per-move proxy hop on the
  live path. Join, reconnect, and all 4 bughouse players resolve to the **same** node via the registry.
- **Failover:** on node loss, affected matches are rehydrated elsewhere (§9), the registry is
  rewritten, and clients reconnect through their normal `resync` path.

## 13. Time Controls & Game-End Flows

### 13.1 Time controls
Model: `{ baseMs, incrementMs, delayType: none|fischer|bronstein, delayMs }`. Matchmaking buckets by
estimated duration `base + 40×inc`: bullet <3m, blitz 3–8m, rapid 10–25m, classical >25m. Lag
compensation as §12.2.

### 13.2 Game-end taxonomy — who decides
| Outcome | Decided by |
|---|---|
| Checkmate, stalemate, insufficient material | Engine (terminal status in `validateAndApply`) |
| Threefold / fifty-move | Engine detects; server issues a draw-**claim** capability |
| Fivefold / 75-move | Engine detects; server **auto-draws** (forced) |
| Resign | Player intent; server ends immediately |
| Draw offer/accept | Intent → pending-draw state → opponent gets `respond_draw` |
| Timeout (flag) | Server flag timer (§12.2) |
| Timeout vs insufficient mating material | Server, using engine "can side X mate?" query → draw |
| Abort | Allowed until each side has made its first move; no rating change |
| Rematch | Both accept; colors swap, same time control |

### 13.3 Flow rules
- **Draw offers:** re-offers are locked until the opponent has moved (anti-spam).
- **Threefold/fifty:** claim-required (server issues the claim capability); forced auto-draw only at
  fivefold / 75-move.
- **Rematch:** bughouse keeps the same teams and swaps colors.
- **Rating changes** apply on decisive/drawn completions, **not** on aborts.

## 14. Spectators & Broadcast

- Each Match holds a **subscriber set** (players + spectators). On snapshot commit the actor fans out
  to all subscribers over `wss`; affinity keeps fan-out in-process.
- Spectators receive **no interactive capabilities** (frontend enforces read-only) and get the full
  snapshot + move history on join. Bughouse spectators see both boards.
- **Rated integrity:** rated games broadcast to spectators on a **~15s delay** (prevents real-time
  engine relay to a player); casual/analysis is instant.
- v1 caps spectators per Match (a few hundred) to bound fan-out; a relay/pub-sub tier for larger
  audiences is deferred.

### 14.1 Chat (v1)
In-game chat ships in v1 with **two separate channels**:
- **Player channel** — the two (or four, in bughouse) participants.
- **Spectator channel** — watchers only.

Spectator chat is **not visible to players during a rated game** (coaching/engine-relay channel), and
inherits the same ~15s rated delay as the board feed. Casual games may relax this. Chat messages are
routed through the Match actor like any other subscriber event.

## 15. Anti-Cheat Data Model (future-proofing)

Principle: **store what cannot be recomputed; defer what can.**
- Evals are recomputable from the stored game (reproducibility invariant) → no per-move evals stored
  now; analysis runs **async later** via the search tier.
- Timing/metadata are ephemeral → captured at commit time or lost forever.

- **`move_events`** (written every commit): `match_id, board_id, ply, uci, side, account_id, seq_no,
  server_recv_ts, client_ts, think_ms, clock_remaining_ms, source_ip, session_id, python_fingerprint?`.
- **`move_analysis`** (later, async, rated games): `match_id, ply, eval_cp, best_move,
  centipawn_loss, depth, computed_at`.
- A **"report player"** action creating a review case (human review in v1).
- **PII retention/access policy** for IPs and fingerprints.

This costs one table now and zero architecture change — the difference between "we can build
move-quality detection later" and "we threw the data away."

## 16. Observability

**Critical property:** the validator tier is on **every** move path (HvH included) and Java has no
fallback rules engine by design → **the validator's availability SLO is effectively the platform's
availability SLO.** Engineer for validator redundancy + fast failover; there is no graceful
degradation for "validators down."

- **Metrics (Prometheus):**
  - *Engine:* validator/search req rate, latency p50/p95/**p99**, error rate by taxonomy, pool size,
    queue depth, worker restarts, worker memory (NNUE), search timeouts.
  - *Match State:* active matches, moves/sec, commit latency, DB latency, flag events.
  - *Session:* active `wss`, connect/disconnect rate, auth failures, rate-limit rejections.
  - *Matchmaking:* queue depth per bucket, time-to-match.
- **SLOs:** validator p99 latency and move-commit p99 (gameplay-feel SLOs, not vanity metrics).
- **Tracing:** OpenTelemetry span around the engine gRPC hop (the one boundary), correlated by
  `match_id + seq_no + intentId`; in-process layers via structured logs with the same correlation IDs.
- **Alerting:** validator p99 breach, worker restart storms, search saturation, auth-failure spikes
  (attack signal), DB pool exhaustion.
- **Stack:** Prometheus + Grafana + OpenTelemetry.

## 17. System Invariants

1. The Java Game Server is the single source of truth.
2. The client never makes game decisions.
3. The Java server computes **no** chess semantics; the engine owns all chess truth.
4. Modules communicate only through their interface contracts — never shared DB, shared memory, or
   any side channel.
5. Only Match State reads or writes persisted game state.
6. No module accesses another module's internal implementation state.
7. Every request passes the gates **on its path**, in order; the engine **validator** is on every
   move path, the engine **search** tier is only on AI/analysis paths.
8. No gate on a request's path may be skipped; a failed gate terminates execution immediately.
9. Between the game server's internal modules, the engine pool is the only network boundary; the
   client-facing gateway and the datastores (Redis/Postgres) are infrastructure, not inter-module channels.
10. Redis is never authoritative and is not a game-state message bus (presence fan-out excepted).
11. Official clocks exist only on the server.
12. Rendering/interaction state originates only from the server (via capabilities).
13. Bitboards never leave the engine.
14. FEN (with `[...]` reserve and `~` promoted marker) plus move history is the canonical persisted
    representation; drop variants additionally persist the per-ply resulting FEN.
15. Every session presents a valid account-scoped session token; the fingerprint is never identity.
16. Every state transition is logged; every accepted move is reproducible.
17. A Match (not a board) is the unit of persistence ownership, sharding, and session affinity.
18. Every component is independently replaceable behind its interface.
19. Live protocol (`wss`) and request/response (`https`) are separate transports, never conflated.
20. Anti-cheat v1 detects automation only — a known, documented limitation.
21. Each Match is a single-writer actor; all its intents and clock/flag events are serialized.
22. The validator tier's availability bounds platform availability; there is no Java-side rules fallback.
23. Rated spectator feeds are delayed (~15s); player feeds are real-time.
