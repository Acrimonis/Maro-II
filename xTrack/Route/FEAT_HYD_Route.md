# Context Hydration — Route — 2026-10-03

**Last Bake:** 2026-10-03 11:30 UTC — written by `#bake`

**Directive trace:** This session built the ladder on the user's explicit words: the two `#impl` runs (Phase F, then the candidate-pass retirement), the rung reorder, the three-stop Settings cursor, the tolerance collapse and the repaint fixes, each on an order; the `#commit` (`5e6211fa`) and this `#bake` followed the user's own sequence. Of the five covered action classes none was met unasked: no dependency was added, no machine-shaped data file was opened, no file was written without the order, the device was never touched, and every git write was named by the user.

## State

**`feature/avoid-more` carries the three-route ladder.** The engine declares three fixed-aversion rungs — around (λ=5), balanced (λ=2.5) and through (λ=0), most-fun first — over one `Deferred`-cached grid built once per arm ([`RouteAvoidEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:207)); each rung is a full solve at its own λ, the forced-crossing growth and the fine pass retained, the budget loop gone. `RouteViewModel` maps the Driving-preference cursor (`softCostAversion`) to the initial rung, folds any rung within `route.avoid.ladder.collapse.toleranceM` (default 25 m, shipped 75) into a marked survivor via `routeDispersionM`, and re-seats the selection on the nearest survivor.

**The candidate-pass apparatus is retired** — its maro.properties keys, `AppConfig` accessors and parser, and the whole `RouteOffer` model are gone; the ladder declares its rungs directly. The Routing Tuning block now carries the free-water pace and a three-stop **Driving preference** cursor, the slow-water budget slider removed.

**Verification.** `gradlew.bat :app:assembleDebug :app:testDebugUnitTest` BUILD SUCCESSFUL, the whole unit suite green. Nothing device-validated: the map pool is sized to the rungs and old lines cleared on repaint, but the three-rung draw and the collapse's double-display fix still want a device pass.

## Target Files

- [`RouteAvoidEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:207) — the three rungs, the shared `GridContext`, `searchRung`/`solveAtLambda`
- [`RouteViewModel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:343) — the cursor→rung initial selection, the tolerance collapse, the survivor mark and the look-up remap
- [`RouteOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:131) — `ROUTE_LADDER_RUNG_COUNT`, `routeRungIndex`, `routeRungLambda`, `routeDispersionM`
- [`RouteHost.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt:122) — the pool sized to the rungs; every unused line disabled and emptied
- [`RouteConfirmPanel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:364) — the collapse note folded into the route's own row
- [`MapScreenSettingsOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1470) — the three-stop Driving-preference cursor
- [`AppConfig.kt`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:336) — `routeAvoidLadderCollapseToleranceM`
- tests: [`RouteAvoidEngineTest.kt`](../../app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt), [`RouteZonePhase4Test.kt`](../../app/src/test/java/ykws/android/maro/spatial/avoid/RouteZonePhase4Test.kt), [`RouteAcquisitionTest.kt`](../../app/src/test/java/ykws/android/maro/ui/map/RouteAcquisitionTest.kt)

## Next Step

The device passes over the three rungs, the paging table and the collapse. Then the deferred second retirement family — the engine's ignored `aversionKn`/`slowWaterBudgetPct` params, the test-only `betterPass`/`PassCost`/`fineSpliceBetter`, and the dead slow-water budget chain.
