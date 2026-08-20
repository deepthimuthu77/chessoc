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
- **Open-games / seeks lobby list** — P2. Discoverable open challenges to join.
- ~~**Resume ongoing games**~~ — shipped. `/games/active` + "Continue playing" on home.
- **Challenge notifications** — P2. Notify the challenged player (needs the notification channel below).

## In-game UX
- ~~**Board flip**~~ — shipped (orients to the player's colour, `autoFlip` setting).
- ~~**Check indicator**~~ — shipped (`inCheck` in the snapshot, king highlighted).
- **SAN move list** — P2. Server sends UCI; convert to SAN (engine or a notation helper).
- **Premoves** — P2. Queue a move for the opponent's turn.
- ~~**Move sounds**~~ — shipped (`sounds` setting).
- **Takeback offer** — P2. New capability + opponent accept, engine re-derives the position.
- **Abort** (before each side's first move, no rating change) — P2. Taxonomy exists; wire the flow.
- **Draw claims** (threefold / fifty-move) — P2. Engine detects; issue a claim capability + UI.
- **Accessibility** — P1. Keyboard move/selection, screen-reader announcements (live region),
  reduced-motion path (§13).

## Post-game
- ~~**Move replay / navigator**~~ — shipped. `/game/moves` + `/review/[id]` step-through.
- **Position analysis** — P2. Eval graph + best move via the search tier; async per §15.
- **PGN export / import** — P2. Moves are stored; add PGN (SAN) serialisation.

## Social
- **Friend requests / accept** — P2. Currently a direct mutual add; add request → accept/decline.
- **Follow players** — P3.
- **Reliable presence** — P2. In-memory online set has no offline detection; add heartbeat/TTL and
  Redis-backed cross-node presence (§10).
- **Spectator discovery** — *partial*. Live-games list (`/games/live` + `/watch`) ships; still to do:
  spectator count, a separate spectator chat channel, and the rated ~15s broadcast delay (§14).

## Profile & settings
- **Rating history graph** — P2. Persist per-game rating deltas; chart them.
- ~~**Per-pool rating breakdown**~~ — shipped. `/profile` returns all variant×speed pools.
- ~~**Client settings**~~ — shipped (sounds, auto-flip, auto-queen, confirm-resign; local). Board
  theme / piece set / premove / reduced-motion still open.

## Variants UI
- **Crazyhouse hand + drops** — P2. Engine + FEN reserve are supported; the board has no reserve/hand
  UI or drop input.
- **Bughouse** — P3. Two-board layout + partner; the Match aggregate supports 1..N boards.

## Ops & platform
- **Observability** — P3. Prometheus/Grafana + OpenTelemetry around the engine hop (§16).
- **JDBC connection pool** — P2. Repositories open a connection per call; add HikariCP.
- **Rate limiting on the action path** — P2. `RateLimiter` exists but is not enforced on intents (§11).
- **Anti-cheat data model + moderation** — P3. `move_events` metadata, report-player, block/mute (§11, §15).
- **TLS** — P2. Dev runs `ws`/`http`; terminate TLS at the edge (`wss`/`https`).
- **Match sharding / gateway registry** — P3. Single node today; add the match-aware gateway (§12.4).
