<!-- scope: feature -->
# Approach A — `animateTo` Interaction with `mapRefreshFps`

> Discussion captured under subfeature [`gps-refreshing`](xTrack/FEATURE_SCOPE_Performance.md:97).

## The two controls

| Control | Where | What it does |
|---------|-------|-------------|
| **`mapRefreshFps`** (default 25) | [`CoastlineViewModel.kt:238`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt:238) — `cameraUpdates` flow | How often the ViewModel emits a camera target to the screen collector |
| **`animateTo(durationMs)`** | [`MapScreen.kt:201`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:201) — osmdroid's `MapController` | How osmdroid smoothly scrolls the viewport from current position to target |

They are **independent mechanisms** — the flow emits targets, the animation moves between them.

## How they interact today

```kotlin
// cameraUpdates emits at 25 fps (every 40 ms)
viewModel.cameraUpdates.collect { target ->
    val point = GeoPoint(target.position.latitude, target.position.longitude)
    if (reengage) {
        mv.controller.animateTo(point)  // smooth scroll back (no duration = ~1500 ms default)
        reengage = false
    } else {
        mv.controller.setCenter(point)  // INSTANT SNAP ← the jitter you feel
    }
    mv.mapOrientation = -target.bearingDeg
    mv.invalidate()
}
```

Each `setCenter` snaps the map instantly. Since `cameraUpdates` emits 25 times/s, but GPS position only changes once per second, the same position is "set" 25 times (no visible change), then on the 26th emission the new position is snapped to → **step**.

## What Approach A changes

Replace `setCenter` with `animateTo` **but only when position actually moved**:

```kotlin
var lastPos: LatLng? = null

viewModel.cameraUpdates.collect { target ->
    val point = GeoPoint(...)
    if (reengage) {
        mv.controller.animateTo(point)
        reengage = false
    } else if (lastPos == null || distanceM(lastPos, target.position) > 2.0) {
        // New GPS fix arrived — animate smoothly over 600 ms
        mv.controller.animateTo(point, 600L, null)
    }
    // Bearing still applied every tick (40 ms) for heading-up smoothness
    mv.mapOrientation = -target.bearingDeg
    mv.invalidate()
    lastPos = target.position
}
```

## Timing diagram: what happens per second

```
GPS fix interval: 1s
mapRefreshFps: 25 (40ms sample period)
animateTo duration: 600ms

Time 0ms:     GPS fix #1 arrives → cameraUpdates emits position A → setCenter(A) or animateTo(A)
Time 40ms:    Same position A → collector sees no meaningful change → skip animation, just set bearing
Time 80ms:    Same → skip
...
Time 960ms:   Same → skip
Time 1000ms:  GPS fix #2 arrives → cameraUpdates emits position B
              └─ Approach A: distance(A, B) > 2m → animateTo(B, 600ms) starts
                 osmdroid runs scroll animation from A to B over 600ms at display refresh (60fps)
              └─ Current code: setCenter(B) → instant snap
Time 1040ms:  cameraUpdates emits position B → animation is still running → skip (position unchanged)
...
Time 1600ms:  animation finishes → steady at B
Time 2000ms:  GPS fix #3 → animateTo(C, 600ms)
```

## Net render cost comparison

| | Current (`setCenter`) | Approach A (`animateTo` 600ms) |
|---|---|---|
| **Frames when stationary** | 25 fps (invalidate every 40ms) | 25 fps (same) |
| **Frames during movement** | 25 fps | 60 fps for 600ms (36 frames) + 25 fps for 400ms (10 frames) = 46 fps average |
| **Extra frames/s** | baseline | +21 extra GPU renders per second |
| **Visual result** | Jittery stepping | Smooth glide from A to B |

### Why this is acceptable

The old problem with `animateTo` was that it ran **continuously** — every fix triggered a full ~1500ms animation that never finished before the next fix arrived (at 1s interval). That meant 100% duty cycle at 60fps.

With a **600ms bounded animation** at 1s intervals:

- 60fps × 600ms = 36 frames during movement
- 25fps × 400ms = 10 frames idle
- **Total: 46 frames/s** for 1 second → then boat stops moving, drops to 25fps

For context, a typical 60fps UI runs at 60 frames/s continuously. 46fps average during movement is well within normal operating range. On a boat phone running a navigation app, the GPS radio (not the GPU) remains the dominant battery drain.

## The critical guard: don't re-trigger animation on every sample

The `cameraUpdates` flow emits 25 times/second even when position hasn't changed (because `sample()` keeps emitting the latest value). Without the guard:

```kotlin
// BAD: every 40ms, position looks "new" → restart animation → stuttering mess
if (distance > 2m) mv.controller.animateTo(point, 600L, null)
```

With the guard, `animateTo` is called only when a real GPS fix arrives (~1/s), not 25 times/s.

## Bottom line

`mapRefreshFps` and `animateTo` are **orthogonal** — the flow controls data delivery rate, the animation controls viewport interpolation. The 25fps cap still governs how often the ViewModel checks position and bearing. `animateTo` adds a smooth visual transition on the consumer side without changing the data pipeline.

