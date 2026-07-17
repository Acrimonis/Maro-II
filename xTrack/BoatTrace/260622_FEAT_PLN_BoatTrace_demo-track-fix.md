<!-- scope: feature -->
# Track Visibility in Demo Mode — Fix Plan

> **Branch:** `feature/dev`
> **Active Feature:** BoatTrace
> **Subfeature:** `demo-track-render`

---

## Root Causes

| # | File | Line | Bug |
|---|------|------|-----|
| 1 | [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt) | 352 | `recordingPoints = track.trackPoints` uses **pre-append** list — after 1st point, UI sees `[]` → no polyline until 2nd point |
| 2 | [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt) | 280 | `if (!moving) return` gates on `AdaptiveGpsPolicy.isStill()`. Policy uses 20m/30s defaults from settings — in demo mode, panning <20m/tick → zero points captured |

## Design

Two targeted fixes, no architectural changes:

### Fix 1: Off-by-one at line 352

```kotlin
// BEFORE (line 352):
recordingPoints = track.trackPoints

// AFTER:
recordingPoints = currentTrack!!.trackPoints
```

`currentTrack` was already updated at line 336 with the new point appended. Use it.

### Fix 2: Bypass stillness gate in demo mode

The TrackRecorder currently has no knowledge of GPS vs demo mode. Add a `gpsMode: Boolean` constructor parameter:

```kotlin
// TrackRecorder.kt — new constructor param
class TrackRecorder(
    private val repository: TrackRepository,
    private val gpsMode: Boolean = true,  // NEW
    ...
)
```

In `addPoint()` at line 280:
```kotlin
// BEFORE:
if (!moving) return

// AFTER:
// In GPS mode, skip points when stationary (battery saving).
// In demo mode, record every tick — user explicitly started recording.
if (!moving && gpsMode) return
```

### Fix 3: Pass `gpsMode` from TrackViewModel

[`TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:50):
```kotlin
val rec = TrackRecorder(
    repository = repository,
    gpsMode = settings.gpsMode,  // NEW
    ...
)
```

Same change in `initRecorder()` at line 95.

### Fix 4 (pre-existing): `hasCourse` in demo GpsFix

The demo-mode `GpsFix` already has `hasCourse = speedMs != null && speedMs > 0.5f` at line 530. If `demoSpeedKnots` is null (stationary pan), `hasCourse = false`. This is correct — no change needed.

---

## Files to Modify

| File | Changes |
|------|---------|
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt) | +`gpsMode` param (line ~85), fix off-by-one (line 352), conditional stillness gate (line 280) |
| [`TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt) | Pass `settings.gpsMode` in 2 constructor calls (lines 50, 95) |

**3 lines of logic change, ~6 lines total.**
