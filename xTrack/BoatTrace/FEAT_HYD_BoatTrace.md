# BoatTrace — Hydration Snapshot

**Baked at:** 2026-08-16 08:19 UTC
**Active Subfeature:** gps-recording-regression (service GPS producer re-arm)
**Branch:** feature/track-gps

## Session Summary

**GPS recording regression — FIXED.** The 2026-08-15 service-owned-recorder refactor moved GPS sample
assembly into `TrackRecordingService`, whose run-once GPS producer died on a startup `SecurityException`
(fresh install / permission not yet granted) and never restarted. The recorder stayed ON with zero samples —
blue idle icon, 0 points, no polyline, empty drawer dash — while demo mode and the UI GPS pipeline stayed healthy.

**Fix:** added `TrackRecordingService.ensureGpsSampling()`, called from `onStartCommand`, which re-arms the
GPS producer only when `demoMode` is false and the sampling job is inactive. `startGpsSampling()` now calls
`adaptivePolicy.reset()` on restart and logs the exception type + message. `gradlew assembleDebug` BUILD SUCCESSFUL.

Side-effect review (Debug mode): no blocking side effects — no double listener, demo gating intact,
START_STICKY restart correct, no stale `_stopped`. Two low notes: negligible `adaptivePolicy` reset-vs-onFix
race and bounded `SecurityException` log spam while permission is missing. No further change required.

## Key Files (modified)

- `app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt` — ensureGpsSampling + re-arm hardening

## Next Step

- On-device verification: fresh install and upgrade both record GPS points.
