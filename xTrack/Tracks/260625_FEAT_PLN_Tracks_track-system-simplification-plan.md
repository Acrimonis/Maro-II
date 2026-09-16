# Track Recording System — Consolidation Plan

## Motivation

The track recording pipeline has grown organically across three components ([`CoastlineViewModel`](../app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt), [`MapScreen`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt), [`TrackRecorder`](../app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt)), accumulating indirection layers and duplicated state. The goal is to consolidate, not to add features.

## Current Architecture

```
GPS fix ──▶ CoastlineViewModel
               │
               ├─ _gpsPosition (StateFlow<LatLng?>)
               ├─ _navigationState (bearing, speedKnots)
               ├─ _acquisitionMode (ACTIVE|IDLE)  ← AdaptiveGpsPolicy #1
               └─ deadReckoning ──▶ _gpsPosition (overwrites)
                                      │
          ┌───────────────────────────┘
          ▼
   MapScreen (565-597): combine(gpsPos, center, nav, ticker)
          │                        ┌─ hasLock = true (always)
          │                        └─ GpsFix reconstructed from ViewModel state
          ▼
   TrackRecorder.processFix()
          │
          ├─ AdaptiveGpsPolicy #2 (duplicate — separate instance)
          ├─ addPoint() → stationary? → spike rejection? → capture
          └─ _uiState.recordingPoints (full list copy per point)
```

## Consolidation Items

### C1 — Single Stillness Source (remove duplicate AdaptiveGpsPolicy)

**Problem:** Two `AdaptiveGpsPolicy` instances evolve independently — their anchor positions, timestamps, and IDLE/ACTIVE states diverge. TrackRecorder can think the boat is moving while CoastlineViewModel thinks it's stopped, or vice versa.

**Solution:** Expose `isStopped` from CoastlineViewModel. TrackRecorder reads this instead of running its own policy.

| Removed from TrackRecorder | Lines |
|---|---|
| `import AdaptiveGpsPolicy` | 21 |
| `private val policy = AdaptiveGpsPolicy()` | 167 |
| `policy.onFix(...)` + `moving` check | 324-331 |
| `adaptiveWindowMs`, `adaptiveThresholdM` constructor params | 114-115 |
| `policy.reset()` in `startManual()` and `cleanup()` | 220, 626 |

**Stillness logic per mode:**

| Mode | Source | `isStopped = true` when |
|------|--------|------------------------|
| GPS (`gpsMode == true`) | `adaptivePolicy == IDLE` | Boat within 20m of anchor for 30s |
| Demo (`gpsMode == false`) | `demoSpeedKnots == null` | No map drag for 500ms (existing stop-detection at line 602-607) |

### C2 — Guard Dead Reckoning Against Dormant GPS (align watchdog with stillness)

**Problem:** The stale-fix watchdog (`GPS_STALE_TIMEOUT_MS = 5s`) fires during normal stationary periods: when the boat stops, the GPS min-distance filter suppresses redundant fixes, the watchdog fires after 5s, and dead reckoning extrapolates along the last known course — injecting estimated positions into `_gpsPosition`.

**Solution:** Gate the stale watchdog and `startDeadReckoning()` on `_acquisitionMode == IDLE`. When the policy says ACTIVE, GPS is expected to be sending fixes — the lack of fixes is due to the min-distance filter, not a real dropout.

```kotlin
// CoastlineViewModel.kt line 665-670
staleWatchdogJob = viewModelScope.launch {
    delay(GPS_STALE_TIMEOUT_MS)
    if (_acquisitionMode.value == AcquisitionMode.IDLE) {  // ← gate
        _gpsStale.value = true
        _forceReconnect.value = true
        startDeadReckoning()
    }
}

// CoastlineViewModel.kt line 809-811
private fun startDeadReckoning() {
    deadReckoningJob?.cancel()
    val state = deadReckoningState ?: return
    if (_acquisitionMode.value != AcquisitionMode.IDLE) return  // ← defense-in-depth
    ...
}
```

### C3 — Flag Estimated Positions (source-of-truth metadata)

**Problem:** The virtual GpsFix in MapScreen hardcodes `hasLock = true` (line 593). TrackRecorder cannot distinguish real GPS positions from dead-reckoned estimates. Estimated positions enter the track as if they were actual GPS fixes.

