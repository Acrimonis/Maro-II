# Route — the best-candidate mark and the route speed control

**Status:** in design, nothing implemented
**Branch:** to be cut when ordered (the work is small enough for the current `feature/tracks-rotes-norm` if the user prefers to keep one branch)
**Created:** 2026-10-07
**Owner:** Route (the acquisition panel and the Routing tab's rows), with `MapPulseDot` owned by UI_Map

---

## Request (the user's words, 2026-10-07)

Two items, ordered together with the Tracks normalisation's findings assessment:

- **a pulse red dot in the row that is the best candidate** — the acquisition's summary table;
- **the Settings Routes Speed and Direction block must match the tracks block** — the same
  two-choice multi-select toggle control.

## What already exists (read, not assumed)

- **The pulse mark is already one shared home.** `MapPulseDot` draws a 10 dp disc in
  `ui.map.pulse.dot` (default `#FFD32F2F` — already red) with a 1 → 0.3 beat over 800 ms, and
  `rememberPulseAlpha(pulseMs, label)` is exposed apart from the disc for anything that wants to
  *pulse* rather than *draw a dot*. Its readers today are the drawer's live block, the layer fan's
  armed square, the track-status icon and the route toggle — all of them controls whose meaning is
  "this control is live" (R69).
- **The acquisition's table has rows per candidate.** `RouteSummaryTable` in `RouteConfirmPanel.kt`
  iterates `pages.forEachIndexed { index, page -> … }` with `selected = index == selectedIndex`,
  `first = index == 0`, a per-row shape, and a bold weight on the selected row. The pages are the
  engine's rungs **in ETA order, the main first**, which the panel's own KDoc states and
  `onSelectPage` repeats ("by its position in the ETA-ordered view").
- **The two settings blocks differ exactly as the user says.** The tracks block
  (`MapScreenSettingsOverlay.kt`, the Tracks expander) is one `MultiSelectRow` over the two axes —
  options `menu_render_arrows` ("Arrows") and `menu_render_colours` ("Speed Colors"), `isOn` and
  `onToggle` keyed on `trackArrows` / `trackColours`. The routes block (the Routing tab's
  `settings_routes_speed_direction_label` expander) is **two `ToggleRow`s** with the long labels
  `settings_routes_speed_color_label` ("Speed colours on routes") and `settings_routes_arrows_label`
  ("Arrows on routes"), over `routeSpeedColor` / `routeSpeedArrows`.

## Item 1 — the pulse dot on the best candidate

- The row to mark is **index 0 of the ETA-ordered table** — the engine's own best rung — not the
  selected row, which already carries its own weight and shape as the selection.
- The mark reuses `MapPulseDot` beside the row's first column, so the colour, the size and the beat
  stay in their one home rather than being re-drawn here.
- **One judgement for the user:** the mark's shipped meaning is "this control is live", and a table
  row is not a control. Reusing it keeps one home but stretches that meaning; a new key or a
  non-pulsing dot would keep the meaning and spend a second home. The plan takes the reuse unless the
  user says otherwise.
- The row's leading column is the engine's own description text, so the dot rides a small leading
  box that the text sits beside — the row's height and the table's column weights are not moved.

## Item 2 — the route speed control matched to the tracks one

- Replace the block's two `ToggleRow`s with **one `MultiSelectRow`** over the same two axes, the
  options reading the **same two short labels the tracks block uses** (`menu_render_arrows` /
  `menu_render_colours`) so the two blocks are one control shape, as asked.
- The expander's own caption and description stay (`settings_routes_speed_direction_label` /
  `settings_routes_speed_direction_desc`, the description already rewritten this session to drop the
  retired master claim).
- The two long labels `settings_routes_speed_color_label` / `settings_routes_arrows_label` become
  unused; the step confirms they have no other reader and deletes them from `values/` and
  `values-fr/` in the same edit (one home per fact — no dead strings).
- No value moves: the two axes keep `routeSpeedColor` / `routeSpeedArrows`, their keys and their
  seeds, so nothing a user sees changes but the control's shape.
- **One judgement for the user:** the tracks block's row sits under its own `SubSectionHeader`
  ("Speed Display"). The routes block has no such header today; the plan keeps it header-less so the
  swap is a control-for-control change, and a header can be added if the user wants the two blocks
  to read identically down to their furniture.

## Steps (when ordered)

1. Cut or reuse a branch (the user's call — `feature/tracks-rotes-norm` already carries the same
   settings file, so one branch avoids a second conflict surface).
2. Item 2 first: swap the two `ToggleRow`s for the `MultiSelectRow`, delete the two dead labels from
   both locales after confirming no other reader.
3. Item 1: put `MapPulseDot` beside the first column's text on `index == 0` in
   `RouteSummaryTable`, leaving the selected row's own styling untouched.
4. Tests: a case pinning that the mark lands on the ETA-best row and not on a row the selection has
   moved to, and that the routes block exposes the same two axes the tracks block does.
5. `apk-build.bat` plus the scoped `ui.map` + `config` suite; the device look stays the user's.

## Risks

- The dot competes with the row's bold selection weight; on a single-page acquisition (`pages.size == 1`)
  the best row and the selected row are the same row, which is the case the device pass should read first.
- `MultiSelectRow` inherits whatever semantics wrapper the tracks block gets; the swap must not change
  the toggle sizes or the row's trailing chevron convention in the Routing tab.

## Out of scope

- No change to the acquisition's ordering, its columns, its figures or the ladder itself.
- No change to the tracks block, whose two chips are untouched.
- No new property key unless the user rejects the shared mark in item 1.
