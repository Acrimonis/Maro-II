<!-- scope: feature -->
# Route — avoid engine, stage 1: avoid land

**Created:** 2026-09-24 · **Branch:** `feature/route-avoid` · **Status:** shipped 2026-09-24

## What this is for

Stage 1 of the avoid algorithm: [`RouteAvoidEngine`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt) stops drawing a straight line and answers a collision-free route around land, islands and hazard rings. The 300 m band (stage 2) and regulated speed zones (stage 3) land later as additive polygon sources over the same rasterizer.

## The locked algorithm, and the budget

- **Corridor-bounded grid A\* + clearance taut pull.** Obstacles are rasterized into a corridor grid; A\* walks the free cells; a taut pull collapses the coarse path into straight waypoints that keep clear of land.
- **No O(n²) pair scan and no easing** — the taut tracer's two measured killers, avoided by construction.
- **Phone budget: ≤ 500 ms wall.** Grounded on the user's real corridor — east of the Îles de Lérins to Port de la Salis — at **~120–250 ms realistic and ~350–450 ms pessimistic**; the numbers are in the performance section below.
- **Stage additivity is a stage-1 contract, not an afterthought**: the cell ships as a tagged, costed state and the pull as a source-parameterized predicate, so the band and the zones land as additive sources and never as rework.

## Keys (locked 2026-09-24)

| Key | Default | Meaning |
|---|---|---|
| `route.avoid.obstacleMarginM` | 25 | clearance the route keeps off land, islands and hazard rings |
| `route.avoid.gridCellM` | 50 | the corridor grid's cell side |
| `route.avoid.corridorReachM` | 1852 | how far the corridor box reaches past the start-aim line (1 NM) |
| `route.avoid.zone300MarginM` | 25 | the band's own margin, unused until stage 2 |

All four read through `AppConfig` with a clamp on parse, one home each — no Settings row until one is asked for.

## Files

- `app/src/main/java/ykws/android/maro/spatial/avoid/AvoidWorld.kt` — the engine's own world interface, and the live adapter.
- `app/src/main/java/ykws/android/maro/spatial/avoid/AvoidGrid.kt` — the corridor grid and the land rasterizer.
- `app/src/main/java/ykws/android/maro/spatial/avoid/AvoidSearch.kt` — the A\*.
- `app/src/main/java/ykws/android/maro/spatial/avoid/AvoidPull.kt` — the clearance taut pull.
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — rewritten as the session that owns the pipeline.
- `app/src/main/java/ykws/android/maro/spatial/RouteEngineChoice.kt` + `ui/map/MapScreen.kt` — the `avoid` factory gains a world provider, supplied by `MapScreen`'s existing `CoastlineRepository` instance.
- `app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt` — a read-only land-ring classification query, `polylineIdx → open / CCW-ring / CW-basin`, so the adapter can read land edges with their orientation (shared read surface, already open by design).

## The world interface

The decoupling rule stands: the feature imports no coastline type, and an engine that wants the water declares its own world interface. `AvoidWorld` is what stage 1 needs, in the engine's own vocabulary:

- `segmentsIn(box: BBox): List<AvoidEdge>` — land edges whose bounding box overlaps `box`, from [`CoastlineSpatialIndex.segmentsInBbox`](../../app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt:479). Each `AvoidEdge` is two points plus an **orientation — open coast, land ring (CCW), or water basin (CW)** — because the index treats CCW rings as land and keeps CW marina basins as water, and a bare even-odd fill would block a basin as land.
- `isWater(lat, lon): Boolean` — from [`CoastlineSpatialIndex.isWater`](../../app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt:559), for point validity and the pull's clearance.
- `distanceToCoastM(lat, lon): Double` — the pull's margin check.
- `coastlineReady: Boolean` — the readiness gate.
- `load(): RouteEngineState` — the trigger `prepare()` fires on a miss; the adapter implements it by calling the repository's existing `loadCoastline()`.

The adapter is the single importer: it holds the `CoastlineRepository` and its index and translates to `Avoid*` types. The orientation it needs is **not public today** — `isRingPoly` and `islandRings` are private and `segmentsInBbox` returns every usable edge unfiltered — so the Coastline-feature shared edit is the **land-ring classification query** named above, whose return shape is `polylineIdx → open / CCW-ring / CW-basin`; that is the one real coupling stage 1 adds. Hazard rings need no separate producer in stage 1: they already arrive as CCW land rings inside the island set.

## The pipeline, stage by stage

