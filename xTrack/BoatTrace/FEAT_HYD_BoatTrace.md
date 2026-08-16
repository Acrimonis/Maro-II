# BoatTrace — Hydration Snapshot

**Baked at:** 2026-08-16 18:23 UTC
**Active Subfeature:** gps-recording-regression (service GPS sampling — Looper fix)
**Branch:** feature/fix-track-gps

## Session Summary

**Root cause of "GPS mode records no points":** the service-owned recorder's GPS producer
([`TrackRecordingService.startGpsSampling()`](app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:279))
collected [`GpsLocationSource.locationUpdates()`](app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt:64)
on `Dispatchers.Default`, which has no `Looper`. `LocationManager.registerGnssStatusCallback` therefore
threw `RuntimeException: Can't create handler … Looper.prepare()` on every attempt and the flow died
before any fix arrived. The UI's `NavigationViewModel` collects the same source on `Dispatchers.Main`,
which is why the map position kept updating while the track stayed at 0 points.

**Fix:** added `.flowOn(Dispatchers.Main.immediate)` to the GPS sampling flow so the listener registers
on the main thread. Verified on device: logcat showed `locationUpdates: GPS listener registered` with no
exception. The temporary diagnostic logs were removed; only the `flowOn` line and its import remain.

## Key Files (modified)

- `app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt` — `.flowOn(Dispatchers.Main.immediate)`

## Next Step

- On-water E2E: start recording in GPS mode and confirm points are captured (point count > 0).
