<!-- scope: feature -->

# 300 m Line — Implementation Plan

Canonical execution plan for the **Zone300 / 300mDesign** subfeature. Rationale
and ELI16 live in [`300MLineDesign.md`](300MLineDesign.md); this is the "how to
build it" companion, mapped to the 10-item `300mDesign` todo list and grounded in
the current code.

---

## 1. Feature context

The app already computes **distance-to-coast** (any coast, islands included). This
feature adds the French regulatory ***bande des 300 m*** — the strip within 300 m
of any shoreline where the speed limit is **5 knots** — drawn so it's identifiable
on the map, plus two query APIs.

**Core principle — the line *is* the metric.** The 300 m boundary is by definition
`{ distanceToCoast == 300 }`, the same field the boat marker already uses. We draw
a *visualization* of that threshold (never an independent geometry), so the map and
the logic can never disagree. Both APIs fall out analytically; the drawn band is
precomputed once and cached.

### Locked decisions (from the design + session)

- **Geometry:** marching-squares contour of the binary mask
  `isOnWater && distanceToCoast ≤ 300`. Single-valued field → can't self-intersect,
  auto-merges across <600 m channels and clustered islands. **No perpendicular
  offset.**
- **Fill = water only.** Land is `!isOnWater()` (island interior *or* mainland) and
  is **never** filled — one uniform rule. Around an island the band is a ring whose
  land middle is a polygon **hole**, derived from mask-ring orientation. **No
  paint-over, no island special-casing.**
- **Grid:** uniform spacing `min(coastline meanSpacingM, 15 m)` (≤ ±7.5 m), **no
  variable resolution**. Sample only the **0–500 m near-coast ribbon**; ignore open
  sea / beyond 500 m for drawing.
- **Seaward-only smoothing:** smooth/simplify only the seaward (d≈300) runs
  (Douglas–Peucker → Chaikin); **snap landward runs to actual coastline vertices** so
  the fill is flush with the coast and never bleeds onto land. The red line = seaward
  runs only.
- **Every closed ring gets a band** — mainland or islet, no minimum-size filter.
- **APIs (analytic, grid-independent):** `isIn300mZone = isOnWater(lat,lon,d) && d≤300`;
  `distanceTo300mZone = d − 300` (signed). Readout in metres, → km above 1000 m;
  valid at any range (even far offshore where no line is drawn).
- **Accuracy:** ~5 % (≈ ±15 m) — it's a visual control.
- **Render** red line + translucent fill via the **same renderer/style path as the
  coastline overlay**.
- **Cache:** band lives in `CoastlineData.zone300`, serialized in the **same**
  protobuf `.bin` (same region key, same `Régénérer` trigger). No new cache file.
- **Progressive load:** emit `Ready(zone300 = null)` first (map usable instantly),
  then the background builder emits `Ready(zone300 = built)`.
- **Performance:** zoom-gate overlay (hide < zoom 13); parallelize mask sampling;
  **tiling SKIPPED for v1** (escalation only — see §9).

---

## 2. Todo → work mapping

| `300mDesign` todo | Section |
|---|---|
| chaikin() smoothing helper + unit tests | §3.1 |
| MarchingSquares.contour() + tests | §3.1 |
| Coastal-strip mask sampler (0–500 m ribbon, 15 m grid) + Zone300Data model | §3.3, §3.4 |
| groupRings() for fill holes (+ line-only fallback) | §3.3 (step 8), §9 |
| Band cache (serializer, cache-aside in loadCoastline) | §3.5, §3.6, §6 |
| isIn300mZone() / distanceTo300mZone() repo APIs + tests | §3.6, §7 |
| ViewModel: expose zone300 + fold zone status into throttled pipeline | §3.7 |
| drawZone300() + fix overlay refresh + z-order | §3.8 |
| Dashboard text/label wiring | §3.8 |
| Visual pass on device; tune DP_EPSILON_M / CHAIKIN_ITERATIONS | §10 |

---

## 3. Work breakdown

