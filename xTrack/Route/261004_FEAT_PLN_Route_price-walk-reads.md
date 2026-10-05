<!-- scope: feature -->
# 261004_FEAT_PLN_Route_price-walk-reads

Topic: **the price walk's own reads** — the pull's priced half stops paying a live read at every 25 m
interval, behind a proof the soft sources themselves declare.

Status: **Phases 1 to 3 and 4b landed** (2026-10-05, `#implement`) — reviewed before the build, and the review's
findings folded in: a zone's **holes** are boundaries the declaration must cover, a mark also paid the **hard
walls' test**, the guard's unit is named, and a group's midpoint is pinned to the fine grid. **Phase 4's device
reading is taken** (2026-10-04, [`route-phase8.txt`](../../route-phase8.txt:1)) and what it says is that the cut
lands exact and **saves nothing yet**: `priceMs` still holds 80 % of the pull and `priceReads` equals the mark
count this plan's own model predicts, because the step a priced rung hands the walk collapses the grouping to
one interval a group. **Phase 4b landed (2026-10-05)** — the step's own value, the cut that reading named:
[`RoutePassRunner`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt:163) now
answers it through the pure `priceStepFor(walk)`, both steps travel as required parameters, five tests pin it,
and the build is green. **Phase 5, the aligned shared grid, stays the user's call.** **The saving's capture was closed unread on the user's word of 2026-10-05** — it confirms no future feature — while **the one test that drives `runPass` itself stays owed**. Written on the order of the feature's own
work list, whose step 4 says the plan comes first: the price walk is **80 % of the pull** and the pull is the
solve's cost centre.

Placement: the work is the shared `multipass` layer's, so **both engines** take it and only the step
travels per engine. The grid, the search, the corner pass, the clock and the drawn line are untouched —
what changes is *which marks pay the price read*, never which marks exist.

## Why

The device reading of 2026-10-04 puts the price where the route's seconds live, on the same pair and
paces as [`route-phase7.txt`](../../route-phase7.txt:1):

- `PULL ms=17303.1 clearMs=3470.6 priceMs=13828.2` at λ = 2.5, its twin rung at 17 970.7 with 14 332.6
  price — so the coarse-marks cut did its part on the clearance half and **the price half now owns 80 %
  of the pull**.
- The instrument's own home is [`PullTiming`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:376):
  `clearanceNanos` and `priceNanos` are told apart, so the win this plan buys is read on `priceMs` alone.

**The cost model, from the loop itself.** [`softPriceSec()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:294)
walks one chord at `marginM / 2` = **25 m** intervals (`route.avoid.obstacle.marginM` = 50, the shipped value
— this line's *25 → 12.5 m* reading was stale and is corrected 2026-10-05), placing a read at each interval's
midpoint:

- the two-pointer evaluates `O(n²)` chord candidates, so the read count is `Σ over chords (chordM / 25)`
  — the same `n² / 2` volume the mark-count item names, measured through the price;
- the prefix [`softPricePrefix()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:308)
  adds one chord walk per path segment, and the path is the raw cell chain, so ~2 reads a point on the band's
  20 m cells and ~9–12 on the 100 m interior chain — the cheap half of the same bill;
- beside the pull, [`snapToCorners()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:150)
  asks **four** priced segments per corner candidate and
  [`RouteFinePass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:30)
  sums the same walk over the fine points, so the price is paid at three sites, not one.

