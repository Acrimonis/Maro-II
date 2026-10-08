<!-- scope: feature -->
# Route — a priced band beyond the depth gate's margin

**Date:** 2026-10-07 · **Status:** in design, for discussion (point 1 of four) · **Order:** the user's word
of 2026-10-07 — *"Margin is closest allowed. I would like to add an extra range and increase its cost. The
idea is not to prevent going through but if possible, go wider away from those low depth. For ex, min is
20 m, but make me pay a bit if I need to go within 50 m. So if it is open, I would steer 50 away. This
should apply to avoid and evolutive."*

## What is there today

- **The gate is a wall, never a price.** [`route.avoid.depthGate.minM`](../../app/src/main/assets/maro.properties:283)
  = 3.0 m ([`AppConfig.routeAvoidDepthGateMinM`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:275),
  clamped 0.5..50) reaches the cost field as [`depthGateSource(...)`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:229),
  a **`RouteCostSource.Hard`**: a known depth under it paints the cell land, so a rung can never cross it at
  any aversion.
- **The margin is a geometric standoff, not a price either.** [`route.avoid.depthGate.marginM`](../../app/src/main/assets/maro.properties:289)
  = 20 m ([`routeAvoidDepthGateMarginM`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:295),
  clamped 0..200) is read by the **corner pass** and the berth carve — [`RouteCornerPass.round(...)`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCornerPass.kt:53)
  refuses a bulge whose points fail `depthClearsGate`, and the ends' discs carve by it.
- **The field already models a price.** [`RouteCostSource.Soft`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt)
  carries a per-source `priceClearanceM()` — the distance to that source's own price boundary — which the
  pull's priced walk uses to prove a whole group of intervals from one read. The band and the rings are both
  soft sources priced per cell and per metre.
- **The resolutions are coarse.** Both engines walk a 100 m interior ([`route.avoid.grid.cellM`](../../app/src/main/assets/maro.properties:303),
  [`route.evolutive.grid.cellM`](../../app/src/main/assets/maro.properties:319)); the fine layers are ~33 m
  (`avoid`) and 20 m (`evolutive`). A 50 m standoff is **half a cell**.

## What the change would be

A **depth-preference band**: between the wall and a new soft depth, water pays a small price that grows toward
the wall, so a rung is never blocked but the search prefers deeper water and the drawn line sits wider where
the water allows. Two shapes, and choosing between them is this plan's first question:

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

- **The band is sub-cell.** At a 100 m interior a 50 m standoff is one cell wide, so it is representable only
  as a cell price or on the fine layer — the same constraint the margin's own doc names ("made representable
  at the grid's resolution without a finer walk").
- **The priced walk's proof is the real obstacle.** The group proof rests on each soft source declaring
  `priceClearanceM()`. A **depth-valued** price has no analytic boundary, so the source must either declare a
  conservative radius (accepting a weaker proof) or keep per-interval reads — and that walk already carries
  ~80 % of the pull's cost, so a new per-interval source on open water is the wrong place to spend.
- **λ must scale it.** Every slow source follows the pass's aversion, so a 0-aversion pass must ignore the
  band and the around pass respect it, or the band becomes a hidden hard constraint.
- **The clock must not move.** The band is a preference: the reported time stays the limits in force, so only
  the drawn line and its own times change — and any time movement must come from the line, never a new limit.
- **Both engines, one change.** `evolutive` holds a private `RouteAvoidEngine` and differs by its plan, so the
  field carries the band to both without a second implementation — only the cell pair differs.

## Phases (sketch)

1. **The key and its law** — a soft depth beside the gate (`route.avoid.depthGate.*`), its accessor, its clamp
   and the asset comment, with the price law written as a named function of depth and distance.
2. **The source** — the band as a `Soft` source in [`RoutePassPrimitives.costField(...)`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:39),
   scaled by λ, with a fixture proving a short deeper detour beats a shallow straight line and that a
   0-aversion pass ignores it.
3. **The walk's declaration** — the source's `priceClearanceM()` (a conservative radius) or the stated
   fallback to per-interval reads, with the read cost measured against the existing walk.
4. **The record** — the key's doc, the engines' reference, and the feature's state line.

## Open questions

- In the user's example, is **20 m a depth and 50 m a depth, or 50 m a distance**? The sentence reads both
  ways, and the two shapes above are exactly those two readings.
- Should the price be **per metre** toward the wall or a **flat surcharge** on a shallow cell?
- Does the band belong to the depth gate (keys beside `minM` / `marginM`) or to the slow-water family that λ
  already owns?
- What must **not** happen: a rung choosing a much longer open-water line to shave a few metres of shallow
  water. Is there a cap on the detour the band may buy, or is λ the only brake?
