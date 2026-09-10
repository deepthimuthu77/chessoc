# vector64 Engine Integration Requirements

What the **vector64** engine (`cpp/chess`, embedded NNUE) must expose so the Java Game Server can
treat it as the single source of chess truth. Companion to
[BACKEND_ARCHITECTURE.md](BACKEND_ARCHITECTURE.md).

The guiding contract: **the Java server computes no chess semantics.** The engine owns legality,
move application, variant rules, terminal detection, search, and evaluation. Anything the server
would otherwise need to "know" about chess must be answerable by an engine call.

---

## 1. Two capability tiers (same binary, different modes)

The engine is used in two very different ways; keep them separable so cheap calls never queue
behind deep searches.

| Tier | Purpose | Cost | Deployment |
|------|---------|------|------------|
| **Validator** | legality, move application, legal-move generation, terminal detection | microseconds; **no search, no NNUE load required** | always-on resident pool, many lean workers |
| **Search** | best move, evaluation, principal variation, analysis | heavy; NNUE + search budget | queued, autoscaled pool |

Requirement: a validator request **must not** trigger a search and **must not** require the NNUE
net to be loaded. Ideally a startup flag / build mode produces a lean validator process.

---

## 2. Validator API (the critical new surface)

All calls are **stateless** — the full position is supplied each time (see §4). This makes workers
interchangeable and poolable.

### 2.1 Validate + apply a move
Input: `position, sideToMove, handState, variant, move (UCI, incl. promotion & drop syntax)`,
plus `positionHistory` (or a repetition hint) for threefold.
Output:
- `legal: bool`
- if legal: `resultingPosition` (FEN + updated hand), and
- `terminal`: one of `none | checkmate | stalemate | draw_fifty_move | draw_threefold |
  draw_insufficient_material | <variant-specific>`, plus `inCheck: bool`, `sideToMove` after the move.
- for a **capture**, `capturedDropType` — the piece type that enters a hand, **after promotion
  revert** (a captured promoted queen re-enters as a pawn), or `null` for a non-capture. In
  crazyhouse the engine appends it to the capturer's own reserve; in **bughouse the engine adds
  nothing** and the server routes this value to the partner board's reserve (§3).
- if illegal: a **reason code** distinct from malformed-input and internal-error (§5).

This is the call Match State makes on every move. The server commits exactly `resultingPosition`.

### 2.2 Generate legal moves
Input: `position, sideToMove, handState, variant`.
Output: the **exact set of legal moves in UCI** (including promotion variants as distinct UCIs and
legal drops for drop variants).

The frontend contract issues one capability per exact UCI (promotion = separate capabilities), so
the server needs the full enumerated legal-move set, not a boolean. This call powers move
highlighting, premove validation, and capability generation.

### 2.3 Terminal / status query (optional if folded into 2.1)
Given a position, report check / checkmate / stalemate / draw conditions without a move — useful for
resync and for validating imported positions.

### 2.4 Can-side-mate query (for timeout-vs-insufficient-material)
Given a position and a side, report whether that side has **sufficient mating material**. The server
uses this to adjudicate a flag: if the opponent flags but the flagging side's opponent cannot mate,
the result is a **draw**, not a win. Uses the FIDE 6.9 "mate possible by some legal continuation"
sense. The engine implements this for **drop variants too** (anything in a reserve suffices), so it is
valid for crazyhouse/bughouse flag adjudication as well.

---

## 3. Variant support

- **standard** — required.
- **crazyhouse** — drops from own hand; hand **encoded in the position** (`[...]` FEN reserve). The
  engine appends a capture to the capturer's own reserve automatically.
