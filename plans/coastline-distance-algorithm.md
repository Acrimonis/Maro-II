# Coastline Distance Algorithm — Design

## Decision: Uniform Spatial Grid (Approach B)

Chosen as the Pareto-optimal compromise between speed (80–150× vs brute-force), accuracy (exact), memory (~30 KB), and implementation complexity (~60 lines of Kotlin).

---

## 1. Algorithm Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    PREPROCESSING (once, after coastline loaded)  │
│                                                                  │
│  1. Compute bounding box of all coastline segments               │
│  2. Create a sparse grid (HashMap<GridCell, MutableList<Int>>)   │
│  3. For each segment (a→b), compute its AABB                     │
│  4. Insert segment index into every grid cell overlapping AABB   │
│                                                                  │
│  Cost: O(S) = ~15,000 iterations, ~1–3 ms                        │
│  Memory: ~30 KB (sparse, only occupied cells are stored)         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    QUERY (per GPS fix)                            │
│                                                                  │
│  1. Hash (lat, lon) → (row, col)                                 │
│  2. Collect candidates from cell + 8 Moore neighbors             │
│  3. If candidates empty (edge case): expand search radius        │
│  4. For each candidate segment, compute pointToSegmentDistance   │
│  5. Track minimum distance AND the closest point on coastline    │
│  6. Return CoastlineDistanceResult(distanceM, closestPoint,      │
│                                     segmentId, polylineId)       │
│                                                                  │
│  Cost: O(1) lookup + O(k) distance checks where k ≈ 50–150      │
│  Time: ~0.03–0.1 ms                                              │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Cell Size Selection

```
Trade-off:
  Large cells (2000m) → fewer cells, more candidates per query, faster build
  Small cells (100m)  → many cells, fewer candidates per query, slower build

For this use case (coastline is a thin 1D structure):
  Cell size = 500m

At Nice latitude (43.5°N):
  1° lat = 111,320 m  → 500m = 0.00449°
  1° lon =  80,700 m  → 500m = 0.00620°

Grid dimensions for Nice–Fréjus bbox (43.30–43.80, 6.58–7.38):
  rows = ceil(0.50° / 0.00449°) ≈ 111
  cols = ceil(0.80° / 0.00620°) ≈ 129
  Total cells = 14,319

BUT: Only cells touching the coastline are stored (sparse HashMap).
  Coastline length ≈ 60,000m → ~120 cells touched (60km / 500m)
  Storage: ~120 keys × (16 bytes key + ~40 bytes value) ≈ 7 KB for grid
  Segment references: ~15,000 × 4 bytes = 60 KB (if each segment touches ~1.5 cells)
  Total: ~30–40 KB in practice
```

---

## 3. Data Structures

### 3.1 New Types in `data/model/`

```kotlin
// app/src/main/java/ykws/android/maro/data/model/CoastlineDistanceResult.kt
package ykws.android.maro.data.model

/**
 * Result of a nearest-coastline query.
 *
 * @property distanceMeters Straight-line distance from query point to the
 *                          closest point on ANY coastline polyline (mainland or island).
 * @property closestPoint The actual geographic point on the coastline that is closest.
 * @property segmentId The coastline segment ID containing [closestPoint].
 * @property isMainland True if [closestPoint] is on the mainland coastline,
 *                      false if on an island.
 */
data class CoastlineDistanceResult(
    val distanceMeters: Double,
    val closestPoint: LatLng,
    val segmentId: String,
    val isMainland: Boolean
)
```

### 3.2 New Index Class in `spatial/`

