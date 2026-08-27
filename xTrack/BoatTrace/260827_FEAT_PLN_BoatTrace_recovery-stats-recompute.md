# 260827 — Recovery stats recompute (Fix 1) + checkpoint hardening

- **Feature:** BoatTrace
- **Status:** planned — awaiting go-ahead
- **Branch:** feature/persist-track-tweak
- **Date:** 2026-08-27

## Problem

Recovered-from-checkpoint tracks show correct end/Total but stale stats:
`nav 4h42m41s`, `avg 0.0 kn`, `dist 0.0 nm`, `idle 0m0s`.

Root cause: `currentTrack` (the checkpoint) carries `trackPoints` + `fastestSpeedMps` + `boatMarkers`
only. `distanceNm`, `averageSpeedMps`, `idleDurationSec` live in recorder accumulators and are written
only in [`finalizeTrack()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:1117).
The old recovery path stamped wall-clock `endTimeMs`/nav and copied the zeros.

## Fix 1 — recompute stats from points (minimal, targeted)

### Step 1 — shared pure function
New `app/src/main/java/ykws/android/maro/data/track/TrackStats.kt`:

- `object TrackStats { fun deriveFromPoints(track: Track): DerivedStats }` where
  `DerivedStats(distanceNm, averageSpeedMps, idleDurationSec, navigatingDurationSec)`.
- Move the idle classifier out of the recorder's private
  [`computeTimelineIdleSec()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:639)
  and the duplicated constants in [`TrackMerger.kt`](app/src/main/java/ykws/android/maro/data/track/TrackMerger.kt:7)
  into this object. Filter `type != PointType.GAP` first.

Formulas (over non-GAP consecutive pairs):
- `distanceNm` = Σ `SpatialOperations.haversine(p[i-1], p[i]) / 1852`.
- `averageSpeedMps` = mean of non-null `p.speedMps`.
- `idleDurationSec` = for each pair `dt > 0`, if `dM/dt < 0.5` m/s and `dM < 500` m → add `dt`.
- `navigatingDurationSec` = `((lastPointTimeMs − startTimeMs)/1000 − idleDurationSec).coerceAtLeast(0)`.

### Step 2 — recovery path
In [`finalizeOrphanedCheckpoint()`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:124):
replace stale stats with `TrackStats.deriveFromPoints(track)` before saving.

### Step 3 — backfill migration
Extend [`backfillLastPointTimeMs()`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:140)
→ `migrateTrackStats()`: for finalized tracks with `distanceNm == 0 && averageSpeedMps == 0 && trackPoints.size >= 2`,
recompute stats + `lastPointTimeMs`. Bump `TRACK_SCHEMA_VERSION` 1 → 2 in
[`TrackViewModel`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt).

### Step 4 — tests
Unit tests for `TrackStats.deriveFromPoints` with synthetic points (moving + idle + GAP cases),
plus a recovery test asserting stats are recomputed.

### Step 5 — build
`gradlew assembleDebug`.

## Hardening option (discussed, optional)

Persist `distanceNm` / `averageSpeedMps` / `idleDurationSec` onto `currentTrack` during recording so the
checkpoint itself is lossless. Details in chat discussion. If adopted, keep `deriveFromPoints` as the
reconciliation guard (it already is the `reconciledIdleSec` source).

## Key files
- [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:639)
- [`TrackRepository.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:124)
- [`TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt)
- [`TrackMerger.kt`](app/src/main/java/ykws/android/maro/data/track/TrackMerger.kt:7)
