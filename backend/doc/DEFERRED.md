# Deferred features / roadmap

What is **not** built yet, after the game core + product shell (auth, matchmaking/create, ratings,
leaderboard, friends, profile, chat, rematch, captured pieces, history) landed. Grouped by area;
each line notes what it needs and whether the backend already has a foothold. See
[FRONTEND_INTEGRATION.md](FRONTEND_INTEGRATION.md) for the current API surface.

Priority: **P1** expected next, **P2** valuable, **P3** advanced/infra.

## Already shipped (for context)
Accounts (email+password), durable session, home + game create/join, **play vs the engine (choose your
colour, engine plays the other side)**, **quick-match queue** (variant×speed pairing, `/seek`), Glicko-2
ratings on rated end, global + friends leaderboards, friends + in-memory presence, profile stats +
per-pool ratings, in-game chat, rematch (colours swapped), engine-sourced captured pieces + material,
game-history list + **move replay**, **board flip**, **check indicator**, **move sounds**, **client
settings**, **account settings** (change password, delete), **spectator discovery** (live-games list),
and a chess.com-style board (per-piece legal dots, capture rings, last-move highlight). Runs in Docker
(Postgres + engine + server + a production web build), against the real vector64 binary via the engine
bridge; the in-container dev engine plays a weak 2-ply material AI.

## Auth & account
- **OAuth (Google / GitHub)** — P1. Login issues the same session token; add the provider handshake.
- **Email verification** — P1. Blocks rated play / friend spam until verified.
- **Password reset** — P1. Token-email flow.
- **Account settings** — *partial*. Change password + delete account ship; change email + connected
  providers still open.
- **Avatar upload** — P2. Storage + `accounts.avatar_url`; show in nav/profile/panels.
- **Guest → account upgrade** — P2. Guests can play casual; let them claim results into a new account.

## Matchmaking & lobby
- ~~**Automated quick-match queue**~~ — shipped. `/seek` + `/seek/status` + `/seek/cancel`, pairing →
  game via `SeekCoordinator`, "Quick match" home UI polls until paired.
- ~~**Open-games lobby list**~~ — shipped. `/games/open` lists created games with an empty seat;
  "Open challenges" on home joins one (seated as Black). (Auto-refreshing seek list still open.)
- ~~**Resume ongoing games**~~ — shipped. `/games/active` + "Continue playing" on home.
- **Challenge notifications** — P2. Notify the challenged player (needs the notification channel below).

## In-game UX
- ~~**Board flip**~~ — shipped (orients to the player's colour, `autoFlip` setting).
- ~~**Check indicator**~~ — shipped (`inCheck` in the snapshot, king highlighted).
- **SAN move list** — P2. Server sends UCI; convert to SAN (engine or a notation helper).
- **Premoves** — P2. Queue a move for the opponent's turn.
- ~~**Move sounds**~~ — shipped (`sounds` setting).
- **Takeback offer** — P2. New capability + opponent accept, engine re-derives the position.
- ~~**Abort**~~ — shipped. `ABORT` capability offered until both sides have moved; ends the game
  ABORTED with no rating change and no history row (`Abort` button replaces `Resign` pre-move).
- **Draw claims** (threefold / fifty-move) — P2. Engine detects; issue a claim capability + UI.
- **Accessibility** — *partial*. Board squares are keyboard-operable (focus + Enter/Space to select
  then move) with descriptive `aria-label`s; the room has an `aria-live` move/result announcer; the
  game-over dialog is a labelled `alertdialog`; reduced-motion honours the OS + a setting. A full
  audit (focus order, high-contrast, all dialogs) is still open.

## Post-game
- ~~**Move replay / navigator**~~ — shipped. `/game/moves` + `/review/[id]` step-through.
- ~~**Position analysis**~~ — shipped (on-demand). `/analyze?fen=&variant=` runs the search tier;
  the review page analyses the viewed position (best move + white-relative eval). The dev engine now
  reports a real material score. A full per-ply eval graph is still open.
- **PGN export / import** — P2. Moves are stored; add PGN (SAN) serialisation.

## Social
- ~~**Friend requests / accept**~~ — shipped. Directed request → accept/decline (reverse request
  auto-accepts); `/friends/add` now requests, `/friends/requests|accept|decline`, requests UI.
- **Follow players** — P3.
- **Reliable presence** — *partial*. Single-node last-seen TTL (90s) + client `/heartbeat` every 30s
  now expires stale sessions; Redis-backed cross-node presence (§10) is still the production upgrade.
- **Spectator discovery** — *partial*. Live-games list (`/games/live` + `/watch`) with a live spectator
  count ships; still to do: a separate spectator chat channel and the rated ~15s broadcast delay (§14).

## Profile & settings
- ~~**Rating history graph**~~ — shipped. `rating_history` table, sample per rated game end,
  `/ratings/history`, inline SVG chart on the profile with a per-pool selector.
- ~~**Per-pool rating breakdown**~~ — shipped. `/profile` returns all variant×speed pools.
- ~~**Client settings**~~ — shipped (sounds, auto-flip, auto-queen, confirm-resign, reduced-motion;
  local). Board theme / piece set / premove still open.

## Variants UI
- ~~**Crazyhouse hand + drops**~~ — shipped. Reserve parsed from the FEN pocket into a `Hand`; clicking
  a reserve piece highlights its legal drop squares (from `P@e4` capabilities) and drops on click.
- **Bughouse** — P3. Two-board layout + partner; the Match aggregate supports 1..N boards.

## Ops & platform
- **Observability** — P3. Prometheus/Grafana + OpenTelemetry around the engine hop (§16).
- ~~**JDBC connection pool**~~ — shipped. `Db` is backed by HikariCP; all Postgres repositories
  (incl. match plies) share the pool, closed on shutdown.
- ~~**Rate limiting on the action path**~~ — shipped. `LiveConnection` gates intents through a
  per-session `RateLimiter` (25 ms min interval); over-rate intents get a REJECTED `rate_limited`.
- **Anti-cheat data model + moderation** — P3. `move_events` metadata, report-player, block/mute (§11, §15).
- **TLS** — P2. Dev runs `ws`/`http`; terminate TLS at the edge (`wss`/`https`).
- **Match sharding / gateway registry** — P3. Single node today; add the match-aware gateway (§12.4).
