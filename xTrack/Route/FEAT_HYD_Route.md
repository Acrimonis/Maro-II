# Context Hydration — Route — 2026-10-04

**Last Bake:** 2026-10-04 00:38 UTC — written by `#bake`; absence means never baked

**Directive trace:** One session on `feature/avoid-speeds`, after the merge and rebase: of the five covered classes none ran unasked — no dependency was added, no machine-shaped data file was opened, work started only on the user's explicit word, the device was never touched, and every claim about the code followed a read.

## State

The curve fitter stays gone: the settled search line is the drawn and saved line, clocked by `timeLineWithLimits` with no ramp, and `limitAtFor` reads the zone limit always; the racing-line corner pass (`RouteCornerPass`) and the backward/forward speed profile (`timeLineWithProfile`) still ride that line. **The acquisition's first column names the slow water again** — `Around slow water` · `Balanced` · `Through slow water` from dedicated `route_rung_*` strings wired in `routesToCompute`, kept apart from the settings' `Fast` · `Balanced` · `Fun`. **In design, not built:** the distance-scaled hybrid grid — fine 25 m beside the coast and the depth gate, coarse 100 m in open water, at a `route.avoid.grid.fineRatio` of 4 — with its plan [`261004_FEAT_PLN_Route_hybrid-grid.md`](261004_FEAT_PLN_Route_hybrid-grid.md). The suite compiles; one test is red, `theFineCellRatioShipsAtFortyPercentOfTheCoarseCell`, which pins the old uniform grid's 40 % fine ratio and is obsolete under the hybrid design.

## Target Files

- `app/src/main/res/values/strings.xml` + `values-fr/strings.xml` — the new `route_rung_*` strings, apart from the settings' `route_computation_*`
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — `routesToCompute` reads the `route_rung_*` ids
- `app/src/main/assets/maro.properties` — `route.avoid.grid.cellM` (now 100, the open-water cell) beside the new `route.avoid.grid.fineRatio` (4)
- `xTrack/Route/261004_FEAT_PLN_Route_hybrid-grid.md` — the hybrid-grid design, in design
- (prior) `app/src/main/java/ykws/android/maro/spatial/avoid/RouteCornerPass.kt`, `RouteEta.kt`, `RouteResult.kt` — the corner pass, the profile and the drawn-line clock

## Next Step

Decide whether `route.avoid.grid.cellM` stays at 100 now or returns to 50 until the fine band lands, then run Phase 1's `cellM=25` channel check.
