<!-- scope: feature -->
# Route — the `selective` engine: selective fine water and a conservative depth price

**Date:** 2026-10-07 · **Status:** in design, revised 2026-10-07 — the open points settled on the user's word:
collar widths one key each at 100 m, the depth law a per-metre gradient with an extra 25 m band riding the fine
layer, the nudge scoped to `selective` alone, its own `route.selective.*` keys, and the French label `Sélective`
· **Order:** the user's word of 2026-10-07, from points 1 and 4 of the algorithm review — *"the approach will be
to be conservative and derive yet another algorithm `selective`, keeping in common all we can, and make this
evolution on that one"*, with the depth price's proof taken as the focus and the exclusion setting treated as
static in practice.

## Why a third engine

`avoid` walks one coarse grid with no fine layer; `evolutive` walks a coarse grid plus a fine layer cut as a
coastal ribbon (today: `bandReachM(band, outsideMargin) + marginM + cellM` = 500 m, derived in
[`fineWaterReachM`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:360), never a
named constant). The ribbon is a blanket, so it pays fine cells where nothing decides and misses shallow water
and zone edges further out. `selective` replaces the blanket with a **union of thin collars** where the decisions
actually are, and adds a **depth preference** priced conservatively. A third engine rather than a change to
`evolutive`, so the two behaviours stay available to choose from and neither ships a regression the other
carried.

## What stays common — almost everything

- **One engine class per row, in the shipped shape.** `selective` is a `RouteSelectiveEngine` that holds a
  private [`RouteAvoidEngine`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:151) and
  forwards the three seam calls, exactly as [`RouteEvolutiveEngine`](../../app/src/main/java/ykws/android/maro/spatial/RouteEvolutiveEngine.kt:28)
  does; the walk it runs differs by its plan alone. The pipeline, the runner, the fine pass, the corner pass,
  the clock, the seam and the flow are untouched.
- **`avoid` and `evolutive` are kept exactly as they are**, as the backup and the reference: neither takes the
  depth nudge nor changes a key, a cell or a drawn line.
- **The plan seam gains the one decision that is the whole difference — but that decision does not live there
  today.** [`RouteGridPlan`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:29)
  has exactly two methods, `firstWalkGrid` and `fineCellM`; the fine layer's *water* is currently the builder's
  own cut — [`buildLayeredGrid`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:198)
  answers an empty window list when `bandSpec == null`, else [`fineWindowBoxes`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:382)
  over the coast. Phase 1 therefore **moves** that cut onto the seam as a new `fineWater(...)` decision, and the
  fixture proves `evolutive`'s windows are byte-identical, so `evolutive` does not move by a cell.
- **The dropdown gains one row.** [`RouteEngineChoice`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngineChoice.kt:23)
  (an id, a label and a factory per engine) gets a `selective` entry and one label per locale; the factory wires
  the new plan into `RouteSelectiveEngine`. [`RouteAvoidEngine.planName()`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:701)
  is generalized to read a plan's own name rather than special-casing `EvolutiveGridPlan`, so the trace names
  `selective` truthfully. Nothing else about the engine choice changes.

## What is new

1. **The selective mask.** A plan-level `fineWater(...)` returns the union of four collar regions in **geographic
   terms (pre-snap)** — the portable half, which depends only on the coast, the zones, the depth and their
   stamps:
   - the shoreline, 0–100 m off the coast (one read: `distanceToCoastM`),
   - a collar straddling the 300 m band's outer boundary (a second threshold on the same read),
   - a collar around each priced zone's boundary (polygon distance, the expensive part),
   - the water within 100 m of a cell the gate blocks (a dilation of the shallow wall, a raster morphology on
     the depth field).
   The builder snaps the union to the corridor's lattice and merges into boxes. **The merge is reusable, the
   marking is not**: [`fineTileGrid`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:419)
   marks tiles only from coast *segments* grown by one `reachM`, so the coast collars reuse `mark(a, b)` as it
   stands, the zone-rim collar needs a new ring-edge marking, and the depth dilation needs a new raster-morphology
   marking — three marking passes, one shared `mergedRuns`.
