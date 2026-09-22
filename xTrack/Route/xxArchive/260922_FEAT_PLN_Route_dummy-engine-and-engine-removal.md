<!-- scope: feature -->
# Route — the dummy engine, and the removal of both real engines

**Date:** 2026-09-22 · **Branch:** `feature/route-dummy` (cut from `feature/route-base` at `d503f06`) · **Status:** in design at the moment of writing, then built in one `#implement`-style pass on the same branch
**Asked for:** the branch carries two unfinished implementations of the routing algorithm and neither is satisfactory — document each one in its own file (flow, then limitations, issues and performance), remove the code behind both while keeping the feature's functional part (route toggle, actions, dialogs, persistence, the route action and the track save), and replace the tracing with a dummy that draws a route from the starting point to the dragged point.

## 1. Why both go

- Neither engine answers the water acceptably: the mesh engine **hugs** whatever constraint is cheapest — its cost is pure time, so the drawn line reads as a zig-zag along the coast and rounds a zone about a metre off its edge — and the corridor tracer costs **tens of seconds a search on the phone** (56 079 ms on the acceptance pair, Pixel 7), which is not a route the mode can preview.
- The two documents below are the record of what each one was, so a third attempt starts from a written failure rather than from a rebuild: [`260922_FEAT_DOC_Route_mesh-engine.md`](../260922_FEAT_DOC_Route_mesh-engine.md) and [`260922_FEAT_DOC_Route_taut-tracer.md`](../260922_FEAT_DOC_Route_taut-tracer.md).
- This is a **removal, not a retirement**: the two engines, their tests, their bake and their artifact leave the tree. Nothing here is kept "for the baseline", because a baseline nobody measures is a folder of dead code — the figures the two documents carry are what the measurement was.

## 2. The functional framework, kept

- `ui/map/RouteViewModel.kt` — the Idle → Draft → Confirmed machine, the frozen start, the coalesced preview, the pace window and the four save outcomes.
- `ui/map/RouteOverlay.kt`, `RouteHost.kt`, `RouteConfirmPanel.kt` — the toggle, the screen-centred aim, the polyline and the pin, the confirmation panel, the aim threshold, the trip figure.
- `data/model/RouteResult.kt` (minus the fields §4 names), `RoutePoint.kt`, `spatial/RouteEngine.kt` (the seam, `RouteEngineState`, `RouteUnavailableReason`), `data/route/RoutePace.kt`, `spatial/Units.kt`, `spatial/SpatialOperations.kt`.
- The `MapScreen` wiring, the dashboard's trip cell, `TrackFromCourse` and the save, the settings row, and every `route.*` key the line, the pin and the pace read.

## 3. The dummy

- `spatial/RouteDummyEngine.kt` implements `RouteEngine` and is what `MapScreen` builds: **`Ready` at once** (it reads no layer, so `prepare()` answers `Ready` and the toggle is never disabled), and `route(start, aim, pace)` answers one straight segment from the start to the aimed point.
- `distanceM` is the haversine distance ([`SpatialOperations.haversine()`](../../../app/src/main/java/ykws/android/maro/spatial/SpatialOperations.kt)), `legTimesSec` is that distance over `Units.knotsToMps(cruiseSpeedKn)`, `durationSec` their sum.
- The fields the removed engines filled are answered **empty and false**: `inBand` is not carried at all (§4), `destinationMoved` false, `forcedCrossingZoneNames` empty, no dossier.
- The `Restore the seam's own words` note: the seam's KDoc still describes a second engine filling a slot; it is rewritten to name the dummy as what ships and to say that the named refusals are the contract's shape with **no producer today**.

## 4. What is removed

| Surface | Files |
|---|---|
| Corridor tracer | `spatial/taut/` — `TautWorld`, `TautGeometry`, `TautObstacles`, `TautBand`, `TautGraph`, `TautGraphBase`, `TautTerrain`, `TautEasing`, `TautSearch`, `TautRouteEngine`, `TautRouteDetails` |
| Mesh engine | `spatial/mesh/` — `MeshRouteEngine`, `RouteSearch`, `RouteShortcut`, `RouteMeshContainment`, `RouteMeshDetails`, `RouteMeshRepository` |
| Shared computation | `spatial/RouteFillet.kt`, `RoutePlanTiming.kt`, `RouteTurnGeometry.kt`, `RoutePointQueries.kt`, `data/route/RouteMeshSerializer.kt`, `data/route/RouteSpatialAdapter.kt`, `data/model/RouteMesh.kt`, `RouteNode.kt`, `RouteEdge.kt`, `RouteEngineDetails.kt` |
| Bake and artifact | `app/src/main/proto/route.proto`, `tools/bake-route.bat`, the `:do_route` label and its `call` in `apk-bake.bat`, `data/app-assets/route/nice-menton.bin`, the empty `data/app-assets/routing/` |
| Build | the `jts` and `poly2tri` version entries and aliases in [`gradle/libs.versions.toml`](../../../gradle/libs.versions.toml:17) and their two `testImplementation` lines |
| Algorithm-only values | `route.zoneBerthM` and `route.turn.lateralAccelMps2` with their `AppConfig` accessors, bounds and KDocs; `route.freeWaterPaceKn` and every line and pin key stay |
| Dead surface | the two read-only handouts `routeCoastline` / `routeRegulatedZones` in [`NavigationViewModel`](../../../app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt:250), read only by the engine construction this plan deletes |

