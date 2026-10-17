<!-- scope: feature -->
# Route — the low-depth fine collar and the tile-seam halo

**Date:** 2026-10-17 · **Status:** in design, ready to implement · **Order:** the user's word — *a fine grid around low depth, at a distance of 150 m around the 20 m exclusion zone*, plus the tile-seam halo found while asking whether the fine pass assesses that distance correctly.

## Why

- The gate berth (20→50 m weighted) and the 20 m exclusion are read from the grid's `gateDistanceM`, but the fine 20 m layer is anchored to the **coast alone**: [`EvolutiveGridPlan.fineWater`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:126) answers one coast band `0.0..(marginM + baseCellM)` = `0.0..150 m`, and [`FineMask`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFineWater.kt:47) carries that coast band only.
- An offshore low-depth bank (the pocket near 43.543, 7.132 in the last acquisition) therefore sits only on 100 m coarse cells: no 20 m standoff, no usable 20→50 m gradient — one coarse cell at best. That is why the standoff read ~15 m and the weight did nothing.
- Separately, [`buildFineTile`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:553) rasterizes only the tile's own 1280 m block, so its BFS never sees shallow water across a seam; a cell just inside a tile whose low depth lies in the neighbour reads "no shallow", and both the exclusion and the weighted price drop there.
- **The collar cannot reuse the berth's distance field as it stands:** [`dilateGateGrid`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt:620) is capped at `max(gateMarginM, berthOuterM)` = 50 m, so every cell beyond 50 m off the shallow reads `MAX_VALUE`. A 170 m collar measured on that field would see "no shallow" between 50 and 170 m and keep no fine water — reproducing the gap. The field's reach must be raised to the collar's.

## The change

1. **The collar's reach, one home.** `depthCollarM = routeAvoidDepthGateMarginM + (marginM + baseCellM)` = 20 + 150 = **170 m** — the gate's own exclusion plus the same `margin + one coarse cell` the shore ribbon uses. It is a plan-level value ([`EvolutiveGridPlan`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:104)), carried on `FineWater` and `FineMask`, and keyed.
2. **The distance field reaches the collar.** `rasterize` / `rasterizeWindow` gain `collarReachM` and the BFS cap becomes `max(gateMarginM, berthOuterM, collarReachM)` — so `gateDistanceM` is known out to 170 m. The berth price is unmoved (its share is zero beyond 50 m); only the mask reads the longer reach.
3. **Membership.** [`applyFineMask`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt:592) keeps a passable cell if its **coast distance** is in `0.0..(marginM + baseCellM)` **or** its **gate distance** is `<= depthCollarM`; every other passable cell is painted land. It runs after the BFS, so one field serves the berth and the collar.
4. **The cut.** The builder harvests the corridor's gate-blocked cells from `world.depthAt` **at the fine lattice (20 m)** — never the coarse lattice, which can miss a 40 m bank — and grows the fine windows' boxes around them by `depthCollarM`, unioned with the coast windows and snapped to the fine lattice. A bank thus gets its own fine window.
5. **Tile halo.** `buildFineTile` rasterizes the tile block grown by `haloCells = ceil(collarReachM / fineCellM)` = 9 fine cells (180 m), runs the gate BFS over the grown block, and `FineTile.extract` keeps only the 64 × 64 inner block — so a member at the inner edge sees shallow across the seam. The halo is **derived** from `collarReachM`; it is never a second key.
6. **TileKey.** Gains `depthCollarM` (the reach). `berthOuterM` already keys the berth; the collar reach depends on the gate margin, the obstacle margin and the coarse cell, so keying `depthCollarM` itself makes any of those moves a rebuild, never a stale tile. The halo needs no key of its own (step 5).
7. **Search / pull unchanged.** The search reads `walk.gateDistanceM`, the pull reads `walk.gateDistanceMAt`, the fine pass prices through `berthRate` — all already wired; this plan only makes the field correct (finer around banks, continuous across seams).

## Cost

- The halo grows a tile's raster from `64²` to `82²` cells (~1.6×) **at build time only**; the stored tile stays its 64 × 64 inner block, so no stored byte is added.
- The collar's BFS reaches 170 m instead of 50 m, so more cells are visited per window build — a one-time cost per tile, paid once and cached.
- The collar adds tiles only where shallow water lies **beyond** the 150 m coastal ribbon — offshore banks, a handful here; the shore's shelf is already inside the ribbon.

## Tests

- A tile built with the 9-cell halo: a member at the inner block's edge reads the shallow distance of a cell in the halo (the seam crossing), instead of `MAX_VALUE`.
- A collar case: a shallow patch beyond the ribbon is fine water (a 20 m cell) and its 20→50 m band is priced; a cell beyond `depthCollarM` in open water stays coarse.
- `TileKeyTest`: the `depthCollarM` flip moves the key; the halo moves nothing on its own (derived).
- The berth price is unchanged by the longer reach: `berthPriceSecAt` still reads zero beyond `outerM`.

## Open

- Whether the cut grows around each harvested shallow cell or around their merged mass; the builder's one open choice.
- Whether a seam still exists at a window boundary after the tile halo (two windows, not two tiles) and, if so, the same rule for the window assembly.
