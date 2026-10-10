<!-- scope: feature -->
# Route — the span-level price proof: one read prices a whole span

**Date:** 2026-10-05 · **Status updated 2026-10-05:** **Phase 1 landed** and **Phase 2 closed unread on the user's word of 2026-10-05** — their own pass found no difference in the routes created, which is the acceptance half, and the measurement confirms no future feature, so the recursion stays as landed with its downside bounded by the floor. Only the record remains.
**Order:** the user's word of 2026-10-05, *"do plan the 2 tasks"* — the first of two, beside
[`261005_FEAT_PLN_Route_mark-count.md`](261005_FEAT_PLN_Route_mark-count.md).
**Rev 2 (2026-10-05):** the plan's own review landed — the chord-scale condition's reach is stated,
the single mid-chord test became a **span bisection** with a floor, the prefix phase was withdrawn as a
no-op at the shipped cells, the acceptance gained its expected arithmetic, and the `>=` boundary's own
reason is said.

**Asked for:** the open-water half of the pull's price cost — a lever found in the code on 2026-10-05
rather than a parked item: the price walk still reads in water where its price is provably **zero**.
The band's own arm answers 0 beyond its reach ([`bandPriceAt()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:198)),
yet the walk pays one declaration read plus one price read per 100 m group, because the band's source
stands in the guard field wherever the price is armed.

## Why

- **The 4b cut's floor is a group.** [`softPriceSec()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:327)
  groups the fine intervals and proves each group from its own midpoint; a group is at most
  `priceStepM / stepM` intervals, so an open-water chord pays two reads per 100 m however long it is —
  and the price it reads is 0.
- **The same inequality already holds at a larger scale.** The group proof is
  `clearance >= half-length`; read at a span longer than a group it says *no boundary of any soft source
  lies inside that span*, so every arm is constant over it and one reading prices it.
- **The reach of that condition, stated because the review asked for it.** A span is provable only if it
  lies inside a boundary-free disc of radius half its own length: a 3.5 km chord needs 1.75 km of clear
  water around its midpoint, so a single test at the chord's own scale fires only deep offshore — while
  the site that pays most of the volume is the **short rejected attempt**, where two reads replace two
  reads and nothing is won.
- **So the proof is read as a recursion, not as one test.** Test a span's midpoint; proved, price the
  span from that one reading, exact at any length; not proved, split the span and test each half, down to
  a **floor** — the span the group walk already prices in two reads, so a short span never regresses.
  The walk then settles on the largest proved spans and falls back to today's group and fine marks only
  where a boundary actually lives.
- **A constant arm's sum is exact, with no partition at all.** Every point of a proved span holds the
  midpoint's own arm, so the span's price is that reading times the span's length — identically the fine
  walk's sum, because the arm is constant. Where the reading is 0 the product is 0, and a proved span
  costs two reads whatever its length.
- **The declaration is already sound for the combination.** `costField`'s `clearanceAt` answers the
  **minimum** over the band's two circles and every ring's edge and collar
  ([`RoutePassPrimitives.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:73),
  [`speedZonePriceClearanceM()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/ZoneGeometry.kt:117)),
  and `priceSec` is the band's price and the zones' combined with `max` — so a clearance naming every arm
  change of every source is exactly what makes one reading answer for a whole span.
- **It lands on the user's own complaint.** On the Palm Beach pair the band's water is 1 135 m of
  3 514 m ([`route-phase8.txt`](../../route-phase8.txt:81)), so the coastal third keeps today's walk and
  the open two thirds are where proved spans replace marks: the pull's 80 % share is the water this cuts.

## The rule

- **One clearance read at a span's midpoint proves the span, and the span is priced from one price read
  at that same point.** If `field.priceClearanceM(midpoint) >= spanLength / 2`, no soft source's arm
  changes anywhere on the span, so its price is `field.softPriceSecAt(midpoint) × spanLength`.
- **`>=` is enough, and the reason is said**: a boundary standing exactly at a span's own end is a single
  point of a partition-free product, and a point contributes nothing to a length-weighted sum.
- **An unproved span splits in half and each half is tested**, recursively, so the walk finds the largest
  proved spans instead of settling for whatever the chord's own length allows.
- **The recursion's floor is the group walk's own span** — `2 × priceStepM` at the shipped cells, the
  length the group walk prices in two reads — so a span at or under the floor goes to the group path, and
  **a short chord can never pay more than it does today**. The floor is derived from `priceStepM`, the
  step the walk already carries: no new key, no new plan member.
- **Zero error, and it is a proof rather than a bound.** The replaced sum is `Σ price × interval` over
  the same span at the fine grid; the arm is constant, so every term is the same number and the sum is
  the reading times the same total length. `Δ price = 0`, so the guard is not relaxed, no tolerance
  enters, and no verdict can move.
- **It is the same inequality, not a new law.** No key, no plan member, no vocabulary: the group proof's
  own condition, read at whatever scale the walk can prove.
- **Everything the group proof cannot prove, this cannot either**: a span whose midpoint stands near the
  band's circles, a ring's edge or a ring's collar is walked as today, and a source that declares
  nothing (`MAX_VALUE`) can never be proved.
- **Every caller inherits it** because they all go through `softPriceSec`:
  [`priceRefusal()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:283)
  on the guard's chord side, [`softPricePrefix()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:371)
  per path segment, and through them [`snapToCorners()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:176)
  and [`RouteFinePass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:30).
