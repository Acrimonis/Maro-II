# GPS Trace Pipeline — Complete Reference

> **Updated:** 2026-07-17 | **Status:** Current as of poor-reception implementation
> **Scope:** Full pipeline from GPS fix arrival to GPX export
> **Source files:** [`GpsLocationSource.kt`](app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt), [`AdaptiveGpsPolicy.kt`](app/src/main/java/ykws/android/maro/data/location/AdaptiveGpsPolicy.kt), [`NavigationViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt), [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt), [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt)

---

## Architecture Overview

```
GpsLocationSource          NavigationViewModel         MapScreen              TrackRecorder
     │                            │                       │                       │
     │ GpsFix                     │                       │                       │
     ├──▶ onEach { fix } ─────────┤                       │                       │
     │                            │ adaptivePolicy.onFix() │                       │
     │                            │ _gpsAccuracy = ...     │                       │
     │                            │ _accuracyIsPoor = ...  │                       │
     │                            │                       │                       │
     │                            │ gpsPosition ──────────▶│ combine → TrackSample  │
     │                            │ navigationState ──────▶│                       │
     │                            │ gpsAccuracy ──────────▶│                       │
     │                            │                       │ pushTrackSample() ───▶│
     │                            │                       │                       │ addPoint()
     │                            │                       │                       ├──▶ Gates (10)
     │                            │                       │                       ├──▶ BoatMarker lifecycle
     │                            │                       │                       └──▶ TrackPoint
```

---

## Stage 1: GPS Fix Acquisition

**File:** [`GpsLocationSource.kt`](app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt)

Android `LocationManager.GPS_PROVIDER` → `callbackFlow<GpsFix>`. Single listener ensures FIFO ordering.

### GpsFix fields

| Field | Source | Notes |
|-------|--------|-------|
| `position` | `Location.latitude/longitude` | WGS84 |
| `bearingDeg` | `Location.bearing` | Only when `speed > MIN_SPEED_MPS` (0.5 m/s) |
| `hasCourse` | `true` when moving | Gates compass vs GPS heading |
| `speedMps` | `Location.speed` | Null when `!hasSpeed()` |
| `hasLock` | GNSS satellite count ≥ 4 | Set false on `TEMPORARILY_UNAVAILABLE` / `onStopped` |
| `timestampEpochMs` | `System.currentTimeMillis()` | Wall clock |
| `accuracyM` | `Location.getAccuracy()` | **New (poor-reception).** 3–5m outdoor, 30–65m indoor. Null when unavailable. |

### GNSS monitoring
- `GnssStatus.Callback` tracks satellite count
- `< 4 satellites` → `emitNoLock()` — emits GpsFix with `hasLock = false`
- `onStopped()` → same

---

## Stage 2: NavigationViewModel Dispatch

**File:** [`NavigationViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt)

### GPS subscription parameters (gpsParams combine)

| Parameter | ACTIVE mode | IDLE mode (delayGps=true) | IDLE mode (accuracy poor) |
|-----------|-------------|--------------------------|--------------------------|
| `intervalMs` | `gpsActiveIntervalSec × 1000` (default 2s) | `stopDetectionTimeSec × 1000 × pct / 100` (default 36s) | `minOf(dormant, 10000)` = **10s floor** |
| `minDistanceM` | `gpsActiveMinDistanceM` (default 5m) | `gpsIdleMinDistanceM` (default 0m) | Same |
| Source | `requestLocationUpdates()` | Same | Same |

### StateFlows updated per fix

| StateFlow | Updated | Purpose |
|-----------|---------|---------|
| `_gpsPosition` | Every fix | Map center, dead reckoning |
| `_acquisitionMode` | `adaptivePolicy.onFix()` result | ACTIVE/IDLE → GPS icon, isStopped |
| `_gpsAccuracy` | `fix.accuracyM` | **New.** WEAK GPS icon |
| `_accuracyIsPoor` | `accuracyM > GPS_ACCURACY_GOOD_THRESHOLD_M` (10m) | **New.** IDLE floor activation |
| `_gpsStale` | Watchdog timeout | STALE GPS icon |

