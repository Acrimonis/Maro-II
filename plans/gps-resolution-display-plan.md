# GPS Track Points — Resolution & Display Plan

> **Feature:** GPS | **Branch:** feature/ya-gps
> **Created:** 2026-07-18 | **Status:** Plan — Ask-reviewed, finalized
> **Ask Review:** 5 gaps found, 4 addressed below. Regression risk: medium (LAND_DETECTION_REJECTIONS 5→2 is highest-risk single change).

## Problem

Three issues observed during car-speed testing (applies to fast boats too):
1. Gaps between track points
2. Low point density / poor precision
3. Track polyline lags behind the boat marker

## Root Causes

| # | Cause | Mechanism |
|---|---|---|
| 1 | `gpsActiveIntervalSec=2s` + `flowOf(0L)` ticker | Hard ceiling of 0.5 Hz — 33m gaps at 60 km/h |
| 2 | `BOAT_MAX_SPEED_KN=32` + Gate 1 | Rejects >32 kn until land mode engages (5-point cost) |
| 3 | Gate 2 sideways multiplier (×0.5, 16 kn cap) | Drops every turn at speed — drift guard misfires on fast vessels |
| 4 | Accuracy gate flat 30m | Drops marginal fixes that are usable during movement |
| 5 | No display interpolation | Polyline frozen between accepted points — visual gap |

## Plan Items

### 1. GPS interval default 2s→1s + minDistance 5m→0m
**Files:** `SettingsManager.kt`
- Default `gpsActiveIntervalSec`: 2 → 1
- Default `gpsActiveMinDistanceM`: 5f → 0f

### 2. Boat/land speed caps → maro.properties
**Files:** `TrackRecorder.kt`, `maro.properties`, `build.gradle.kts`
- `tracking.boatMaxSpeedKn=32` (sea mode Gate 1 cap)
- `tracking.landMaxSpeedKn=90` (land mode Gate 1 cap, was hardcoded 120)
- `BuildConfig` fields via **`propDouble()`** (not `propInt` — avoids clamping + type narrowing at 7 call sites where `BOAT_MAX_SPEED_KN` is used as `Double`)
- Gate 1 `baseCap` reads from `BuildConfig` fields, selected by `isOnLand`

### 3. Land detection rejections 5→2
**Files:** `TrackRecorder.kt`
- `LAND_DETECTION_REJECTIONS`: 5 → 2
- Faster land-mode switch recovers 3 points per transition
- **Highest regression risk.** Mitigated by `SEA_RECOVERY_CONSECUTIVE=10` auto-correct.

### 4. Gate 2: speed-gate sideways multiplier
**Files:** `TrackRecorder.kt`
- When GPS speed ≥ `SIDEWAYS_SPEED_THRESHOLD_MPS` (5.0, ~10 kn): use neutral cap (×1.0)
- Below threshold: keep sideways multiplier (×0.5) — drift guard intact
- New constant: `SIDEWAYS_SPEED_THRESHOLD_MPS = 5.0` (hardcoded — same tier as `LOW_SPEED_MPS`, `MAX_STATIONARY_DRIFT_M`)

### 5. Accuracy gate: speed-aware threshold
**Files:** `TrackRecorder.kt`
- GPS speed ≥ `MIN_MOVEMENT_SPEED_FOR_ACCURACY_MPS` (~0.5 kn, moving): threshold = `maxRecordingAccuracyM * ACCURACY_MOVING_MULTIPLIER` (~50m)
- GPS speed < threshold (stationary): threshold = `maxRecordingAccuracyM` (30m)
- New constants: `ACCURACY_MOVING_MULTIPLIER = 1.7`, `MIN_MOVEMENT_SPEED_FOR_ACCURACY_MPS = 1.0 / 1.94384` (hardcoded — same tier)

### 6. Display-only interpolated trailing polyline
**Files:** `MapScreen.kt`
- Separate `Polyline` overlay (title `"track_trailing"`, distinct from `"track_recording"`)
- Semi-transparent solid (not dashed — avoid GAP marker confusion), e.g., `alpha = 0.4 * trackingColorActive`
- Source: `snapshotFlow { viewModel._displayPosition.value }` at ~20 Hz within a `LaunchedEffect` keyed on `trackRecorderState.state`
- On each `_displayPosition` change: remove previous trailing polyline, add new one from last accepted point to `_displayPosition`
- On each `newPointStream` emission (accepted point): remove trailing polyline, append point to recording polyline, create new trailing segment from new point to `_displayPosition`
- Cleanup: remove trailing polyline on recorder OFF (same `LaunchedEffect` that manages recording polyline lifecycle at line 1212)
- Flicker risk at 20 Hz: mitigated by single-Polyline replace (not add/remove/add), which osmdroid handles as a repaint

### 7. Build + verification
- `apk-build.bat`
- Car-speed testing: highway, turns, stops, acceleration
- **Missing gate-level tests** — Gate 1/2, accuracy gate, land detection have zero coverage. Not blocking implementation but noted for follow-up.

## Constants

| Constant | Value | Where | Backing |
|---|---|---|---|
| `SIDEWAYS_SPEED_THRESHOLD_MPS` | 5.0 (~10 kn) | TrackRecorder.kt | Hardcoded (same tier as `LOW_SPEED_MPS`) |
| `ACCURACY_MOVING_MULTIPLIER` | 1.7 | TrackRecorder.kt | Hardcoded |
| `MIN_MOVEMENT_SPEED_FOR_ACCURACY_MPS` | 1.0 / 1.94384 | TrackRecorder.kt | Hardcoded |
| `tracking.boatMaxSpeedKn` | 32 | maro.properties → BuildConfig | `propDouble()` |
| `tracking.landMaxSpeedKn` | 90 | maro.properties → BuildConfig | `propDouble()` |

**Rationale for hardcoded vs. property-backed:** Boat/land speed caps are user-facing tuning knobs (different boats, regions). Gate 2 threshold, accuracy multiplier, and movement threshold are internal algorithm constants — exposing them as properties would be premature configuration without evidence users need to tune them.

## Ask Review Findings

| # | Finding | Disposition |
|---|---|---|
| 1 | `propInt()` clamping at 0-100 would silently cap landMaxSpeedKn >100 | → Use `propDouble()`, no clamping |
| 2 | Type narrowing `Double→Int` forces `.toDouble()` at 7 call sites | → `propDouble()` fixes both 1 and 2 |
| 3 | Zero gate-level test coverage | → Noted for follow-up, not blocking |
| 4 | MapScreen trailing polyline lifecycle underspecified | → Item 6 expanded with overlay lifecycle, flicker mitigation |
| 5 | Inconsistent property backing | → Rationalized: user-facing caps → properties; algorithm constants → hardcoded |
