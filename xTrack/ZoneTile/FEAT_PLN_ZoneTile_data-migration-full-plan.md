<!-- scope: feature -->
# Zone Data Model — Full Migration Plan

## Goal
Replace the fragmented zone data layer (7 StateFlows + multi-branch SpeedLimitCard) with a unified `ZoneSituation` model and two query methods.

---

## Phase 1: BL Refactoring (CoastlineViewModel)

### 1a. Define `ZoneSituation` type
```kotlin
data class ZoneSituation(
    val currentZone: ZoneBoundaryInfo?,      // zone we're inside (null if outside)
    val headingAhead: ZoneBoundaryInfo?,     // closest zone along heading
    val nearbyZones: List<ZoneBoundaryInfo>, // all zones within radius, sorted
)
```

### 1b. Add `maxSearchM` parameter to existing methods
- `infoToZoneEntryAlongHeading(..., maxSearchM = 500.0)` — pass to `distanceTo300mAlongHeading()` and `firstSpeedZoneAhead()` filter
- `infoToZoneExitAlongHeading(..., maxSearchM = 500.0)` — pass to `findBandExitAlongHeading()`

### 1c. Implement `infoToZoneAroundBoat(radiusM = 500.0)`
```kotlin
fun infoToZoneAroundBoat(lat, lon, radiusM = 500.0): List<ZoneBoundaryInfo> {
    // 1. SHOM zones from query()
    // 2. 300m band if distToCoast <= 300 + radiusM
    // 3. Sort by distance, prioritize heading-aligned zones
}
```

### 1d. Implement `infoZoneAndZoneAhead(radiusM = 500.0)`
Combines exit info + what's beyond:
```kotlin
fun infoZoneAndZoneAhead(lat, lon, headingDeg, ..., radiusM = 500.0): ZoneSituation {
    val current = buildCurrentZoneInfo(...)   // exit distance + ETA + beyond
    val ahead = infoToZoneEntryAlongHeading(exitPoint, ..., radiusM)  // next zone past exit
    val nearby = infoToZoneAroundBoat(lat, lon, radiusM)
    return ZoneSituation(currentZone = current, headingAhead = ahead, nearbyZones = nearby)
}
```

### 1e. Single StateFlow
```kotlin
private val _zoneSituation = MutableStateFlow(ZoneSituation(null, null, emptyList()))
val zoneSituation: StateFlow<ZoneSituation> = _zoneSituation.asStateFlow()
```

### 1f. Wire in pipeline
Replace the 7 individual StateFlow updates with:
```kotlin
_zoneSituation.value = if (insideAnyZone) {
    infoZoneAndZoneAhead(lat, lon, headingDeg, ...)
} else {
    ZoneSituation(
        currentZone = null,
        headingAhead = infoToZoneEntryAlongHeading(...),
        nearbyZones = infoToZoneAroundBoat(lat, lon, radiusM)
    )
}
```

### 1g. Remove obsolete StateFlows
Remove these from ViewModel (no longer needed by any consumer after UI migration):
- `_headingAheadDistance`
- `_speedLimitKn`
- `_activeSpeedZone`
- `_distanceToSpeedZone`
- `_approachingSpeedZone`
- `_speedZoneQuery` (if not used elsewhere)
- `_zoneBoundaryInfo` (interim, superseded by `_zoneSituation`)

---

## Phase 2: UI Integration (MapScreen → DashboardPanel → SpeedLimitCard)

### 2a. MapScreen collects new StateFlow
```kotlin
val zoneSituation by viewModel.zoneSituation.collectAsState()
```
Pass to DashboardPanel. Remove the 7 old zone parameters from DashboardPanel call.

### 2b. Shrink DashboardPanel parameters
```kotlin
fun DashboardPanel(
    state: CoastlineState,
    isWater: Boolean,
    distanceToShore: Double?,
    depthSample: DepthSample?,
    speedKnots: Float?,
    zoneSituation: ZoneSituation?,     // ← single param replaces 7
    modifier: Modifier
)
```

