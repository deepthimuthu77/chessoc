# Server → Engine: Reply to Integration Response

Answering your §10 point by point. Headline: **all six confirmed**, two of them
your way. Two small additions requested back. One server-side change your §3.2
fix triggered, noted for your awareness.

Source of truth on our side: [BACKEND_ARCHITECTURE.md](BACKEND_ARCHITECTURE.md),
updated to match. Engine requirements: [ENGINE_INTEGRATION.md](ENGINE_INTEGRATION.md).

---

## Answers to your §10

1. **Wire format — confirmed.** `<board>[<reserve>] <stm> <castling> <ep> <halfmove> <fullmove>`.
   The `~` promoted marker **will survive persistence** — Match State stores the FEN string verbatim
   (bracket + `~` included) and we have a store→reload round-trip test asserting the `~` is preserved.
   Consider it pinned.

2. **`DrawRules value variant` for bughouse — confirmed, and we changed our spec to match yours.**
   You were right; we reversed the earlier "full standard draws" decision. Bughouse boards get
   `DrawRules=variant`: no threefold, no fifty-move. We rely on you to still report `stalemate` and
   `draw_insufficient_material` under that setting, per your note. Standard and crazyhouse boards use
   the default rules.

3. **Threefold input — move-list form is enough.** Our canonical state is FEN + move history, so
   `position fen <start> moves …` is the natural fit; no position-list form needed. (Server-side, for
   bughouse we additionally persist the per-ply resulting FEN so we can reconstruct externally-fed
   reserves — see below. That's ours to handle, not an engine change.)

4. **Search determinism — does not matter to us; do not pin `Threads 1`.** We record the AI's chosen
   move in history and reconstruct games through the deterministic validator, so search strength beats
   reproducibility here. Run full-strength Lazy SMP for AI.

5. **Drop-variant strength — crazyhouse-vs-AI ships in v1, legal-but-weak is accepted.** We are
   offering a crazyhouse AI opponent now and accept that it hangs forced mates / has drop-unaware SEE /
   runs classical eval. So: **not blocking v1**, but the strength work (quiescence drops, drop-aware
   SEE) is wanted on the roadmap when you can get to it. (Note: Bughouse now supports mixed human and
   AI seating, with computer seats driven by per-board bot movers.)

6. **Protocol split — acceptable.** The Adapter parses JSON for the validator tier and UCI text for
   search, owns the TCP line-protocol / process worker boundary, and treats timeout / non-zero exit as
   your `internal_error` (evict + respawn), since a hard fault can't self-report. Two additions requested — see below.

## Requests back to you (both you offered as small)

- **JSON search reply** — `{"bestMove":…,"score":{"cp":…}|{"mate":…},"pv":[…],"depth":…}` so the
  Adapter has one structured parser instead of scraping `info` lines. Would like this.
- **Single-line validator request** form so each call is self-contained. Nice-to-have; the current
  two-line (`position …` then query) is acceptable if the single-line form is costly.

## One thing your §3.2 fix changed on our side (FYI, no action for you)

Because the engine derives nothing from a bughouse capture, per-board `startpos + moves` replay can't
rebuild bughouse reserves. So **Match State is now the sole mutator of bughouse reserves** — it takes
your `capturedDropType`, routes it into the *partner* board's `[...]` field, and **persists the
resulting per-ply FEN** for drop variants so state is always reconstructable. Your one-board model is
exactly what we want; we just own all cross-board hand movement. Good catch flagging the earlier
keep-own-captures behaviour as a bug.

## Net

Nothing blocks you. Confirmed: wire format (with `~`), `DrawRules=variant` for bughouse, move-list
threefold, no `Threads 1` pin, crazyhouse AI weak-for-now. Open on your side only if you take them:
JSON search output and the single-line validator form.
