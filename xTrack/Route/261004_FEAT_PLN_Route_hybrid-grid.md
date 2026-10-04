<!-- scope: feature -->
# 261004_FEAT_PLN_Route_hybrid-grid

Topic: **the adaptive grid** — two resolutions in one walk. Fine **20 m** beside the coast and the depth
gate, coarse **100 m** in open water, ratio **1 : 5**. **The metres land first; nothing of the two-layer walk
is built.**

Status: Phase 1 landed (2026-10-04); the adaptive walk's Phases 3–6 remain in design.

Placement: **this document is the algorithm, not the engine.** It is built inside a new engine named
`evolutive` — see [`261004_FEAT_PLN_Route_evolutive-engine.md`](261004_FEAT_PLN_Route_evolutive-engine.md)
— and `avoid` keeps its behaviour, its keys and its 50 m design. Every key named here is therefore
`route.evolutive.*`, and `avoid`'s own `route.avoid.fine.cellRatio=0.3333` experiment is left exactly as it
stands.

Vocabulary: the thing is the **adaptive grid**; `hybrid` and `distance-scaled` are retired names.

## What landed, 2026-10-04 — Phase 1, the metres the walk reads

- **The three keys ship, each with one home**: `route.evolutive.grid.cellM=100`,
  `route.evolutive.grid.fineCellM=20` — clamped 10-20 and held at or under the coarse cell — and
  `route.evolutive.fine.corridorHalfWidthM=150`, with their accessors and their parse in
  [`AppConfig`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:186); `avoid`'s own keys, its
  ratio and its clamp constants are untouched.
- **The seam gained the metres fact**: [`RouteGridPlan.fineCellM(baseCellM)`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:60),
  which [`UniformGridPlan`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:83)
  answers as the ratio's own arithmetic — today's behaviour exactly — and
  [`EvolutiveGridPlan`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:93) as its
  own key; the value travels on
  [`GridContext.fineCellM`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassModels.kt:40),
  so no reader derives a size of its own.
- **Both fine consumers and every clock site read it**, and
  [`clockSampleM`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt:47) now takes
  metres rather than a ratio: `avoid`'s coarse-pass and final figures are arithmetically identical, and the
  one figure that moves is the fine re-walk's own timed line, which feeds the trace alone — the splice
  compares priced costs, never times.
- **`EvolutiveGridPlan` is the row's default**, so `evolutive` runs 100 m coarse and 20 m fine where
  `avoid`'s shipped asset runs the ratio it carries. That supersedes the engine plan's *behaves exactly like
  `avoid`* sentence, and it is what lets Phase 2's device pass read the keys at all.
- **The gate**: `apk-build.bat` green and the unit suite at `870 tests, 1 failed, 10 skipped`, the single red
  `avoid`'s own parked ratio test — the phase does not move it.
- **Two debts named rather than hidden**: `route.evolutive.fine.corridorHalfWidthM` ships with no production
  reader until Phase 3's chain, and evolutive's second pass is still the line's bounding box with its padding
  term — the corridor is Phase 3, not an omission here.

## Purpose

The 50 m uniform grid is the design, and it costs too much over open water: the A\* spends its cells on
free sea where the geometry is not tight.

