# whereAmI() — Comprehensive Fix Plan

> **Feature:** Markers | **Subfeature:** whereami-gaps
> **Created:** 2026-06-30 | **Status:** Plan — ready for implementation
> **Based on:** Deep code analysis of MarkerMatcher.kt (893 lines), MarkersViewModel.kt, MarkerDrawer.kt

## Summary

7 issues identified. 3 high-severity (🔴), 2 medium (🟡), 2 low (🟢).
Implementation order: trivial fixes first (build momentum, verify toolchain) → core fix → accuracy improvements → optimization.

---

## Fix 1 — Safe proximity fallback (NPE prevention)

**Location:** [`MarkerMatcher.kt:381-382`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt:381)

**Current:**
```kotlin
private fun proximityRange(marker: UserMarker): Double =
    marker.proximityOverrideM!!
```

**Fix:**
```kotlin
private fun proximityRange(marker: UserMarker): Double =
    marker.proximityOverrideM ?: when (marker.geometry) {
        is MarkerGeometry.Pin -> 200.0
        is MarkerGeometry.Circle -> marker.geometry.radiusM * 3.0
        is MarkerGeometry.Corridor -> marker.geometry.widthM * 3.0
    }
```

**Rationale:** `proximityOverrideM` is set at save time from the formula, but pre-fix markers, corrupted JSON, or race conditions could leave it null. Defensive fallback prevents crash.

---

## Fix 2 — Correct bearing direction

**Location:**
- [`MarkerMatcher.kt:126`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt:126) — ZoneMatch
- [`MarkerMatcher.kt:138`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt:138) — ProximityMatch (at-marker path)
- [`MarkerMatcher.kt:152`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt:152) — ProximityMatch (normal path)

**Current (all three locations):**
```kotlin
// ZoneMatch: initialBearing(center, boat)       → marker → boat
// ProximityMatch: initialBearing(unblocked, boat) → marker → boat
// At-marker: initialBearing(center, boat)        → marker → boat
```

**Fix:** Swap arguments to `initialBearing(boat, ...)` so bearing is FROM boat TO marker:
```kotlin
// ZoneMatch: initialBearing(boat, center)
// ProximityMatch: initialBearing(boat, unblocked)
// At-marker: initialBearing(boat, center)
```

**Impact on display:** [`MarkerDrawer.kt:460`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:460) displays `"${cardinalDirection(match.bearingDeg)} of ${match.marker.name}"`. After fix, the cardinal direction will correctly indicate marker direction FROM boat.

---

## Fix 3 — Remove hardcoded debug marker name

**Location:** [`MarkerMatcher.kt:255`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt:255)

**Current:**
```kotlin
val dump = marker.name == "Sainte Marguerite "
```

**Fix:** Remove the hardcoded check. The existing WIA debug mode (long-press boat marker → map tap) provides equivalent diagnostic output without embedding marker-specific logic in production code.

Remove lines 255-274 (the `dump` variable + direct line test logging that was only for this marker). The cone-level logging at lines 278-306 stays — it's gated by `dump` but should be simplified to use a general debug flag or just use existing `Log.d("WIA", ...)` calls.

Also remove additional hardcoded debug gate at [`MarkerMatcher.kt:634`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt:634): `if (bearing in 80.0..87.0)` — another marker-specific debug remnant in `corridorDistanceAtBearing`.

**Alternative:** Keep the direct line test but apply it as a general mechanism (see Fix 4).

---

## Fix 4 — Direct-line fast path (core fix for ~50% match rate)

**Location:** [`MarkerMatcher.kt:246-311`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt:246) — `closestUnblockedPoint()`

**Root cause:** Angular shadows from wide island-spanning coastline segments merge to cover the full view cone, blocking matches even when the closest boundary point has a clear sea path (confirmed by direct line test).

**Fix:** Insert a direct-line check before the angular shadow pipeline. If the geometrically-closest boundary point has a clear line of sight, return it immediately.

```kotlin
fun closestUnblockedPoint(
    boat: LatLng,
    marker: UserMarker,
    spatialIndex: CoastlineSpatialIndex
): LatLng? {
    // ── Direct-line fast path ──
    val closestBoundary = closestGeometricBoundaryPoint(boat, marker.geometry)
    if (closestBoundary != null && !spatialIndex.segmentIntersectsLand(boat, closestBoundary)) {
        Log.d("WIA", "  DIRECT: clear path to boundary, skipping angular analysis")
        return closestBoundary
    }

    // ── Fall back to angular shadow analysis ──
    val cones = viewCones(boat, marker.geometry)
    // ... (existing angular shadow pipeline)
}
```

