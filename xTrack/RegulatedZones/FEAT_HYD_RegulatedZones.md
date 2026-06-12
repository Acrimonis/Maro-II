# Hydration: RegulatedZones

**Last Bake:** 2026-06-12 10:16 UTC+2
**State:** Active — preparation-for-icons-layout subfeature complete.

## Summary
- **GPS icon moved** from bottom-left to top-left — grouped with EarthWaterIcon in a Row at `Alignment.TopStart`
- **Icon colors normalized** — GPS HEALTHY now uses `#2E7D32` (EarthWaterIcon's green), GPS IDLE uses `#1565C0` (theme blue), both more saturated
- **Icon transparency properties** added to `maro.properties`:
  - `icon.back.active.transparency=75` (75% opacity for active states)
  - `icon.back.inactive.transparency=50` (50% opacity for dimmed/demo states)
- **ZoneConfig refactored** — unified `iconBackActiveAlpha`/`iconBackInactiveAlpha` fields; old `gpsIconBgAlpha`/`gpsIconDimBgAlpha`/`waterIconBgAlpha` now delegate to them
- **`BUILD SUCCESSFUL`** — `assembleDebug` compiles cleanly

## Target Files
- `maro.properties` — MODIFIED. Added `icon.back.active.transparency`, `icon.back.inactive.transparency`
- `app/build.gradle.kts` — MODIFIED. Reads new properties as `BuildConfig.ICON_BACK_ACTIVE_ALPHA`, `ICON_BACK_INACTIVE_ALPHA`
- `app/src/main/java/ykws/android/maro/ui/map/ZoneConfig.kt` — MODIFIED. Unified `iconBackActiveAlpha`/`iconBackInactiveAlpha` fields
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — MODIFIED. GPS icon repositioned, colors normalized

## Key Changes
- GpsStatusIcon + EarthWaterIcon now in a Row at TopStart (GPS left, Earth/Water right)
- GPS HEALTHY: `#4CAF50` → `#2E7D32` (consistent green)
- GPS IDLE: `#42A5F5` → `#1565C0` (consistent theme blue)
- GPS DEMO contentAlpha: 0.35 → 0.50
- All icon backgrounds now use unified 75% / 50% opacity from maro.properties

## Next Steps
None pending — feature is stable. Open for future enhancement:
- Vessel-size filter integration in display pipeline
- Per-type visibility toggles (speed, anchoring, access, etc.)
- Zone tap interaction (highlight + full details in info banner)
