<!-- scope: feature -->
# Route — the mark count: the two-pointer's sampled volume

**Date:** 2026-10-05 · **Status:** **Phases 1 (the lattice) and 2 (the memo) landed 2026-10-05, the review's six should-fixes folded the same day** — `apk-build.bat` green, the suite at 923 with the one parked red, no verdict assertion moved; **the bound recorded below was accepted on the user's word of 2026-10-05**, and the memo built on it turns a counting fixture from 410 price reads to **270** (now asserted, no longer prose) with the sums unchanged, so **Phase 3 (the reading) remains the one pass this line keeps.**
**Order:** the user's word of 2026-10-05, *"do plan the 2 tasks"* — the second of two, beside
[`261005_FEAT_PLN_Route_chord-price-proof.md`](261005_FEAT_PLN_Route_chord-price-proof.md).
**Rev 2 (2026-10-05):** the plan's own review landed — the phases were split so the lattice's error and
the memo's win are two steps with two exits, the exit's wording stopped claiming that no assertion may
move at all, the read-density figures gained their source, and the sibling plan's interaction inside
`softPriceSec` is stated.

**Asked for:** the parked lever of [`261004_FEAT_PLN_Route_price-walk-reads.md`](261004_FEAT_PLN_Route_price-walk-reads.md:356)
— *"the two-pointer's `n² / 2` sampled volume … Fixed sample marks along a chord with a memo of the reads
they already paid is the next lever, and it is measurable once the per-read cost is down"* — now planned
rather than parked. It is the feature's own order step 5.

## Why

- **The cut that landed coarsens what a mark costs, never how many marks there are.** Phases 1–3 and 4b
  group intervals so a *proved* group pays one read instead of `k`; the number of marks — and so the
  walk's `Σ over chords (chord length / fine step)` — is untouched, which is why the phase-8 capture's
  `priceReads` equalled the model's own mark count ([`route-phase8.txt`](../../route-phase8.txt:1)).
- **The volume is the loop's own arithmetic.** [`pull()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:71)
  stands its anchor while the probe advances; every evaluation walks the candidate chord twice over —
  the clearance walk of [`legClearCause()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:223)
  and, where that chord is clear, the price walk of [`softPriceSec()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:327).
  On a failed extension the anchor becomes the probe's predecessor, so the same water is walked again by
  the next attempt, and again by the one after it.
- **Three further sites pay the same bill**: [`softPricePrefix()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:371)
  walks the raw cell chain once — about **2 reads a point on the band's 20 m cells and 9–12 on the 100 m
  interior chain**, the price-walk plan's own model
  ([`261004_FEAT_PLN_Route_price-walk-reads.md`](261004_FEAT_PLN_Route_price-walk-reads.md:42)) —
  [`snapToCorners()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:176)
  asks four priced segments per corner candidate, and [`RouteFinePass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:30)
  sums the same walk over the fine points. The lever is the marks, at four sites, and not one.
- **Its trigger was the reading, and the landing met the cost half of it.** The plan names the mark count as
  *measurable once the per-read cost is down*, and 4b is that cut; the reading that would have re-priced the
  pull was **closed unread on the user's word of 2026-10-05**, so this plan now supplies its own at Phase 3,
  which is the one pass the feature keeps.
- **It sits inside the group path of the sibling plan and not beside it**: the span proof of
  [`261005_FEAT_PLN_Route_chord-price-proof.md`](261005_FEAT_PLN_Route_chord-price-proof.md) decides which
  spans are priced from one reading at all, and this plan's marks are what an unproved span falls back
  to. Either can ship alone, and together they are one walk.

## The lever, and the price it carries

- **Today's marks are chord-relative.** `softPriceSec` derives `steps = ceil(dist / sampleStep)` and
  places its marks at `(j + 0.5) / steps` of the chord, so `stepM = dist / steps` differs between two
  attempts of different length and the marks of the shorter attempt do not stand on the longer one's
  points. A memo keyed on a mark's own position therefore **cannot hit** while the marks are relative.
- **Cutting the volume means fixing the marks on a step lattice.** Marks at `anchor + (j + 0.5) × step`
  with `step = clearanceStep(marginM)` (25 m at the shipped margin) make consecutive attempts from one
  anchor share every mark but the tail, so a memo of the reads already paid hits **exactly** — the same
  point, the same pure function, the same double.
- **The partition moves with the marks, and that is the decision.** Today the marks cover the chord
  exactly; on a lattice the tail is a remainder unless the last interval is sized to it. Where the tail
  is left as a remainder the sum changes by up to one interval's price, the same class the plan already
  states for the aligned shared grid ([`261004_FEAT_PLN_Route_price-walk-reads.md`](261004_FEAT_PLN_Route_price-walk-reads.md:141)).
  Sizing the last interval to the remainder keeps the coverage exact, and the change left is the
  **positions** of the interior marks.
- **A moved sum can move a verdict, and a verdict is the drawn line.** [`priceRefusal()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:283)
  compares the chord's price against the path's prefix, so both sides must be built on the same lattice
  or the guard compares two different walks. This is a change the user can see, so the lattice is the
  user's word — the plan's own reopening of `Δ price = 0`.
