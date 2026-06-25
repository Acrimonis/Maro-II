# Track Recording System — yarefact Consolidation

> **Feature:** GPS
> **Subfeature:** yarefact
> **Date:** 2026-06-25
> **Status:** planned, reviewed (round 2)
> **Validated against:** codebase commit at 2026-06-25
> **Design decisions:** finalized via Architect + Ask review on 2026-06-25

## Motivation

The track recording pipeline has grown organically across three components ([`CoastlineViewModel`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt) → rename to `NavigationViewModel`, [`MapScreen`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt), [`TrackRecorder`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt)), accumulating indirection layers and duplicated state. Goal: consolidate, not add features.

## Design Decisions (from review)

| # | Issue | Decision |
|---|-------|----------|
| 1 | `isStopped` always false in demo mode | Feed map center into `AdaptiveGpsPolicy` in demo mode. `isStopped` = single truth for both modes. Gate: `if (isStopped.value) return` — no `gpsMode` branch. |
| 2 | Demo TrackSample source unclear | MapScreen reads NavigationVM gauges, pushes `TrackSample` → `TrackViewModel.pushTrackSample()`. MapScreen = wire connector, keeps NavigationVM and TrackVM decoupled. |
| 3 | Watchdog reads stale acquisition mode | Move `adaptivePolicy.onFix()` before watchdog block. Rename to `GpsSignalWatchdog`. |
| 4 | `initRecorder()` has no `isStopped` | Default `isStopped = MutableStateFlow(false)` in TrackRecorder — harmless before pipeline is wired. |
| 5 | `isEstimating` guard removed in Phase B | Keep guard in new MapScreen combine: `if (estimating) return@combine null`. |
| 6 | CoastlineViewModel badly named | Rename `CoastlineViewModel` → `NavigationViewModel`. |

## Current Architecture

```
GPS fix ──▶ CoastlineViewModel (→ NavigationViewModel)
               │
               ├─ _gpsPosition (StateFlow<LatLng?>)
               ├─ _navigationState (bearing, speedKnots, demoSpeedKnots)
               ├─ _acquisitionMode (ACTIVE|IDLE)  ← AdaptiveGpsPolicy
               └─ deadReckoning ──▶ _gpsPosition (overwrites)
                                      │
          ┌────────────────────────────┘
          ▼
   MapScreen (554-579): combine(gpsPos, center, nav, isEstimating, ticker)
          │                        ┌─ hasLock = true (always)
          │                        └─ GpsFix reconstructed from ViewModel state
          ▼
   TrackRecorder.processFix()
          │
          ├─ AdaptiveGpsPolicy #2 (duplicate — separate instance)
          ├─ addPoint() → stationary? → spike rejection? → capture
          └─ _uiState.recordingPoints (full list copy per point)
```

## Target Architecture

```
MapScreen: combine(gpsPos, center, nav, isEstimating, ticker)
                │  if (estimating) return@combine null
                │  TrackSample(position, speedMps, bearingDeg, hasLock, timestamp)
                │
                ▼ pushTrackSample()
   TrackViewModel._trackSample (SharedFlow)
                │
                ▼
   TrackRecorder.processSample()
          │
          ├─ isStopped from NavigationVM (single AdaptiveGpsPolicy, shared)
          ├─ addPoint() → spike rejection → capture
          └─ _uiState.recordingPoints (full list copy per point)
```

## Validation Against Codebase

| Item | Status | Evidence |
|------|--------|----------|
| C1: duplicate AdaptiveGpsPolicy | **Partial** — `isStopped` exposed but TrackRecorder has own policy | [`isStopped`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt:346-348) ✅ · [`policy` in TrackRecorder](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:167) ❌ |
| C2: DR guard on dormant GPS | **Partial** — DR state invalidated on IDLE, watchdog unguarded | [`deadReckoningState = null`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt:712-714) ✅ · [watchdog unguarded](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt:665-670) ❌ |
| C3: isEstimated on TrackPoint | **Skipped** — solved by `isEstimating` guard in combine | [`if (estimating) return@combine null`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:563) |
| C4: virtual GpsFix indirection | **Not done** — combine still creates virtual GpsFix | [`combine` at 554-579](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:554-579) |
| C5: full list copy | **Not done** — per-point copy | [`recordingPoints = currentTrack!!.trackPoints`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:472) |

---

## Phase A — Core Consolidation

### Changes

#### A1 — Wire `isStopped` into TrackRecorder, remove duplicate policy

