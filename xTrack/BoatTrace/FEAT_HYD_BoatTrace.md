# BoatTrace — Hydration Snapshot

**Baked at:** 2026-08-31 16:20 UTC
**Active Subfeature:** marker-export-import
**Branch:** feature/track-n-markers

## Session Summary

Auto-marker lifecycle + marker-track relationship rework, in phases:

- **Phase 1 (auto-marker cleanup):** recorder-owned `AutoMarkerManager` (createTemp/confirm/delete), `UserMarkerRepository` Mutex + change channel, durable finalize fallback, startup crash-orphan cleanup, merged markers keepable. BUILD SUCCESSFUL.
- **Phase 2 (marker-track link):** single `UserMarker.trackId` set at creation; one-time backfill migration; removed persisted `BoatMarker.autoMarkerId`; delete-track deletes its IDLE_AUTO markers; markers rendered from unfiltered `_allMarkers`; marker badge + "Belongs to track" row. BUILD SUCCESSFUL.
- **Track export:** fixed duplicate-entry crash (unique names), Windows-safe filename sanitization, `yyyy_MM_dd_HH_mm-title-counter.gpx` naming, menu Import/Export reorder with labeled controls.
- **Track import modes:** single GPX → Skip/Update/New dialog; ZIP → silent skip; update prefers edited `<trkpt>` points and recomputes stats. BUILD SUCCESSFUL.
- **Phase 3** (cross-navigation) planned, deferred. **Phase 4** marker export/import planned, partially implemented (track import modes done).

## Next Step

On-device E2E for all implemented work; then Phase 4 marker export/import; then Phase 3 cross-navigation.

## Key Files

- `app/src/main/java/ykws/android/maro/data/markers/AutoMarkerManager.kt`
- `app/src/main/java/ykws/android/maro/data/markers/UserMarkerRepository.kt`
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`
- `app/src/main/java/ykws/android/maro/data/track/GpxImporter.kt`, `GpxExporter.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`, `MarkerManagementOverlay.kt`, `MarkerDrawer.kt`, `MenuDrawerOverlay.kt`
