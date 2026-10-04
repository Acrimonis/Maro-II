# Context Hydration — Route — 2026-10-04

**Last Bake:** 2026-10-04 13:29 UTC — written by `#bake`; absence means never baked

**Directive trace:** This session landed Phase 5 of the adaptive grid — the seam between its two layers — so
of the five covered classes one was touched: orders were given and acted on (`#impl phase 5`, then `#commit`),
and the device was not read, no logcat fetched. No dependency was added and no machine-shaped data file was
opened. Every claim about the code in this record followed a read except the suite and build figures, which
rest on the implementing hop's own runs — named here, not closed.

## State

**The two layers now meet at their seam, and that was Phase 5.** `MultipassSearch.searchWalk` no longer keeps
a step inside its own layer: beside each layer's eight neighbours it asks `WalkWindows.crossLayerSlots`, backed
by the `SeamNeighbours` helper, for the cells **across the seam** — the exact `1 : 5` many-to-one relation the
shared origin makes arithmetic (a coarse cell's face meets five fine cells, its corner one, and a fine cell
reaches a coarse cell only on its block's face). Every edge is priced as the **destination cell's own
seconds-per-metre rate over the two centres' distance**, so a same-layer step keeps the uniform charge
`cellSec × multiplier` exactly and only the crossing is distance-true; the heuristic stays admissible because
every edge costs at least its distance at the pace.

**Three enabling changes carry it.** `CellIndex` gained a **layer** (default `0`, so every single-grid cell
and test literal reads as before), `SearchOutcome` gained **`costSec`** — the aim's own `g`, the reading the
clock test compares against the path's timed length — and the search's **`zonePriceSec` callback is handed the
destination cell's size**, so the seam's fine water is priced at 20 m rather than the interior's 100 m. A path
now resolves back to points on its own resolution (`RoutePassRunner` reads `windows.center(it.layer, …)`).

**The gate is the suite, as the hop ran it** — `apk-build.bat` green and the full unit suite at
`880 / 1 / 10`, the single red still `theFineCellRatioShipsAtFortyPercentOfTheCoarseCell`, `avoid`'s parked
ratio residue and the user's to settle. The two new [`SeamCrossingTest`](../../app/src/test/java/ykws/android/maro/spatial/multipass/SeamCrossingTest.kt:1)
cases are green: the g-versus-clock reading on a seam-crossing path, and a hybrid solve agreeing on total time
with a uniform fine solve.

**The fine window's shape is still the open defect, and the seam makes it live.** The fine layer is still a
tile spanning the **whole corridor** with the band as a membership mask on it — twenty-five times the coarse
grid's cells — but its water is now **walked**, not merely allocated, so the 25× is paid on purpose rather than
by accident. The plan's own code-health section rejects that shape, `GridTile(box, cellM)` cannot express a
strip, and **no phase in the plan owns reshaping it**; the epic's `## Todos` carries the gap.

**Three smaller findings stand beside it**, unchanged from Phase 4: the band's depth-gate arm is not evaluated
where the coast arm is, `WalkWindows.passableCount()` still sums both layers so a count there is no longer an
area, and the seam adjacency is sound by construction but **unconfirmed on a device** — an irregular band edge
links to the interior through fine-to-fine same-layer steps reaching the block face.

## Target Files
- `xTrack/Route/261004_FEAT_PLN_Route_hybrid-grid.md` — the plan of record: Phases 1–5 landed (Phase 5's seam now marked landed with its three named deviations), Phase 6 (local resolution) still in design
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassSearch.kt` — the seam-aware neighbour expansion and the one `relax` that prices every edge, plus `SearchOutcome.costSec`
- `app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt` — `SeamNeighbours` and `WalkWindows.crossLayerSlots` / `cellSizeM`
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt` — `CellIndex`'s layer
- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt`, `RouteFinePass.kt` — the layer-aware path read and the per-cell-size pricing callback
- `app/src/test/java/ykws/android/maro/spatial/multipass/SeamCrossingTest.kt` — Phase 5's two exit tests
- `xTrack/Route/FEAT_DSC_Route.md`, `xTrack/GLOBAL_CONTEXT.md` — folded and dated by this bake

## Next Step
Phase 6 — pull, snap and corner at **local resolution**: the pull takes no cell of its own, so what takes the
band's 20 m is `snapToCorners`' field, the `CornerSet` distances, `carveReachCells` and `openEndDisc`, so the
fine resolution survives into the drawn points. The fine window's corridor-sized shape stays the epic's open
defect until a plan owns it.
