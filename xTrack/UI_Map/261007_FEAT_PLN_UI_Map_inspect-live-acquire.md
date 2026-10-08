<!-- scope: feature -->
# Inspect Mode — live acquire, pause-to-recentre

> Host feature: **UI_Map** (map interaction and map chrome). The card, its Prev/Next walk and the close
> rules stay owned by **Ui_General** / **Markers**.
> Supersedes, on the trigger and the exit rules alone,
> [`260917_FEAT_PLN_UI_Map_inspect-mode.md`](260917_FEAT_PLN_UI_Map_inspect-mode.md): the ranking, the
> warm sweep, the ladder, the cursor and the candidate overlay all carry over unchanged.
> Settled with the user on 2026-10-07; §8 carries the checks owed and the one interpretation to confirm.

## Problem

- Today the card opens only on a genuine finger lift plus a quiet wait, and the open is what moves the
  camera, so the gold follows the drag while the panel lags behind it.
- The mode's question — "what is nearest me?" — is already answered on screen long before any lift, so
  a panel that waits for one reads as a delay rather than a confirmation.
- The rework makes the panel follow the highlight live, spends the quiet on a recentre instead of a pick,
  and narrows the way out to the toggle, Back and the boat-icon tap.

## 1. Behaviour

- **Reference** — the boat icon at the map centre is the measuring point, exactly today's anchor, centre
  offset included. The **user's own drag re-establishes it**, so the acquisition starts again from where
  they leave the map, while the pause's recentre moves the view and never the acquire point (2026-10-07).
- **Live acquire** — while armed, the nearest inspectable item to that point is re-ranked on every sweep
  and its dashboard is opened at once: no lift and no dwell gate the open. A change of nearest swaps the
  panel, but only by the margin `map.inspect.switchMarginPct` settles (2026-10-07): a newcomer must be
  meaningfully closer than the item the panel holds, or two candidates at nearly the same distance hand
  it back and forth all through a drag.
- **No target, no panel** — the panel is present exactly while a target is identified: it opens on
  acquisition and closes when the anchor loses its target, the gold clearing with it, and the mode stays
  armed meanwhile.
- **The pause recentres** — after `map.inspect.dwellMs` of quiet, the mode moves the camera onto the
  acquired item, centred and given the screen: a marker through the existing focus framing, a track
  through its zoom-to-fit. The ladder is still snapshotted at that moment (§2).
- **Reset after the recentre** — once the mode's own recentre has landed, a user drag forgets everything:
  the card, the ladder and its walk, and the camera captured at arming. The mode re-acquires the nearest
  and opens its dashboard, as a fresh arming does.
- **A drag before the recentre is not a reset** — during the quiet it only restarts the quiet, as today.
- **Exits** — the toggle, the system Back and the dashboard's Back arrow leave the mode, and so does any
  action that supersedes it: the boat-icon tap, which leaves the mode *and* runs the where-amI query
  itself, and arming a route, which keeps its exclusivity with the mode.
- **The gold is unchanged** — it still marks the selected item.
- **Dropped** — the armed tap on a map marker, and with it the overlay's own tap plumbing (the proximity
  receiver and the per-pin listeners), which then has no consumer.
- **GPS anchor off** — the boat stops holding the centre for the whole time the mode is on, rather than
  being suppressed per open.

## 2. The gate (unchanged)

- The ladder is still one bounded pass, run at the pause over the warm candidates and ranked from the
  mode's own reference — which the recentre cannot move — ordered by proximity and deliberately not
  viewport-filtered (`InspectRanking.rank`). The walk steps that order, and a post-reset drag rebuilds it
  and resets the arrows with it.

## 3. The camera

- One authority while armed: the pause's recentre and the user's own drag. The **live acquire** stops
  framing, so a swap moves nothing under the finger; a **step** of the frozen ladder is the one exception
  (2026-10-07) and frames the item it selects, because the map following the selection is what the walk is
  for.
- The recentre reuses the framing the mode already reads — `markerFocusTarget` for a marker, the track
  zoom-to-fit — re-timed from the open to the pause; no second framer is written.