### 3.1 Pure geometry — `spatial/SpatialOperations.kt` (extend existing object)
Reuse `douglasPeucker`, `pointToSegmentDistance`, `projectPointOntoSegment`,
`haversine`, `EARTH_RADIUS_M`. Add:
```kotlin
fun chaikin(points: List<LatLng>, iterations: Int = 1, closed: Boolean = false): List<LatLng>
fun marchingSquares(mask: BooleanArray, cols: Int, rows: Int): List<List<GridPt>>  // closed rings, grid coords
data class GridPt(val col: Double, val row: Double)
```
`chaikin` needs `closed` (seaward runs are *open*; a fully-seaward channel ring is
*closed*). Binary mask → mid-edge crossings, no interpolation. Every mask node has
degree 0 or 2 → clean disjoint loops (saddle cases 5/10 resolved by fixed pairing).

### 3.2 Index support — `spatial/CoastlineSpatialIndex.kt`
The landward snap needs *which* coastline polyline + vertex a point maps to.
`query()` already computes `bestSegIdx` / `ref.polylineIdx` internally — expose them:
extend `CoastlineDistanceResult` (or add a sibling query) with `polylineIdx: Int` and
the segment's start-vertex index. Small additive change.

### 3.3 Band builder — `spatial/Zone300Builder.kt` (NEW, pure JVM)
```kotlin
class Zone300Builder(
  index: CoastlineSpatialIndex, segments: List<CoastlineSegment>,
  refLat: Double,                 // = CoastlineData.metadata.projectionRefLat
  bandM = 300.0, ribbonM = 500.0,
  cellM = 15.0, dpEpsilonM = 15.0, chaikinIters = 2,
) { fun build(): Zone300Data }
```
Pipeline (planar `xM/yM` frame via `refLat`, consistent with stored
`CoastlinePoint.xM/yM`):
1. **`ribbonCells()`** — walk every coastline edge, stamp grid cells within
   `ribbonM + cellM` of each edge AABB. Cheap, *before* distance math → confines work
   to the coastal strip.
2. **`sampleMask()`** — per candidate cell center: `r = index.query(lat,lon)`;
   `inZone = r.distanceMeters ≤ bandM && isOnWater(lat,lon,r.distanceMeters)`. **Hot
   loop — parallelize** over `Dispatchers.Default` (index is immutable/thread-safe).
3. **`marchingSquares()`** → closed rings → map `GridPt` → `LatLng`.
4. **`classifyVertices()`** — per vertex: `distanceToCoast < 150` → *landward*, else
   *seaward*. Split each ring into maximal same-label runs (alternating).
5. **Seaward runs:** `douglasPeucker(run, 15)` then `chaikin(run, 2, closed=false)`;
   collect into `seawardLines` (the red line).
6. **Landward runs:** replace with the actual coastline sub-path between the run's
   endpoints (query → polylineIdx + vertex index, walk real polyline vertices). Flush
   to coast, never on land.
7. **`stitchRing()`** seaward(smoothed)+landward(snapped) → one cleaned closed ring.
8. **`groupRingsToPolygons()`** — signed area (shoelace) → outers CCW / holes CW;
   assign each hole to the containing outer (point-in-polygon). Depth-1 nesting only.
   → `List<BandPolygon>`.

**Edge cases (tests):** fully-seaward ring (narrow channel, zero landward verts) →
`chaikin(closed=true)`, no snap; isolated island → two rings (outer all-seaward 300 m
+ inner all-landward island coast = the hole); tiny islet → no size filter, guard
degenerate <4-vertex rings; ribbon/bbox edge → terminate seaward run at the boundary.

### 3.4 Model — `data/model/Zone300Data.kt` (NEW) + `CoastlineData` field
```kotlin
@Serializable data class Zone300Data(
  val fillPolygons: List<BandPolygon>,    // water-only fill, land = holes
  val seawardLines: List<List<LatLng>>,   // red boundary, seaward runs only (open)
  val gridCellM: Double, val bandM: Double = 300.0)
@Serializable data class BandPolygon(val outer: List<LatLng>, val holes: List<List<LatLng>> = emptyList())
```
Add `val zone300: Zone300Data? = null` to `CoastlineData` (nullable → existing
constructors/tests unaffected). `CoastlineState.Ready` gets `val zone300 get() = data.zone300`.

