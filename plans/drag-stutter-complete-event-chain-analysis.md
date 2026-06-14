# Drag Stutter — Complete Event Chain Analysis

## 1. The trigger: finger drag → osmdroid scroll event

```
User finger drags map (60 fps touch events)
  → osmdroid MapView.onTouchEvent
    → MapListener.onScroll(ScrollEvent)
      → callback: onCenterChanged(lat, lon)     ← UI thread
      → return false (osmdroid continues normally)
        → osmdroid tile rendering loop            ← UI thread
```

## 2. The first split: two ViewModels get a 60 Hz write

```kotlin
onCenterChanged = { lat, lon ->
    viewModel.updateMapCenter(lat, lon)        // CoastlineVM
    depthViewModel.updateMapCenter(lat, lon)   // DepthVM
}
```

### 2a. CoastlineViewModel.updateMapCenter (60 Hz)
- `_mapCenter.value = LatLng(lat, lon)` — StateFlow write, creates 1 LatLng object
- Throttled (1 Hz): `settingsManager.update { copy(mapCenterLat = lat, mapCenterLon = lon) }`
- Demo mode: `computeDemoSpeed(lat, lon)` — see 2c

### 2b. DepthViewModel.updateMapCenter (60 Hz)
- `_mapCenter.value = LatLng(lat, lon)` — StateFlow write, creates 1 LatLng object

### 2c. computeDemoSpeed (60 Hz outer, 3 Hz inner)
```kotlin
fun computeDemoSpeed(lat: Double, lon: Double) {
    // Every call (60 Hz):
    if (lastPanMs != 0L) {
        if (elapsed >= 333L) {                    // ← 3 Hz
            SpatialOperations.haversine(2 LatLng)  // creates 2 LatLng
            _navigationState.update { copy(...) }  // StateFlow write
        }
    } else {
        lastPanLat = lat; lastPanLon = lon; lastPanMs = now
    }
    // 🔴 EVERY CALL (60 Hz):
    panStopJob?.cancel()                           // Coroutine cancellation
    panStopJob = viewModelScope.launch {           // 🔴 NEW COROUTINE 60/s!
        delay(500ms)
        _navigationState.update { copy(demoSpeedKnots = null) }
    }
}
```

**Bottleneck #3:** `viewModelScope.launch` creates 60 coroutine Job objects per second → GC pressure.

## 3. The throttled pipeline (3 Hz on Dispatchers.Default)

Both ViewModels have pipelines that sample the 60 Hz _mapCenter stream:

### CoastlineVM pipeline (3 Hz — all on Dispatchers.Default)
```
sample(333ms) → mapLatest { center ->
    repository.distanceToCoast(center)              // spatial index query
    repository.isOnWater(center, dist)              // spatial index query
    SpeedZoneIndex.query(lat, lon)                  // grid + exhaustive containment
    querySpeedZoneAhead(lat, lon, heading)           // 200 ray-march steps
      → distanceTo300mAlongHeading()                 //   200× distanceToCoastMeters()
      → speedZoneIndex.firstSpeedZoneAhead()         //   polygon intersection ray-march
      → cone-priority edge distance                  //   pointToSegmentDistance
    ShoreState(..., headingAheadResult)
} → flowOn(Dispatchers.Default) → onEach { ... }
```

### DepthVM pipeline (3 Hz — all on Dispatchers.Default)
```
sample(333ms) → mapLatest { center ->
    repository.depthAt(lat, lon)                    // depth raster lookup
} → flowOn(Dispatchers.Default) → onEach { ... }
```

## 4. The onEach block (3 Hz — ALL on UI thread)

This is the "unit of work" that fires 3 times per second on the UI thread:

```
onEach { shore ->
    // ── CoastlineVM StateFlow writes (10 writes) ──
    _distanceToShore.value = shore.distanceMeters
    _isWater.value = shore.isWater
    _inZone300.value = shore.inZone
    _distanceToZone.value = shore.distToZone
    _speedZoneQuery.value = szQuery
    _activeSpeedZone.value = szQuery.nearestZone
    _distanceToSpeedZone.value = szQuery.distanceToBoundaryM
    _approachingSpeedZone.value = szQuery.approaching
    _speedLimitKn.value = min(allLimits)
    _headingAheadDistance.value = shore.headingAheadResult

    // ── Auto-show decisions (pure computation) ──
    zoneAutoShowDecision(300m-band config)
    zoneAutoShowDecision(speed-zone config)
    // Possibly: settingsManager.update { copy(zone300Visible = ...) }  ← SharedPreferences write
    // Possibly: settingsManager.update { copy(speedZonesVisible = ...) }  ← SharedPreferences write

    // ── DepthVM StateFlow write (1 write) ──
    _depthAtCenter.value = depth
}
```

