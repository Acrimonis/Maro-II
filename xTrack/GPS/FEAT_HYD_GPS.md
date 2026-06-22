# Hydration — GPS

**Last Bake:** 2026-06-22 19:51
**State:** track-simplification: single-pass compound-importance Douglas-Peucker (ε=3m, δ=3kn) implemented. timeOffsetMs added (ProtoNumber 15, monotonic unique). Build ✅.

## Summary
- Single-pass compound-importance simplification: `importance = max(spatial/ε, |speed-avg|/δ)`. Replaces two-pass approach — no more speed-reinsertion artifacts.
- `timeOffsetMs: Long` (ProtoNumber 15) added to TrackPoint — monotonically unique ms timestamps for collision-free binary search.
- Spike rejection lock-in fix: `STALE_FIX_TIMEOUT_MS` (10s) in addPoint().
- Build: ✅ SUCCESSFUL

## Modified Files
- `app/src/main/java/ykws/android/maro/data/track/TrackSimplifier.kt` — single-pass compound importance (rewritten)
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — stale-fix timeout, timeOffsetMs computation
- `app/src/main/java/ykws/android/maro/data/track/TrackPoint.kt` — timeOffsetMs (ProtoNumber 15)
- `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt` — simplify params plumbing
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — simplify AppSettings fields
- `app/src/main/assets/maro.properties` — tracking tunables
- `xTrack/GPS/FEAT_DSC_GPS.md` — track-simplification subfeature
- `xTrack/GPS/FEAT_PLN_GPS_track-simplification.md` — design plan
- `xTrack/GPS/FEAT_HYD_GPS.md` — this file
- `xTrack/GLOBAL_CONTEXT.md` — active pointers

## Next Step
On-device verification: check simplified track for smoothness and speed profile preservation.
