<!-- scope: feature -->
# Plan — the end anchored on a fine cell with no water

**Status:** v2 (2026-10-21) · implemented · **Feature:** Route · **Created:** 2026-10-21

## The report

> A search from the current position (La Salis, 43.57044,7.13106) to the marker Enfer de Dante
> (43.53769,7.08255) found nothing — every rung `NO PATH`.

## What the device trace shows

- The aim anchors on the fine layer with no way in: `aimLayer=1 aimOpen=0` — a one-cell island.
- The aim's own cell is open water: `depth=34.0m coast=191.5m state=BAND→FREE`.
- The start is genuinely in the ribbon (`startLayer=1 startOpen=4`), and the same start lands a full
  11.7 km route to a point 80 m north (43.53840,7.08274, 19 m deep, 111 m off coast).
- Both aims share one coarse cell (r=37,c=37); only the fine layer differs.

## The root cause

1. The fine layer is the coastal ribbon only — a cell is fine water where `gateDistanceM <= depthCollarM`
   (170 m); beyond it the fine rasterizer leaves the cell LAND ([`MultipassGrid.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt:827)).
2. The aim, 191 m off the coast, stands beyond the collar: its fine cell is LAND.
3. The fine-window loop force-frees the end's fine cell anyway
   ([`RouteGridBuilder.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:307)
   → [`forceFree`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt:331)),
   manufacturing a passable fine cell.
4. [`endCellAt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt:518) anchors on the
   finest passable cell (layer 1); the fine-supersedes-coarse rule then hides the reachable coarse cell.
5. The search sees a one-cell island (`aimOpen=0`) and answers `NO_PATH`.

The marker's routing role is not involved (it carries no cost); the destination source only moves the
coordinate, and the same coordinate fails from the map centre.

## The fix

In the fine-window loop ([`RouteGridBuilder.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:303)),
skip `forceFree`/`openEndDisc` for an end only on the **fine** windows — `window.layer != 0` — whose cell
is LAND (`!grid.passable(cell)`), so [`endCellAt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt:518)
falls back to the coarse cell. The layer-0 (coarse) window keeps its unconditional force-free.

- The same loop also force-frees the **coarse** window (layer 0); a LAND coarse cell is the shore margin,
  which `forceFree` exists to waive. The guard must therefore be scoped to `window.layer != 0`, or a start
  whose coarse cell is `LAND→FREE` (the La Salis start) would stop anchoring and every search would fail.
- A LAND fine cell means "no fine water", never "shore margin": the ribbon has no margin to waive.
- This is the channel plan's S expectation: *"endCellAt anchors only on a cell the walk can leave."*

## Non-regression

- `avoid` is untouched (single grid, no fine windows).
- An end genuinely in the ribbon (the La Salis start) keeps its fine anchor — its fine cell is water,
  the guard passes it through.
- The fine-supersedes-coarse rule is untouched: it stays correct, and the fix only stops a cell from
  being manufactured where the ribbon has no water.

## Review findings

- [`RouteFinePass.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:212) also
  force-frees the two ends on a fine grid (the zone-crossing re-solve). It runs only after a coarse path
  exists and searches with `waiveEndSlots = false`, so a local no-path there abandons the splice, not the
  route. The device rerun after the fix must confirm the full pipeline still lands the route; if the aim's
  fine cell provokes a local splice refusal, guard that site identically.

## Tests

- Engine fixture: a two-layer world whose aim stands beyond the fine collar; the aim anchors on the coarse
  layer (`aimLayer=0`) and a route lands.
- The La Salis start keeps `startLayer=1`.
- `GateEndStepTest` and `DirectionSymmetryRepairsTest` stay green.

## Strongest objection

- An end one metre either side of the collar boundary flips between a fine and a coarse anchor — but the
  ribbon's edge is exactly where the fine refinement ends, and the coarse cell is the correct anchor there.

## Implemented

**2026-10-21, uncommitted.** The fine-window end opening is guarded to the ribbon's own water, so an end
standing beyond the fine layer anchors on the coarse cell instead of a manufactured one-cell island.

- **The fix:** in the fine-window loop ([`RouteGridBuilder.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:303)),
  `forceFree`/`openEndDisc` run on a fine window (layer 1) only where the cell is already passable (fine
  water); a LAND fine cell is skipped, and the layer-0 window keeps its unconditional free (the shore
  margin).
- **The trace:** the `PASS … NO PATH` line now prints `aimLayer=` and `aimOpen=`, the aim's own cell
  reading beside the start's.
- **Tests:** `FinePriorityWalkTest` gained `anAimBeyondTheRibbonAnchorsOnTheCoarseLayer` and
  `anAimInsideTheRibbonKeepsTheFineLayer`; `GateEndStepTest` and `DirectionSymmetryRepairsTest` stay green.
- **Build:** the full unit suite and `assembleDebug` are green.
- **Open:** the device pass on the La Salis → Enfer de Dante pair (the user's own run), and the fine pass's
  own force-free at [`RouteFinePass.kt:212`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:212)
  to be confirmed by that run.