### AdaptiveGpsPolicy decision

**File:** [`AdaptiveGpsPolicy.kt`](app/src/main/java/ykws/android/maro/data/location/AdaptiveGpsPolicy.kt)

```
onFix(nowMs, pos, windowMs, thresholdM, speedMps?, accuracyM?)
    │
    ├─ accuracyM > 20m? → widen thresholdM to accuracyM  (NEW: poor-reception)
    │
    ├─ displacement ≥ effectiveThresholdM?
    │   ├─ speed < 0.5 m/s? → tiebreaker: re-anchor, stay ACTIVE  (NEW: poor-reception)
    │   └─ else → re-anchor, return ACTIVE
    │
    └─ displacement < thresholdM?
        ├─ windowMs elapsed? → IDLE
        └─ else → ACTIVE
```

| Constant | Value | Location |
|----------|-------|----------|
| `MIN_SPEED_MPS` | 0.5 | `GpsLocationSource.kt` |
| `ACCURACY_FLOOR_THRESHOLD_M` | 20.0 | `AdaptiveGpsPolicy` companion |

### Dead reckoning

When GPS lock is lost while moving: extrapolates position along last known bearing at last known speed for up to `DEAD_RECKONING_MAX_MS`. Display only — `_isEstimating = true` gates TrackSample creation in MapScreen.

---

## Stage 3: MapScreen → TrackSample

**File:** [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)

`combine(gpsPosition, mapCenter, navigationState, isEstimating, ticker)` → `TrackSample`:

| TrackSample field | GPS mode source | Demo mode source |
|-------------------|-----------------|------------------|
| `position` | `gpsPosition` | `mapCenter` |
| `speedMps` | `navigationState.speedKnots × 0.514` | `demoSpeedKnots × 0.514` |
| `bearingDeg` | `navigationState.bearingDeg` | `demoBearingDeg` |
| `hasLock` | `true` (GPS) | `false` (demo) |
| `timestampEpochMs` | `System.currentTimeMillis()` | Same |
| `accuracyM` | `gpsAccuracy.value` | `null` |

Gated by `if (estimating) return@combine null` — dead reckoning never reaches TrackRecorder.

---

## Stage 4: TrackRecorder.addPoint() — Gate Pipeline

**File:** [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt)

All gates execute in order. First rejection ends processing for that sample.

### Pre-gates

| # | Gate | Line | Condition | Action |
|---|------|------|-----------|--------|
| 0 | Resume gap | 589 | `isResuming == true` | Insert GAP marker if distance/time threshold |
| 1 | **Dedup (dynamic)** | 606 | Identical lat/lon within window | Skip |
| 2 | isStopped | 663 | `isStopped.value == true` | Skip (no capture when stationary) |
| 3 | No speed | 667 | `sample.speedMps == null` | Skip (transient GPS state) |

### Dedup window (dynamic)

| Condition | Window | Constant |
|-----------|--------|----------|
| `speedMps < MIN_SPEED_MPS` (0.5 m/s) | 5000ms | `STATIONARY_DEDUP_WINDOW_MS` |
| `speedMps ≥ MIN_SPEED_MPS` | 500ms | `MOVING_DEDUP_WINDOW_MS` |

### Accuracy gate (NEW — poor-reception)

| # | Gate | Line | Condition | Action |
|---|------|------|-----------|--------|
| 4 | **Accuracy** | 679 | `sample.accuracyM > maxRecordingAccuracyM` (default 30m) | Reject |

### Spike rejection v2 (GPS mode only)

