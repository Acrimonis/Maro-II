# Map Offset — Dynamic Speed-Based Plan

**Branch:** `feature/map-offset`
**Status:** Reviewed — ready for implementation

## Goal

Boat marker renders at screen center when stationary, slides down to 1/3 from bottom
when moving at speed. Reveals more map ahead during navigation while keeping the
logical map center at the real GPS position.

## Approach

**Option A — OSMdroid `setMapCenterOffset` + Compose overlay realignment.**

- `MapView.setMapCenterOffset(0, yPx)` shifts where the logical center renders
  on screen — purely visual, does not affect `mapCenter` GeoPoint or rotation pivot.
- Compose overlays (boat marker, direction line, cap arrow) move their anchor
  by the same pixel offset to stay visually aligned with the GPS position.
- Speed → offset fraction via linear ramp: 0 kn → 0%, ≥20 kn → 100% (default).
  Both ramp speed and max fraction are configurable via `maro.properties`.

## Speed-to-Offset Mapping

Configurable via `maro.properties`:

| Property | Default | Range | Description |
|---|---|---|---|
| `map.lookAhead.speedKn` | 20.0 | 1.0–50.0 | Speed (kn) at which offset reaches maximum |
| `map.lookAhead.maxFraction` | 0.167 (1/6) | 0.05–0.4 | Fraction of screen height for max offset |

```
effectiveSpeedKn = navigationState.speedKnots ?: navigationState.demoSpeedKnots
fraction = (effectiveSpeedKn / appSettings.mapLookAheadSpeedKn).coerceIn(0.0, 1.0)
offsetPx = (fraction * screenHeightPx * appSettings.mapLookAheadMaxFraction).toInt()
offsetDp = fraction * screenHeightDp * appSettings.mapLookAheadMaxFraction
```

With defaults (20 kn, 1/6):

| Speed | fraction | Boat position |
|---|---|---|
| 0 kn (stopped) | 0.00 | Screen center |
| 10 kn | 0.50 | Midway |
| 20 kn | 1.00 | 1/3 from bottom |

Smoothing: offset fraction passed through `animateFloatAsState` with
`spring(dampingRatio = 0.8f, stiffness = 200f)` — provides ~1s visual
smoothing without requiring a separate EMA on GPS speed.

## Files Modified

### 1. `app/src/main/java/ykws/android/maro/config/AppConfig.kt`

#### 1a. New properties (near existing `mapNavigation*` properties, ~line 219)

```kotlin
/** Speed (knots) at which the map look-ahead offset reaches its maximum.
 *  Default 20.0. Set via `map.lookAhead.speedKn` in maro.properties. */
var mapLookAheadSpeedKn: Double = 20.0
    private set
/** Fraction of screen height for maximum look-ahead offset.
 *  Default 1/6 (~0.167) shifts center from H/2 → 2H/3 from top, placing boat at bottom 1/3.
 *  Set via `map.lookAhead.maxFraction` in maro.properties. */
var mapLookAheadMaxFraction: Double = 1.0 / 6.0
    private set
```

#### 1b. Load from maro.properties (near `map.navigation.line.color`, ~line 627)

```kotlin
props.getProperty("map.lookAhead.speedKn")?.toDoubleOrNull()?.let {
    mapLookAheadSpeedKn = it.coerceIn(1.0, 50.0)
}
props.getProperty("map.lookAhead.maxFraction")?.toDoubleOrNull()?.let {
    mapLookAheadMaxFraction = it.coerceIn(0.05, 0.4)
}
```

### 2. `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

#### 2a. Remove hardcoded constants — use `appSettings` instead

The `FULL_OFFSET_SPEED_KN` and `MAX_OFFSET_FRACTION` constants are replaced by reading
`appSettings.mapLookAheadSpeedKn` and `appSettings.mapLookAheadMaxFraction` at the
call site.

#### 2b. `MapContent` composable — new parameter and offset computation

Add parameter:
```kotlin
mapCenterOffsetDp: Dp = 0.dp,
```

Compute offset in `MapContent` call site (around line 1480, where `MapContent` is invoked in the `BoxWithConstraints` block):

```kotlin
// Compute dynamic map offset from speed — configurable via maro.properties
val fullOffsetSpeedKn = appSettings.mapLookAheadSpeedKn  // default 20.0
val maxFraction = appSettings.mapLookAheadMaxFraction     // default 1/6
val effectiveSpeedKn = navigationState.speedKnots ?: navigationState.demoSpeedKnots
val targetFraction = ((effectiveSpeedKn ?: 0f) / fullOffsetSpeedKn.toFloat())
    .coerceIn(0f, 1f)
val animatedFraction by animateFloatAsState(
    targetValue = targetFraction,
    animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f),
    label = "mapOffsetFraction"
)
val mapCenterOffsetDp = (animatedFraction * maxHeight.value * maxFraction).dp
```

Pass `mapCenterOffsetDp` to `MapContent`.

#### 1c. `CoastlineMapView` — accept + apply offset reactively

Add parameter:
```kotlin
centerOffsetYPx: Int = 0,
```

In the `AndroidView` factory, after `controller.setCenter(...)`, apply initial offset
to avoid a one-frame flash at screen center:
```kotlin
if (centerOffsetYPx != 0) {
    setMapCenterOffset(0, centerOffsetYPx)
}
```

