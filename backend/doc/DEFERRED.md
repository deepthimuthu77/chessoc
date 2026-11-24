# Deferred features / roadmap

What is **not** built yet, updated to reflect the implementation as of November 2026.
Grouped by area; each line notes what it needs and whether the backend already has a foothold.
See [FRONTEND_INTEGRATION.md](FRONTEND_INTEGRATION.md) for the active API surface and
[FEATURE_ROADMAP.md](../../doc/FEATURE_ROADMAP.md) for prioritized next steps.

Priority: **P1** expected next, **P2** valuable, **P3** advanced/infra.

## Already shipped (for context)
Accounts (email+password), durable session tokens, guest accounts and guest-to-registered upgrade,
game creation/join, **play vs the engine (choose color or play both human and AI seats)**,
**quick-match queue** (variant×speed pairing, `/seek`), Glicko-2 ratings on rated end,
global + friends leaderboards, friends + online presence, profile stats + per-pool ratings,
in-game chat with **profanity masking, burst rate-limiting, and emoji support**, rematch with swapped colors,
engine-sourced captured pieces + material score, game-history list + **move replay & position analysis**,
**board flip**, **check indicator**, **move sounds**, **premoves**, **game abort**, **client settings**,
**account settings** (change password, delete account), **spectator discovery** (`/games/live` + `/watch`
with 4-match pinning and SSE stream), a chess.com-style board (per-piece legal dots, capture rings,
last-move highlight), **Crazyhouse with reserve hands and drops**, and **two-board coupled Bughouse**
(four seats, partner piece transfer routing, bughouse draw rules, and mixed human/AI seats).
Runs in Docker (Postgres + engine + server + production Next.js build + Caddy), with edge TLS and
public HTTPS sharing via Cloudflare Quick Tunnels.

## High-priority next additions (P1)
- **Bughouse pregame lobby** — P1. Explicit Ready buttons, host start, visible human/AI assignments,
  and safe seat changes before clocks run.
- **Separate chat channels** — P1. Split room chat into player, partner-only team chat, and spectator
  channels; spectators should not coach active players.
- **Public-demo resource controls** — P1. Request size bounds, per-IP/user budgets, analysis rate
  limiting, and structured busy responses.
- **Room privacy controls** — P1. Public, unlisted, and invite-only room visibility enforced on join
  and discovery.

## Auth & account
- **OAuth (Google / GitHub)** — P2. Login issues the same session token; add provider handshake.
- **Email verification** — P2. Blocks rated play / friend spam until verified.
- **Password reset** — P2. Token-email flow.
- **Account settings** — *partial*. Change password + delete account ship; change email + connected
  providers still open.
- **Avatar upload** — P2. Storage + `accounts.avatar_url`; show in nav/profile/panels.
- ~~**Guest → account upgrade**~~ — shipped. "Continue as guest" persists a guest session; `/auth/upgrade`
  registers an account and reassigns the guest's games + ratings/history to it.

## Matchmaking & lobby
- ~~**Automated quick-match queue**~~ — shipped. `/seek` + `/seek/status` + `/seek/cancel`, pairing →
  game via `SeekCoordinator`, "Quick match" home UI polls until paired.
- ~~**Open-games lobby list**~~ — shipped. `/games/open` lists created games with an empty seat;
  "Open challenges" on home joins one (seated as Black).
- ~~**Resume ongoing games**~~ — shipped. `/games/active` + "Continue playing" on home.
- **Challenge notifications** — P2. Direct challenge invitations to online friends.
- **Persistent bughouse teams** — P2. Rematch four players together and swap colors/teams.

## In-game UX
- ~~**Board flip**~~ — shipped (orients to player's color, `autoFlip` setting).
- ~~**Check indicator**~~ — shipped (`inCheck` in snapshot, king highlighted).
- **SAN move notation** — P2. Server sends UCI; display algebraic notation (SAN) in scoresheet.
- ~~**Premoves**~~ — shipped. Queue a move on opponent's turn; auto-fires on turn if legal.
- ~~**Move sounds**~~ — shipped (`sounds` setting).
- **Takeback offer** — P2. Takeback capability + opponent accept, engine re-derives position.
- ~~**Abort**~~ — shipped. `ABORT` capability offered until both sides have moved.
- **Draw claims** (threefold / fifty-move) — P2. Engine detects; issue claim capability.
- **Bughouse partner requests** — P2. One-click "Need knight", "Need pawn", "Hold", or "Go" routed
  directly to teammate.
- **Accessibility** — *partial*. Keyboard navigation, aria-live announcements, alert dialogs,
  and reduced-motion support. Full WCAG 2.1 AAA audit still open.

## Post-game & Review
- ~~**Move replay / navigator**~~ — shipped. `/game/moves` + `/review/[id]` step-through.
- ~~**Position analysis**~~ — shipped. `/analyze?fen=&variant=` queries search tier; best move + eval.
- **PGN export / import** — P2. Standard, crazyhouse, and paired bughouse PGN export with clocks
  and drop events.
- **Full evaluation graph** — P2. Per-ply evaluation graph and mistake blunder detection.

## Social & Community
- ~~**Friend requests / accept**~~ — shipped. `/friends/add`, `/friends/requests|accept|decline`.
- **Follow players** — P3.
- **Reliable cross-node presence** — *partial*. Single-node TTL + client `/heartbeat` active;
  Redis-backed cross-node presence deferred until multi-node deployment.
- **Spectator delay for rated games** — P2. ~15s broadcast delay to prevent outside engine assistance.

## Variants
- ~~**Crazyhouse hand + drops**~~ — shipped. Reserve parsed from FEN bracket into `Hand`; legal drops
  highlighted and committed via `P@e4` capabilities.
- ~~**Bughouse**~~ — shipped. Two coupled boards (`A` and `B`), four seats (`aw`, `ab`, `bw`, `bb`),
  partner reserves with automatic capture transfer, bughouse draw rules (no threefold or 50-move),
  mixed human and AI seats, and synchronized team game termination.
- **Coordinated bughouse AI** — P3. Search engine awareness of partner-board pieces and requests.

## Ops & platform
- **Observability** — P2. Prometheus/Grafana metrics, queue depth, engine worker latency.
- **Database backups & restore tooling** — P2. Documented and tested automated pg_dump/restore scripts.
- ~~**JDBC connection pool**~~ — shipped. `Db` backed by HikariCP across all Postgres repositories.
- ~~**Rate limiting on action path**~~ — shipped. `LiveConnection` gates intents with a per-session limiter.
- ~~**TLS**~~ — shipped. Caddy terminates TLS with on-demand local LAN certificates and Cloudflare Quick Tunnels.
- **Multi-node match sharding** — P3. Single deployable server today; scale to cluster when required.