**The field-by-field ruling, read rather than guessed** (`inBand`, `forcedCrossingZoneNames`, `stale`):

- `RouteResult.Success.inBand` and `RoutePlan.inBand` — **deleted**: no `ui/` file reads either, the only reader being `RoutePlan.of`'s own copy.
- `forcedCrossingZoneNames` — **kept**: [`DashboardPanel`](../../../app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt:355) prints `route_trip_forced` and [`RouteConfirmPanel`](../../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:183) prints `route_forced_crossing`; the dummy leaves it empty and both lines stay unreachable, which is the honest state of an engine that never crosses anything.
- `RouteState.Confirmed.stale` — **kept**: the dashboard reads it for `route_trip_stale` and for the dulled value colour, and nothing in this pass sets it.

**Tests removed with their subjects:** `spatial/taut/`'s nine files (`TautAssertionsTest`, `TautBandTest`, `TautEasingTest`, `TautGeometryTest`, `TautObstaclesTest`, `TautRouteHarness`, `TautSearchTest`, `TautTerrainTest`, `TautTestWorld`), the mesh's suite (`RouteSearchTest`, `RouteShortcutTest`, `RouteMeshContainmentTest`, `RouteFilletTest`, `RouteMeshDetailsReadings`) and the bake's (`RouteMeshBuilder`, `RouteMeshBuilderTest`, `RouteMeshPrebakeTest`, `RouteMeshSerializerTest`, `RouteTrajectoryProbeTest`, `PrebakedRouteGeometry`, `RouteGeometry`).
**Kept:** [`CoastlinePointWalkTest`](../../../app/src/test/java/ykws/android/maro/spatial/CoastlinePointWalkTest.kt) guards `CoastlineSpatialIndex`, which stays.

## 5. Returned to the corpus, and to the branch

- Both documents §1 points at, attached to this feature's `## Docs`, with the epic's `## Key Files`, `## Sections` rules and `## Implemented` rewritten onto what now ships.
- The three plans the documents replace — `260920_FEAT_PLN_Route_taunt_algorithm-design.md`, `260920_FEAT_PLN_Route_engine-seam-and-parking.md`, `260920_FEAT_PLN_Route_trajectory-quality.md` — are **archived** with an index row and the digest floor, and the epic's open `## Walk` levels are **closed as superseded** on the user's word of 2026-09-22.
- `260919_FEAT_PLN_Route_registration-and-drift.md` stays live as the shipped record of the mesh era; it names a bake that no longer exists, and that is what a shipped plan is.

## 6. Verification

- `gradlew` compile and the full unit suite green, then `apk-build.bat` green; the build is taken twice — once after the dummy is switched in with both engines still present, and once at the end after the deletions.
- `RouteDummyEngineTest` pins the segment's two points, its distance, its pace-made-good time and the `Ready` it answers; `RouteEngineSeamTest` and `RoutePlanTest` follow the trimmed contract.
- `#doctor` clean, and no backticked path anywhere in `docs/` or the live xTrack files pointing at a file this plan deletes.

## 7. What this plan does not do

- It does not look for a replacement algorithm, and it takes no position on what a third attempt should be: the seam is what a next engine plugs into, and the two documents are the map of what the first two got wrong.
- It changes nothing outside Route except the two handouts, the bake wiring, the two library entries and the corpus rows this removal orphans.

## Outcome

- **Shipped in the order §1 set, in one pass on `feature/route-dummy` (2026-09-22):** the two documents written while the code was still readable, then §4's inventory removed in full — `spatial/mesh/`, `spatial/taut/`, the shared computation, the model types only they used, `route.proto`, the bake with its artifact, the two test-only libraries and twenty-two test files — then the placeholder behind the unchanged seam, switched in at one expression in `MapScreen`.
- **Verified as §6 asked**, by the pass's own recorded run: `gradlew :app:compileDebugKotlin` green, `apk-build.bat` green, and `:app:testDebugUnitTest` green over 74 result files with no failure. The suite was re-read on the branch after the develop merge of the same day: 535 tests, no failure.
- **As built rather than as planned:** four keys went where §4 named two — `route.zoneBerthM`, both turn caps and `route.shoreOffsetM` — and the probe's own system property left as an orphan §4 did not name.
- **§5 planned three archived plans and four were**, the mesh era's settled design joining the tracer's design of record, the engine-seam plan and the trajectory study.
- **The one sentence the plan could not write about itself is its own §1:** this was a removal rather than a retirement, which is exactly what happens to it now — the removal is done and documented, so the plan retires, its record standing in the epic's `## Implemented`.
- **What it refused to answer is still unanswered:** no replacement engine, and no position on what a third attempt should be. The seam is what such an engine plugs into, and the two documents are the map of what the first two got wrong.
