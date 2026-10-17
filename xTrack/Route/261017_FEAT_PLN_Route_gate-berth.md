<!-- scope: feature -->
# Route — the 20–50 m gate berth on a per-grid distance field

**Date:** 2026-10-17 · **Status:** implemented · **Order:** the user's word — *at least 20 m away, then if there is room, move me out* — delivered as a **standoff berth**: full price at 20 m off the gate edge, fading to nothing at 50 m, fed by a per-grid distance field that also replaces the eight-bearing ring's per-read cost.

## Why

- The hard 20 m standoff is landed — the ring in `depthGateSource` plus the corner bows — and measured at `gateEdge=20.0 m` on every rung.
- The soft *move me out* half is absent: the preference is depth-valued, and on a flat 4 m bank there is no depth room, so the line sits exactly at the margin while 300+ m of open water stands beyond it.
- The ring's eight extra `depthAt` reads per cell and per mark cost ~3× the coarse stage and ~2× the fine stage; a per-grid distance field turns both into one read.

## The change

1. **Distance field.** `MultipassGrid` gains a λ-free `gateDistanceM` per cell; `rasterizeFrame` records the bare gate block (depth < `minM`) during its per-cell pass, one 8-neighbour BFS fills every cell with its distance to the nearest blocked cell (diagonals `√2·cellM`), and any cell **at or inside** `route.avoid.depthGate.marginM` is painted land — the dilation is **inclusive**, because a strict *closer than* paints nothing beyond the shallow cells themselves whenever the cell is not finer than the margin, which is exactly how a 20 m fine cell against a 20 m margin collapsed the standoff. `depthGateSource` returns to the bare step.
2. **Berth law.** `berthShare(dist, margin, outer)` and `berthPriceSecAt(dist, cellM, margin, outer, secPerM)`; keys `route.evolutive.berth.secPerM=0.1` and `route.evolutive.berth.outerM=50` replace `route.evolutive.depth.*`.
3. **Search.** `MultipassSearch.relax` adds `λ × berthPriceSecAt(walk.gateDistanceM(toIdx), destCellM, margin, outer, secPerM)`.
4. **Pull.** `PullSetup`/`PullContext` gain a `gateDistanceMAt: (LatLng) -> Double` plus the berth's `λ · margin · outer · secPerM`; `legClearCause` refuses a mark where `gateDistanceMAt(p) < marginM`, and `softPriceSec` adds the berth rate. The berth's `priceClearanceM` is `min(|d − margin|, |d − outer|)`, so the group proof survives and no per-interval fallback appears. The guard is built from `walk` so the lookup reads the mark's own layer — the coarse interior or the fine window.
5. **Tiles.** `FineTile` and `FineTileCodec` (v4 → v5) carry `gateDistanceM`; `TileKey` drops `depthBandSoftDepthM` / `depthBandSecPerM` and gains `berthSecPerM` / `berthOuterM`.
6. **Retirement.** `DepthBand`, `depthBandPriceSecAt`, `DEPTH_BAND_CURVE`, `depthBandSecOf`, `RouteGridPlan.depthBand()`, the field's `depthBand*` plumbing and the `route.evolutive.depth.*` keys leave; `PriceArm.DEPTH` stays as the berth's arm.
7. **Corner pass.** The bows keep the existing `gateStandoffClears` ring — few in number — or read the grid distance where one is in scope.

## Tests

- `BerthCostTest` replaces `DepthBandCostTest`: the law, a shelf where a cell 10 m off blocked water pays full and one 40 m pays a third, the per-metre equality across 20 m and 100 m cells, the BFS distance, and a fixture where a wider berth line beats a margin-hugging one at λ 1.
- `BerthPropertiesTest`, `TileKeyTest` / `TileTestFixtures` / `FineTileMapTest` key updates; `RouteDepthReadingTest` unchanged.

## Update — 2026-10-17 (device pass)

- The first device pass read `minDepth=2.5m gateEdge=5.0m` on every rung — the 20 m standoff did not hold, and the line crossed below the 3 m gate.
- Root cause: the dilation used **strict** `distance < marginM`. At a 20 m fine cell against a 20 m margin the smallest non-zero distance is exactly 20 m, so `20 < 20` painted nothing beyond the shallow cells and the standoff collapsed; the pull's refusal had the same strict `<`.
- Fix: the dilation and the pull's refusal are now **inclusive** (`<=`), matching the retired ring's `distance <= margin` — the cell standing exactly one cell off the wall is painted, exactly as the ring refused it. `BerthCostTest` pins the boundary.

## Open

- Whether λ alone brakes the berth — there is no cap — and the next device pass once the inclusive standoff is landed.
