# Hydration — GPS

**Last Bake:** 2026-06-11 14:00  
**State:** All gps-loss fixes implemented and building successfully.

## Summary
- Added `GnssStatus.Callback`, `onStatusChanged` handler, `PASSIVE_PROVIDER` supplement to `GpsLocationSource`
- Added stale-fix watchdog (5 s timeout), error logging, debounce (100 ms), idle min distance to `CoastlineViewModel`
- Added `gpsIdleMinDistanceM` setting to `AppSettings`
- Replaced dashboard GPS status bar with compact 5-state `GpsStatusIcon` on the map (below EarthWaterIcon)
- Build: ✅ SUCCESSFUL

## Modified Files
- `app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt`
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

## Next Step
On-device verification of: stale indicator, GPS lock recovery, passive provider fallback, idle cadence display.