- **The exact fallback is named and is thin.** A memo that leaves the partition alone can only hit
  where the points coincide by construction, which relative marks never do; the prefix is already
  single-paid. So an exact mark-cut does not exist at this shape, and the honest choice is between the
  lattice's stated error and leaving the volume as it is.

## Phases

Two steps, and they are split because the first carries the error and the second carries the win: a
lattice alone changes positions and saves nothing, and a memo alone can never hit.

1. **The lattice, with today's reads and its error stated.** One `MarkLattice` for a walk — the step from
   `clearanceStep(marginM)`, marks at `anchor + (j + 0.5) × step`, the last interval sized to the chord's
   remainder so the coverage stays exact — in `softPriceSec` and in `legClearCause`'s coarse marks alike.
   Exit: the fixture for the moved sample green, the drawn line's own figures recorded against the
   current capture, and **no assertion about a verdict changed** — an assertion about a sample's position
   may legitimately move here, which is what this phase is for.
   The error bound and its fixture live here, not later: a chord whose arm changes across a moved sample
   states the bound, and the user's word is taken before the memo is built on top of it.
2. **The memo.** One memo per walk, keyed on the mark's own point, shared by `legClearCause` and
   `softPriceSec` so a mark's clearance read and its price read are each paid once, and cleared with the
   field so no answer prices the next solve. Exit: a counting field proving the reads fall across
   attempts of one anchor, with the sums unchanged from Phase 1's.
3. **The reading.** `priceReads` and `priceMs` on the phase-8 pairs for both engines, with the drawn line's
   figures as the check — **the sibling plan's reading was closed on the user's word of 2026-10-05, so this
   one lands alone, and it is the one pass the feature keeps**.

## Phase 1 — the lattice, as landed (2026-10-05)

- **Built, and no device pass is owed (R97):** one [`MarkLattice`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:73) for a walk — marks at
  `anchor + (j + 0.5) × step`, the last interval sized to the chord's remainder so the coverage stays
  exact — used in [`softPriceSec()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:375) and in
  [`legClearCause()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:264)'s coarse marks alike.
- **The step each site lattices on, resolved.** The plan's wording predates Phase 4b's step split, so each
  site lattices on the step it actually partitions by: the coarse clearance marks on the walk's **coarse**
  step (`coarseStepM`), and the price walk's **intervals** on its own **fine sampling** step
  `clearanceStep(marginM)` — the step its sum is taken over — while `priceStepFor`'s **price step**
  continues to govern the **grouping** alone. Lattice the fine partition on the price step and the walk
  coarsens fourfold, breaking the "unproved group keeps today's reads" contract, which is not this phase.
- **The bound, and it is not zero.** A chord whose arm changes across a moved sample errs by at most **one
  interval's price**, `price × stepM`, and it is zero exactly where the boundary lands on an interval edge
  — the number the user's word is taken on, pinned by
  [`aPriceBoundaryAcrossAMovedSampleErrOrsByAtMostOneInterval`](../../app/src/test/java/ykws/android/maro/spatial/multipass/AvoidPriceWalkTest.kt:1).
- **The drawn line's own figures, against the current capture** ([`route-phase8.txt`](../../route-phase8.txt:1)): **3 514.1 m / 1 366.2 s** on the
  3.5 km pair (λ = 2.5, 5 kn; λ = 0 and 5 identical) and **13 330.5 m / 1 111.4 s** on the 13.3 km one,
  with **10 936.4 m / 2 989.9 s** on the 10.9 km pair — unchanged on every fixture, since no verdict
  assertion moved and a chord whose length is an exact multiple of the step keeps today's marks byte for
  byte.
- **`apk-build.bat` green; the suite at 918 / 1 — the parked `route.avoid.fine.cellRatio` test — / 11**,
  three new fixtures green: the moved sample with the lattice's exact coverage, the bound, and the coarse
  marks' own lattice.

## Phase 2 — the memo, as landed (2026-10-05)

- **Built, and the exit is met.** One [`MarkMemo`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:116)
  stands at a walk's own start — [`pull()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:118) made
  one per call — keyed on the mark's own `LatLng` (never a rounded index), shared by
  [`legClearCause()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:264) and
  [`softPriceSec()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:375) and threaded through
  `chordDecision`, `softPricePrefix`, `spanPriceSec` and `groupPriceSec`. A hit answers the field's own double, so
  no sum can move, and the price-read tally increments on a **miss alone**.
