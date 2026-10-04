<!-- scope: feature -->
# 261004_FEAT_PLN_Route_hybrid-grid

Topic: **the adaptive grid** — two resolutions in one walk. Fine **20 m** beside the coast and the depth
gate, coarse **100 m** in open water, ratio **1 : 5**; the second pass is one fine grid **along the path**.
**The metres land first; nothing of the two-layer walk is built.**

Status: Phases 1–6 landed (2026-10-04) — Phases 1–3 ship the metres, the one lattice and the corridor chain;
Phase 4 builds the two-layer rasterize — the family, the layer in the cell key, the two windows and the band's
membership; Phase 5 lands the seam that expands a neighbour across the two layers and prices it from the two
cell centres; Phase 6 carries the band's 20 m into the drawn points — the corner radii, the carve reach, the
end disc and the snap guard all read the **local** size the water was resolved at. Phase 7 (a measurement
decision on `avoid`) and Phase 8 (the record) remain.

Placement: **this document is the algorithm, not the engine.** It is built inside a new engine named
`evolutive` — see [`261004_FEAT_PLN_Route_evolutive-engine.md`](261004_FEAT_PLN_Route_evolutive-engine.md)
— and `avoid` keeps its behaviour, its keys and its 50 m design. Every key named here is therefore
`route.evolutive.*`, and `avoid`'s own `route.avoid.fine.cellRatio=0.3333` experiment is left exactly as it
stands.

Vocabulary: the thing is the **adaptive grid**; `hybrid` and `distance-scaled` are retired names.

## What landed, 2026-10-04 — the metres the walk reads, then the chain

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
- **The two debts Phase 1 named were both discharged by Phase 3**: `route.evolutive.fine.corridorHalfWidthM`
  found its reader in the chain, and evolutive's second pass stopped being the line's bounding box.

**Phase 3, the same day — the one lattice, the window walk and the chain.**

- **One lattice, derived once**: `WalkLattice` takes the pair from the **corridor's** mid-latitude and puts
  every chain box on that lattice by snapping it outward; `rasterize` kept its own per-box derivation for the
  single-region case, and `rasterizeWindow` lays a window out from the lattice instead — the one fill body,
  two frames.
- **One walk, as the section above demands**: `MultipassSearch.searchWalk` is the only loop, and
  `search(grid, …)` delegates through `WalkWindows.of(grid)` whose slots are still `row * cols + col`, whose
  centres are still the grid's own and whose passable reading is still one linear pass — which is why the
  suite's count did not move.
- **The chain is `EvolutiveGridPlan`'s answer**: boxes of side `2w` whose centres are at most `w` apart along
  the pulled line and whose first and last centres are the start and the aim, with `w` read from
  `route.evolutive.fine.corridorHalfWidthM`; `avoid`'s plan still answers its own box, padding term and all.
- **The pass walks the list**: one region keeps the old path exactly, several are rasterized as windows on one
  lattice and walked together; the ends are forced free and disced in whichever window holds them.
- **The gate**: `apk-build.bat` green and the suite at `874 / 1 / 10` — the 870 pre-existing tests untouched
  and the same single red, plus four new tests: the ends' discs, the carve reach where it fits, the chain's
  spacing, and a path drawn across the seam between two windows.
- **Two deviations named**: `fineReSearch` now branches **once** on the region count (one box = the old
  `rasterize`, several = windows), which is a two-path raster rather than a two-walk; and the plan's ends test
  proved unsatisfiable as it was written — see `## The fine region`.

## What landed, 2026-10-04 — Phase 2's reading, and the re-walk retired for `evolutive`

- **The instrument**: two lines on the existing `MaroRoute` channel, built only where that tag's level is on —
  `DEVICE PASS` per rung (the cell, passable and expansion counts, the path's cells, the coarse walk's own
  duration and the second pass's beside it) and `DEVICE DEV` per rung (the kept line's deviation from the
  coarse one, and from a **fine reference** walked over the line's own span at the fine cell, which no
  corridor caps). [`referenceWalk`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:349)
  is that reference and [`deviationTo`](../../app/src/main/java/ykws/android/maro/spatial/multipass/LineDeviation.kt:24)
  is the measure; both are dev-only and no shipped path reaches them.
