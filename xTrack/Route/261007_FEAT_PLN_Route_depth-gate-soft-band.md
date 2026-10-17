<!-- scope: feature -->
# Route — a priced band beyond the depth gate's margin

**Date:** 2026-10-07 · **Status:** in design as **one standing question** — the band landed on `evolutive`,
was re-tuned to a per-metre cubic fall, and the 20 m standoff is enforced; the open question is the
**20–50 m berth** (standoff price vs depth price) ·
**Anchors re-verified 2026-10-16** · **Order:** the user's word
of 2026-10-07 — *"Margin is closest allowed. I would like to add an extra range and increase its cost. The
idea is not to prevent going through but if possible, go wider away from those low depth. For ex, min is
20 m, but make me pay a bit if I need to go within 50 m. So if it is open, I would steer 50 away. This
should apply to avoid and evolutive."*

## What is there today

- **The gate is a wall, never a price.** [`route.avoid.depthGate.minM`](../../app/src/main/assets/maro.properties:274)
  = 3.0 m ([`AppConfig.routeAvoidDepthGateMinM`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:425),
  clamped 0.5..50) reaches the cost field as [`depthGateSource(...)`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:258),
  a **`RouteCostSource.Hard`**: a known depth under it paints the cell land, so a rung can never cross it at
  any aversion. It is added to both fields — the lookup's
  ([`RoutePassPrimitives.costField`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:47))
  and the tiles' own ([`:188`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:188)).
- **The margin is a geometric standoff, not a price either.** [`route.avoid.depthGate.marginM`](../../app/src/main/assets/maro.properties:280)
  = 20 m ([`routeAvoidDepthGateMarginM`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:445), clamped 0..200) is read by the **corner pass**
  and the berth carve — [`RouteCornerPass.round(...)`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCornerPass.kt:145)
  refuses a bulge whose points fail `depthClearsGate`, and the ends' discs carve by it
  ([`RouteGridBuilder`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:127)).