- The mode marks its own recentre: a programmatic centre change raises the same scroll event a drag does,
  so the mark is what stops the quiet restarting and the reset firing on the mode's own move.
- The live swap reuses the held-predecessor machinery the cross-type step already uses, so the slot's
  visibility never blinks; each swap must skip the openers' pre-navigation capture, or every nearest
  change would file a frame for the exit to restore.
- The mode's own track preview hands over to the canonical rebuild on every swap, exactly as it does once
  today, so the gold line and the selected line never both stand.

## 4. Exits and the hold

- The disarms that go: the track-save path and the landing's own stand-down. Arming the route keeps its
  disarm because it supersedes the mode, and the armed flag otherwise ends only on §1's exits.
- The arming capture keeps its exit meaning — put follow back or leave the frame — until a reset drops it,
  after which the exit restores nothing.
- With the anchor off while armed the centre hold stops being a suppression pair, and the recentre square
  becomes visible while the mode is armed.

## 5. Keys and strings

- `map.inspect.dwellMs` keeps its key and its value; its description moves from the quiet before the
  pick to the quiet before the recentre.
- `map.inspect.switchMarginPct` is new (2026-10-07): the percent by which a newcomer must beat the held
  item's own distance before the panel hands over, clamped 0–90, shipped at 15.
- No new user-facing string is expected.

## 6. Change surface

- `ui/map/InspectMode.kt` — the trigger block and the pick path.
- `ui/map/MapScreen.kt` — the arm / open / landing / close machine, the disarm call sites, the framing
  effect and the boat-icon tap.
- `ui/map/NavigationViewModel.kt` and `ui/map/PanResumeTimer.kt` — the hold and the resume rule.
- `ui/map/MarkerOverlay.kt` — the dropped tap plumbing.
- `config/AppConfig.kt` — the dwell key's comment.

## 7. Verification

- **Unit** — the live swap, the reset boundary (after the recentre, never before), and the recentre's own
  mark keeping the quiet from restarting.
- **Build** — `apk-build.bat` green with the `ui.map` suite.
- **Device** — a drag whose panel swaps as the nearest changes; the pause's recentre; a post-recentre drag
  resetting everything; the toggle, Back and boat-icon exits; and the anchor staying off while armed.

## 8. Owed and to confirm

- Confirmed: for a point marker, "full screen" is the existing focus framing's share of the screen, a
  literal full-screen zoom of a point being meaningless.
- Reading recorded: the user's "wizard" is taken to be the item's panel — the dashboard the mode opens —
  so "opens as soon as a target is identified, closed when not" is the panel's presence rule, and the
  editor keeps its own door on the card.
- Nothing else remains open; the checks live in §7.

### Findings of the 2026-10-07 implement pass

- **Fixed in the follow-up pass.** `disarmInspectMode` now closes the card (`closeInspectCard()`) before
  `viewModel.disarmInspect(...)`: without it `inspectCardOpen` stayed set, `inspectLastExit` read false,
  and the retained capture never landed on the toggle-with-a-card or on the Back door.
- **§7 unit checks written.** The live swap, the reset boundary and the recentre's own mark are three
  pure rules in `InspectMode.kt` — `inspectAcquireAction`, `inspectUserMoveAction`,
  `inspectActivityAfterMotion` — pinned in `InspectAcquireTest.kt`; the clock's own test was retimed to
  the quiet before the recentre.
- **§1 deviation.** `MarkerOverlay` keeps one bare `{ _, _ -> true }` tap suppressor where §1 drops the
  per-pin listeners: with no listener at all, osmdroid opens its own info window on every marker tap.
- **§4 readings.** "GPS anchor off" is the centre hold collapsing to the armed flag alone, which is what
  keeps §4's retained capture true; the "track-save path" disarm is **kept**, because `followSavedTrack`
  arms the route mode and §1 protects arming a route's exclusivity — the two rulings conflict and §1 was
  followed.