```kotlin
// app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt
package ykws.android.maro.spatial

import ykws.android.maro.data.model.CoastlineDistanceResult
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.LatLng

/**
 * Uniform-grid spatial index for O(1) nearest-coastline queries.
 *
 * Built once after coastline is loaded, then used for all distance queries.
 * Uses a sparse HashMap so only cells that actually contain coastline segments
 * consume memory.
 *
 * @param segments The coastline polylines as loaded from [CoastlineRepository].
 * @param cellSizeM Grid cell size in meters (default 500m). Tune for
 *                  memory/performance trade-off.
 */
class CoastlineSpatialIndex(
    segments: List<CoastlineSegment>,
    cellSizeM: Double = 500.0
) {
    // -- Internal helpers --

    /** A flat descriptor for a single segment (a→b) for fast lookup. */
    private data class SegmentRef(
        val polylineIdx: Int,
        val segmentStart: Int,  // index of 'a' within its polyline
        val a: LatLng,
        val b: LatLng,
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double
    )

    /** Grid cell key (row-major). */
    private data class GridCell(val row: Int, val col: Int)

    // -- State --

    private val segmentRefs: List<SegmentRef>
    private val grid: Map<GridCell, List<Int>>  // cell → list of segmentRef indices
    private val minLat: Double
    private val maxLat: Double
    private val minLon: Double
    private val maxLon: Double
    private val cellSizeLat: Double  // degrees per cell (latitude)
    private val cellSizeLon: Double  // degrees per cell (longitude)

    // The mainland polyline is always index 0 (first in the list).
    // Islands follow at indices 1..N.
    private val mainlandPolylineCount: Int
}
```

### 3.3 Key Methods

```kotlin
/**
 * Queries the nearest coastline point and distance.
 *
 * Algorithm:
 * 1. Hash query point to grid cell (row, col)
 * 2. Collect candidate segments from cell + 8 neighbors (Moore neighborhood)
 * 3. If no candidates (rare edge case for points far offshore),
 *    expand search radius by 1 cell ring at a time
 * 4. For each candidate segment, compute pointToSegmentDistance
 * 5. Track both minimum distance AND the projected closest point
 * 6. Return CoastlineDistanceResult
 *
 * @param latitude  WGS84 latitude (-90..+90)
 * @param longitude WGS84 longitude (-180..+180)
 * @return Distance result; distanceMeters = Double.MAX_VALUE if no
 *         coastline data is loaded.
 */
fun query(latitude: Double, longitude: Double): CoastlineDistanceResult {
    val point = LatLng(latitude, longitude)
    val row = ((latitude - minLat) / cellSizeLat).toInt()
    val col = ((longitude - minLon) / cellSizeLon).toInt()

    val candidates = collectCandidates(row, col)

    var bestDist = Double.MAX_VALUE
    var bestClosestPoint = point  // fallback
    var bestSegmentIdx = -1

    for (segIdx in candidates) {
        val ref = segmentRefs[segIdx]
        val d = SpatialOperations.pointToSegmentDistance(point, ref.a, ref.b)
        if (d < bestDist) {
            bestDist = d
            bestSegmentIdx = segIdx
            // Project point onto segment to get the exact closest point
            bestClosestPoint = projectPointOntoSegment(point, ref.a, ref.b)
        }
    }

    if (bestSegmentIdx < 0) {
        return CoastlineDistanceResult(
            distanceMeters = Double.MAX_VALUE,
            closestPoint = LatLng(latitude, longitude),
            segmentId = "",
            isMainland = true
        )
    }

    val ref = segmentRefs[bestSegmentIdx]
    val polyline = segments[ref.polylineIdx]
    return CoastlineDistanceResult(
        distanceMeters = bestDist,
        closestPoint = bestClosestPoint,
        segmentId = polyline.id,
        isMainland = ref.polylineIdx == 0
    )
}
```

---

## 4. Preprocessing Algorithm (Build Phase)