**StateFlow writes are individual Compose invalidations.** Each `_xxx.value = ...` triggers Compose to mark the reading composable as dirty for the next frame. Batch or not, 10+ invalidations in the same frame cause Compose to re-run the MapScreen composable.

## 5. Compose recomposition cascade (3 Hz — UI thread)

After the `onEach` block emits, Compose schedules recomposition. During the next frame:

```
MapScreen composable re-executes                          ← UI thread
  → 20+ collectAsState() re-reads                         ← reads stable values (fast)
  → effectiveHeadingDeg recomputes                         ← pure math (fast)
  → MapContent called with new props                       ← composable call
    → AndroidView.update block:
      if (overlayKey unchanged) → skip                    ← fast
    → CenterMarkerOverlay called                           ← composable call
      → Image(painterResource)                             ← fast (cached drawable)
      → Cap arrow composable                               ← fast
    → DashboardPanel called with new props                 ← composable call
      → 4× DashboardCard (Distance, SpeedLimit, Depth, Speed)
        → 4× AutoSizeValue (BoxWithConstraints)            ← 🔴 LAYOUT PASS
          → measure child with max constraints
          → font size calculation (text.length, height, width)
      → ValidationBadge                                    ← fast
```

**Bottleneck #1: `BoxWithConstraints`** in `AutoSizeValue` triggers a **subcomposite measurement pass**. This is one of Compose's most expensive operations — it needs to measure the child with the `BoxWithConstraints` constraints, which requires a full layout pass for that subtree. With 4 cards, this is 4 subcomposite passes per recomposition.

## 6. osmdroid native rendering (60 fps — UI thread, always on)

Completely independent of our pipeline — runs every display frame:

```
osmdroid MapView.onDraw:
  for each overlay (×~188):
    GroundOverlay.draw() × 16     ← stretch bitmap to geo-bounds (GPU texture)
    Polygon.draw() × 80           ← project vertices + fill + stroke
    Polyline.draw() × 92          ← project vertices + stroke
```

**Each `Polygon.draw()` and `Polyline.draw()` projects every vertex from lat/lon to screen x/y using Mercator projection.** This is the heaviest operation:

| Overlay type | Count | Approx vertices total | Projection cost |
|-------------|:-----:|:---------------------:|:---------------:|
| Regulated zones (Polygon) | ~72 | ~10,000 | High |
| Coastline (Polyline) | ~50 | ~50,000+ | **Very High** |
| Isobaths (Polyline) | ~30 | ~5,000 | Medium |
| Zone300 (Polygon + Polyline) | ~10 | ~2,000 | Medium |
| Hazard markers (Polygon + Polyline) | ~5 | ~200 | Low |
| Depth map (GroundOverlay ×16) | 16 | — | Low (GPU texture) |
| Low depth warning (GroundOverlay ×16) | 16 | — | Low (GPU texture) |

**Total: ~188 overlays, ~67,000+ vertex projections per frame at 60 fps.**

On a 60 fps display (16.6ms frame budget), projecting 67,000 lat/lon pairs every frame costs:
- ~3-5ms for the projection math alone
- ~2-4ms for canvas draw calls (fill, stroke, anti-aliasing)
- ~1-2ms for tile compositing (osmdroid tile cache)
- **Total: ~6-11ms for osmdroid rendering**

This leaves only **~5-10ms** for:
- Compose recomposition (after pipeline emission at 3 Hz)
- Compose layout passes (BoxWithConstraints × 4)
- GC pauses

**Bottleneck #2: osmdroid overlay vertex projection at 60 fps** is the base cost before any Compose work. When our pipeline fires at 3 Hz and adds 2-5ms of Compose work on top, the total can exceed 16ms → frame drop.

## 7. Garbage Collection (intermittent, every ~3-10s)

Objects created per second during drag:

