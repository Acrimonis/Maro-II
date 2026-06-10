# Hydration: Navigation

**State:** Active — all subfeature implementation complete. Pending on-device visual verification.

**Session summary:** Replaced three separate StateFlows (`_mapBearing`, `_speedKnots`, `_demoSpeedKnots`) with a single atomic `NavigationState` data class. Fixed arrow rendering by removing `.offset(y = -arrowDp)` which was pushing the arrow ~38dp above the boat. Added `DirectionLine` composable (dashed line from screen center to map edge in heading direction). Added `headingLineVisible` (default ON) + `capArrowVisible` (default OFF) to `AppSettings` with a Navigation settings card under Display. Added `direction.line.color` to `maro.properties`. Retuned arrow: 1dp min @3kn → 65dp max @30kn, 2.5kn threshold, thicker stroke ×1.5, bigger dashes.

**Target files:**
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/ZoneConfig.kt`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- `app/src/main/assets/maro.properties`
- `xTrack/Navigation/FEAT_DSC_Navigation.md`
- `xTrack/GLOBAL_CONTEXT.md`

**Next step:** Deploy APK and visually verify on-device: direction line, cap arrow, settings toggles, arrow length distribution across speeds.
