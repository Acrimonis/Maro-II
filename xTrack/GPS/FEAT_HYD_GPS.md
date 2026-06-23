# Hydration — GPS

**Last Bake:** 2026-06-23 08:09
**State:** fix-track-extrapolation: dead-reckoning positions gated from track recording via `isEstimating` guard in MapScreen.kt combine flow. Build ✅.

## Summary
- Fix A — gated dead-reckoning extrapolated positions from leaking into track recording.
- `MapScreen.kt`: added `isEstimating` to combine inputs (line 519), guard `if (estimating) return@combine null` (line 524), `filterNotNull()` downstream (line 540).
- Import: `kotlinx.coroutines.flow.filterNotNull` (line 144).
- Dead reckoning updates `_gpsPosition` for display only; TrackRecorder now only receives real GPS fixes.
- Build: ✅ SUCCESSFUL (gradlew assembleDebug --rerun-tasks, 40/40)

## Modified Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — isEstimating gate + filterNotNull in combine flow feeding TrackRecorder
- `xTrack/GPS/FEAT_DSC_GPS.md` — fix-track-extrapolation subfeature marked [x], added to Implemented
- `xTrack/GPS/FEAT_PLN_GPS_fix-track-extrapolation.md` — analysis & fix plan
- `xTrack/GPS/FEAT_HYD_GPS.md` — this file
- `xTrack/GLOBAL_CONTEXT.md` — active pointers

## Next Step
On-device verification: stop boat in GPS mode, confirm no forward extrapolation spikes appear in recorded track (GPX export). Long-term: consider Option B (separate recording flow).
