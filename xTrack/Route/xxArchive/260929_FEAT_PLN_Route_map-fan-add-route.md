<!-- scope: feature -->
# The map's add-route fan — replacing the Add Zone square

**Feature:** Route (with ArcLayout for the fan framework) · **Branch:** `feature/add-marker-trigger`
**Status:** settled and reviewed — §9's findings are all folded and its one blocking gap, R4, closed by the
read it owed; still **nothing built**. Two things were settled by the user's word of 2026-09-29: the square
becomes a fan, and the **parent opens the fan** instead of acting.

## 1. Where it sits

The square being repurposed is the `cm` control standing directly above the layer fan
([`MapScreen.kt:4082`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:4082)), which calls
`onAddZone(mapCenter)` ([`MapScreen.kt:2382`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2382)). Markers no longer depend on it: commit `12b8338`
gave creation a door at the head of both markers headers, so this swap closes the loop that plan was opened
for and deliberately left to the user.

The swap costs a `ControlId` entry — the enum's own comment invites it
([`MapControls.kt:39`](../../app/src/main/java/ykws/android/maro/ui/map/MapControls.kt:39)) — and leaves the layer fan untouched at its six children. The
stack already handles two fans: `expandedFanId` and `anyFanOpen` already fade every non-expanded control
([`MapScreen.kt:4046`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:4046), `:4074`), and the scrim closes whichever fan is open.

## 2. The arc's five actions

The user's list, with the phases it is gated on. The mode's phases are the epic's own: **off**, the
**acquisition** (a search running, then a line standing, selected or not) and **following** (a route selected,
where the panel is gone and the ordinary dashboard returns — R58).

| Action | off | acquiring, no line | acquiring, line unwritten | following, unwritten | following, written |
|---|---|---|---|---|---|
| Route (auto) | ✅ | — | — | — | — |
| Route | ✅ | — | — | — | — |
| Save to track | — | — | ✅ | ✅ | — |
| Save to track and exit | — | — | — | ✅ | — |
| Discard (unasked) | — | ✅ | ✅ | ✅ | ✅ |

Why those cells: the two Route entries are the arming doors, and an arming belongs to Idle alone (R65 — one
answer per arming, the acquisition entered from Idle); Save mirrors the panel's own face, greyed once the
selected line is written (R55/R57); Save-and-exit belongs to the following phase, because inside the
acquisition "exit" is the phase move Cancel and asks nothing (R63); Discard stands wherever a line can be
left, from the search itself to a written route.

## 3. Icon suggestions

The app's rule: a Material Symbol not already in the Compose libraries ships as a standalone vector in
`ui/icons/[IconName].kt` ([`docs/material-icons-standalone-guide.md`](../../docs/material-icons-standalone-guide.md:15)). The route family has no glyph
in the tree — `ui/icons/Route.kt` was deleted as dead in the 2026-09-28 pass — so every mark below is a fresh
file except where noted.

