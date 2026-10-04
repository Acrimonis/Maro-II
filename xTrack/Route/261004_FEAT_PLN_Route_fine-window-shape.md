<!-- scope: feature -->
# 261004_FEAT_PLN_Route_fine-window-shape

Topic: **the fine layer's own water** — the 20 m band layer stops being one tile over the whole corridor and
becomes windows over the water the mask actually keeps.

Status: **Phases 1 to 4 landed** (2026-10-04) — the instrument, the cut, the cell budget and the device
reading of the crash's own arms: **no crash**, the grown corridor inside the ceiling, the line unchanged, and
the fine layer's rasterization priced at **6 725 ms for 361 windows**. What remains is the **record alone** —
these figures folded into the feature's history at the next bake. The plan exists because a crash named it:
the shape the feature file already called *the open defect Phase 4 left, which no phase owns* was fatal on a
long route on the device.

Placement: the work is the adaptive grid's, so the walk itself is the subject — the lattice family, the
windows and the rasterizer's mask stay exactly what they are; what changes is the **box** the fine layer is
cut on, and the bound that keeps any walk inside the heap.

## Why

The device's own crash buffer, 2026-10-04 20:32:40, one pair (Lérins → Salis) at 19 and 7 kn:

```text
java.lang.OutOfMemoryError: Failed to allocate a 16 byte allocation ... <1% of heap free after GC
  at java.lang.Integer.valueOf(Integer.java:1195)
  at MultipassGridKt.fillScanlineEvenOdd(MultipassGrid.kt:722)
  at MultipassGridKt.fillClosedRingEvenOdd(MultipassGrid.kt:629)
  at MultipassGridKt.rasterizeFrame(MultipassGrid.kt:454)
  at MultipassGridKt.rasterizeWindow(MultipassGrid.kt:391)
  at RouteGridBuilder.buildLayeredGrid(RouteGridBuilder.kt:186)
  at RouteGridBuilder.buildGrid(RouteGridBuilder.kt:60)
  at RouteAvoidEngine.searchRung(RouteAvoidEngine.kt:306)
```

- The trace beside it names the two facts, in order: `CORRIDOR reach=7408.0m box=(43.44712..43.63706,6.94669..7.22297)`
  — the **grown** corridor, twice the plain 3704 m — then `GRID layer=fine cells=1178555 land=1165709 band=9786 zone=3057 free=3`.
- **Fine ÷ coarse = 24.7**, which is the cell ratio squared (100 m ÷ 20 m)² — so the fine tile is the
  **corridor's own box** at 20 m, 1 178 555 cells, while the water the mask keeps is ~0.8 % of it.
- The plain corridor is the same shape at 476 700 cells and survived twice; the grown one quadruples it and
  the 512 MB heap dies. The failed allocation is the ring fill's **own transient**, so two mechanisms share
  one box: the grid's dense cells and the fill's per-scanline allocations — **both scale with the box's area**,
  which is why the reshape answers both.
- **What is cleared, by the stack rather than by argument**: the pull — it had completed (`DONE stage=PULL
  result=true`, 20:32:04) and the crash is in the *next* corridor's build; the merged `develop` work — it
  brings no multipass file; the coarse-marks change — it only reads, and its arrays are bounded by the chord.
- **What the log also shows**: the fine layer's usable water is the coastal ribbon (`band=9786` of 1.18 M), so
  the tile is roughly 120× the water it delivers. The feature file's own words agreed before the crash:
  *twenty-five times the coarse grid's cells where the design's own strip would be a few thousand*.

## The rule

**The fine layer covers the band's water and nothing else, and no answer moves.**

- **The water is the mask's own definition** — [`rasterizeWindow`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt:374)'s
  `bandMask = true` keeps the coast and the depth gate dilated by `bandMaskWidthM`, so the fine layer is
  passable exactly within the band's reach. One home for "which water is fine", never a second copy of the
  band's arithmetic.
- **The cells are the ones the walk could reach before**: every lattice cell the whole-corridor tile kept
  passable must be held by some window, and no cell it kept land may become water. What changes is the
  **allocation**, never the domain.
- **The layers, the ratio and the seam are untouched** — coarse first, the exact `1 : ratio` nesting and
  [`SeamNeighbours`](../../app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt:156) are the
  adaptive grid's contract and the reshape must not touch them.
- **The mask is not the fix** — masking is already right; it is the *box* that pays for the corridor's span.
- **A walk is bounded before it is built** — a cell budget every walk's own tiles are checked against, so no
  corridor can be handed to the rasterizer if its layers cannot fit. The bound is the invariant; the strip is
  the economy.

## The cut

**Windows on the fine lattice, cut along the coast inside the corridor — not along the route.**