### 2c. Rewrite SpeedLimitCard
Single-branch render from `ZoneSituation`:

```kotlin
@Composable
private fun SpeedLimitCard(
    zoneSituation: ZoneSituation?,
    isWater: Boolean,
    state: CoastlineState,
    insideAnyZone: Boolean,
    currentSpeedKnots: Float?,
    modifier: Modifier
) {
    // Fallback states (no zone data)
    if (state !is CoastlineState.Ready) { /* loading dash */; return }
    if (!isWater) { /* on-land */; return }

    val info = zoneSituation?.headingAhead
        ?: zoneSituation?.nearbyZones?.firstOrNull()

    if (info == null) {
        // No zone anywhere nearby → "LIBRE"
        DashboardCard(title = "LIBRE", value = "—", subtitle = "Aucune limite")
        return
    }

    // Single render path
    val valueText = if (insideAnyZone) "${info.speedLimitKn} kn"
                    else "${info.directionArrow}${distanceText(info.distanceM)}"

    val subtitleText = buildString {
        info.etaSeconds?.let { append(etaText(it)) }
        if (info.beyondType != BeyondType.ZONE) append(" · ${info.beyondType.name}")
    }

    DashboardCard(
        title = info.zoneName,
        value = valueText,
        subtitle = subtitleText,
        cardColor = when {
            insideAnyZone && info.isCompliant -> zoneCompliant
            insideAnyZone && !info.isCompliant -> zoneDanger
            else -> cardBg
        },
        borderColor = if (insideAnyZone) cardColor else Color.Transparent,
        borderWidth = if (insideAnyZone) 2.dp else 0.dp,
        modifier = modifier
    )
}
```

### 2d. Remove unused imports and composables
- Remove `HeadingAheadResult` import from DashboardPanel.kt
- Remove `SpeedZone`, `SpeedZoneQuery` imports if no longer referenced
- The old `Zone300Card` composable was already removed

---

## Phase 3: Code Cleanup

### 3a. CoastlineViewModel cleanup
- Remove `querySpeedZoneAhead()` if fully replaced by `infoToZoneEntryAlongHeading()`
- Remove `buildHeadingResult()` and `HeadingAheadResult` if no consumers remain
- Remove `ShoreState.headingAheadResult` field
- Rename or remove the `_headingAheadDistance` StateFlow (depends on whether MapScreen's dashed line rendering still uses it)

### 3b. String resources
- Remove `dash_speed_suffix_ok` (already done)
- Remove `dash_speed_suffix_warn` (only used if still referenced somewhere)

### 3c. MapScreen parameter cleanup
Remove the 7 collected state variables and their corresponding `by viewModel.*.collectAsState()` calls.

---

## Migration order

```
Phase 1: BL refactoring (CoastlineViewModel)
  └─ Add ZoneSituation type + new StateFlow
  └─ Add maxSearchM parameter
  └─ Implement infoZoneAndZoneAhead()
  └─ Wire in pipeline (parallel to old StateFlows)

Phase 2: UI integration
  └─ MapScreen: collect zoneSituation instead of 7 individual states
  └─ DashboardPanel: accept ZoneSituation instead of 7 params
  └─ SpeedLimitCard: single-branch render from ZoneSituation

Phase 3: Cleanup
  └─ Remove old StateFlows from ViewModel
  └─ Remove old composable branches
  └─ Remove unused imports, strings, dead code
```

## Files affected

| File | Phase | Changes |
|---|---|---|
| `CoastlineViewModel.kt` | 1 | Add ZoneSituation, maxSearchM param, infoZoneAndZoneAhead, single StateFlow, remove 7 old StateFlows |
| `MapScreen.kt` | 2 | Collect zoneSituation, remove 7 collectAsState calls, update DashboardPanel call |
| `DashboardPanel.kt` | 2 | Shrink params, rewrite SpeedLimitCard to single-branch |
| `strings.xml` | 3 | Remove dash_speed_suffix_warn from both locales |