2. **Independent of the band.** The shoreline and depth collars must exist even with
   `route.avoid.zone300.enabled` off — today the two-layer build gives `bandSpec == null` an empty window list,
   so a fine layer dies with the band. `selective`'s mask answers its own water, not the band's, and that gate
   is `evolutive`'s alone. The builder's `bandSpec == null` short-circuit is replaced by a consultation of the
   plan's `fineWater(...)`.
3. **The rim is deliberate.** A large zone's interior is uniformly slow and is priced by the limit in force
   anyway; the rim is where a crossing is decided, so the interior stays coarse by design rather than by
   accident. **The collar widths are one key per collar, each starting at 100 m** — four keys, one per collar,
   so a width is tuned without moving the others, each snapped to the fine cell.
4. **The window mask must become the collar membership, or the collars die at the rasterizer.** Every fine window
   today is rastered with `bandMask = true` ([`RouteGridBuilder`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:230)),
   and [`applyBandMask`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt:505) paints
   every passable cell beyond `marginM + cellM` of the coast as land — exactly the water the zone-rim, band-outer
   and shallow collars stand in. `rasterizeWindow`'s mask is generalized from the coast ribbon to a
   collar-membership predicate derived from the snapped union, with the interior layer keeping the beyond-mask.
5. **The depth preference — three integration points, not two.** A `Soft` source that prices the water beside the
   shallow wall: the price grows **per metre toward the wall** across a band whose width key starts at an **extra
   25 m beyond the gate's own margin**, scaled by the pass's λ. The read is the raster the shallow collar already
   builds — the gate's blocked cells carried into the water — because the gate's own wall, unlike the coastline,
   carries no distance index. The search prices λ-scaled slow water off the grid's stored **limits** through
   [`MultipassSearch.stepSec`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassSearch.kt:204),
   while the rasterized field is built λ-free with no soft source — so a depth price scaled by λ is **neither** a
   stored limit **nor** a λ-free `sourceCostSec`. The integration is therefore:
   - a **λ-free depth-price coefficient** written per cell by the rasterizer (a new grid array, like the limits
     but a scalar field, never seconds),
   - the **A\* read-time price**: `MultipassSearch.stepSec` scales that coefficient by the pass's λ,
   - the **pull's guard field**: a `withDepthBand` door in [`costField`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:29),
     mirroring the existing `withZones`/`withBand` doors, reading the same law.
   All three read the one law, or the pull prices water the search never priced — the disagreement the guard
   exists to prevent. The price **rides the fine layer by construction**: the shallow collar guarantees a fine
   grid around the wall, so the band is always drawn finely and no coarse-reach question arises. It is scoped to
   `selective` alone — `withDepthBand` is on for the new engine and off everywhere else.
6. **The conservative proof.** The priced walk's group proof needs each soft source to declare the distance
   within which its own price cannot change. A per-metre price over a raster distance field has no analytic
   boundary index, so the **conservative declaration is that raster's own cell size at that point** — and **zero
   wherever the distance can change within one price step**, which is the honest floor: a group is provable only
   within that radius, and no radius means no grouping over the rim. The consequence is measured with the
   existing `priceReads` instrument, never assumed; the fallback if it reads too hot is the flat shallow-cell
   surcharge, which has a wider, still-honest clearance.
7. **The mask cache — the geographic union, not the snapped boxes.** The fine lattice's origin is derived from
   the corridor each arm, so a box list snapped to one arm's lattice does **not** tile the next arm's lattice.
   What is portable is the mask **before** the snap — the union of collar regions. Cache that geographic union,
   keyed on the generation stamps (the tree already keys the depth raster on `gridTimestampMs` + the cutoff in
   [`RasterCache`](../../app/src/main/java/ykws/android/maro/data/depth/RasterCache.kt:45)), the switches, the
   depth cutoff **and the collar widths** — a width change moves the union, so it is key, never default — and
   snap-and-merge per arm, which is the cheap half. The zone harvest is per-corridor-box, so the cache holds the
   region-wide union and each arm intersects its own box. The excluded-zone set is **treated as static** on the
   user's call; if that proves wrong the set joins the key later at one line of cost.

## Phases

1. **The seam** — `fineWater(...)` on `RouteGridPlan` answering the pre-snap mask, `EvolutiveGridPlan` answering
   today's coast ribbon, and `buildLayeredGrid` refactored to snap-and-merge the plan's answer instead of the
   `bandSpec`/`fineWaterReachM` derivation; a fixture proving `evolutive`'s windows are byte-identical.
