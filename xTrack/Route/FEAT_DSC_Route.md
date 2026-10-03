---
name: Route
status: active
created: 2026-08-16 10:44
modified: 2026-10-03 11:30
---

# Feature: Route

State doc — what the mode is, what the code does today, and what is left. Read it as the code's own description: the requirement list in
[`xxArchive/260922_FEAT_PLN_Route_ask-policy-and-target-validity.md`](xxArchive/260922_FEAT_PLN_Route_ask-policy-and-target-validity.md:1) is
**history**, retired to the archive on 2026-09-30 — on 2026-09-29 the user's word dropped or corrected a large part of it (the aim and its
crosshair, the reroute, the ladder, the camera rules, the interface parts the current flow does not use, and the settings that already ship) —
and the numbered requirements added since live in this file's own `## Requirements` section, which the archive's index names their successor.

## Concept

Set a destination and have the app draw the route from the boat's position to it, then save that line as an ordinary track.

- **The two ends are the drawer's** — the hamburger menu's Route sub-section under Navigation holds one dropdown per end, persisted per navigation mode; the start offers the current position, the marker position and every marker flagged as an origin, the destination the marker position and every marker flagged as a destination.
- **An end is read when the action is pressed** — where either end is the current position or the marker position, its value is the corresponding point **at the time of the route action**, and nothing re-reads it afterwards.
- **Arming computes at once** — the drawer's action and the map's route fan, whose `Route` and `Route (auto)` children open the same trigger on the pair that stands when they are pressed, are the doors; the fan's parent only opens the arc. Nothing re-asks afterwards, so one arming holds one answer and no line is ever replaced from inside the mode.
- **The engine offers three routes at once** — the ladder computes three fixed aversions (around · balanced · through) as separate lookups over one shared grid, and each row prints its own distance and ETA.
- **The drawing is the selected line, with the others dimmed further** — the selected line is drawn at the route's own transparency during the acquisition and once confirmed, and every line beside it wears the one shared dimming key. **No ladder of superseded lines exists.**
- **The acquisition owns three outcomes** — `Save to track` (the selected line, greyed once written), `Select route` (navigation on it, the other routes dropped) and the red `Discard route` (leaves, asking nothing). Once selected there is no panel at all: the ordinary dashboard returns, and the mode's presence is the line, the toggle and the one exit dialog `Save Route to Track · Continue route · Discard Route`.
- **Speeds come from where the line is** — in open water the pace is the free-water pace setting; at a point inside the 300 m band or a regulated speed zone the speed is that limit, the strictest one where both hold, and the clock follows the limit in force. The band's own limit is read **whatever its price switch says**: `route.avoid.zone300.enabled` prices water, it never suspends a limit. The **dummy's fixed 15 kn** is the placeholder's own fiction and the one place this does not hold.
- **An end the boat stands on may need moving** — where the origin or the destination is the boat's own position and that point is not valid water (land, or shallower than the minimum depth), the point is **moved to the nearest valid water on the sea side**. This replaces the refused-end crosshair, whose mark, beat and three `route.target.*` keys leave with it.
- **A saved route is an ordinary track carrying the `route` flag** — listed, exported, drawn and deleted like any other.

## Current state of the code

