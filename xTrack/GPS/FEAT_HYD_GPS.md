# Hydration — GPS

**Last Bake:** 2026-06-25 10:15
**State:** yarefact: Phase A+B+C complete. Polyline streaming via _newPoint SharedFlow, incremental appending, stop→restart fix. Build ✅.

## Summary
- Phase A — removed duplicate AdaptiveGpsPolicy from TrackRecorder, unified isStopped gate (`if (isStopped.value) return`), added feedDemoPosition() for demo mode stop detection with 1Hz periodic re-feed. GpsSignalWatchdog reordered before onFix, double-gated on `_acquisitionMode != IDLE`. setStoppedSource() forwarding on TrackViewModel.
- Phase B — created TrackSample data class, migrated entire recording pipeline from virtual GpsFix to TrackSample. MapScreen combine produces TrackSample directly. TrackRecorder: processFix→processSample, all internal methods updated.
- Fixes: stale remember closure on appSettings, periodic demo position feed for stop convergence.
- Rename: CoastlineViewModel → NavigationViewModel (file + class + all references).
- Build: ✅ SUCCESSFUL

## Modified Files
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` → `NavigationViewModel.kt` — file+class rename, watchdog reorder/gate, feedDemoPosition
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — TrackSample combine, feedDemoPosition calls, setStoppedSource
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — policy removal, isStopped wiring, GpsFix→TrackSample
- `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt` — _trackSample, pushTrackSample, setStoppedSource, Flow<TrackSample>
- `app/src/main/java/ykws/android/maro/data/track/TrackSample.kt` — new file
- `app/src/main/java/ykws/android/maro/MainActivity.kt` — NavigationViewModel import+type+Factory
- `xTrack/GPS/FEAT_DSC_GPS.md` — yarefact subfeature marked, Implemented section updated
- `xTrack/GPS/FEAT_PLN_GPS_yarefact.md` — refined consolidation plan

## Next Step
Phase C (streaming optimization) — deferred. On-device verification of recording in GPS and demo modes.
