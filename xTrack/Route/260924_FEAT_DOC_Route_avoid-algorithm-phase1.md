<!-- scope: feature -->
# Route — avoidance algorithm, phase 1: avoid land

**Created:** 2026-09-24 · **Branch:** `feature/route-avoid` · **Shipped:** 2026-09-24

The shipped avoid engine, documented as phase 1 of the avoidance algorithm — the water a route may use. Phase 1 keeps the line clear of land, islands and hazard rings; phase 2 adds the 300 m band, phase 3 the regulated speed zones, both as additive polygon sources over the same rasterizer, A* and pull.

## The pipeline

One ask runs seven stages in [`RouteAvoidEngine.searchOnce()`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:122):

1. **Corridor.** The start-aim bounding box inflated by `route.avoid.corridorReachM`, clamped to the region's bounds; a truncated box is accepted — it is the searchable water, not a refusal. Recomputed per ask.
2. **Harvest.** `segmentsIn(box)` returns the ring and basin edges, `openCoastIn(box)` the ordered mainland-coast polylines; each ring edge carries its orientation, so a CCW ring is land and a CW basin stays water.
3. **Rasterize.** [`rasterize`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidGrid.kt:94) paints a margin band over every edge and coast segment, fills CCW-ring interiors by an even-odd scanline, and closes each open-coast polyline on its land side at the cap — into a tagged, costed cell state (`FREE`/`LAND`/`BAND`/`ZONE` plus a source cost in metres). Phase 1 uses `FREE` and `LAND` only.
4. **Ends.** Both end cells are forced free — the margin binds the path, not where the boat already is — and an off-water end is refused before the search.
5. **A\*.** [`AvoidSearch.search`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidSearch.kt:40) walks the free cells: eight neighbours, diagonal cost `√2 × cellM`, a metres-equivalent g-cost so the haversine heuristic stays admissible when phases 2–3 add `BAND`/`ZONE` costs, a deterministic tie-break, and a cancellation check every few hundred expansions.
6. **Taut pull.** [`AvoidPull.pull`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidPull.kt:28) collapses the cell path into the straight waypoints the margin allows, sampled at `≤ marginM / 2` with the end-discs exempt.
7. **Corner snap.** [`TangentCorners.corners`](../../app/src/main/java/ykws/android/maro/spatial/avoid/TangentCorners.kt:37) collects the convex offset corners, and the engine moves each pulled bend onto its nearest corner **only when both neighbouring legs stay clear**, then pulls taut again — a sharp headland gets its true corner, a smooth island keeps its grid line.

The whole pipeline runs on `Dispatchers.Default` — the search is compute-only, and the caller's state writes resume on the main thread.

## The world interface

[`AvoidWorld`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidWorld.kt:25) is the engine's own vocabulary — the feature imports no coastline type beyond it: `segmentsIn` · `openCoastIn` · `isWater` · `distanceToCoastM` · `coastlineReady` · `regionBounds` · `load()`. The one real coupling it added is the read-only land-ring orientation query on the index (`polylineIdx → open / CCW-ring / CW-basin`), because the index treats CCW rings as land and keeps CW marina basins as water.

## The keys

| Key | Default | Meaning |
|---|---|---|
| `route.avoid.obstacleMarginM` | 25 | clearance the route keeps off land, islands and hazard rings |
| `route.avoid.gridCellM` | 50 | the corridor grid's cell side |
| `route.avoid.corridorReachM` | 1852 | how far the corridor box reaches past the start-aim line (1 NM) |
| `route.avoid.zone300MarginM` | 25 | the band's own margin, read only from phase 2 |

All four read through `AppConfig` with a clamp on parse, one home each — no Settings row until one is asked for.

## The taut pull's history, on device evidence

- **Corner-graph A\*.** The first taut pass held every convex corner and searched the complete graph over them; on the phone it measured **7.3 s over 191 corners** — the quadratic pair scan that had killed the removed taut tracer.
- **Greedy tangent walk.** Its replacement hopped corner to corner, six clearance checks on sparse fixtures — but the user's 19:19 route export showed it jittery on the real coastline, chasing digitization noise.
- **Grid A\* + corner snap + pull.** The shipped pass: the grid A* owns the order, the corners own the exact points, and the verified snap plus a second pull leaves a clean taut line.

The ANR that opened the pass — the first pipeline ran on the main thread — is closed by `withContext(Dispatchers.Default)` around `search()`.

## Performance

Budget ≤ 500 ms wall. The Lérins-to-Salis-shaped corridor answers under it in the engine test; the real-corridor device measurement remains the user's.

## Phases 2 and 3

The cell already ships as a tagged, costed state and the pull as a source-parameterized predicate, so the 300 m band (`BAND`) and the regulated zones (`ZONE`) land as additive sources over the same rasterizer, A* and pull — never as rework.

## Files and tests

- `spatial/RouteAvoidEngine.kt` — the pipeline; `spatial/avoid/AvoidWorld.kt` · `AvoidGrid.kt` · `AvoidSearch.kt` · `AvoidPull.kt` · `TangentCorners.kt`.
- `data/coastline/CoastlineRepository.kt` — the read-only land-ring orientation query.
- Tests: `AvoidStage1Test` (rasterizer, A*, pull), `TangentCornersTest` (corner collection), `RouteAvoidEngineTest` (headland corners, circle margin, digitized-coast collapse, Lérins budget).