- **Fixed after the first device read (2026-10-07).** The live open called `openEditDrawer` straight, so
  it never recorded `lastFramedMarkerId` the way the click-n-move path did: the marker framing effect
  then zoomed the card the instant it appeared, before any quiet, and the card was additionally sourced
  as the list world while no ladder existed yet. The framing effect is now guarded on
  `DrawerSource.INSPECT`, and the non-framing open sources the card as the mode's own with the
  map-filtered world.
- **Fixed after the second device read (2026-10-07).** The reset was keyed on a centre delta, so residue
  of the mode's own recentre landed after the mark dropped, read as a drag, and dropped the ladder the
  recentre had just frozen. It now keys on the pan gate — the once-per-drag signal `MapPanDetector`
  already produces — which is §1's "user drag" and nothing else, and the mark keeps only its §3 job.
- **Key spelling corrected.** Both plans named the dwell key `ui.map.inspect.dwell.ms`; the asset and the
  code use `map.inspect.dwellMs`, which is the source of truth, and `maro.properties`' own comment still
  described the lift-plus-pick. Both the comment and the plan text now follow the asset.
- **Fixed after the third device read (2026-10-07).** The walk must follow its selection once the mode has
  taken the camera: the live acquire still never frames, but a step of the frozen ladder does. The open now
  carries a `frame` flag — false for the acquire, true for a step — and the framing effect's guard covers
  the acquire's window alone (`armed && !landed`) rather than every inspect card; the recentre records the
  id it framed, so the selection's own effect steps aside for it.
- **Fixed after the fourth and fifth device reads (2026-10-07).** The acquire point followed the camera: the
  anchor was re-read on every motion, so the pause's recentre moved what "nearest" was measured from. The
  reference now follows the **user's own motion only** — a drag re-establishes it, so the acquisition starts
  again from where they leave the map — and the viewport is read fresh on every scan. The mark that
  separates the two is released only once the centre has settled, because a fixed delay under-ran the tail
  of an animated zoom-to-fit and let a residual event drag the point onto the item.
- **Damped after the sixth device read (2026-10-07).** The live acquire swapped the panel the instant the
  nearest changed, so two candidates at nearly equal distance handed it back and forth and the card swap
  flickered. The acquire now keeps its item unless a newcomer is closer by `map.inspect.switchMarginPct`
  of the held item's own distance, judged against that item's distance **as the scan finds it** — so the
  panel can neither churn nor stick — and the gold moves with the panel, so the two never disagree.
- **Fixed after the seventh device read (2026-10-07): the swap's few-frame balloon.** `DrawerScaffold`'s
  wrap branch remembers its three part heights per call site, and a cross-type swap replaces that call site
  (the two selected-item cards are different composables), so they restart at zero — and "not yet measured"
  read as "zero tall" for a frame: the bottom slack opened at the whole floor and the body was free to
  measure against the entire frame, so the panel ballooned to almost the middle of the screen before
  collapsing. Until a part reports, the panel is now pre-sized at the default dashboard height (the slack
  shut, the body capped at what that height leaves under the header), so the first frame is exactly the
  frame's own floor and the bottom-anchored layout appears one frame later at the same size. The scaffold is
  shared by every wrap-mode panel, so the guard is written to change nothing once a part has reported; its
  own home is `xTrack/Ui_Settings/260906_FEAT_PLN_Ui_Settings_drawer-content-measurement.md`.
- **§7 device pass** stays owed to the user, as does any commit or deploy.

- **Normalised after the eighth device read (2026-10-07).** The three map dashboards disagreed on their
  headers: the track card took `DrawerScaffold`'s 24 dp default, the route panel carried its own 16 dp /
  8 dp pair that `ui-drawer-guidelines.md` §6 did not even name, and the marker viewer's 12 dp horizontal
  with the shared 6 dp vertical is the reference they are all on now. The portrait track card also took
  the marker card's scrollable body, and the map cards gained a ~120 ms fade-in (`fadeInOnEnter`) so a
  cross-type swap dissolves rather than cutting. §6 now states the one header token and §12's tables carry
  `fadeInOnEnter` and the five map cards; the stale marker/wizard vertical figures are corrected.