The same 50 m grid also closes any passage narrower than about `cell + 2 × obstacleMargin` = **100 m** —
[`fineReSearch()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:205) names the mechanism.
The second pass cannot recover one, because it rasterizes only the caught line's own bounding box,
[`inflateBox(lineBBox(line), outsideMarginM + cellM)`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:71),
which already bypassed the channel. **The first walk must therefore be resolution-aware**: fine where the
water is constrained, coarse where it is open.

**The second pass's region is a separate defect and a separate fix**, and it can land on its own: see
Phases 3 and 7.

## The precision contract

- **The drawn line resolves at 20 m.** This is the requirement the whole design answers to, and it is
  stated in **metres**, never as a ratio.
- **The contract is the key's own ceiling**: `route.evolutive.grid.fineCellM` is clamped to 10-20, so no
  accepted value can breach it, and the guard test asserts the shipped value sits at or under 20.
- The fine cell is the **fact**; the coarse-to-fine ratio is **derived** (`cellM / fineCellM`, 5 at the
  design pair).
- The old ratio form is retired because it is relative: `fineCellM = cellM × ratio` coarsens the second
  pass by the same factor the coarse cell grows, which is how a 50 m design's 20 m became 40 m at 100 m.
- The retired metres argument is reversed with it: a metres twin may not drift from the coarse cell, but a
  ratio *does* drift from the precision it is supposed to hold — so the precision stays in metres.

## The decision — 20 m fine, 100 m coarse, 1 : 5

- **Fine cell 20 m**, held by the contract, and `2 × obstacleMargin` = 50 m wide with it.
- **Coarse cell 100 m**, and its bound is a **price** feature rather than a channel: the smallest
  open-water price band the A\* reads is the speed zone's outside margin,
  [`route.avoid.speedZone.outsideMarginM=100`](../../app/src/main/assets/maro.properties:333), so 100 m is
  the largest coarse cell that still stands that collar on a full cell.
- **1 : 5 is the pivot.** 120 m (1 : 6) tolerates slightly more band (13.6 % vs 12.5 %) but makes the
  100 m collar sub-cell; 80 m (1 : 4) saves less in open water and tolerates less band (10.4 %).
- **The fine band is one coarse cell wide** — 100 m from the inflated boundary, exactly five fine cells
  deep, and **its outer edge is the seam** between the two layers.
- **The depth gate's own standoff is a separate quantity**: `route.avoid.depthGate.marginM=20` is how far
  the curve stays off the gate's wall, not how far the band extends from it — at a 20 m fine cell the two
  merely coincide in size.
- **The band's trigger list is complete for passability**: every obstacle that can close water arrives as
  an edge or as the depth gate — islets and hazard rings included — so no zone ring seeds the band.
- **The collars are prices, not walls.** A coarse cell misprices a 50 m collar strip
  ([`route.avoid.zone300.outsideMarginM=50`](../../app/src/main/assets/maro.properties:258)) without ever
  hiding water; that is an accuracy note, and it is the same bound that fixes the coarse cell at 100 m.
- **The channel floor improves**: inside the band it is `20 + 2 × 25` = **70 m**, against the 50 m
  design's 100 m.

## The two layers, and one lattice

- **Two `MultipassGrid` instances at one seam**, not a new grid type: the fine band and the coarse interior are
  each a dense rectangle, and [`MultipassGrid`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt:84)
  carries one `cellM` and one cell-size pair by design.
- **Every rectangle in this design is a window onto one lattice** — one origin and one cell-size pair — and
  that is a precondition, not a convenience. Two rectangles built from their own corners land their cells
  up to a cell apart, so a neighbour across a seam would need a search rather than an index.
- **The lattice's own cost is one row and one column per rectangle.** A rectangle off the lattice snaps
  outward, so a 16 × 16 box can become 17 × 17 — about 13 % of that box's cells, and a couple of thousand
  across the 12 km example's 80 boxes, most of it swallowed by their overlap.
- **Nothing else in the pipeline changes**: the cell-centre read, the rasterizer's per-cell work and the
  A\*'s expansion count are all untouched, since expansions follow the free water and not the array's
  shape — while the seam's neighbour becomes arithmetic instead of a search.
- **The one constraint it adds is latitude.** `mPerDegLon` falls with `cos φ`, so the lattice fixes one
  degrees-per-cell per resolution at the corridor's mid-latitude; across a 5 km route a 20 m cell then
  drifts by under two centimetres, and the 5 : 1 nesting holds exactly in degrees and to a hundredth of a
  percent in metres.
- **On one lattice the two layers nest exactly**: at 20 m and 100 m each coarse cell covers 5 × 5 fine
  cells, so the seam's neighbourhood is a fixed relation and the same helper enumerates it for the corridor's
  equal-cell seams.
- **The seam is the band's outer edge**, where the two rectangles meet at different cell sizes.
- **Both rectangles are marked by clearance, not by price**: the fine band is the coast and the depth gate
  dilated by one coarse cell, so what a cell is stays the rasterizer's question and the A\*'s price stays
  the A\*'s.
- **`GridContext` carries both**, and the cells' sizes travel with the line so the later passes can read
  the local one.

## Perf

- Relative to the **50 m design** (density V): open water costs `(50/100)²` = **0.25 ×** cells, the band
  `(50/20)²` = **6.25 ×**.
- Cell cost is `0.25 × open + 6.25 × band`, so the hybrid pays while the band stays under **12.5 %** of the
  corridor (1 : 4 at 25 m gave 20 % — that figure is retired with the 25 m cell).
- **The model is cells, and the device measures work**: expansions, not cells, are what the A\* costs, so
  Phase 2's reading is the one that confirms the trade.
- **The second pass is the costlier raster, not the first**: it is built per rung, and its cell count
  follows its region's area at 20 m — which is why the region's shape below matters as much as the first
  walk's.
- Worst case — coast everywhere — degenerates toward the fine grid, ~6.25 × the design's cells; rare in
  practice.

## The second pass

- **The pull sets where the fine region lies.** [`fineReSearch`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:205)
  takes the pulled line as the spine of its region, so the coarse line's shape decides the fine pass's
  extent — and, as the Purpose says, decides which channels it can ever find.
- **The pull does not set the vertex precision.** Its waypoints are the path's own points
  ([`result.add(path[probe - 1])`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:75)),
  cell centres by construction, and its guarantee is clearance sampled at `marginM / 2` = 12.5 m
  ([`clearanceStep`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:118)).
- **So the second pass is where the 20 m is delivered**, which is why the fine cell is the contract.
- **The order is field first, raster second**: [`finePass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:40)
  refines the line on the fine **field**, and [`fineReSearch`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:205)
  then re-walks it on the fine **raster**, keeping its line only where that line is strictly cheaper. Both
  read `route.evolutive.grid.fineCellM` and nothing else.
