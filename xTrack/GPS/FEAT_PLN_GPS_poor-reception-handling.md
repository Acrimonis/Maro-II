# GPS Poor-Reception Handling — Comprehensive Plan

> **Feature:** GPS | **Subfeature:** poor-reception
> **Created:** 2026-07-17 09:11 UTC | **Updated:** 2026-07-17 10:40 UTC (finalized)
> **Status:** Plan — two Ask reviews, zero known issues
> **Branch:** feature/gps-still-spikes
> **Scope:** 9 items (8 P0 on-the-fly + 1 P1 post-hoc) across 7 source files

---

## Problem Summary

Two distinct poor-reception patterns from Port de La Salis GPX files:

| Pattern | Symptom | Root Cause |
|---------|---------|------------|
| **Drift spikes** (July 14) | 200m–1km position jumps, speed=0 | GPS triangulation noise, addressed by still-spike fix |
| **Duplicate-stuck** (July 15) | Identical positions repeated, course oscillating wildly, speed=0 | GPS reports same position; course derived from noisy doppler |

The **idle cadence** (36s between fixes when IDLE) compounds both: sparse data during IDLE means a single bad fix can flip the adaptive policy back to ACTIVE, which then records garbage at 1s intervals.

---

## Code Review Findings

### Verified against current source

All line references from [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt), [`NavigationViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt), [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt), [`AdaptiveGpsPolicy.kt`](app/src/main/java/ykws/android/maro/data/location/AdaptiveGpsPolicy.kt), [`GpsLocationSource.kt`](app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt) as of 2026-07-17.

### Existing addPoint() flow (line 585–827)

```
1. Resume gap detection (line 589)
2. Fix D dedup — 500ms window, exact lat/lon match (line 599–610)
3. isStopped check (line 612)
4. Idle duration + BoatMarker session (line 618–656)
5. if (stopped) return — skip when stationary (line 663)
6. if (sample.speedMps == null) return — skip w/o speed (line 667)
7. Spike rejection v2 — GPS mode only (lines 669–823):
   a. Still-spike gate (speed<2kn, dist from lastGenuine>150m, bearing sanity) — line 671–694
   b. Stale timeout (10s, relaxed caps) — line 696–725
   c. Gate 0: GPS recovery — line 727–734
   d. Gate 0.5: GPS speed cap 40kn — line 736–746
   e. Fix C: stationary drift anchored to lastGenuine — line 754–768
   f. Gates 1–3: implied speed, direction, acceleration — line 770–821
8. captureAcceptedPoint (line 826)
```

### Insertion points

- **Accuracy gate (item 5):** after line 667 (`speedMps == null`), before line 669 (`if (gpsMode)`)
- **Extended dedup (item 6):** modify existing logic at lines 599–607
- **WEAK icon (item 7):** `GpsIconState` private enum at [`MapScreen.kt:3266`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3266), `GpsStatusIcon` private composable at line 3269

### Design issue identified: Policy/Recording layer separation

The adaptive policy runs in NavigationViewModel **before** TrackSample reaches TrackRecorder. Even with the accuracy recording gate (item 5), bad fixes still influence the policy's ACTIVE/IDLE decision. **Accuracy-aware thresholds (item 3.2) promoted to P0** to close this gap.

### IDLE floor mechanism detail

Adding `_accuracyIsPoor: MutableStateFlow<Boolean>` to NavigationViewModel, updated per-fix in the `onEach` block. Uses `distinctUntilChanged()` in the `gpsParams` combine — re-subscription only triggers on good↔poor transitions, avoiding unnecessary `removeUpdates`+`requestLocationUpdates` churn.

### Extended dedup mechanism detail

Modifies existing Fix D at lines 599–607. Current: static `DEDUP_WINDOW_MS = 500`. Change: dynamic — 500ms when moving, 5000ms when `speed < MIN_MOVEMENT_SPEED_MPS`. `lastAcceptedTimeWallMs` (line 610) only updates on accepted points, so dedup-rejected points leave the clock ticking — next non-identical point always passes.

---

## Architecture Overview

```
GpsLocationSource.emitFix()
    │  loc.accuracy → GpsFix.accuracyM  ← NEW
    ▼
NavigationViewModel.onEach { fix ->
    _gpsAccuracy.value = fix.accuracyM   ← NEW (for WEAK icon)
    _accuracyIsPoor.value = ...          ← NEW (for IDLE floor)
    adaptivePolicy.onFix(now, pos, window, threshold, fix.speedMps)  ← MODIFIED
}
    │
    ▼
MapScreen: TrackSample combine
    │  accuracyM from fix → TrackSample  ← NEW
    ▼
TrackRecorder.addPoint(sample)
    │  ┌─ Fix D dedup (dynamic window)  ← MODIFIED
    │  ├─ isStopped check
    │  ├─ speedMps null check
    │  ├─ ACCURACY GATE (new)           ← NEW
    │  └─ Spike rejection v2 (existing)
    ▼
captureAcceptedPoint(sample)
    │  TrackPoint(..., accuracyM)        ← NEW
    ▼
TrackPoint proto field 11               ← NEW
```

---

## P0 Items — Implementation Order

### 1. Data plumbing: `accuracyM` field (3 files)

**Files:**
- [`GpsFix`](app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt:29) — add `val accuracyM: Float? = null`
- [`TrackSample`](app/src/main/java/ykws/android/maro/data/track/TrackSample.kt:13) — add `val accuracyM: Float? = null`
- [`TrackPoint`](app/src/main/java/ykws/android/maro/data/track/TrackPoint.kt:22) — add `@ProtoNumber(11) val accuracyM: Float? = null`
- [`captureAcceptedPoint()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:838) — pass `accuracyM = sample.accuracyM` to `TrackPoint` constructor

