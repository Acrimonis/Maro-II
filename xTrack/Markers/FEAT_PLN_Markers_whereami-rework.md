# Markers — Where Am I? Rework

> **Feature:** Markers | **Subfeature:** whereami-rework
> **Created:** 2026-06-25 | **Status:** Design — confirmed, all review findings addressed
> **Invocation:** Boat marker tap → "where am I?" (auto-refresh timing deferred)

---

## 1. Motivation

Replace the complex `TieredMatchResult` (precision-ranked, spatially-nested) with a simpler model:
> "Smallest first" — spatial containment tree traversed depth-first, leaves before parents.

## 2. Data Model

```kotlin
sealed class WhereAmIMatch {
    data class ZoneMatch(
        val marker: UserMarker,
        val zoneSizeM: Double,            // radius for Circle, width for Corridor
        val distanceToCenterM: Double,    // boat to zone center
        val children: List<WhereAmIMatch> = emptyList()  // immutable, tree built via copy()
    ) : WhereAmIMatch()

    data class ProximityMatch(
        val marker: UserMarker,
        val seaDistanceM: Double          // sea-path distance to closest unblocked boundary point
    ) : WhereAmIMatch()
}

data class WhereAmIResult(
    val allMatches: List<WhereAmIMatch>   // depth-first, leaves-first, capped at 8
)
```

**Pattern:** `children` is immutable `List` (not `MutableList`). Tree built with temp `mutableListOf()` then frozen via `zone.copy(children = temp.toList())`. Same pattern as [existing `MatchResult.ZoneMatch`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt:241).

## 3. Algorithm

### 3.1 Search Radius

**1km search fence** — only markers whose bounding box (expanded by their proximity range) overlaps the boat's 1km search circle are considered. Proximity range formula is NOT capped — the 1km is purely a BBox pre-filter gate. Markers inside the fence use their full natural range (override or formula).

### 3.2 Per-Marker Resolution (unchanged from current `resolveMatch()`)

1. **Zone check** (geometric, no land test) → boat inside geometry → `ZoneMatch`. No distance limit.
2. **Compute proximity range** — override or formula, no cap
3. **At-marker fast path** (≤1m) → skip land check
4. **Land-blocking search** via `closestUnblockedPoint()` using [`CoastlineSpatialIndex.segmentIntersectsLand()`](app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt) — grid-pre-filtered, 10m grazing tolerance
5. Sea-path distance ≤ range → `ProximityMatch`, else skip

### 3.3 Bulk Resolution

```
resolveAllMarkers(boat, markers, spatialIndex, config):
  // ── 1. BBox pre-filter (1km search fence) ──
  candidates = markers.filter { boatInExpandedBbox(boat, it, maxSearchRadius=1000m) }
  
  // ── 2. Per-marker resolve ──
  matches = []
  for marker in candidates:
    result = resolveMatch(boat, marker, spatialIndex, config)
    if result != null → matches.add(result)
  
  // ── 3. Build containment tree ──
  nested = mutableSetOf<String>()  // marker IDs already placed as children
  for zone in matches.filterIsInstance<ZoneMatch>():
    temp = mutableListOf<WhereAmIMatch>()
    for other in matches:
      if other != zone && other.marker.position inside zone.marker.geometry:
        temp.add(other)
        nested.add(other.marker.id)
    if temp.isNotEmpty():
      zone = zone.copy(children = temp.toList())  // immutable snapshot
  
  roots = matches.filter { it.marker.id !in nested }
  
  // ── 4. Depth-first traversal, children before parent, sorted by sizeOf() ──
  display = depthFirstLeavesFirst(roots).take(8)
  
  return WhereAmIResult(display)
```

### 3.4 Traversal Order

```kotlin
fun depthFirstLeavesFirst(nodes: List<WhereAmIMatch>): List<WhereAmIMatch> {
    val result = mutableListOf<WhereAmIMatch>()
    for (node in nodes.sortedBy { sizeOf(it) }) {
        if (node is ZoneMatch && node.children.isNotEmpty()) {
            result.addAll(depthFirstLeavesFirst(node.children))
        }
        result.add(node)
    }
    return result
}

fun sizeOf(match: WhereAmIMatch): Double = when (match) {
    is ZoneMatch -> match.zoneSizeM          // smaller zone = more specific
    is ProximityMatch -> match.seaDistanceM   // closer = more relevant
}
```

`sizeOf()` mixes zone radius with sea distance (different units), but this is correct for the user-facing order: "most specific first." A pin 20m away is more specific than being inside a 5km zone — and `sizeOf()` naturally produces this order (20 < 5000).

### 3.5 Coastline Spatial Index Integration

The [existing `CoastlineSpatialIndex`](app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt) (620 lines, 500m grid cells, 0.03ms queries, ring-expansion, degenerate filtering) is reused. Three changes:

1. **Expose** from [`CoastlineRepository`](app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt:52): drop `private` from `spatialIndex` field
2. **Add method** `segmentIntersectsLand(a, b): Boolean` — queries only grid cells overlapping segment A→B, applies 10m grazing tolerance, short-circuits on first land hit
3. **Inject** into `MarkersViewModel` from `MapScreen` — no second index build

## 4. Performance