- **Every A\* pass ends in the geometry.** [`runPass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt:33)
  pulls, snaps to the tangent corners and pulls again, returning that last pull's waypoints;
  [`fineReSearch`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:205) calls the same
  helper, so the fine grid's line is tightened exactly as the coarse one's, and the corner pass curves the
  survivor afterwards.
- **The corridor is therefore a grid-only concern.** The pull and the snap read a `RouteCostField`, an
  evaluator rather than a raster, so nothing in the geometry needs the region's shape.
- **The ratio is logged, never stored**: `cellM / fineCellM` is derived at read time.

## The fine region — a chain of small boxes along the pulled line

The shape:

- **The bounding box is retired**: [`inflateBox(lineBBox(line), outsideMarginM + cellM)`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:71)
  sizes the region by the line's **span**, so a U-shaped or dog-legged route pays for the rectangle that
  contains it.
- **The region is the union of axis-aligned boxes of side `2w`**, with centres placed **at most `w` apart
  along the pulled line**, so consecutive boxes overlap by at least `w` — a cell at any cell size below
  `w`.
- **The boxes share the one lattice** the two layers use, so an equal-cell seam between two of them is an
  index relation and nothing else, and their cells land on the same grid as the band's.
- **`w` is the guaranteed perpendicular half-width, and the guarantee is exact**: an axis-aligned box of
  half-side `w` centred on the line contains every point within `w` of it and promises nothing wider. An
  oblique ribbon therefore inflates by up to `sqrt 2`, since [`MultipassGrid`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt:84)
  is lat/lon-aligned and a rotated box is unavailable.
- **The ends are inside by construction**: the first and last centres sit on the start and the aim. The
  floors they impose are small — the end disc is `marginM` = **25 m**
  ([`openEndDisc`](../../app/src/main/java/ykws/android/maro/spatial/multipass/BerthCarve.kt:115) frees the
  centres within `marginM`), and the carve reach is [`ceil(marginM / cellM) + 1`](../../app/src/main/java/ykws/android/maro/spatial/multipass/BerthCarve.kt:100)
  cells ≈ `marginM + cellM` = **45 m** at the design pair — so the width below is never governed by them.
- **The chain, not a mask.** Masking the bounding box still rasterizes and allocates every cell of the
  span and saves only the A\*'s expansions; the chain saves the rasterization and the memory too, at the
  price of walking several grids — a price the seam helper already pays.
- **Both fine consumers read it**: the re-search's raster and [`finePass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:40)'s
  fine cost field, so one region definition serves both.
