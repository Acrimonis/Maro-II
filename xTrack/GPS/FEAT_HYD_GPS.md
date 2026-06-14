# Hydration — GPS

**Last Bake:** 2026-06-11 15:00  
**State:** All fixes implemented and building. UI icon tuning complete.

## Summary
- Icon backgrounds now configurable via `maro.properties` (waterIconBgAlpha, gpsIconBgAlpha, gpsIconDimBgAlpha)
- GPS status icon moved to bottom-left, DEMO uses 📡 with dim alpha (19%), IDLE color changed to blue
- Bottom padding reduced to 6dp for all elements
- Build: ✅ SUCCESSFUL

## Modified Files
- `app/src/main/java/ykws/android/maro/ui/map/ZoneConfig.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/assets/maro.properties`

## Next Step
On-device verification.