| Source | Objects/s |
|--------|:---------:|
| `_mapCenter.value = LatLng(lat, lon)` × 60 Hz | 60 LatLng |
| `computeDemoSpeed`: `LatLng` for haversine × 3 Hz | 2 LatLng |
| `computeDemoSpeed`: coroutine launch × 60 Hz | 60 Job objects |
| Pipeline `mapLatest`: `ShoreState` + `SpeedZoneQuery` × 3 Hz | ~10 objects |
| Pipeline `mapLatest`: ray-march `pointAlongBearing` LatLng × 3 Hz × 200 steps | 600 LatLng |
| String formatting for logs × 3 Hz | ~5 strings |
| `mutableListOf<Double>()` × 3 Hz | 1 list |
| Compose recomposition temporary objects × 3 Hz | ~50 objects |
| **Total** | **~788 objects/s** |

**Bottleneck #3:** At ~788 objects/second, Android's concurrent GC runs every ~3-8 seconds. A concurrent GC pause typically takes **5-15ms** during which **all threads (including UI) are paused**. This causes a dropped frame every few seconds.

## Summary: Three bottlenecks

| # | Bottleneck | Cost | Frequency | Type |
|---|-----------|:----:|:---------:|------|
| 1 | **`BoxWithConstraints`** × 4 (`AutoSizeValue` in dashboard cards) | **~2-4ms** per recomposition | 3 Hz | Compose layout |
| 2 | **osmdroid vertex projection** for 188 overlays / 67K vertices | **~6-11ms** per frame | **60 Hz** | Canvas rendering |
| 3 | **GC pauses** from 788 objects/s (coroutine jobs, LatLng, strings) | **~5-15ms** pause | Every ~3-8s | JVM GC |

**The critical path:** Bottleneck #2 runs at 60 Hz and uses 6-11ms of the 16.6ms frame budget. When Bottleneck #1 fires (3 Hz, 2-4ms), it steals time from osmdroid's rendering. When Bottleneck #3 fires (every few seconds), it pauses everything for 5-15ms, causing a visible hiccup.

## What to fix (in priority order)

### Fix A: Eliminate `computeDemoSpeed` coroutine churn (Bottleneck #3 contributor)
The `panStopJob?.cancel() + viewModelScope.launch { delay(500ms) }` pattern creates 60 coroutines/second. Replace with a simple `lastScrollMs` timestamp check in the pipeline `onEach` instead of a per-scroll coroutine:

```kotlin
// In updateMapCenter, instead of:
panStopJob?.cancel()
panStopJob = viewModelScope.launch { delay(500ms); ... }

// Just track the last scroll time:
lastScrollMs = SystemClock.elapsedRealtime()
```

Then in the pipeline `onEach`, check if last scroll was >500ms ago:
```kotlin
if (SystemClock.elapsedRealtime() - lastScrollMs > 500L) {
    _navigationState.update { it.copy(demoSpeedKnots = null) }
}
```

### Fix B: Eliminate `LatLng` allocation at 60 Hz (Bottleneck #3 contributor)
`_mapCenter.value = LatLng(lat, lon)` creates 60 `LatLng`/s. Replace with a reusable pair or inline the values:

```kotlin
// Instead of storing LatLng in StateFlow, store two doubles:
private val _mapCenterLat = MutableStateFlow(43.55)
private val _mapCenterLon = MutableStateFlow(7.00)
```

Or pool LatLng objects (less idiomatic but reduces GC).

### Fix C: Simplify osmdroid overlays (Bottleneck #2 — hardest)
This requires reducing vertex count in the baked coastline and regulated zone data. Options:
- Simplify coastline geometry (Douglas-Peucker) during bake
- Combine multiple adjacent Polygons into single MultiPolygon overlays
- Zoom-gate detailed overlays more aggressively (hide regulated zones until higher zoom)

### Fix D: Replace `AutoSizeValue` `BoxWithConstraints` (Bottleneck #1)
Use Compose's `TextUnit` based font sizing instead of `BoxWithConstraints`:

```kotlin
val fontSize = (maxHeight * 0.82f).coerceIn(14f, 64f).sp
// This always triggers a subcomposite layout pass
```

Replace with a simple `Text` using `style = MaterialTheme.typography...` with appropriate `fontSize` based on the text length — no `BoxWithConstraints`.
