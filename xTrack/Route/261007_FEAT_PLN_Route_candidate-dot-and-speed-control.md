# Route — the best-candidate mark

**Status:** in design, nothing implemented
**Branch:** to be cut when ordered (the work is small enough for the current `feature/tracks-rotes-norm` if the user prefers to keep one branch)
**Created:** 2026-10-07
**Owner:** Route (the acquisition panel), with `MapPulseDot` owned by UI_Map

> The second item this plan first carried — the Routes Speed and Direction block matching the tracks
> control — **moved to Ui_Settings**, whose Tracks/Routes section re-arrangement owns that surface:
> [`../Ui_Settings/261007_FEAT_PLN_Ui_Settings_tracks-routes-sections-rearrangement.md`](../Ui_Settings/261007_FEAT_PLN_Ui_Settings_tracks-routes-sections-rearrangement.md).

---

## Request (the user's words, 2026-10-07)

- **a pulse red dot in the row that is the best candidate** — the acquisition's summary table.

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
## The pulse dot on the best candidate

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

## The speed control — moved out

The control swap this plan first specified lives in the Ui_Settings plan above, where the section
re-arrangement carries it along with the two moves around it: the routes chips become a "Speed Display"
sub-section at the top of Routes Appearance, on the same two-choice row the tracks block uses, and the
block they left is renamed "Speed Arrows Settings" and moved above export/import. Nothing of it is
planned here.

## Steps (when ordered)

1. Cut or reuse a branch (the user's call — `feature/tracks-rotes-norm` already carries the same map
   files, so one branch avoids a second conflict surface).
2. Put `MapPulseDot` beside the first column's text on `index == 0` in `RouteSummaryTable`, leaving
   the selected row's own styling untouched.
3. Tests: a case pinning that the mark lands on the ETA-best row and not on a row the selection has
   moved to.
4. `apk-build.bat` plus the scoped `ui.map` + `config` suite; the device look stays the user's.

## Risks

- The dot competes with the row's bold selection weight; on a single-page acquisition (`pages.size == 1`)
  the best row and the selected row are the same row, which is the case the device pass should read first.
- The mark's meaning is "this control is live" everywhere it is used today, so the reuse bends that
  reading; the user's word settles whether it stays.

## Out of scope

- No change to the acquisition's ordering, its columns, its figures or the ladder itself.
- No change to the tracks block, whose two chips are untouched.
- No new property key unless the user rejects the shared mark.
