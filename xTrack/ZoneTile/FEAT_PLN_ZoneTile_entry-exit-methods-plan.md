<!-- scope: feature -->
# Zone Tile — Entry/Exit Methods Implementation Plan

## Scope: `exiting zone` todo under `tweak` subfeature

Implements `infoToZoneEntryAlongHeading()` and `infoToZoneExitAlongHeading()` to normalize zone tile data gathering, then simplifies SpeedLimitCard to render from a single data source.

---

## Step 1: Define shared types

**File:** `CoastlineViewModel.kt` (near `HeadingAheadResult`, line ~1215)

```kotlin
enum class BeyondType { LAND, ZONE, OPEN_SEA }

data class ZoneBoundaryInfo(
    val distanceM: Double,
    val etaSeconds: Double?,
    val directionArrow: String,       // ↑ → ← ↓
    val zoneName: String,
    val speedLimitKn: Double,
    val currentSpeedKnots: Float?,
    val isCompliant: Boolean,
    val beyondType: BeyondType,
    val beyondName: String?,           // zone name if ZONE, null otherwise
    val boundaryPosition: LatLng
)
```

---

## Step 2: Implement `infoToZoneEntryAlongHeading()`

**File:** `CoastlineViewModel.kt`, method of `CoastlineViewModel`

Replaces/refactors the existing heading-ahead logic inside `querySpeedZoneAhead()`.

```
Input:  lat, lon, headingDeg, currentDistToCoast, currentSpeedKnots, speedZoneIndex
Output: ZoneBoundaryInfo? (null if no zone ahead)

Algorithm:
1. Find nearest entry boundary along heading
   a. 300m band: ray-march binary search (existing distanceTo300mAlongHeading)
   b. SHOM zones: ray projection through SpeedZoneIndex (existing firstSpeedZoneAhead)
2. Pick closest entry result
3. Compute ETA = distance / (speedKnots * 0.514444)
4. Compute directionArrow from zoneBearing - headingDeg
5. beyondType = ZONE (always — we're entering a zone)
6. beyondName = zone name
7. Return ZoneBoundaryInfo
```

---

## Step 3: Implement `infoToZoneExitAlongHeading()`

**File:** `CoastlineViewModel.kt`, method of `CoastlineViewModel`

New method. Only called when `insideAnyZone == true`.

```
Input:  lat, lon, headingDeg, currentDistToCoast, currentSpeedKnots, speedZoneIndex
Output: ZoneBoundaryInfo? (null if can't determine exit)

Algorithm:
1. Find exit boundary along heading
   a. 300m band: inverted ray-march — walk forward until coastDist > ZONE_DISTANCE_M
   b. SHOM zones: polygon edge ray intersection (iterate outerRing edges)
2. Pick closest exit result
3. Compute ETA = distance / (speedKnots * 0.514444)
4. Compute directionArrow from exitBearing - headingDeg
5. beyondType = determineBeyondType(boundaryPos, headingDeg)
   a. firstSpeedZoneAhead(boundaryPos) → ZONE
   b. exponential probe at 25/50/100/200/400/800/1600m → LAND or OPEN_SEA
6. Return ZoneBoundaryInfo
```

---

## Step 4: Implement `determineBeyondType()`

**File:** `CoastlineViewModel.kt`, private function

```kotlin
private fun determineBeyondType(
    boundaryPos: LatLng, headingDeg: Double
): Pair<BeyondType, String?> {
    // 1. Zone check (independent of distance)
    val nextZone = firstSpeedZoneAhead(boundaryPos, headingDeg)
    if (nextZone != null) return ZONE to nextZone.first.name

    // 2. Exponential land probe: 25 → 50 → 100 → 200 → 400 → 800 → 1600
    var probe = 25.0
    while (probe <= 1600.0) {
        val pt = pointAlongBearing(boundaryPos, headingDeg, probe)
        if (!isOnWater(pt)) return LAND to null
        probe *= 2
    }
    return OPEN_SEA to null
}
```

---

## Step 5: Simplify `SpeedLimitCard`

**File:** `DashboardPanel.kt`

Replace the 7-branch SpeedLimitCard with a single-branch renderer:

```kotlin
@Composable
private fun SpeedLimitCard(..., modifier) {
    val info = if (insideAnyZone) exitInfo else entryInfo

    if (info == null) {
        // Fallback: loading / on-land / no-data
        renderFallbackState(...)
        return
    }

    val valueText = "${info.directionArrow}${distanceText(info.distanceM)}"
    val subtitleText = buildString {
        append(etaText(info.etaSeconds))
        if (info.beyondType != ZONE) append(" · ${info.beyondType}")
    }

    DashboardCard(
        title = info.zoneName,
        value = if (insideAnyZone) "${info.speedLimitKn} kn" else valueText,
        subtitle = subtitleText,
        cardColor = if (info.isCompliant) zoneCompliant else zoneDanger,
        ...
    )
}
```

**Key change:** The card value shows **speed limit** when inside zone, **distance to boundary** when approaching. The subtitle shows ETA + what's beyond.

---

## Step 6: Wire into tile rendering

