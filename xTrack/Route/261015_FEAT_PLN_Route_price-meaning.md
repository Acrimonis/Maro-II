<!-- scope: feature -->
# Route — the price: its unit, and what it is worth

**Date:** 2026-10-15 · **Status:** in design, for discussion · **Order:** the user's word of 2026-10-15 —
*"how to improve the price? Assess plan review report"*, after *"One seem better than the other. Why the
delta? Can't we improve the route? So it is more consistant?"*

**Scope:** the seconds the search and the pull's guard charge for slow water — their unit, their arms, and
which of them the clock can see. It is the precondition for any consistency work on the ladder, and it
sits beside [`261014_FEAT_PLN_Route_acquisition-perf.md`](261014_FEAT_PLN_Route_acquisition-perf.md) and
[`261014_FEAT_PLN_Route_pull-lookback-pass.md`](261014_FEAT_PLN_Route_pull-lookback-pass.md).

## What is there today

- **Every soft price is a per-cell cost in seconds, scaled by the pass's λ.** `zonePriceSec` is
  `cellM × (1/v(limit) − 1/v(pace)) × k` — the cost of crossing **that cell** at the limit instead of the
  pace ([`RouteCostField.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:129)) —
  and `DepthBandLaw.priceSec` is its own mirror, "the seconds a cell of size `cellM` carrying coefficient
  `coef` costs at cursor `k`" ([`RouteFineWater.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFineWater.kt:168)).
  The rasterizer writes that per-cell cost and the A\* adds it once per cell entered, which is why the
  search costs in real time.
- **Four arms price one point, the dearest winning**: a ring's interior and its outside collar, the band's
  own width and its collar, and — on `selective` alone — the **depth band**, a per-metre ramp toward the
  shallow wall whose coefficient is λ-free and dimensionless and becomes seconds only at the pass's
  cursor ([`RouteCostField.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:179)).
- **The pull's guard prices with the same law, and mislabels it.** It sums `price × marks.widthM(j)`
  ([`MultipassPull.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:625))
  where `price` is a per-**cell** cost and `widthM` is in **metres**, so every figure it produces carries
  an extra factor of the field's cell — and its KDoc calls the sum "seconds".
- **Nothing in the clock can see the depth band.** `slowShares` splits the drawn line's slow time into
  ring, band and ramp; the depth price is not one of them, and it is not a limit, so it never appears in
  the duration a user reads.

## The assessment

- **The unit mislabel is real, and it is verdict-neutral.** Both sides of the guard's comparison are
  inflated by the same field cell, so `chord × cell` against `path × cell` decides exactly what
  `chord` against `path` would: no line and no verdict moves. `chordSec=15925.154` from the 2026-10-15
  pass is therefore ≈ 796 s of true price at the fine guard's 20 m cell, not 15 925 s — the "hundred
  times the route's slow water" read was that factor, and it was mine to make and mine to retract.
- **The price is nevertheless doing real work, and the depth arm is what dominates.** On that pass the
  `balanced` rung's last bend kept with `pathSec=0.000` and `chordSec=15925.154` reads, in true seconds,
  legs that pay nothing against a chord that pays ≈ 796 s — a chord running along the shallow wall's ramp
  where the legs stay clear of it. A depth price of ≈ 796 s against a clock difference of 12.7 s is the
  whole reason the bend survives the sweep and reads as arbitrary in the panel.
- **So "improve the price" splits in two.** Its *unit* needs to be true seconds so the figures are
  readable and comparable with the clock; its *meaning* needs the depth arm to have a standing — a clock
  counterpart, an attribution arm, or a named preference.

## The change, least risk first

1. **P1 — the unit.** Make the pull's price walk sum true seconds: divide the per-cell price by the
   field's own cell, at one home, so both the guard's threshold and every reported figure are seconds.
   It cannot move a verdict, since it scales both sides by the same positive constant, and that is the
   claim to pin first.
2. **P2 — name the price by arm.** Report the guard's two sides per arm — ring, band, depth, marker — so
   a kept bend names the arm that kept it rather than a number. The `SWEEP` lines already carry the two
   sides; this adds the split.
3. **P3 — give the depth arm a standing.** Decide between letting the clock count it (so the search
   minimises the time the panel shows), adding a fourth attribution arm (`slowShares`), or accepting it
   as a preference and naming it as one in the corpus. Each has a falsifier: the first changes drawn
   lines, the second changes only display, the third changes nothing and must then explain why a bend
   worth 12 s of clock is kept for 796 s of price.
4. **P4 — the scale, once the unit is true.** `route.selective.depth.priceSecPerM` and the band's width
   together set how many seconds of detour the search will pay for clearance. Publish that reading
   (seconds per metre of wall distance) so the value can be judged as a preference rather than a number.

## Feasibility and risks

- **High** for P1 and P2: P1 is arithmetic at one home and provably verdict-neutral; P2 is a trace line
  over an existing reading.
- **Medium** for P3: the clock-counting option changes the drawn line and the ranking with it, and the
  attribution option adds a user-facing field whose strings must exist in both locales.
- **The real brake is the same as the perf plan's**: every step whose subject is a *reading* waits on a
  device pass, and R97 makes that pass the user's own.
- **The honest null case:** P1 changes nothing a user sees, so on its own it improves only the record. If
  P3 lands on "a named preference", the delivery is a decision and a doc line, not a behaviour change.

## Phases