### 3.5 Serializer — `app/src/main/proto/coastline.proto` + `CoastlineSerializer.kt`
Backward-compatible new fields (old caches → `zone300 = null`):
```proto
message CoastlineCache { ...; Zone300 zone300 = 12; }
message Zone300 { double grid_cell_m=1; double band_m=2; repeated BandPolygon fill=3; repeated Polyline seaward=4; }
message BandPolygon { Polyline outer=1; repeated Polyline holes=2; }
```
Reuse the existing packed-float `Polyline` (lat/lon only). Add
`zone300ToProto`/`fromProto`; set only when non-null; gate read with `hasZone300()`.

### 3.6 Repository — `data/coastline/CoastlineRepository.kt`
- Analytic APIs: `isIn300mZone`, `distanceTo300mZone`, `getZone300()`.
- **Cache-miss** in `loadCoastline`: build index → emit `Ready(null)` →
  `Zone300Builder(...).build()` (in `withContext(ioDispatcher)`, progress phase
  `"Bande des 300 m"`) → `data.copy(zone300=band)` → `writeToCache` → emit `Ready(band)`.
  Use `metadata.projectionRefLat`, `cellM = min(metadata.meanSpacingM, 15.0)`.
- **Cache-hit:** band already in `cached.zone300`; build index; do **not** rebuild.
  Pre-feature cache (`zone300==null`) → overlay stays off until next refresh.
- `refreshCoastline` already deletes cache → cache-miss path rebuilds. No change.

### 3.7 ViewModel — `ui/map/CoastlineViewModel.kt`
Reuse the distance already computed in the throttled `mapLatest` block (no extra
query). Extend `ShoreState` with `inZone: Boolean`, `distToZone: Double?`; expose
`inZone: StateFlow<Boolean>`, `distanceToZone: StateFlow<Double?>`, and
`zone300: StateFlow<Zone300Data?>` (from `repository.getZone300()` once Ready).

### 3.8 Rendering — `ui/map/MapScreen.kt`
- Import `org.osmdroid.views.overlay.Polygon`. Constant `ZONE_MIN_ZOOM = 13.0`.
- `drawZone300(mapView, zone, zoomLevel)`: no-op if `zone==null || zoom<ZONE_MIN_ZOOM`.
  Per `BandPolygon`: `Polygon{ points=outer→GeoPoint; holes=holes→GeoPoint;
  fillPaint=ARGB(~0x33,red); outlinePaint.strokeWidth=0 }`. Per `seawardLines` run:
  red `Polyline` (coastline style). **Draw order: fill → coastline → red line.**
- **Lifecycle fix (critical):** `CoastlineMapView.update{}` currently
  `removeAll { it is Polyline }` — wipes the band's red line, leaks stale `Polygon`s.
  Change to `removeAll { it is Polyline || it is Polygon }`, then `drawZone300(...)` +
  `drawCoastline(...)`. Thread `zone300` + `zoomLevel` into `CoastlineMapView` params
  so crossing zoom 13 re-runs `update{}`.
- Dashboard (`DashboardPanel`): zone label near the distance `Text`, driven by
  `inZone`/`distanceToZone`: inside → "à {|d|} avant la fin de zone — 5 nœuds",
  outside → "à {d} de la zone des 300 m".

---

## 4. Architecture / data flow

```
loadCoastline (cache MISS):
  generate() ─▶ CoastlineData (no band)
            ─▶ spatialIndex = CoastlineSpatialIndex(allSegments)
            ─▶ emit Ready(zone300 = null)              ← map shows now
            ─▶ Zone300Builder(index, segments, refLat).build()   (parallel, bg)
            ─▶ data.copy(zone300 = band) ─▶ writeToCache ─▶ emit Ready(band)
loadCoastline (cache HIT):
  readFromCache ─▶ CoastlineData (band included) ─▶ build index ─▶ Ready(band)
```

---

## 5. Tuning constants