- **The ribbon follows the coast, not the line.** The mask keeps the water within reach of the coast, so the
  fine water is the coastal ribbon wherever it lies inside the corridor. A strip along the ends' line would
  *remove* fine water the walk may use today — a reroute dressed as a saving.
- **The windows are a tile grid over the corridor, keeping the tiles the ribbon touches** — built and
  green: tiles on the fine lattice's own lines, side = the band's reach, marked by the coast's own grown
  boxes, from the harvested `edges` and `openCoast` the builder already holds.
  `WalkWindows` already accepts **several windows on one layer**
  ([`onLattice`](../../app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt:400)) and counts
  overlaps as one cell per unique lattice cell, so no dedupe is owed.
- **A chain per coast segment was rejected on the coast's own shape**: the coast arrives as thousands of
  short edges, so [`corridorChain`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCorridorChain.kt:21)
  per edge would stack thousands of overlapping boxes over the same water, where one tile grid holds each
  piece of coast once. The chain stays in the tree for a mesh of few, long polylines.
- **The cut over-covers by construction, and that is safe**: every extra cell a tile gains outside the
  ribbon the mask paints land — memory, never an answer — so the cut's only correctness requirement is that
  **no ribbon tile is missed**, which is what the coverage test pins.
- **Fatter windows, not more of them.** Each window re-sweeps every edge, so the window count is a real cost —
  and the reading of 2026-10-04 prices it: **361 windows cost 6 725 ms**, about 12 ms a window for a
  576-cell tile. The half-width is chosen at the ribbon's own scale rather than at the fine cell's, and the
  next cut merges tiles rather than adding them.
- **The ends stand on the interior.** Today the fine window holds both ends and opens a disc around each; a
  windowed layer may hold neither, and the interior grid already anchors the two ends and the carve. That
  delta is named and tested rather than assumed harmless.

## What must not move

- **The answers**: the line, the distance, the ETA, the zone crossings, the corner sets and the clock's step
  (`cellSizeAt`) — the fine cell the clock reads is a plan answer and is untouched.
- **The coarse layer**: still one tile over the whole corridor, unchanged. Only the fine layer's box moves.
- **`avoid`**: one tile, so the layered path is never taken and nothing about it changes.
- **The mask**: still `bandMask = true` with `bandMaskWidthM = cellM`, so the ribbon's definition and the
  band's own price law cannot drift apart.

## Where it lands

- [`RouteGridBuilder.buildLayeredGrid`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:166) —
  the fine layer's windows are cut here, from the edges it already harvested and `bandLaw(world)` it already
  reads; `fineBox = family.fine.snapOutward(box)` becomes a **list of windows**, one `rasterizeWindow` each.
- [`RouteGridPlan`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:30) — **the
  seam is not widened**: the plan keeps answering the corridor and the two cells, because the band's reach is
  a world fact and not an algorithm's choice.
- **The one seam kept open**: the reach the fine layer covers is read in one named place, so a plan that ever
  wants different fine water takes it over without rewriting the builder.
- **The budget** — a pure cell estimate (a box's area ÷ cell², the lattice's own arithmetic) asked before a
  build, answering a refusal rather than an allocation; the engine logs the refusal where it already logs the
  corridor, and the grown retry answers the line it already has instead of doubling into the heap.

## Phases

1. **The instrument, before the change** — the fine layer's own footprint: per window its box and `rows × cols`,
   the layer's total cells, and the rasterize time; plus a read of
   [`fillScanlineEvenOdd`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt:722)
   to name what it allocates. The exit is a measured target, not a hope, and the two mechanisms told apart.
2. **The cut** — the fine layer's windows over the corridor's coast, the mask untouched, the coverage test
   green, and the maskless case decided (below). **Done.** The exit is the same passable water with several
   times fewer cells: the ribbon's own area is the floor this fix can reach, not an order of magnitude, and
   the budget (Phase 3) is what makes the heap safe rather than the shape alone.
3. **The budget** — **done**: `cellsOf` is the estimator (a box's rows × columns at a lattice's own cell),
   `withinBudget` is the one comparison, and both build paths ask it **before** anything is rastered —
   [`buildLayeredGrid`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:166)
   now returns `null`, which is the engine's own *answer the line you have*, with a
   `WALK refused cells=… coarse=… fine=… windows=… ceiling=…` trace line. The ceiling is
   `route.walk.maxCells`, default 600 000, clamped 10 000..5 000 000, read through `AppConfig` — the plan's
   own recommendation of a key over a derived figure.