**Backward compat:** New nullable field with default null. Proto field 11 (next available). Old tracks deserialize fine.

### 2. Capture `Location.getAccuracy()` (1 file, 1 line)

**File:** [`GpsLocationSource.emitFix()`](app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt:72)

```kotlin
accuracyM = if (loc.hasAccuracy()) loc.accuracy else null
```

Typical values: 3–5m (excellent outdoor), 10–20m (urban), 30–65m (indoor). Free from Android — currently discarded.

### 3. Speed-aware AdaptiveGpsPolicy (1 file + 2 call sites)

**File:** [`AdaptiveGpsPolicy.onFix()`](app/src/main/java/ykws/android/maro/data/location/AdaptiveGpsPolicy.kt:37)

Add `speedMps: Float? = null` parameter. When displacement ≥ thresholdM AND speed is available AND speed < `MIN_MOVEMENT_SPEED_MPS` (0.5 m/s ≈ 1 kn), do NOT immediately return ACTIVE. Fall through to the window-timer check instead.

```kotlin
if (SpatialOperations.haversine(anchorPos!!, pos) >= thresholdM) {
    // Speed tiebreaker: if GPS speed says stationary, don't trust position jump.
    // BUT re-anchor to prevent slow genuine drift from locking into permanent IDLE.
    if (speedMps != null && speedMps < MIN_MOVEMENT_SPEED_MPS) {
        anchorPos = pos
        anchorMs = nowMs
        val result = if (nowMs - anchorMs >= windowMs) IDLE else ACTIVE
        lastMode = result
        return result  // window timer restarted from new anchor
    }
    anchorPos = pos
    anchorMs = nowMs
    lastMode = ACTIVE
    return ACTIVE
}
```

**Why re-anchor:** Without it, a boat drifting at 0.3 kn hits the tiebreaker on every fix, anchor never updates, and after `windowMs` the policy permanently returns IDLE — even 500m later. With re-anchor, each fix resets the window from the new drifted position, so the timer never expires while the boat keeps moving. Validated by Ask-mode code review.

**Call sites:**
- [`NavigationViewModel.kt:737`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt:737) — pass `fix.speedMps`
- [`NavigationViewModel.kt:1202`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt:1202) — pass `null` (demo path, no speed data)

**Feature loss risk:** Boat drifting <1kn (tide) could be delayed up to `stopDetectionTimeSec` (45s) before detecting movement. Acceptable — subsequent fixes with consistent displacement will trigger ACTIVE via the window timer.

**Regression risk:** Demo mode passes `null` → speed tiebreaker never activates → no behavior change.

