<!-- scope: feature -->
# SpeedLimitCard — Heading-Ahead + Side-Zone Display Design

## User's Requirement

> If a zone is ahead within 500m, show "→ 456m".
> If nothing ahead, but a zone is nearby at 300m to the left/right, show that instead.

**Ahead cone:** ±15° from heading (not 30°).

## Card States

| Scenario | Title | Value | Subtitle |
|----------|-------|-------|----------|
| Zone ahead (within ±15° cone, <500m) | `CAP D'ANTIBES` | `10 kn` | `→ 456 m · ETA 1m` |
| Nothing ahead, zone **left** (within 500m, -90° to -15°) | `LIBRE` | `--` | `300 m ← Gauche` |
| Nothing ahead, zone **right** (within 500m, +15° to +90°) | `LIBRE` | `--` | `250 m → Droite` |
| Nothing ahead, zone **behind** (within 500m, beyond ±90°) | `LIBRE` | `--` | `500 m ↓ Derrière` |
| No zone within 500m any direction | `LIBRE` | `--` | `Aucune limite` |

## Sector Logic (heading = 0°)

```
         Ahead (-15° to +15°)
              │
        ──────┼──────
     Left ◄───┤───► Right
    (-90,-15) │  (+15,+90)
              │
         Behind (outside ±90°)
```

---

## Detailed Implementation Plan

### Phase 1: Keep Demo Heading Alive (Bug Fix #1)

**File:** [`CoastlineViewModel.kt`](../app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt)

**Change 1.1 — Stop clearing `demoBearingDeg` on pan-stop**
In `computeDemoSpeed()`, the stop-detection timer currently clears `demoSpeedKnots` AND `demoBearingDeg`. Change it to only clear `demoSpeedKnots`:

```kotlin
// Before (line ~787):
if (isActive) _navigationState.update { it.copy(demoSpeedKnots = null, demoBearingDeg = null) }

// After:
if (isActive) _navigationState.update { it.copy(demoSpeedKnots = null) }
// demoBearingDeg persists — last known heading stays valid
```

**Change 1.2 — Reset `demoBearingDeg` only on explicit heading change**
Add a `lastDemoBearingDeg` field. When `computeDemoSpeed()` computes a new bearing that differs from `lastDemoBearingDeg` by >45°, reset. This prevents stale heading from persisting when user starts panning in a different direction.

Actually simpler: just keep the current logic where each new pan updates `demoBearingDeg` normally. The only change is not clearing it on stop. When the user pans again, `computeDemoSpeed()` updates it with the new direction. This is sufficient.

### Phase 2: Add `bearingBetween()` to SpatialOperations

**File:** [`SpatialOperations.kt`](../app/src/main/java/ykws/android/maro/spatial/SpatialOperations.kt)

**Change 2.1** — Add function:

```kotlin
/**
 * Initial bearing (degrees clockwise from true north) from [p1] to [p2].
 * Uses the standard great-circle formula.
 */
fun bearingBetween(p1: LatLng, p2: LatLng): Double {
    val dLon = Math.toRadians(p2.longitude - p1.longitude)
    val lat1 = Math.toRadians(p1.latitude)
    val lat2 = Math.toRadians(p2.latitude)
    val x = sin(dLon) * cos(lat2)
    val y = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return (Math.toDegrees(atan2(x, y)) + 360) % 360
}
```

Imports needed: `kotlin.math.sin`, `kotlin.math.cos`, `kotlin.math.atan2` (check if already imported in SpatialOperations.kt).

### Phase 3: Add `SideZoneInfo` + StateFlow to CoastlineViewModel

**File:** [`CoastlineViewModel.kt`](../app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt)

**Change 3.1** — Add data class (near `HeadingAheadResult`):

```kotlin
/**
 * Information about a speed zone that is NOT ahead of the boat but is
 * nearby to the left, right, or behind. Used as fallback when [HeadingAheadResult]
 * is null or beyond threshold.
 */
data class SideZoneInfo(
    val distanceM: Double,
    val relativeBearingDeg: Double,  // -180..+180: negative=left, positive=right
    val zoneName: String
)
```

**Change 3.2** — Add StateFlow (near `_headingAheadDistance`):

```kotlin
private val _sideZoneInfo = MutableStateFlow<SideZoneInfo?>(null)
val sideZoneInfo: StateFlow<SideZoneInfo?> = _sideZoneInfo.asStateFlow()
```

**Change 3.3** — Compute side zone in onEach block (after heading-ahead computation, lines ~430-449):