4. **The device pass and the record** — **the pass is taken** (2026-10-04, the crash's own arms): no OOM, the
   grown corridor's walk at 253 110 cells of the 600 000 ceiling, the fine layer down from 1 178 555 to
   205 824 cells, and the line, distance and clock unchanged. What remains is the record — the figures folded
   at the next bake.

## Verification

- **The equivalence test is the reshape's own proof**: on a fixture corridor and a real coast fixture, the
  set of **passable fine cells** (and `cellSizeAt` per cell) is identical between the whole-corridor tile and
  the windowed layer. A saving, never a reroute.
- **The count test**: the windowed layer's cells are several times below the tile's on the same fixture —
  the ribbon's own area being the floor, so the assertion is a real ratio and not a round number.
- **The three tests landed in [`RouteFineWindowShapeTest`](../../app/src/test/java/ykws/android/maro/spatial/multipass/RouteFineWindowShapeTest.kt)**:
  every fine cell inside the reach is held by some window, the windowed layer costs a fraction of the
  corridor's tile, and a corridor with no coast beside it asks for no windows at all.
- **The budget's own two tests, built**: the estimator counts exactly what a frame would allocate, and the
  shipped ceiling sits **between the crash's own two corridors** — 476 700 cells inside it, 1 178 555 outside.
  The refusal's *end-to-end* path — the engine answering the line it already has — is **not** unit-tested: it
  would need a whole world fixture, and the device pass is what confirms it.
- **The ends test**: an end standing seaward, outside every fine window, still anchors the walk, keeps its
  berth carve and arrives — the interior grid is what holds it.
- **The device reading, taken** (2026-10-04, the crash's own arms on the adaptive grid): no OOM and **no
  `WALK refused` line anywhere** — the grown corridor's walk held `cellsInterior=47286` beside
  `GRID layer=fine windows=361 cells=205824 reach=475.0m cell=20.0m ms=6725.0`, 253 110 cells of the ceiling —
  and the answers are the ones the fine layer already gave (`LINE distance=18467.9m duration=1843.5s`,
  `forced=[Zone 3 kn]`, `step=10.0m`). The refusal's own end-to-end path was **not** reached on these routes,
  so it stays unproven until a route crosses the ceiling.

## Risks

- **A missed ribbon cell moves an answer at the shore.** The coverage test is the regression, and the mask is
  the only definition of the ribbon — a second definition would drift from it.
- **The window count multiplies the rasterizer's per-window edge sweeps — measured, and it is seconds.** Fat,
  few windows: 361 windows cost **6 725 ms**, about 12 ms each for a 576-cell tile, which is per-window
  overhead rather than per-cell work. The lever is fewer, fatter windows, and merging adjacent tiles along the
  coast is where it starts.
- **The axis-aligned box over-covers an oblique coast by up to `sqrt 2`** — `corridorChain`'s own KDoc names
  it. That is waste, never a change, and the readings decide whether it matters.
- **The band switched off means no mask**: `bandLaw(world)` is `null` unless `route.avoid.zone300.enabled` and
  a positive width, and with no mask the fine layer is passable **everywhere** over the corridor today — the
  same corridor-sized tile, and the crash without the waste. The plan decides: **no band, no fine layer** (the
  walk is the coarse grid alone, as `avoid`'s is), which is a behaviour change on that switch and is named,
  tested and reported as one.
- **The grown corridor's quad is the trigger, not the cause.** The reshape alone would leave the next longer
  route able to double past the heap again.

## Parked

- **The window count's own cost** — the reading prices it at ~12 ms a window, so the trade has moved from
  memory to time: merging adjacent tiles along the coast, or cutting the chain at a wider half-width, is the
  next cut. Its own reading decides, since widening trades the memory back.
- **A tile that is not a rectangle** — a segment plus a half-width, rastered as a rotated lattice. It would
  remove the `sqrt 2` over-coverage, and it is a lattice change rather than a box change. Resume condition:
  the reshape measured — done — and the over-coverage visible in its readings, which it is not yet.
- **The fine layer's necessity per plan** — a plan that wants no fine water at all. The seam kept open above
  is what would let it answer so.

## Open questions

- **The budget's home — decided**: a key, `route.walk.maxCells` with a 600 000 default, read through
  `AppConfig`, because every value in this corpus lives in the properties file. The alternative — a figure
  derived from the heap at build time — needs a bytes-per-cell constant nobody has measured.
- **The chain's half-width**: the band's reach plus the mask's width is the water's own edge; anything wider is
  over-coverage only. Phase 1's figures decide the value.
- **Whether the instrument settles the mechanism — settled**: 12 ms for a 576-cell tile is per-window work, so
  the rasterizer's edge sweeps dominate and the ring fill's own per-cell allocations are **not** the next
  lever. The window count is, and the instrument's own line is what said so.