- **The corridor can ship alone.** Nothing in it needs the two-layer walk: on today's uniform grid it still
  retires the span-sized box and fixes the U-shape cost, which is why it is Phase 3 rather than a tail
  phase.

The width:

- **`w` is a metres key**: `route.avoid.fine.corridorHalfWidthM`, seeded **150** — the 300 m box — clamped
  100-400, and the box side is derived (`2 × w`) with no second key.
- **Recommended 150 m, on two floors that bind and one that does not.** A coarse walk inside open water
  deviates by at most the cell it was walked in — half a cell diagonally, about 70 m of a 100 m cell, plus
  the pull's tangency — so 1.5 coarse cells absorbs it.
- **The binding floor is the widest price collar the A\* reads**: `route.avoid.speedZone.outsideMarginM` is
  100 m, and the corridor must let the fine pass re-decide a collar it met at coarse resolution, so `w`
  never falls below 100 m whatever the measurement says — and that floor is the clamp's own bottom.
- **The ends' floor clears it comfortably**: 45 m of carve reach against the 100 m the collar demands, so
  the ends never bind.
- **Why not 300.** The deviations that would need it are the ones the adaptive walk already removed:
  beside the coast and the depth gate the line is walked at 20 m, so a side-of-it error became a
  fine-resolution decision, and a 300 m corridor pays double for a case the fine band covers.

The measurement:

- **The corridor's condition is measured, never assumed**: beyond `w` of the optimum it silently caps the
  answer, which the box never did.
- **Phase 2 owes the reading, and a uniform 100 m walk gives a conservative one**: the experiment runs
  before the band exists, so the deviation it measures is the pre-hybrid deviation — an upper bound on
  what the adaptive walk will produce.
- **Its thresholds**: keep 150 while the measured deviation stays at or below 100 m, go to 300 only above
  about 200 m, and read anything between as the first walk not yet resolving what it should.
- **Both numbers of the ask are kept**: "within 300 m" needs `w = 300` and a 600 m box, "a 300 m box" gives
  `w = 150`, and the reading above settles which.
- **The band overlap is the diagnostic, not a defect to design around.** Where the pulled line runs inside
  the fine band, the corridor re-walks at 20 m what the first walk already walked at 20 m — and that
  overlap cannot be engineered away, since the region has to stay connected from start to aim, so excluding
  the band stretches would leave the A\* no path. It is instead the reading Phase 7 decides on: measure how
  far the fine line differs from the coarse one over the band stretches and over the coarse stretches, and
  retire the pass if the difference is nil but for the collars.

The cost:

- **A 12 km U with a 4 km span at 20 m cells**: today's box is `(4000 + 300)²` ≈ **18.5 km²** ≈ 46,000
  cells; `w = 300` covers 7.2 km² ≈ 18,000; `w = 150` covers 3.6 km² ≈ 9,000, and the chain rasterizes
  ≈ 20,000 with its overlaps — about 80 boxes of 16 × 16 cells.
- **The box's waste is about bearing as much as turning.** A 5 km **north-south** leg already gets a 300 m
  strip from the bounding box, so the corridor merely matches it — 1.6 km² against 1.5 km² — while the same
  leg walked **diagonally** hands the box a 3.84 km square, 14.75 km², and the corridor's 1.5 km² is worth
  roughly five times, less the chain's own `sqrt 2` over-cover on an oblique ribbon.