1. **P0 — pin the unit.** A fixture asserting the guard's two sides equal the same sums divided by the
   field's cell, and that every recorded verdict is unchanged by the scaling.
2. **P1 — the true-seconds sum.** Land it at one home and re-run the scoped suites.
3. **P2 — the per-arm split** on the guard's two sides, with the fixture that witnesses it.
4. **P3 — the depth arm's standing**, decided on the record with its falsifier stated.
5. **P4 — the scale's reading**, published beside the key.

**Acceptance:** no line, clock or ranking moves without P3's explicit decision; every price figure on the
trace is seconds; a kept bend names the arm that kept it; no dependency is added.

## Review of this plan, against itself

- **Its strongest objection:** P1 improves readability and nothing else, and readability is not what the
  user is short of — a bend worth 12 s of clock is kept by a price, and fixing the label does not change
  that. The plan is honest about it, but P1 must not be mistaken for the fix.
- **A second:** P3's clock-counting option is a behaviour change that would move lines and the ranking,
  which contradicts the "no regression" standing of the previous two plans; it must be the user's word,
  not an inference from this plan.
- **A third:** the per-arm split in P2 assumes the arms are separable at a point; where two overlap the
  field already takes the dearest, so the split must report the winning arm rather than a naive sum.

## Implemented

- **The marker arm shipped 2026-10-16 — P2, narrowed to the arm that was missing.** A marker-priced point now
  names itself: the price's own `Soft` source in [`markerSources()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteMarker.kt:168)
  declares `armAt = { p -> PriceArm.MARKER to markerPriceSecAt(p) }`, its money read from the **same local price lambda**
  that feeds its `priceSec`, so the arm it reports can never disagree with the seconds it charges and
  [`armPricesAt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:351)'s
  sum-to-the-total holds by construction. A marker-priced point therefore reads `marker=` on the pull's
  `SWEEP arms` line beside `band` · `zone` · `depth` where it was lumped into `other`, and the per-mark line
  names the same arm — no drawn line, clock, verdict, string or properties key moved, and the wall still
  declares none, being law rather than a price.
- The witness is [`RouteMarkerCostTest`](../../app/src/test/java/ykws/android/maro/spatial/multipass/RouteMarkerCostTest.kt:176):
  a priced point reports `MARKER` alone carrying its own seconds and no `OTHER`, the arms sum to what
  `softPriceSecAt` answers, a point beyond every collar reports none, and the switch off reports none at all.
  The unit suite and the packaging are green at the hop.
- **Untouched by this hop:** the unit (P1), the depth arm's standing (P3) and the scale's reading (P4) stand
  exactly as the plan left them.
- **The review's named limits, recorded rather than fixed:** a point under several priced markers sums them
  into that one arm, so the report names **a** marker and not which of them; and the fine tile's own field
  declares the arm although a tile stores a pace-free factor and no arm is read there. `PriceArm.OTHER` now
  has no production producer, its remaining role the honest default for a future source that does not
  decompose.
- **The presence fact shipped 2026-10-16 — the decision the forced-crossing reader was waiting on.** The report
  now speaks **presence** beside the price: [`forcedCrossingNames`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:811)
  keeps filtering on the price — a zone its `speedRoute` prices to nothing is still never named as a forced
  crossing — while a **second, independent read** names the water the line crossed:
  [`crossedSlowZoneNames`](../../app/src/main/java/ykws/android/maro/spatial/multipass/ZoneGeometry.kt:232)
  answers the zones the drawn line **enters** whose **own limit in force is below the cruise pace**, read off the
  **displayed** limit ([`SpeedZone.speedLimitKn`](../../app/src/main/java/ykws/android/maro/data/regulation/SpeedZone.kt:29),
  the field [`strictestDisplayLimitKnAt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/ZoneGeometry.kt:92)
  reads and through it `MultipassWorld.zoneDisplayLimitKnAt`) and never `routeLimitKn` and never the price. The
  two functions share one entry test, so neither can disagree about which water the line entered, and a zone at
  or above the pace is named by neither — it costs no second and shows in no colour.
- It rides [`RouteResult.Success`](../../app/src/main/java/ykws/android/maro/data/model/RouteResult.kt:98) and the
  plan as a **record and not a drawn fact** — no proto, the two saved-route constructions in
  [`RouteOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:509) leave it empty, the
  loss mirror carries it unchanged (R99) — and prints as `crossed=[…]` beside `forced=[…]` on the engine's own
  answer trace, so a device reading tells the two facts apart at a glance.
- The witness is [`RouteZonePhase4Test`](../../app/src/test/java/ykws/android/maro/spatial/multipass/RouteZonePhase4Test.kt:330):
  a zone the price calls free is entered and never forced, a zone at or above the pace is named by neither, and a
  zone genuinely crossed with no way around is **both** — plus the engine's
  [`theZoneLimitSlowsTheClockWhateverThePriceSwitchSays`](../../app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt:566),
  where `route.avoid.speedZone.enabled` off still names what the clock times. Unit suite and packaging green at
  the hop. Two stale claims that a surface read the report — `RouteResult.Success`'s own KDoc and the panel's
  column-3 comment — now state the truth: **no composable prints the report**, no surface was invented and no
  string added; the words for one are the user's own, and were deliberately not part of this hop.
- **Untouched by this hop:** the unit (P1), the per-arm split's remaining arms (P2), the depth arm's standing
  (P3) and the scale's reading (P4) stand exactly as the plan left them.