### 6a. Expose both results from the pipeline

**File:** `CoastlineViewModel.kt`, the `mapLatest` block (~line 400)

```kotlin
val entryInfo = if (!insideAnyZone) infoToZoneEntryAlongHeading(...) else null
val exitInfo  = if (insideAnyZone)  infoToZoneExitAlongHeading(...)  else null

// Store in ShoreState or emit as separate StateFlows
// Option A: extend ShoreState with entryInfo / exitInfo fields
// Option B: two new MutableStateFlow<ZoneBoundaryInfo?> on the ViewModel
// Option C: single MutableStateFlow that holds the active one based on insideAnyZone
```

**Recommendation: Option C** — one StateFlow, the caller picks entry or exit:

```kotlin
private val _zoneBoundaryInfo = MutableStateFlow<ZoneBoundaryInfo?>(null)
val zoneBoundaryInfo: StateFlow<ZoneBoundaryInfo?> = _zoneBoundaryInfo.asStateFlow()

// In the pipeline onEach:
_zoneBoundaryInfo.value = if (insideAnyZone) exitInfo else entryInfo
```

This keeps the ViewModel API simple — one state flow instead of two.

### 6b. Collect in MapScreen

**File:** `MapScreen.kt`

```kotlin
val zoneBoundaryInfo by viewModel.zoneBoundaryInfo.collectAsState()
```

Pass to `DashboardPanel` and then to `SpeedLimitCard`, replacing the 7 individual parameters (`headingAheadDistance`, `speedLimitKn`, `activeSpeedZone`, etc.) with a single `ZoneBoundaryInfo?`.

### 6c. Rewrite SpeedLimitCard to single-branch render

**File:** `DashboardPanel.kt`

```kotlin
@Composable
private fun SpeedLimitCard(
    zoneInfo: ZoneBoundaryInfo?,      // ← single data source replacing 7 params
    insideAnyZone: Boolean,
    isWater: Boolean,
    state: CoastlineState,
    modifier: Modifier
) {
    // ── Fallback states (no zone info) ──
    if (state !is CoastlineState.Ready) {
        DashboardCard(title="ZONE", value="—", isEmpty=true)
        return
    }
    if (!isWater) {
        DashboardCard(title="ZONE", value="TERRE", subtitle="À TERRE",
                      cardColor=zoneNormal, ..., dullAlpha)
        return
    }
    if (zoneInfo == null) {
        // No zone ahead and not inside any zone → "LIBRE"
        DashboardCard(title="LIBRE", value="—", subtitle="Aucune limite")
        return
    }

    // ── Has zone info: single render path for both entry and exit ──
    val valueText = if (insideAnyZone) {
        "${zoneInfo.speedLimitKn} kn"               // inside: show speed limit
    } else {
        "${zoneInfo.directionArrow}${distanceText(zoneInfo.distanceM)}"  // outside: show distance
    }

    val subtitleText = buildString {
        append(etaText(zoneInfo.etaSeconds))
        if (zoneInfo.beyondType != BeyondType.ZONE) {
            append(" · ${zoneInfo.beyondType.name}")
        }
    }

    DashboardCard(
        title = zoneInfo.zoneName,
        value = valueText,
        subtitle = subtitleText,
        cardColor = if (zoneInfo.isCompliant) zoneCompliant else zoneDanger,
        borderColor = if (zoneInfo.isCompliant) zoneCompliant else zoneDanger,
        borderWidth = 2.dp,
        modifier = modifier
    )
}
```

### 6d. Parameter cleanup

`DashboardPanel` and `SpeedLimitCard` signatures shrink from 11 zone-related parameters to just 2:
- `zoneBoundaryInfo: ZoneBoundaryInfo?`
- `insideAnyZone: Boolean`

The removed parameters: `speedLimitKn`, `activeSpeedZone`, `distanceToSpeedZone`, `approachingSpeedZone`, `headingAheadDistance`, `inZone300`, `distanceToZone`, `alertDistanceM`.

### 6e. Card color logic

| Scenario | Color | Condition |
|---|---|---|
| Inside, compliant | green | `insideAnyZone && isCompliant` |
| Inside, speeding | red | `insideAnyZone && !isCompliant` |
| Approaching (any beyond type) | blue | `!insideAnyZone` |
| No zone / on land | grey | fallback |

## Step 7: Wire into pipeline (ViewModel side)

**File:** `CoastlineViewModel.kt`, the `mapLatest` block (~line 400)

```kotlin
val entryInfo = if (!insideAnyZone) infoToZoneEntryAlongHeading(...) else null
val exitInfo  = if (insideAnyZone)  infoToZoneExitAlongHeading(...)  else null
```

Emit both as part of a new composite state or extend `ShoreState`.

---

## Files affected

| File | Changes |
|---|---|
| `CoastlineViewModel.kt` | New types + 2 new methods + `determineBeyondType` + pipeline wiring |
| `DashboardPanel.kt` | Rewrite `SpeedLimitCard` to single-branch render from `ZoneBoundaryInfo` |
| `strings.xml` | May remove unused `dash_speed_suffix_warn` string resources |

