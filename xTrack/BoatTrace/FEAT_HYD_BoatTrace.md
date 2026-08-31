# BoatTrace — Hydration Snapshot

**Baked at:** 2026-08-31 13:16 UTC
**Active Subfeature:** auto-marker-cleanup (🕐 IDLE_AUTO marker lifecycle hardening)
**Branch:** feature/track-n-markers (pending — not yet created from origin/develop)

## Session Summary

Planned hardening of the auto-created marker (🕐 IDLE_AUTO) lifecycle. Temp markers are created `confirmed=false, keepable=false` and confirmed or deleted when the idle period ends; the decision currently lives in the MapScreen composable, which dies on backgrounding while the foreground service keeps recording. Plan (Ask-reviewed, approved with 5 amendments): move createTemp/confirm/delete into a recorder-owned AutoMarkerManager; serialize repository writes; remove dead ACTION_SET_* intents and set*AutoMarkerId plumbing; confirm before persisting BoatMarker.autoMarkerId with a durable finalize fallback; render-time existence check for ghost pins; scope startup cleanup to crash orphans only; fix merged markers to confirmed+keepable. Final open idle at finalize stays kept by design.

## Key Files (target)

- `app/src/main/java/ykws/android/maro/data/markers/UserMarkerRepository.kt` — add Mutex
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — recorder wiring
- `app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt` — dead code removal
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — merge fix, expose unfiltered ids
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — ghost-pin check, startup cleanup scope
- `xTrack/BoatTrace/260831_FEAT_PLN_BoatTrace_auto-marker-cleanup.md` — plan

## Next Step

Implement the plan (pending user go-ahead); then build + deploy + E2E cleanup scenarios.