- **The field already models a price.** [`RouteCostSource.Soft`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:92)
  carries a per-source `priceClearanceM()` — the distance to that source's own price boundary — which the
  pull's priced walk uses to prove a whole group of intervals from one read
  ([`MultipassPull`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:786)). The band and the rings are both
  soft sources priced per cell and per metre
  ([`softPriceSecPerMAt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:342)).
- **The resolutions are coarse.** Both engines walk a 100 m interior ([`route.avoid.grid.cellM`](../../app/src/main/assets/maro.properties:291),
  [`route.evolutive.grid.cellM`](../../app/src/main/assets/maro.properties:307)); the fine layers are 33.3333 m
  ([`route.avoid.grid.fineCellM`](../../app/src/main/assets/maro.properties:299)) and 20 m
  ([`route.evolutive.grid.fineCellM`](../../app/src/main/assets/maro.properties:313)). A 50 m standoff is **half a cell**.
- **The fine layer's reach is the plan's own answer, not the field's** — [`RouteGridPlan.fineWater`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:59)
  is where a plan states where its fine water may stand, and today `avoid` answers **none at all**
  ([`UniformGridPlan`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:79)) while `evolutive` answers the
  **coastal ribbon alone** ([`EvolutiveGridPlan`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:113)).

## What the change would be

A **depth-preference band**: between the wall and a new soft depth, water pays a small price that grows toward
the wall, so a rung is never blocked but the search prefers deeper water and the drawn line sits wider where
the water allows. Two shapes, and choosing between them was this plan's first question:

1. **Depth-valued price** — the cell's bilinear depth sets the price (deeper is cheaper), with no geometry.
   Cheap to build, works at any resolution, and reaches both engines through the one field. It prices the
   water *under* the cell rather than the *distance to* the shallow edge, so a corridor hugging a shoal pays
   the same as one 90 m off it in equally shallow water.
2. **Geometric standoff price** — the price scales with how far the cell's centre lies inside a standoff
   (the user's 50 m) from the shallow edge. This is exactly *steer 50 m away*, but the gate's wall has **no
   distance index** (unlike land, which has `distanceToCoastM`), so the standoff must be approximated on the
   grid or searched for.
3. **Both** — (1) to shape the price, (2) to own the distance.

## Feasibility and risks

- **The band is sub-cell, and the fork is narrower than it reads.** At a 100 m interior a 50 m standoff is one
  cell wide, so it is representable only as a cell price or on a fine layer — and the fine layer is no
  general one: `avoid` has none and `evolutive`'s is the coastal ribbon, so a fine-layer band is a
  **per-engine plan** change rather than a field one.
- **The priced walk's proof is the real obstacle, and both live families pay it honestly.** The group proof
  rests on each soft source declaring `priceClearanceM()`, and the two surviving soft families declare real
  boundaries ([`RoutePassPrimitives`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:78),
  [`RouteMarker`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteMarker.kt:187)). A **depth-valued** price has no analytic
  boundary, so the source must either declare a conservative radius (accepting a weaker proof) or fall back to
  per-interval reads — and that walk's cost is what the engine that carried this band paid and lost.
- **λ must scale it, and λ is the only brake there is.** Every slow source follows the pass's aversion, scaled
  at build ([`RoutePassPrimitives`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:64)), and the
  ladder's λ are fixed at 5 / 3 / 1 ([`maro.properties`](../../app/src/main/assets/maro.properties:402)–[:404](../../app/src/main/assets/maro.properties:404)).
  **No cap exists anywhere**, and neither the crown ([`RoutePassRanking`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRanking.kt:50))
  nor the clock ([`RouteEta`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt:26)) can see a price: a band the
  search takes is invisible to the report and to the ranking alike.
- **The clock must not move, and structurally cannot.** [`RouteEta`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt:26)
  declares itself *a clock, never a price*, reading neither λ nor the search cost, so the band can only move
  the line and its own times — never a limit.
- **Both engines, one change — half-holds.** `evolutive` holds a private
  [`RouteAvoidEngine`](../../app/src/main/java/ykws/android/maro/spatial/RouteEvolutiveEngine.kt:44) and differs by its plan alone, so the
  field carries a band to both without a second implementation; the **fine reach does not**, being each plan's
  own answer.

## Phases (sketch — 2026-10-07, the carrier retired)

1. **The key and its law** — a soft depth beside the gate (`route.avoid.depthGate.*`), its accessor, its clamp
   and the asset comment, with the price law written as a named function of depth and distance.
2. **The source** — the band as a `Soft` source in
   [`RoutePassPrimitives.costField(...)`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:29),
   scaled by λ, with a fixture proving a short deeper detour beats a shallow straight line, and — the wording
   this file used — that a **0-aversion pass** ignores it. That clause is superseded: no shipped rung is
   0-aversion, the aversion dial is retired, and the rungs' λ are fixed 5 / 3 / 1, so the fixture must be
   stated on the ladder's own **Coastal** rung (λ 1) instead.
3. **The walk's declaration** — the source's `priceClearanceM()` (a conservative radius) or the stated
   fallback to per-interval reads, with the read cost measured against the existing walk.
4. **The record** — the key's doc, the engines' reference, and the feature's state line.

## Open questions

- **The berth's currency.** The user's *at least 20 m away, then out if there is room* reads as a
  **standoff**, but the shipped price is in **depth**. On the last pair every rung sits at exactly
  `gateEdge=20.0 m` over a flat 4 m bank, because there is no depth room to move into. Whether the 20–50 m
  berth should be priced in **standoff distance** — fed by a per-grid distance-to-gate field, which would
  also replace the eight-bearing ring's per-read cost — is the decision now open. The per-metre cubic, the
  plateau's removal and the hard 20 m standoff above it are settled and recorded in `## Update — 2026-10-17`.

## Update — 2026-10-16: the design landed, and its home is leaving

- **This file's first question was answered by the code, in favour of its second shape** — the price is a
  **geometric standoff** measured by an 8-bearing radial ring scan on the gate's own predicate
  (`DepthBandLaw`, which left the tree with the engine), charged as
  a **per-metre gradient** rather than a flat surcharge, so neither the depth-valued reading nor a surcharge holds.
- **Its keys went to the engine rather than to the gate** — `route.selective.depth.priceSecPerM` (0.05, times λ,
  per cell) beside `route.selective.depth.bandExtraM` (25 m over the gate's own `route.avoid.depthGate.marginM`),
  so the band is **45 m** wide and a cell standing on the wall pays one cell's worth at λ 1.
- **The group proof was the obstacle, as this file said, and it was paid for rather than solved** — the source
  declares a **zero clearance**, so every priced walk on that engine falls back to per-interval reads; the cost
  this file put at ~80 % of the pull's was accepted there and nowhere else.
- **The "both engines, one change" premise did not hold** — `withDepthBand` is a door every engine's `costField`
  takes, so nothing in the code forbade `avoid` or `evolutive`; the scope was narrowed to the new engine by the
  2026-10-07 order's own conservatism, `avoid` staying the shipped default and `evolutive` the reference.
- **And that engine is now retiring** — a measured ~8× dearer fine build on fewer cells, 1.39×–2.12× the adaptive
  arm's warm cost, and this file's own zero-clearance coupling paid by its walks (the retirement plan above). The
  band, its collar, its law and its keys leave with the engine, so the ask this file was opened for — *a bit of
  cost between the margin and 50 m, on `avoid` and `evolutive`* — has **no home in the tree**. **Superseded
  2026-10-16: the band returned on `evolutive` — see `## Implemented`.**
- **What survives, and what this file holds now** — depth is handled by the **shared wall** (`costField`'s
  `depthGateSource`) and the **margin** (the corner pass and the berth carve) for every engine that remains, and
  whether a **soft preference returns on `evolutive`** — the engine the user actually drives — is the one question
  left standing here, with the reach, the magnitude and the detour brake as its three dials. **Superseded
  2026-10-16: the preference returned, on `evolutive` alone — see `## Implemented`.**

## Update — 2026-10-16: the independent review, and what it corrected

- **The order's two engines still carry no preference.** The word of 2026-10-07 named `avoid` and `evolutive`;
  depth is handled there by the **wall** and the **margin** alone, and no record in the corpus cancels the ask.
  So this file's subject is ordered work unfulfilled, not only an open discussion — the corpus reads the
  narrowing as *a choice*, and its reversal as *a choice too*
  ([`261016_FEAT_PLN_Route_selective-retirement.md`](261016_FEAT_PLN_Route_selective-retirement.md:44)).
- **Its ten anchors were re-pointed and one was dead** — every file:line this file cited had drifted (nine by
  twelve to a hundred and fifty lines, the deepest being `AppConfig`'s two accessors) and the tenth pointed at
  `RouteFineWater.kt:120` for `DepthBandLaw`, in a file that is now 58 lines and declares only `FineWater`,
  `FineWaterQuery` and `FineMask`. All are corrected above; the law's own anchor is gone with the law.
- **The ~80 % figure the feasibility section leans on is archived and λ-2.5-era** — its one home is
  13.8–14.3 s of 17.3–18.0 s at λ 2.5 ([`xxArchive/261004_FEAT_PLN_Route_pull-clearance-sampling.md`](xxArchive/261004_FEAT_PLN_Route_pull-clearance-sampling.md:185),
  with [`261004_FEAT_PLN_Route_price-walk-reads.md`](xxArchive/261004_FEAT_PLN_Route_price-walk-reads.md:17) and
  [`261006_FEAT_PLN_Route_fine-walk-price-step.md`](xxArchive/261006_FEAT_PLN_Route_fine-walk-price-step.md:42) at 67–80 %), so it
  describes a build that no longer exists rather than a live cost.
- **The shape this review would recommend is the depth-valued, per-metre price** — no new geometry, works at
  every resolution, reaches both rows through the one field, and per-metre matches *pay a bit as you come
  closer*, which a flat surcharge cannot express.
- **Its strongest objection, stated rather than buried** — it prices the water *under* the cell rather than the
  distance *to* the shoal, so a corridor hugging a shoal pays what one 90 m off it pays in equally shallow
  water, and the **50 m the order named cannot be expressed as a distance at all**. The geometric standoff that
  could is exactly the read-heavy shape whose ~25 of a ~29 ms fine build the retirement's numbers price.
- **The `Slow water` dial cannot carry it** — that dial weighs *seconds under a limit*
  ([`RouteEta`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt:242)) and is the crown's whole second term
  ([`RoutePassRanking`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRanking.kt:8)), so a depth price needs its
  own arm or it reports as `OTHER` ([`RouteCostField`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:361)) — the
  very lumping the marker work of 2026-10-16 just undid.
- **Left standing in the code, none of it this file's to fix** — [`TileKey`](../../app/src/main/java/ykws/android/maro/spatial/multipass/TileKey.kt:62)
  still calls the gate's margin *the depth price band's inner half* and
  [`MultipassGrid`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt:417) still says *within the depth collar of a
  gate-blocked cell*, both already owed by the retirement plan, while
  [`FineTileCache`](../../app/src/main/java/ykws/android/maro/spatial/multipass/FineTileCache.kt:13) still says a tile is valid for `evolutive`
  and `selective`, which the retirement did not list.
- **Decisions that stay the user's** — whether a preference returns at all; `evolutive` alone or both rows; the
  reach (the margin's 20 m, an extra 25–30 m, or the 50 m the order named); and the shape, the one of the four
  that changes the drawn line.

## Implemented — 2026-10-16

- **The band returned on `evolutive` alone, depth-valued and charged per metre of the water's own deficit** — the
  keys `route.evolutive.depth.reachM` (25) and `route.evolutive.depth.priceSecPerM` (0.05), the law
  `depthBandPriceSecAt` = `max(0, softDepthM − cellDepthM) × secPerM` with `softDepthM = route.avoid.depthGate.minM +
  reachM`, answered by the plan seam ([`RouteGridPlan.depthBand()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:72)),
  read by both fields ([`costField`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:29),
  [`tileCostField`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:192)), stored
  **λ-free per cell** and scaled by the pass's λ at read, its seconds under their own `PriceArm.DEPTH`, and
  [`FineTileCodec`](../../app/src/main/java/ykws/android/maro/spatial/multipass/FineTileCodec.kt:30) moved v3 → v4
  with the two band values carried in the tile key. `:app:testDebugUnitTest` green at 1299 tests with 12 pre-existing
  skips, and `apk-build.bat` packaging.
- **Its reach reads as metres of depth, not the order's planimetric 45 m** — `softDepthM` is 3 + 25 = **28 m of
  water**, so *steer 50 m away* is not what shipped; that gap is the user's word to close, not a further inference.
- **Its charge is per cell, not per metre of water** — the law ignores the cell's size while the base and the zone
  prices scale with it, so the 20 m ribbon pays per metre five times what the 100 m interior pays.
- **A field pricing a band declares a zero clearance**, so `evolutive`'s ring and band price walks fall to
  per-interval reads too — measured: 4818 price reads for 4834 marks against 149 for 5146 without the band, the
  arm's warm wall clock 53–59 ms against 23–29 ms.
- **The band is inert on the water the user drives** — the charge is per cell, so at λ 1 a shallow run pays only past
  ~1.25 km, at λ 3 past ~500 m and at λ 5 past ~250 m; on a gradual shelf the shift pays over kilometres. It steers
  over a **bank**, not over the shelf the order described — and since `route.engine.id` ships as `avoid`, nothing
  changes until `evolutive` is chosen.
- **Open** — the reach against the order's 45 m; the per-cell unit; the clearance cost paid by `evolutive`; the band
  gated by `route.avoid.depthGate.enabled`, which leaves the two `route.evolutive.depth.*` keys without a switch of
  their own; three stale KDocs ([`TileKey.kt:63`](../../app/src/main/java/ykws/android/maro/spatial/multipass/TileKey.kt:63),
  [`MultipassGrid.kt:412`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt:412) and
  [`:446`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt:446)); a codec round-trip test
  that never checks the new member; and a test count and APK size that rest on the hop's word, the log reading
  `FROM-CACHE`. The user's own device pass followed the same day — two acquisitions on `evolutive`, captured as
  [`route_depth_pass.log`](../../route_depth_pass.log:1) — and it found the band pricing **pockets** the pull then
  steps around, its seconds held out of play by the clock and the crown alike; the figures and the fix they point
  at, a windowed taunt with an allowance, live in
  [`261016_FEAT_PLN_Route_taunt-window-and-allowance.md`](261016_FEAT_PLN_Route_taunt-window-and-allowance.md).

## Update — 2026-10-17: the dials, the unit and the plateau

- **The band's dials are the outer edge and the rate** — `route.evolutive.depth.softDepthM=50` and
  `route.evolutive.depth.priceSecPerM=0.1`; the `route.evolutive.depth.reachM` key and its accessor are gone, so
  the water the price ends at is the order's own 50 m depth rather than the gate plus a reach.
- **The charge is per metre of water** — `depthBandPriceSecAt(band, depthM, cellM)` returns
  `secPerM × (the depth's share of the band)³ × cellM`, so the 20 m ribbon and the 100 m interior pay the same per
  metre; `depthBandSecOf` and `tileCostField` carry the cell size, and `theRateIsTheSamePerMetreAtEveryCellSize`
  pins it. The device evidence that drove it — a three-rung 5 kn pair with the whole run inside the 300 m band —
  showed the per-cell unit leaving the coarse grid five times cheaper per metre, where the offshore detour is
  decided.
- **No plateau stands above the wall** — the band's inner end is the gate's own `minM`, so the price falls in one
  straight line to the 50 m edge and a 4 m and a 7 m cell are different prices. The second device pair — 643 m,
  every metre inside the former 20 m plateau — returned the *same line at every λ*, because a flat price cancels
  out of the ranking; the `route.evolutive.depth.floorM` key, its accessor and the tile key's floor field were
  removed before the branch was recorded.
- **The fall is cubic** — the depth's share of the band is raised to the cube (`DEPTH_BAND_CURVE`), so twenty
  metres of water costs under a third of the wall's rate. On the 1.4 km pair that makes the Detour rung's saving
  (~474 s at λ5) cover a ~1 km offshore detour (~389 s), where the linear fall saved only ~48 s and the pair's
  rungs sat within 9–17 m; `theFallIsCubicInTheDepthShare` pins the curve.
- **Build** — the six scoped suites are green (DepthBandCostTest, DepthBandPropertiesTest, TileKeyTest,
  FineTileMapTest, FineTileExtractTest, FineTilePaceFreeTest). The full unit suite runs 1317 tests with **one
  failure that predates this change** — `PullAllowancePropertiesTest` expecting
  `route.avoid.pull.allowanceShare=0.02` while `maro.properties` ships `0.1`; neither file is in this change's set.
- **The LINE now carries the gate's own two readings** — `minDepth=` (the shallowest known depth on the
  drawn line) and `gateEdge=` (the closest it comes to the water the gate blocks, by an eight-bearing
  ring scan at a 5 m step), both read from the line's 10 m samples **inside** the trace sink, so a solve
  with the tag off pays neither; `RouteDepthReading` holds them and `RouteDepthReadingTest` pins the
  shelf geometry.
- **The new readings put the last pair on record** — λ1 runs 1 398 m with `minDepth=2.7 m` and
  `gateEdge=5 m`: the coastal rung rides the gate's own threshold and the drawn line **dips under the
  gate's 3 m between marks**, five metres off blocked water. The two detour rungs run 2 638 · 2 656 m at
  `minDepth=4.1 m` and `gateEdge=20 m`. The preceding 2.9 km pair read 4.9 m / 25 m on all three rungs.
- **The 20 m standoff is enforced** — on the user's word that 5 m was too close, `depthGateSource` gained
  its margin: `gateStandoffClears` samples an eight-bearing ring at `route.avoid.depthGate.marginM` (20 m)
  and the corner pass's bows use the same test, so a cell, a chord or a bow whose ring touches blocked
  water is refused, while `0` restores the bare step. That answers the sub-gate dip at its source — the
  pull's 20 m marks could pass over shallower water than either end, and the ring closes the gap. Its
  device pass reads `gateEdge=20.0 m` on all three rungs — the Coastal rung moved from 5 m to 20 m and
  its shallowest water from 2.7 m to **3.9 m** — at the price of a slower acquisition: coarse
  688–856 ms against 201–286 ms, fine 1.1–1.8 s against 0.56–0.77 s, and `passableUnique` 9 648 → 9 165.
- **Still open** — the berth's currency. The hard 20 m standoff holds and the cubic separates the rungs, but
  the 20–50 m berth the user asked for is invisible because the preference is in depth, not standoff; a
  per-grid distance-to-gate field would carry that berth and replace the ring's per-read cost. The zero
  clearance and the per-interval reads it buys stand beside it.