- **The box count is the one knob beyond `w`**, a larger box trading fewer seams for more over-cover.
- **The local variant is noted and not taken.** A narrower corridor inside the fine band and a wider one in
  open water would pay on mostly-coastal routes, at the price of an asymmetric region and a second rule,
  and it would have to keep the region connected anyway; it waits until the uniform corridor is measured.

## Expected against today

- **The channel floor is the precision gain.** Today's 50 m walk needs 100 m of clear water, and the
  second pass's 20 m recovers a 70 m channel only inside its own box; the adaptive walk puts a 20 m cell
  everywhere along the coast and the depth gate, so **70 m becomes the guaranteed floor** on constrained
  water rather than a lucky one.
- **The drawn geometry does not change**: the corner pass, the snap and the pull are untouched, the second
  pass still resolves 20 m, and the clearance guarantee stays sampled at 12.5 m.
- **What the corridor costs in quality is bounded and new.** Today the second pass may search the whole
  bounding box; the chain caps it at `w` = 150 m from the coarse line, so the drawn route can no longer
  borrow a way round lying further off. Phase 2's reading is what bounds that gap.
- **The first walk gets cheaper and is never slower than the design**: its cell count is
  `0.25 × open + 6.25 × band` of the 50 m grid — ≈ **2.7× fewer** cells where the band covers 2 % of the
  corridor, break-even at 12.5 % — against the 100 m experiment's 4× as the ceiling.
- **The second pass gets cheaper by a factor the route's shape decides**: its region falls from the line's
  span to a 300 m ribbon, so a 12 km U goes from ≈ 46,000 cells to ≈ 20,000 (≈ 2.3×), a 5 km diagonal from
  ≈ 37,000 to ≈ 7,500 (≈ 5×), and an axis-aligned leg barely moves.
- **Passes per solve do not change**: three rungs, one second pass each, the same keep rule.
- **All of it is cell arithmetic.** Phase 2's device reading is the only confirmation, since the A\*'s
  expansions follow the free water rather than the array, and a seam crossing costs one neighbour
  resolution.

## Property and code changes

- `route.evolutive.grid.cellM=100` — **new**, metres, the engine's coarse cell. `evolutive` owns its own
  namespace and `avoid`'s `route.avoid.grid.cellM` is left alone.
- `route.evolutive.grid.fineCellM=20` — **new**, metres, the precision fact. **Clamped 10-20**, the
  contract being the clamp's own ceiling, and code-required to stay at or below `cellM`.
- `route.evolutive.fine.corridorHalfWidthM=150` — **new**, metres, the corridor's half-width, clamped
  100-400 so the price collar's floor cannot be set below; the box side is derived (`2 × w`).
- **Nothing is removed from `avoid`.** The two ratio keys an earlier draft of this plan retired —
  `route.avoid.grid.fineRatio` and `route.avoid.fine.cellRatio` — stay in the file; what changes is that
  `evolutive` reads a metres key instead. **Only the second has a reader**: it is read at the clock's three
  sites and by both fine passes
  ([`cellM * AppConfig.routeAvoidFineCellRatio`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:62)),
  while `route.avoid.grid.fineRatio` has none in `app/src/main/java`, in `app/src/test` or in the batch
  scripts — an unread key, and a claim this section no longer makes for it.
- **The box's padding term goes with the box, in `evolutive`**: `outsideMarginM + cellM` stops sizing its
  fine region, so the zone's price collar no longer leaks into the second pass's geometry.
- `AppConfig` gains the three `routeEvolutive*` accessors. `routeAvoidFineCellRatio`,
  `routeAvoidGridCellM` and their clamp constants stand as they are.
