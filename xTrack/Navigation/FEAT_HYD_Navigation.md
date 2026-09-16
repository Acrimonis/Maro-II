# Hydration: Navigation

**Baked:** 2026-09-16 17:10 UTC
**Last Bake:** 2026-09-16 17:10 UTC
**Status:** active

## State

The dashboard's position source is mode-aware: `dashboardPositionFor(marker, boat, gpsMode)` in `DashboardPosition.kt` returns the boat in GPS mode and the marker in demo, and the `dashboardPosition` flow feeds the shore pipeline in place of `_mapCenter`, so a dragged map can no longer move the water flag, the distance, the band state, the speed-zone query or the zone situation. `syncCenterFromBoat` gates both boat-driven centre writes behind `autoFollowSuppressed` — the user owns the centre until the recenter button or the resume timer hands it back, and both now restore it in-frame. The tag stack and the auto-reveal narrowing keep the marker by decision, so the screen answers two questions on purpose. Plan `260916_FEAT_PLN_Navigation_dashboard-position-source.md` is implemented: `DashboardPositionTest` green, `apk-build.bat` SUCCESS, device pass open.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/DashboardPosition.kt` — the pure predicate
- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt` — `dashboardPosition`, `syncCenterFromBoat`, `recenterNow`, `startTimer`
- `app/src/test/java/ykws/android/maro/ui/map/DashboardPositionTest.kt` — the three cases
- `xTrack/Navigation/260916_FEAT_PLN_Navigation_dashboard-position-source.md` — the plan and its review V1–V8

## Next Step

Device pass: the band card and the distance stay on the boat while the map is dragged in GPS mode, the same values follow the marker in demo, an approach reveal still fires at the configured distance, and the recenter button and the timer each restore the centre at once.