| # | Gate | Line | Sea cap | Land cap | Notes |
|---|------|------|---------|----------|-------|
| 5 | **Still-spike** | 671 | Speed<2kn + dist from lastGenuine > 150m → reject | Same | Anchored to speed-confirmed positions |
| 5b | **Bearing sanity** | 682 | Bearing >90° from median of last 5 → reject | Disabled | Sea only, window ≥ 3 entries |
| 6 | **Stale timeout** | 696 | 10s no accepted → relaxed caps (48/96 kn) + Fix C check | — | Gated on `!stopped` |
| 7 | **Gate 0** | 727 | Lock false→true → accept unconditionally | — | GPS recovery |
| 8 | **Gate 0.5** | 737 | GPS-reported speed > 40kn → reject | Disabled | Sea only |
| 9 | **Fix C** | 752 | Speed<2kn + dist from lastGenuine > 150m → reject | 150m | Anchored to genuine position |
| 10 | **Gate 1** | 773 | Implied speed > 32kn (sea) / 120kn (land) → reject | 120kn | Context-aware |
| 11 | **Gate 2** | 777 | Direction multiplier: aligned ×1.5, sideways ×0.5 | Disabled | Sea only, requires course history |
| 12 | **Gate 3** | 795 | Acceleration > 10kn/s (sea) / 30kn/s (land) → reject | 30kn/s | Last-to-current speed delta |
| 13 | **Same-ms** | 810 | Same timestamp + jump > 30m → reject | 30m | Out-of-order GPS timestamps |

### Land/sea auto-detection

- 5 consecutive rejections with GPS speed > 32kn → switch to LAND mode (relaxed caps)
- 10 consecutive accepted fixes ≤ 32kn → switch back to SEA mode

---

## Stage 5: captureAcceptedPoint → TrackPoint

**File:** [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt) (line 830)

Creates `TrackPoint` with proto fields:

| Field | Proto | Source |
|-------|-------|--------|
| `lat` | 1 | `sample.position.latitude` |
| `lon` | 2 | `sample.position.longitude` |
| `speedMps` | 3 | `sample.speedMps` |
| `bearingDeg` | 4 | `sample.bearingDeg` |
| `timeOffsetSec` | 5 | Seconds since recording start |
| `timeOffsetMs` | 15 | Milliseconds since recording start (monotonic) |
| `type` | 10 | `NORMAL` or `GAP` |
| `accuracyM` | 11 | `sample.accuracyM` **(new)** |

Updates internal trackers: `lastValidPoint*`, `lastAcceptedTimeMs`, `lastGenuine*` (when speed ≥ 2kn), `bearingWindow`, `courseHistory`.

---

## Stage 6: BoatMarker Lifecycle

**File:** [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt) (line 1088)

### Idle session flow

```
isStopped transitions false→true
    │
    ├─ IdlePeriodStarted event → MapScreen: addTempAutoMarker()
    │   ├─ Proximity dedup (dedupRadiusM, default 50m)
    │   │   ├─ Unconfirmed marker nearby → reuse it
    │   │   └─ Confirmed marker nearby → skip (return "")
    │   └─ New → create temp pin
    │
    ├─ startIdleTimer() → after idleThresholdSec (30s)
    │   ├─ BoatMarker merge check (NEW)
    │   │   ├─ Proximity scan: existing BoatMarker within dedupRadiusM?
    │   │   ├─ Cumulative track distance between markers < minTravelBetweenStopsM (25m)?
    │   │   └─ YES → reopen existing BoatMarker + union snapshots
    │   ├─ whereAmI callback → marker snapshots
    │   └─ Create BoatMarker (trigger=IDLE, startTimeMs, markers, boatLat/Lon)
    │
    └─ isStopped transitions true→false
        ├─ closeOpenBoatMarker() → sets endTimeMs on the open BoatMarker
        ├─ IdlePeriodCompleted event → MapScreen: confirmAutoMarker()
        │   └─ Min duration gate (minDurationSec, default 60s)
        └─ Track description rebuilt (recomputeDescription)
```

### BoatMarker merge constants

