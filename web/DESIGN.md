# Chessoc interface

The visual direction uses neutral charcoal and graphite, warm ivory, gold actions,
and quieter brass details. Features should be presented with care and useful context.
Do not invent subscription tiers, ratings, activity, or features to make a screen look busy.

## Shared language

- Colors, surfaces, typography, spacing, focus, and breakpoints live in `src/app/globals.css`.
- `TopNav` supplies the main destinations, current-page state, profile, settings, and a skip link.
- `PageHeading`, `FeatureLink`, and `JourneyNav` connect related destinations with a reason to visit.
- Primary buttons use a restrained gold gradient with dark text; secondary buttons are outlined. Destructive
  game/account actions keep their distinct rose treatment.
- Cormorant Garamond supplies editorial headings and Manrope supplies controls and body text.
  Clocks retain tabular monospace. The two variable Latin WOFF2 files total about 62 KB and are
  served locally through Next's font loader; licenses and sources live in `src/app/fonts`.
- Frosted glass is reserved for navigation, game/promotion/reconnect dialogs, the emoji picker,
  the setup action panel, and the pinned viewing area. Boards and reading panels stay opaque.
  Browsers without backdrop filtering receive solid surfaces; increased contrast removes glass.
- Use the shared SVG icon set; icons support text and are hidden from assistive technology.

## Flows

- Dashboard: the current setup and primary action appear together before the detailed choices
  and stay below the navigation while scrolling. Variant, pace, and opponent/seats update that
  summary. Custom time is disclosed on request. Computer, friend, and matchmaking modes share
  the same setup, and creation errors remain beside the action.
- Links with `opponent=friend` or `opponent=match` select that setup. `variant=standard`,
  `variant=crazyhouse`, and `variant=bughouse` can preselect the variant.
- Game rooms: board first, clocks beside player names, scoresheet and controls adjacent.
  Keep a dashboard escape visible, including during reconnect and unavailable-room states.
- Watch: discover matches, pin up to four, expand the viewing room, then enter a game.
  Filters sit above saved boards and explicitly apply to live discovery rather than curated pins.
  Frozen states and ended matches retain their real meaning.
- History leads to review and engine analysis; friends lead back to friend-game setup.
  Empty states explain what belongs there and provide a useful next step.

## Motion and accessibility

- Transitions are short and limited to useful changes. Pages fade for 300 ms; the dashboard
  introduction has a brief stagger. Buttons lift at most two pixels, and dialogs move only
  a few pixels and scale by two percent. Piece travel uses a 160 ms ease-out without a spring.
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
