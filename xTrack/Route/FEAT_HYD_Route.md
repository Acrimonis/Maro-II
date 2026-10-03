# Context Hydration — Route — 2026-10-03

**Last Bake:** 2026-10-03 23:31 UTC — written by `#bake`; absence means never baked

**Directive trace:** Two sessions merged — `feature/avoid-speeds` (the speeds rework) rebased over the develop line's dashed saved-route rendering and the R95 ladder: of the five covered classes none ran unasked — no dependency was added, no machine-shaped data file was opened, work started only on the user's explicit approvals, the device was never touched, and every claim about the code followed a read.

## State

The curve fitter is gone: the settled search line is the drawn and saved line, clocked by `timeLineWithLimits`, which times each leg at the enforced limit in force at its midpoint with no ramp, and `limitAtFor` reads the zone limit always, whatever the price switch. Two post-passes ship over that line — `RouteCornerPass` rounds each snapped corner into a single-bend clothoid–arc–clothoid racing line whose curvature peaks at the corner at the lateral-G max, the corner speed `v = sqrt(a_lat · R)` and the turn length the `route.turn.reachFraction` lever, then `timeLineWithProfile` times the drawn line with a backward pass that anticipates deceleration and a forward pass that bounds acceleration, the enforced limit the hard ceiling. Clearance shrinks the radius where the inward bow fouls, and the vMin floor was dropped so a tight corner lowers its speed to fit the largest radius its legs allow instead of staying sharp. The rebase merged the develop line's R95 ladder — Fast · Balanced · Fun ordered by ETA — and the dashed saved-route rendering (`map.track.width.route.dashOn` / `dashOff`), whose device pass is still owed. Build green and the full unit suite green.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/avoid/RouteCornerPass.kt` — the racing-line corner pass: clothoid–arc–clothoid, apex at the corner, reach and clearance bisection, the per-point ceiling
- `app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt` — `timeLineWithProfile` and the simplified no-ramp `timeLineWithLimits`
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — `solveAtLambda`'s two post-passes and the always-on zone limit in `limitAtFor`
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` + `app/src/main/assets/maro.properties` — `routeTurnReachFraction`, the comfort keys, and the dashed-route keys
- `app/src/main/java/ykws/android/maro/data/model/RouteResult.kt` — the duration/leg docs rewritten for the drawn-line clock
- `app/src/test/java/ykws/android/maro/spatial/avoid/RouteCornerPassTest.kt`, `RouteSpeedProfileTest.kt` — the two new suites
- `app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt`, `RouteZonePhase4Test.kt` — the relaxed clearance tolerance and the clock assertions
- `RouteCurveFitter.kt` + `RouteCurveFitterTest.kt` — deleted
- `MapTrackOverlayEffects.kt`, `MapTrackSegments.kt`, `TrackRouteRoleTest.kt`, `TrackOutlineTest.kt` — the dashed saved-route rendering merged in from develop

## Next Step

The device pass — a saved route dashed beside a recording and the rounded corners on water — with D4 parked under the closed walk.
