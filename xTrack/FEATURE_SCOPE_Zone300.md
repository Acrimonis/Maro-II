# Feature: Zone300

**Status:** Active
**Created:** 2026-06-03T13:00:00.000Z
**Last Modified:** 2026-06-05T00:00:00.000Z
**Active Subfeature:** distancetocoast
**Description:**
Identify and render a regulatory band 300 m from the coastline (all coasts,
islands included) within which a 5-knot speed limit applies. The app already
computes distance-to-coast; this feature derives the 300 m boundary from that
and makes the zone visually identifiable on the map.

**One-liner:** Render the 300 m regulatory 5-knot speed-limit band along all coastlines.

## Subfeatures
### trace  [ ]

#### Todos

#### Rules

#### Key Files

### drawZone  [ ]
How to draw/derive the 300 m line and zone so it is identifiable on the map.

#### Todos

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

#### Key Files
- `docs/300MLineDesign.md` — design notes: distance-field isoline method, line+fill rendering, DP→Chaikin smoothing, single-source distance metric.

### 300mDesign  [ ]
Detailed implementation plan for the 300 m line/zone (geometry, APIs, rendering).

#### Todos
- [x] chaikin() smoothing helper + unit tests
- [x] MarchingSquares.contour() + tests  (SpatialOperations.marchingSquares)
- [x] Coastal-strip mask sampler (0–500 m ribbon, 15 m grid) + Zone300Data model  (Zone300Builder)
- [x] groupRings() for fill holes  (uniform water-only fill; land=!isOnWater=hole)
- [x] Band cache (in CoastlineSerializer protobuf, cache-aside in loadCoastline)
- [x] isIn300mZone() / distanceTo300mZone() repo APIs + tests
- [x] ViewModel: expose zone300 + fold zone status into throttled pipeline
- [x] drawZone300() + fix overlay refresh + z-order + zoom gate
- [x] Dashboard text/label wiring
- [ ] Visual pass on device; tune DP_EPSILON_M / CHAIKIN_ITERATIONS (MASK_RES fixed at 15 m)
      ↳ band renders on device; fixed red-line breaks (now classify seaward by
        out-cell deep-water vs land, robust in narrow bands) + denoise/mergeLines/
        small-hole-drop; added "Bande 300 m" fast-rebuild button. Awaiting device
        re-check of the narrow-band fix.

#### Rules

#### Key Files
- `docs/300MLinePlan.md` — detailed implementation plan (build order, signatures, file anchors).

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