**What one read costs.** `field.evaluate(p).softCostSec` is the field's own sum
([`evaluate()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:267)), which
for the shipped field is **one** soft arm —
[`costField()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:62)
combines the band's price and the rings' with `max`, never a sum:

- the band's arm calls [`world.distanceToCoastM`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt:50)
  — **the same live coastline index query the clearance walk pays** (≈33 µs a mark, the clearance plan's
  own figure), through [`bandPriceAt()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:185);
- the rings' arm calls [`strictestLimitKnAt()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/ZoneGeometry.kt:64)
  (even-odd containment over every zone) and
  [`speedZoneCollarLimitKnAt()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/ZoneGeometry.kt:80)
  (point-to-segment over every zone's outer ring);
- and the same call walks the **hard** sources first: [`evaluate()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:268)
  loops every source for `blocked`, so a mark also pays the depth gate's `world.depthAt` sample wherever the
  gate is on — a read whose verdict [`softPriceSec()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:302)
  then throws away, since it reads `softCostSec` alone. That is the third cost this cut can strike out, and
  the plan below does.

**The reading this plan exists because of**: a priced rung's price marks are, by construction, *the same
points* the clearance walk already samples — and the clearance walk has just learned to spare its read
behind a proof. Nothing equivalent exists for the price, so the price half pays in full what the
clearance half no longer does.

## The rule

**The price walk's mark set does not change. Only which intervals pay a read of their own changes — and
the proof is declared by the sources it protects, never inferred by the walk.**

- **The marks stay exactly what they are**: the fine grid is `marginM / 2` along each chord, each interval
  priced at its own midpoint as
  [`softPriceSec()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:294)
  places them today. No phase, no spacing and no count moves — which is what makes the equivalence below
  exact rather than approximate.
- **Consecutive fine intervals group into one coarse interval**, whose length is a whole number of fine
  intervals, so a group's price is a multiple of the fine grid's own `stepM` and the sum's partition is
  refined rather than replaced. **The price step is the walk's interior cell — 100 m on both engines → `k = 4`
  at the shipped margin (`marginM` 50, so the fine interval is 25 m), a 50 m proof radius — and never the
  walk's local cell**, which the measurement of 2026-10-04 showed to be the band's own 20 m on `evolutive`,
  collapsing the quotient to 1. The clearance walk keeps the local cell it already threads per engine, and the
  two steps travel together as required parameters.
- **One read prices a whole group where it is proved to hold one price.** A group is a whole number `k` of
  the fine grid's own intervals — whose length is `dist / steps`, never a nominal 25 m — so its read stands
  at `chordPoint(a, b, (i0 + k / 2) / steps)`, the group's own midpoint on that grid, and its half-length is
  `k × stepM / 2`. The fine midpoints inside it are then skipped.
- **A proved group's hard test is skipped with it, and no verdict moves.** The depth gate is a *step*, so no
  bound proves a cell inside a group clear of it — and none needs to: the price walk's `evaluate` call
  discards its `blocked` flag, while the walk that decides the margin is
  [`legClearCause()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:211) and
  keeps every mark of its own. Skipping the test is a saving, never a permission.
- **Every soft source declares where its own price changes** — [`priceClearanceM()`](#the-declaration) in
  metres, the distance from a point to the nearest place that source's arm changes. A group whose midpoint
  reading `c` satisfies `c ≥ groupLength / 2` holds **no** such boundary inside it, so every point of the
  group lies in the same arm and the group's price is that one reading, exactly.
- **Where it is not proved, today's read happens**: the fine midpoints are walked as they are today, at
  today's positions, in today's order. Near the band's edge and inside a collar the walk is exactly
  today's.
- **The band's own read does double duty, and proving a group still costs one read.** Its boundary clearance
  is a function of the coast distance the arm reads (`min(|d − 300|, |d − 350|)`) — but the declaration is a
  **separate live query at the group's midpoint**, never a reuse of the price read, so a proved group pays
  **one** coast read to prove itself and then one for its price, against the `k` fine reads it replaces: a win
  of `k − 2` a group, not the `k` the first model claimed. The landed hop states this as its one deviation, and
  `priceReads` carries the figure the measurement will price.
- **Both steps are required, never defaulted**: the price step travels as its own required parameter beside
  the clearance step [`legClearCause()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:211)
  takes as `coarseStepM`, and this corpus has already ruled that a defaulted step re-arms the defect the
  first time a caller forgets.
- **No plan member, no property key and no new vocabulary**: the thresholds are the shipped keys the law
  already reads, and the step is a parameter.

## The price error the coarsening accepts

**Zero — and it is a proof, not a hope.**

- A proved group's contribution is `price(groupMidpoint) × groupLength`, and the fine grid's own sum over
  the same group is `Σ price(midpoint_i) × stepM`; the arm is constant over the group by the clearance
  inequality, so the two are **the same number**, not two numbers close together.
- **The unit is the guard's own, and it is named here**: the law answers a **cell's** excess in seconds
  ([`zonePriceSec()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:108) is
  `cellM × (1 / v(limit) − 1 / v(pace)) × k`) and the walk multiplies it by the interval's **metres**, so the
  sum is seconds-times-metres — the same construction on both sides of `chord > replaced`, which is what makes
  the comparison well-posed and the zero above unit-free.
  [`softPriceSec()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:294)'s own
  KDoc calls the law *the seconds a metre costs*, which is not what `zonePriceSec` returns; the cut repairs
  that sentence as it touches the function.
- An unproved group's contribution is today's fine sum, unchanged.
- So the coarsened price of a chord equals today's price of that chord **identically**, and the guard's
  verdict ([`priceRefusal()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:267),
  `chord > replaced`) cannot move: a chord refused today is refused, a chord pulled taut today is taut.
- The statement is in the guard's own unit and is the guard's own arithmetic: **Δ price = 0**, so the
  guard is not relaxed and no tolerance enters the comparison.
- **The one relaxation found is named and left to the user**, because it moves the drawn line: aligning
  the price's midpoints onto the fine marks — so **one** coastline read serves the clearance and the price
  at the same point instead of two reads half a step apart (see *The second lever* below) — replaces the
  partition and therefore **accepts an error of up to one interval's price**, `price × stepM`, per group
  whose arm changes across the moved sample. It is not this plan's recommendation and it is not taken
  here; it is written down because the reading may yet make it the larger win, and because its error is a
  number the guard would have to be given.

## The declaration

**A soft source declares the distance to its own price boundary.** One member on
[`RouteCostSource.Soft`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:62):

- **`priceClearanceM(p)`** — the metres from `p` to the nearest point at which this source's price arm
  changes, `Double.MAX_VALUE` where it declares none. A source that declares none can never be proved,
  which is the honest default and the shape the inert phase ships.
- **The field answers the minimum over its soft sources**
  ([`RouteCostField`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:247)),
  the same shape [`hardDistanceM()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:308)
  already has for the materialized walls — so the walk asks one number and the sources stay the only ones
  who know their own law.
- **The contract is the hard sources' contract, restated**: a declared boundary must be a **true distance**
  to a real boundary of the arm, and **every** boundary of every arm belongs in the declaration — a hole in
  a ring is a boundary of the price, not a detail of the geometry. A source that changes its price anywhere it
  did not declare is a silent defect, exactly as a non-metric hard wall is.
- **It costs a read of its own, and the win is read net of it.** The band's clearance is arithmetic off the
  coast distance the arm reads, but as **its own** query at the group's midpoint — one read to prove, one to
  price, against the `k` fine reads replaced. A ring's is dearer: the shipped collar walk reads the **outer
  ring's** segments alone ([`speedZoneCollarLimitKnAt()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/ZoneGeometry.kt:80)
  loops `zone.outerRing`), while the arm also toggles on **every hole's** ring
  ([`contains()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/ZoneGeometry.kt:54)), so the
  declaration owes a point-to-segment walk over the holes too. A source for which nothing is free pays its own
  read, and must say so — and a group only ever breaks even where `k` exceeds what proving it costs.

Shipped declarations:

- **The band** — boundaries at `route.avoid.zone300`'s own two circles: the coast distance at
  **300 m** (`world.bandWidthM`, `CoastlineRepository.ZONE_DISTANCE_M`) and at **350 m**
  (`bandReachM(bandWidthM, outsideMarginM)`, the shipped `outsideMarginM` 50), so
  `min(|d − 300|, |d − 350|)`. The arms are `fullSec`, `fullSec × 0.66`, and 0.
- **A speed zone** — boundaries at the **outer ring** and at **every hole ring** (distance 0, containment
  toggling: the interior arm holds only where
  [`contains()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/ZoneGeometry.kt:54) does, so a
  hole's edge switches it back to 0) and at the **collar** edge (the shipped
  `route.avoid.speedZone.outsideMarginM` = 100), per zone — the minimum over the outer ring and each hole of
  `ringDist` and `|ringDist − 100|`, then the strictest-limit rule over the zones. The arms are the interior
  excess, the collar excess × 0.33, and 0.
- **The parked marker weights** (the avoid algorithm's Phase 5) become a third declaration the day their
  law ships — a circle's radius or a corridor's half-width is exactly a boundary distance — which is the
  one-home argument for building the member now rather than a band-shaped special case.

## The cut — where it lands

- [`softPriceSec()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:294)
  takes the price step as a **required** parameter, groups the fine intervals into whole price steps
  intervals, reads each group's midpoint, and asks the field's clearance at that reading before it
  honours it; the group's product replaces its fine walks where proved.
- [`softPricePrefix()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:308)
  and [`priceRefusal()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:267)
  inherit it, so the chord's price and the span it replaces are coarsened by the **same** rule — the two
  sides of the guard's comparison must never be built on two different partitions.
- **The two other sites are sites in their own right**:
  [`snapToCorners()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:150)
  asks four priced segments per candidate and its verdict decides where a bend lands, and
  [`RouteFinePass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:30)
  prints the fine pass's own price; both take the same step and inherit the same proof.
- **The step is threaded from the callers**: [`RoutePassRunner`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt:163)
  answers the pure `priceStepFor(walk)` — the walk's interior cell, never its fine one — and hands it beside
  the clearance step.
- **One grid, one floor**: `softPriceSec` samples at the raw `marginM / 2` today while
  [`clearanceStep()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:149)
  floors the same quantity at `MIN_SAMPLE_STEP_M`; the cut routes both through the one home, so the two
  walks can never disagree about the fine grid's own length.
- **The hard walls' verdicts stay exactly as they are, and one of their reads stops being paid**: the depth
  gate's step still decides the margin in `legClearCause`, at every mark, and the coastline's distance still
  belongs to the clearance walk's own coarsening. What the price walk stops paying is the **duplicate** test
  it makes through `evaluate` for a flag it discards — asked instead through a price-only read on the field
  ([`RouteCostField`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:247)),
  whose body is the price sum and whose `evaluate` becomes that body plus the hard loop, so the law keeps one
  home and the walk asks it one question.

## The second lever, named and parked

- Today the clearance walk's marks stand at `i / steps` of a chord and the price walk's at
  `(i + 0.5) / steps` — the **same 25 m grid, offset by half an interval**. So the coast read a proved
  price group makes is a *second* read of a point a mark stands beside, not a shared one.
- Aligning the price's interval midpoints onto the fine marks, and letting both halves read one coast
  distance at that point, would halve the coast reads — but it replaces the Riemann partition and so
  **accepts a stated error** (up to one interval's price), unlike the proof above. It is a separate
  decision, on the user's word, and its own reading should come first.

## What must not move

- **The verdicts and the line**: the guard's comparison, the chords it refuses and the line it pulls —
  Δ price 0 by construction, so the drawn line, the distance, the ETA and the drawn tail are unchanged.
- **The price law**: `bandPriceAt`'s three arms, `zonePriceAtLimits`' two, the `max` that stops two
  sources double-charging one cell, and the `λ` a pass prices with — all read, none re-derived.
- **The clock**: `limitAtFor` is λ-free and pays no price; nothing here touches it.
- **The hard walls**: the depth gate's per-mark step and the coastline's distance keep the clearance
  walk's own coarsening and nothing else.
- **The grid**: the rasterizer, the layers, the windows, the estimator and the ceiling are the fine
  layer's business; this plan reads them and moves none.

## Phases

1. **The declaration, provably inert** — `priceClearanceM` on the soft source, `MAX_VALUE` everywhere, the
   field answering the minimum, `softPriceSec` grouped with the coarse step a required parameter and
   every caller threading **today's** fine step, so every group is refused the proof and the read set is
   exactly today's. The whole suite green with no assertion changed is the phase's exit. **Done** (2026-10-04),
   its exit held as the hop's own checkpoint.
2. **The proof, per site** — the grouping and the skip in `softPriceSec`, the prefix, the guard, the
   corner pass and the fine pass; the **price-only read** on the field, so the walk stops paying the hard
   walls' test for a verdict it discards; and the tests below, with a counting field proving both halves at
   once (same sum, fewer price reads, no hard read at a proved group). **Done**: `softPriceSecAt` is the body
   and `evaluate` the body plus the hard loop, and the grouping's tests are green.
3. **The shipped sources declare their boundaries** — the band's two circles off its own coast read, a
   ring's outer ring and **every hole ring** plus the collar edge; the trace line gains the read count so the
   saving is readable. **Done**: `costField` names the band's two circles and
   [`speedZonePriceClearanceM()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/ZoneGeometry.kt:117)
   the rings' outer ring, holes and collar, with `priceReads` on the pull's own lines.
4. **The device measurement, and the record — measured** (2026-10-04, [`route-phase8.txt`](../../route-phase8.txt:1)):
   two corridors and three arms a corridor — the fine layer at **11 windows / 69 576 cells / 885–1 004 ms** on a
   3.5 km corridor and **26 windows / 145 396 cells / 1 828–1 838 ms** on a 13.3 km one, and the long route's
   final pull at `ms=18340.0 clearMs=3592.5 priceMs=14738.3 priceReads=87176`, its twin at 20051.7 / 4054.6 /
   15987.3 / 91767. **The split is unchanged**: `priceMs` is 80.4 % and 79.7 % of the pull — the share the `## Why`
   opened with — and 87 176 reads on a 13.3 km route is the mark count this plan's own model predicts rather than
   a fraction of it. **The diagnosis is arithmetic**: [`softPriceSec()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:338)
   groups `floor(priceStepM / stepM)` fine intervals, and the step a priced rung handed it was the walk's own
   **local** cell — the band's **20 m**, which is exactly the water a price is paid in — so the quotient is **1**,
   no group is ever formed, and every fine mark reads as it did before. What the pass also records: the merge's
   own count is 11 and 26 windows at **~0.013 ms a cell** with no per-window overhead left (the old shape was
   0.033 ms a cell plus 12 ms a window), and the price half's per-read cost stands at **169 µs**
   (`14738.3 ms / 87176` reads).
4b. **The step's own value — done 2026-10-05, the cut the measurement named** — `priceStepFor(walk)` answers
   the walk's interior cell and never its fine one, and that step travels as a **required** parameter beside
   the clearance step: [`pull`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:70)
   and [`snapToCorners()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:175)
   take both, the priced sites take the price step alone, and the prefix and the guard keep one partition.
   Nothing else moved: exactness rests on the arm being constant and never on the step's size, so the line, the
   distance, the guard and the clock cannot move. Landed: five tests in
   [`AvoidPriceWalkTest`](../../app/src/test/java/ykws/android/maro/spatial/multipass/AvoidPriceWalkTest.kt:236)
   — the equivalence with the `k − 2` saving, the seam, the two-layer fixture, the collar chord and the shipped
   declaration closure — the suite at 903 tests with its one parked red, and `apk-build.bat` green. **Two gaps
   this landing's review named, both owed**: the seam test pins `priceStepFor` but drives no `runPass`, so a
   reverted call site would collapse the grouping with the suite green; and the collar chord's deep half passes
   on the hole's own `|d − 100|` term, a reason its comment does not state and its assertions do not count.
5. **The second lever, only on the user's word** — the aligned shared grid, with its own stated error and
   its own reading.

## Verification

- **The equivalence is arithmetic, so it is testable exactly**: on a fixture field with a declared
  boundary and a counting price source, the coarsened `softPriceSec` returns **the same double** as
  today's walk and pays **strictly fewer** reads — one test, two assertions, and it is the proof rather
  than a proxy for it.
- **The step handed at the seam is asserted, not assumed**: Phase 4's collapse was invisible to the whole
  suite, because every fixture threads its own step, so the engine's own choice could fall to `k = 1` with
  every test green. Landed 2026-10-05 **short of its own claim**: `priceStepFor` is pinned and a two-layer
  fixture proves a group forms, but no test drives `runPass`, so the call site that hands the step is still
  unasserted — the gap the landing's review named and the next pass owes.
- **An unproved group keeps today's reads and today's verdict**: a chord running along the band's edge at
  350 m reads at every fine midpoint, and the chord's acceptance is the one today's walk gives.
- **A shallow patch inside a group is still refused** — the depth gate is a hard step and
  [`legClearCause()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:211) keeps
  every one of its marks; the regression the clearance plan's validation caught, re-asserted here because the
  price cut changes the field read the pull hands that walk.
- **A hole's edge is a boundary like any other**: a fixture whose chord crosses the hole of a holed ring
  keeps the fine reads across that edge, and each group either side is priced by its own reading — the defect
  the review found, and the test that must exist before any of this ships.
- **A proved group pays no hard read**: the counting field separates the price reads from the hard tests, so
  the saving is read once and the discarded `blocked` flag is proven discarded.
- **The prefix is asserted, not inferred**: the coarsened `softPricePrefix` equals today's prefix over a path
  fixture, since its identity follows from the chord's by construction alone.
- **Two gaps an earlier review left, now closed** (2026-10-05): the ring collar has its walk-level chord, and
  the **shipped** declaration closure in `costField` is exercised by a test of its own — every fixture used to
  hand-roll its own, so the one declaration that ships is no longer the one the suite never asks.
- **A ring's collar boundary is proved like the band's**: a fixture whose chord runs inside the collar's
  100 m ring keeps the fine reads, and one deep inside the ring proves the group from its own ring
  distance.
- **The suites stay green with no assertion changed** — the same proof the clearance cut rested on: the
  same marks are visited, and every read that decides a verdict still happens.
- **The device reading, taken** (2026-10-04): the figures in Phase 4 above — and the verdict they carry is that
  **a proved group never formed on a priced rung**, so this cut's saving is still owed rather than found. The
  equivalence itself is untouched by that: nothing moved, which is what the landing claimed and what the pass
  confirms.

## Risks

- **A mis-declared boundary is silent, and a hole is the case that finds it.** The inequality proves nothing
  about a boundary a source failed to declare — the shipped collar read walks the outer ring alone, so a
  declaration built from that read would be blind to every hole, and a group straddling one would be proved
  when it must not be. The declaration is the contract, the hole test is its own check, and this stays the
  plan's worst failure mode: it moves the drawn line rather than crashing.
- **A boundary that is not a true distance breaks it.** A ring's containment toggle and a collar edge are
  distances; a rule that changed its price on a *count* (the strictest of several limits, the dearest of
  two arms) is only provable because every underlying boundary is a distance — a future source must state
  which it is, exactly as the hard sources must.
- **The gain is smallest exactly where the route is dearest**: a chord running along the band's edge or
  inside a collar keeps today's reads, and those are the chords a priced rung is made of. The reading,
  not the arithmetic, decides — the same risk the clearance cut carried, and it was the reading that
  settled it.
- **Grouping must refine, never replace, the fine partition**, or the sum moves and the guard with it.
  The test asserts the sum's equality, not its closeness.
- **`priceClearanceM` must not become a second law read**: where the clearance is computable from the
  arm's own reads it is free; where it is not, the source pays a read and the win shrinks by it.
- **A second read of the law would drift from the first**: the price-only read and `evaluate` must be one
  implementation — the price-only body, with `evaluate` the body plus the hard loop — or the two prices can
  disagree while every test still passes on one of them.
- **The instrument can flatter the cut**: `priceReads` counts the price's own reads and not the declaration's,
  so a logged saving overstates what a proved group paid by the one read that proved it. The device pass must
  read both, or the number it prints is not the win — and the saving's own shape is `k − 2` a group wherever a
  boundary is provably absent.

## Parked

- **The mark count itself** — the two-pointer's `n² / 2` sampled volume (the feature's order step 5).
  Fixed sample marks along a chord with a memo of the reads they already paid is the next lever, and it
  is measurable once the per-read cost is down.
- **The second lever above** — the aligned shared grid, whose error is up to one interval's price.
- **The landing's hygiene pair** (2026-10-04) — the test titled `aProvedGroupPaysNoHardRead` where only the
  price read is what is proven, and `RouteCostField`'s vestigial field-level clearance loop; the trio's third,
  the stale `pull` `@param coarseStepM` KDoc, was repaired by Phase 4b when the two steps split.

## Open questions

- **The coarse step's value — settled 2026-10-05: the walk's interior cell**, the law stated once in *The
  rule* above and carried by its own phase. The walk's own cell was the original recommendation because it
  needs no key and already describes the water's resolution, and the measurement showed what that means on the
  water a price is paid in: on `evolutive` the local cell *is* the band's fine one, so the quotient was 1 and
  no group ever formed. The same reading makes plain that the collapse is that engine's alone — `avoid`'s single
  100 m walk had been grouping at the shipped margin's `k = 4` all along, its price share never measured — so the settlement makes
  the two engines agree instead of opening a gap. The named alternative, an explicit multiple of the fine
  cell, is **not taken**: it invents a factor the walk already carries, and exactness rests on the arm being
  constant and never on the step's size.
- **The aligned shared grid — the user's call, not the agent's**: it moves the guard's arithmetic by a
  stated step and so can move the line, which is a change the user sees; the proof above cannot.
- **Whether the collar's own price is worth protecting separately — settled in shape, open in value**: the
  collar is a price like any other and its declaration is free, except that a **holed** ring owes the holes'
  segment walk. Whether those chords are common enough to matter is the reading's, not this plan's.
