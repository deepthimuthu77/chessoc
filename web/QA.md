# App regression check - 2026-10-29

Tested against the production Docker stack through Caddy at http://localhost.

## Fixed in this pass

- Full room invite URLs previously produced a broken nested route. Join now extracts the room and preserves the requested seat.
- Room-to-room navigation could retain the previous reducer, result, capabilities, and rematch redirect. Each room/seat now mounts fresh state.
- Delayed reconnect tickets could reopen sockets after leaving a room. Closed transports now stop both successful and failed asynchronous retries.
- Quick-match polling overlapped and swallowed errors. Requests now run serially with visible retry feedback.
- Canceling matchmaking could hide failures or lose an already-created match. Cancellation now preserves the search on failure and opens a match that won the race.
- Pairing discovery was consumed by a single poll; lost responses lost the destination. Polling is now repeatable until a new seek or cancellation.
- Players could queue in multiple pools, and different time controls within the same speed could pair. A new seek replaces the old queue entry and requires the same time control.
- Malformed JSON, invalid variants, unsupported methods, and endpoint suffixes could close requests or run the wrong handler. They now receive structured errors.
- Guest upgrade could orphan active game seats. Active games must finish before upgrade; upgrade also cancels the old queue entry.
- Fresh guests had broken profile links. Known guest identities now have profiles, and guest profiles do not offer an unusable friend action.
- Upgrade fields lacked validation and submit protection. Required fields, constraints, accessible labels, and a synchronous submit guard now apply.
- Chat cleared messages submitted while disconnected. Its input is disabled until connected.
- Successful open-room polling could hide a failed active-game request. The two error states are now independent; stale requests are ignored.

## Verification

- Backend: 126 tests passed, zero skipped, using a disposable PostgreSQL instance on port 15432. The host port 5432 rejected the test credentials; the isolated database avoids that host conflict. The disposable instance was removed afterwards.
- Frontend: production build, TypeScript, and ESLint passed. Reconnect regression tests: `node --test tests/transport.test.mjs` (four passed).
- Nine concurrent rooms / 12 boards with real Vector-64, including human and AI games in standard, crazyhouse, and bughouse. All boards reached four plies; 28 human moves accepted and persisted. Live-feed response p50 4 ms, p95 9 ms, max 42 ms; first SSE event 8 ms.
- Browser checks: expired session/new guest recovery; unknown friend error; empty history; guest profile; settings; leaderboard variant/speed filters; full invite link; unavailable room; missing review and retry; matchmaking start/cancel; AI game, abort, and play again with fresh board state; Watch live feed.

## Limits

This is a regression and obvious-failure-path pass, not an exhaustive production load or security audit. Password changes and permanent account deletion were not executed through the browser. Long network outages, every device/browser combination, large-scale load, and TLS trust installation on external devices were not exercised in this pass.

## Follow-up fixes

- A stalled WebSocket handshake now times out after 15 seconds. An open socket without inbound traffic for 45 seconds is closed at the next heartbeat and reconnects with a fresh ticket.
- Reconnecting with an expired or deleted account now stops both board connections and returns to sign-in with the room destination preserved.
- Null envelopes, non-object payloads, and intents missing identifiers receive a nonfatal protocol error. The connection still answers subsequent pings.
- Deleted registered accounts cannot reuse existing bearer tokens to access the API or mint tickets. Account deletion is blocked while a game seat is active.
- Account lifecycle regressions exercise password replacement, rejection of the old password, deletion, old-token rejection, and active-seat protection using test accounts.
- Four transport tests cover late tickets, failed requests after closing, stalled handshakes, and silent connections. Network timeout tests use a controlled clock; they do not simulate every real network outage.

External-device certificate trust and testing on devices not available here remain deployment verification steps.

## Board and address follow-up - 2026-11-05

- A pending legal move now keeps a presentation-only piece preview at the destination until a server snapshot confirms it. Rejection, protocol error, and disconnect remove the preview without changing authoritative FEN or history.
- The board isolates piece stacking; reconnect, unavailable, and waiting overlays sit above every piece. The partner bughouse board also displays its own connection state.
- Empty-square clicks pass through the piece layer to the board buttons; interactive pieces still receive pointer input.
- Startup now keeps an address monitor open unless `-NoWatch` is supplied. It prints changed LAN/tunnel addresses and updates `.demo/addresses.json` and `.demo/public-url.txt`. A restart check confirmed a replacement Cloudflare URL was printed and saved; Cloudflare issuance exceeded the initial 50-second test window before succeeding.
- Edge verified the reconnect overlay after a real server restart: every piece stays behind the panel, recovery removes the overlay, and a subsequent e2e4 received the AI reply c7c5.
- Disconnected finished-game screens previously retained stale rematch capabilities and could show an endless pending request. Disconnect now invalidates capabilities; rematch pending state is entered only after an actual socket send, resets on disconnect, and exposes rejection errors in the result dialog.
- Reconnect, unavailable-board, waiting-for-players, and result screens provide a Go to dashboard link. Room navigation uses the same label.
- Edge verified the final production build: reconnect preserved e2e4/c7c5, resign opened the result dialog with dashboard navigation, Play again created a fresh game with swapped colors and an AI opening move, and room navigation returned to the dashboard.
- Eleven frontend tests pass, including pending-preview acceptance/rollback, capability invalidation across reconnect, and disconnected-send reporting. TypeScript, ESLint, and the production build pass.
