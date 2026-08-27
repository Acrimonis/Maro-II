# 260827 — Idle under-count fix (raw vs simplified points)

- **Feature:** BoatTrace
- **Status:** implemented — build passing
- **Branch:** feature/persist-track-tweak
- **Date:** 2026-08-27

## Problem

🤿Le Village Englouti + Feu Artifice (2026-08-13) dashboard shows `nav 3h43m56s / idle 51m22s`,
but the points show two long stops — Village dive **2h41.9m** (18:40:17→21:22:09 UTC) and fireworks
**46.6m** (21:32:42→22:19:19 UTC) — i.e. **idle ≈ 3h28m57s, nav ≈ 1h04m20s**.

## Root cause

At finalize, idle is reconciled with `timelineIdleSec()` over **RAW** points (dense, 1–2 s, GPS jitter).
Per-fix jitter makes `dM/dt` exceed 0.5 m/s, so one long stop fragments into small "moving" pieces and only
~51 m of idle survives. On the **SIMPLIFIED** points (what is stored/exported) jitter averages out and the
same classifier sees the long stop. `migrateTrackStats()` (Fix 1) only repairs recovered-checkpoint tracks
(`distance == 0 && avg == 0`), so normally-finalized tracks keep their under-counted idle.

## Change 1 — finalize reconciles on simplified points (forward fix)

In [`finalizeTrack()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:1059):

- `reconciledIdleSec = maxOf(idleDurationSec, timelineIdleSec(simplifiedPoints)).coerceAtMost(totalElapsedSec)`
  (currently uses raw `track.trackPoints`).
- Keep `maxOf` with the live accumulator so the post-last-point idle flush is never lost.

## Change 2 — migration 2 repairs existing tracks (schema 2 → 3)

Extend [`migrateTrackStats()`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:140)
with a monotonic idle/nav repair for every finalized track, after the existing `lastPointTimeMs` fill:

```
spanSec        = (lastPointTimeMs - startTimeMs) / 1000
tlIdle         = timelineIdleSec(track.trackPoints)
tailSec        = max(0, (endTimeMs - lastPointTimeMs) / 1000)   # finalize tail; 0 for recovered tracks
storedDataIdle = max(0, track.idleDurationSec - tailSec)        # strip tail for like-for-like compare
if (tlIdle > storedDataIdle + 60):                              # under-counted by > 1 min
    idleDurationSec       = tlIdle
    navigatingDurationSec = max(0, spanSec - tlIdle)
# distanceNm / averageSpeedMps / fastestSpeedMps / endTimeMs / updatedAtEpochMs untouched
```

## Review — no negative impact on A–E + Fix 1

- **A (atomic save / save-before-delete):** migration 2 writes via existing `atomicReplace` + `rebuildIndex`; unchanged.
- **B (recovery end time):** unchanged. Recovered tracks have `endTimeMs == lastPointTimeMs` → `tailSec == 0`,
  and Fix 1 already set `idle == timelineIdle` → `tlIdle == storedDataIdle` → no-op.
- **C (lastPointTimeMs):** migration 2 runs after the existing fill and consumes it for `spanSec`.
- **D (transactional stop):** Change 1 is computation-only, still inside the same `runBlocking` write.
- **E (recovered-track backfill gate):** retained; migration 2 is an additional pass for normal tracks.
- **Fix 1 `withDerivedStats`:** unchanged; still the recovery/migration recompute.

## Edge cases

- Live tracks (`endTimeMs == null`) skipped.
- `< 2` non-GAP points → `tlIdle == 0` → no repair.
- Resumed tracks: `resumeGapDurationSec` is not persisted, so the nav recompute ignores it; the 60 s threshold
  and `spanSec` basis keep the change minimal — flagged as a known limitation.
- Idempotent: after repair `tlIdle == storedDataIdle` → no further change.
- Monotonic: idle is only ever raised, never lowered — a live-detected gap stop (no points captured) is preserved.

## Tests

- Unit: `timelineIdleSec` reproduces the under-count on dense-jitter points and the correct value on simplified points.
- Migration: storedIdle 51 m + tlIdle 3h28 m → repaired; storedIdle 1 h gap (no points) → untouched.
- Regression: recovered track from Fix 1 → migration 2 no-op.

## Key files
- [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:1059)
- [`TrackRepository.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:140)
- [`TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt)
- [`TrackStats.kt`](app/src/main/java/ykws/android/maro/data/track/TrackStats.kt)

## Implemented

- [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt) — `finalizeTrack()` now reconciles
  idle with `timelineIdleSec(simplifiedPoints)` instead of raw points.
- [`TrackRepository.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt) — `migrateTrackStats()` extended
  with a monotonic idle/nav repair for all finalized tracks (tail-stripped compare, +60 s threshold; distance/avg/fastest/end untouched).
- [`TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt) — `TRACK_SCHEMA_VERSION` 2 → 3.

Build: `gradlew assembleDebug` — **BUILD SUCCESSFUL**.