| Const | Value | Role |
|---|---|---|
| `ZONE_DISTANCE_M` | 300.0 | band half-width |
| `cellM` | `min(meanSpacingM, 15.0)` | mask resolution (fixed policy) |
| `ribbonM` | 500.0 | near-coast sampling strip |
| `DP_EPSILON_M` | 15.0 | seaward simplify tolerance |
| `CHAIKIN_ITERATIONS` | 2 | seaward corner rounding |
| `ZONE_MIN_ZOOM` | 13.0 | hide band below this zoom |
| fill / line | `~0x33FF0000` / red | translucent fill / red boundary |

---

## 6. Performance

Ribbon ≈ 80–100 km coast × 500 m ≈ 50 km²; 15 m cells ≈ ~200–250k queries × ~0.15 ms
≈ **10–40 s naive**. Hidden by progressive load, but parallelize anyway. Levers, in
order: (1) **parallelize** `sampleMask` over cores (immutable index) → ~5–8× →
single-digit seconds; (2) **tighter ribbon** (~250–400 m brackets the contour);
(3) two-pass coarse→fine only if still slow. v1 = (1)+(2), uniform 15 m preserved.

---

## 7. Testing (pure JVM, JUnit4, `app/src/test/java/.../spatial/`)

Extend `SpatialOperationsTest`: `chaikin` (open preserves endpoints/within-hull;
closed wraps, 2× verts), `marchingSquares` (single square → 1 ring; donut → outer +
inner opposite winding; empty → none; full → 1).
New `Zone300BuilderTest` (synthetic coasts, coarse `cellM` for speed): straight coast
(seaward 300±15, landward == input vertices), convex cape (arc), narrow channel
(fully-seaward ring, mid-point `inZone`), isolated island (BandPolygon + one hole,
opposite winding), tiny islet (no crash), `classifyVertices` threshold, API parity
(`isIn300mZone`/`distanceTo300mZone` agree with drawn line ±15 m), serializer
round-trip. Run `.\gradlew testDebugUnitTest`.

---

## 8. Build sequence & top risks

1. `chaikin` + `marchingSquares` + tests → 2. extend `query` (polylineIdx+vertex) →
3. `Zone300Data`/`BandPolygon` + `CoastlineData.zone300` → 4. `Zone300Builder` + tests
(coarse cell) → 5. proto + serializer round-trip → 6. repository build-site + APIs +
progressive emit → 7. viewmodel flows → 8. `MapScreen` draw + lifecycle fix + zoom gate
→ 9. perf pass if needed.

**Risks:** (1) **mask cost** (~10–40 s naive) — design `sampleMask` parallel/stateless
from the start; (2) **overlay lifecycle** — the `Polyline`-only purge wipes the red
line / leaks `Polygon`s; fix `update{}` + draw order; (3) **classify+snap robustness**
on channels/islets/edges — guarded by synthetic-coast tests before device testing.

---

## 9. Runtime repaint & tiling (escalation path — SKIPPED for now)

> **Decision:** tiling is **skipped for v1.** Ship the vector overlay with
> simplify + zoom-gating and measure. Notes below are the documented escalation path
> *only if* device measurement later shows pan jank.

Three distinct costs: **(a)** geometry compute = one-time, cached, retriggerable like
the coastline; **(b)** `drawZone300()` building overlays = only on data change; **(c)**
osmdroid repainting overlays each pan frame = the only live cost (line ≈ coastline;
the **translucent fill** is the new weight). v1 keeps it cheap via simplify + low
vertex budget + zoom-gating. **Only if panning janks**, escalate to **raster tiling**
(pre-bake the band into transparent tiles, rendered lazily per `(z,x,y)` and
disk-cached — *not* vector tiling). The vector geometry cache stays the source of
truth.

---

## 10. Verification

1. `.\gradlew testDebugUnitTest` — all new + existing tests pass.
2. `apk-build.bat` → run on device, region `nice-frejus`.
3. Visual: band continuous across bays; islands show donut (water ring, land clear);
   line+fill match coastline style; red line only seaward; **no fill on land**; smooth
   at zoom ≥ 13, hidden below; no jank on pan/fling; band appears a few seconds after
   first load then instant on subsequent loads (cache).
4. Cross-check the dashboard readout against the drawn line at the boat marker (agree
   within tolerance); toggle in/out of the band and confirm the label switches.
