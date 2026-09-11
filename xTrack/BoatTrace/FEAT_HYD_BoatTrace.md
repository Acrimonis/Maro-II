# BoatTrace — Hydration Snapshot

**Baked at:** 2026-09-11 15:55 UTC
**Active Subfeature:** live-track-paint-regression
**Branch:** feature/tracks-recording (created from origin/develop 2026-09-11)

## Session Summary

Diagnosed and fixed the live recorded track no longer painting in real time.

- **Root cause (confirmed):** the C3/C4 seam extraction turned a `by collectAsState()` delegate read into a plain-parameter read. `MapTrackOverlayLiveEffects` received a frozen `TrackRecorderUiState`, so `snapshotFlow { trackRecorderState.state }` inside an effect keyed on `(mapView, trackingColorActive)` emitted the launch-time value once and never saw `OFF → ON`. The `track_recording` polyline was never created and both consumers silently bailed (append dropped points, trailing segment returned early).
- **Proof:** `git log` shows `MapTrackOverlayEffects.kt` has a single commit (`9d95a16`, PR #225); its parent `MapScreen.kt` contains no `snapshotFlow` at all and reads the state directly. The wrapper was introduced by the extraction.
- **Fix:** creation effect re-keyed on `trackRecorderState.state`; append/trailing self-heal (create the line on demand); `!it.isLive` added to the map-resolve filter and highlighted pull-back (latent — reachable only on resume, where the main `.bin` is re-saved with `endTimeMs = null`).
- Build `assembleDebug` SUCCESSFUL; Ask review SOUND. Uncommitted at bake time.

## Next Step

Device E2E: fresh recording paints the active line point-by-point (demo + GPS), resume still paints, GAP seam dashed, filter cases clean.

## Key Files

- `app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt`
- `xTrack/BoatTrace/260911_FEAT_PLN_BoatTrace_live-track-paint-regression.md`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt`
- `app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt`
