# 260827 — Recovery stats recompute (Fix 1) + checkpoint hardening

- **Feature:** BoatTrace
- **Status:** implemented — build passing
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

- Expose an ergonomic extension `fun Track.withDerivedStats(): Track` that returns
  `copy(distanceNm=…, averageSpeedMps=…, idleDurationSec=…, navigatingDurationSec=…, lastPointTimeMs=…)`.
- Move the idle classifier out of the recorder's private
  [`computeTimelineIdleSec()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:639)
  and the duplicated constants in [`TrackMerger.kt`](app/src/main/java/ykws/android/maro/data/track/TrackMerger.kt:7)
  into this file. Filter `type != PointType.GAP` first.

Formulas (over non-GAP consecutive pairs):
- `distanceNm` = Σ `SpatialOperations.haversine(p[i-1], p[i]) / 1852`.
- `averageSpeedMps` = mean of non-null `p.speedMps`.
- `idleDurationSec` = for each pair `dt > 0`, if `dM/dt < 0.5` m/s and `dM < 500` m → add `dt`.
- `navigatingDurationSec` = `((lastPointTimeMs − startTimeMs)/1000 − idleDurationSec).coerceAtLeast(0)`.

Keep [`finalizeTrack()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:1059) unchanged — its
accumulators include the finalize-time idle flush (idle after the last point), which a points-only recompute cannot see.
`withDerivedStats()` is for recovery + migration only.

### Step 2 — recovery path
In [`finalizeOrphanedCheckpoint()`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:124):
replace stale stats with `TrackStats.deriveFromPoints(track)` before saving.

### Step 3 — backfill migration
Extend [`backfillLastPointTimeMs()`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:140)
→ `migrateTrackStats()`. Detection gate (recovered-checkpoint signature, excludes healthy/stationary tracks):

```
endTimeMs != null
&& distanceNm == 0f && averageSpeedMps == 0f && idleDurationSec == 0L
&& fastestSpeedMps > 0f
&& trackPoints.size >= 2
```

For matches: `track.withDerivedStats()` (recompute stats + `lastPointTimeMs`), re-save atomically, rebuild index.
Bump `TRACK_SCHEMA_VERSION` 1 → 2 in [`TrackViewModel`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt).
Idempotent — a second run finds no further matches.

### Step 4 — tests
Unit tests for `TrackStats.deriveFromPoints` with synthetic points (moving + idle + GAP cases),
plus a recovery test asserting stats are recomputed.

### Step 5 — build
`gradlew assembleDebug`.

## Hardening option (discussed, optional)

Persist `distanceNm` / `averageSpeedMps` / `idleDurationSec` onto `currentTrack` during recording so the
checkpoint itself is lossless. Details in chat discussion. If adopted, keep `deriveFromPoints` as the
reconciliation guard (it already is the `reconciledIdleSec` source).

## Review notes (Fix 1)

- **Detection precision** — `fastestSpeedMps > 0` is the discriminator: a recovered trip always has a recorded
  max speed (e.g. 28.7 kn) while a genuinely stationary track's max is ~0, so the gate never rewrites a healthy
  zero-distance track. `distance == 0 && avg == 0 && idle == 0` pins it to the checkpoint-zero signature.
- **Distance epsilon** — [`captureAcceptedPoint()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:951)
  accumulates with [`TrackGeofenceChecker.distanceM`](app/src/main/java/ykws/android/maro/data/track/TrackGeofenceChecker.kt:18)
  while the recompute uses `SpatialOperations.haversine`. Both are haversine; the epsilon is irrelevant because the
  gate only touches tracks whose distance is currently 0.
- **Idle tail** — `withDerivedStats()` derives idle only up to the last point. That is correct for recovery (the last
  point is the end of data) but would be wrong for the normal finalize path (which must count the post-point idle flush).
  Hence finalize is left untouched.
- **GAP handling** — skip GAP markers in both distance and idle so a seam is never counted as a huge idle interval.
- **Degenerate tracks** — `< 2` non-GAP points → zeros; `nav` coerced ≥ 0.
- **Proto compat** — no schema change to `Track`/`TrackSummary`; only the migration version bumps.

## Key files
- [`TrackStats.kt`](app/src/main/java/ykws/android/maro/data/track/TrackStats.kt)
- [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:639)
- [`TrackRepository.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:124)
- [`TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt)
- [`TrackMerger.kt`](app/src/main/java/ykws/android/maro/data/track/TrackMerger.kt:7)

## Implemented

- New [`TrackStats.kt`](app/src/main/java/ykws/android/maro/data/track/TrackStats.kt) — shared `IDLE_MAX_SPEED_MPS`/`IDLE_MAX_DRIFT_M`,
  `timelineIdleSec()`, and `Track.withDerivedStats()` (distance, avg speed, idle, nav, lastPointTimeMs from points).
- [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt) — removed private
  `computeTimelineIdleSec` + idle constants; finalize calls shared `timelineIdleSec`.
- [`TrackMerger.kt`](app/src/main/java/ykws/android/maro/data/track/TrackMerger.kt) — removed duplicated idle constants.
- [`TrackRepository.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt) —
  `finalizeOrphanedCheckpoint()` uses `withDerivedStats()`; `backfillLastPointTimeMs()` → `migrateTrackStats()`
  with the recovered-checkpoint gate (`distance==0 && avg==0 && idle==0 && fastest>0 && points>=2`).
- [`TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt) — calls `migrateTrackStats()`,
  `TRACK_SCHEMA_VERSION` 1 → 2.

Build: `gradlew assembleDebug` — **BUILD SUCCESSFUL**.
