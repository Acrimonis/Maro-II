<!-- scope: feature -->
# Route — the depth walls and the weighted band, all measured to the water

**Date:** 2026-10-17 · **Status:** in design · **Order:** the user's three rules, verbatim — *depth exclusion = wall · 20 m out of the depth exclusion = wall · 20→50 m out = weighted avoidance to give it space when possible* — and the agreement that the weighted band's ends still sit on the soundings rather than the water.

## Where the three rules stand

- **Depth exclusion = wall**: the gate paints every cell shallower than 3 m land — in place.
- **20 m out of the depth exclusion = wall**: the pull's [`legClearCause`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:807) now refuses any mark whose 20 m ring touches the actual shallow water through [`gateStandoffClears`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:265), so the drawn line cannot come inside 20 m of the water — landed and exact.
- **20→50 m out = weighted avoidance**: the berth prices that band and steers the line out where there is room — but its inner and outer ends are still counted from the depth **soundings**, which sit a few metres inside the water, so the band starts ~5 m inside the 20 m wall. This is the one change left.

## The change

1. **The weight's ends measured to the water.** [`berthLawOf`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:185) answers `marginM = routeAvoidDepthGateMarginM + halfCell` and `outerM = routeEvolutiveBerthOuterM + halfCell`, where `halfCell = routeEvolutiveGridFineCellM / 2` — so the band is **20→50 m of water**, sitting exactly outside the 20 m wall, not 5 m inside it.
2. **One half-cell, one home.** A single helper (`depthWaterShiftM()`) is the only place the shift is written; `berthLawOf` and the tile key read it, so a moved fine cell moves the weight's ends and the cached tiles together and never pulls them apart.
3. **Consumers unchanged.** `berthPriceSecAt` still takes `marginM`/`outerM`; only their values move, so the search, the pull and the fine pass price the shifted band with no further edit.
4. **The ring is the wall, not a refinement.** The 20 m wall is now exact at the mark, so the earlier "re-solve the crossing" refinement is **dropped** unless a device pass shows a crossing survive it — the ring is the enforcement, and the berth is the price beyond it.

## Tests

- `BerthCostTest`: the band is 20→50 m of **water** — `berthShare` full at `routeAvoidDepthGateMarginM + halfCell` from a sounding, zero at `routeEvolutiveBerthOuterM + halfCell`.
- The ring's 20 m wall stays pinned by `RouteDepthReadingTest` and `AvoidDepthGateTest` (unchanged by this plan).
- `TileKeyTest`: the berth's outer edge already keys the tile; the shift rides it, so no new flip.

## Open

- The half-cell is taken as `routeEvolutiveGridFineCellM / 2`; the depth raster's own sounding width may differ, and whether to read it once on the device is the plan's one open number.