- **`avoid`'s one wrong comment is already corrected in the file, so Phase 1 owes it nothing**: the grid
  comment at [`maro.properties`](../../app/src/main/assets/maro.properties:286) now states one `cellM` for
  the whole corridor and that `route.avoid.grid.fineRatio` has no reader — the claim this section used to
  correct.
- **The asset ships a value the plan's baseline does not name**: `route.avoid.grid.cellM=100` beside
  `route.avoid.fine.cellRatio=0.3333`, against the 50 m code default at
  [`AppConfig.routeAvoidGridCellM`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:156).
- **The guard test is `evolutive`'s**, written rather than rewritten: the shipped second-pass cell is at
  most 20 m, and `cellM` divides by it. `avoid`'s own
  [`theFineCellRatioShipsAtFortyPercentOfTheCoarseCell`](../../app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt:638)
  stays where it is — and stays red, because the file it reads was moved to 0.3333 by the user's
  experiment: that redness is `avoid`'s residue and the user's to settle, which is why Phase 1 below does
  not touch it.

## Verification

- **The guard test is `evolutive`'s, written rather than rewritten**: a metres assertion that the shipped
  second-pass cell is at most 20 m and that `cellM` divides by it, the shape the retired ratio assertion's
  third arm already had. `avoid`'s own
  [`theFineCellRatioShipsAtFortyPercentOfTheCoarseCell`](../../app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt:638)
  stays where it is, and stays red, as `## Property and code changes` and Phase 1 both state — the reading
  of it as rewritten was stale, and it is corrected here rather than acted on.
- **The equivalence test asserts the cost, never the point list.** On a channel fixture a hybrid solve and
  a uniform fine solve must agree on total time; their **lines may differ**, because
  [`MultipassSearch`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassSearch.kt:90) breaks ties by
  row-major cell index and two grids index the same geometry differently. Naming the line is only sound
  where the fixture admits a single optimum.
- **The seam's test is the clock's**: the A\*'s g on a seam-crossing path equals that path's own timed
  cost, which is what fails today if the edge is priced by the destination cell.
- **The lattice's test is the nesting's**: a coarse cell and the 5 × 5 fine cells over it agree on their
  centres' geometry, which is the precondition the seam's index relation rests on.
- **The corridor's test is the ends'**: both end discs and both carved reaches lie inside the chain, at
  `w = 150` and `cellM` both at 50 and at 100.
- **Phase 1's exit is the existing suite green with the key change alone**, since that phase touches no
  algorithm — and its expected effect is named in the phase.

## Phases

1. **Landed 2026-10-04 — `evolutive`'s semantics, no algorithm change and `avoid` untouched.** The three
   `route.evolutive.*` keys, the three accessors, the metres fine cell answered by the plan and carried on
   `GridContext`, `clockSampleM` in metres, `EvolutiveGridPlan` as the row's default and the guard test; the
   exit is met with the suite at `870 / 1 / 10`, the single red `avoid`'s own ratio residue. *The wording
   "the engine's own fine consumers" predates the split*: after it those consumers are the shared seats, so
   the metres value had to reach them through the plan seam — the one deviation, and the reason this phase
   edits shared code where the phase's own text promised it touched none.
2. **The device experiment — the user's pass, on `evolutive`.** `route.evolutive.grid.cellM=100` with the
   second pass held at 20 m: measure the open-water A\* cost, and measure how far the coarse line sits from
   a fine reference line — the deviation that pins `w`.
3. **The corridor chain — shippable alone, and it lands in `EvolutiveGridPlan`.** The plan's
   [`secondPassRegions`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:98)
   answers the chain where it now answers the line's bounding box: boxes of side `2w` on the one lattice,
   centres at most `w` apart along the pulled line, the first and last centres on the start and the aim, and
   `w` read from `route.evolutive.fine.corridorHalfWidthM` — the metres key this phase is its first reader.
   `avoid`'s plan keeps the box with its `outsideMarginM + cellM` padding term, so the span-sized region falls
   for `evolutive` alone; the second pass then walks several boxes rather than one, and the equal-cell seam
   between two of them is the index relation the one lattice makes it.
