# Hydration: ColorManagement

**Last Bake:** 2026-06-17 14:39 UTC
**State:** Session complete — all planned changes implemented and building green.

## Session Summary

Full color centralization pass:
1. Replaced 46 hardcoded `ComposeColor.White` refs (42 MapScreen.kt + 1 FanLayout.kt + 2 kept structural in FanIconComponents.kt)
2. Synced 12 stale AppConfig defaults to match colors.properties
3. Removed 4 orphaned zone.properties color fields + their loading code
4. Added 3 isobar color tokens + 2 isobar width tokens to colors.properties
5. Consolidated dual-sourced `lowDepthWarningMinOpacityPct`
6. Moved zone.properties spatial values to maro.properties, deleted zone.properties
7. Removed 3 dead alpha keys from maro.properties
8. Removed 3 dead gradient fields from AppConfig

## Target Files
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt`
- `app/src/main/assets/colors.properties`
- `app/src/main/assets/maro.properties`
- `plans/color-taxonomy-hardcoded-whites-audit.md`
- `plans/color-taxonomy-alpha-values.md`

## Next Step
None — session complete. Ready for commit.