### 4. Accuracy-aware thresholds in policy (1 file, same as item 3)

**File:** [`AdaptiveGpsPolicy.onFix()`](app/src/main/java/ykws/android/maro/data/location/AdaptiveGpsPolicy.kt:37)

Add `accuracyM: Float? = null` parameter alongside `speedMps`. When accuracy is known and poor (>20m), use accuracy as floor for the movement threshold:

```kotlin
val effectiveThresholdM = if (accuracyM != null && accuracyM > ACCURACY_FLOOR_THRESHOLD_M) {
    maxOf(thresholdM, accuracyM.toDouble())
} else {
    thresholdM
}
```

This prevents declaring "movement" for position deltas within the GPS error margin.

**Note:** The NavigationViewModel onEach block already has `fix.position` and `fix.speedMps` — adding `fix.accuracyM` to the call is trivial.

### 5. IDLE minimum fix-rate floor (1 file + maro.properties + new StateFlow)

**File:** [`NavigationViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt)

**New StateFlow:**
```kotlin
private val _accuracyIsPoor = MutableStateFlow(false)
val accuracyIsPoor: StateFlow<Boolean> = _accuracyIsPoor.asStateFlow()
```

Updated in `onEach` block (after line 744):
```kotlin
_accuracyIsPoor.value = fix.accuracyM != null && fix.accuracyM > BuildConfig.GPS_ACCURACY_GOOD_THRESHOLD_M
```

**Modified gpsParams combine** (line 700–720): Add `_accuracyIsPoor.distinctUntilChanged()` as 5th input:

```kotlin
combine(
    enabled,
    settings.distinctUntilChangedBy { ... },
    _acquisitionMode,
    _accuracyIsPoor.distinctUntilChanged(),  // NEW — only emits on transitions
    _forceReconnect
) { on, s, mode, accuracyPoor, forceReconnect ->
    val intervalMs = when {
        forceReconnect -> 0L
        mode == AcquisitionMode.IDLE && s.stopDetectionDelayGps -> {
            val dormant = s.stopDetectionTimeSec * 1000L * BuildConfig.STOP_DETECTION_GPS_DORMANT_PCT / 100
            if (accuracyPoor) minOf(dormant, BuildConfig.GPS_IDLE_MAX_INTERVAL_MS) else dormant
        }
        else -> s.gpsActiveIntervalSec * 1_000L
    }
    // distance unchanged
}
```

**maro.properties — new GPS section:**
```properties
# ── GPS position processing ─────────────────────────
# Maximum interval (ms) between GPS fixes when the adaptive policy
# declares the boat stationary (IDLE) and accuracy is poor (>goodThresholdM).
# Prevents the default 36s dormant interval from starving the policy of
# data during marginal reception. Only activates when accuracy is known-poor.
gps.idle.maxIntervalMs=10000

# Accuracy threshold (metres) below which GPS reception is considered
# "good". Above this value the IDLE fix-rate floor activates, the WEAK
# GPS icon state displays, and adaptive policy displacement thresholds
# are widened to the accuracy value (see ACCURACY_FLOOR_THRESHOLD_M).
gps.accuracy.goodThresholdM=10
```

**Battery:** 36s→10s costs ~0.01% battery/hour. Only activates when accuracy is known-poor AND IDLE. Good reception preserves full 36s.

### 6. Accuracy threshold recording gate (1 file + settings)

**File:** [`TrackRecorder.addPoint()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt), insert after line 667 (speedMps null check), before line 669 (spike rejection):

```kotlin
// ── Accuracy gate: reject fixes with poor self-reported accuracy ──
if (sample.accuracyM != null && sample.accuracyM > maxRecordingAccuracyM) {
    Log.v(TAG, "Accuracy gate: rejected fix with accuracy=${"%.1f".format(sample.accuracyM)}m > ${maxRecordingAccuracyM}m")
    return
}
```

**New setting:** `tracking.maxRecordingAccuracyM: Float = 30f` in `AppSettings` + `SettingsManager`.

**maro.properties:**
```properties
# Maximum GPS self-reported accuracy (metres) for a fix to be recorded.
# Fixes with accuracy worse than this are rejected. Prevents ±45m indoor
# fixes from polluting the track. Default 30m. Configurable in Settings.
tracking.maxRecordingAccuracyM=30
```

