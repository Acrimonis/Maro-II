# Context Hydration — Route — 2026-11-11

**Last Bake:** 2026-11-11 14:53 UTC — written by `#bake` on `feature/route-tweeks`.

**Directive trace:** no dependency was added, no binary file opened and the device not touched; the code moved on the user's `#impl` after a plan the user approved point by point (D1–D7), the `ask` hop reviewing the design and the built change; the user then invoked `#bake`, `#commit` and `#push` in one message; none of the covered action classes stopped.

## State

`feature/route-tweeks` removes the question at the end of a followed route. The toggle's off and the back key end it through the deferred window with Undo (`leaveRouteMode()` is the one door), the menu's Go restarts silently, and arrival is a status pill held one second (`route.follow.arrival.settleSec`) while the boat is within `route.follow.arrival.etaSec` of the destination, in a new stack of status pills (`MapStatusBannerStack`). The exit dialog, its doors and the arrival cue are gone. `apk-build.bat` green, the suite at 1 762 tests, 27 skipped, 0 failures. Open: the device pass (the status stack against the Undo snack column), the hold's missing unit test, and the any-angle P3/P5 and the parked items in the feature file.

## Target Files

- `xTrack/Route/261111_FEAT_PLN_Route_end-of-life.md` — the plan, implemented; §5 names the `goRoute` commit-before-in-flight-guard race left alone
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `leaveRouteMode()` and the arrival notice's settle hold
- `app/src/main/java/ykws/android/maro/ui/map/MapControls.kt` — `MapStatusBannerStack`
- `xTrack/Route/261029_FEAT_PLN_Route_any-angle-search.md` — P3 and P5, still open

## Next Step

The end-of-life device pass (the user's own); the any-angle plan's P3 after it.
