<!-- scope: feature -->
# Zone-Ahead Cone — Implementation Plan

## Goal
Draw a visual cone (wedge) on the map showing the heading-ahead search area, and prioritize zones inside the cone for the heading-ahead result.

## Changes

### 1. Cone Detection Priority

**File:** `CoastlineViewModel.kt` — `querySpeedZoneAhead()`

Logic:
1. Compute bearing to nearest zone (already done via `zoneBearing`)
2. Compute absolute diff from heading: `min(|zoneBearing - heading|, 360 - |zoneBearing - heading|)`
3. If diff ≤ `CONE_HALF_ANGLE_DEG` (15°):
   - Zone is "in cone" → use its distance via `pointAlongBearing` and give priority
4. If diff > 15°:
   - Fall back to ray-intersection (`firstSpeedZoneAhead`)
5. The cone-detected zone is preferred even if ray-intersection finds a closer zone outside the cone

Also expose `coneBearingDeg: Double?` in `HeadingAheadResult` for the cone rendering.

### 2. ZoneConfig Constants

**File:** `ZoneConfig.kt`

```kotlin
var zoneAheadConeHalfAngleDeg: Double = 15.0
var zoneAheadConeMaxRadiusM: Double = 2000.0
```

Loadable from `zone.properties` via keys `zoneAhead.coneHalfAngleDeg` and `zoneAhead.coneMaxRadiusM`.

### 3. Cone Visualization

**File:** `MapScreen.kt` — new `drawZoneAheadCone()` function

Isolated like `drawZoneAheadLine()`:
```
private fun drawZoneAheadCone(
    mapView: MapView,
    boatPosition: LatLng?,
    headingDeg: Double?,
    headingAheadResult: HeadingAheadResult?,
    zoomLevel: Double
)
```

- Uses osmdroid `Polygon` with translucent fill (`0x1A1565C0` = Material Blue 700, 10% alpha)
- Points: boat → arc point 1 → ... → arc point N → back to boat
- Arc: 10 segments from heading-15° to heading+15°
- Radius: `distanceAheadM` from result, or `coneMaxRadiusM` if no result
- Only draws when heading available, zoom ≥ ZONE_MIN_ZOOM
- Debounced via `stableIntersectionLatLng` flow (same 200ms gate)
- To remove: delete function + call sites

### 4. Wire Through

Same pattern as the dashed line:
- `stableIntersectionLatLng` flow already debounces → add `stableHeadingDeg` flow with same debounce
- Pass to `MapContent` → `CoastlineMapView`
- Call `drawZoneAheadCone()` in factory and update blocks
- Include in `overlayKey`