Then add a `LaunchedEffect` alongside the existing per-layer `LaunchedEffect` blocks
(~line 2749) to reactively update the offset when speed changes:
```kotlin
LaunchedEffect(centerOffsetYPx, localMapView.value) {
    localMapView.value?.setMapCenterOffset(0, centerOffsetYPx)
}
```

Convert `mapCenterOffsetDp` to px at the call site using `LocalDensity.current`:
```kotlin
val density = LocalDensity.current
val centerOffsetYPx = with(density) { mapCenterOffsetDp.roundToPx() }
```
Pass `centerOffsetYPx` to `CoastlineMapView` at its call site (~line 2201).

#### 1d. `CenterMarkerOverlay` — accept + apply offset

Add parameter:
```kotlin
centerOffsetYDp: Dp = 0.dp,
```

At the call site, keep `Modifier.align(Alignment.Center)` and chain `.offset(y = centerOffsetYDp)`:
```kotlin
modifier = Modifier.align(Alignment.Center).offset(y = centerOffsetYDp)
```

The existing `yOffset = finalSizeDp / 2` inside `CenterMarkerOverlay` (to align boat bow
with center) stays unchanged — it's independent of the map offset.

#### 1e. `DirectionLine` — accept + apply offset

Add parameter `centerOffsetYDp: Dp = 0.dp`.  
Replace `cY = size.height / 2` with `cY = size.height / 2 + centerOffsetYDp.toPx()`.

#### 1f. `CapArrowOverlay` — accept + apply offset

Add parameter `centerOffsetYDp: Dp = 0.dp`.  
Replace `midY = size.height / 2` with `midY = size.height / 2 + centerOffsetYDp.toPx()`.

#### 1g. Plumb `mapCenterOffsetDp` through all overlay call sites

In `MapContent`'s overlay section (lines 2228-2249), pass `mapCenterOffsetDp` to:
- `DirectionLine`
- `CapArrowOverlay`
- `CenterMarkerOverlay`

And to `CoastlineMapView` as `centerOffsetYPx` (converted from dp).

### 3. No changes needed

- `NavigationViewModel.kt` — `cameraUpdates`, `_gpsPosition`, `_mapCenter`, `computeDemoSpeed` all unchanged
- `DepthViewModel.kt` — depth reads real GPS position, unaffected
- `MarkerOverlay.kt` — osmdroid markers unaffected
- `MarkerDrawer.kt` — uses `boatPosition` (real GPS), unaffected
- `RegulatedZoneComponents.kt` — uses `boatPosition` (real GPS), unaffected
- `SettingsManager.kt` — no new setting in v1 (can add toggle later)
- `OverlayTracker.kt` — unaffected

## Flow Diagram

```
GPS fix / pan event
    │
    ▼
NavigationViewModel
    │ navigationState.speedKnots (GPS)
    │ navigationState.demoSpeedKnots (demo)
    │
    ▼
MapScreen (BoxWithConstraints)
    │ effectiveSpeedKn = speedKnots ?: demoSpeedKnots
    │ fraction = speedKn / appSettings.mapLookAheadSpeedKn clamped [0,1]
    │ animateFloatAsState(fraction)
    │ offsetDp = fraction * screenHeight * appSettings.mapLookAheadMaxFraction
    │
    ├──► CoastlineMapView
    │       setMapCenterOffset(0, offsetPx)   ← shifts map rendering
    │
    ├──► CenterMarkerOverlay
    │       offset(y = offsetDp)              ← moves boat icon down
    │
    ├──► DirectionLine
    │       anchor.y += offsetDp              ← moves line anchor
    │
    └──► CapArrowOverlay
            anchor.y += offsetDp              ← moves arrow anchor
```

## Verification

- [ ] Build succeeds
- [ ] Static: at 0 kn, boat at screen center (no regression)
- [ ] Moving: at 20+ kn GPS speed (default), boat at configured max offset
- [ ] Smooth transition: speed ramp from 0→20 kn produces gradual offset change
- [ ] Demo mode: fast pan → offset grows; stop panning → offset fades to 0
- [ ] Map rotation (heading-up): rotation still pivots on GPS position
- [ ] Pinch-zoom: behavior acceptable with offset applied
- [ ] Manual pan (autoFollowSuppressed): offset persists; recenter still works
- [ ] Marker wizard: animateTo target appears at correct screen position
- [ ] Depth readout: still shows depth at boat position
- [ ] Distance-to-shore: still correct
- [ ] whereAmI: still matches markers correctly
- [ ] Orientation change: offset recomputed from new screen height
- [ ] Track navigation: animateTo target with offset applied looks correct

## Risk: Pinch-Zoom Behavior

**Unknown:** Does `setMapCenterOffset` affect where pinch-zoom centers?
If the zoom origin is the logical center (unaffected by offset), gesture feels natural.
If the zoom origin is the visual offset point, the zoom center is below the fingers.

**Mitigation:** Smoke test on first build. If problematic, options:
- Only apply offset during GPS auto-follow, suppress during manual gestures
- Accept the behavior (it may feel fine in practice)

## Future: Settings Toggle

Not in v1. Can add `mapLookAhead: Boolean` to `AppSettings` later,
defaulting to `true`. Toggle in Settings → Map section.