**New helper — `closestGeometricBoundaryPoint()`:**
```kotlin
/** Closest point on the marker geometry boundary, ignoring land. */
private fun closestGeometricBoundaryPoint(boat: LatLng, geometry: MarkerGeometry): LatLng? {
    return when (geometry) {
        is MarkerGeometry.Pin -> geometry.position
        is MarkerGeometry.Circle -> {
            val dist = SpatialOperations.haversine(boat, geometry.center)
            val bearing = SpatialOperations.initialBearing(boat, geometry.center)
            if (dist <= geometry.radiusM) return null  // boat is inside — no boundary point
            val t = dist - geometry.radiusM  // distance from boat to circle edge along bearing
            SpatialOperations.pointAlongBearing(boat.latitude, boat.longitude, bearing, t)
        }
        is MarkerGeometry.Corridor -> {
            val halfW = geometry.widthM / 2.0
            // Check if boat is inside corridor first
            if (isInsideGeometry(boat, geometry)) return null
            // Closest point on centerline
            val closestOnCL = SpatialOperations.projectPointOntoSegment(boat, geometry.p1, geometry.p2)
            val distToCL = SpatialOperations.pointToSegmentDistance(boat, geometry.p1, geometry.p2)
            val bearing = SpatialOperations.initialBearing(boat, closestOnCL)
            val distToBoundary = (distToCL - halfW).coerceAtLeast(0.0)
            SpatialOperations.pointAlongBearing(boat.latitude, boat.longitude, bearing, distToBoundary)
        }
    }
}
```