| Action | Recommended | Why | Alternative |
|---|---|---|---|
| Parent | `route` | The user's word of 2026-09-29: the anchor wears the mode's own subject — the polyline the app draws — in the stack's own near-white in **all three faces**, so nothing of the state is the glyph's to carry and the dot alone says it | `add_road` (*add route*, a road with a plus: the first pass's choice, withdrawn by the same word) |
| Route (auto) | `bolt` | Nothing in the route family says *automatic*; the bolt says "taken the instant it exists" | `alt_route` (a forked path — nearer *candidate* than *automatic*) |
| Route | `route` | Two waypoints and the polyline between them: the app's own subject, and the pair's unmarked half | `directions` |
| Save to track | `save` | The universal mark; the app has no save glyph yet | `bookmark_add` (a save that states *into a collection*, which a track is) |
| Save to track and exit | `logout` | The leaving half is what has to be legible beside `save`, and no Material glyph carries both | `done_all` (finish; the leaving half lost) |
| Discard, unasked | `cancel` | The user's word of 2026-09-29 — *cancel* names stopping the route rather than destroying it, and its filled disc is not the bare `close` the fan's own dismiss gesture would answer to | `delete`, the first pass's own mark for a loss |

**The arc's own order is the user's word of 2026-09-29, read top to bottom on the screen**: `Route (auto)`
leads, `Route` follows it, then the two saves and the loss, and the parent is the anchor rather than a slot in
it. **The sequence is the screen's, not the list's**, and the difference is the fan's own geometry:
`FanDirection.LEFT` fixes `baseAngleDeg` at 270° ([`FanConfig.kt:65`](../../app/src/main/java/ykws/android/maro/ui/map/FanConfig.kt:65)), so with five children at a 36°
step index 0 lands at the arc's **bottom** (198°) and index 4 at its **top** (342°) — the children list
therefore runs **bottom to top** (`cancel` first, `bolt` last) for the arc to read `bolt` · `route` · `save` ·
`logout` · `cancel` downwards on the screen. The first pass wrote the list in the reading order, which drew the
arc upside down; this note is here so the mirroring is never re-derived.

Two consequences worth stating: the arc is icon-only today, so `Save` and `Save and exit` sit **side by side in
that order** and are told apart by the pair, not by either glyph alone; and if the device shows the pair being
confused, the fallback is a label under each child, which is a `FanLayout` change rather than an icon change.

## 4. What the framework owes

Verified by reading, not assumed:

- **No per-child enablement exists.** [`FanLayout.kt:72`](../../app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt:72) passes each child only `isActive`, and
  [`MapControlButton.kt:22`](../../app/src/main/java/ykws/android/maro/ui/map/MapControlButton.kt:22) takes no `enabled` at all. An enablement matrix therefore needs
  `FanLayout` to carry per-child enabled state and `MapControlButton` to gain `enabled` — a shared control used
  by every map button ([`FanLayout.kt:165`](../../app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt:165), `:183`, [`MapControls.kt:282`](../../app/src/main/java/ykws/android/maro/ui/map/MapControls.kt:282)). Drawing the disabled face in
  each child instead would leave the press live, so it is not an option.
- **A momentary fan is already possible.** `toggleChildren` and `stayOpenAfterToggle` exist
  ([`FanLayout.kt:45`](../../app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt:45)), so the route fan sets `toggleChildren = false`, drops
  `showActiveBadge`, and closes on a press.
- **Geometry adapts with the count.** Five children give `effectiveTheta = 36°` in full-arc mode, so the radius
  comes out a little tighter than the layer fan's six — no config beyond `maxCount`/`currentCount`.
- **The parent keeps its own face.** R51's three faces (off · acquiring, the route's green · navigating, the
  palette's blue, each with the pulsing dot) can stay on the parent, which now states the mode where it used to
  state it by arming.

## 5. Rules this rewrites

These are Route's own rules and the master book's R-numbers, not new opinion — each is a contradiction to
resolve, not to slip past:

- **R49 · R50 · R73** — the map's square *is* a door onto the single arming trigger, resolving the standing pair
  and arming at once, opening no drawer. A parent that opens a fan is none of that: the arming moves to the
  first child, and the "one door, arms at once" sentence needs restating as *two arming children behind one
  anchor*.
- **The single-mode habit** — the route toggle and the inspect toggle are mutually exclusive, and the exclusion
  currently hangs on the square's press. It moves to the child that arms.
- **R59 · R60** — leaving the following phase asks first, by any of its three doors. `Discard (unasked)` adds a
  **fourth door that does not ask**, deliberately: the fan becomes the shortcut surface for the exit dialog's
  own two outcomes, and the dialog stays for the toggle's off and the back key. The risk is named in §6.
- **R55 · R57 · R63** — the panel's grid and the dialog's affirmative hold the phrases the fan's cells mirror;
  the fan must not become a third place where "written" is decided.
- **Nothing at all covers auto-pick.** "Route (auto)" is a new behaviour: arming with the intent to select the
  first answer the instant it exists.

## 6. Open, with a recommendation each

1. **Resolved 2026-09-29 — what "auto" picks.** The settled line, at index 0 of the drawn set (D6): it arms as
   `Route` does, the first line to answer is selected without a press, and the panel stands while the search
   runs and gives way the instant that selection lands (R58). The objection stands as written — the candidate
   rows exist so the user compares (R62, R74), and auto-pick is the deliberate refusal to, which is why it is
   never the default child.
2. **The unasked discard.** Recommendation: keep it, and amend R59/R60 to name the exception — the fan's
   Discard asks nothing, and the loss it can take is an **unwritten** route. The objection: an unasked door
   over an unwritten line is a real data-loss path, and the only thing standing between the user and it is the
   arc's own legibility.
3. **Resolved 2026-09-29 — no colour in the fan at all.** The user's word, in two passes: every glyph in a map
   button wears the stack's own icon colour, so the arc's Discard lost the danger red this point had recommended
   **and the parent's glyph followed it** — the anchor wears the same near-white in all three faces, "all white
   all the time", the state no longer being the glyph's to carry. The mode's read therefore lives on the
   **pulsing dot** and on the map's own route toggle, which keeps R51's green and blue because it is a square of
   its own rather than this fan's anchor. The dialog's `Discard route` stays red, being a dialog action and not a
   map button, and the **disabled face stays the fan's 0.25** strength by the same word.
4. **Labels.** Recommendation: icon-only, as every fan child is today; the labelled child is the fallback if
   the two route glyphs prove confusable on the device.
5. **The parent's badge.** Recommendation: none — an active badge counts toggles, and nothing here toggles.
6. **Resolved 2026-09-29 — the parent's mark.** `route`, in all three faces, by the user's word: the anchor
   wears the mode's own subject in the stack's own near-white, and the faces are told apart by the **pulsing dot
   alone**, so the glyph carries no face's meaning at all. The waypoint mark therefore stands on the parent
   **and** on the second child, which is the change's one cost and a device question; if the two read as one
   mark, §7's label fallback is the untouched answer.

## 7. Not in this discussion

- The repurposing of `onAddZone`'s own removal from `MapScreen` — that is the companion change, and it is what
  frees the square.
- The candidate engine's own behaviour, the fine band, the device pass — all Route's standing walk items
  (cursor item 3 is stale; the first open item is 13).
- Any change to `MarkerCreateAction`, committed today as `12b8338`.

## 8. Plan

### 8.1 Decisions

**D1 — The square is replaced, not joined.** `Add Zone` leaves the `cm` slot and the new parent takes its place,
so `onAddZone`'s plumbing goes with it ([`MapScreen.kt:2382`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2382), the parameter at `:3688`, the argument at
`:4082`) and [`AddLocationAltIcon()`](../../app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt:140) is deleted if the removal leaves it with no
caller — verified that [`MarkerCreateAction`](../../app/src/main/java/ykws/android/maro/ui/components/MarkerCreateAction.kt:1) draws the standalone vector, not the wrapper.

