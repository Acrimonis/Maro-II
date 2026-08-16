# BoatTrace — Hydration Snapshot

**Baked at:** 2026-08-16 08:55 UTC
**Active Subfeature:** gps-recording-regression (re-arm cooldown + permission dialog)
**Branch:** feature/track-gps

## Session Summary

**GPS recording regression — FIXED + hardened.** The service-owned recorder's GPS producer now re-arms after a
startup `SecurityException`, throttled to once per 15s. A new `gpsPermissionMissing` StateFlow is set on
`SecurityException` and cleared on the first successful fix; `MapScreen` shows a once-per-episode AlertDialog
with Open Settings / Not Now (EN+FR strings), gated on GPS mode. Clean build (`gradlew clean assembleDebug`)
SUCCESSFUL — 42 tasks, pre-existing warnings only.

## Key Files (modified)

- `app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt` — cooldown + gpsPermissionMissing
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — permission-missing AlertDialog
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` — gps_permission_title + gps_permission_message

## Next Step

- On-device verification: fresh install and upgrade both record GPS points; permission dialog appears when denied.