**Key behavior:** If boat is inside the geometry (`isInsideGeometry` returns true), `closestGeometricBoundaryPoint` returns null → falls through to angular analysis (which won't matter since `resolveMatch` already handled zone match). This is correct — inside-zone matches skip `closestUnblockedPoint` entirely.

**Edge case:** What if the direct line is clear but there's a BETTER (closer) boundary point at a different bearing? The direct-line fast path returns the geometrically-closest boundary point. If that's reachable, it IS the optimal answer — any other boundary point is farther by definition (it's the closest geometric point). The angular analysis would only find farther points. So returning the closest geometric point when it has a clear path is always optimal.

---

**Additional fix — Pin `bestBoundaryPoint` must respect land:**
[`MarkerMatcher.kt:825-828`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt:825) currently ignores `unblocked` intervals entirely for pins — always returns `geometry.position` even if land blocks. The direct-line fast path catches the case where the pin is blocked (falls through to angular), but angular also returns the pin regardless. Both paths need fixing:

```kotlin
is MarkerGeometry.Pin -> {
    // Only match if pin bearing falls within an unblocked interval
    val bearingToPin = SpatialOperations.initialBearing(boat, geometry.position)
        .let { (it % 360 + 360) % 360 }
    val isUnblocked = unblocked.any { bearingToPin in it.start..it.end }
    if (isUnblocked) geometry.position else null
}
```

Without this, a pin behind an island would always match — the direct-line fast path blocks it, but the angular fallback still returns it.

---

## Fix 5 — Coroutine cancellation on rapid taps

**Location:** [`MarkersViewModel.kt:654-670`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:654)

**Current:**
```kotlin
fun whereAmI(boatPos: LatLng) {
    // ...
    viewModelScope.launch {
        val result = withContext(Dispatchers.Default) { ... }
        _matchResult.value = result
        _drawerState.value = MarkerDrawerState.MatchResult
    }
}
```

**Fix:** Track the current job and cancel previous before launching new:
```kotlin
private var whereAmIJob: Job? = null

fun whereAmI(boatPos: LatLng) {
    val index = coastlineIndex ?: return
    val all = _markers.value
    if (all.isEmpty()) {
        _matchResult.value = WhereAmIResult(emptyList())
        _drawerState.value = MarkerDrawerState.MatchResult
        return
    }

    whereAmIJob?.cancel()
    whereAmIJob = viewModelScope.launch {
        val result = withContext(Dispatchers.Default) {
            MarkerMatcher.resolveAllMarkers(boatPos, all, index)
        }
        _matchResult.value = result
        _drawerState.value = MarkerDrawerState.MatchResult
    }
}
```

---

## Fix 6 — Edge line bearing sampling

**Location:** [`MarkerMatcher.kt:876-877`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt:876)

**Current:** Tests only the midpoint of each unblocked interval for corridor edge line intersection.

**Fix:** Sample N evenly-spaced bearings across the interval (e.g., 5 samples: start, 25%, mid, 75%, end). Test edge intersection at each, keep closest.

```kotlin
// Replace single midpoint test with multi-sample:
val samples = 5
for (i in 0 until samples) {
    val t = i.toDouble() / (samples - 1)
    val sampleBearing = interval.start + (interval.end - interval.start) * t
    val ptEdge = corridorEdgePointAtBearing(boat, geometry.p1, geometry.p2, halfW, sampleBearing)
    if (ptEdge != null) {
        val d = SpatialOperations.haversine(boat, ptEdge)
        if (d < bestDist) { best = ptEdge; bestDist = d }
    }
}
```

---

## Fix 7 — Per-component corridor cones

**Location:** [`MarkerMatcher.kt:452-490`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt:452) — `viewCones()` for Corridor

**Current:** Single cone spanning min→max bearing of all extreme points. Over-estimates angular coverage.

**Fix:** Split corridor into 4 component cones — one per corridor component (P1 end-cap disc, P2 end-cap disc, two edge rectangles). Each cone has its own `zoneDistanceAt` function specific to that component. This prevents coastline segments from one component's angular range from casting shadows that block another component.

```kotlin
is MarkerGeometry.Corridor -> {
    val halfW = geometry.widthM / 2.0
    val segBearing = SpatialOperations.initialBearing(geometry.p1, geometry.p2)
    
    // P1 end-cap cone
    val p1Cone = buildCircleCone(boat, geometry.p1, halfW, segBearing)
    // P2 end-cap cone
    val p2Cone = buildCircleCone(boat, geometry.p2, halfW, segBearing)
    // Left edge cone
    val leftCone = buildEdgeCone(boat, geometry.p1, geometry.p2, halfW, +1.0)
    // Right edge cone
    val rightCone = buildEdgeCone(boat, geometry.p1, geometry.p2, halfW, -1.0)
    
    listOfNotNull(p1Cone, p2Cone, leftCone, rightCone)
}
```

Each component cone has a `zoneDistanceAt` that returns the actual distance to THAT component (not max of all components). This ensures the depth check in `angularShadow` only considers whether a coastline segment is between the boat and the specific component being tested.

**Note:** This is the most complex fix. If Fix 4 (direct-line fast path) resolves the match rate issue, this can be deferred as a performance optimization.

---

## Implementation Order

| Step | Fix | File | Complexity | Dependency |
|------|-----|------|------------|------------|
| 1 | Fix 1 — NPE safety | MarkerMatcher.kt | Trivial | None |
| 2 | Fix 2 — Bearing direction | MarkerMatcher.kt | Trivial | None |
| 3 | Fix 3 — Debug cleanup | MarkerMatcher.kt | Trivial | None |
| 4 | Fix 5 — Coroutine cancel | MarkersViewModel.kt | Low | None |
| 5 | Fix 4 — Direct-line fast path | MarkerMatcher.kt | Medium | None |
| 6 | Fix 6 — Edge sampling | MarkerMatcher.kt | Low | None |
| 7 | Fix 7 — Component cones | MarkerMatcher.kt | High | May defer if Fix 4 resolves |

Steps 1-3 are one-line changes each, combined in a single MarkerMatcher.kt edit.
Step 4 is a separate file (MarkersViewModel.kt).
Step 5 is the core fix.
Steps 6-7 are accuracy/optimization improvements.

## Files Changed

| File | Fixes |
|------|-------|
| `app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt` | Fix 1, 2, 3, 4, 6, 7 |
| `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` | Fix 5 |

## Verification

1. `compileDebugKotlin` — must pass after each file change
2. `assembleDebug` — full build
3. Deploy APK + test Sainte Marguerite corridor: 4 test clicks that previously failed (2/4 match) should now all match
4. Verify bearing display: "NW of MarkerName" now shows correct direction
5. Verify no NPE with newly-created markers
6. Rapid-tap boat marker: only last result displayed, no flicker