```kotlin
// ── Side-zone computation (fallback when nothing ahead) ──────────
val sideInfo = if (headingAhead == null && hasHeading && shore.distanceMeters != null) {
    // Find the closest zone edge point and compute bearing to it
    val nearestDist = szQuery.distanceToBoundaryM
    val nearestZone = szQuery.nearestZone
    if (nearestDist != null && nearestDist > 0.0 && nearestDist < 500.0 && nearestZone != null) {
        // We need the closest point on the zone boundary.
        // SpeedZoneIndex.query() returns nearestDistance but not the point.
        // For the bearing, we can approximate using the _navigationState position 
        // and the zone's centroid or closest point.
        // 
        // Since we don't store the closest point, we estimate the bearing
        // by taking the zone's outerRing centroid as an approximation.
        val centroidLat = nearestZone.outerRing.map { it.latitude }.average()
        val centroidLon = nearestZone.outerRing.map { it.longitude }.average()
        val brgToZone = SpatialOperations.bearingBetween(
            LatLng(_mapCenter.value.latitude, _mapCenter.value.longitude),
            LatLng(centroidLat, centroidLon)
        )
        val relativeDeg = ((brgToZone - headingDeg + 540) % 360) - 180
        SideZoneInfo(
            distanceM = nearestDist,
            relativeBearingDeg = relativeDeg,
            zoneName = nearestZone.name.ifBlank { "Zone" }
        )
    } else {
        // Also check 300m band as a fallback
        val bandDist = shore.distToZone
        if (bandDist != null && bandDist > 0.0 && bandDist < 500.0) {
            // For 300m band, the band edge is at distToZone from the boat.
            // The bearing to the band edge is approximately the same as
            // the bearing to the nearest coastline point.
            // This is an approximation — we assume the band wraps the coast.
            val brgToBand = nav.bearingDeg.toDouble() // simplified
            val relativeDeg = 0.0 // band is all around
            // Since 300m band wraps the coast, it's hard to say left/right.
            // Show it as "nearby" without direction for the 300m case.
            null
        } else null
    }
} else null
_sideZoneInfo.value = sideInfo
```

**Important consideration:** The `SpeedZoneIndex.query()` returns the closest distance but NOT the closest point coordinates. To compute the bearing to the nearest zone edge, we need the closest point. The `pointToSegmentDistance` function returns the distance but not the point.

**Solution:** Add a `nearestPoint` field to `SpeedZoneQuery` that stores the coordinates of the closest point on the nearest zone boundary. This requires modifying `SpeedZoneIndex.query()`.

**Change 3.4** — Add `nearestPoint` to `SpeedZoneQuery`:

In [`SpeedZone.kt`](../app/src/main/java/ykws/android/maro/data/regulation/SpeedZone.kt):
```kotlin
data class SpeedZoneQuery(
    ...
    val nearestPoint: LatLng? = null,  // closest point on nearest zone boundary
    ...
)
```

In [`SpeedZoneIndex.kt`](../app/src/main/java/ykws/android/maro/spatial/SpeedZoneIndex.kt), in `query()` method:
- When computing `pointToSegmentDistance`, also compute the projected point coordinates
- Store the closest point from the nearest segment
- Return it in `SpeedZoneQuery`

**Change 3.5** — Update `query()` to return nearest point:

In `pointToSegmentDistance` loop, when we find a closer distance, also save the projected point:

```kotlin
// In query(), near where we track nearestDistance:
var nearestPoint: LatLng? = null
...
for (edgeIdx in candidateEdgeIndices) {
    val ref = edges[edgeIdx]
    val (dist, projPoint) = SpatialOperations.pointToSegmentDistanceWithPoint(
        LatLng(lat, lon), ref.a, ref.b
    )
    if (dist < nearestDistance) {
        nearestDistance = dist
        nearestZoneIdx = ref.zoneIdx
        nearestPoint = projPoint
    }
}
```

This requires either modifying `pointToSegmentDistance` to return the projected point, or adding a new function `pointToSegmentDistanceWithPoint` that returns both.

### Phase 4: Update SpeedLimitCard with Side-Zone Display

**File:** [`DashboardPanel.kt`](../app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt)

**Change 4.1** — Add `sideZoneInfo` parameter to `SpeedLimitCard`:

```kotlin
@Composable
private fun SpeedLimitCard(
    ...
    headingAheadDistance: HeadingAheadResult? = null,
    sideZoneInfo: SideZoneInfo? = null,   // NEW
    ...
)
```

**Change 4.2** — Update the DashboardPanel caller signature (add `sideZoneInfo` param).

**Change 4.3** — Replace the fallback branch (current lines 479-509) with:

```kotlin
// ── Outside: show side-zone info or LIBRE ───────────────────────
if (sideZoneInfo != null) {
    // Zone is nearby but not ahead — show direction
    val relBearing = sideZoneInfo.relativeBearingDeg
    val zoneText = distanceText(abs(sideZoneInfo.distanceM))
    
    val subtitle = when {
        relBearing < -90 -> "${zoneText} ↓ ${stringResource(R.string.dash_behind)}"
        relBearing < -15 -> "${zoneText} → ${stringResource(R.string.dash_left)}"
        relBearing > 90 -> "${zoneText} ↓ ${stringResource(R.string.dash_behind)}"
        relBearing > 15 -> "${zoneText} ← ${stringResource(R.string.dash_right)}"
        else -> "${zoneText} ${stringResource(R.string.dash_ahead)}" // shouldn't happen
    }
    
    DashboardCard(
        title = stringResource(R.string.dash_libre_title),
        value = stringResource(R.string.dash_libre_value),
        subtitle = subtitle,
        cardColor = DashboardColors.cardBg,
        modifier = modifier
    )
} else {
    // No zone within threshold
    DashboardCard(
        title = stringResource(R.string.dash_libre_title),
        value = stringResource(R.string.dash_libre_value),
        subtitle = stringResource(R.string.dash_no_limit),
        cardColor = DashboardColors.cardBg,
        modifier = modifier
    )
}
```

### Phase 5: Wire in MapScreen

**File:** [`MapScreen.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)

**Change 5.1** — Add state collection:

```kotlin
val sideZoneInfo by viewModel.sideZoneInfo.collectAsState()
```

**Change 5.2** — Pass to DashboardPanel (both landscape and portrait):

```kotlin
DashboardPanel(
    ...
    headingAheadDistance = headingAheadDistance,
    sideZoneInfo = sideZoneInfo,
    ...
)
```

### Phase 6: String Resources

**File:** `app/src/main/res/values/strings.xml`

```xml
<string name="dash_left">Gauche</string>
<string name="dash_right">Droite</string>
<string name="dash_behind">Derrière</string>
<string name="dash_ahead">Devant</string>
```

**File:** `app/src/main/res/values-fr/strings.xml`

```xml
<string name="dash_left">Gauche</string>
<string name="dash_right">Droite</string>
<string name="dash_behind">Derrière</string>
<string name="dash_ahead">Devant</string>
```

### Phase 7: Threshold Constants

**File:** [`ZoneConfig.kt`](../app/src/main/java/ykws/android/maro/ui/map/ZoneConfig.kt)

```kotlin
var speedZoneHeadingConeDeg: Double = 15.0  // ±15° ahead cone
    private set
var speedZoneSideThresholdM: Double = 500.0
    private set
```

Read from `maro.properties` in `init()`:
```kotlin
props.getProperty("speedZone.headingConeDeg")?.toDoubleOrNull()?.let {
    speedZoneHeadingConeDeg = it.coerceIn(5.0, 45.0)
}
props.getProperty("speedZone.sideThresholdM")?.toDoubleOrNull()?.let {
    speedZoneSideThresholdM = it.coerceIn(100.0, 2000.0)
}
```

**File:** [`maro.properties`](../maro.properties)

```properties
# Speed zone heading-aware display thresholds
speedZone.headingConeDeg=15
speedZone.sideThresholdM=500
```

### Phase 8: Remove Diagnostic Logging

Once the feature is verified working, remove the `Log.d(TAG, "DEMO heading: ...")` and `Log.d("SpeedZoneIndex", "firstSpeedZoneAhead ...")` logging lines added during debugging.

---

## Files Summary

| # | File | Change Type | Description |
|---|------|-------------|-------------|
| 1 | [`SpatialOperations.kt`](../app/src/main/java/ykws/android/maro/spatial/SpatialOperations.kt) | **Modify** | Add `bearingBetween()` function |
| 2 | [`SpeedZone.kt`](../app/src/main/java/ykws/android/maro/data/regulation/SpeedZone.kt) | **Modify** | Add `nearestPoint: LatLng?` to `SpeedZoneQuery` |
| 3 | [`SpeedZoneIndex.kt`](../app/src/main/java/ykws/android/maro/spatial/SpeedZoneIndex.kt) | **Modify** | Return `nearestPoint` from `query()`, add `pointToSegmentDistanceWithPoint()` or modify existing |
| 4 | [`CoastlineViewModel.kt`](../app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt) | **Modify** | Stop clearing `demoBearingDeg` on pan-stop; add `SideZoneInfo` data class + StateFlow; compute side zone in `onEach` |
| 5 | [`DashboardPanel.kt`](../app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt) | **Modify** | Add `sideZoneInfo` param; replace fallback with left/right/behind direction display |
| 6 | [`MapScreen.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | **Modify** | Wire `sideZoneInfo` StateFlow to `DashboardPanel` |
| 7 | [`ZoneConfig.kt`](../app/src/main/java/ykws/android/maro/ui/map/ZoneConfig.kt) | **Modify** | Add `speedZoneHeadingConeDeg`, `speedZoneSideThresholdM` |
| 8 | [`maro.properties`](../maro.properties) | **Modify** | Add `speedZone.headingConeDeg=15`, `speedZone.sideThresholdM=500` |
| 9 | `strings.xml` + FR | **Modify** | Add `dash_left`, `dash_right`, `dash_behind`, `dash_ahead` |

