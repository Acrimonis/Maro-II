# 260827 — Gap-idle classification (GAP markers + distance/time)

- **Feature:** BoatTrace
- **Status:** implemented — build passing
- **Branch:** feature/persist-track-tweak
- **Date:** 2026-08-27

## Problem

🤿Le Rascoui (2026-08-17): the 2 h dive stop appears as a point gap (16:36:41 → 18:37:06 UTC,
displacement 35 m, implied 0.005 m/s) but the dashboard shows `nav 2h59m33s / idle 3m7s` — the
2 h dive is counted as navigating.

## Root cause

[`timelineIdleSec()`](app/src/main/java/ykws/android/maro/data/track/TrackStats.kt) skips any pair
adjacent to a GAP marker:

```
if (points[i].type == PointType.GAP || points[i-1].type == PointType.GAP) continue
```

During the dive the live stop detection inserted a GAP marker and captured no points for 2 h, so the
pair spanning the dive is GAP-adjacent → skipped → the 2 h never enters the idle classifier and falls
into `nav = span − idle`.

## Fix

In `timelineIdleSec()`: drop the GAP-skip, filter GAP markers out first, and classify every consecutive
pair of real points with the existing compound predicate:

```
real = points.filter { it.type != PointType.GAP }
for each consecutive pair (a,b) in real:
    dt = (b.timeOffsetMs - a.timeOffsetMs)/1000
    dM = haversine(a, b)
    if dt > 0 && dM/dt < 0.5 && dM < 500  →  idle += dt
```

- Rascoui dive gap: 35 m over 2 h → 0.005 m/s → idle.
- A resume/transit gap where the boat moved > 500 m (or fast) → navigating, unchanged.
- Identical rule to [`TrackMerger`](app/src/main/java/ykws/android/maro/data/track/TrackMerger.kt:97) inter-track gaps.

Because `withDerivedStats()` and [`finalizeTrack()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:1083)
both call `timelineIdleSec()`, a single change propagates to recovery, finalize, and migration.

## Migration

Bump `TRACK_SCHEMA_VERSION` 3 → 4 in [`TrackViewModel`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt).
The existing idle/nav repair (monotonic, tail-stripped, > 60 s threshold) re-runs with the corrected
`timelineIdleSec`, raising this track's idle from 3 m to ~2 h and lowering nav to ~58 m retroactively.

## Review — no negative impact

- **Village track:** 0 GAP markers → the filter is a no-op; idle unchanged.
- **Recovered tracks (Fix 1):** `withDerivedStats()` now includes gap idle → more correct, not regressed.
- **TrackMerger:** already uses the same predicate; untouched.
- **finalize:** `timelineIdleSec(simplifiedPoints)` handles both simplified-away and preserved GAP markers.
- **Conservative 500 m cap retained:** a dive whose drift exceeds 500 m would still classify the gap as
  moving (same existing trade-off as TrackMerger) — flagged, not changed.

## Tests

- Unit: `timelineIdleSec` classifies a 2 h / 35 m gap as idle and a 2 h / 2 km gap as moving.
- Migration: Rascoui-like track (storedIdle 3 m, gap 2 h) → repaired to idle ~2 h.
- Regression: Village track (no GAP) → unchanged; recovered track (Fix 1) → idle only rises.

## Key files
- [`TrackStats.kt`](app/src/main/java/ykws/android/maro/data/track/TrackStats.kt)
- [`TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt)

## Implemented

- [`TrackStats.kt`](app/src/main/java/ykws/android/maro/data/track/TrackStats.kt) — `timelineIdleSec()` now
  filters GAP markers and classifies every consecutive real-point pair (including time gaps) with the
  compound predicate (`dM/dt < 0.5 m/s && dM < 500 m`).
- [`TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt) — `TRACK_SCHEMA_VERSION` 3 → 4.

Build: `gradlew assembleDebug` — **BUILD SUCCESSFUL**.
