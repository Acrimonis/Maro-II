# Context Hydration — Route — 2026-09-30

**Last Bake:** 2026-09-30 21:46 UTC — written by `#bake`; absence means never baked

**Directive trace:** Two sessions meet in this tree, each on the user's own words — the `route-markers` one (`#new`, `#focus`, the discuss gate, four ordered review passes, `#impl`, then `#merge`, `#bake`, `#commit` and `#push`) and the `avoid-I` one (an `#implement` whose Code and Ask hops closed the shortest-exit plan's Phase 1 and 1b, then `#bake`, `#commit` and this `#merge`) — and of the five covered action classes none was met unasked: no dependency was added, no machine-shaped data file was opened, no file was written without one of those words, and the device was never touched; two `route-markers` claims were corrected after the reads that settled them, and every claim about the avoid code followed its own read.

## State

**A saved route keeps its ends and is found by them.** The two flagged markers a route ran between now persist with the track it became and with that track's summary — `routeStartMarkerId` and `routeDestinationMarkerId` at proto 20/21, an empty string meaning *that end was not a marker* — and the index pass projects both beside the `route` flag, so the acquisition's match reads them without loading a track. Only a flagged-marker end carries an identity, and no index version was bumped, an absent string decoding to the honest *no marker recorded*, which is why routes saved before the change never match.

**Arming on that pair answers with the stored line, not a search, and the return trip is the same line mirrored.** `armRouteMode` rebuilds the plan through `routePlanOf` — no `routesToCompute`, no `startLookup` — links it in the session so `isRouteSaved` shuts the save door, skips the short-pair guard on the match and re-applies it where the fallback must search; `mirroredPlanOf` is the forward inverse with its lists reversed, so mirrored leg *k* is stored leg *n−1−k* and the stored milliseconds carry over with no pace arithmetic, as a new plan named for the arming instant with its save door left open, armable only where each marker carries both end flags. Requirements **R82–R87** live in `FEAT_DSC_Route.md`'s own `## Requirements`; the master book was retired to `xTrack/Route/xxArchive/` on 2026-09-30, so a requirement written into an archived body is reached by nothing.

**The band's law is priced by the search now, and the budget is a zone's.** The 300 m band's limit left the grid's frozen base for **per-cell limits** the A\* prices per expansion, so the band follows the corrected λ exactly as a ring does and the two never charge one cell twice; the pull's guard prices the same law with its two arms merged by `max`; the clock's slow time is split **zone · band · ramp** with the λ loop, `betterPass` and `budgetUnmetZoneShare` keyed on the zone share alone, and the cape acceptance pinned on the derived rate `(pace/limit − 1) × λ`.

**Verification.** `apk-build.bat` SUCCESSFUL and `:app:testDebugUnitTest` green for both bodies of work — the route-markers suite twice, forward and after the reverse fold, and the avoid suites 28 · 9 · 22 after the band's law; nothing of either is device-validated. The screen's own arming branch is pinned by no test: `armRouteMode`'s reversed path, the null track id, the minted instant and the guard's re-application rest on reading.

**The record.** The Route row in `## Feature Summaries` could not be rewritten by either bake — it stands beyond the tools' 2 000-character line cap, the same cap that blocked this pass's Focus History prune, the stack keeping eleven entries against its cap of ten — and the shorter row it needs is the standing item the Delta's "The record" note carries.

## Target Files

- [`Track.kt`](../../app/src/main/java/ykws/android/maro/data/track/Track.kt:75) · [`TrackRepository.kt`](../../app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:343) · [`TrackFromCourse.kt`](../../app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt:59) — the two ids on the track and the summary, the index projection, the save that writes them
- [`RouteOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:177) — `routePlanOf`, `mirroredPlanOf`, `storedRouteMatch` with its two passes
- [`MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1823) · [`RouteViewModel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:202) · [`RouteConfirmPanel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:394) — the ends' ids, the match, the guard skip and its fallback, the mirror, the armed pair
- `spatial/avoid/AvoidGrid.kt` · `AvoidSearch.kt` · `RouteCostField.kt` · `RouteEta.kt` — the band's per-cell limits, the per-expansion read, `slowWaterPriceAt`, `slowShares`
- [`RouteAvoidEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:141) — the grid built λ-free, the merged guard arm, the zone-share key, the traces
- [`260930_FEAT_PLN_Route_avoid-shortest-exit.md`](260930_FEAT_PLN_Route_avoid-shortest-exit.md) — Phase 1 · 1b shipped, D6 and D7 settled, D8 confirmed; [`260930_FEAT_PLN_Route_saved-route-ends.md`](260930_FEAT_PLN_Route_saved-route-ends.md) — the forward match and the reverse

## Next Step

The avoid walk's item 2 — Phase 0's remainder: measure the `PULL` boundary's mapping cost on the device against the ≤ 500 ms budget and settle D4, the fine band's code-versus-record decision; the band's new inshore times are re-read in the user's own device run. Owed beside it, the saved-route-ends device passes for both matches, the archived body's stale "in design" status line, and the summary row's shortening.