- **The seam** — [`RouteEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngine.kt:66) publishes `progress: StateFlow<RouteProgress?>`, one emission carrying the stage the pipeline has entered and the line it holds, cleared on every answer and every abort; readiness (`NotReady` · `Ready` · `Unavailable(reason)`), the two position entry points, the validity question and the readiness promise sit beside it, and their **relevance is under review** — the current flow arms on the drawer's pair and calls them only as the pipeline needs.
- **Two engines ship** — the `dummy` is one straight segment at its own fixed 15 kn, ready on construction, judging nothing and answering a null progress flow ([`RouteDummyEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteDummyEngine.kt:65)); the `avoid` crosses five boundaries — corridor · grid · search · pull · snap — publishing each one's stage and geometry, the raw cell chain at pull and the pulled line at snap ([`RouteAvoidEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:1623)). `route.engine.id` names the shipped one and an unclaimed id falls back to it.
- **The avoid engine's world is ten files** — tagged costed cells, an 8-neighbour A\*, a source-parameterized taut pull, a berth carve, one cost field whose base is always set and whose sources only add, the fairing fitter, the clock that obeys the limit in force, the tangent corners and the zone geometry ([`spatial/avoid/`](../../app/src/main/java/ykws/android/maro/spatial/avoid)).
- **The view model is the mode's whole state** — Idle · Choosing · Following with no arrival state, one worker per ask, the plan and its `remainingFrom` projection, the session's route-to-track link and the save predicate ([`RouteViewModel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:349)).
- **One file touches osmdroid** — the pool and the pin are attached once and mutated in place, and the paint order is applied as a rank over the whole list so nothing is pinned by position ([`RouteHost.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt:69), [`OverlayZOrder.kt`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayZOrder.kt:85)).
- **The panel is the acquisition's whole surface** — the stage rides the header's acquiring word, the sentence line carries a refusal or the no-route word, the selected line's details sit on the shared reading cell with the pin under them, and the rung rows carry next/prev over three bottom-anchored actions ([`RouteConfirmPanel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:102)).
- **The feature's pure rules have one home** — the rung set the selection walks, the collapse dispersion and the rung mapping, the auto-pick's one-shot, the next/prev wrap, the trip figure and the point's printed form ([`RouteOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:54)).
- **Every value lives in the properties file** — the line's colour, transparency and width, the shared dimming key, the navigate colour, the pin and the avoid family's margins, gates and prices, all read through [`AppConfig`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:126); the app's own rows are the engine dropdown, the free-water pace and the **Active route** colour.
- **Still in the code and on the removal list** — the refused-end crosshair with its `route.target.*` keys (replaced by the sea-side move above), the route opacity-ladder keys `tracking.transparency.routeFrom` / `routeTo` (the dimming key covers the need), and the seam members the current flow does not lean on, which the user will re-evaluate.
- **Known limits** — the dummy promises nothing about water and no setting moves it; the progressive-draw plan's build order still names `route.progress.transparencyPct`, retired on 2026-09-28 for the shared `route.dimmed.transparencyPct`, so the plan's text is the stale one and not the tree; and `route.avoid.fine.cellRatio`'s parse comment and its property now agree that the fine pass reads it.

### Placement and the three seams

The route sits in the app's own slicing rather than in a package of its own, and the isolation survives as three seams on shared files:

1. `MapScreen.kt` — the engine resolved at one expression, one `RouteHost(...)` call, the panel composed into the dashboard slot, the exit doors.
2. The drawer — the Route sub-section and the read-only mode summary, both inside the Navigation card.
3. The Settings overlay — the engine's row, the free-water pace and the **Active route** colour, held by `SettingsManager`.

```text
data/model/    RoutePoint.kt, RouteResult.kt
data/route/    RouteEndSelection.kt, RoutePace.kt
spatial/       RouteEngine.kt, RouteDummyEngine.kt, RouteEngineChoice.kt, SpatialOperations.kt, Units.kt
spatial/avoid/ AvoidWorld.kt, AvoidGrid.kt, AvoidSearch.kt, AvoidPull.kt, BerthCarve.kt,
               RouteCostField.kt, RouteCurveFitter.kt, RouteEta.kt, TangentCorners.kt, ZoneGeometry.kt
ui/map/        RouteViewModel.kt, RouteHost.kt, RouteOverlay.kt, RouteConfirmPanel.kt, MapPulseDot.kt
```

### The mode's flow

```text
Idle → a door armed on the standing pair → Choosing (the search runs; the panel and the provisional line)
Choosing → Select route → Following (the line, the toggle, the one exit dialog)
Choosing → Save to track / Discard route → Idle
Following → the toggle's off or the back key → the one dialog → save and exit · continue · discard
```

Arrival carries no state and no cue: it is the trip cell reading zero while the line stays drawn.

### Decoupling rules

