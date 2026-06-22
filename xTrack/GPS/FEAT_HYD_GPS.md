# Hydration — GPS

**Last Bake:** 2026-06-22 16:52
**State:** troubleshoot-gps-turns: spike rejection lock-in fix implemented (stale-fix timeout 10s). Build ✅.

## Summary
- Spike rejection lock-in fix: added `STALE_FIX_TIMEOUT_MS` (10s) in `TrackRecorder.kt` — skips all gates when no fix accepted for >10s
- Covers both sharp-turn lock-in (stale `lastValidCourseDeg`) and silent GPS recovery (no `emitNoLock` triggered)
- Added `tracking.checkpointIntervalMs` and `tracking.staleFixTimeoutMs` to `maro.properties`
- Build: ✅ SUCCESSFUL

## Modified Files
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — stale-fix timeout check + `lastAcceptedTimeMs` field
- `app/src/main/assets/maro.properties` — `tracking.checkpointIntervalMs=30000`, `tracking.staleFixTimeoutMs=10000`
- `xTrack/GPS/FEAT_DSC_GPS.md` — troubleshoot-gps-turns subfeature
- `xTrack/GPS/FEAT_PLN_GPS_troubleshoot-gps-turns.md` — root cause analysis + fix plan
- `xTrack/GPS/FEAT_HYD_GPS.md` — this file
- `xTrack/GLOBAL_CONTEXT.md` — active feature/subfeature pointers

## Next Step
On-device verification with sharp turns and GPS shadow zones (tunnels, bridges).
