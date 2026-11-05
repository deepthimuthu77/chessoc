# Feature priorities

Assessment of the current implementation, 2026-11-05. This is a product roadmap, not a list of features already built. Source code takes precedence over older architecture documents, which still describe bughouse AI as future work.

## Already present

Standard chess, crazyhouse, and bughouse; human and AI opponents; mixed human/AI bughouse seats; configurable seats and time controls; AI effort levels; room invites; guest accounts and registration; friends and presence; matchmaking; game history; per-pool ratings for supported rated games; board review and position analysis; reconnect/resync; persisted live games; spectator discovery, filters, and four-match pinning; Docker startup/shutdown and temporary HTTPS sharing.

## Needed now

| Priority | Addition | Current gap and benefit |
| --- | --- | --- |
| 1 | Complete bughouse pregame lobby | Seat configuration/invites exist, but players need explicit Ready, visible human/AI assignments, host start, and safe seat changes before clocks run. |
| 2 | Separate player, teammate, and spectator chat | Current board chat broadcasts to all subscribers. Team strategy needs a private partner channel; spectators should not coach rated players through shared chat. |
| 3 | Public-demo resource controls | There is a move-intent limiter and bounded engine pool, but broad authentication, room creation, analysis, and chat limits are missing. Add request/message size limits, per-user/IP budgets, and useful busy responses. |
| 4 | Room privacy | Open-seat invites are not private invitations. Add public/unlisted/invite-only visibility and enforce it on join and game discovery. |
| 5 | Rating integrity | Add a consistent delayed rated spectator feed across discovery, rooms, and history APIs. Position analysis is publicly callable; UI restrictions alone cannot prevent outside engine assistance. |
| 6 | Recovery and operating visibility | Live-room persistence exists. Add database backups with a tested restore command, API/engine health, queue depth, and useful error logs. |

For a closed friends demo, pregame readiness, team chat, and resource controls bring the most immediate value. Before retaining public user accounts, account recovery also becomes a priority.

## Additions that strongly complement this setup

| Addition | Why it fits | State |
| --- | --- | --- |
| Bughouse partner requests | One-click Need knight, Need pawn, Hold, or Go, routed only to the partner. Adds coordination without typing during blitz. | Missing |
| Challenge a friend directly | Invite an online friend, accept/decline, then enter the right room/seat. | Friends exist; backend challenge primitives are not a complete exposed product flow |
| Persistent bughouse teams | Save preferred partners, rematch the same four people, and optionally swap teams/colors together. | Existing new-game flow creates another room; team sessions are incomplete |
| Per-seat AI effort | Different strength for each computer, especially a stronger partner or weaker opponent. | Current level is shared across computer seats in a room |
| Coordinated bughouse AI | Search with partner-board information and requested pieces. | Captured-piece transfers work; current move selection is per-board |
| Readable move notation and export | SAN-style notation, FEN copy, standard/crazyhouse PGN, and paired bughouse export with clocks and transfer events. | Current presentation/history uses UCI; no export workflow |
| Better postgame review | Evaluation graph, identified mistakes, alternatives, and synchronized bughouse board timelines. | Position analysis and separate board review exist |
| Variant onboarding | Explain drops, promoted captures, team colors, clocks, and seats with a short first-game guide. | Missing dedicated onboarding |
| Connection information | Show reconnect state, recent latency, and opponent presence without implying that clocks pause on disconnect. | Recovery exists; richer feedback is incomplete |

## Nice to have

- QR invites, copy-link feedback, and an optional share panel in the lobby.
- Board/piece themes, keyboard shortcuts, volume control, and configurable clock alerts.
- Saved game presets and saved spectator layouts beyond the current pin set.
- PWA install, fullscreen board, and improved touch ergonomics; offline UI must never imply offline live-game authority.
- Spectator follow/favorites and notifications when a friend starts playing.
- Daily variant puzzles and practice positions, after review/export foundations exist.

## Entirely missing product areas

| Area | What would need to be built | When |
| --- | --- | --- |
| Account recovery | Email verification, reset links, session/device management, and revocation policy | Before relying on persistent public accounts |
| Moderation | Mute/block/report, review cases, moderator tools, bans, and chat retention policy | Before open public chat |
| Competitive integrity | Fair-play review, evidence capture, and a defined response/appeal process | Before presenting ratings as competition-ready |
| Bughouse ratings | A deliberate team/per-player rating model with team-aware results | After team sessions and rating policy |
| Tournaments | Teams, pairing, standings, scheduling, disconnect rules, and spectator pages | Later |
| Study tools | Analysis variations, annotations, shared studies, saved positions, and imports | Later |
| Multi-node operation | Match ownership, routing, failover, shared presence, and operational tooling | After measured load requires it |
| Stable public hosting | Persistent hostname, deployment lifecycle, and external uptime monitoring | When the temporary no-domain demo becomes a lasting service |

## Suggested build order

1. Pregame readiness and private team communication.
2. Public/unlisted/private rooms and resource controls.
3. Friend challenges and persistent four-player/team rematches.
4. Readable notation, exports, and synchronized bughouse review.
5. Account recovery, moderation, backups, and operational visibility before public growth.
6. Stronger coordinated AI, team ratings, studies, and tournaments.

Do not treat more AI engine processes, a larger spectator catalog, or multi-node infrastructure as automatic upgrades. Measure CPU, queue time, memory, and concurrent-match latency first.