**D2 — The parent opens the fan and states the mode** (the user's word, 2026-09-29). The arming moves to the
first child, and R51's three faces stay on the parent — off, acquiring with the pulsing dot, navigating with it
— so the mode is still readable where it is read today.

**The two fans fade for each other, and the rule is not the one standing next to it.** The Add Zone line fades
on `anyFanOpen` alone ([`MapScreen.kt:4084`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:4084)), while the layer fan fades only when
**another** fan is open (`anyFanOpen && !isExpanded`, [`MapScreen.kt:4074`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:4074) through `:4080`). A new
parent that copied the line being removed would vanish the instant its own fan opened, so each parent takes the
layer fan's form: hidden while the *other* is open, standing while its own is.

**D3 — The fan is momentary.** `toggleChildren = false` and `showActiveBadge = false`, and a child's press
closes the fan by the caller clearing `expandedFanId`, which is how the layer fan's own parent click already
works. `stayOpenAfterToggle` is left alone for the toggle fan that owns it. **The children must also ignore
`isActive`**: [`FanLayout.kt:158`](../../app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt:158) derives it as `activeStates.getOrElse(i) { config.toggleChildren }`, which is
`false` for every child of a momentary fan, so the layer fan's own `alpha = if (isActive) 1f else 0.25f` idiom
would dim all five glyphs. The route fan passes no `activeStates`, and its children draw at full tint and read
**enablement** instead.

**D4 — Enablement is per child, and the framework owes it.** Read, not assumed: [`FanLayout.kt:72`](../../app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt:72) passes a
child only `isActive`, and [`MapControlButton.kt:22`](../../app/src/main/java/ykws/android/maro/ui/map/MapControlButton.kt:22) takes no `enabled`. So `MapControlButton` gains
`enabled: Boolean = true` and `FanLayout` gains `enabledStates: List<Boolean> = emptyList()`, which suppresses
that child's press and lets it draw the disabled face; both defaults keep the layer fan and every other caller
untouched. Drawing a grey child with a live press is not an alternative — it is a defect. **The disabled face
is the inactive alpha** (`ButtonColors.inactiveAlpha`, 0.25) with the press suppressed: that reading is
unambiguous here precisely because nothing in this fan toggles, so a quarter-strength glyph cannot mean *off*
the way it does in the layer fan beside it.

**D5 — The five children map to seams that exist.** Route → `armRouteMode()` ([`MapScreen.kt:2492`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2492)); Route
(auto) → the same arming plus D6's flag; Save to track → `saveRouteTrack(front, routePinned)` (the dialog's own
path, [`MapScreen.kt:3505`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3505)); Save to track and exit → that save, then `endRouteMode()`;
Discard (unasked) → `endRouteMode()` alone, which is the dialog's third action without its question.

