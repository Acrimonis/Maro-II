# Context Hydration — Route — 2026-10-03

**Last Bake:** 2026-10-03 03:11 UTC — written by `#bake`

**Directive trace:** The `#merge` of `origin/feature/route-dash-n-flow` into `feature/more-routing` ran on the user's word with the source swapped, the four conflicts resolved per the matrix, and this `#bake` followed the user's "Bake first" before `#commit` and `#push`. Of the five covered action classes none was met unasked: no dependency was added, no machine-shaped data file was opened, no file was written without the order, the device was never touched, and every git write was named by the user.

## State

**`feature/more-routing` now carries both the slow-water dials and the route-dash-n-flow work.** Merge commit `b3778e5b` brought the four incoming commits — the early select/save, the shared-`DrawerScaffold` acquisition panel with its paging three-column table, the single-home Routing tab with Tuning/Appearance blocks, and the `GLOBAL_CONTEXT.md` pointer delegation — onto the aversion work. Four conflicts resolved per the matrix: the aversion dial re-homed into the Routing tab's Tuning block (the Navigation tab lost its Route section), `GLOBAL_CONTEXT.md` kept the pointer form with the 02:39 aversion session at the Focus History top, `FEAT_DSC_Route.md` kept both `## Implemented` entries, and the hydration kept the newest bake.

**The two features sit side by side.** The three dials (free-water pace, slow-water budget, the aversion slider) live in the Routing tab's Tuning block; the acquisition panel is the shared `DrawerScaffold` with the paging table and the one weighted `Save · Select · Discard` row. Requirements R88–R90 are the functional flow's; Phases 0/A/B/D of the aversion plan shipped, with Phase C and the Driving-preference cursor still designed and owed.

**Verification.** `gradlew.bat :app:assembleDebug :app:testDebugUnitTest` BUILD SUCCESSFUL (40 s) after the merge, whole unit suite green. The three aversion test harnesses pass the `aversionKn` provider and are the standing uncommitted work. Nothing device-validated.

## Target Files

- [`MapScreenSettingsOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1501) — the aversion dial re-homed into `RoutingSettings`' Tuning block
- [`GLOBAL_CONTEXT.md`](../GLOBAL_CONTEXT.md:5) — pointer form kept, the aversion session at the Focus History top
- [`FEAT_DSC_Route.md`](FEAT_DSC_Route.md:224) — both `## Implemented` entries kept, requirements R88–R90
- tests: [`RouteAvoidEngineTest.kt`](../../app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt), [`RouteEngineChoiceTest.kt`](../../app/src/test/java/ykws/android/maro/spatial/RouteEngineChoiceTest.kt), [`RouteZonePhase4Test.kt`](../../app/src/test/java/ykws/android/maro/spatial/avoid/RouteZonePhase4Test.kt) — the `aversionKn` provider passed through the harnesses (uncommitted)

## Next Step

Commit the three test harnesses and push; then the standing owed items — Phase C's iterative calibration, the Driving-preference cursor build, and the device passes over the paging table, the early select/save and the dials.