```
function BUILD(segments, cellSizeM):
    // 1. Compute global bounding box
    minLat = min over all segment endpoints .latitude
    maxLat = max over all segment endpoints .latitude
    minLon = min over all segment endpoints .longitude
    maxLon = max over all segment endpoints .longitude
    // Add 0.5% padding to avoid floating-point boundary issues
    padLat = (maxLat - minLat) * 0.005
    padLon = (maxLon - minLon) * 0.005
    minLat -= padLat; maxLat += padLat
    minLon -= padLon; maxLon += padLon

    // 2. Compute cell size in degrees
    midLat = (minLat + maxLat) / 2
    mPerDegLat = 111_320  // constant
    mPerDegLon = 111_320 * cos(radians(midLat))
    cellSizeLat = cellSizeM / mPerDegLat
    cellSizeLon = cellSizeM / mPerDegLon

    // 3. Flatten all segments into SegmentRef list
    segmentRefs = []
    for polylineIdx, polyline in segments:
        for i in 0..polyline.points.size-2:
            a = polyline.points[i]
            b = polyline.points[i+1]
            ref = SegmentRef(
                polylineIdx, i, a, b,
                min(a.lat, b.lat), max(a.lat, b.lat),
                min(a.lon, b.lon), max(a.lon, b.lon)
            )
            segmentRefs.add(ref)

    // 4. Build sparse grid
    grid = HashMap<GridCell, MutableList<Int>>()
    for segIdx, ref in segmentRefs:
        minRow = floor((ref.minLat - minLat) / cellSizeLat)
        maxRow = floor((ref.maxLat - minLat) / cellSizeLat)
        minCol = floor((ref.minLon - minLon) / cellSizeLon)
        maxCol = floor((ref.maxLon - minLon) / cellSizeLon)
        for r in minRow..maxRow:
            for c in minCol..maxCol:
                cell = GridCell(r, c)
                grid.getOrPut(cell) { mutableListOf() }.add(segIdx)
```

---

## 5. Integration Points

### 5.1 Changes to [`CoastlineRepository`](app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt)

```
Current state:
  - rawPolylines: List<List<LatLng>>          (flat list of point lists)
  - distanceToCoastMeters(lat, lon): Double    (brute-force, distance only)

Target state:
  - spatialIndex: CoastlineSpatialIndex?       (built once after coastline loads)
  - distanceToCoast(lat, lon): CoastlineDistanceResult  (replaces old method)

Changes:
  1. In restoreFromCache() and generate() → after setting rawPolylines,
     also build spatialIndex = CoastlineSpatialIndex(segments).
  2. Replace distanceToCoastMeters() with distanceToCoast() that
     delegates to spatialIndex.query().
  3. Keep isOnWater() — it's a different concern (water/land classification
     vs distance measurement).
  4. Old distanceToCoastMeters() can be deprecated or removed.
```

### 5.2 New file in `spatial/`

```
app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt
  - CoastlineSpatialIndex class (grid build + query)
  - projectPointOntoSegment helper (private)
```

### 5.3 New file in `data/model/`

```
app/src/main/java/ykws/android/maro/data/model/CoastlineDistanceResult.kt
  - CoastlineDistanceResult data class
```

---

## 6. Helper: `projectPointOntoSegment`

The current [`pointToSegmentDistance`](app/src/main/java/ykws/android/maro/spatial/SpatialOperations.kt:38) already computes the projection parameter `t` internally but discards it, returning only the distance. We need a variant that also returns the projected point.

```kotlin
/**
 * Projects point [p] onto line segment [a]→[b] and returns the closest point
 * on the segment. Uses the same local planar projection as [pointToSegmentDistance].
 *
 * @return The point on segment [a]→[b] closest to [p]. If a==b, returns [a].
 */
private fun projectPointOntoSegment(p: LatLng, a: LatLng, b: LatLng): LatLng {
    val midLat = (p.latitude + a.latitude + b.latitude) / 3.0
    val mPerDegLat = EARTH_RADIUS_M * PI / 180.0
    val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))

    val px = p.longitude * mPerDegLon
    val py = p.latitude * mPerDegLat
    val ax = a.longitude * mPerDegLon
    val ay = a.latitude * mPerDegLat
    val bx = b.longitude * mPerDegLon
    val by = b.latitude * mPerDegLat

    val abx = bx - ax
    val aby = by - ay
    val abLenSq = abx * abx + aby * aby

    if (abLenSq == 0.0) return a  // degenerate segment

    val t = ((px - ax) * abx + (py - ay) * aby) / abLenSq
    val tClamped = t.coerceIn(0.0, 1.0)

    val cx = ax + tClamped * abx
    val cy = ay + tClamped * aby

    return LatLng(
        latitude = cy / mPerDegLat,
        longitude = cx / mPerDegLon
    )
}
```