2. **The four collars** — each behind its own fixture and its own 100 m `route.selective.*` width key, then the
   union, then the lattice alignment and the merge into boxes, with the cell count read per collar. The coast
   collars reuse the segment marking; the zone-rim and depth-dilation collars add their own marking passes onto
   the shared `mergedRuns`.
3. **The window mask** — `rasterizeWindow`'s band-mask generalized to the snapped collar membership, so the
   collars survive the rasterizer; the interior layer keeps the beyond-mask; a fixture proving a zone rim in
   open water stays fine water.
4. **The depth price** — the λ-free grid coefficient, the `MultipassSearch.stepSec` read-time scaling, and the
   pull's `withDepthBand` guard door, under the **per-metre gradient** whose band starts at an extra 25 m beyond
   the gate's margin and rides the fine layer, scoped to `selective`; three fixtures: a deeper detour wins when
   it is short, a 0-aversion pass ignores the band, and the search and the guard price one point identically.
5. **The cache** — the geographic-union cache keyed on the stamps, switches, depth cutoff and the
   `route.selective.*` collar widths, reusing the raster cache's key shape.
6. **The engine** — the `selective` entry, `RouteSelectiveEngine` in `RouteEvolutiveEngine`'s shape, the
   generalized `planName()`, its label in both locales (French `Sélective`) and the factory; the dropdown row,
   with [`route.engine.id`](../../app/src/main/assets/maro.properties:169) left on `avoid`.
7. **The record and the reading** — the engines' reference, the feature's state, and a device pass on a coastal
   route (the user's own) reading the line near the shore and the reported clock.

## Risks

- **The depth clearance may cost reads.** A conservative, per-source-cell clearance can force per-interval
  reads over shallow water; it is measured first and the flat-cell fallback is the lever if it is too hot.
- **The gradient's proof is the fragile half of the law.** A per-metre price steps with the raster distance
  field, so its clearance is a single cell and the group proof is weak; the Phase 4 `priceReads` reading decides
  whether the shipped law stays the gradient or falls back to the flat surcharge.
- **The search-side coefficient is the new half of an old risk.** The grid gains a third read-time price beside
  the two limit families, and it must stay λ-free in storage and λ-scaled in the read or the three-rung sharing
  breaks — the same discipline the limits already keep.
- **The zone rims are the expensive half of the mask derivation**, and a zone-dense coast can blow the walk's
  cell ceiling — the merge and the budget have to be designed, with a graceful degrade rather than a refusal.
- **Multiple depth resolutions.** The shallow wall's position — and so the distance the price reads — differs per
  point (coarse EMODnet, fine Litto3D, NoData), so the conservative clearance is per-point, not one number.
- **The mask generalization touches the rasterizer's membership sweep**, which is the same sweep the margin
  uses — the collar predicate must keep the coast agreement that sweep preserves, or the fine layer and the
  land disagree about where the water is.
- **The exclusion-set assumption is the cheapest possible risk** — if the set turns out to move, the key gains
  it in one line.
- **A third engine is a second behaviour to keep green**, but it shares the whole pipeline, so the cost is one
  plan, one composition class and one dropdown row, not a second codebase.

## Open questions

None outstanding — the four points closed on the user's word of 2026-10-07:

- **The depth price's reach** — the fine layer, always: the shallow collar guarantees a fine grid around the
  wall, so the band is never drawn on the coarse interior.
- **The depth preference's scope** — `selective` alone, `withDepthBand` on for the new engine and off
  elsewhere; `avoid` and `evolutive` are kept as the backup and the reference, which **narrows** the point-1
  scope recorded in [`261007_FEAT_PLN_Route_depth-gate-soft-band.md`](261007_FEAT_PLN_Route_depth-gate-soft-band.md:8).
- **The fine cell and the collar widths** — the third engine gets its **own** `route.selective.*` fine-setting
  keys, for the fine cell and each collar width.
- **The label and the default** — French `Sélective` (English `Selective` proposed to match
  [`Adaptive`](../../app/src/main/res/values/strings.xml:732)), and `selective` is not the default:
  [`route.engine.id`](../../app/src/main/assets/maro.properties:169) stays `avoid`.
