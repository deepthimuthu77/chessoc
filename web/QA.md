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

- Backend: 123 tests passed, zero skipped, using a disposable PostgreSQL instance on port 15432. The host port 5432 rejected the test credentials; the isolated database avoids that host conflict. The disposable instance was removed afterwards.
- Frontend: production build, TypeScript, and ESLint passed. Reconnect regression tests: `node --test tests/transport.test.mjs` (two passed).
- Nine concurrent rooms / 12 boards with real Vector-64, including human and AI games in standard, crazyhouse, and bughouse. All boards reached four plies; 28 human moves accepted and persisted. Live-feed response p50 4 ms, p95 9 ms, max 42 ms; first SSE event 8 ms.
- Browser checks: expired session/new guest recovery; unknown friend error; empty history; guest profile; settings; leaderboard variant/speed filters; full invite link; unavailable room; missing review and retry; matchmaking start/cancel; AI game, abort, and play again with fresh board state; Watch live feed.

## Limits

This is a regression and obvious-failure-path pass, not an exhaustive production load or security audit. Password changes and permanent account deletion were not executed through the browser. Long network outages, every device/browser combination, large-scale load, and TLS trust installation on external devices were not exercised in this pass.
