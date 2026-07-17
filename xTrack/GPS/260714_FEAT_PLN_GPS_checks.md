# GPS Checks — Spike Gates, Coarse Capture, and Map Update Audit

**Created:** 2026-07-14 08:15 UTC
**Updated:** 2026-07-14 08:32 UTC
**Status:** design

## Context

Audit of GPS spike rejection gates, capture-vs-save timing, and causes of coarse
capturing / map updating during track recording. Evolved into a map-smoothness fix proposal.

## Spike Gate Handling

### Where are spike gates applied?

Spike rejection lives in [`TrackRecorder.addPoint()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt) — at **capture time**, not at save time. When a `GpsFix` arrives, the four-gate pipeline (Gates 0–3) runs before the point is accepted into the in-memory buffer. Only accepted points are appended to `_recordingPoints`; rejected points are discarded immediately.

`finalizeTrack()` / `repository.save()` never sees rejected spikes — they are filtered upstream.

### Gate summary

| Gate | Condition | Action |
|------|-----------|--------|
| 0 (Dedup) | Same lat/lon within 500 ms | Drop duplicate |
| 1 (Stale-timeout cap) | Speed cap: 48 kn stationary, 96 kn otherwise | Reject spike |
| 2 (GPS-reported speed) | GPS speed > 40 kn and not on land | Reject spike |
| 3 (Stationary distance) | GPS speed < 2 kn, distance > 150 m | Reject spike |
| Recovery | Stale timeout (10 s) or Gate 0 acceptance | Reset rejection state |

## Map Smoothness Fix

### Root Cause

[`MapScreen.kt:494`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:494) calls `animateTo(fixPosition, null, 600ms)`. The map scrolls toward the GPS fix over 600ms, but the boat moves `speed × 0.6s` during the animation. By the time the scroll completes, the target is stale:

| Speed | Displacement in 600ms | Map-center lag |
|-------|----------------------|----------------|
| 6 kn (3.1 m/s) | 1.9 m | Subtle drag |
| 12 kn (6.2 m/s) | 3.7 m | Visible lag |
| 20 kn (10.3 m/s) | 6.2 m | Obvious trailing |
| 30 kn (15.4 m/s) | 9.3 m | Map feels "behind" |

This creates a perpetual "chasing the tail" effect — the map is always animating toward a position that's already stale by the time the animation completes.

### Proposed Fix: Unified Continuous Dead Reckoning

**Core idea:** Make the existing dead reckoning system run continuously (not just during GPS dropout), split position into two streams, and replace `animateTo` with `setCenter`.

#### Architecture

```
Raw GPS fix (1 Hz)
  ├─→ _gpsPosition (TRUTH)          → track recording, zones, dashboard
  └─→ Dead Reckoning (continuous)   → _displayPosition (LEAD, 20 Hz)
                                       → cameraUpdates → setCenter()
```

**Two position streams:**

| Stream | Source | Update rate | Consumers |
|--------|--------|-------------|-----------|
| `_gpsPosition` | Raw GPS fix (truth) | ~1 Hz | Track recording, zone queries, dashboard, `isOnWater` |
| `_displayPosition` | Continuous dead reckoning | ~20 Hz (every 50ms) | Map centering only (`cameraUpdates`) |

**Dead reckoning coroutine (continuous):**
```
On each GPS fix:
  - Store lastFix (position, bearing, speed, timestamp)
  - _displayPosition = lastFix.position  (reset to ground truth)

Every 50ms:
  - If speed < 3 kn → _displayPosition = lastFix.position (no lead)
  - elapsed = (now - lastFix.timestamp) / 1000
  - leadM = min(speed × elapsed, MAX_LEAD_M = 30m)
  - _displayPosition = pointAlongBearing(lastFix.position, bearing, leadM)
```

**MapScreen change:**
```
cameraUpdates.collect { target ->
    mv.controller.setCenter(GeoPoint(target.position))
    mv.mapOrientation = -target.bearingDeg
    mv.invalidate()
}
```

Replace `animateTo(point, null, GPS_ANIMATION_DURATION_MS)` with `setCenter(point)`. The smoothness comes from the 20 Hz dead reckoning stream, not from osmdroid's animation. No more lag — `setCenter` is instant.

### Review: Accuracy & UX

#### Accuracy of Position

| Concern | Assessment |
|---------|------------|
| **Drift from truth** | `_displayPosition` resets to raw GPS fix on every new fix (every ~1s). Max drift = `speed × 1s` before reset. At 20 knots: ~10m max drift between fixes. Reset is instant (`setCenter` snap). |
| **Bearing noise at low speed** | Gated: no lead below 3 knots. `_displayPosition = lastFix` (raw truth) at idle/low speed. |
| **GPS spike in speed** | `MAX_LEAD_M = 30m` cap prevents wild extrapolation from bogus speed readings. |
| **Track recording unaffected** | `_gpsPosition` remains raw truth. `_displayPosition` is display-only. No change to recorded track accuracy. |
| **Zone queries unaffected** | `_gpsPosition` drives zone auto-show, distance queries, speed compliance. `_displayPosition` never touches spatial queries. |

#### User Experience

| Aspect | Before (animateTo) | After (continuous DR + setCenter) |
|--------|-------------------|----------------------------------|
| **Map smoothness** | Jagged 1 Hz jumps, smoothed by 600ms animation → laggy | 20 Hz smooth updates, instant positioning → fluid |
| **Position accuracy on screen** | Always 2-9m behind (lag) | Within 10m of truth (DR drift between fixes), snaps to truth on each fix |
| **At anchor / idle** | 3m haversine guard filters GPS jitter | No lead below 3kn → same jitter filtering |
| **During GPS dropout** | Dead reckoning activates (existing) | Dead reckoning continues seamlessly (same coroutine) |
| **Sharp turns** | 600ms animation follows old heading | `_displayPosition` uses current bearing from GPS — extrapolates along new heading immediately |
| **Perceived responsiveness** | Sluggish — map always catching up | Crisp — boat marker stays centered |

**Key UX win:** The stale comment at [`NavigationViewModel.kt:370`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt:370) says *"the screen collector applies each tick with one `setCenter` + `mapOrientation` (a single repaint)"* — this was the intended design. The current `animateTo` was a workaround for 1 Hz stepping. With continuous dead reckoning providing 20 Hz positions, `setCenter` becomes the correct choice.

### What Changes

| File | Change | Risk |
|------|--------|------|
| [`NavigationViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt) | Add `_displayPosition: MutableStateFlow<LatLng?>`. Add continuous dead reckoning coroutine (50ms tick). `cameraUpdates` reads from `_displayPosition` instead of `_gpsPosition`. | Medium — new coroutine, new StateFlow |
| [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | Replace `animateTo(point, null, GPS_ANIMATION_DURATION_MS)` with `setCenter(point)` in GPS follow collector (line 494). Remove haversine guard (no longer needed — DR smoothness replaces it). Keep re-engage `animateTo(point)` for smooth scroll-back after panning. | Low — single-line change |
| Constants | Remove `GPS_ANIMATION_DURATION_MS`, `GPS_ANIMATION_MIN_MOVE_M`. Add `DISPLAY_POSITION_INTERVAL_MS = 50L`, `MIN_LEAD_SPEED_MPS = 1.54`, `MAX_LEAD_M = 30.0`. | Low |

### Edge Cases

| Scenario | Behavior |
|----------|----------|
| **GPS fix arrives during DR extrapolation** | `_displayPosition` snaps to new fix (sub-meter correction). User sees a tiny shift — imperceptible at typical zoom. |
| **GPS dropout** | DR coroutine continues extrapolating from last known course+speed. `_isEstimating = true` gates track recording. Same as today's `startDeadReckoning()`, but unified in the same coroutine. |
| **Speed drops below 3 knots mid-extrapolation** | `_displayPosition` freezes at `lastFix.position` on next tick. No lead. Boat marker stays at last known truth position. |
| **GPS reports 0 speed but position changes** | At anchor, GPS position can drift 3-5m with 0 speed. No lead (speed < 3kn gate). `setCenter` on each fix will show small position shifts — same as today. |
| **Very fast boat (>30 knots)** | `MAX_LEAD_M = 30m` cap. At 40 knots with 1s between fixes: actual displacement ~20.6m, DR leads ~20.6m but capped at 30m — sufficient. |

### Rejected Alternatives

| Approach | Why rejected |
|----------|-------------|
| **Dynamic animation duration** (600→200→100ms) | Reduces lag but doesn't eliminate it. Still has residual ~2m lag at 20 knots. Still has 1 Hz stepping feel. |
| **Dead-reckoned target + animateTo** (lead the animation target but keep animateTo) | Double-extrapolation during GPS dropout. Two separate lead mechanisms. More complex than unified approach. |
| **setCenter on every fix** (no DR, no animation) | Accurate but jagged — 1 Hz discrete jumps with no smoothing. |

### Implementation Order

1. Add `_displayPosition` StateFlow + continuous DR coroutine in NavigationViewModel
2. Switch `cameraUpdates` to use `_displayPosition`
3. Replace `animateTo` with `setCenter` in MapScreen GPS follow collector
4. Remove unused constants (`GPS_ANIMATION_DURATION_MS`, `GPS_ANIMATION_MIN_MOVE_M`)
5. Build (apk-build.bat)
6. On-water verification: idle, 6 kn, 12 kn, 20 kn, GPS dropout (tunnel/shadow)

## Todos

- [ ] Implement `_displayPosition` + continuous dead reckoning coroutine in NavigationViewModel
- [ ] Switch `cameraUpdates` source from `_gpsPosition` to `_displayPosition`
- [ ] Replace `animateTo` with `setCenter` in MapScreen GPS follow collector
- [ ] Remove unused constants, add new constants
- [ ] Build (apk-build.bat) + on-water verification at idle, 6, 12, 20 knots + GPS dropout