1. **Corridor.** The box is the start-aim bounding box inflated by `corridorReachM`, **clamped to the region's bounds** (a truncated box is accepted — it is the searchable water, not a refusal), with metres-per-degree computed at the box's mid-latitude by the index's own convention. The box is recomputed per ask, so every aim drag re-harvests.
2. **Harvest.** `segmentsIn(corridorBox)` returns the mainland open coast, the island rings and the hazard rings as edges, each with its orientation. No simplification in stage 1 — the box already bounds the set.
3. **Rasterize into a tagged, costed cell state.** A cell carries `FREE`, `LAND`, `BAND` or `ZONE` plus a source cost — never a bare blocked boolean — so stage 2's band and stage 3's zones add a tag and a cost without reworking the rasterizer or the A\*. Stage 1 uses `FREE` and `LAND` only. The margin band is ruled by **cell-centre-to-edge-segment distance ≤ `obstacleMarginM`** — never stepped point discs, which would leave holes where two 25 m discs of a 50 m step stand tangent. A **land ring** (CCW, hazard rings included) has its interior filled `LAND` by even-odd, while a **CW basin stays water**. The open coast's landward side is closed by the same walk, with the closure radius pinned to the **half-diagonal ~35 m** (a 25 m margin is under it, so a coast crossing a cell diagonally is sealed), and edge-adjacent cells are then confirmed with `isWater` and marked `LAND` when not water. Cells not near any edge stay `FREE` without a single water query.
4. **Ends.** Start and aim cells are **force-free** — the margin binds the path, not where the boat already is. An end with `isWater = false` is refused by `validatePoint` as `OFF_WATER`, and the entry points answer `RouteResult.OutsideWater` for it. The emitted polyline starts at the **raw start** and ends at the **raw aim** — the snapped cell is only the search's anchor, so `destinationMoved = false` and the pin stands on the aim — and the pull's clearance is **exempt within a margin-radius disc around each forced-free end**, which is how the first and last legs reconcile with the margin predicate.
5. **A\*.** Eight neighbours, diagonal cost `√2 × gridCellM`, the g-cost reading each cell's source cost **in metres-equivalent** so the haversine heuristic stays admissible when stages 2–3 add `BAND`/`ZONE` costs; stage 1's costs are all equal with `LAND` impassable. Ties break by shorter-so-far then a deterministic neighbour order, never insertion order. `ensureActive()` every few hundred expansions so an abandoned drag stops inside the search. Exhaustion is `NoPath`, retried once with the corridor reach doubled, then refused by name.
6. **Taut pull.** The coarse cell path is pulled taut over a **source-parameterized clearance predicate** — the same source list the rasterizer and the A\* read — so stages 2 and 3 pass band and zone sources without touching the pull. The walk is the classic two-pointer string-pull: the anchor stays while the probe advances, and on a failed extension the probe's predecessor becomes the new anchor. The clearance sample step is **≤ `obstacleMarginM / 2` (12.5 m)** so the margin is a real guarantee rather than an approximation, and the concave-coast case where a rejected extension is re-walked is **bounded, not asserted linear** — each failed chord re-walks once. The result is the ordered waypoint list, as direct as the margin allows and independent of grid orientation.
7. **Output.** `RouteResult.Success(points = waypoints, legTimesSec = per-leg haversine at `paceKn()`, distanceM, durationSec, destinationMoved = false, forcedCrossingZoneNames = empty)`. `NoPath` maps straight to `RouteResult.NoPath`, and an off-water end maps to `RouteResult.OutsideWater`.

## Precision of the emitted line

- The emitted route needs **no curve segmentation**: the shortest path never follows a curve, it chords it — around a half-circle coastline it is one straight leg between two tangent waypoints, each standing `obstacleMarginM` off the circle.
- Precision has two knobs, neither of them an emitted shape: the obstacle's own vertex density (a curve stored as N vertices is chording at the polygon's sagitta, already negligible on the dense OSM coastline) and the pull's clearance sampling step, which bounds verification only.
- The leg from the start to the first waypoint is the polyline's own first edge; only inside the forced-free end-disc is it exempt from the margin predicate, and it is otherwise cleared like every leg after it.

## The seam mapping

- `prepare()` fires `load()` on a miss and latches `Ready` once `coastlineReady`; a not-ready engine answers `COASTLINE_NOT_LOADED`.
- `validatePoint(point)` answers `null` when `isWater`, else `RouteRefusalReason.OFF_WATER`.
- `onOriginPositionChanged` / `onDestinationPositionChanged` hold their end and run the pipeline; `null` while the other end is missing, an off-water end answers `RouteResult.OutsideWater`.
- `isReadyToRecompute()` stays `true` — stage 1 reads nothing that expires between asks.

