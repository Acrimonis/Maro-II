<!-- scope: feature -->
# 261004_FEAT_PLN_Route_hybrid-grid

Status: in design — the avoid engine's first walk on a two-resolution grid, fine near the coast and the
depth gate, coarse in open water. Nothing is implemented.

## Purpose

The 50 m uniform coarse grid closes any passage narrower than about two cells — [`fineReSearch()`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:1007)
names it — so channels like the one between Pointe du Bacon and La Grenille read as land and the line
is forced around them. The fine re-search cannot help because it re-rasterizes only a swath of the
coarse line's own bounding box, which already bypassed the channel. The fix is to make the **first**
walk resolution-aware: fine where the water is constrained, coarse where it is open.

## The proposed model

1. **One ratio knob — fine : coarse = 1 : 4.** Near the boundary the cell is `coarseCell / 4`; in open
   water it is `coarseCell`. No distance-scaled ramp, no hand-set band width.
2. **Concrete sizes:** coarse **100 m** (twice today's 50 m), fine **25 m** (half today's 50 m).
3. **The fine band is one coarse cell wide** — 100 m from the inflated boundary (coast minus its margin,
   the depth gate minus its margin), which is exactly four fine cells deep.
4. **The open water pays for the band:** at ratio 4 the open-water raster is 4× cheaper per area than
   today and the band is 4× dearer per area, so the hybrid breaks even when the band covers 20 % of the
   corridor. A coastal ribbon is almost always well under that, so the first walk gets finer where it
   matters and cheaper overall.
5. **The first walk runs on the hybrid grid**, then the taut pull and the corner pass supply the drawn
   precision — see Assessment.

## Assessment

- **Sound.** This is a two-level adaptive grid, and it is the right trade: A* work is spent where the
  geometry is tight, not on a uniform fine raster over the whole corridor.
- **The 25 m fine cell is chosen against the margins, not in isolation.** The obstacle margin is 25 m and
  the depth margin 20 m; a channel leaves a free cell only when its clear width exceeds roughly
  `cell + 2 × margin`. At 25 m the cell is no longer the binding term — the margin is — so finer than
  25 m buys little while the 4× band cost grows.
- **The distance is to the inflated boundary**, not the raw edge: `distanceToCoast − obstacleMargin` and
  the depth excess `depth − minDepth − depthGateMargin`, the smaller of the two driving the cell.
- **The depth gate has no stored contour.** Exact distance to it is a distance transform of the depth
  field; the cheap proxy is to use the depth excess over the gate rather than a computed contour distance.
- **The open-water walk is much faster** — coarse cells collapse the open-water expansion count, and the
  A* spends its work near obstacles.
- **Precision comes from the pull, not the grid.** The taut pull and the corner pass operate on
  continuous geometry and already give sub-cell precision; the grid only decides passable versus blocked
  and prices the expansion. The second finer pass ([`fineReSearch`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:1015))
  then becomes a safety net rather than the only precision source.

## Decision — 1 : 4 is the sweet spot

- The ratio is chosen **1 : 4** — coarse 100 m, fine 25 m.
- **1 : 5 and 1 : 6 are rejected**: the break-even band coverage barely moves (20 % → ~22 % → ~23 %), so coarsening the open water further buys almost nothing while the coarse cell grows to 125 / 150 m.
- **The coarse cell's true bound is the smallest open-water feature the A* must still price** — a speed-zone ring or the 300 m band's edge — not the compensation math; 100 m keeps those resolvable, 125 m is acceptable only where zones run large.
- **The fine cell stays at 25 m**: below that the 25 m obstacle margin is the binding term in `cell + 2 × margin`, so finer buys no channel while the band's cost climbs.

## Property

- **Written in `maro.properties`** as of 2026-10-04, side by side and documented — the pair is in the
  file; the `fineRatio` consumer is owed in Phase 2.
- `route.avoid.grid.cellM` — the open-water coarse cell, shipped **100** (today's 50 became the base's
  new value).
- `route.avoid.grid.fineRatio` — one human-readable integer, shipped **4**: the near-obstacle cell is
  `cellM / fineRatio` (25 m). Clamped 1..8; 1 degenerates to today's uniform grid.

```
# How much finer the grid gets beside the coast and the depth gate: the open-water cell is this many
# times the near-obstacle cell. 1 = uniform, 4 = 100 m open / 25 m near. Clamped 1-8.
route.avoid.grid.fineRatio=4
```

## Target shape — first cut

- Two discrete layers, not a continuous mesh: one fine uniform raster inside the 100 m band, one coarse
  raster outside, stitched at the seam.
- `cellM_fine = coarseCell / 4`, `cellM_coarse = coarseCell`; the band is `coarseCell` wide from the
  inflated boundary.
- The A* neighbour step becomes resolution-aware: a cell reads its own size, and a coarse cell adjacent
  to fine cells expands across the seam by size lookup — still O(1) neighbours per cell.

## Perf

- Cell-count ratios: 50 m uniform = V; the hybrid is `0.25·A_open + 4·A_band` in units of V.
- Break-even at ratio 4: the band equals 20 % of the corridor area; below that the hybrid is cheaper
  than today, above it dearer. A coastal ribbon is typically far below 20 %.
- Rasterization runs once per solve over the band's fine cells; the open-water raster shrinks by the
  coarse-cell factor.
- Worst case — coast everywhere — degenerates toward the fine grid, ~4× today's cells; rare in practice.

## Phases

1. **Config experiment (no code)** — set `route.avoid.grid.cellM=25` and confirm the channel opens and
   the device A* cost is acceptable. This decides whether the rework is needed at all.
2. **Two-layer rasterize** — build the fine band and the coarse interior, marking cells by clearance.
3. **A* seam crossing** — resolution-aware neighbour expansion across the coarse/fine boundary.
4. **Pull and corner at local resolution** — thread the local cell size into the pull, snap and
   `TangentCorners` sets so the fine band's precision survives.
5. **Retire or demote `fineReSearch`** once the first walk already resolves the channel.

## Open questions

- Whether the depth gate needs a true contour distance or the depth-excess proxy suffices.
- Whether one band of four fine cells deep suffices, or the seam needs a second transitional layer.

## Risks

- The A* seam and the pass chain assume one `cellM` today; this touches [`AvoidGrid`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidGrid.kt:75),
  [`rasterize`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:341) and [`AvoidSearch`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidSearch.kt) —
  a real rework, not a property change.
- A wrong band width or seam re-opens the channel-closing bug this plan removes.