- **The numbers.** The counting fixture on the priced-corner pair reads **270 against 410** — a 140-read fall,
  now **asserted** on the fixture's own counts rather than left in prose — with the pulled line identical to
  the memo-less walk's; the one-mark-twice fixture asserts the two doubles identical and the counter at **1**.
  `apk-build.bat` green, the suite at **923 / 1 / 11** (the two fixtures add two to Phase 1's 918, and the
  review's three fixtures below add three more; the single parked red unchanged), **no verdict assertion
  moved**, and the span proof's own read was left untouched.
- **The review's six should-fixes landed (2026-10-05, the `#implement` Ask hop).** A straight-path fixture now
  pins the overlap the triangle never produced — one read per distinct shared mark, asserted on the points
  themselves; the counting fixture asserts its own **410 → 270** instead of naming it in prose; the guard's
  claim was cut to **one step and one grouping rule, not one partition**; the unproved stretch is pinned
  **point for point** at the lattice's midpoints with the memo armed and without it; `MarkMemo` is **internal**
  (with `pull`), and it is **bound to its field** — a different field identity wipes both tables and `hasPrice`
  reads the field too, so no answer can cross a rebuild, pinned by `theMemoWipesItsCacheWhenTheFieldChanges`;
  and the pull's KDoc now says what the walk does, the clearance half inert and the price half the win.
- **Where the win lands, named.** Within one walk the exact-point hits come from the prefix walking each chord
  before the attempt does; two *different* chords from one anchor land on different points — the marks carry the
  chord's direction and its haversine length — so the clearance half of the memo is effectively inert in a real
  walk and the shipped win is the price half the plan names.

## Verification

- **A counting field proves both halves at once**: the same sum, strictly fewer reads, and one read per
  distinct mark where two attempts share their stretch — the overlap pinned by
  `twoAttemptsThatShareAStretchReadEachDistinctMarkOnce`.
- **The memo cannot change a value**: a fixture reads one mark twice and asserts the two doubles are
  identical and the counter rose once.
- **The lattice covers the chord exactly**: the intervals' lengths sum to the chord's own haversine, so
  the one-interval error cannot hide in an uncovered tail.
- **The verdicts are the check Phase 1 exists for**: the same chords refused and the same line pulled on
  every existing fixture, and `LINE distance` and `duration` against the current capture on the pairs.
- **An unproved stretch keeps today's reads**: near the band's edge or inside a collar the walk reads as
  it does today — at the landed lattice's own midpoints, in order, whether the memo is armed or not —
  pinned point for point by `anUnprovedStretchReadsTodaysPositionsWithAndWithoutTheMemo`.
- **The guard's two sides share one step, not one partition**: the prefix's per-segment walks and the
  chord's one-line walk obey the same step and the same grouping rule, so the guard compares like with
  like; their anchors differ, so they meet mark for mark only on a straight run.

## Risks

- **The lattice's error is the plan's worst case** — it moves the guard's arithmetic and so the drawn
  line, silently, exactly as the aligned shared grid does. It is Phase 1's whole subject: the fixture
  states the bound, and a bound of zero is the only outcome that needs no user's word.
- **The memo's key must be the point, never a rounded index**: a cell-keyed memo answers with a
  neighbour's price while every test still passes on the fixture's own points.
- **A memo's lifetime is one walk**: a memo that survives an answer would price a second solve with the
  first's field, and the field is rebuilt per λ pass.
- **The win may be smaller than the volume suggests**: the attempts that pay most are the long ones, and
  those are the ones a memo serves last (their first marks are shared, their tails are not). The sibling
  plan's span proof is what takes the long ones out of this walk altogether.
- **The instrument still under-counts the total**: `priceReads` counts the price's reads alone, so a
  saving logged on it is the price half's, never the pull's.

## Open questions

- **The lattice, and the error it accepts — the user's call, not the agent's**, for the reason the
  aligned shared grid is: the change can reach the drawn line. The plan's own recommendation is to take
  it only if Phase 1's bound is zero on the shipping pairs, otherwise to leave the volume parked.
  **Settled 2026-10-05 on the user's word — accepted**: the bound is at most one interval's price, zero
  where a boundary lands on an interval edge, and the three recorded routes did not move, so the volume is
  taken rather than parked.
- Whether the lattice is per chord (an anchor origin) or absolute over the corridor — an absolute one
  serves more marks across anchors and moves more samples with it.
- Whether the prefix's segments want the memo at all, given their own walk is single-paid.

## Parked / out of scope

- The clearance walk's own mark count beyond what the memo serves — the proof that spares a *read* is
  landed, and this plan changes only how often a mark is *visited*.
- Any new key, any new dependency, and any change to the drawn line that the reading does not demand.
