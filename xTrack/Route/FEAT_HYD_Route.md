# Context Hydration — Route — 2026-10-24

**Last Bake:** 2026-10-24 06:34 UTC — written by `#bake` on `feature/route-comment`, merged with `feature/route-card-hilight`'s 05:47 bake from `origin/develop`.

**Directive trace:** no dependency was added, no binary file opened and the device never touched; both sessions ran under `#implement` with an `ask` review hop each, and none stopped.

## State

A saved route's track now carries a comment composed at save time: `#rank • rung • pace • pref` over a per-zone time line (`Open water • 300M • limit`, each `m:ss min`) and `Routed on date in s sec` — the rank read at the time of save, the words from the locale active then, and the zone line gated on a live search so a read-back route never mislabels its duration. The routes-list card's leading accent bar is dashed (the track card's solid), reusing the map's own saved-route dash rhythm. `compileDebugKotlin` and the scoped route/track suites are green (79 tests), and the branch is committed and pushed.

## Target Files

- `app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt` — `build()` gained the `comment` parameter
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — `RoutePlan.searchDurationSec`, `commentFacts()`, `captureFollowedFacts()`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `routeCommentFor()` composes the three lines at `writeRouteTrack`
- `app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt` — `verdictDialWord()` and `paceHeaderWord()` non-composable cores
- `app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt` — the pace header reads `paceHeaderWord`
- `app/src/main/java/ykws/android/maro/ui/components/ListSelectionRail.kt` — the `dashed` parameter and dash branch
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt` — `TrackCardContent` passes `dashed = summary.route`
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` — `route_comment_routed_*`, `route_comment_open_water`, `route_comment_zone_entry_fmt`
- `docs/ui-drawer-guidelines.md` §9, `docs/ui-lists-guidelines.md` §Selection door — the dashed-route / solid-track bar shape
- `xTrack/Route/261024_FEAT_PLN_Route_route-comment.md`, `xTrack/Route/261024_FEAT_PLN_Route_route-card-left-bar-dashed.md` — the two plans

## Next Step

Save a route on the device and read its comment in the track list/card; the dashed-versus-solid card bars and the comment's rank, per-zone line and `Routed on` figure are the device pass, the user's own.
