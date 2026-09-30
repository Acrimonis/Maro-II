<!-- scope: feature -->
# Route — the drawer's route group becomes a mode summary

The right-hand drawer's **Navigation** section carries a **Route** action group today — a title, a comment and three labeled pills (To · From · Save). That group leaves, with its icon, its strings and every piece of ladder plumbing built only for it, and a read-only summary takes its place: the mode's status word, the plan's own distance and ETA, and the boat-relative pair while a route is followed. One arithmetic moves with it: what is left of a route is measured from the **nearest point on the line** rather than from the nearest vertex.

Owning feature: **Route** — the pills landed under Route (`FEAT_DSC_Route.md` `## Implemented`, 2026-09-25) and the drawer is that feature's accepted menu seam (Isolation Design §2). Ui_Menu gains a pointer line only.

Reviewed by Ask on 2026-09-26 — verdict **revise**, folded in full at §7.

---

## 1. What leaves

| What | Where | Note |
|---|---|---|
| `RoutePill` composable + its KDoc | `MenuDrawerOverlay.kt:61–86` | the only user of the `route` glyph |
| `import ykws.android.maro.ui.icons.route` | `MenuDrawerOverlay.kt:51` | |
| the route title / comment block | `MenuDrawerOverlay.kt:208–228` | inside the Navigation card, after a `SectionDivider` |
| the three-pill row | `MenuDrawerOverlay.kt:229–250` | `To` · `From` · `Save` |
| the seven route parameters | `MenuDrawerOverlay.kt:129–140` | `routeActive` · `routeConfirmed` · `routeFrontSaved` · `routeAimOffBoat` · `onRouteTo` · `onRouteFrom` · `onSaveRoute` |
| `RouteOverlayData` | `OverlayLayerParams.kt:158–185` | replaced by the summary bundle, §4 |
| the ladder parameter and its hand-off | `OverlayLayer.kt:119`, `OverlayLayer.kt:366–374` | |
| the three action aliases | `MapScreen.kt:1806–1824` (`routeToAction`), `:1826–1829` (`routeFromAction`), `:1867–1870` (`saveRouteAction`) | their only callers are the bundle's construction at `:2971–2980` |
| five string keys × two locales | `values/strings.xml:649–654`, `values-fr/strings.xml:648–653` | `menu_route_title` · `_comment` · `_to` · `_from` · `_save` — retired, not reused |
| the `route` glyph file | `ui/icons/Route.kt` | imported by nothing else; **deletion waits on the user's word** (no file is deleted on the agent's initiative) |

**What stays.** `rerouteRoute()` and `saveRouteTrack()` keep serving the panel; `RouteToggleButton` on the map control stack (`MapScreen.kt:3729`) remains the mode's front door; the panel's four actions per phase carry the same outcomes the pills offered.

**One capability narrows, and it is accepted.** From Idle the To pill armed the mode *and* aimed in one tap (`MapScreen.kt:1811–1817`). With it gone the panel is not even composed until the mode owns the slot, so the same end is reached in two gestures on the map: the toggle (`MapScreen.kt:2348`) arms, then `Acquire route` asks from the aim. The two-gesture path is intended, not an oversight.

