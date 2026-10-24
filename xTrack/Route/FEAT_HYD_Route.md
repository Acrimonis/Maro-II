# Context Hydration — Route — 2026-10-24

**Last Bake:** 2026-10-24 06:34 UTC — written by `#bake` on `feature/route-comment`; the `Any`-pace and marker-pricing bakes stand merged beside it.

**Directive trace:** no dependency was added, no binary file opened and the device never touched; the session ran on the user's `#impl` orders, every write was code, strings, plan and xTrack files, each claim had a read behind it, and the two `ask` review hops ran under `#implement` — none stopped.

## State

A saved route's track now carries a comment composed at save time: `#rank • rung • pace • pref` over a per-zone time line (`Open water • 300M • limit`, each `m:ss min`) and `Routed on date in s sec`. The rank is read at the time of save (live while the acquisition is open, the frozen end-of-search rank once following), the words come from the locale active at save time, and the zone line is gated on a live search so a read-back route never mislabels its duration. `compileDebugKotlin` and the scoped `TrackFromCourseTest` / `RoutePlanTest` / `PartialPlanOfTest` / `PerPointSpeedInvariantTest` / `RouteAcquisitionTest` are green (79 tests), and the work is committed on `feature/route-comment`.

## Target Files

- `app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt` — `build()` gained the `comment` parameter
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — `RoutePlan.searchDurationSec`, `commentFacts()`, `captureFollowedFacts()`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `routeCommentFor()` composes the three lines at `writeRouteTrack`
- `app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt` — `verdictDialWord()` and `paceHeaderWord()` non-composable cores
- `app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt` — the pace header reads `paceHeaderWord`
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` — `route_comment_routed_*`, `route_comment_open_water`, `route_comment_zone_entry_fmt`
- `xTrack/Route/261024_FEAT_PLN_Route_route-comment.md` — the plan, `## Implemented` filled

## Next Step

Save a route on the device and read its comment in the track list/card — the rank, the per-zone line and the `Routed on` figure — the device pass being the user's own.
