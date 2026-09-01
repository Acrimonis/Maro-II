# BoatTrace — Hydration Snapshot

**Baked at:** 2026-09-01 16:16 UTC
**Active Subfeature:** marker-export-import
**Branch:** feature/track-n-markers

## Session Summary

Auto-marker lifecycle + marker-track relationship rework, in phases:

- **Phase 1 (auto-marker cleanup):** recorder-owned `AutoMarkerManager` (createTemp/confirm/delete), `UserMarkerRepository` Mutex + change channel, durable finalize fallback, startup crash-orphan cleanup, merged markers keepable. BUILD SUCCESSFUL.
- **Phase 2 (marker-track link):** single `UserMarker.trackId` set at creation; one-time backfill migration; removed persisted `BoatMarker.autoMarkerId`; delete-track deletes its IDLE_AUTO markers; markers rendered from unfiltered `_allMarkers`; marker badge + "Belongs to track" row. BUILD SUCCESSFUL.
- **Track export:** fixed duplicate-entry crash (unique names), Windows-safe filename sanitization, `yyyy_MM_dd_HH_mm-title-counter.gpx` naming, menu Import/Export reorder with labeled controls.
- **Track import modes:** single GPX → Skip/Update/New dialog; ZIP → silent skip; update prefers edited `<trkpt>` points and recomputes stats. BUILD SUCCESSFUL.
- **Bug fix (track-pin crash):** `save()` shared-tmp race → `NoSuchFileException`; fixed with unique temp filename + guarded `atomicReplace` fallback + collapsed redundant double-save in `onUpdateTrack` handlers. BUILD SUCCESSFUL.
- **Import UX normalization:** magic-byte ZIP/GPX sniffing, `ImportResult(imported, ignored)`, normalized Compose result banner, 3-action conflict sheet (Duplicate/Override/Cancel), localized strings. BUILD SUCCESSFUL.
- **Drawer Import/Export:** whole control clickable + dismiss-then-action; "Exporting/Importing…" status banner. BUILD SUCCESSFUL.
- **Pin unification:** PushPin icon + `cd_pin`/`cd_unpin` across track/marker cards + multi-select. BUILD SUCCESSFUL.
- **Marker pinned→icon simplification:** drop `pinned`, derive from `icon != null`; one-time migration; geometry filter split (Pins/Circles/Corridors); origin sort Manual-first; "Icon" filter labels; emojis 📍/🎯/🛤️. BUILD SUCCESSFUL.
- **Phase 3** (cross-navigation) planned, deferred. **Phase 4** marker export/import planned (track import modes done).

## Next Step

On-device E2E for all implemented work; then Phase 4 marker export/import; then Phase 3 cross-navigation.

## Key Files

- `app/src/main/java/ykws/android/maro/data/markers/AutoMarkerManager.kt`
- `app/src/main/java/ykws/android/maro/data/markers/UserMarkerRepository.kt`
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`
- `app/src/main/java/ykws/android/maro/data/track/GpxImporter.kt`, `GpxExporter.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`, `MarkerManagementOverlay.kt`, `MarkerDrawer.kt`, `MenuDrawerOverlay.kt`