## The harness edit

The shipped factory is `(paceKn: () -> Double) -> RouteEngine`. Stage 1 needs the world, so the `avoid` row's factory becomes `(paceKn, world: () -> AvoidWorld) -> RouteEngine` — the provider answering the live world, readiness riding `coastlineReady`. `MapScreen` supplies the provider from the `CoastlineRepository` instance it already holds (the same holder that already calls `isOnWater`), and the `dummy` row ignores the second argument exactly as it ignores pace. This widens the shared `factory` field type for every row and brings `AvoidWorld` into the registry's imports, so the registry's own "one row plus one class, nothing else changes" note is revised beside it. It revises D3 of [`260924_FEAT_PLN_Route_algorithm-harness.md`](260924_FEAT_PLN_Route_algorithm-harness.md) — the `RouteEngine` seam itself stays untouched.

## Performance — east of the Îles de Lérins to Port de la Salis

The user's corridor, from about 43.508 N 7.065 E to 43.572 N 7.114 E:

- **Corridor box** Δlat 0.064° = 7 125 m, Δlon 0.049° × 111 320 × cos(43.54°) = 3 954 m, inflated 1 NM each side → **10 829 m N-S × 7 658 m E-W**.
- **Cells** ceil(10 829/50) × ceil(7 658/50) = 217 × 154 = **33 418 cells**, ~33–67 KB of tagged state.
- **A\*** near-straight with a haversine heuristic explores a narrow band, ~2 000–6 000 expansions → **~5–15 ms**; a worst detour around the islands expands most of the grid → **≤ ~80 ms**.
- **Harvest** `segmentsInBbox` enumerates ~22 × 16 index cells → **< 1 ms**.
- **Rasterizer** thick-line arithmetic and even-odd fills → **~1–3 ms**, plus the edge-adjacent `isWater` confirmation at ~750–1 500 calls: mainland-adjacent ones stop at 0–1 rings (~0.03–0.05 ms), island-adjacent ones expand 3–10 rings to the mainland (~0.3–0.6 ms) → **~50–160 ms realistic, up to ~250 ms worst**.
- **Pull** ~8.2–9 km path at 12.5 m samples = ~330–360 clearance samples → **~20–70 ms**.
- **Total ~120–250 ms realistic, ~350–450 ms pessimistic** — the 500 ms wall holds. The swing term is the rasterizer's island-adjacent `isWater`; the failure mode is an accidental full-grid water test (33 418 × ~0.1 ms ≈ 3.3 s), which the "no query for non-edge cells" rule exists to prevent and a stage-1 perf assertion must pin.

## Tests

- concave land: a bay with a peninsula between the ends routes around it, never across.
- overlapping hazards: two touching rings read as one blocked mass, the line going around the outside.
- margin clearance: every waypoint and every pulled segment stands ≥ `obstacleMarginM` from land, the end-discs exempt.
- no valid path: land walling the corridor answers `NoPath`, and the grow-once retry is observed.
- cancellation: a cancelled job stops inside the A\* without an answer.
- performance ceiling: the acceptance pair answers under the budget, and the island-adjacent `isWater` confirmation count is asserted rather than assumed.

## Out of scope

The 300 m band (stage 2), regulated speed zones (stage 3), any Settings row, any bake or artifact, any new dependency, and the `forcedCrossingZoneNames` content until stage 3.

## Open points

- Whether the corridor reach grows once or more on `NoPath` is a policy the user may settle.

## Outcome

Shipped 2026-09-24 in two Code hops: the avoid engine now routes around land, islands and hazard rings — `AvoidWorld` with `load()` and the read-only land-ring orientation query, `AvoidGrid` (tagged costed cells with the cell-centre-to-edge margin rule, CCW-ring fill, CW-basin water, half-diagonal closure), `AvoidSearch` (8-neighbour A\*, metres-equivalent cost, deterministic tie-break), `AvoidPull` (source-parameterized two-pointer, ≤ margin/2 sampling, end-disc exemption), the rewritten `RouteAvoidEngine` and the widened harness factory. The Ask hop returned one blocking finding — the performance ceiling unpinned — and a second Code hop closed it and the should-fixes: the perf-ceiling and budget tests, the touching-rings, concave-bay and segment-interior clearance tests, the `margin/2` sampling pin, the dead nullable-provider path dropped, and `LandRingOrientation` relocated to the index layer. Build green under `apk-build.bat`, the focused route suites green. Deviation: the provider is non-null `() -> AvoidWorld` (readiness rides `coastlineReady`), and `AvoidWorld` gained `regionBounds` for the corridor clamp.
