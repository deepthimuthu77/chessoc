# Frontend Integration

How client interfaces connect to the Java Game Server. The client remains a passive presentation layer;
the server is the sole authority for game state, rules, clocks, and capabilities.

## Transport split

- **HTTPS** (`http://<host>:8080/api` or `/api` via Caddy reverse proxy) — auth, account management,
  lobby/seating, seek coordination, spectator feeds, and ticket issuance.
- **WSS** (`ws://<host>:8081/game?ticket=&gameId=` or `/game?ticket=&gameId=` via Caddy) — persistent,
  authenticated live envelope protocol.

TLS is terminated at the edge by Caddy (using on-demand local LAN certificates or public Cloudflare Quick Tunnels).

## HTTP API endpoints

All endpoints accept and answer standard CORS preflight requests (`OPTIONS` → 204) with `Access-Control-Allow-Origin: *`.
Authenticated requests provide an HTTP Bearer header: `Authorization: Bearer <session-token>`.

### Authentication & accounts
| Method + path | Request payload / query | Auth | Response | Purpose |
|---|---|---|---|---|
| `POST /login` | `{ playerId }` | None | `{ token, sessionId, playerId }` | Quick guest session start. |
| `POST /auth/register` | `{ username, email, password }` | None | `{ token, sessionId, playerId, username, email }` | Create registered account. |
| `POST /auth/login` | `{ username, password }` | None | `{ token, sessionId, playerId, username, email }` | Sign in to registered account. |
| `POST /auth/upgrade` | `{ username, email, password }` | Bearer | `{ token, sessionId, playerId, username, email }` | Convert current guest into registered user. |
| `GET /me` | None | Bearer | `{ id, username, email, isGuest }` | Current session user details. |
| `POST /heartbeat` | None | Bearer | `{ ok: true, activeGames: [...] }` | Keepalive and online presence refresh. |
| `POST /account/password` | `{ currentPassword, newPassword }` | Bearer | `{ ok: true }` | Change account password. |
| `POST /account/delete` | `{ password }` | Bearer | `{ ok: true }` | Delete account (blocked if games active). |

### Game creation & matchmaking
| Method + path | Request payload / query | Auth | Response | Purpose |
|---|---|---|---|---|
| `POST /ticket` | `{}` | Bearer | `{ ticket }` | Mint single-use, short-lived WSS ticket. |
| `POST /join` | `{ room, seat? }` | Bearer | `{ gameId, color, room, seat, ... }` | Join room or specific bughouse seat. |
| `POST /play/create` | `{ variant, speed, rated, humanSeats?, timeControl? }` | Bearer | `{ roomId, gameId, ... }` | Create standard, crazyhouse, or bughouse game. |
| `POST /play/rematch` | `{ gameId }` | Bearer | `{ gameId, color }` | Request/accept rematch with swapped colors. |
| `POST /seek` | `{ variant, speed, timeControl }` | Bearer | `{ seekId }` | Queue in matchmaking pool. |
| `GET /seek/status` | None | Bearer | `{ status: "waiting"\|"matched", gameId? }` | Poll matchmaking status. |
| `POST /seek/cancel` | None | Bearer | `{ ok: true }` | Cancel active matchmaking seek. |

### Social, discovery & review
| Method + path | Request payload / query | Auth | Response | Purpose |
|---|---|---|---|---|
| `GET /friends` | None | Bearer | `{ friends: [{ id, username, online, inGame }] }` | List friends and presence. |
| `POST /friends/add` | `{ friendId }` | Bearer | `{ ok: true }` | Send friend request. |
| `GET /friends/requests`| None | Bearer | `{ incoming: [...] }` | Incoming friend requests. |
| `POST /friends/accept` | `{ friendId }` | Bearer | `{ ok: true }` | Accept friend request. |
| `POST /friends/decline`| `{ friendId }` | Bearer | `{ ok: true }` | Decline friend request. |
| `GET /leaderboard` | `?variant=&speed=` | Optional | `{ entries: [{ rank, username, rating, ... }] }` | Pool leaderboard. |
| `GET /leaderboard/friends`| `?variant=&speed=` | Bearer | `{ entries: [...] }` | Friends pool leaderboard. |
| `GET /profile` | `?id=<playerId>` | Optional | `{ user, stats, ratings }` | Profile stats and ratings per pool. |
| `GET /ratings/history` | `?id=&variant=&speed=` | Optional | `{ points: [{ at, rating }] }` | Historical rating progression. |
| `GET /games/mine` | None | Bearer | `{ games: [...] }` | Caller's completed game history. |
| `GET /games/active` | None | Bearer | `{ games: [...] }` | Ongoing games caller is seated in. |
| `GET /games/open` | None | Optional | `{ games: [...] }` | Open challenges awaiting an opponent. |
| `GET /games/live` | None | Optional | `{ games: [...] }` | Live matches with spectator count. |
| `GET /games/live/events`| None | Optional | SSE text/event-stream | Real-time live feed for Watch page. |
| `GET /game/moves` | `?id=<gameId>` | Optional | `{ moves: ["e2e4", ...], ... }` | Replay moves for review page. |
| `GET /analyze` | `?variant=&fen=` | Optional | `{ bestMove, score, pv, depth }` | On-demand engine position evaluation. |