- **Gold rim added after the ninth device read (2026-10-07).** A selected line that takes the banded path
  carries the speed ramp and so has no gold of its own — which is why an inspect-acquired route or track
  looked exactly like any other line while a marker kept its ring. `path.line.casing.selected` is new: the
  selection's own gold, drawn one dp a side outside the dark casing on every selected path and laid down
  first, so it is the outermost stroke and the dark rim still reads between it and the core. The chevrons
  keep the dark casing.

- **Walk-row spacing closed after the tenth device read (2026-10-07).** Measured, the two footers already
  agreed on everything but one: the track card's footer ends with a 10 dp spacer below its pills, while the
  marker card's `MarkerPrevNext` ended flush on the frame's bottom edge. It now carries the same trailing
  spacer, so the item frame to the row, the gap between Previous and Next, and the row to the frame's
  bottom edge are all 10 dp on both cards (the row's own 12 dp horizontal matches the cards'
  `contentPadding`). `docs/ui-drawer-guidelines.md` §6 records the token.

- **Gold rim's width made its own setting (2026-10-07).** `path.line.casing.selected.width` is new — the
  rim's width alone, shipped at **22 dp**, the tripled value of the 7.333 it had been deriving (the dark
  casing's 5.3333 plus one dp a side) — read into `AppConfig.trackSelectionCasingWidthDp` at
  `PathClass.SELECTED`, and `selectionGoldCasing()` reads it rather than computing an add. The other render
  widths also carry Settings-grid rows; a row for this one is a further step and was not taken here.

- **Walk row normalised to the route panel after the eleventh device read (2026-10-07).** The route's
  footer is the reference — `padding(horizontal = 12.dp, vertical = 8.dp)` with `spacedBy(8.dp)`, so the
  three distances it defines are 8 dp — while the marker card's `MarkerPrevNext` and the track card's two
  byte-identical footers sat at 10 dp on all three. All three are now 8 dp from the item frame to the row,
  8 dp between Previous and Next, and 8 dp from the row to the frame's bottom edge, with the 12 dp
  horizontal and the pills' own height untouched. §6's walk-row note in `ui-drawer-guidelines.md` now names
  the route panel as the token's source and the other two as its homes.

- **Layout validated and the shape aligned after the twelfth device read (2026-10-07).** The audit that
  followed corrected the earlier report: the two headers are **already one height** — the marker's
  `deleteAction()` and the track's `TrackDrawerHeaderActions` are both a 36 dp `IconButton` carrying a
  24 dp icon — so the header was never an asymmetry. The one geometric difference left was the landscape
  corner shape, this card wearing its own `bottomStart` rounding where the marker card, the wizard and the
  route panel all wear `topEnd`/`bottomEnd`; it now matches. Everything else was measured identical: one
  slot, one scaffold, one floor, one ceiling, `bottomAnchoredContent` on both, and now one footer token
  (8 dp above, 8 dp between, 8 dp below, 12 dp horizontal, the same 36/24 buttons).
- **Still unexplained, and needing a rendered reading:** the prev/next row's movement on a track↔marker
  swap. No token explains it, since the chain that pins the row — wrap root, its Column at `BottomCenter`,
  the slot at `BottomCenter` — is shared; the band, which follows the open panel's measured height, is the
  one candidate outside the tokens. A device reading that distinguishes the row's own position from the
  card-with-band is what settles it.

- **Dash panels squared after the thirteenth device read (2026-10-07).** The portrait panels were already
  `RoundedCornerShape(0.dp)`; the rounding lived in the **landscape** shapes — the marker card's own
  `bottomStart = 16.dp` (the corner "at the bottom" the complaint named), the route panel's
  `topEnd`/`bottomEnd = 16.dp`, and the track card's landscape shape this pass had just set to
  `topEnd`/`bottomEnd`. All three now pass `RoundedCornerShape(0.dp)` in both orientations, so a map dash
  panel never rounds a corner; `ui-drawer-guidelines.md` §6 records the rule and notes that
  `DrawerScaffold`'s own default rounding belongs to the drawers alone while the wizard keeps its shape.