| File | Change |
|------|--------|
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:107-120) | Remove `adaptiveWindowMs`, `adaptiveThresholdM` constructor params |
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:107-120) | Add `isStopped: StateFlow<Boolean> = MutableStateFlow(false)` constructor param |
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:167) | Remove `private val policy = AdaptiveGpsPolicy()` |
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:21) | Remove `import ykws.android.maro.data.location.AdaptiveGpsPolicy` |
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:20) | Remove `import ykws.android.maro.data.location.AcquisitionMode` — orphan after policy removal |
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:324-329) | Remove `policy.onFix(...)` call |
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:330-331) | Replace `policy.isStill()` with `isStopped.value` |
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:337) | Remove `gpsMode` guard: `if (!moving && gpsMode) return` → `if (isStopped.value) return` |
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:220) | Remove `policy.reset()` in `startManual()` |
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:626) | Remove `policy.reset()` in `cleanup()` |
| [`TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:48-64) | Accept `isStopped` param in `startRecorder()`, pass to TrackRecorder |
| [`TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt) | Add `fun setStoppedSource(flow: StateFlow<Boolean>)` — survives Phase B when `startRecorder()` is removed |
| [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:580) | Pass `navigationViewModel.isStopped` to `trackViewModel.startRecorder()` |
| [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | Call `trackViewModel.setStoppedSource(navigationViewModel.isStopped)` once at init |

#### A2 — Rename `GpsSignalWatchdog`, reorder acquisition mode, gate on IDLE

**Reorder:** Move `adaptivePolicy.onFix()` BEFORE the watchdog block so the gate reads current fix's acquisition mode, not previous.

```kotlin
// NavigationViewModel.kt — GPS onEach block (reordered)
// 1. Update position, map center (existing)
// 2. Update acquisition mode EARLY
val s = settings.value
_acquisitionMode.value = adaptivePolicy.onFix(now, fix.position,
    s.stopDetectionTimeSec * 1000L, s.stopDetectionDistanceM.toDouble())
if (_acquisitionMode.value == AcquisitionMode.IDLE) deadReckoningState = null
// 3. Stale-watchdog (gated)
if (fix.hasLock) {
    deadReckoningJob?.cancel()
    _isEstimating.value = false
    _gpsStale.value = false
    lastFixMs = now
    gpsSignalWatchdogJob?.cancel()
    if (_acquisitionMode.value != AcquisitionMode.IDLE) {
        gpsSignalWatchdogJob = viewModelScope.launch {
            delay(GPS_STALE_TIMEOUT_MS)
            if (_acquisitionMode.value != AcquisitionMode.IDLE) {
                _gpsStale.value = true
                _forceReconnect.value = true
                startDeadReckoning()
            }
        }
    }
}
```

| File | Change |
|------|--------|
| [`CoastlineViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt:665-670) | Rename `staleWatchdogJob` → `gpsSignalWatchdogJob`. Reorder. Gate on `!= IDLE`. |
| [`CoastlineViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt:809-811) | Add `if (_acquisitionMode.value != IDLE) return` at top of `startDeadReckoning()` |

#### A3 — Feed map center into adaptive policy in demo mode

NavigationViewModel needs a method to accept map center positions when GPS is off.

```kotlin
// NavigationViewModel.kt
fun feedDemoPosition(lat: Double, lon: Double) {
    val now = SystemClock.elapsedRealtime()
    val s = settings.value
    _acquisitionMode.value = adaptivePolicy.onFix(now, LatLng(lat, lon),
        s.stopDetectionTimeSec * 1000L, s.stopDetectionDistanceM.toDouble())
}
```

MapScreen calls this on each pan update in demo mode.

| File | Change |
|------|--------|
| [`CoastlineViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt) | Add `feedDemoPosition(lat, lon)` method |
| [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | Call `navigationViewModel.feedDemoPosition()` on pan/drag in demo mode |

#### A4 — Rename `CoastlineViewModel` → `NavigationViewModel`

Rename class, file, and all references across the codebase. Update imports in MapScreen, DashboardPanel, etc.

| Files | Change |
|-------|--------|
| `CoastlineViewModel.kt` | Rename file → `NavigationViewModel.kt`. Rename class. |
| `MainActivity.kt` | Update import + type reference + Factory reference |
| `MapScreen.kt` | Update import + references |
| `DashboardPanel.kt` | Update import + references (cosmetic — uses `CoastlineState`, not `CoastlineViewModel`) |
| All other referencing files | Update imports |

### Verification

- Unit tests pass
- GPS mode: track records while moving, pauses while stationary
- Demo mode: track records while dragging, pauses when stopped dragging ≥ stopDetectionTimeSec
- Dead reckoning does not fire when boat is stationary with GPS min-distance filtering
- Dead reckoning fires on genuine GPS dropout while moving

---

## Phase B — Pipeline Simplification

### Changes

#### B1 — Create `TrackSample` data class

```kotlin
// app/src/main/java/ykws/android/maro/data/track/TrackSample.kt
package ykws.android.maro.data.track
import ykws.android.maro.data.model.LatLng

data class TrackSample(
    val position: LatLng,
    val speedMps: Float?,
    val bearingDeg: Float?,
    val hasLock: Boolean,
    val timestampEpochMs: Long
)
```

#### B2 — TrackViewModel owns `_trackSample` SharedFlow

```kotlin
// TrackViewModel.kt — new fields
private val _trackSample = MutableSharedFlow<TrackSample>(extraBufferCapacity = 8)
val trackSample: SharedFlow<TrackSample> = _trackSample.asSharedFlow()

fun pushTrackSample(sample: TrackSample) {
    _trackSample.tryEmit(sample)
}
```

TrackRecorder.start() collects `trackSample` internally instead of accepting an external Flow parameter.

#### B3 — MapScreen combine produces TrackSample

Replace the current GpsFix combine. Keep `isEstimating` guard.

```kotlin
// MapScreen.kt — replaces current combine at 554-579
val trackSampleFlow = combine(
    navigationViewModel.gpsPosition,
    navigationViewModel.mapCenter,
    navigationViewModel.navigationState,
    navigationViewModel.isEstimating,
    ticker
) { gpsPos, center, nav, estimating, _ ->
    if (estimating) return@combine null
    val isGps = appSettings.gpsMode
    val pos = gpsPos ?: center
    val speedMs = (if (isGps) nav.speedKnots else nav.demoSpeedKnots)?.let { it * 0.514444f }
    val bearing = if (isGps) nav.bearingDeg else nav.demoBearingDeg
    TrackSample(
        position = pos,
        speedMps = speedMs,
        bearingDeg = bearing,
        hasLock = isGps,
        timestampEpochMs = System.currentTimeMillis()
    )
}.filterNotNull()

trackSampleFlow.collect { trackViewModel.pushTrackSample(it) }
```

#### B4 — TrackRecorder: GpsFix → TrackSample

All method signatures change from `GpsFix` to `TrackSample`. `processFix()` → `processSample()`. Accessors are identical.

| File | Change |
|------|--------|
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:177) | `start(gpsFlow: Flow<GpsFix>)` → internal collect of `trackSample` |
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:225) | `processFix(fix: GpsFix)` → `processSample(sample: TrackSample)` |
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:22) | Remove `import GpsFix` |
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:319) | `addPoint()` param type changes |
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:411) | `captureAcceptedPoint()` param type changes |
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:579) | `checkLandDetection()` param type changes |
| [`TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:12) | Remove `import GpsFix`, add `import TrackSample` |

#### B5 — Remove `startRecorder()` from TrackViewModel API

Since TrackRecorder now collects `trackSample` internally, MapScreen no longer calls `startRecorder()`. Instead, MapScreen calls `trackViewModel.pushTrackSample()` on each tick. TrackRecorder starts collecting when TrackRecorder is created.

Simplify: TrackRecorder's `start()` becomes internal — called by TrackViewModel on init. MapScreen just pushes data.

### Verification

- Both GPS and demo modes record correctly
- No regression in track point data
- Virtual GpsFix fully removed

---

## Phase C — Streaming Optimization (deferred)

### Changes

| File | Change |
|------|--------|
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt) | Add `_newPoint: MutableSharedFlow<TrackPoint>`, emit in `captureAcceptedPoint()` |
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:466-473) | Remove `recordingPoints = currentTrack!!.trackPoints` from `_uiState.update` |
| [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | Incremental polyline append via `_newPoint` |

### Verification

- Polyline draws correctly during recording
- Measurable reduction in Compose recomposition

---

## Stillness Logic

| Mode | Source | `isStopped = true` when |
|------|--------|------------------------|
| GPS (`gpsMode == true`) | `_acquisitionMode == IDLE` | Boat within `stopDetectionDistanceM` for `stopDetectionTimeSec` |
| Demo (`gpsMode == false`) | `_acquisitionMode == IDLE` | Map center within `stopDetectionDistanceM` for `stopDetectionTimeSec` (via `feedDemoPosition`) |

## Branch Strategy

```bash
git checkout develop && git pull origin develop
git checkout -b feature/gps-yarefact
```

Implement phases sequentially. `./gradlew testDebugUnitTest` after each phase.

## Files Changed

### Phase A
- `CoastlineViewModel.kt` → `NavigationViewModel.kt` (rename + reorder watchdog + feedDemoPosition)
- `TrackRecorder.kt` — remove policy, wire isStopped, remove `gpsMode` gate
- `TrackViewModel.kt` — wire isStopped
- `MapScreen.kt` — pass isStopped, call feedDemoPosition, rename imports
- `DashboardPanel.kt` — import rename
- All CoastlineViewModel referencing files — import rename

### Phase B
- `TrackSample.kt` — **new file**
- `TrackViewModel.kt` — `_trackSample` SharedFlow, `pushTrackSample()`
- `TrackRecorder.kt` — GpsFix → TrackSample, internal collect
- `MapScreen.kt` — new TrackSample combine, pushTrackSample

### Phase C (deferred)
- `TrackRecorder.kt` — `_newPoint` SharedFlow, remove full-list copy
- `MapScreen.kt` — incremental polyline append
