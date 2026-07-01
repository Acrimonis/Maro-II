# WhereAmI — resolveMatch specification

## Core rules

A marker has two spatial concepts:

| Concept | Source | Purpose |
|---------|--------|---------|
| **Zone** | Marker geometry (circle area, corridor pill) | Boat inside → ZoneMatch |
| **Proximity zone** | `marker.proximityOverrideM` (always set by creation wizard) | Pre-filter: is this marker worth testing? |

## Flow

```
Boat in zone? ──yes──▶ ZoneMatch (no line-of-sight test)
  no
Boat ≤1m from marker? ──yes──▶ LineOfSightMatch
  no
Pin? ──yes──▶ distance ≤ proximityOverrideM?
                yes → LineOfSightMatch (no land check)
                no  → null
  no (Circle / Corridor)
minBoundaryDist > proximityOverrideM? ──yes──▶ null (pre-filter, no segments)
  no
closestUnblockedPoint()
  null   → null (all blocked by land)
  found  → LineOfSightMatch (no distance cap)
```

## proximityOverrideM lifecycle

| Stage | Where | How |
|-------|-------|-----|
| **Creation** | [`MarkersViewModel.saveMarker`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:510) | User value or formula default: Pin=200m, Circle=radius×3, Corridor=width×3 |
| **Edit** | [`MarkersViewModel.updateMarker`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:553) | Same |
| **Matching** | `MarkerMatcher.resolveMatch` | Reads `marker.proximityOverrideM` — always set, never computed |

## Changes to MarkerMatcher.kt

### 1. `proximityRange()` — simplify

```kotlin
// Before
private fun proximityRange(marker: UserMarker): Double =
    marker.proximityOverrideM ?: when (marker.geometry) {
        is MarkerGeometry.Pin -> 200.0
        is MarkerGeometry.Circle -> marker.geometry.radiusM * 3.0
        is MarkerGeometry.Corridor -> marker.geometry.widthM * 3.0
    }

// After
private fun proximityRange(marker: UserMarker): Double =
    marker.proximityOverrideM!!  // always set by creation wizard
```

### 2. `resolveMatch()` — restore pre-filter, keep proximity gate removed

Insert before `closestUnblockedPoint`:

```kotlin
// ── 4. Range pre-filter ──
val range = proximityRange(marker)
val minBoundaryDist = when (marker.geometry) {
    is MarkerGeometry.Pin -> directDist
    is MarkerGeometry.Circle ->
        (directDist - marker.geometry.radiusM).coerceAtLeast(0.0)
    is MarkerGeometry.Corridor -> {
        val halfW = marker.geometry.widthM / 2.0
        val dSeg = SpatialOperations.pointToSegmentDistance(boat, marker.geometry.p1, marker.geometry.p2)
        (dSeg - halfW).coerceAtLeast(0.0)
    }
}
if (minBoundaryDist > range) return null

// ── 5. Find closest unblocked boundary point ──
val unblocked = closestUnblockedPoint(boat, marker, spatialIndex)
// ... (no distance gate after this)
```

### 3. `sortScore()` — unchanged

Already uses `proximityRange(markerOf(match))` which now reads `proximityOverrideM`.

## Files touched

| File | Change |
|------|--------|
| `MarkerMatcher.kt` | `proximityRange()` simplified; pre-filter restored; `LineOfSightMatch` rename (done); `ProximityConfig` removed (done) |
| `MarkerOverlay.kt` | `LineOfSightMatch` rename (done) |
| `MarkerDrawer.kt` | `LineOfSightMatch` rename (done) |

## Summary

| Before | After |
|--------|-------|
| `proximityRange()` computed formula defaults | Reads `proximityOverrideM` directly |
| Proximity gate rejected LOS matches | Removed — LOS is sole pass/fail |
| Pre-filter used computed defaults | Pre-filter uses stored `proximityOverrideM` |
| `ProximityMatch` | `LineOfSightMatch` |
| `circlePointAtBearing` dropped tangents | `pointAlongBearing` from center |