| Constant | Default | Location |
|----------|---------|----------|
| `dedupRadiusM` | 50m | `AppConfig.boatMarkerAutoMarkerDedupRadiusM` |
| `minTravelBetweenStopsM` | 25m | `AppConfig.boatMarkerMinTravelBetweenStopsM` |

### Auto-marker pin lifecycle

| Event | MapScreen action |
|-------|-----------------|
| `IdlePeriodStarted` | `addTempAutoMarker()` → temp 🕐 pin |
| `IdlePeriodCompleted` | `confirmAutoMarker()` → permanent pin (if duration ≥ minDurationSec) |
| Track finalize | Pins become `keepable = false` (cleaned on next startup unless manually saved) |

### Manual BoatMarkers

User taps boat-marker button → `addManualBoatMarker(snapshots)` → `BoatMarkerTrigger.MANUAL`. Not affected by merge logic (only `IDLE` trigger scanned).

---

## Stage 7: Track Description & Title

**File:** [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt) (line 1241)

### Description format

```
  - stopped at [Zone Name] @ HH:mm for Xmin
  - stopped @ HH:mm for Xmin
```

`formatStopLine(bm, zoneNames)` reads `bm.boatLat/Lon` → `whereAmI` → zone name. Reopened markers (endTimeMs=null during merge) omit duration; closed markers show accumulated duration.

### Title priority (3-tier)

1. 🤿 Diving pinned marker present → diving-related title
2. Manual BoatMarker → manual stop name
3. Longest IDLE BoatMarker → longest stop location

---

## Stage 8: GPX Export

**File:** [`GpxExporter.kt`](app/src/main/java/ykws/android/maro/data/track/GpxExporter.kt)

Per-point export:
```xml
<trkpt lat="43.5837" lon="7.0999">
  <speed>0.00</speed>
  <course>108.48</course>
  <time>2026-07-14T17:25:39.934Z</time>
</trkpt>
```

Track description embedded in `<cmt>` with BoatMarker stop lines. GAP markers rendered as separate `<trkseg>` elements with `<name>GAP</name>`.

---

## Constants Reference

| Constant | Value | Location |
|----------|-------|----------|
| `MIN_SPEED_MPS` | 0.5 | `GpsLocationSource.kt` |
| `MIN_SATELLITES_FOR_LOCK` | 4 | `GpsLocationSource.kt` |
| `ACCURACY_FLOOR_THRESHOLD_M` | 20.0 | `AdaptiveGpsPolicy` companion |
| `GPS_ACCURACY_GOOD_THRESHOLD_M` | 10 | `BuildConfig` (maro.properties) |
| `GPS_IDLE_MAX_INTERVAL_MS` | 10000 | `BuildConfig` (maro.properties) |
| `STOP_DETECTION_GPS_DORMANT_PCT` | 80 | `BuildConfig` (maro.properties) |
| `STALE_FIX_TIMEOUT_MS` | 10000 | `TrackRecorder.kt` |
| `MAX_STATIONARY_DRIFT_M` | 150.0 | `TrackRecorder.kt` |
| `MOVING_DEDUP_WINDOW_MS` | 500 | `TrackRecorder.kt` |
| `STATIONARY_DEDUP_WINDOW_MS` | 5000 | `TrackRecorder.kt` |
| `BOAT_MAX_SPEED_KN` | 32.0 | `TrackRecorder.kt` |
| `LAND_MAX_SPEED_KN` | 120.0 | `TrackRecorder.kt` |
| `dedupRadiusM` | 50 | `AppConfig.boatMarkerAutoMarkerDedupRadiusM` |
| `minTravelBetweenStopsM` | 25 | `AppConfig.boatMarkerMinTravelBetweenStopsM` |
| `maxRecordingAccuracyM` | 30 | `AppSettings` (default from maro.properties) |
| `idleThresholdSec` | 30 | `AppConfig.boatMarkerAutoMarkerIdleThresholdSec` |
| `minDurationSec` | 60 | `AppConfig.boatMarkerAutoMarkerMinDurationSec` |