- **The prev/next row's movement — cause found, fixed after the fifteenth device read (2026-10-07).** The
  row was not moving: it was being pushed. The route panel's footer is a `ConfirmActionButton` row, and a
  tier-1 button carries the 2 dp accent rim and the `ButtonDefaults` content padding of a real action — the
  hand-rolled pills the marker card and the track card wore were shorter, so a cross-type swap exchanged a
  short footer for a tall one. Every dash panel is bottom-anchored with its footer last, so the taller
  footer lifted the row's top edge and the card above it by exactly that difference, in portrait and
  landscape alike. `MarkerPrevNext` and the track card's two byte-identical footers now wear the route
  panel's own `ConfirmActionButton` pair — Previous `SECONDARY`, Next `PRIMARY` — and each row carries the
  route footer's whole frame in its own `padding(horizontal = 12.dp, vertical = 8.dp)` with no external
  spacer, so one button and one frame serve all three panels. `ui-drawer-guidelines.md` §6 records it as
  "Dash-footer buttons (settled 2026-10-07)": a dash footer wears the route panel's tier-1 button, and a
  hand-rolled pill is not to be used there.
- **The swap's remaining jump — the frame between the swap and the parts' report (2026-10-07).** A wrap
  panel's first frame knows no header, no body and no footer: the card arriving in a slot was laid out at
  the floor with its walk row floating above the frame's bottom edge, and a taller card could overshoot
  the frame before settling — which is what a cross-type swap showed. `DrawerScaffold` now takes
  `initialHeight`, the height the outgoing panel in the same slot reported, and pins the pre-measured
  frame to it with the body and the footer held on that frame's bottom edge; the map seeds it from
  `DashboardBandState.lastShown`, written by whichever panel comes through the slot. A swap therefore
  paints the size the screen already shows and settles once, and a host that never swaps passes nothing
  and keeps the floor.
- **Green:** `apk-build.bat` and the scoped `ui.map` unit run, both after the footer normalisation and
  again after the swap seed.

## Outcome

Shipped 2026-10-07. The lift-plus-dwell trigger is gone: `MapInspectEffects` publishes the sweep's true
closest and the shell turns that answer into the panel's presence — opened at once, swapped on a change
of nearest, closed when the anchor loses its target while the mode stays armed. The quiet is now the
pause: its expiry recentres the camera through the **existing** framer (`frameMarker` for a marker, the
track's own zoom-to-fit) and freezes the ladder, which the card's walk then steps. The card's own close
is the mode's exit, a drag after a landed recentre resets the card, the ladder and the captured frame,
and the armed tap on a map marker went with `MarkerOverlay`'s proximity receiver, its two hit-test
helpers and the `onMapTouch` lift hook. The centre hold collapsed to the armed flag alone
(`inspectHoldsCentre`), the dwell key's comment moved to the quiet before the recentre, and the dead
`MarkerOverlay` helpers were deleted rather than left standing.

Deviations. `MarkerOverlay` keeps **one** bare `{ _, _ -> true }` suppressor where §1 drops the per-pin
listeners: with no listener at all, osmdroid opens its own info window on every marker tap. §4's
"track-save path" disarm is **kept**, because `followSavedTrack` arms the route mode and §1 protects
arming a route's exclusivity — §1 was followed. §4's "GPS anchor off" is read as the centre-hold pair
collapsing to the armed flag, which is what keeps §4's retained capture true. The recentre is a second
*invocation* of the shared framer, never a second framer.

Fixed in the same pass. `disarmInspectMode` closes the card before the ViewModel disarm; without it the
retained capture never landed on the toggle-with-a-card or on the Back door. The three rules §7 asks for
are now pure (`inspectAcquireAction`, `inspectUserMoveAction`, `inspectActivityAfterMotion`) and pinned
in `InspectAcquireTest.kt`.

Green: `apk-build.bat`, and the scoped `ui.map` unit run including the new test. Owed: §7's device pass,
and any commit or deploy — the user's.