- **bughouse** — the engine derives **nothing** from its own captures (a captured piece goes to the
  **partner's** reserve on the *other* board, which this engine instance cannot see). The engine
  reports `capturedDropType`; **the server routes it to the partner board's reserve.** The reserve
  reaches the engine **solely through the `[...]` field of the supplied FEN** — there is no separate
  hand-injection call. **The server is the sole mutator of bughouse reserves.**

**Bughouse draw rules — DECIDED: real bughouse.** The server sends `setoption name DrawRules value
variant` for bughouse boards, so `terminal` never reports `draw_threefold` or `draw_fifty_move`
(the partner board keeps state moving; real bughouse has no such draws). **Stalemate and insufficient
material are still detected.** *(Status: standard works today; crazyhouse/bughouse drops implemented
on branch `STK/four-player-game-support-112931`.)*

---

## 4. Statelessness & position representation

- Every request **carries the full position** — no reliance on hidden internal game history except
  what is passed in. A worker must answer any request cold.
- For threefold repetition the server passes history as the **move-list form**
  (`position fen <start> moves <uci> ...`); the engine replays and detects repetition. **CONFIRMED:**
  move-list form is what we use (our canonical state is FEN + move history). No position-list form
  needed. *(Server-side note: for bughouse, per-board move replay cannot reconstruct externally-fed
  reserves, so the server persists the resulting per-ply FEN — reserve included — for drop variants.)*
- **Canonical wire format — CONFIRMED:** `<board>[<reserve>] <stm> <castling> <ep> <halfmove>
  <fullmove>`. `<board>` is standard FEN with a trailing `~` on any promoted piece (**this marker must
  survive persistence** or promotion-revert breaks on reload). `[<reserve>]` present only in drop
  variants (uppercase = White, lowercase = Black, `[]` when empty). A standard-variant worker
  **rejects** a FEN carrying a bracket. Shared verbatim by engine, server, and persistence.
- Determinism: **validator calls are fully deterministic** and this is what our reproducibility relies
  on. **Search is only deterministic at `Threads 1`; we do NOT pin it** — we record the AI's chosen
  move in history and reconstruct via the deterministic validator, so AI runs full-strength multithread.

---

## 5. Error taxonomy

Distinguish, in responses, at minimum:
- **illegal move** — well-formed request, move not legal in this position (with a reason code);
- **malformed request** — unparseable position/move/hand/variant;
- **internal error** — engine fault (triggers Adapter restart / worker eviction).

The server treats these very differently (reject-and-log vs terminate-pipeline vs restart-worker),
so they must not collapse into one failure signal.

**Adapter handling of hard faults:** `internal_error` is reserved in the protocol but the engine
cannot emit it for a real fault (a crashing/hung process can't send JSON). The Adapter therefore
treats **no reply within the timeout** and **non-zero process exit** as the internal-error signal →
evict and respawn the worker.

---

## 6. Search API (HvAI + analysis)

- **Best move:** `position, variant, budget {movetime | depth | nodes}` → `bestMove (UCI)`,
  optional `ponder`.
- **Evaluation:** centipawn score or mate-in-N, side-relative and sign-defined.
- **Principal variation** and optional **MultiPV** for analysis mode.
- Honor the supplied time/nodes budget so the server can bound AI think-time against clock rules.
- No opening book / networking / persistence concerns inside the engine — the server owns those.
- **Requested:** a **JSON search reply** (`{"bestMove","score":{cp|mate},"pv":[...],"depth"}`) so the
  Adapter parses one structured shape instead of scraping `info` lines.
- **Drop-variant strength — DECIDED:** crazyhouse-vs-AI ships in v1 as **legal-but-weak** (drops are
  not generated in quiescence, SEE is drop-unaware, and **NNUE is off for variants** — classical eval
  only), improved later. Bughouse is **HvH-only** (no AI). The AI may hang forced mates in crazyhouse;
  accepted for v1.

---

## 7. Process, concurrency & lifecycle

- **Poolable workers:** the Engine Adapter spawns/monitors N workers per tier. A worker must support
  fast startup (especially validators), clean shutdown, and be safe to kill and respawn.
- **Concurrency — CONFIRMED:** **one request at a time per process** (the engine has process-global
  mutable state — TT, history — so it cannot serve concurrent positions). The Adapter uses **many
  single-request worker processes** and never multiplexes onto one worker.
- **Health / readiness:** a cheap `isready`/health probe so the Adapter can detect a hung or dead
  worker and evict it.
- **Timeouts:** every call is time-boundable by the Adapter; a worker exceeding budget is killed and
  the request retried elsewhere.
- **Protocol — CONFIRMED (line-based stdin/stdout; Adapter owns gRPC):** setup commands are
  UCI-shaped text (`setoption`, `position`, `isready`); **validator queries return JSON** (`apply`,
  `legalmoves`, `status`, `canmate`, `getfen`); search returns UCI `info`/`bestmove` text (JSON search
  reply requested, §6). **Requested:** a combined **single-line validator request** form so each call
  is self-contained (current form is two lines: `position …` then the query). Two-line is acceptable if
  the single-line form is costly.
- **Binaries:** `ChessEngine-validator` (lean validator tier — refuses `go`/`bench`),
  `ChessEngine-variants` (search; `--validator` runs it in validator mode), and the standard
  tournament binaries `ChessEngine`/`ChessEngine-nnue` (**no variant support — never use for drop
  variants**). Variant code is compile-time gated so it cannot regress standard strength.

---

## 8. Explicit non-goals (keep the engine isolated)

The engine must remain unaware of: networking, sockets, Redis, players, accounts, client IDs,
rooms, matchmaking, clocks, chat, authentication, bans. It receives positions and returns chess
answers. All multiplayer concerns stay in the Java server.

---

## 9. Summary checklist (engine reports 12/12 implemented on branch `STK/four-player-game-support-112931`)

- [x] Lean **validator mode** (`ChessEngine-validator`, refuses `go`/`bench`).
- [x] `apply` → legal + resulting position + terminal + `capturedDropType` (promotion-reverted).
- [x] `legalmoves` → exact UCI set (promotions + drops enumerated).
- [x] `canmate` → sufficient-material check; also valid for drop variants.
- [x] Bughouse draw detection — **real bughouse**: server sends `DrawRules=variant` (no threefold/fifty; stalemate + insufficient still reported).
- [x] **crazyhouse** drops with in-position `[...]` hand encoding.
- [x] **bughouse** drops — **server owns all hand movement**; reserve arrives via the FEN `[...]` field; engine derives nothing from its own captures.
- [x] Stateless; threefold via move-list history; validator deterministic (search only at `Threads 1`, which we do not pin).
- [x] Error taxonomy (`illegal_move` / `malformed_request` / `internal_error`); hard faults handled by Adapter as timeout/non-zero-exit.
- [x] Search API with movetime/depth/nodes, eval (cp/mate), PV/MultiPV — crazyhouse AI legal-but-weak for v1.
- [x] Poolable workers: fast start, `isready`, killable, timeout-safe; **one request per process**.
- [x] Canonical wire format confirmed: `<board>[<reserve>] …` with `~` promoted marker.

## 10. Confirmed integration decisions (server ↔ engine)

1. **Wire format confirmed** (§4). Server persists the FEN verbatim, including `~` and `[...]`; a
   store→reload round-trip test guards the `~` marker.
2. **Bughouse draws = real bughouse** — server sends `DrawRules=variant`; no threefold/fifty-move.
3. **Threefold input = move-list form** — no position-list form needed. Drop variants additionally
   persist the resulting per-ply FEN (reserve included) so bughouse state is reconstructable.
4. **Search determinism not required** — AI runs full-strength multithread; the chosen move is
   recorded and replayed through the deterministic validator.
5. **Crazyhouse-vs-AI ships in v1 legal-but-weak**; bughouse is HvH-only; NNUE off for variants.
6. **Protocol split accepted** — requested additions: JSON search reply, and a single-line validator
   request form. Adapter owns the gRPC boundary and hard-fault detection.
