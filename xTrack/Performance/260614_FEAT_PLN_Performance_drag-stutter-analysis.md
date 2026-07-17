<!-- scope: feature -->
# Map Drag Stutter — Performance Analysis

## Current Pipeline Rate

The shore recompute pipeline runs at **`SHORE_SAMPLE_INTERVAL_MS = 150L`** → **~6.6 Hz** (every 150ms).

Defined at [`CoastlineViewModel.kt:1121`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt:1121).

## What Runs Per Tick

Each pipeline tick (triggered by map center change during drag) executes:

| Step | Operation | Cost estimate |
|------|-----------|--------------|
| 1 | `repository.distanceToCoast()` | Fast — spatial index lookup |
| 2 | `repository.isOnWater()` | Fast — spatial index lookup |
| 3 | `SpeedZoneIndex.query()` | Fast — grid spatial index ~1-2ms |
| 4 | **`querySpeedZoneAhead()`** | **Expensive** — see below |
| 5 | StateFlow updates (`_headingAheadDistance.value = ...`) | Triggers Compose recomposition |
| 6 | Compose → `MapContent` re-receives props | UI thread work |
| 7 | LaunchedEffect restarts → `drawZoneAheadCone()` + `drawZoneAheadLine()` + `mapView.invalidate()` | UI thread + osmdroid canvas redraw |

## The Expensive Step: `querySpeedZoneAhead()`

Defined at [`CoastlineViewModel.kt:969`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt:969).

### `distanceTo300mAlongHeading()` — ray-march

```
Up to 200 iterations (2000m / 10m steps)
  each: SpatialOperations.pointAlongBearing()
        + repository.distanceToCoastMeters()
If crossing found: +10 binary search iterations (same ops)
```

**Cost per tick:** Up to **210 spatial queries** to `distanceToCoastMeters()`.

### `speedZoneIndex.firstSpeedZoneAhead()` — SHOM zone ray-march

Another ray-march against speed zone polygon edges. Polygon intersection test is more expensive than point distance.

### Total per second during drag

```
6.6 ticks/s × 210 spatial queries/tick = ~1,386 spatial queries/second
```

While `flowOn(Dispatchers.Default)` shifts computation off the UI thread, the CPU load is high enough to starve the UI thread via thread contention on mobile devices with limited cores.

## The UI Thread Problem

### The critical path during drag

```
User finger drags map
  → osmdroid onScroll event (UI thread)
    → onCenterChanged(lat, lon) (UI thread)
      → _mapCenter StateFlow update
        → pipeline tick (Dispatchers.Default)
          → querySpeedZoneAhead() → 200+ spatial queries
          → _headingAheadDistance.value = result
            → Compose recomposition (UI thread)
              → MapContent re-renders
                → LaunchedEffect(headingAheadResult) restarts (UI thread)
                  → drawZoneAheadCone() — remove old Polygon + add new Polygon (UI thread)
                  → drawZoneAheadLine() — remove old Polyline + add new Polyline (UI thread)
                  → mapView.invalidate() (UI thread)
                    → osmdroid full canvas redraw
```

**The `invalidate()` call every 150ms clashes with osmdroid's own drag-time tile rendering.** osmdroid is already loading and compositing map tiles during a drag. Forcing a full overlay redraw at 6.6 Hz causes visible frame drops because:

1. The `removeAll` + `add` of cone/line triggers osmdroid's `onDraw` early
2. osmdroid's tile cache may be mid-load when the invalidation arrives
3. The LaunchedEffect restart also triggers Compose layout pass on the main thread

## Secondary Issue: Unnecessary Redraws

The LaunchedEffect keys on `headingAheadResult` directly:

```kotlin
LaunchedEffect(headingAheadResult, zoomLevel, boatPosition, headingDeg) { ... }
```

Even when the heading-ahead result is **functionally identical** (same zone, same distance ±small delta), the object reference changes every pipeline tick → LaunchedEffect restarts → cone/line redrawn needlessly.

## Proposed Solutions

### Option A: Debounce the heading-ahead overlay update

Add a separate debounced StateFlow for the cone/line, updating at a lower rate (e.g., 500ms) while dragging:

```kotlin
val stableHeadingAhead = _headingAheadDistance
    .debounce(500)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
```

Use `stableHeadingAhead` in the LaunchedEffect key instead of raw `headingAheadResult`. During a drag, the overlay only redraws every 500ms (2 Hz) instead of every 150ms (6.6 Hz).

**Trade-off:** Cone/line position lags slightly behind the drag. Acceptable for a visual indicator.

### Option B: Throttle the heading-ahead computation

The `querySpeedZoneAhead()` ray-march doesn't need to run at 6.6 Hz when the user is dragging. Either:
- Increase `SHORE_SAMPLE_INTERVAL_MS` during drag (from 150ms to 400ms)
- Use a **separate, slower sample** for heading-ahead only: `_mapCenter.sample(400)` for the heading-ahead query, while the shore pipeline keeps running at 150ms

### Option C: Skip heading-ahead during active drag

Track `_autoFollowSuppressed` (already exists, set during manual pan) and skip `querySpeedZoneAhead()` entirely while the user is actively dragging. Resume when auto-follow resumes.

```kotlin
if (shore.distanceMeters != null && hasHeading && !_autoFollowSuppressed.value) {
    val result = querySpeedZoneAhead(...)
    _headingAheadDistance.value = result
} else {
    _headingAheadDistance.value = null  // or keep last value
}
```

**Trade-off:** Heading-ahead info goes stale during drag. But the user is looking at the map while dragging, not at the dashboard's heading-ahead indicator.

### Option D (Recommended): Combine A + C

1. **Skip heading-ahead computation during drag** (Option C) — the user is panning manually, heading-ahead is irrelevant.
2. **Debounce the overlay update** (Option A) — even in GPS mode, the cone doesn't need 6.6 Hz updates. 2 Hz is sufficient.
3. **Keep the shore pipeline at 150ms** — the dashboard distance values update smoothly during drag.

Implementation sketch:

```kotlin
// In CoastlineViewModel.onEach block:
val headingAhead = if (shore.distanceMeters != null && hasHeading && !_autoFollowSuppressed.value) {
    querySpeedZoneAhead(...)
} else {
    null  // don't compute during drag
}
_headingAheadDistance.value = headingAhead

// Separate debounced flow for overlay rendering:
val stableHeadingAhead: StateFlow<HeadingAheadResult?> = _headingAheadDistance
    .debounce(500)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
```

```kotlin
// In MapScreen:
// Use stableHeadingAhead instead of headingAheadResult for the LaunchedEffect key
LaunchedEffect(stableHeadingAhead, zoomLevel, boatPosition, headingDeg) {
    ...
}
```

## Estimated Impact

| Metric | Before | After (A+C) |
|--------|--------|-------------|
| Heading-ahead ray-march rate | 6.6 Hz (always) | 0 Hz during drag, 2 Hz otherwise |
| Cone/line redraw rate | 6.6 Hz | 2 Hz (debounced) |
| `mapView.invalidate()` rate | 6.6 Hz during drag | 2 Hz or 0 Hz (during drag, none) |
| Spatial queries/s during drag | ~1,386 | ~660 (shore only, no heading-ahead) |
| UI thread preemptions/s | ~13 (every 150ms) | ~2 (every 500ms) for overlay |

