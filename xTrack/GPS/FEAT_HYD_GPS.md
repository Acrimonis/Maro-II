# Hydration — GPS

**Last Bake:** 2026-06-28 17:52
**State:** yarefact: Phase A+B+C complete. background-recording: ON_PAUSE GPS-kill gated behind recording state. Build ✅.

## Summary
- yarefact Phase A — removed duplicate AdaptiveGpsPolicy from TrackRecorder, unified isStopped gate, feedDemoPosition(), GpsSignalWatchdog reorder + double-gate. setStoppedSource() forwarding.
- yarefact Phase B — TrackSample data class, entire pipeline migrated from virtual GpsFix. MapScreen combine produces TrackSample directly.
- yarefact Phase C — _newPoint SharedFlow, incremental polyline append, removed full-list recordingPoints copy.
- **background-recording** — one-line conditional in ON_PAUSE: `if (trackRecorderState.state != TrackRecorderState.ON) { setGpsActive(false) }`. GPS survives backgrounding when recording.
- Fixes: stale remember closure, periodic demo position feed, CoastlineViewModel→NavigationViewModel rename.
- Build: ✅ SUCCESSFUL (both yarefact and background-recording)

## Modified Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — ON_PAUSE guard, TrackSample combine, incremental polyline append, feedDemoPosition, setStoppedSource
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` → `NavigationViewModel.kt` — rename, watchdog reorder/gate, feedDemoPosition
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — policy removal, isStopped wiring, GpsFix→TrackSample, _newPoint SharedFlow
- `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt` — _trackSample, pushTrackSample, setStoppedSource
- `app/src/main/java/ykws/android/maro/data/track/TrackSample.kt` — new file
- `app/src/main/java/ykws/android/maro/MainActivity.kt` — NavigationViewModel import+type+Factory
- `xTrack/GPS/FEAT_DSC_GPS.md` — background-recording subfeature [x], Implemented section updated
- `xTrack/GPS/FEAT_PLN_GPS_yarefact.md` — plan validated, all items [x]

## Dead Code
- `recordingPoints: List<TrackPoint>` in `TrackRecorderUiState` (line 78) — field defined but never populated.

## Next Step
On-device verification: start recording, background app, confirm track continues.
