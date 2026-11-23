# Chessoc interface

The visual direction is a quiet chess club: charcoal green, warm ivory, muted jade,
and small brass accents. Features should be presented with care and useful context.
Do not invent subscription tiers, ratings, activity, or features to make a screen look busy.

## Shared language

- Colors, surfaces, typography, spacing, focus, and breakpoints live in `src/app/globals.css`.
- `TopNav` supplies the main destinations, current-page state, profile, settings, and a skip link.
- `PageHeading`, `FeatureLink`, and `JourneyNav` connect related destinations with a reason to visit.
- Primary buttons use light jade with dark text; secondary buttons are outlined. Destructive
  game/account actions keep their distinct rose treatment.
- Serif type is reserved for editorial headings; controls use the system sans font and clocks
  use tabular monospace. No external font request is required.
- Use the shared SVG icon set; icons support text and are hidden from assistive technology.

## Flows

- Dashboard: variant, pace, opponent/seats, summary, then one primary action. Custom time is
  disclosed on request. Computer, friend, and matchmaking modes share the same setup.
- Links with `opponent=friend` or `opponent=match` select that setup. `variant=standard`,
  `variant=crazyhouse`, and `variant=bughouse` can preselect the variant.
- Game rooms: board first, clocks beside player names, scoresheet and controls adjacent.
  Keep a dashboard escape visible, including during reconnect and unavailable-room states.
- Watch: discover matches, pin up to four, expand the viewing room, then enter a game.
  Filters, frozen states, and ended matches retain their real meaning.
- History leads to review and engine analysis; friends lead back to friend-game setup.
  Empty states explain what belongs there and provide a useful next step.

## Motion and accessibility

- Transitions are short and limited to useful changes. Page entry is a small 380 ms fade/shift;
  controls transition for 160 ms. Dialogs move only a few pixels and scale by two percent.
- Never delay a server move, reanimate the complete board on a clock tick, or animate layout
  around live controls. The pending move remains a presentation-only preview.
- `Experience` honors the OS motion preference and the app's Reduce motion setting.
  CSS follows that preference immediately, including switches and entrance animations.
- Keep keyboard focus visible, inputs labeled, selected states explicit, and dialog focus trapped.
  Preserve polite move announcements and avoid live clock announcements.
- Test narrow screens as real layouts. Navigation gets its own row, primary controls remain
  reachable, names truncate, and the board keeps its square geometry.

## Verification

Run frontend lint, TypeScript/build, and the existing regression suite. Visually verify the
dashboard, a standard game, bughouse seating/two boards, Watch/pins, review, account screens,
and mobile overflow. Verify guest navigation, custom time, game creation, an accepted move,
and result/review navigation against the real backend. Do not describe a mocked preview as
a tested production match.