**D6 — Auto-pick takes the settled line, through the panel's own seam.** The user's words are *the first route
as soon as it is computed*, and the drawn set makes that exact: [`routeCandidateLines`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:82) puts the
**settled** line at **index 0** and appends the engine's offers behind it, so the set holds one entry the moment
an answer lands and grows only as the background job publishes ([`RouteOverlay.kt:70`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:70)). The one-shot therefore
fires on the first `Choosing` whose `plan != null` ([`RouteViewModel.kt:239`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:239)) and calls
[`selectRoute()`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:642) **unchanged** — nothing waits for an offer, no selection path is added, and the shell's own
follow hand-over takes it from there. The flag clears on the selection, on an end and on a new arming, and it is
never the default child.

**D7 — One derivation feeds the matrix.** The shell computes the five booleans from `RouteState.phase`
([`RouteViewModel.kt:208`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:208)), `routeArmed`, `candidates` and `isRouteSaved`
([`RouteViewModel.kt:683`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:683)), in one place, so §2's table is stated once in code.

**D8 — The exit dialog keeps its three doors.** The fan's Discard is the fourth and the only unasked one; the
toggle's off and the back key still raise the dialog.

**D9 — The master book goes first.** This repo's own habit: a change to the delivery is a change to the
requirements first, and [`260922_FEAT_PLN_Route_ask-policy-and-target-validity.md`](260922_FEAT_PLN_Route_ask-policy-and-target-validity.md)
is their home and wins any conflict with the epic. This pass amends **R49 · R50 · R51 · R59 · R60 · R73** and
adds the fan's own rows, continuing the numbering from R74.

### 8.2 Steps

1. **The master book** — the six amended rows and the new ones for the fan, its five actions and the auto-pick,
   written before the code.
2. **The icons** — six standalone vectors into `ui/icons/` by [`docs/material-icons-standalone-guide.md`](../../docs/material-icons-standalone-guide.md:15),
   or a core icon where one already carries the mark.
3. **The framework** — `MapControlButton`'s `enabled` and `FanLayout`'s `enabledStates`, with the disabled face
   and the suppressed press.
4. **The fan in the stack** — `ControlId.ROUTE_FAN` ([`MapControls.kt:39`](../../app/src/main/java/ykws/android/maro/ui/map/MapControls.kt:39)), the parent in the `cm` slot, and a
   `FanConfig(maxCount = 5, currentCount = 5, direction = LEFT, isOpen = …, toggleChildren = false,
   showActiveBadge = false)` with the five children and their `onChildClick` dispatch.
