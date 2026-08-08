# BoatTrace — Hydration Snapshot

**Baked at:** 2026-08-08 16:01 UTC
**Active Subfeature:** merge-idle-gap (inter-track gap as idle + moving)
**Branch:** feature/merge-track-idling

## Session Summary

**Merge Idle Gap — IMPLEMENTED.** TrackMerger.kt now estimates inter-track gap time as idle + moving, BUILD SUCCESSFUL.

For each adjacent pair of merged tracks, the time gap between `track[N].endTimeMs` and `track[N+1].startTimeMs` is split:
- **Moving:** `distance(lastPoint, firstPoint) / weightedAvgSpeed` (clamped to gap duration)
- **Idle:** remaining gap time

Both added to `navigatingDurationSec` and `idleDurationSec`. Invariant `navigating + idle = totalElapsed` now holds.

## Key Files (modified)

- `app/src/main/java/ykws/android/maro/data/track/TrackMerger.kt` — Gap-aware stats assembly (lines 88-121), +2 imports

## Plan

- `xTrack/BoatTrace/260808_FEAT_PLN_BoatTrace_merge-idle-gap.md`
