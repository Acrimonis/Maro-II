# BoatTrace — Hydration Snapshot

**Baked at:** 2026-06-18 16:59 UTC

## Session Summary

Multiple fixes in this session:

1. **Live track name/comment persistence** — `updateCurrentTrackMeta()` now emits to `_uiState` so UI reflects edits. Added `currentTrackComment` to `TrackRecorderUiState`. Fixed `remember` keys for name/comment fields in `LiveTrackCard`.

2. **Recorder disposal on GPS mode toggle** — `LaunchedEffect(appSettings.gpsMode)` was recreating the recorder on every mode switch, killing active recordings. Changed key to `Unit` with dynamic `flatMapLatest` ticker.

3. **Auto-start suppression** — Removed speed-based auto-start paths. Only geofence exit (inside→outside transition) triggers auto-start.

## Files Changed

- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — UI state emission, geofence-exit detection, removed auto-start paths
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt` — comment field init, remember keys
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — LaunchedEffect(Unit), flatMapLatest ticker
- `app/src/main/java/ykws/android/maro/ui/map/TrackDrawerOverlay.kt` — POSITION SOURCE section, GPS toggle color
- `app/src/main/res/values/strings.xml` — track stat strings
- `app/src/main/res/values-fr/strings.xml` — track stat French translations
- `xTrack/BoatTrace/FEAT_DSC_BoatTrace.md` — updated rules to match new trigger design

## Next Steps

- [ ] Verify no regressions in manual start/stop via drawer
- [ ] Verify auto-start on geofence exit (inside→outside Port Salis)
- [ ] Verify auto-stop on geofence re-entry
- [ ] E2E: leave Port Salis → verify auto-start → track on map → return → verify auto-finalize