| Component | Cost |
|-----------|------|
| BBox pre-filter (1km fence) | O(N), ~20 markers max, 4 float comparisons each |
| Per-marker zone check | O(1), haversine/point-to-segment |
| Per-marker proximity with spatial index | O(S × M_local) where S≈20 samples, M_local≈300 edges = ~6,000 tests |
| Containment tree build | O(K²) where K≤20 matches |
| Depth-first traversal | O(K) |

**Worst case (20 markers, all proximity, clear sea):** 20 × 6,000 = 120,000 segment tests → **< 5ms** on `Dispatchers.Default`.

## 5. What Gets Removed

From [`MarkerMatcher.kt`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt):

| Remove | Reason |
|--------|--------|
| `precisionComparator` | Replaced by `sizeOf()` + depth-first traversal |
| `geometryTypeRank()` | Unused |
| `distanceFromResult()` | Unused |
| `isMatchInsideZone()` | Rewritten inline in tree building |
| `MatchResult.NoMatch` | Filtered out before tree building (null) |
| `TieredMatchResult` | Replaced by `WhereAmIResult` |
| `MatchResult` sealed class | Migrated to `WhereAmIMatch` |
| `segmentIntersectsPointList()` | Replaced by `CoastlineSpatialIndex.segmentIntersectsLand()` |

**Kept:**
- `resolveMatch()` — logic unchanged, takes `CoastlineSpatialIndex` instead of `CoastlineData`
- `closestUnblockedPoint()` — updated to use spatial index
- `isInsideGeometry()`, `distanceToClosestGeometryPoint()`
- `proximityRange()` — unchanged (no cap, 1km is BBox search fence only)
- `sampleGeometry()`, `sampleCircle()`, `sampleCorridor()`
- `boatInExpandedBbox()` — add `maxSearchRadius` parameter (1000m)

## 6. What Gets Added

| New | Where |
|-----|-------|
| `WhereAmIMatch` / `WhereAmIResult` | [`MarkerMatcher.kt`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt) |
| `segmentIntersectsLand(a, b): Boolean` | [`CoastlineSpatialIndex.kt`](app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt) |
| `depthFirstLeavesFirst()` | [`MarkerMatcher.kt`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt) |
| `sizeOf()` | [`MarkerMatcher.kt`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt) |
| `maxSearchRadius` param (1000m) | [`MarkerMatcher.kt`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt) — `boatInExpandedBbox()` |
| Public getter for `spatialIndex` | [`CoastlineRepository.kt`](app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt) |

## 7. Integration Points

### 7.1 MarkersViewModel

- `_matchResult` type: `TieredMatchResult?` → `WhereAmIResult?`
- `coastlineData: CoastlineData?` → `coastlineIndex: CoastlineSpatialIndex?`
- `whereAmI()` updated to pass index instead of coastline data

### 7.2 MapScreen

- Inject `coastlineRepository.spatialIndex` into `markersViewModel.coastlineIndex`

### 7.3 MarkerDrawer — MatchResultContent (temporary stub)

Current UI renders recursive nested tree. New `allMatches` is flat. Temporary stub to compile:

```kotlin
// Temporary — shows raw data, full sentence format deferred
allMatches.forEach { match ->
    Text(when (match) {
        is ZoneMatch -> "in ${match.marker.name}"
        is ProximityMatch -> "next to ${match.marker.name}"
    })
}
```

## 8. Deferred

- Sentence format string ("next to P1, in Z2, next to C3")
- Proper UI component in `MatchResultContent`
- Auto-refresh timing (currently: boat marker tap only)

## 9. Key Files

| File | Change |
|------|--------|
| [`MarkerMatcher.kt`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt) | New types, revised `resolveAllMarkers()`, depth-first traversal, 1km BBox fence, remove dead code |
| [`CoastlineSpatialIndex.kt`](app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt) | Add `segmentIntersectsLand(a, b): Boolean` |
| [`CoastlineRepository.kt`](app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt) | Expose `spatialIndex` (drop `private`) |
| [`MarkersViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt) | `matchResult` type updated, `coastlineIndex` injection |
| [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | Inject `spatialIndex` into ViewModel |
| [`MarkerDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt) | Temporary flat-list UI stub for `MatchResultContent` |

## 10. Examples

### Ex 1 — Simple nested
```
Boat inside Z2 (circle 150m), P1 (pin) at 30m inside Z2
→ ZoneMatch(Z2, children=[ProximityMatch(P1)])
→ Display: [ProximityMatch(P1), ZoneMatch(Z2)]  → "next to P1, in Z2"
```

### Ex 2 — Double nested
```
Boat inside Z2 (circle 150m), Z2 inside C3 (corridor 400m), P1 at 30m inside Z2
→ ZoneMatch(C3, children=[ZoneMatch(Z2, children=[ProximityMatch(P1)])])
→ Display: [ProximityMatch(P1), ZoneMatch(Z2), ZoneMatch(C3)]  → "next to P1, in Z2, in C3"
```

### Ex 3 — Flat proximity only
```
Boat near P1 (50m), P2 (120m), C5 corridor (300m). Nothing contains boat.
→ Display: [ProximityMatch(P1), ProximityMatch(P2), ProximityMatch(C5)]  → "next to P1, next to P2, next to C5"
```