## WSS Live Protocol

Connect to `ws://<host>:8081/game?ticket=<ticket>&gameId=<gameId>`.
All messages are wrapped in versioned envelopes:
`{ "v": [1,0], "type": "<type>", "payload": { ... } }`.
Frames with incompatible major versions are refused.

| Type | Dir | Payload shape | Purpose |
|---|---|---|---|
| `intent` | client→ | `{ intentId, capabilityId, params?, clientTimestamp }` | Execute authorized action. |
| `resync` | client→ | `{ gameId, lastKnownVersion }` | Request full state catch-up. |
| `ping` / `pong` | both | `{}` | Heartbeat keepalive. |
| `snapshot` | →client | `{ snapshot: GameSnapshot, capabilities: Capability[] }` | Authoritative game state update. |
| `resync_ok` | →client | `{ snapshot: GameSnapshot, capabilities: Capability[] }` | Reconnected recovery snapshot. |
| `receipt` | →client | `{ intentId, serverTimestamp }` | Acknowledge intent arrival. |
| `result` | →client | `{ disposition: accepted\|rejected\|stale, intentId, reason?, snapshot?, capabilities? }` | Action outcome. |
| `chat` | client→ | `{ text }` | Submit room message. |
| `chat` | →client | `{ color, text, serverTimestamp }` | Broadcast sanitized message. |
| `error` | →client | `{ code, message, text? }` | Error notice (`text` holds draft on rate limit). |

### Core payload definitions
- `GameSnapshot`:
  `{ gameId, version, initialFen, fen, sideToMove, moves[], clockSample, result?, whitePlayerId, blackPlayerId, capturedByWhite[], capturedByBlack[], inCheck, pendingDrawOfferBy? }`
- `Capability`:
  `{ id, sessionId, gameId, gameVersion, kind, params, expiresAtEpochMs, signature }`
- `ClockSample`:
  `{ whiteRemainingMs, blackRemainingMs, activeSide, sampledAtEpochMs }`

## Wired UI Features

- **Board Rendering & Legal Moves:** Rendered from server `fen`. Square dots, capture rings, and drag
  destinations are derived entirely from server-supplied `move` capabilities.
- **Local Prediction & Reconciliation:** Committing a move shows an optimistic preview at the target square.
  An `accepted` snapshot confirms it smoothly without jumps; a `rejected` disposition snaps the piece back.
- **Dual Boards & Bughouse Hands:** Bughouse rooms mount two synchronized boards (Board A and Board B)
  sharing clocks and seating. Drops are executed by selecting a reserve piece from `Hand` and dropping
  on a capability-authorized square (`P@e4`). Captured pieces transfer automatically between partner hands.
- **Scoresheet & Clocks:** Tabular monospace clock display interpolates remaining time between server clock
  samples. Flags and timeouts are adjudicated solely by the server.
- **Game Controls:** Abort (available before move 2), Resign (with confirmation), and Draw offers/responses
  (`offer_draw`, `respond_draw`).
- **Pawn Promotion:** `PromotionPicker` appears when a move matches multiple promotion capabilities
  (`e7e8q`, `e7e8r`, etc.); commits the chosen capability.
- **Rematch:** When a game ends, clicking Rematch sends `POST /play/rematch`, redirects both players
  to the new game room, and swaps white/black colors.
- **Captured Pieces & Material Count:** Sourced directly from `capturedByWhite` / `capturedByBlack` in
  the snapshot; material advantage displayed in real time.
- **Watch & Pinned Spectator Mode:** `/watch` streams active matches via SSE with polling fallback.
  Users can pin up to 4 games to follow simultaneously.
- **In-Game Chat & Moderation:**
  - Masking for common English profanity (including leetspeak, spacing, and invisible separators).
  - Unsafe direction/control characters stripped; bounded to 500 UTF-16 units without breaking graphemes.
  - Burst throttle: 1 message per player per board every 750 ms. Rejections return the draft to the input.
  - Accessible emoji picker with keyboard escape and focus restoration.