**Two findings die with the pills** rather than needing fixes (both in the epic's 2026-09-25 entry): the To pill's cold-start arming wait that can hang, and the From pill's `active` gate sitting before a Following-only `reroute()`.

**No test touches the pills or the bundle** — `RouteOverlayData` has no third reader, and no test names the drawer, the pills or the bundle.

---

## 2. The summary block

Placement: the same spot in the drawer's Navigation card, after a `SectionDivider`. The block's visibility is a chrome flag (§4), so nothing stands when the mode is off.

Gate: `routeArmed && routeState.phase != RoutePhase.IDLE` — the shipped predicate at `MapScreen.kt:2402` — and, since the review's polish pass, **something to say**: `routeSummaryVisible` now reads `routeOwnsSlot && (a search is running || a plan stands)`, so the block shows while **computing** (CHOOSING) or **active** (FOLLOWING) and never as a bare heading while the mode is armed with nothing acquired.

Rows, drawn with the drawer's own `StatRow` shape (`MenuDrawerOverlay.kt:530`), the shape its recording block already uses. The panel's `StatCell` is not borrowed: each surface keeps its own reading cell.

| Row | Text | Where the words come from |
|---|---|---|
| status | `Acquiring…` beside the engine's stage word while it searches; `Route active` while following | `route_status_acquiring` · `route_stage_*` · `route_status_active` — all shipped, and the same derivation the panel makes |
| block title | `Route` | `route_trip_title` — shipped |
| planned distance | the plan's length | label `track_stat_dist`; value `route_trip_distance_nm(plan.distanceNm)` — both shipped, the panel's own labels |
| planned ETA | the plan's own course time | label `route_label_eta`; value `route_eta_value_fmt` — both shipped |
| pair title | `Remaining` | **one new key** in both locales |
| distance left | `RouteTripFigure.distanceNm` | the same two shipped keys as above |
| ETA left | `RouteTripFigure.etaSeconds` | the same two shipped keys as above |

**Word inventory, enumerated** — so nothing is short: the status line spends no label, the block title is shipped, both row labels are shipped, and exactly **one new key per locale** is added, the sub-title that names what is left. The review counted three or four because it read the block as one label per row; the sub-title carries the pair's identity and the rows keep the panel's own two labels.

**The planned ETA is the panel's own derivation**, not a second one: the panel takes `plan.remainingFrom(plan.start).durationSec` (`RouteConfirmPanel.kt:416–417`) and prints it with an inline split (`:447`). That split moves into one small print helper beside the feature's other print rules, and both readers call it — the panel included — so the ETA has one home.

**The status word is the panel's**, reused rather than re-derived: `route_status_acquiring` belongs to the searching engine alone, so the block never prints it while the mode is merely aiming. The bundle carries the searching flag; the row reads the stage's word only while that flag is true.

Settled behaviour: the status line + stage whenever the gate holds; the plan's pair as soon as a plan stands; the boat-relative pair **only while FOLLOWING** — a draft is not being followed, so "expected" would describe a line the boat may never take. That reproduces the shipped rule rather than inventing one: the figure's own gate in `MapScreen` is Following.

**The echo is accepted and it is the point.** The drawer opens over the panel at 75 % width from the right, so while it is open the panel is largely covered; the block is what remains readable of the mode. Echoing the panel's words is how the two surfaces agree in a glance, and the block carries no action where the panel carries four.

Left out by scope: the forced-crossing note the dashboard card carries (`route_trip_forced`) is not requested here.

---

## 3. The bundle the drawer takes

`RouteOverlayData` becomes a read-only summary bundle — no callbacks, plain values, ids rather than resolved text (a spec type holds the `@StringRes`, never the literal):

```
RouteSummaryData(
    searching: Boolean,             // the engine is mid-search: gates the acquiring word
    stageRes: Int?,                 // the engine's boundary, null when nothing is searching
    plannedDistanceNm: Double,
    plannedEtaSeconds: Double,      // the panel's own derivation, passed in
    remaining: RouteTripFigure?,    // MapScreen's existing routeTrip; null through CHOOSING
)
```

- **No `RoutePhase` reaches the drawer.** The bundle's own KDoc records that nothing of the mode state is duplicated into a composable's parameters, and the phase is that state; `searching`, the nullable stage and a non-null `remaining` carry everything the block needs.
- **Visibility lives in `OverlayChrome`**, which is what that bundle is for — the sibling bundles are non-null with a default and their surfaces are gated there, so the summary follows that shape rather than inventing a nullable bundle. The flag is computed from the same predicate as today's `routeOwnsSlot`, in one place.
- **The figure is passed, never recomputed.** `MapScreen` already holds this exact value as `routeTrip` (`MapScreen.kt:2403`); the bundle takes it, so a composition computes it once.
- `MenuDrawerOverlay` takes one `routeSummary: RouteSummaryData` in place of the seven parameters.

Cost: the projection is about 1.6× today's per-vertex haversine loop at the scale the route docs record, and the drawer already receives a bundle that changes every second (`recorderState`), so no bound is pinned for it.

---

## 4. The geometry change

What is left of a route is measured from the **nearest point on the line**, not from the nearest vertex.

- Today: `RoutePlan.remainingFrom(from)` (`RouteViewModel.kt:85–98`) snaps `from` to its nearest **vertex** through `nearestVertexIndex` (`:100–115`) and sums whole legs from there.
- Change: for each leg `i`, project with the shipped `SpatialOperations.projectPointOntoSegment` (declared at `SpatialOperations.kt:94`, its KDoc at `:88`), take the leg whose `pointToSegmentDistance` is smallest — ties to the **lower index**, matching today's strict `<` at `:109`, so the choice stays deterministic — then
  - `remainingM` = haversine from the projected point to `points[i+1]`, plus every following leg;
  - `remainingSec` = the current leg's own `legTimesSec[i]` × the fraction not yet travelled, plus every following leg's own seconds. The fraction is the haversine from `points[i]` to the projected point over that leg's own length.
- **A zero-length leg answers a fraction of 1.0** — nothing of that leg is spent, since a fix standing on it has not travelled it. Today's loop has no such case; the new halfway test cannot catch it, so the guard states its own value.
- `nearestVertexIndex` retires with its only caller, and its KDoc sentence — "the one snap both readings of this plan use" — moves onto the projection, which becomes that one snap.
- The KDoc on `remainingFrom` argues *for* the vertex snap and must be **rewritten with the new rationale**, not deleted: the sentence it replaces is the record of why the old choice was made.

Behaviour that must not move, with what holds it:

- A point at the route's start answers the whole course — the panel's table reads `remainingFrom(plan.start)` (`RouteConfirmPanel.kt:416`). It holds because `points[0]` **is** the raw start, which the engines' own code states (`RouteAvoidEngine.kt:202`, `:350`, `RouteDummyEngine.kt:112`) — that equality is the invariant, not an accident.
- The destination answers **zero**, the arrival reading the trip cell shows.
- `points.size < 2` answers the plan's own totals (`RouteViewModel.kt:86`).

Consumers move together — both read `remainingFrom`, so neither can drift: `routeTripFigure` (`RouteOverlay.kt:147–165`, the dashboard's trip card through `DashboardPanel.kt:332–363`) and the panel's data table.

Tests:

- The three shipped cases hold untouched (`RoutePlanTest.kt:79` zero at the destination, `:87` whole from the start, `:95` the rest from a mid-route vertex).
- The leg-times pin (`:104–127`) is amended to read *a fraction of a leg's own time*.
- The two further pinned readings of the same figure (`RoutePlanTest.kt:240`, `:255`, and `RouteEngineSeamTest.kt:186`) are **read and left alone**: each puts the fix on a vertex, where the two geometries agree by construction.
- **One new case pins what the change buys** — a fix halfway along a leg answers half that leg's distance and half its seconds. The projection is planar and accurate to < 1 % below 50 km (`SpatialOperations.kt:53`), so the case uses a **short fixture leg and asserts at that stated tolerance**, never at the 1e-6/1e-9 the vertex cases use.

**The record's own sentence is superseded, and it is not where the review first looked.** The snap is stated at `260924_FEAT_PLN_Route_acquisition-and-route-workflow.md:56` — "the remainder reading snaps to the nearest vertex so a lead that undershoots or overshoots stays harmless" — and that line is what this change contradicts. `260922_FEAT_PLN_Route_ask-policy-and-target-validity.md`, the requirement book, holds no snap statement at all (its only "snap" is the stage's name at `:70`, `:83`), so nothing there is superseded. The epic's own Isolation Design lines carry the stale *destination picker* wording and are amended with the plan pointer.

---

## 5. Build order

1. The geometry: `RouteViewModel.remainingFrom` + its KDoc + `RoutePlanTest` (one shipped case amended, one added, the two further readings verified untouched).
2. The bundle swap: `RouteOverlayData` → `RouteSummaryData` in `OverlayLayerParams.kt`, the `OverlayChrome` flag, the ladder parameter in `OverlayLayer.kt`, the construction in `MapScreen.kt` taking the existing `routeTrip` — and the three aliases deleted with it.
3. The ETA split's one home: the small print helper, with the panel moved onto it.
4. The drawer: the route group removed, the summary block added in its place.
5. The strings: five keys retired and the one new sub-title added, in both locales.
6. The record: the epic's Isolation Design sentences and menu-seam row; the plan pointer in the epic's `## Docs`; the superseded line at 260924:56; a pointer line in Ui_Menu's epic.
7. `apk-build.bat`, then the route-filtered unit suites.

The drawer's look, and the trip card's reading after the geometry change, are a device pass — the user's to run.

---

## 6. What this change leaves behind

The pills were never given a plan of their own — no file under `xTrack/Route/` names the menu group, the pills or a destination row (the archived registration plan mentions the menu seam in passing) — so there is no plan to retire for them. The invalidated items are sentences and keys inside live files, and each is amended or retired by §§1–4: the epic's two seam sentences, the snap sentence at 260924:56, and the five `menu_route_*` keys.

---

## 7. The review, and how each finding was folded

Ask reviewed the first draft on 2026-09-26 and returned **revise** — the removal sweep and the arithmetic held; the word inventory, one record pointer and the status row did not.

| Finding | Disposition |
|---|---|
| Blocking — the snap is not in the requirement book | Corrected: the sentence is at 260924:56, and §4 now says the book holds none |
| Blocking — one new label cannot label five rows | Corrected: the inventory is enumerated in §2 and comes to exactly one new key per locale |
| Blocking — the status row would say *Acquiring* while merely aiming | Corrected: the bundle carries `searching`, and the acquiring word stays the searching engine's |
| Should-fix — the figure would be computed twice | Corrected: the bundle takes `MapScreen`'s existing `routeTrip`; §3 states it |
| Should-fix — the ETA format unnamed | Corrected: `route_eta_value_fmt`, the panel's own derivation, with its split given one home |
| Should-fix — the new test's tolerance | Corrected: a short fixture leg and the projection's stated < 1 % |
| Should-fix — two more pinned readings | Recorded in §4 as read and left alone, with the reason |
| Should-fix — `RoutePhase` crossing into the drawer | Corrected: `searching` + nullable stage + non-null `remaining`; no phase in the bundle |
| Should-fix — the zero-length leg guard unnamed | Corrected: the fraction answers 1.0, and §4 says why |
| Should-fix — the start invariant uncited | Corrected: cited to the engines that make `points[0]` the raw start |
| Should-fix — the To pill was not equivalent to the panel's action | Corrected: §1 states the two-gesture path as intended |
| Should-fix — two citations miss their lines | Corrected: `OverlayLayer.kt:366–374`, `SpatialOperations.kt:94` |
| Should-fix — the CHOOSING echo | Accepted in §2 with its reason: the drawer stands over the panel |
| Held — the removal inventory's completeness | Kept as written |
| Held — the arithmetic's invariants | Kept as written, with the citations added |

One item is the user's eye rather than the plan's: the sub-title's exact word, proposed as *Remaining* / *Restant*.

## Outcome

**Shipped 2026-09-26 in one `#implement` run on `feature/menu-route`** — all seven steps of §5 in its own order: [`remainingFrom`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:85) projects onto the nearest point of the line with `nearestVertexIndex` retired into it; `RouteSummaryData` replaces `RouteOverlayData`, its visibility in `OverlayChrome` and `MapScreen` handing it the `routeTrip` it already held; the ETA split has one home in `routeEtaText()` and the panel reads it; the drawer's Route group leaves with `RoutePill`, the glyph import and the seven parameters, and the summary block stands in the same Navigation card; the five `menu_route_*` keys are gone from both locales with `route_trip_remaining` joining them. The record moved with it: the epic's seam sentences and collision row no longer say *destination picker*, its `## Docs` carries this plan's pointer, the snap sentence at [`260924:56`](260924_FEAT_PLN_Route_acquisition-and-route-workflow.md:56) states the projected reading, and Ui_Menu's epic gained its pointer line. `apk-build.bat` SUCCESSFUL with `app-debug.apk` produced; the route-filtered suites green at **126 tests in 18 suites, 0 failures**; the Ask hop returned **ship** with seven should-fixes, named in the epic's `## Implemented` entry.

**Deviations, each named at the hop:** this plan's §3 sketch carried the plan's own pair as plain `Double`s and it landed nullable, §2's settled behaviour needing the pair only once a plan stands, so the snippet was the stale side; §2's row above named `route_searching` for the searching engine while the acquiring word landed — what §2's prose, §7's correction and the panel's own word all agree on, and this cell is corrected above to match; the sub-title landed as the proposal (*Remaining* / *Restant*) with the user's own word still open; the record work ran to §5.6's list, wider than the three edits the hop was handed; and `ui/icons/Route.kt` stayed in place, no file being deleted on the agent's initiative.

**Polish, same session:** the review's three code-side should-fixes were closed in a follow-up hop — the plan's pair printing only when both halves are present, the §2 gate narrowed as above to stand only while a search runs or a plan stands, and `RoutePlanTest`'s doc naming the halfway case as the fractional pin — with `apk-build.bat` SUCCESSFUL and the route-filtered suites at **126 tests in 18 suites, 0 failures**.

**Owed:** the device pass — the drawer's look and the trip card's reading after the projection — which is the user's.