5. **The swap** — the Add Zone button, its parameter, its argument and any icon left dead by the removal.
6. **The enablement** — D7's one derivation, feeding `enabledStates`.
7. **The auto-pick** — D6's flag and its one-shot, keyed on `plan != null` and selecting index 0 through the
   panel's own `selectRoute()`, with a test beside `RouteAcquisitionTest` asserting the line taken is the
   settled one and that no offer is needed.
8. **Strings** — the parent's and the five children's `contentDescription`s in both locales.
9. **The record** — the epic's toggle and exit rules ([`FEAT_DSC_Route.md:62`](xTrack/Route/FEAT_DSC_Route.md:62), `:63`), the ArcLayout epic's
   `## Implemented` note that "Add Zone above fan" is superseded, and any doc that states the stack's
   arrangement. Read each before writing, and amend only where it states this surface. **This capture joins
   the epic's `## Docs` in the same pass** — the list's newest entry is still 2026-09-28's — and the epic's
   front-matter date moves with it.
10. **Verification** — `apk-build.bat`, the route-filtered suites plus the new auto-pick case, and the device
    pass over the arc: five children, their enablement per phase, the close on press, and the parent's faces.

### 8.3 Guards

- **Nothing else in the stack moves.** The layer fan keeps its six children, and the zoom and menu squares are
  untouched; the new `ControlId` entry is the only addition.
- **The panel and the dialog keep their authority.** The fan mirrors their outcomes; it never becomes a third
  place where "written" or "selected" is decided.
- **The epic's Key Files rows** for `RouteViewModel` and `RouteConfirmPanel` still describe the pre-R65 grid
  (`Reroute` · `New route`), which R65 removed; this pass corrects only what it rewrites, and the staleness is
  named here so it is not mistaken for this change's.
- **No dependency**, no new `maro.properties` key, and the parent's own appearance stays with the tokens the
  stack already uses.
- **Three removal sites, not one** — the button ([`MapScreen.kt:4082`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:4082)), the argument ([`MapScreen.kt:2382`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2382)) and the
  `onAddZone` parameter in the controls composable's own signature ([`MapScreen.kt:3688`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3688)).

## 9. Review — 2026-09-29, verdict **revise**, every finding folded the same day

Self-review in the challenge-shaped form: the reviewer wrote the plan, so this reads against it.

- **R1 · corrected — a defect, not a preference.** The plan had the new parent taking the removed Add Zone
  button's slot with nothing said about the fade, and the two lines differ: the old button faded on
  `anyFanOpen` alone. Copied as-is, the parent would disappear the moment its own fan opened. D2 now carries the
  layer fan's own form.
- **R2 · corrected — the arc would have drawn dimmed.** [`FanLayout.kt:158`](../../app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt:158) resolves `isActive` from
  `config.toggleChildren` when no `activeStates` is given, so a momentary fan hands every child `false` and the
  established `alpha = if (isActive) 1f else 0.25f` idiom dims all five glyphs. D3 now says the children ignore
  `isActive` and read enablement.
- **R3 · corrected — the disabled face had no token.** D4 demanded a disabled child without saying what
  disabled looks like; it is now the inactive alpha with the press suppressed, which is unambiguous only
  because nothing in this fan toggles.
- **R4 · closed 2026-09-29 — the auto-pick's target, read rather than assumed.** The finding was right to hold
  the step: `selectRoute()` selects out of `_candidates`, and nothing in the plan established that the computed
  route is a member. The read answers it favourably and exactly — [`routeCandidateLines`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:82)
  returns `settled` first and the offers behind it, with index 0 the settled line the moment an answer lands —
  so the panel's own `selectRoute()` is the seam and nothing new is written. Had the set excluded the settled
  line, the fallback was `RouteState.Following(settled)` written directly on the worker's own hand-off rule.
- **R5 · corrected — the plan omitted registering itself.** The epic's `## Docs` newest entry is 2026-09-28's;
  step 9 now carries the row and the date, the same debt the marker plan's own review caught as F4.
- **R6 · noted — the swap's sites.** Three, listed in §8.3, so the removal is not mistaken for a one-line
  delete.