> **Note:** `pointToSegmentDistance` can be refactored to call `projectPointOntoSegment` internally and then compute `haversine(p, projected)` to avoid duplicating the projection math. This is an internal optimization — the public API remains unchanged.

---

## 7. Edge Cases & Robustness

| Edge Case | Handling |
|-----------|----------|
| **Query point in empty grid cell** (far offshore) | Expand search radius by 1 cell ring at a time until candidates are found or the entire grid is exhausted |
| **No coastline loaded** (spatialIndex is null) | Return `CoastlineDistanceResult` with `distanceMeters = Double.MAX_VALUE` and a sentinel `closestPoint` |
| **Degenerate segment** (a == b) | `projectPointOntoSegment` returns `a`; `pointToSegmentDistance` already handles this |
| **Point exactly on the coastline** | Distance = 0.0, closest point = query point (within floating-point epsilon) |
| **Point between mainland and an island** | Grid cell contains segments from both; both are checked; minimum wins |
| **NaN/Inf coordinates** | Clamp row/col to valid grid range before lookup |
| **Grid boundary** | Clamp row/col to `[0, maxRow]` and `[0, maxCol]` |

---

## 8. Unit Test Strategy

Tests go in [`app/src/test/java/ykws/android/maro/spatial/CoastlineSpatialIndexTest.kt`](app/src/test/java/ykws/android/maro/spatial/).

| # | Test | What it validates |
|---|------|-------------------|
| 1 | `singleSegment_exactHit` | Point exactly on a coastline segment returns distance ≈ 0 |
| 2 | `singleSegment_nearby` | Point 100m south returns ~100m |
| 3 | `closestPoint_isOnSegment` | The returned `closestPoint` lies between segment endpoints |
| 4 | `closestPoint_isEndpoint` | Point beyond segment end clamps to nearest endpoint |
| 5 | `mainland_vs_island` | Point between mainland and island returns the closer one |
| 6 | `emptyCell_expandsSearch` | Point far offshore still finds the nearest coast |
| 7 | `noData_returnsMaxValue` | Query before coastline is loaded returns sentinel |
| 8 | `gridBoundary_clamped` | Point outside the grid bounding box is handled gracefully |
| 9 | `identicalTo_bruteForce` | For 100 random points, grid result matches brute-force within 0.01m |
| 10 | `degenerateSegment` | Coastline with a zero-length segment doesn't crash |

Test data: reuse the synthetic Villefranche → La Napoule coastline already used in [`SpatialOperationsTest`](app/src/test/java/ykws/android/maro/spatial/SpatialOperationsTest.kt).

---

## 9. What Does NOT Change

- [`SpatialOperations.kt`](app/src/main/java/ykws/android/maro/spatial/SpatialOperations.kt) — all existing functions remain. Only a new `projectPointOntoSegment` helper is added (or `pointToSegmentDistance` is internally refactored to reuse it).
- [`CoastlineGenerator.kt`](app/src/main/java/ykws/android/maro/data/coastline/CoastlineGenerator.kt) — no changes.
- [`CoastlineSegment.kt`](app/src/main/java/ykws/android/maro/data/model/CoastlineSegment.kt) — no changes.
- [`isOnWater()`](app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt:157) — unchanged, separate concern.

---

## 10. Files Affected (Summary)

| File | Action |
|------|--------|
| `app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt` | **CREATE** — grid index + query logic |
| `app/src/main/java/ykws/android/maro/data/model/CoastlineDistanceResult.kt` | **CREATE** — result data class |
| `app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt` | **MODIFY** — build index on load, replace `distanceToCoastMeters()` |
| `app/src/main/java/ykws/android/maro/spatial/SpatialOperations.kt` | **MODIFY** — add `projectPointOntoSegment` helper |
| `app/src/test/java/ykws/android/maro/spatial/CoastlineSpatialIndexTest.kt` | **CREATE** — 10 unit tests |
