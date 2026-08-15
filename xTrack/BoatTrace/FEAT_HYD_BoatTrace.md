# BoatTrace — Hydration Snapshot

**Baked at:** 2026-08-15 14:48 UTC
**Active Subfeature:** idle-reconciliation (compound idle predicate + BoatMarker sweep + GPX UTC)
**Branch:** feature/tracks-mgt

## Session Summary

**Idle Reconciliation — IMPLEMENTED.** Unified compound idle predicate `(d/Δt < 0.5 m/s) AND (d < 500 m)` applied at finalize and at merge. BUILD SUCCESSFUL (×2).

### Fix 1: Within-track idle reconciliation on save
`TrackRecorder.computeTimelineIdleSec()` re-derives idle from the RAW point timeline at finalize. Long screen-off/backgrounded stops (e.g. the 2h34m dive stop) now count as idle instead of silently becoming navigating. `maxOf(live, timeline)` + clamps.

### Fix 2: Resume seam always marked
`detectAndInsertGap(force = true)` on the first post-resume point — the seam is always a GAP marker, so timeline idle computation skips it deterministically.

### Fix 3: BoatMarker finalize sweep
Open IDLE BoatMarkers (never MANUAL) are closed at finalize with a single `finalizeTimeMs`. `recomputeDescription()` re-run after sweep so durations land in the comment. Checkpoint-write race fixed with `isFinalizing` guard.

### Fix 4: Merge same-area shortcut
`TrackMerger` applies the same compound predicate before its `d/v_ref` decomposition — a gap where the boat stayed in the area is fully idle.

### Fix 5: GPX export UTC
`GpxExporter.isoFormat` now writes UTC timestamps (was device-local + literal `Z`).

## Key Files (modified)

- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — compound predicate, forced resume GAP, finalize reconciliation + marker sweep + isFinalizing
- `app/src/main/java/ykws/android/maro/data/track/TrackMerger.kt` — same-area shortcut before d/v_ref
- `app/src/main/java/ykws/android/maro/data/track/GpxExporter.kt` — UTC isoFormat

## Plan

- `xTrack/BoatTrace/260815_FEAT_PLN_BoatTrace_idle-reconciliation-fixes.md`
