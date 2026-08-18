# Frontend Integration

How the web client connects to the Java Game Server, and which parts of the API surface are wired to
UI versus documented-only (no UI yet). The client stays a passive projection of server state; the
server is the sole authority.

## Transport split

- **HTTPS** (`http://<host>:8080`, CORS-open) — auth, lobby/seating, ticket issuance.
- **WSS** (`ws://<host>:8081/game?ticket=&gameId=`) — the live envelope protocol.

TLS terminates at the edge in dev (plain `http`/`ws`).

## HTTP endpoints

| Method + path | Body | Response | Notes |
|---|---|---|---|
| `POST /login` | `{ playerId }` | `{ token, sessionId, playerId }` | Dev stand-in for OAuth / email+password. |
| `POST /join` | `{ room }` + `Bearer token` | `{ gameId, color }` | Seats the caller: first joiner `white`, next distinct `black`, then `spectator`. Creates the game on first join. |
| `POST /ticket` | `{}` + `Bearer token` | `{ ticket }` | Single-use, short-lived; consumed on WSS connect. |
| `POST /game` | `{ whiteId, blackId, variant? }` | `{ gameId }` | Dev direct create with both seats named. |

All endpoints answer CORS preflight (`OPTIONS` → 204) and send `Access-Control-Allow-Origin: *`.

## WSS envelope

`{ "v": [1,0], "type": "...", "payload": { ... } }`. Incompatible major → the frame is refused.

| type | dir | payload |
|---|---|---|
| `intent` | client→ | `{ intentId, capabilityId, params, clientTimestamp }` |
| `resync` | client→ | `{ gameId, lastKnownVersion }` |
| `ping` / `pong` | both | `{}` |
| `snapshot` / `resync_ok` | →client | `{ snapshot: GameSnapshot, capabilities: Capability[] }` |
| `receipt` | →client | `{ intentId, serverTimestamp }` |
| `result` | →client | `{ disposition: accepted\|rejected\|stale, intentId, reason?, snapshot?, capabilities? }` |
| `error` | →client | `{ code, message }` |

`GameSnapshot = { gameId, version, initialFen, fen, sideToMove, moves[], clockSample, result?, whitePlayerId, blackPlayerId }`.
`Capability = { id, sessionId, gameId, gameVersion, kind, params, expiresAtEpochMs, signature }`.

## Client integration layer (`web/src/lib`, `web/src/hooks`)

`protocol.ts` (types + envelope framing) · `api.ts` (HTTP client) · `transport.ts` (`LiveTransport`:
keepalive ping, bounded-backoff reconnect, resync on resume) · `store.ts` (pure reducer; projection
replaced wholesale, monotonic version, one pending action) · `view-model.ts` (FEN→pieces,
capabilities→targets, clock interpolation, result mapping) · `use-game.ts` (wires them; exposes
render props + `sendMove`).

## Wired to UI

- Board render from `fen`; legal targets and interactivity from move capabilities.
- Click/drag move → `intent`; reconciliation on `result`/`snapshot`.
- Move history (UCI), server clocks with display interpolation, game-over modal from `result`.
- **Resign** — `GameControls`, two-step confirm.
- **Draw offer / response** — `GameControls`: offer shows "Draw offered"; the opponent gets
  Accept/Decline (`respond_draw {accept}`).
- **Promotion picker** — `PromotionPicker` modal when a from→to has multiple promotion capabilities
  (`promotionChoices`); a single option auto-commits.
- **Connection status** — `ConnectionOverlay` freezes the board on `reconnecting` / `closed`;
  `LiveTransport` reconnects with bounded backoff and resyncs on resume.
- **Spectator** — a third joiner is seated `spectator`, receives snapshots with no capabilities; the
  board is inert and the panel reads "Spectator".

## Documented, no UI yet

Supported server-side / reachable via the integration layer but deliberately not surfaced:

- **Rematch** — `rematch {opponentId}` is issued at game end, but the server currently rejects it
  (`rematch_not_supported_v1`); it needs a server flow (new game, swapped colours, client redirect)
  before a button is worth wiring. `view-model.ts` exposes `findRematch` for when it lands.
- **Clock staleness** — no stale/disconnected clock indicator (§6).
- **Captured pieces / material** — not in `GameSnapshot`; the panels render empty. Needs a server field
  (must not be recomputed client-side).
- **Chat** — player/spectator channels are unimplemented server-side and unbuilt client-side.

## Seating & clocks (dev defaults)

Room name maps to one game; lobby games are unrated, standard, `3+2` (`fischer(180000, 2000)`). Two
browser tabs on the same room become White and Black. A player identity is a per-tab
`sessionStorage` id, so two tabs are two players.
