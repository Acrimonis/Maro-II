# whereAmI() — Implementation Status (2026-06-29)

## Current Code State

### Files Changed

1. **`app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt`**
   - Replaced brute-force geometry sampling with unified tangent-guided angular shadow projection
   - New data classes: `AngularInterval`, `ViewCone`
   - New methods: `viewCones()`, `angularShadow()`, `mergeOverlapping()`, `mergeAndComplement()`, `bestBoundaryPoint()`, `corridorDistanceAtBearing()`, `corridorEdgePointAtBearing()`, `circleDistanceAtBearing()`, `circlePointAtBearing()`, `pointCone()`, `splitWrappingCone()`, `buildCone()`
   - Removed: `sampleGeometry()`, `sampleCircle()`, `sampleCorridor()`
   - Rewrote: `closestUnblockedPoint()` — unified loop over view cones
   - `resolveMatch()` / `resolveAllMarkers()` — removed `ProximityConfig` parameter, proximity now comes from marker model
   - `proximityRange()` simplified to `marker.proximityOverrideM!!`
   - `resolveMatch()` — removed stale corridor halfW subtraction (new code returns boundary points, not centreline)
   - Debug logging active (tag: `WIA`) including full trace for Sainte Marguerite marker
   - Direct line test active: computes `landOnLine` for closest boundary point

2. **`app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt`**
   - Added `BboxSegment` data class + `segmentsInBbox()` method for bulk coastline queries

3. **`app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt`**
   - `saveMarker()` / `updateMarker()` — persist computed default proximity when field empty
   - `whereAmI()` — removed `ProximityConfig` construction
   - Removed unused `ProximityConfig` import

### Algorithm Summary

```
closestUnblockedPoint(boat, marker, spatialIndex):
  for each view cone:
    1. Query coastline segments in cone bbox
    2. For each segment: angularShadow() — bearing interval + depth check
       - Depth check: segDist >= zoneDistAt(clamped.mid) → filtered
       - Clamp interval to cone
    3. mergeAndComplement(shadows, cone) → unblocked intervals
    4. bestBoundaryPoint(boat, geometry, unblocked) → closest boundary point
  return best across cones
```

### Fixes Applied

| # | Fix | Status |
|---|-----|--------|
| 1 | Ray-circle/circle-point intersection for circles | ✅ |
| 2 | `corridorDistanceAtBearing()` — bearing-dependent zone distance | ✅ |
| 3 | `clampTo()` returns null on non-overlap | ✅ |
| 4 | Geometry-derived `maxDist` for bbox | ✅ |
| 5 | `corridorEdgePointAtBearing()` — ray-segment intersection for edge lines | ✅ |
| 6 | Stale corridor halfW subtraction removed | ✅ |
| 7 | Proximity persisted at save time, `ProximityConfig` removed | ✅ |
| 8 | Depth check uses clamped interval midpoint (not raw interval edges) | ✅ |

## Remaining Issue

**Partial match rate.** Sainte Marguerite corridor: 2/4 test clicks match (146m, 286m). 2/4 fail with `unblocked=0`.

**Root cause confirmed via direct line test:** `spatialIndex.segmentIntersectsLand(boat, closestBoundaryPoint)` returns `false` — the closest boundary point IS reachable by a straight line with no land. But the angular algorithm produces shadows that cover the full cone, blocking the match.

**Mechanism:** When the boat is close to the island, the island shoreline spans a wide angular range from the boat's perspective. Coastline segments at the cone edges have very large zoneDist values (cap = `maxOf(dP1,dP2,dSeg)+halfW`), so they pass the depth check. These edge shadows merge with middle shadows to cover the full cone, even though the closest boundary point bearing is clear.

**The gap between direct test and angular algorithm:** The direct test checks only the closest boundary point. The angular algorithm checks ALL bearings. The island shoreline at bearings far from the closest approach casts shadows that merge to cover the gap.

**Potential fix direction:** Replace or augment the angular algorithm with the direct line test. If the closest geometric boundary point has a clear line of sight, return it directly without angular shadow analysis. Only fall back to angular shadows if the closest point is blocked.