- **Sweep of the five covered classes.** No dependency was added; no machine-shaped data file was opened; the
  capture was written only under the discuss gate's own exception for one `FEAT_PLN_` file, on an explicit
  "plan, report, discuss"; the device was not touched; and every claim above carries a file read behind it —
  R4's premise included, now that its read is made and its answer folded into D6.

## Outcome

Built 2026-09-29 on `feature/add-marker-trigger`, from an `#implement` run whose own review finding was folded
before the pointer landed.

- **As designed:** the Add Zone square left at all three of its sites and `AddLocationAltIcon()` went with its
  last caller; `ControlId.ROUTE_FAN` took the slot with a parent that opens the arc, arms nothing and states
  the mode; the five children are momentary, close the fan on a press, and are gated by one derivation;
  `MapControlButton` gained `enabled` and `FanLayout` `enabledStates`, both defaulted; the auto-pick fires on
  the first `Choosing` carrying a plan and takes index 0 through `selectRoute()`; the master book took the six
  amended rows and R75–R80.
- **The arc's order, its loss's glyph and the parent's mark were all settled by the user's word after the first
  build (2026-09-29)**: `bolt` leads and `route` follows it — the auto child first — the unasked Discard wears
  `cancel` in place of `delete_forever`, so the mark reads *stop the route* rather than *destroy*, and the
  parent wears `route` in all three faces, the same word withdrawing the first pass's `add_road` and leaving that
  vector with no caller. **The order is read on the screen** (§3), which the first application missed: the list's
  own order is the arc's reverse, because index 0 sits at the bottom of a left-facing fan.
- **The fan lost its colour, glyph by glyph, on the user's word the same hour**: the Discard child wore
  `AppConfig.semanticDanger` and now wears `ButtonColors.icon` like every other map glyph, §6.3's danger-red
  recommendation withdrawn with it — and then the **parent's state-coloured glyph followed**, so the anchor wears
  the same near-white in all three faces and the fan carries no hue at all, the mode's read being the dot's and
  the map's own route toggle's. The **disabled face is untouched**: 0.25 is the fan's own strength and the user's
  word keeps it, which is what the ghosted look on an unarmed `Discard` actually was.
- **Deviations, both declared and accepted:** only **two new string keys**, the other four children reusing the
  keys their acts already own — one home for an act's word, which is what [`AGENTS.md`](../../AGENTS.md:1)'s own
  rule asks for, so step 8 is met in substance rather than in count; and the parent's three faces ended up told
  apart by `MapPulseDot` alone, the glyph wearing the stack's icon colour in all of them, because
  `MapControlButton` carries one skin for every square in that stack.
- **The folded finding:** the derivation first gated the two arming cells and `Discard` on `RouteState.phase`,
  which is the machine's own reading while those doors gate on `routeArmed` — a `rememberSaveable` with no
  restore effect — so after process death with the mode armed, and on the arming frame, the arc drew live cells
  that did nothing and a dead `Discard`. They now read `routeArmed`, the flag their doors read
  ([`MapScreen.kt:1825`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1825)'s `if (routeArmed) return`,
  [`MapScreen.kt:1860`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1860)'s `if (!routeArmed) return`), and the now-dead `idle` local went with it.
- **Verification:** `apk-build.bat` BUILD SUCCESSFUL with the APK packaged; **18 route-filtered suites, 175
  tests, 0 failures**, `RouteAutoPickTest` 4/4. No JVM suite reaches a Compose arc, so the enablement, the fade
  and the disabled face are gated by the compile and the suites alone.
- **Owed, the user's:** the two-fan fade, the 0.25 face's legibility across five glyphs, the close on press, the
  parent's three faces, the fifth child's tighter radius, and the auto-pick's panel-to-dashboard hand-over.
- **Two points the run left to judgement rather than settling alone:** whether the parent should wear the mode's
  faces at all now that `RouteToggleButton` keeps its own — the mode would then read twice — and whether the
  two-key reduction meets step 8 as written (answered above, in substance).
- **Still open from §6, all preferences:** the discards' colour, whether the arc ever carries labels, whether
  the parent's badge returns, and whether the parent's glyph follows its face instead of staying `add_road`.