- **The reading that pins `w`, over five arms on two routes at 37, 20 and 5 kn**: the coarse line's error
  against the uncapped reference is **61–230 m forward and 84–185 m back**, at or under 100 m on four of the
  five arms, and the one arm above it sits in the 100–200 m band the plan reads as *the first walk not yet
  resolving what it should* — so **150 m is confirmed and the clamp does not rise to 300**.
- **The open-water A\* cost**: 31–31 129 expansions over corridors of 6 930–46 428 cells, the λ=5 rung
  closing 59–99 % of the passable water on the constrained routes against the λ=0 rung's 31–1 691 cells — the
  aversion, not the geometry, is what the coarse walk spends.
- **The re-walk's verdict**: kept **once in five arms**, and that once on a `λ = 0` tie the priced comparison
  cannot refuse; every other arm found no path or was refused on price (18.6 km λ=5: 73 576 against 64 786;
  λ=2.50: 35 596 against 33 585). Its precision was −0.30 % and +0.17 % of length on the 18.6 km route and
  −1.30 % on the 1.1 km one.
- **Its cost**: 2.0×–5.2× the coarse pass on the 1.1 km route (520–1 493 ms against 100–582 ms) and, on one
  grown 46 428-cell rung of the 18.6 km route, **53.6 s against that pass's 12.2 s** — paid once per rung and
  again on every grown corridor, before the keep rule decides.
