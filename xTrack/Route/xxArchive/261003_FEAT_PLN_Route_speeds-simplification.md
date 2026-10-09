# 261003_FEAT_PLN_Route_speeds-simplification

Status: shipped — the per-point enforced-limit clock, the curve-fitter removal and the two-pass rework
are all in: the single-bend racing-line corner pass, then the backward/forward speed profile, with the
`route.turn.reachFraction` lever and the vMin floor dropped.

## Purpose

Every route point's speed is the limit in force at that point: the strictest speed-zone or 300 m
band limit, enforced whatever the price switch says, and the cruising speed everywhere else. No
ramp, no anticipation, no corner-speed cap.

## The machinery being retired

- `timeLineWithLimits` ramps every transition at `route.speed.accelMps2` — decelerating before the
  boundary, accelerating after — and bounds legs by the fairing's `CurveCap`s.
- `segmentTimeM` / `decelSpeedMps` — the constant-acceleration ramp.
- `routeTimedLine` — folds the fairing's cap delta into the last leg.
- `limitAtFor` reads the speed-zone limit only while `route.avoid.speedZone.enabled` is on, so a
  price switch off also drops the limit from the clock.

## The new model

1. `limitAtFor` reads the speed-zone limit **always** — the switch prices the search only and never
   suspends the limit, the band's own rule.
2. `timeLineWithLimits` re-vertexes at limit changes, then times each leg at the limit in force at
   its midpoint, capped by the pace: `time = distance / speed`, so a leg's made-good speed **is** the
   enforced limit. No ramp, no caps.
3. The curve fitter is removed entirely — the drawn line is the settled search line (`reSearched`),
   clocked directly. Bends are no longer faired into curves; that handling is a future rework.
4. `solveAtLambda` times the settled line once; the reported distance and duration read that same
   drawn line.

## Parked for later — post-processing

Once every point carries its enforced speed, one post pass may re-introduce, for a 6 m boat with
passengers: the acceleration/deceleration ramps, the anticipation before a zone boundary, and the
corner-speed caps.

## Retired / removed

`RouteCurveFitter.kt` and `RouteCurveFitterTest.kt` (the whole curve fitter), `CurveCap`,
`decelSpeedMps`, `segmentTimeM`, `capBoundMps`, `routeTimedLine`, the `caps` and `accelMps2`
parameters of `timeLineWithLimits`, and the base/faired/cap timing tail in `solveAtLambda`.

## Curve fitter removal — 2026-10-03

The user's word: **remove the curve fitter; we will handle it differently.** The settled search line
is drawn, saved and timed as-is; no bends are faired into steerable curves for now.

The four comfort keys — `route.speed.accelMps2`, `route.turn.lateralAccelMps2`,
`route.turn.transitionSec`, `route.turn.minSpeedKn` — are **disconnected and kept**: their accessors
and `maro.properties` entries stay for the future curve handling, and retirement is decided when that
handling lands.

## Two-pass rework — discussion (2026-10-03)

The "handle it differently" replacement is two post-passes over the settled search line, in order:

1. **Corner pass (geometry)** — round each snapped corner into a steerable curve (a cubic Bezier per
   corner, tangent to both legs) that **bulges outward** from the corner, which stays where it is. The
   corner already sits at the obstacle margin, so a curve that never crosses the two legs toward the
   obstacle is clear **by construction** — no wall test in the common case. It emits the drawn polyline
   and its per-point curvature; the corner speed is bounded by `v ≤ sqrt(a_lat · r)`. Where the outward
   bulge would reach a neighbouring impassable point (a wall, a shallow cell, a priced zone), the corner
   **slows to shrink the bulge** until it clears — the speed-shrinks-radius lever, now applied to the
   outward bulge instead of the old fitter's inward intrusion. Bezier gives G1 only; a quintic or a
   clothoid gives G2 (curvature continuity) at the cost of complexity.
2. **Speed pass (clock)** — a per-point speed curve that never exceeds the enforced limit at that point
   and transitions between limits with comfortable longitudinal acceleration (`a_long`). It decelerates
   before a limit drop (anticipation) and accelerates after a rise; the limit is the hard ceiling and
   comfort the soft target. A decel too short for comfort must brake harder rather than break the limit.

The two passes share one authority: the curve is the source of truth for position and curvature, and the
speed pass must not assign a speed the geometry cannot hold. The four kept comfort keys are these two
passes' inputs; the enforced speed limit stays the ceiling throughout. The common case is test-free by
construction (the outward bulge can only gain clearance), and the exception — a bulge reaching another
impassable point — is answered by slowing, never by cutting the corner.

## Racing line rework — 2026-10-03 (supersedes the Bezier corner pass)

The Bezier corner pass failed; the replacement is the **single-bend racing line**: curvature peaks at
the corner (the apex) at the lateral-G max and tapers to zero at both ends, never inverting sign.

1. One bend only: a clothoid ramps curvature `0 → 1/R`, a constant-radius arc holds the apex, a second
   clothoid ramps `1/R → 0` — the boat leans one way, easing to straight at each end.
2. The apex is the corner, where lateral G is at its max `a_lat`; the corner speed is `v = sqrt(a_lat · R)`.
3. The turn length is a new lever `route.turn.reachFraction` (0..1): how much of the half-segment each
   side of the apex uses — 1.0 curves all the way to the neighbouring apex's midpoint, less leaves a
   straight run between.
4. Clearance (coast, depth, zone) shrinks the radius where the inward bow would foul.
5. The speed pass is unchanged.

## Outcome

Shipped 2026-10-03: the curve fitter is removed entirely and the settled search line is the drawn and
saved line, clocked directly — `timeLineWithLimits` re-vertexes at limit changes and times each leg at
the limit in force at its midpoint, no ramp and no caps, and `limitAtFor` reads the zone limit always,
whatever the price switch. The two post-passes land as the single-bend racing-line corner pass — a
clothoid–arc–clothoid whose curvature peaks at the corner at the lateral-G max and tapers to zero, the
corner speed `v = sqrt(a_lat · R)` and the turn length the `route.turn.reachFraction` lever — followed
by `timeLineWithProfile`, a backward/forward two-pass that anticipates deceleration and bounds
acceleration, the enforced limit the hard ceiling. The vMin floor was dropped in the final fix: a tight
corner lowers its speed to fit the largest radius its legs allow instead of staying sharp.
