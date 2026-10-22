# Context Hydration — Route — 2026-10-22

**Last Bake:** 2026-10-22 13:20 UTC — written by `#bake` on `feature/route-dash`.

**Directive trace:** none of the five covered action classes was met since the last bake — no dependency added, no machine-shaped spatial file opened, the work was ordered, the device was untouched, and every claim came from a file read.

## State

The route search table is reworked on `feature/route-dash`: each of the three columns takes an equal third, column 1 keeps the rung name, the rank and the crown, column 2 centers its time · speed · distance lines with the new all-inclusive average speed seated second (one `routeAvgSpeedKn` home read by the row and `RoutePlan.plannedPaceKn`), and column 3 lists one speed-limit reading per line — `300M : 1:05 min` — rounded to the second, a sub-minute reading kept and only a zero dropped. The two-per-line pairing and its `routeSlowLimitRows` helper were removed, and all three columns carry the same 8/8/4/4 inner padding on a tightened line height.

**Open:** the device look across portrait and landscape.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt` — the equal-thirds table, the centered figures column, the one-reading-per-line grid and the `routeSpeedReadings` helper
- `app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt` — `routeAvgSpeedKn`, `RouteRowKind.SPEED`, the three-line `routeRowLines`, seconds in `RouteSlowLimitEntry`; `routeSlowLimitRows` removed
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — `RoutePlan.plannedPaceKn` reads `routeAvgSpeedKn`
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml` — the speed value and unit keys; `route_slow_minutes_fmt` removed
- `app/src/test/java/ykws/android/maro/ui/map/RouteSlowLimitTest.kt`, `app/src/test/java/ykws/android/maro/ui/map/RouteAcquisitionTest.kt` — the seconds rounding, the three-line order and the average-speed pin
- `xTrack/Route/261022_FEAT_PLN_Route_dash-table.md` — the plan, with `## Implemented`

## Next Step

Deploy and check the table's equal thirds, the centered figures and the `1:05 min` readings on the device.