4. **Two-layer rasterize** — the fine band (coast and depth gate triggers, `fineCellM` deep, one coarse
   cell wide) and the coarse interior, both on that one lattice.
5. **The seam helper grows to unequal cells** — resolution-aware neighbour expansion with the seam edge
   **priced from the two cell centres at the pace**, never from the destination cell's own size; the
   diagonal and the admissibility bound re-derived per layer. Exit: the equivalence and g-versus-clock
   tests above.
6. **Pull, snap and corner at local resolution** — the pull takes no cell of its own, so what takes the
   local size is `snapToCorners`' field, the `CornerSet` distances, `carveReachCells` and `openEndDisc`, so
   the band's 20 m survives into the drawn points.
7. **The demotion decision, read off the band/coarse diagnostic** — what survives of
   [`fineReSearch`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:205): the
   price-crossing re-solve alone, or the corridor walk too.
8. **Record** — bake, fold, and settle the epic's `## Implemented`.

## Risks

- **The corridor caps the answer by an assumed constant.** Its half-width is the only guarantee that the
  optimum is reachable; an unmeasured `w` turns a quality cap into a silent defect, and the box had no such
  failure mode.
- **The seam edge cost decides the whole design.** [`baseCostSec(cellM, paceKn) = cellM / knotsToMps(paceKn)`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:89)
  is the destination cell's own crossing time, and the expansion charges
  [`cell.sourceCostSec * step.multiplier`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassSearch.kt:124) —
  correct only while every cell is one size. Priced that way at a seam, a 100 m hop into a 20 m cell costs
  a fifth of the distance it covered and the return hop five times it.
- **The bound follows the edge.** [`haversine(center, aimCenter) / paceMps`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassSearch.kt:87)
  is admissible because the cheapest step costs exactly one cell of water at the pace; that survives per
  layer only while the seam edge is distance-priced.
- **The diagonal has no seam meaning.** [`STEPS`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassSearch.kt:36)
  fixes `sqrt 2` on the assumption that both ends of a step are the same square, which a seam step is not.
- **The one lattice is a precondition, not a detail.** Off it, two same-size rectangles misalign by up to a
  cell and the cheap seam crossing the design advertises becomes a search.
- **The corridor's region must stay connected from start to aim**, so its boxes cannot be thinned to the
  coarse stretches alone; the overlap with the fine band is the price of that connection.
- **Every single-grid consumer needs a resolution-aware contract**: [`GridContext.cellM`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassModels.kt:35),
  [`runPass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt:33),
  [`carveReachCells`](../../app/src/main/java/ykws/android/maro/spatial/multipass/BerthCarve.kt:100),
  [`openEndDisc`](../../app/src/main/java/ykws/android/maro/spatial/multipass/BerthCarve.kt:115) and the
  corner-set radii [`cellM * 2.0`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:103)
  / [`zoneOutsideMarginM + cellM`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:112).
  The chain multiplies that question by its box count.
- **A chain of boxes must overlap by more than a cell**, or a path cannot cross where two of them meet.
- **Both fine consumers must be handed the local cell and the corridor**, or the 20 m contract and the
  region hold in the re-raster and not in the pull.
- **A coarse cell above 100 m makes the zone's collar sub-cell**; a band above 12.5 % of the corridor makes
  the hybrid dearer than the 50 m design.
- **A wrong band depth or seam re-opens the channel-closing bug** this plan removes.

## Open questions

- **The corridor's half-width is decided, not open**: 150 m, on the two binding floors above, and Phase 2's
  reading only confirms it or moves it to 300. What remains is the measurement, not the choice.
- The box count: `2w` at 300 m boxes as proposed, or fewer larger boxes, trading seams against over-cover.
- Whether the seam needs a transitional layer once its edge is distance-priced, or one band suffices.
- Whether the depth-gate band trigger reads the depth excess over the gate or a true contour distance.
