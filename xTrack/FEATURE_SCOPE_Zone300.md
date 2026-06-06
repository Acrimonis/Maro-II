# Feature: Zone300

**Status:** Active
**Created:** 2026-06-03T13:00:00.000Z
**Last Modified:** 2026-06-06
**Active Subfeature:** (none)
**Description:**
Identify and render a regulatory band 300 m from the coastline (all coasts,
islands included) within which a 5-knot speed limit applies. The app already
computes distance-to-coast; this feature derives the 300 m boundary from that
and makes the zone visually identifiable on the map.

**One-liner:** Render the 300 m regulatory 5-knot speed-limit band along all coastlines.

## Subfeatures
### drawZone  [x]
Derive, draw, and query the 300 m band — geometry, APIs, rendering. (Consolidates
the former empty `trace` stub, the `drawZone` design notes, and the `300mDesign`
implementation plan.)

#### Todos
- [x] chaikin() smoothing + marchingSquares() contour (+ tests)
- [x] Coastal-strip mask sampler (0–500 m ribbon, 15 m grid) + Zone300Data model
- [x] groupRings() fill holes (+ small-hole drop for noise)
- [x] Band cache (protobuf, cache-aside in loadCoastline)
- [x] isIn300mZone() / distanceTo300mZone() repo APIs (+ tests)
- [x] ViewModel: zone300 + zone-status flows
- [x] drawZone300() + overlay lifecycle + zoom gate (11)
- [x] Dashboard label + "Bande 300 m" fast-rebuild button (with progress)
- [x] Fix red-line breaks — out-cell seaward classification (robust in narrow bands)
- [x] Fix fill chords / dark overlaps — simple per-vertex polygon (no sub-path stitch)
- [x] Fix harbour/bay holes — **flood-fill water mask + end-caps (current approach)**
- [x] Tried signed-distance band → **REVERTED**: mainland orientation unreliable (OSM stitching reverses segments) → band inverted onto land. Orientation methods need a generator orientation fix first. Cleanup (signedDistance/audit) deleted.
- [x] Final on-device visual pass — **validated on device**: water-side only, islands donut, no land-side mirror. Fixed by keeping only the open-sea flood component anchored at the deepest-water cell (`Zone300Builder.markSeaComponent`) — inland pockets fed by ray-cast misclassification are separate components and get dropped. Zone300 band done.
- [x] Isolated-feature bands — far-offshore hazards (Phare de la Fourmigue, Basses de la Chrétienne) were dropped by that same component filter (their 500 m candidate ribbon is disconnected from the main sea). Fixed by flooding through **all open water**, not just the ribbon (`Zone300Builder.floodWater`); coast + `capOpenEnds` still wall off inland. Regression test `isolated feature far offshore still gets its own band` added. ⏳ pending on-device re-validation.

#### Rules
- Merge overlapping/intersecting 300 m zones into a single union (opposite shores, clustered islands).
- Performance is a hard constraint: precompute + cache the geometry, no per-frame recompute, keep vertex count low enough to stay smooth during pan/zoom.
- Smooth/simplify the 300 m line so it is clean and practical for navigation, not jagged.
- Expose `isIn300mZone()` and `distanceTo300mZone()` as part of this subfeature.
- Grid: uniform `min(coastline-gen resolution, 15 m)`, no variable resolution.
- Sample only the 0–500 m near-coast ribbon; ignore open sea / beyond 500 m for drawing.
- Smooth the seaward (300 m) edge only; snap the landward edge to coastline vertices — fill stays flush with the coast, never on land.
- Render red line + transparent fill via the same renderer/style path as the coastline overlay.
- Accuracy target ~5 % (≈ ±15 m).
- Readout in metres, switch to km above 1000 m; metric is analytic/grid-independent, valid at any range.
- Every closed coastline ring (mainland or islet) gets a band — no minimum-size filter.
- Cache/rebuild via the same logic as coastline generation.
- Band water/land via **signed distance to coast** (orientation: water on right of travel; pseudonormal at vertices) — sd>0 water, sd<0 land, band = 0<sd≤300. (Superseded the ray-cast and the flood-fill mask.)

#### Key Files
- `docs/300MLineDesign.md` — design notes: distance-field isoline, line+fill rendering, smoothing, single-source metric.
- `docs/300MLinePlan.md` — implementation plan (build order, signatures, file anchors).
- `spatial/Zone300Builder.kt` — band builder (signed-distance mask → marching squares → classify → fill/line).
- `spatial/CoastlineSpatialIndex.kt` — `signedDistanceToCoast` (orientation + pseudonormal); `auditWaterClassification` in repo.

### distancetocoast  [x]
Validate/fix the core nearest-coast distance query (`CoastlineSpatialIndex.query`).
Symptom: occasional invalid jumps in the distance value along curves.

**Root cause + fix:** query stopped at the first non-empty grid ring → could miss a
closer segment one ring out → over-estimates / discontinuous jumps at cell
boundaries. Now expands with a provably-safe stop (`bestDist ≤ ring·cellSize·0.95`)
so it always returns the true nearest. Validated by a brute-force grid-sweep test.

#### Todos
- [x] Analyse + fix early-stop nearest-neighbour bug in `query`
- [x] Add `CoastlineSpatialIndexTest` (brute-force grid sweep + edge cases)

#### Rules
- Nearest-segment search must expand until provably safe, never stop at the first
  non-empty ring.

#### Key Files
- `spatial/CoastlineSpatialIndex.kt` — uniform-grid nearest-segment query.
- `app/src/test/.../spatial/CoastlineSpatialIndexTest.kt` — correctness sweep.

## Todos

## Rules

## Key Files
