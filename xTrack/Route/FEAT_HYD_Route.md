# Context Hydration — Route — 2026-10-04

**Last Bake:** 2026-10-04 16:35 UTC — written by `#bake`; absence means never baked

**Directive trace:** This session landed Phase 6 of the adaptive grid, then ran a device-led hunt for speeds
that read wrong, so of the five covered classes two were touched: orders were given and acted on throughout
(`#impl phase 6`, then the fix directives), and the device was read only on the user's own word — the logcat
fetched after he reported each run. No dependency was added and no machine-shaped data file was opened. Every
claim about the code in this record followed a read except the suite and build figures, which rest on the
hops' own runs — named here, not closed.

## State

**Phase 6 landed** — the drawn tail reads the **local** resolution. [`WalkWindows.cellSizeAt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt:289)
answers the finest layer's cell where passable water stands under a point, and the four sites read it:
`CornerSet.radiusM` became a per-point function, the ends' `carveReachCells` follow each end's local cell,
`openEndDisc` takes each window's own cell, and the snap guard is built at the fine cell. `avoid`'s
single-grid answers stay cell for cell.

**Then the speed hunt found four unrelated faults, in four layers, and all four are fixed.** Each looked like
the same symptom while being its own defect:

- **The spiral's maths (geometry).** [`RouteCornerPass.composite`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCornerPass.kt:209)
  advanced the heading at `τ²/2` — right for a clothoid — but built the position from the Fresnel pair at
  `τ` where the heading implies `∫cos(σ²/2)dσ`, so every bend bowed **twice as sharply as its own heading**.
  The too-deep bow inflated the tangent the fit measures, so corners a true clothoid fits were rejected and
  pinned to `route.turn.minSpeedKn`. The pair is now read at `τ/√2` and scaled by `√2`.
- **Two keys five times below their defaults (settings).** `route.turn.lateralAccelMps2` was **0.2** against
  its comment's and the code's **1.0**, and `route.turn.reachFraction` **0.20** against **1.0**; both
  restored. The radius is `v²/a_lat` and the room is `reachFraction × half-leg`, so both were ~25× out
  together and no bend could fit.
- **The saved route's label, one leg out of step (storage).** [`TrackFromCourse.build`](../../app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt:83)
  gave every vertex the speed of the leg **arriving** at it, while its own class doc promises the **outgoing**
  leg and the test's own comment says "the leg leaving it". The first vertex inside a zone therefore wore the
  open-water leg behind it and a saved route read **30 kn inside a 10 kn zone**. The code now matches its doc,
  and the one stale assertion was corrected to the intent its comment states.
- **The leg's timing (arithmetic).** [`timeLineWithProfile`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt:314)
  timed each leg from its two end vertices alone, and as one uniform ramp. A leg with a floor bend at either
  end therefore held that floor across **679 m**, and a 5→10 kn leg over 734 m claimed the climb lasted the
  whole leg. Each leg is now timed by what the boat does **along** it — climb to the leg's own limit, hold,
  descend — with the triangular profile where the two climbs meet.

**The reading that carried it was `OVER LIMIT n=0` on every route**: no leg's made-good speed ever exceeded
the lowest limit along it, so the plan was never the fault and each remaining symptom had to be its own layer.
`UNDER LIMIT` then showed a bend's **5 kn floor** at the ends of 7–24 m legs (the band's edge) and the
mean-based figures over 186–893 m legs.

**The gate is the suite, as the hops ran it** — `apk-build.bat` green and the full unit suite at
`881 / 1 / 10`, the single red still `theFineCellRatioShipsAtFortyPercentOfTheCoarseCell`, `avoid`'s parked
ratio residue and the user's to settle. Every temporary instrument (LEG / UNDER LIMIT / OVER LIMIT traces) has
been removed.

**Still open, by decision**: the bends themselves keep the 5 kn floor at the vertex, which is physically right
— a bend between 7–24 m legs has no room to fair — and whether that floor should rise is a sailing preference
(`route.turn.minSpeedKn`, clamped 2–10, set to 5). A device pass confirming the new figures is owed.

## Target Files
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteCornerPass.kt` — the spiral's corrected Fresnel argument
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt` — each leg timed by the boat's own climb, hold and descent
- `app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt`, `app/src/test/java/ykws/android/maro/data/track/TrackFromCourseTest.kt` — the saved vertex's speed, and the assertion that pinned the old direction
- `app/src/main/assets/maro.properties` — `route.turn.lateralAccelMps2` and `route.turn.reachFraction` restored to 1.0
- `app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt`, `RouteGridBuilder.kt`, `RoutePassRunner.kt`, `RoutePassModels.kt` — Phase 6's local resolution
- `xTrack/Route/261004_FEAT_PLN_Route_hybrid-grid.md` — Phases 1–6 landed; Phase 7 (a measurement decision) and Phase 8 (the record) remain
- `route-speed-curve.svg` — a scratch curve of one run's per-leg speeds, at the repo root

## Next Step
A device pass on the restored settings and the new timing, reading `LINE`, `PULL` and the route rows: the
open-water speeds should sit at the pace, a leg between two floor bends should read near its limit, and the
saved route's colours should change where its water changes.
