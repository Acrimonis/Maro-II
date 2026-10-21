# Context Hydration — Route — 2026-10-21

**Last Bake:** 2026-10-21 23:19 UTC — written by `#bake` on `feature/whatEver`; the `Any` pace redesign shipped across this session, each hop built and tested.

**Directive trace:** every covered action class was met and none stopped. Code changed only on the user's `#impl` orders; the device was never touched; no dependency was added; no binary read; the git writes were the user's own `#commit`s.

## State

**The `Any` pace's observed-speed source is redesigned and shipped (2026-10-21).** The live leg reads a recency-weighted 75th-percentile plateau live; a stored trip reads its 90th-percentile open-water cruise, speed-zone and 300 m band points excluded; the history is a weighted median of the last N trip speeds, seeded from the filtered tracks list, folded in on each completed trip and imported at startup; a three-second stop ends the live leg; and the stop reads `-- Now --` / *Live*. Full unit suite and `testDebugUnitTest` green.

**Open:** nothing outstanding — the filter is the filtered tracks list, the weight default is 60 %, and the plan's three open points are all closed.

## Target Files

- `app/src/main/java/ykws/android/maro/data/route/RoutePace.kt`, `SpeedHistory.kt`, `SpeedHistorySeeder.kt` — the reference, the window, the seed
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — the live feed, the stop reset, the history fallback
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`, `OverlayLayer.kt`, `NavigationViewModel.kt` — the seed, fold-in, startup import, exclusion
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`, `app/src/main/assets/maro.properties`, `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the keys and persistence
- `app/src/main/res/values*/strings.xml` — the `-- Now --` / Live rename and the Speed History section

## Next Step

None — the feature is complete and built; the device pass is the user's.