1. Route depends only on `data/model`'s `LatLng`, `RoutePoint` and `RouteResult`, the shared `spatial/Units.kt` and `SpatialOperations`, and the `RouteEngine` seam.
2. An engine that wants the water reads it through **its own** world interface; the dummy reads none at all.
3. The feature declares no conversion constant, no baked artifact and no dependency of its own, and the zone and band values stay the RegulatedZones layer's.
4. All Route runtime state lives in `RouteViewModel` (`StateFlow`), and `RouteHost` is the only file touching osmdroid.

## Delta

**Owed builds**

- The trigger's read of the pair hoisted above the readiness test, so both the ready path and the retry arm on the pair the press resolved → [`260929_FEAT_PLN_Route_trigger-read-at-press.md`](260929_FEAT_PLN_Route_trigger-read-at-press.md).
- **The new invalid-end behaviour**: where the boat's own position is the origin or the destination and is not valid water (land, or shallower than the minimum depth), move that point to the nearest valid water on the sea side.

**Owed removals**

- The refused-end crosshair: its mark and beat, `route.target.color` · `route.target.widthDp` · `route.target.pulseMs` in [`maro.properties`](../../app/src/main/assets/maro.properties:114) and their [`AppConfig`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:189) accessors, the refused branch of the aim ring in [`RouteHost.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt:416), and the refusal state the view model keeps for it.
- The route opacity ladder: `tracking.transparency.routeFrom` / `routeTo` and any row reading them.
- The seam members the current flow does not use — parked for the user's re-evaluation rather than deleted blind.

**Owed device passes** — the Phase 6 fairing with its four keys and the GPX acceptance; the route fan's arc, enablement and auto-pick; the acquisition's face, the toggle's door and the paint order; the progressive draw's staircase and its 55 % face; the confirming pass after the paint-order repair and the **Active route** colour row; the acquisition panel's paging table with its early select and early save.

**The progressive draw's open points** — the flicker on a sub-second ask, the angular staircase, the `PULL` boundary's mapping cost unmeasured against the ≤ 500 ms budget, and whether the aim beat should stop while a partial line draws, with its retry pin and the device reading owed ([`260925_FEAT_PLN_Route_progressive-draw.md`](260925_FEAT_PLN_Route_progressive-draw.md:112)).

**Parked under a future algorithm improvement** — Phase 5's marker weights merged with the shipped marker scale into one scale, taken with the review over Phase 6's folded fixes; and the fine band's Change 4, whose width and mechanism the code and the walk disagree about (the engine already re-solves at the ratio).

**The open walk's remaining items** — 14 the marker weights merged with the review over Phase 6's folded fixes and 15 the fine band's Change 4, both **parked**; items 16–21 were closed or dropped on 2026-09-30 and the level's own note carries them.

**The record** — the Route row in `## Feature Summaries` is longer than the tools' 2 000-character line cap, so it can be neither read back nor rewritten; the remedy is a shorter row ([`GLOBAL_CONTEXT.md`](../GLOBAL_CONTEXT.md:89)), the item that carried it having been dropped on 2026-09-30 with this note kept in its place.

## Requirements

The live numbered requirements — added after the master book was retired on 2026-09-30; the archived book's own R1–R81 are its history and this section continues the numbering from R81. The code cites these numbers by ordinal, so their home is here.

- **R82 — A saved route's two ends persist on the track and its summary** (2026-09-30) — `Track.routeStartMarkerId` and `Track.routeDestinationMarkerId` at proto 20/21, an **empty string** meaning *this end was not a marker*, and the index pass projects both into `TrackSummary` beside `route` so the acquisition's match reads them **without loading a track**. Only a flagged-marker end carries an identity — `CurrentPosition` and `MarkerPosition` leave the strings empty — and **no index version is bumped**, an absent string decoding to the honest *no marker recorded*, so routes saved before the field never match, by construction.
- **R83 — An acquisition whose resolved ends are the same two markers pulls the stored line back instead of searching** (2026-09-30) — `armRouteMode` matches the armed pair over the summaries (`route` on, both ids equal, newest `startTimeMs` first), loads the track, rebuilds the plan through the shipped `routePlanOf`, and arms with it: **no `routesToCompute`, no `startLookup`**. The stored branch seeds one settled page carrying the plan instance the session links, so `Choosing.start` is the **line's own first point**, and the match skips the short-pair guard the search alone keeps.
- **R84 — The match is directional and flagged-marker-only** (2026-09-30) — the start id is compared to the stored start field and the destination id to the stored destination field, never crossed, so a stored A→B line belongs to A→B; the opposite arm takes the same line **mirrored** rather than refusing the swap, and the limit is that only a **double-flagged** pair — each marker carrying both `routeOrigin` and `routeDestination` — admits it (R86). A `CurrentPosition` or `MarkerPosition` end is never stored or matched, an end being identified by a **marker** and not by a point.
- **R85 — A re-acquired line's save door stays shut** (2026-09-30) — the matched track id is what the rebuilt plan is already written as, registered in the mode's session at arming, so `isRouteSaved` reads true with **no new predicate** and the save writes nothing a second time.
- **R86 — The reverse pair is matched and mirrored** (2026-09-30) — **extends R84's directional rule** with the return trip: the swap is answered by **mirroring the line**, not by refusing it, so a stored line whose marker pair is the armed pair **reversed** is taken at its own stored figures rather than searched, an end pair being two markers and the water between them the same water the other way. `storedRouteMatch` runs a second pass after the exact pair finds nothing, newest `startTimeMs` first, and answers it with `reversed` set; `mirroredPlanOf(track, nowMs)` is **exactly the forward inverse with its lists reversed** — `routePlanOf`'s points and leg times reversed, so mirrored leg *k* is stored leg *n−1−k* and the stored milliseconds carry over with no pace arithmetic redone — and it returns null under two points.
- **R87 — A mirrored line is a new plan with an open save door** (2026-09-30) — it takes the **arming instant** for its name and registers no session link, because the track that exists holds the other direction: `armRouteMode` passes a **null track id**, so `isRouteSaved` reads false and saving writes a new track.
- **R88 — A route is selectable while its line is still drawing** (2026-10-02) — `Select route` opens once the main lookup has a drawable partial line (≥ 2 provisional points), and the press **commits** the main instead of freezing it: the candidate lookups are cancelled, the main keeps running, and the mode enters `Following` with the landed line the moment it lands; a refusal un-commits rather than following.
- **R89 — A route is saveable while its line is still drawing** (2026-10-02) — `Save to track` opens on the same partial line, writes it immediately, and **re-saves the same track id at each main iteration** — a fixed id, `createdAtMs` and name, only the points and legs growing — until the landed full line is written once more and the session links the landed plan to the draft, shutting the save door.
- **R90 — The acquisition panel is the shared dashboard scaffold with a paging summary table** (2026-10-02, amended 2026-10-03) — the panel rides [`DrawerScaffold`](app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt:149) with `showBack = false`, `wrapContent = !isLandscape` and `wrapContentMinHeight = portraitDashboardHeight`, so it auto-grows to its content instead of scrolling internally; the header carries the title and, at its trailing edge, the stage status and the ‹ › dots; the body is a bordered three-column table — the description (0.75 of the comparison column), the route's Dist · ETA as value · unit pairs (value right-aligned, unit left-aligned, a placeholder while it waits), and a candidate's delta against the main with the forced-crossing note — with a hairline between the columns, wrapping top-aligned rows, the selected row on the taken-choice face (a `ui.select.container` fill, its `ui.accent` edge, white bold text) whose corners adapt to the row's position, and the whole table paging laterally by swipe or the header's ‹ › pair; the top Dist · ETA display, the start/destination coordinates and the pin option are gone; the three actions — Save · Select · Discard — share one weighted bottom row, and the destination-moved note follows only while a plan stands.

## Todos

- [ ] **The pin's hiding flag** — the route pin is hidden with `isEnabled`, an idiom proven in this repo for `Polyline` only; should a device ever show a stale pin where no route stands, the pin's pre-refactor shape — created and added only when a plan exists — is the fallback.
- [ ] **The multi-route save, withdrawn 2026-09-24** — the exit dialog no longer offers to write the session's routes; resolve later whether it comes back and, if it does, what names its files.

## Key Files

- `app/src/main/java/ykws/android/maro/spatial/RouteEngine.kt` — the seam: readiness, the two position entry points, the validity question, the readiness promise, and the `progress` flow carrying `RouteProgress(stage, points)`, cleared on every answer and every abort
- `app/src/main/java/ykws/android/maro/spatial/RouteDummyEngine.kt` — one straight segment at a fixed 15 kn, ready on construction, refusing nothing, a null progress flow
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — the five-boundary pipeline and its one `publish(stage, pass, points)`, the sources and their switches, the candidates' own lane, the forced crossing's names, the fairing and the clock
- `app/src/main/java/ykws/android/maro/spatial/RouteEngineChoice.kt` — an id, a label id and a factory per engine, resolved from the setting at one expression
- `app/src/main/java/ykws/android/maro/spatial/avoid/` — `AvoidWorld`, `AvoidGrid`, `AvoidSearch`, `AvoidPull`, `BerthCarve`, `RouteCostField`, `RouteCurveFitter`, `RouteEta`, `TangentCorners`, `ZoneGeometry`
- `app/src/main/java/ykws/android/maro/data/model/RouteResult.kt`, `RoutePoint.kt`, `RouteOffer.kt` — the domain: the polyline, the per-leg times, the length, the duration, the offers, and the refusals `OutsideWater` · `NoPath`
- `app/src/main/java/ykws/android/maro/data/route/RouteEndSelection.kt` — the eligible ends built from the marker flags and the fallback of a selection that stops resolving
- `app/src/main/java/ykws/android/maro/data/route/RoutePace.kt` — the pace reduction over samples outside the zones and the band
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — the state machine, the worker, the plan, the session link, the saved predicate and the progress proxy
- `app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt` — the one osmdroid file: the pool, `route_line` · `route_progress` · `route_target` · `marker_route_dest`, the back key routed to the shell's exit rule
- `app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt` — the toggle and the pure rules: `routeCandidateLines`, `routeAutoPickReady`, `routeStepIndex`, the trip figure and the ETA print, and the saved-route inverses — `routePlanOf` for a stored line and `mirroredPlanOf` for the return trip
- `app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt` — the acquisition's panel for the dashboard slot, specified with the other cards in `docs/ui-component-guidelines.md` §5.8
- `app/src/main/java/ykws/android/maro/ui/map/OverlayZOrder.kt` — `paintRankOf` and `TrackTier`, the rank that orders the whole overlay list
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the engine resolved and handed in as a selection, the progress collected and passed to both panel call sites, the one `RouteHost(...)` call and the exit doors
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` + `app/src/main/assets/maro.properties` — where every route value lives
- `app/src/test/java/ykws/android/maro/spatial/` — `RouteDummyEngineTest`, `RouteAvoidEngineTest`, `RouteEngineChoiceTest`, `PrebakedCoastline`, `CoastlinePointWalkTest`
- `app/src/test/java/ykws/android/maro/spatial/avoid/` — eight suites: stage 1, the cost field, the depth gate, the band's cost, the berth carve, the fairing, the zone phase 4, the tangent corners
- `app/src/test/java/ykws/android/maro/ui/map/` — `RouteAcquisitionTest`, `RouteEngineSeamTest`, `RoutePlanTest`, `RouteStoredMatchTest`, `RouteMirrorPlanTest`
- `app/src/main/java/ykws/android/maro/data/track/Track.kt` — the `route` flag and the two persisted end ids (`routeStartMarkerId`, `routeDestinationMarkerId`) a saved route carries
- `app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt` — the save that writes the two end ids on the built track
- `app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt` — the index pass that projects the flag and the two end ids into every summary

## Docs

- [`261002_FEAT_PLN_Route_functional-flow.md`](261002_FEAT_PLN_Route_functional-flow.md) — the early select, the early save's growing draft, and the acquisition panel's swipe pager
- [`260929_FEAT_DOC_Route_engine-interface.md`](260929_FEAT_DOC_Route_engine-interface.md) — **the implementation spec** for the engine interface and the flow's conversion onto it: the types, the repair, the reason set, the computations, the disposals and the build order, facts only
- [`FEAT_DOC_Route_avoid-algorithm.md`](FEAT_DOC_Route_avoid-algorithm.md) — **the avoidance algorithm's design of record**, folded from the archived phase and zone plans: the pipeline, the cost field, the λ loop, the standoff's retirement, the fairing and the evidence
- [`261002_FEAT_PLN_Route_aversion-and-slow-water-model.md`](261002_FEAT_PLN_Route_aversion-and-slow-water-model.md) — the slow-water model: the three-route ladder over one shared grid, the Driving-preference cursor, the tolerance-based collapse, and the retired candidate-pass apparatus
- [`260930_FEAT_PLN_Route_saved-route-ends.md`](260930_FEAT_PLN_Route_saved-route-ends.md) — the saved-route ends: the pair on the track and its summary, the directional flagged-marker match that replaces the search, the reverse pair taken mirrored, and the shut save door
- Thirty-one files are archived in `xTrack/Route/xxArchive/` with their index rows, and `#archive` is the only way into that folder

## Walk

**Level 1 — Date:** 2026-09-28 · **Source:** the pending set — the drawer plan's steps first, then the standing items the two 2026-09-28 plans and the ui-flow plan's §11 name, in ship order · **Cursor:** 21 · **Closed:** 2026-09-30 — closed by decision
- Note: items 1–13 are **closed** (2026-09-28 · 2026-09-29) — the drawer plan's steps 1 and 3–9, the roller's three points closed by its retirement with the component, and item 13, whose design is [`260929_FEAT_PLN_Route_trigger-read-at-press.md`](260929_FEAT_PLN_Route_trigger-read-at-press.md) with the build owed.
- [ ] 14 · **Parked 2026-09-29** — Phase 5's marker weights — avoid-only, the weight scaling the per-metre price inside a circle's radius or a corridor's band and never replacing the base, [`AvoidCell`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidGrid.kt:66) being the trap the plan names — merged with the shipped marker scale's wall into one scale at that time, and the review over Phase 6's folded fixes, whose first point is the fairing's shipped values read against the plan's own table
- [ ] 15 · **Parked 2026-09-29** — Change 4: the fine band, its mechanism and its width — the code already re-solves at the ratio, so the item is a decision about the record
- [x] 16 · The progressive-draw plan's remaining review findings — closed 2026-09-30: flicker forgotten; the staircase folded as an obligatory step of the algorithm; the unmeasured PULL cost and the re-emit pin deferred to the next route-acquisition-algorithm chunk; the aim beat and the 55 % face died with the rework
- [x] 17 · The F1 and F2 wording pair of the standoff register — dropped 2026-09-30: deferred to the next route-acquisition-algorithm chunk
- [x] 18 · The record repairs — the `GLOBAL_CONTEXT.md` Route row, extracted with `findstr` first; the epic's own line was delivered by this rewrite — dropped 2026-09-30: the row shortening stays recorded in the Delta's "The record" note
- [x] 19 · The bake, with the standing items carried into the hydration — closed 2026-09-30: the bake ran and re-runs after the archive
- [x] 20 · The Phase 6 device pass — the fairing, its four keys and the GPX acceptance (the user's own) — dropped 2026-09-30: stays owed to the user, recorded in the Delta's "Owed device passes"
- [x] 21 · The saved-route ends bake — fold the pair on the track and its summary, the directional arming match, the reverse pair taken mirrored and the shut save door into the epic's `## Implemented` and the hydration → [`260930_FEAT_PLN_Route_saved-route-ends.md`](260930_FEAT_PLN_Route_saved-route-ends.md)
- Closed 2026-09-30 by decision: 16 resolved, 17 dropped to the algorithm chunk, 18 dropped (recorded in the Delta), 19 absorbed, 20 dropped as the owed device pass, 21 absorbed by the bake itself, which this closure unblocked; 14 and 15 stay parked under it.

**Level 1 — Date:** 2026-09-30 · **Source:** [`260930_FEAT_PLN_Route_avoid-shortest-exit.md`](260930_FEAT_PLN_Route_avoid-shortest-exit.md:1) — its decisions and phases, in the order they gate; the band's law added the same evening · **Cursor:** 1
- [ ] 1 · **D4** — the fine band: change the code or change the record
- [ ] 2 · **Phase 0** — measure λ, the three shares, in-zone metres and the PULL cost; repair the record — **the trace half shipped 2026-09-30**: `PASS`, `PASSKEEP` and `LINE` carry λ, the band's priced λ, the three shares and in-zone metres; the PULL device measurement and D4 stay owed
- [x] 3 · **Phase 1** — split the share three ways; the loop reads the zone share alone — shipped 2026-09-30
- [x] 4 · **D6** — one weight for all slow water, or a weight per source — settled 2026-09-30: one cursor prices every slow source and the band's own key is retired
- [x] 5 · **D7** — the band's outside margin: keep it as a gradient, and at what fraction — settled 2026-09-30: the collar keeps its own fraction over the limit in force
- [x] 6 · **D8** — the clock and the band: does the reported time pay 5 kn inside it, switch or no switch — confirmed 2026-09-30 as shipped: it pays the band's limit, switch or no switch
- [x] 7 · **Phase 1b** — the band's law: its own limit, one price law, the clock, the tests, the record — shipped 2026-09-30
- [ ] 8 · **D1** — the budget's authority: the loop's target, or a ceiling only reported
- [ ] 9 · **Phase 2** — own the cursor: `softCostAversion` means one thing where it is read
- [x] 10 · **Phase 3** — keep the better pass, compared by share, then in-zone metres, then time — shipped 2026-09-30, ticked ahead of the cursor on the user's word
- [ ] 11 · **D2** — the fairing's no-price rule: keep it, or refuse a ring
- [ ] 12 · **Phase 4** — make the tail λ-aware: the fine splice and the fairing — its splice half shipped 2026-09-30; the fairing half waits on D2
- [ ] 13 · **D3** — the hard mode's face, since it changes what a number does — re-judged after Phase 1b
- [ ] 14 · **Phase 5** — the earliest exit as the range's hard mode, gated by the forced-crossing probe
- [ ] 15 · **D5** — marker weights now, or behind the regional grid
- [ ] 16 · **Phase 6** — the regional coarse grid
- [ ] 17 · **Phase 7** — the parked algorithm items: the marker weights and Phase 6's fairing review
- Parked beneath: the closed level above carries items 14 and 15, each with its resume condition; a fresh set was built rather than resuming them.

## Implemented

The pointer index — one line per shipped pass; the archived pointers are dropped and the bare one-liner stays:

- The mode's build: the toggle, the panel, the pin, the trip figure and the save
- Both engines removed and a straight-line placeholder shipped in their place
- The aim ring moved under the boat as the map's own overlay
- Several engines behind one seam, chosen in Settings
- The avoid engine's stage 1: land avoided by grid A\* and a taut pull
- The taut pull's rework: grid A\* plus a verified corner snap, the ANR closed
- The avoid engine's phases 2–6: one cost field, the depth gate, the band, the zones and the fairing
- The depth and zone switches and the band's tangent look-ahead
- The acquisition workflow rebuilt: explicit phases, no timer, the anchor's lead
- The drawer's route group became a read-only mode summary, and the remainder is measured from the line
- The line drawn as the engine builds it, in its own overlay
- The ends moved into the drawer, the acquisition armed on the stored pair, the candidates given their rows
- The Route sub-section joined the Navigation card and actions parted from selections
- The drawer's refinements: the selected face, one head, the two shapes that stopped being actions
- The acquisition's own face, the toggle's door and the track band's three tiers
- The paint order became a rank over the whole list, and the Active route colour gained its row
- The map's add-route fan, the unasked Discard and the auto-pick
- The two render switches gate their chip for a route
- The saved line became a route on the track side — the flag, the render pair, the filter and the card → [`../Tracks/260922_FEAT_PLN_Tracks_trace-flag-and-display.md`](../Tracks/260922_FEAT_PLN_Tracks_trace-flag-and-display.md)
- The engine's interface reworked — the pair repaired inside `routesToCompute`, declared computations, `startLookup(id)`, one id-carrying update flow, the page set and the comparison, the readiness gate and the refused crosshair retired → [`260929_FEAT_DOC_Route_engine-interface.md`](260929_FEAT_DOC_Route_engine-interface.md)
- The avoidance algorithm's design, folded into one document → [`FEAT_DOC_Route_avoid-algorithm.md`](FEAT_DOC_Route_avoid-algorithm.md)
- The route fan's "route" child gained its dual purpose — acquire while idle, select/confirm once the settled line stands
- The acquisition's update stream stopped being torn down per arm — `distinctUntilChanged` keeps the one `updates` subscription alive, closing the drop window that left the mode searching with no stage, line or plan
- The followed route is painted again — the pool reads the `Following` plan's own points once `Select route` clears the page set, and the pin lands on its resolved destination
- The track card's follow door — the route icon in the resume slot follows a saved route as the active route, the followed line painted from the state, and the exit dialog's third door reads Stop following → [`260930_FEAT_PLN_Route_follow-saved-route-card.md`](260930_FEAT_PLN_Route_follow-saved-route-card.md)
- The route ends survive restart and a sub-100 m pair refuses arming — `allMarkers` read, the write-back gated on the marker load, and the pure distance gate with its toast → [`260930_FEAT_PLN_Route_end-persistence-and-short-guard.md`](260930_FEAT_PLN_Route_end-persistence-and-short-guard.md)
- A saved route keeps its two flagged ends and is found by them — the ids on the track and its summary, the arming match that answers a stored line with no search and a shut save door, and the reverse pair answered by mirroring the stored line at its own times → [`260930_FEAT_PLN_Route_saved-route-ends.md`](260930_FEAT_PLN_Route_saved-route-ends.md)
- The 300 m band's own limit became its price — the zones' one law and no aversion key — and the clock reads that limit whatever the price switch says, shipped ahead of the cursor
- The band's limit became a **limit on the grid**, priced per expansion so its price follows the corrected λ; the slow time split **zone · band · ramp** with the loop's budget keyed on the zone share alone; the cape's bend pinned on the derived exchange rate → [`260930_FEAT_PLN_Route_avoid-shortest-exit.md`](260930_FEAT_PLN_Route_avoid-shortest-exit.md)
- A route is selectable and saveable while its line is still drawing — an early select commits the main, cancels the candidates and follows the line when it lands; an early save writes the partial line and grows the same track id at each iteration until the full line is written; the acquisition panel became a swipe pager and stopped repeating the ends → [`261002_FEAT_PLN_Route_functional-flow.md`](261002_FEAT_PLN_Route_functional-flow.md)
- The acquisition panel rode the shared `DrawerScaffold` and became a paging three-column table — the description, the route's Dist · ETA as value · unit pairs, and a candidate's delta with the forced-crossing note, the selected row on the taken-choice face — with `Save to track` · `Select route` · `Discard route` in one weighted row → [`261002_FEAT_PLN_Route_functional-flow.md`](261002_FEAT_PLN_Route_functional-flow.md)
- The slow-water dials rework — the aversion exposed as a Settings dial seeded from `softCostAversion` and read live at every solve, the overrun surfaced, the empty candidate-pass no-op fixed, the fine pass made λ-respecting, and the providers re-pointed at the live settings; Phases 0/A/B/D shipped, Phase C superseded → [`261002_FEAT_PLN_Route_aversion-and-slow-water-model.md`](261002_FEAT_PLN_Route_aversion-and-slow-water-model.md)
- The three-route ladder — the acquisition computes three fixed-λ rungs (around · balanced · through) over one shared grid, the Driving-preference cursor picks the initial rung, and collapsed rungs drop with the nearest survivor selected → [`261002_FEAT_PLN_Route_aversion-and-slow-water-model.md`](261002_FEAT_PLN_Route_aversion-and-slow-water-model.md)
- The candidate-pass apparatus retired — its keys, accessors, parser and `RouteOffer` model removed as dead once the ladder declared its rungs directly → [`261002_FEAT_PLN_Route_aversion-and-slow-water-model.md`](261002_FEAT_PLN_Route_aversion-and-slow-water-model.md)