**Wiring path:** `SettingsManager` → `AppSettings.maxRecordingAccuracyM` → `TrackViewModel` constructor (reads from `settings` StateFlow, same pattern as `gpsMode`) → `TrackRecorder` constructor parameter. TrackRecorder already receives `gpsMode: Boolean` this way — `maxRecordingAccuracyM` follows the identical pattern.

**Feature loss risk:** If ALL fixes exceed threshold, track goes silent. This is correct — no data is better than bad data. The WEAK icon (item 7) tells the user why. Threshold is configurable.

### 7. Extended dedup for stuck-GPS (1 file, modify existing)

**File:** [`TrackRecorder.addPoint()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:599–607)

Change static `DEDUP_WINDOW_MS` to dynamic:

```kotlin
val dedupWindowMs = if (sample.speedMps != null && sample.speedMps < MIN_MOVEMENT_SPEED_MPS) {
    STATIONARY_DEDUP_WINDOW_MS  // 5_000L
} else {
    MOVING_DEDUP_WINDOW_MS      // 500L (existing DEDUP_WINDOW_MS)
}
```

No precision loss — only byte-for-byte identical lat/lon are collapsed. The `lastAcceptedTimeWallMs` field at line 610 is only set on accepted points, so dedup-rejected points don't reset the clock.

### 8. GPS WEAK icon state (1 file)

**File:** [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)

**New StateFlow** in NavigationViewModel:
```kotlin
private val _gpsAccuracy = MutableStateFlow<Float?>(null)
val gpsAccuracy: StateFlow<Float?> = _gpsAccuracy.asStateFlow()
```

Set in `onEach` block: `_gpsAccuracy.value = fix.accuracyM`

**Modified `GpsIconState` enum** (line 3266): Add `WEAK`

**Modified `gpsIconState` derivation** (line 355–363):
```kotlin
val gpsIconState = remember(appSettings.gpsMode, gpsPosition, gpsStale, acquisitionMode, isEstimating, gpsAccuracy) {
    when {
        !appSettings.gpsMode -> GpsIconState.DEMO
        gpsPosition == null && !isEstimating -> GpsIconState.ACQUIRING
        isEstimating -> GpsIconState.ESTIMATING
        gpsStale -> GpsIconState.STALE
        gpsAccuracy != null && gpsAccuracy > GPS_ACCURACY_GOOD_THRESHOLD_M -> GpsIconState.WEAK  // NEW (same constant as IDLE floor)
        acquisitionMode == AcquisitionMode.IDLE -> GpsIconState.IDLE
        else -> GpsIconState.HEALTHY
    }
}
```
`gpsAccuracy` added to `remember` keys (validated by Ask review).

**Modified `GpsStatusIcon` composable** (line 3269): Add WEAK branch — amber background (reuse `AppConfig.statusGpsAcquiring` color `#FFA726`), same alpha as HEALTHY.

**New AppConfig:** `statusGpsWeak: Int` — maybe same as acquiring, or a distinct amber.

---

## P1 Items — Post-Hoc Cleanup

### 9. Merge duplicate BoatMarkers — reopen instead of re-create (1 file)

**File:** [`TrackRecorder.startIdleTimer()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:1088)

When a new idle period starts at the same location as a previous BoatMarker within `dedupRadiusM`, check whether the boat actually traveled between them. If cumulative track point distance between the two markers is below `MIN_TRAVEL_BETWEEN_STOPS_M` (no real movement), **reopen the existing BoatMarker** instead of creating a new one. This accumulates idle duration correctly.

If there WAS real travel (cumulative distance ≥ threshold), create a new BoatMarker — the boat genuinely left and returned.

```kotlin
val dedupRadiusM = AppConfig.boatMarkerAutoMarkerDedupRadiusM
val nowMs = System.currentTimeMillis()
val existingNearby = track.boatMarkers.findLast { bm ->
    bm.trigger == BoatMarkerTrigger.IDLE &&
    SpatialOperations.haversine(LatLng(lat, lon), LatLng(bm.boatLat, bm.boatLon)) <= dedupRadiusM
}
if (existingNearby != null) {
    // Was there real travel between the two idle periods?
    val trackStartMs = currentTrack!!.startTimeMs
    val pointsBetween = track.trackPoints.filter { pt ->
        val absTimeMs = trackStartMs + pt.timeOffsetMs
        absTimeMs > existingNearby.startTimeMs && absTimeMs < nowMs
    }
    var cumulativeDist = 0.0
    for (i in 1 until pointsBetween.size) {
        cumulativeDist += SpatialOperations.haversine(
            LatLng(pointsBetween[i-1].lat, pointsBetween[i-1].lon),
            LatLng(pointsBetween[i].lat, pointsBetween[i].lon)
        )
    }
    if (cumulativeDist < MIN_TRAVEL_BETWEEN_STOPS_M) {
        // No real travel — GPS noise → merge by reopening
        val idx = track.boatMarkers.indexOf(existingNearby)
        val reopened = existingNearby.copy(endTimeMs = null)
        currentTrack = track.copy(
            boatMarkers = track.boatMarkers.toMutableList().also { it[idx] = reopened }
        )
        session.boatMarkerIndex = idx
        session.boatMarkerMerged = true
        return@launch
    }
    // else: real travel → fall through, create new BoatMarker
}
```

**New config:** `track.boatMarker.autoMarker.minTravelBetweenStopsM=25` in `maro.properties` + `AppConfig.boatMarkerMinTravelBetweenStopsM: Double`.

**Snapshot merging:** Union old and new `whereAmI` snapshots by deduplicating on `MarkerSnapshot.markerId`, keeping newer entries when both exist. Ensures the merged BoatMarker reflects marker changes across both idle periods.

```kotlin
val mergedMarkers = unionMarkerSnapshots(existingNearby.markers, result.entries)
// unionMarkerSnapshots: LinkedHashMap by markerId, new overwrites old
val reopened = existingNearby.copy(endTimeMs = null, markers = mergedMarkers)
```

**Why cumulative distance replaces the time gate:** The track data already proves whether movement was real. If the boat sailed 500m and returned → track points show displacement → new marker. If GPS noise caused ACTIVE↔IDLE oscillation → track points show near-zero displacement → merge. No arbitrary time window needed.

**Edge cases resolved:**
- Boat returns to same mooring after real trip → cumulative distance >> 25m → new marker ✅
- Poor reception oscillation at same spot → cumulative distance ≈ 0 → merge ✅
- Boat at anchor, genuine 20m wind swing, re-settles → cumulative distance < 25m → merge (correct — same stop)
- First idle too short to create BoatMarker → no existing marker → new marker ✅

**New maro.properties entry** (under existing `# ── Auto-marker idle tracking ──` section):
```properties
# Minimum cumulative track point distance (metres) between two idle periods
# at the same location required to consider them separate stops.
# Below this threshold, the BoatMarker is reopened (merged) instead of
# creating a new one — handles poor-reception ACTIVE↔IDLE churn.
track.boatMarker.autoMarker.minTravelBetweenStopsM=25
```

---

## P2-P3 Items (summarized)

| Pri | Item | Files | Notes |
|-----|------|-------|-------|
| P1 | GNSS C/N0 monitoring | `GpsLocationSource.kt` | Extend existing `GnssStatus.Callback` at line 106 |
| P1 | Course stability signal | `TrackRecorder.kt` | Leverage existing `bearingWindow` at line 200 |
| P1 | Course-stability recording gate | `TrackRecorder.kt` | Erratic bearings at speed=0 → suppress |
| P1 | GPX export accuracy | GPX exporter | `<accuracy>` element per trkpt |
| P2 | Dashboard accuracy readout | `DashboardPanel.kt` | "±12m" below speed tile |
| P2 | Track list quality marker | Track list UI | Per-track quality score |
| P3 | Map low-quality rendering | `MapScreen.kt` | Dashed polyline for poor-accuracy segments |

---

## Feature Loss / Regression Analysis

| Scenario | Risk | Mitigation |
|----------|------|------------|
| Boat drifting <1kn (tide) | Delayed movement detection (up to 45s) | Speed tiebreaker only suppresses single-bad-fix flips; consistent displacement triggers ACTIVE via window timer |
| All fixes above accuracy threshold | Empty track recording | WEAK icon tells user why; threshold is configurable; no data > bad data |
| Demo mode | Speed tiebreaker could suppress demo recording | Demo passes `speedMps=null` → tiebreaker never activates |
| Good reception outdoors | New gates could reject valid points | All gates self-bypass when accuracy < 10m AND speed > 1kn |
| Proto backward compat | Old tracks break on deserialization | New field 11 is nullable with default null |
| GPS re-subscription churn | IDLE floor triggers unnecessary reconnects | `distinctUntilChanged()` on `_accuracyIsPoor` — only fires on good↔poor transitions |

---

## Constants & Config Reference

| Constant | Value | Location | Note |
|----------|-------|----------|------|
| `MIN_MOVEMENT_SPEED_MPS` | 0.5f | [`GpsLocationSource.MIN_SPEED_MPS`](app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt:169) | Already exists — reuse |
| `ACCURACY_FLOOR_THRESHOLD_M` | 20.0 | `AdaptiveGpsPolicy` companion | New — item 4 |
| `GPS_ACCURACY_GOOD_THRESHOLD_M` | 10 | BuildConfig (from maro.properties) | New — items 5, 8 |
| `GPS_IDLE_MAX_INTERVAL_MS` | 10000 | BuildConfig (from maro.properties) | New — item 5 |
| `MAX_STATIONARY_DRIFT_M` | 150.0 | [`TrackRecorder`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt) | Already exists |
| `MIN_TRAVEL_BETWEEN_STOPS_M` | 25.0 | `AppConfig.boatMarkerMinTravelBetweenStopsM` | New — item 9 |

**Wiring note:** `GPS_IDLE_MAX_INTERVAL_MS` and `GPS_ACCURACY_GOOD_THRESHOLD_M` are `BuildConfig` constants. Add `buildConfigField` entries in [`build.gradle.kts`](app/build.gradle.kts) using `propInt(...)` pattern — same as existing `STOP_DETECTION_GPS_DORMANT_PCT`.

**New field on `IdleSessionContext`:** Add `var boatMarkerMerged: Boolean = false` for item 9.

**`tracking.maxRecordingAccuracyM`** is a user-facing setting persisted by `SettingsManager` (in `AppSettings`). The `maro.properties` entry provides the default value (30f), not the canonical storage.

---

## Key Files

| File | Changes |
|------|---------|
| [`GpsLocationSource.kt`](app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt) | `GpsFix.accuracyM`, `emitFix()` captures `loc.accuracy` |
| [`AdaptiveGpsPolicy.kt`](app/src/main/java/ykws/android/maro/data/location/AdaptiveGpsPolicy.kt) | `onFix()` adds `speedMps`, `accuracyM` params |
| [`TrackSample.kt`](app/src/main/java/ykws/android/maro/data/track/TrackSample.kt) | Add `accuracyM` field |
| [`TrackPoint.kt`](app/src/main/java/ykws/android/maro/data/track/TrackPoint.kt) | Add `@ProtoNumber(11) accuracyM` |
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt) | Accuracy gate, extended dedup, `captureAcceptedPoint` passes accuracyM |
| [`NavigationViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt) | `_accuracyIsPoor`, `_gpsAccuracy` StateFlows, IDLE floor, speed+accuracy pass-through to policy |
| [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | `GpsIconState.WEAK`, `GpsStatusIcon` WEAK branch, TrackSample wiring |
| [`SettingsManager.kt`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt) | `maxRecordingAccuracyM` field |
| [`maro.properties`](app/src/main/assets/maro.properties) | `gps.idle.maxIntervalMs`, `gps.accuracy.goodThresholdM`, `tracking.maxRecordingAccuracyM` |

---

## Rules

- Accuracy metadata is **recording-only** — must not affect map display (position, heading, dead reckoning).
- IDLE cadence changes must not regress battery savings when reception is good (accuracy < 10m).
- Speed-aware policy changes must not prevent genuine slow movement detection (drifting at <1kn with tide).
- All new accuracy thresholds must be configurable via `maro.properties` with sensible defaults.
- Proto field additions must be backward-compatible (new fields with defaults, no field number reuse).
