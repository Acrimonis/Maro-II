# Context Hydration — Route — 2026-10-22

**Last Bake:** 2026-10-22 14:09 UTC — written by `#bake` on `feature/route-dash`.

**Directive trace:** none of the five covered action classes was met since the last bake — no dependency added, no machine-shaped spatial file opened, the work was ordered, the device was untouched, and every claim came from a file read.

## State

The route search table is rebuilt on `feature/route-dash`: a 50/25/25 split where column 1 keeps the rung name, the rank and the crown, column 2 is vertically centered with its time · speed · distance lines (value right-aligned in the fixed box, unit left-aligned) and the all-inclusive average speed seated second (one `routeAvgSpeedKn` home read by the row and `RoutePlan.plannedPaceKn`), and column 3 grows past its share to print one speed-limit reading per line — `300M : 1:05 min` — rounded to the second, a sub-minute reading kept and only a zero dropped. The two-per-line pairing and its `routeSlowLimitRows` helper were removed, and the table's UI-specific values live in `ui.properties` under `ui.route.table.*`, read through `AppConfig`.

**Open:** the device look across portrait and landscape.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt` — the 50/25/25 table with `BoxWithConstraints`, the vertically centered figures column, the one-reading-per-line grid and the `routeSpeedReadings` helper
- `app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt` — `routeAvgSpeedKn`, `RouteRowKind.SPEED`, the three-line `routeRowLines`, seconds in `RouteSlowLimitEntry`; `routeSlowLimitRows` removed
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — `RoutePlan.plannedPaceKn` reads `routeAvgSpeedKn`
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`, `app/src/main/assets/ui.properties` — the `ui.route.table.*` tokens
- `docs/ui-drawer-guidelines.md` — the column 2 and `m:ss min` rules
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml` — the speed value and unit keys; `route_slow_minutes_fmt` removed
- `app/src/test/java/ykws/android/maro/ui/map/RouteSlowLimitTest.kt`, `app/src/test/java/ykws/android/maro/ui/map/RouteAcquisitionTest.kt` — the seconds rounding, the three-line order and the average-speed pin
- `xTrack/Route/261022_FEAT_PLN_Route_dash-table.md` — the plan, with `## Implemented`

## Next Step

Deploy and check the 50/25/25 split, the centered figures and the `m:ss min` readings on the device.