- **The decision, on the user's word**: `EvolutiveGridPlan.secondPassRegions` answers the interface's own
  *no region can be cut*, so the re-walk stops running for `evolutive` while `avoid` is untouched. The fine
  cell stays (the clock's step at three sites, the plan's metres answer, the guard test) and
  [`finePass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:40) stays — its
  re-tension is the cheap half and produced every visible change the reading showed, while its zone crossing
  re-solve fired and answered nothing at about 6 s an arm.
- **What the retirement does not delete**: the chain, the lattice and the window walk stay in the tree as the
  **two-layer first walk's** next user, and `route.evolutive.fine.corridorHalfWidthM` is left in place with
  the parked corridor design rather than deleted with its reader — nothing in `app/src/main` reads it now,
  which is the state `route.avoid.grid.fineRatio` is already in.
- **The gate**: `apk-build.bat` green and the suite at **874 / 1 / 10**, the single red still `avoid`'s own
  parked ratio test.

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
- **Every rectangle in this design is a window onto one lattice family — one origin and one cell-size pair
  per resolution, in an exact 1 : 5 ratio** — and that is a precondition, not a convenience. One pair alone
  cannot serve 20 m and 100 m, and two origins would leave the seam's neighbour a search rather than
  arithmetic. Two rectangles built from their own corners land their cells up to a cell apart, so a neighbour
  across a seam would need a search rather than an index.
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
- **On one family the two layers nest exactly**: at 20 m and 100 m each coarse cell covers 5 × 5 fine cells,
  so the seam's neighbourhood is a fixed relation — **a coarse cell's edge meets five fine cells and its
  corner a 5 × 5 block**, the many-to-one enumeration Phase 5's helper owns — and the equal-cell case stays
  the index arithmetic it is today.
- **A cell's identity carries its layer.** [`packCell`](../../app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt:12)
  packs `(row, col)` alone and [`onLattice`](../../app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt:174)
  keeps the first window to claim a key, so two layers over the same water would silently drop the second
  layer's cells: a coarse cell at `(1, 1)` and a fine cell at `(1, 1)` are different squares wearing one key.
  Right for equal-cell windows, wrong the moment the sizes differ — which is what Phase 4's (b) exists for.
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
  cells ≈ `marginM + cellM` = **45 m** at the region's own design cell — so the width below is never governed
  by them **there**. At the **first walk's** coarse cell the same formula reads `2 × 100` = **200 m**, wider
  than the 150 m half-width: the chain guarantees the end **disc** at every cell and the carve **reach** only
  where `2 × cellM` stays under `w`, which is a named limit rather than an assumption.
- **The chain, not a mask.** Masking the bounding box still rasterizes and allocates every cell of the
  span and saves only the A\*'s expansions; the chain saves the rasterization and the memory too, at the
  price of walking several grids — a price the seam helper already pays.
- **The chain is the re-search's region, and the crossing keeps its own box.** The first pass walks two layers
  and the second walks the fine grid **along the path**, so the chain bounds the re-walk — while
  [`solveCrossing`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:114) clamps the
  **zone's** box to the lookup's corridor box, and keeps doing exactly that — a choice, not an oversight, with
  its fallback noted under the open questions. A zone the settled line enters is **on the path by
  construction**, so that local repair is already fine water along the path and needs no chaining; the
  refinement's cost field is an **evaluator**, sampled where the pull goes, so the corridor is
  not a bound on `finePass` itself either.
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
- **`route.evolutive.fine.corridorHalfWidthM` is read by nothing in `app/src/main` since 2026-10-04**, its
  only reader having been the chain, and the key is **kept** with the parked corridor design rather than
  deleted — the same state `route.avoid.grid.fineRatio` is in, and one the record now states instead of
  implying a reader.

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
- **Its second test is the identity's** (Phase 4's): two windows over the same water at the two sizes each
  keep their own cells — the case [`onLattice`](../../app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt:174)'s
  coordinate-only key collapses — and the band's outer edge lands on the family's own lines rather than
  between them.
- **The corridor's test is the ends'**: both end **discs** lie inside the chain at `w = 150` with the region's
  cell at 50 and at 100, and both **carve reaches** at the cells that can hold them — 20 and 50 — the reach
  being `ceil(marginM / cellM) + 1` cells and therefore wider than `w` at a 100 m cell (see
  `## The fine region`).
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
2. **Landed 2026-10-04 — the device experiment, the user's own pass, on `evolutive`.** Two rounds on device
   at `route.evolutive.grid.cellM=100` with the second pass at 20 m: the open-water A\* cost and the
   deviation that pins `w` (61–230 m forward, 84–185 m back — 150 m stands). Its by-product is the retirement
   above: the corridor re-walk was kept once in five arms at a 2×–5× cost, so `evolutive` no longer runs it.
3. **Landed 2026-10-04 — the corridor chain, and retired for `evolutive` the same day.** Three pieces in this
   order, all of them Phase 3's, landing in `EvolutiveGridPlan`.
   **(a) The one lattice.** [`rasterize`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt:349)
   derives its cell-size pair from the box's own mid-latitude, so the pair is derived **once** from the
   corridor's mid-latitude and every box snapped outward onto it: a window onto one lattice, never a box
   carrying a lattice of its own. **(b) The window walk.** The A\* indexes `row * cols + col` over one grid's
   arrays, so several windows need the sparse lattice-id map this design pins — **one search loop, never a
   second one**, the single-window case delegating through the same loop so `avoid`'s path stays the one the
   suite already proves, and the charge staying `sourceCostSec × multiplier` while every cell is one size (the
   centres-distance form is Phase 5's, where the sizes differ). **(c) The chain itself.** Boxes of side `2w` on
   that lattice, centres at most `w` apart along the pulled line, the first and last centres on the start and
   the aim, and `w` read from `route.evolutive.fine.corridorHalfWidthM` — the metres key this phase is its
   first reader; `avoid`'s plan answers its own box unchanged, padding term and all, so the span-sized region
   falls for `evolutive` alone. The exit is a green suite plus a line drawn across a seam between two boxes,
   and **a chain the pass does not walk is the one shape this phase must not ship**: the re-search reads the
   whole region list, never its first box. **Retired for `evolutive` the same day**, on Phase 2's reading — that
   plan answers no region now, and the chain stays in the tree as the two-layer first walk's next user.
4. **Landed 2026-10-04 — two-layer rasterize.** The fine band (coast and depth gate triggers, `fineCellM`
   deep, one coarse cell wide) and the coarse interior, both on the one lattice **family**. **Five pieces, in
   this order**, all of them in `multipass` and none of them pricing anything.
   **(a) The family.** [`WalkLattice`](../../app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt:20)
   carries one `cellM` and one pair today, so it becomes a family: the fine pair from the corridor's
   mid-latitude, the coarse pair derived as exactly `5 ×` it from the same origin, every window built from
   the family's pair for its own resolution and no box deriving a pair of its own.
   **(b) The layer in the key.** [`packCell`](../../app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt:12)
   packs `(row, col)` alone and [`onLattice`](../../app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt:174)
   keeps the first window to claim a key, so the identity becomes `(layer, row, col)` and a neighbour is
   resolved **through the layer that holds it** — without this, two layers over the same water silently drop
   cells and the A\* answers a longer way without raising anything.
   **(c) The build order, all of it [`buildGrid`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:57).**
   It reads `plan.firstWalkGrid(...).first()` today and stretches everything on that one grid: the two end
   discs, `carveReachCells(marginM, cellM)`, both corner-set radii (`cellM * 2.0` and
   `zoneOutsideMarginM + cellM`), the growth escalation's second build, and the `CORRIDOR`/`GRID` traces. Each
   takes the **layer that holds it**, an end's disc and its carve run in whichever layer carries that end, and
   `GridContext`'s single `cellM` becomes per-layer while `fineCellM` stays the clock's step.
   **(d) The band's membership.** The predicate is the margin's own — a cell whose centre stands within
   `bandWidthM + cellM` of the coast, or within the fine cell of the depth gate's wall — evaluated by the
   sweep the margin already runs at that larger radius, so the band's water and the land's can never disagree
   about where the coast is. *The twin plan's "a per-cell distance read, not a dilation pass" is reconciled
   here: the two are one predicate, and the sweep is its cheaper evaluation.*
   **(e) The reading's unit.** `passableCount()` counts unique lattice cells, and a coarse cell is twenty-five
   times a fine one, so every count this phase reports names its layer or its area — a mixed cell count is a
   figure the engine cannot stand behind.
   **The exit is a green suite with `avoid`'s answers unmoved** — `UniformGridPlan` still answers one tile, so
   the multi-layer path stays unreachable for it — **plus a two-layer walk whose cells nest 5 : 1 on one
   origin, asserted where the family is built**; a band whose edge cannot be laid on the family's lines is the
   one shape this phase must not ship. **The exit is met**: `apk-build.bat` green and the suite at
   **878 tests, 1 failed, 10 skipped**, the single red `avoid`'s parked ratio test and `avoid`'s own answers
   unmoved, with the two new [`LatticeFamilyTest`](../../app/src/test/java/ykws/android/maro/spatial/multipass/LatticeFamilyTest.kt:1)
   cases — the **nesting** and the **identity** — green.
   **Two deviations, both named**: the two tiles are ordered **interior first**, so `GridContext`'s `grid`, its
   two end cells and its `cellM` stay the interior's and every single-grid read site keeps its answer; and the
   band is a **membership mask** on a corridor-sized fine window rather than a band-shaped rectangle, so the
   small-band memory intent is met in walkability but not yet in allocation.
   **Not this phase, and not built**: the seam that resolves a neighbour across the two layers and prices it
   from the two cell centres — Phase 5 — so the first walk resolves on the coarse interior until it lands; the
   depth-gate arm of the band predicate, owed with it.
5. **Landed 2026-10-04 — the seam helper grew to unequal cells.** The resolution-aware neighbour expansion
   resolves a step **across** the seam through `SeamNeighbours` (the exact `1 : ratio` many-to-one relation the
   one origin makes arithmetic), and the edge is **priced from the two cell centres at the pace** — the
   destination cell's own seconds-per-metre rate over the centres' distance — so a same-layer step keeps the
   uniform charge exactly and a seam step is distance-true. The diagonal stays the layer's own, and the
   heuristic stays admissible per layer because every edge costs at least its distance at the pace. **The exit
   is met**: the g-versus-clock reading on a seam-crossing path and the hybrid-versus-uniform-fine time
   equivalence are green in [`SeamCrossingTest`](../../app/src/test/java/ykws/android/maro/spatial/multipass/SeamCrossingTest.kt:1),
   with `apk-build.bat` green and the suite at **880 / 1 / 10**, the single red still `avoid`'s parked ratio test.
   **Three enabling changes named**: `CellIndex` carries its layer, `SearchOutcome.costSec` exposes the aim's g,
   and the search's `zonePriceSec` callback is handed the destination cell's size, so the seam's fine water is
   priced at 20 m rather than the interior's 100 m. **What it does not settle**: the fine window is still the
   corridor-sized mask Phase 4 left — the 25× the code-health section rejects — now **walked** rather than only
   allocated; the epic's own todo carries that gap and no phase here owns reshaping it.
6. **Landed 2026-10-04 — pull, snap and corner at local resolution.** The size the tail reads travels with the
   water under the point: [`WalkWindows.cellSizeAt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt:289)
   answers the finest layer whose own window holds passable water there, the coarsest where none does, and a
   single grid its own cell. The four sites read it — `CornerSet.radiusM` is now a **per-point function** (a
   fine 20 m corner near the coast moves a bend 40 m, a coarse one in open water 200 m), the two ends'
   `carveReachCells` follow each end's local cell, `openEndDisc` takes each window's own cell, and the snap
   guard field is built at the fine cell for a two-layer walk. **The exit is met**: `apk-build.bat` green and
   the suite at **881 / 1 / 10** — the one red still `avoid`'s parked ratio test — with `avoid`'s single-grid
   build passing constant radii so its answers are cell for cell, and
   `SeamCrossingTest.theLocalCellSizeFollowsTheWaterUnderThePoint` pinning the mechanism. **Named deviations**:
   `CornerSet`'s radius changed shape from `Double` to `(LatLng) -> Double` (no other reader); and the guard is
   a single per-pass field, so "the local size" was read as the fine cell — provably decision-neutral, since
   both sides of the pull's price refusal scale linearly with that size and `legClear` reads only the hard
   distance. **Not yet on a device**: the corner radius is now local, so `evolutive`'s coastal bends move at
   most 40 m instead of 200 m — the phase's intent, user-visible, and the confirming pass is the user's.
7. **Settled for `evolutive` on 2026-10-04, ahead of its own trigger — the demotion decision was taken off
   Phase 2's reading**: the corridor walk retired with no re-walk replacing it. What the phase still owns is
   `avoid`'s half (whether the re-walk earns its 2×–5× there) and the surviving seat's other mechanism, the
   zone crossing re-solve, which on the reading expanded 9 032 cells towards an aim it never closed.
8. **Record** — bake, fold, and settle the epic's `## Implemented`.

## The code health this landing must not cost

The one-lattice work edits the rasterizer, the grid, the search and the seats at once, which is where a feature
pays later for moving fast now. What this plan therefore fixes in advance:

- **One walk, not two.** The shortcut is a second A\* beside [`MultipassSearch`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassSearch.kt:30)
  for the windows; the loop stays single and parameterised by the walk, the single-window case reproducing
  today's index space exactly, or `avoid`'s answers drift and the two loops diverge from the first bug fixed in
  one of them.
- **One pipeline.** [`runPass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt:33)
  owns A\* → pull → snap → pull → clock; the windows arrive as the walk it already takes, never as a second
  entry point that re-implements the tail, so the pull, the corner sets and the clock keep one home each.
- **`avoid` cannot move, and the count proves it.** Its plan answers one region, so its path runs the same code
  with the same numbers — the charge formula unchanged until Phase 5 — and the suite's counts, not a drawn-line
  comparison alone, are the evidence.
- **The reading stays honest.** Overlapping windows make a plain sum of passable cells double-count; the figure
  is **unique lattice cells**, taken once when the search ends.
- **Two duplications to fold rather than grow.** [`secondPassRegions`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:74)
  is byte-identical in both plans and belongs in the interface as a default; and the shared builder still names
  `route.avoidGridCellM` as "the configured base" while [`EvolutiveGridPlan`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:95)
  ignores it — the base belongs to the engine whose key it is.
- **`GridContext` is a 26-field positional constructor** and this work adds to it: the new seams take named
  construction rather than a 27th positional argument, or the next transposed pair of `Double`s is a silently
  wrong line — the failure mode this feature has already named once.
- **The chain never becomes a mask.** Marking the bounding box's cells impassable still rasterizes and allocates
  the whole span, which is the cost this phase exists to remove; if the windows cannot be walked, the phase stops
  rather than shipping the mask.

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
- **The one lattice family is a precondition, not a detail.** Off it, two same-size rectangles misalign by up
  to a cell and the cheap seam crossing the design advertises becomes a search — and with two resolutions the
  precondition is stronger: the two pairs must be an exact integer ratio on one origin, or a coarse cell
  covers a fractional number of fine cells and no seam neighbour is arithmetic any more.
- **A packed coordinate that ignores the layer is a silent cell-eater.** [`onLattice`](../../app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt:174)'s
  de-duplication is a feature for equal-cell windows and a defect for two layers, and its failure is quiet:
  the dropped cells are simply not walkable, so the search returns a longer way rather than an error.
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
- **The local cell must reach the pull, the corner sets and the carve, and the chain the re-search alone** —
  the 20 m contract held in the re-raster and lost in the pull is the shape of the defect; the crossing's own
  box is a separate, local repair, clamped to the lookup's corridor and never to the chain.
- **A coarse cell above 100 m makes the zone's collar sub-cell**; a band above 12.5 % of the corridor makes
  the hybrid dearer than the 50 m design.
- **A wrong band depth or seam re-opens the channel-closing bug** this plan removes.

## Open questions

- **The corridor's half-width is decided, not open**: 150 m, on the two binding floors above, and Phase 2's
  reading only confirms it or moves it to 300. What remains is the measurement, not the choice.
- **The box count is no longer a question**: with the side fixed at `2w` and the centres at most `w` apart,
  the count is the pulled line's own length over `w` — derived, not a knob. The crossing's clamp is not a
  second region to reconcile: it is the zone's own box, and it stays where it is.
- **Parked — whether `w` must clear the coarse carve's reach too.** Phase 3's own test showed the ends
  guarantee splits: the disc holds at every cell, the carve reach only while `2 × cellM` is under `w`, and at
  the first walk's 100 m cell that reach is 200 m against the shipped 150 m. The fine pass re-opens the ends
  itself and keeps the coarse line when the splice is not better, so the exposure is a refused improvement
  rather than a wrong line. The resume condition is a device reading in which the ends' fine stretch is
  refused; the other answer is the clamp's floor rising to `2 × cellM`. **Moot for `evolutive` since
  2026-10-04** — no chain walks that region any more — so it stays parked with the corridor design.
- Whether the seam needs a transitional layer once its edge is distance-priced, or one band suffices.
- Whether the depth-gate band trigger reads the depth excess over the gate or a true contour distance.
- **Parked — the zone repair's own raster, if a reading ever says it dominates.** It stays the zone's box
  clamped to the lookup's corridor, decided 2026-10-04: not chained, because that is the bill `avoid` pays
  today and nothing measures a large zone's fine raster against a solve. The resume condition is a device
  reading in which that raster dominates; the fallback then is to bound it by the chain boxes it overlaps, and
  it is cheaper than it looks — the detour the repair is looking for needs the 100 m collar the chain's `2w`
  already covers.