- **The prefix gets nothing from it, and that is stated rather than planned**: a path segment is 100 m on
  the interior chain and 20 m on the band's cells, and the group walk already prices each in two reads,
  so the recursion's floor sends every segment straight to the group path. The prefix's own walk is
  single-paid by construction ([`softPricePrefix()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:371)).
- **The hard half is untouched**: `legClearCause` keeps every mark of its own, the depth gate's step
  stays per mark, and the coastline's distance stays the clearance walk's business.
- **It lands independently of the sibling plan**: the span test runs before the group walk, and the
  mark-count plan's lattice and memo live inside the group path, so either can ship alone.

## Phases

1. **The span proof and its recursion.** `softPriceSec` tests the span's midpoint; at or above
   `spanLength / 2` it takes one price read at that same point and returns `price × spanLength`; below
   it, a span over the floor splits in half and recurses, and a span at or under the floor walks the
   groups 4b already walks. Exit: the whole unit suite green with the same verdicts on every existing
   fixture — the exit the declaration phase held — and the new fixture green.
2. **The reading.** `PULL` and `FINAL` `priceReads` and `priceMs` on the phase-8 pairs, both engines,
   against the baselines on the record: 5 048 reads at 3.5 km and 87 176 at 13.3 km
   ([`route-phase8.txt`](../../route-phase8.txt:52),
   [`261004_FEAT_PLN_Route_price-walk-reads.md`](261004_FEAT_PLN_Route_price-walk-reads.md:264)), with
   `LINE distance` and `duration` as the check that nothing moved. The expected arithmetic is stated so
   the reading can be judged: `priceReads` falls to about **twice the number of proved spans plus the
   boundary spans' marks**, since a proved span pays one price read and one proof read at one point.

## Verification

- **A proved span reads exactly twice and sums to 0**: a fixture whose midpoint stands beyond the band's
  reach, with a counting field asserting two reads and the walk's own 0.
- **A long span is priced from one reading**: a kilometre-long span in clear water returns
  `price × length` as the same double the fine walk returns, not a near miss.
- **The recursion finds the halves**: a chord whose middle third holds a boundary is split, the two clear
  halves are proved and priced from their own readings, and the third keeps today's marks — the sum
  asserted equal to today's walk over the same chord.
- **The floor holds**: a span at or under `2 × priceStepM` takes the group path and pays no extra read,
  so a short chord's counts are today's.
- **A constant arm's product is exact**: a nonzero-arm fixture proves `price × length` equals the fine
  walk's sum as the same double. This is the test that would catch a partition creeping back in.
- **The boundary is a boundary**: a span whose midpoint clearance stands just under half its length is
  split rather than proved.
- **A collar or a hole refuses the proof**: the ring fixtures of 4b keep their fine reads, so the hole
  defect the review found cannot re-enter by a wider proof.
- **The declaration is the contract, restated**: a fixture source that changes its price without
  declaring a boundary leaves the span proof wrong — the test states it as the known silent failure.
- **Δ price = 0 on the shipping pairs**: the same chord, priced both ways, asserted equal as doubles.

## Risks

- **A single read speaks for a whole span**, so a source that under-declares is wrong over that span
  rather than over a group of four intervals. The declaration's contract is the same one the group proof
  rests on, and the hole case is its check.
- **A failed test costs its reads before it saves any** — the recursion's own objection. Depth is bounded
  by the floor (`log2(span / floor)` levels), and the fixture above pins that the split is taken.
- **The proof's clearance read is not counted in `priceReads`**, so the logged saving is the price
  reads' alone while a proved span's true cost is two reads at one point; the acceptance says so, and
  putting the proof count on `PullTiming` is named as the honest fix.
- **The `max` combination is what keeps one reading sufficient**: a future source that *sums* with the
  band's arm rather than taking its max must declare accordingly, exactly as a non-metric hard wall must.
- **The win is zero exactly where the route is dearest**: a span along the band's edge or inside a collar
  keeps today's reads. The reading, not the arithmetic, decides which pairs benefit.
- **The 4b landing's owed seam test is a premise, not this plan's**: the test that drives `runPass` so a
  reverted `priceStepFor` call site cannot pass unseen is still owed
  ([`261004_FEAT_PLN_Route_price-walk-reads.md`](261004_FEAT_PLN_Route_price-walk-reads.md:296)), and a
  second proof in the same walk makes its absence more expensive.

## Open questions

- **Whether the recursion is worth its failure path on the coastal pairs** — the pairs where the band's
  water is most of the line get little from it, and the pull's share is measured on those. The plan's own
  recommendation is to ship Phase 1 and let Phase 2's reading answer it, since the floor bounds the
  downside to a few extra reads a chord. **Closed by the user's word of 2026-10-05:** the reading was
  withdrawn, so the recursion stays as landed, its downside bounded by the floor.
- **Whether the floor is the group walk's span or a coarser one** — a coarser floor saves more reads and
  gives up the group proof; the shipped value is derived, not chosen, and changing it is a reading's call.
- **Whether the proof's own read travels on `PullTiming`** so the instrument reports a proved span
  honestly rather than a price-read count that flatters it.

## Parked / out of scope

- **The aligned shared grid** (the earlier plan's second lever, [`261004_FEAT_PLN_Route_price-walk-reads.md`](261004_FEAT_PLN_Route_price-walk-reads.md:222))
  — still the user's call, still error-carrying, and untouched here: this plan's proof does not move the
  partition at all.
- The mark count, the sibling plan.
- Marker weights (the walk's item 14), the fine band's Change 4 (item 15), and the `avoid` engine's own
  parked ratio test, which stays the user's.

## Outcome

**Landed 2026-10-05 (Phase 1); Phase 2 closed unread.** `softPriceSec` tests a span's midpoint and, proved, prices the whole span from one reading — recursing down to the group walk's own floor (`2 × priceStepM`) where a boundary actually lives — so Δ price = 0 and no verdict moves, and a constant arm's product is exact at any length. The recursion ships as landed, its downside bounded by the floor, because the user's word withdrew the reading that would have priced it.