**Solution:** Add `isEstimated: Boolean` to `TrackPoint`. When TrackRecorder detects an estimated position (via CoastlineViewModel's `_isEstimating`), it skips recording.

```kotlin
// TrackPoint.kt — new field
@ProtoNumber(16) val isEstimated: Boolean = false

// TrackRecorder.addPoint() — skip estimated
if (_isEstimating.value && gpsMode) return
```

### C4 — Direct Position Pipeline (remove virtual GpsFix indirection)

**Problem:** MapScreen reconstructs `GpsFix` objects from ViewModel StateFlows (position, bearing, speed, hardcoded `hasLock`). TrackRecorder decomposes them back into `TrackPoint`. This round-trip adds complexity with no benefit.

**Solution:** Introduce a lightweight `TrackSample` data class and emit it directly from CoastlineViewModel, bypassing the virtual GpsFix layer entirely.

```kotlin
// New: data/track/TrackSample.kt
data class TrackSample(
    val position: LatLng,
    val speedMps: Float?,
    val bearingDeg: Float?,
    val hasLock: Boolean,
    val isEstimated: Boolean,
    val timestampEpochMs: Long
)

// CoastlineViewModel emits TrackSample
private val _trackSample = MutableSharedFlow<TrackSample>(...)
val trackSample: SharedFlow<TrackSample> = _trackSample.asSharedFlow()

// MapScreen wires directly (removes combine at lines 553-597)
trackViewModel.startRecorder(viewModel.trackSample, appSettings)
```

**Demo mode:** A separate 1Hz ticker-based flow emits `TrackSample` from demo state (`_gpsPosition`, `demoSpeedKnots`, `demoBearingDeg`). Merged with the GPS flow.

### C5 — Stream Points Incrementally (reduce state copy)

**Problem:** Every captured point copies the entire `trackPoints` list into `_uiState.recordingPoints`. For long recordings (thousands of points), this serializes the full list into Compose recomposition on every capture.

**Solution:** Emit new points via `SharedFlow`. The UI appends to the osmdroid `Polyline` incrementally. Keep `recordingPoints` in the StateFlow for other consumers but stop copying the full list.

```kotlin
// TrackRecorder.kt
private val _newPoint = MutableSharedFlow<TrackPoint>(extraBufferCapacity = 64)
val newPoint: SharedFlow<TrackPoint> = _newPoint.asSharedFlow()

// MapScreen.kt — incremental polyline
recorder.newPoint.collect { point ->
    polyline.addPoint(GeoPoint(point.lat, point.lon))
    mv.invalidate()
}
```

---

## Implementation Phases

### Phase 1 — C1 + C2 + C3 (core consolidation)

**Files touched:** [`CoastlineViewModel.kt`](../app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt), [`TrackRecorder.kt`](../app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt), [`TrackViewModel.kt`](../app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt), [`TrackPoint.kt`](../app/src/main/java/ykws/android/maro/data/track/TrackPoint.kt)

**Changes:**

| File | Change |
|------|--------|
| `CoastlineViewModel.kt` | Add `_isStopped` StateFlow (public `isStopped`) |
| `CoastlineViewModel.kt` | Gate stale watchdog and `startDeadReckoning` on `_acquisitionMode == IDLE` |
| `CoastlineViewModel.kt` | Update `_isStopped` on each GPS fix (GPS mode) and on pan stop-detection (demo mode) |
| `TrackRecorder.kt` | Remove `AdaptiveGpsPolicy` import, field, `onFix()` call, `moving` check |
| `TrackRecorder.kt` | Remove `adaptiveWindowMs`, `adaptiveThresholdM` constructor params |
| `TrackRecorder.kt` | Replace stationary check with `isStopped` StateFlow |
| `TrackRecorder.kt` | Add `isEstimated` skip in `addPoint()` |
| `TrackRecorder.kt` | Remove `policy.reset()` calls |
| `TrackPoint.kt` | Add `isEstimated: Boolean` field |
| `TrackViewModel.kt` | Wire `isStopped` to TrackRecorder |

**Verification:**
- Unit tests pass (especially `AdaptiveGpsPolicyTest`)
- GPS mode: track records while moving, pauses while stationary
- Demo mode: track records while dragging, pauses when stopped dragging

### Phase 2 — C4 (pipeline simplification)

**Files touched:** [`CoastlineViewModel.kt`](../app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt), [`MapScreen.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt), [`TrackRecorder.kt`](../app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt), new file `TrackSample.kt`

**Changes:**

| File | Change |
|------|--------|
| New: `TrackSample.kt` | Add `TrackSample` data class |
| `CoastlineViewModel.kt` | Emit `TrackSample` from GPS fix processing |
| `MapScreen.kt` | Remove virtual GpsFix combine (lines 553-597) |
| `MapScreen.kt` | Wire `viewModel.trackSample` directly to TrackRecorder |
| `MapScreen.kt` | Add demo-mode 1Hz `TrackSample` ticker, merge with GPS flow |
| `TrackRecorder.kt` | Replace `processFix(fix: GpsFix)` with `processSample(sample: TrackSample)` |

**Verification:**
- Both modes record correctly
- No regression in track point data (lat/lon/speed/bearing/timing)
- Virtual GpsFix is fully removed

### Phase 3 — C5 (incremental streaming)

**Files touched:** [`TrackRecorder.kt`](../app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt), [`MapScreen.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)

**Changes:**

| File | Change |
|------|--------|
| `TrackRecorder.kt` | Add `_newPoint` SharedFlow, emit in `captureAcceptedPoint()` |
| `MapScreen.kt` | Switch polyline update from full-list recomposition to incremental append |

**Verification:**
- Track polyline draws correctly during recording
- No visual difference from current behavior

---

## Demo Mode Behavior Summary

| Scenario | `isStopped` | Points recorded? |
|----------|------------|-----------------|
| User dragging map | `false` (demoSpeedKnots != null) | Yes |
| User stopped dragging < 500ms | `false` (within PAN_STOP_DELAY) | Yes |
| User stopped dragging ≥ 500ms | `true` (demoSpeedKnots = null) | No |
| GPS mode, boat moving | `false` (policy = ACTIVE) | Yes |
| GPS mode, boat stopped 30s | `true` (policy = IDLE) | No |

---

## Files Changed Per Phase

| Phase | Files |
|-------|-------|
| Phase 1 | `CoastlineViewModel.kt`, `TrackRecorder.kt`, `TrackViewModel.kt`, `TrackPoint.kt` |
| Phase 2 | `CoastlineViewModel.kt`, `MapScreen.kt`, `TrackRecorder.kt`, `TrackSample.kt` (new) |
| Phase 3 | `TrackRecorder.kt`, `MapScreen.kt` |

**Total lines removed:** ~60 (TrackRecorder policy + MapScreen virtual GpsFix)
**Total lines added:** ~120 (new StateFlows, guards, TrackSample, streaming)

---

## Branch Strategy

Create a feature branch off `develop`:

```
git checkout develop
git pull origin develop
git checkout -b feature/track-recorder-consolidation
```

Implement phases sequentially. Run `./gradlew testDebugUnitTest` after each phase.
