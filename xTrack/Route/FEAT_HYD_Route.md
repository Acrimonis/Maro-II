# Context Hydration — Route — 2026-10-04

**Last Bake:** 2026-10-04 13:01 UTC — written by `#bake`; absence means never baked

**Directive trace:** This session cut the second pass for the adaptive engine, shipped R96's provisional row
figures, repaired and then built Phase 4 of the adaptive grid, so of the five covered classes two were touched:
orders were given and acted on throughout, and the device was read only on the user's own word — the logcat
fetched after he reported the test had run, twice. No dependency was added and no machine-shaped data file was
opened. Every claim about the code in this record followed a read except the suite and build figures, which
rest on the implementing hops' own runs — named here, not closed.

## State

**The adaptive engine's first walk is two layers on one lattice family.** `EvolutiveGridPlan.firstWalkGrid`
answers two tiles over the corridor — 100 m and 20 m — on one origin with the coarse pair derived as exactly
5 × the fine one; cells are keyed by `(layer, row, col)` so two windows over the same water keep their own
cells; `GridContext` carries the walk's windows, added by **named construction** rather than a 27th positional
field; and `avoid` still answers one tile, so the multi-layer path stays unreachable for it and its answers are
provably unmoved. **Phase 5's seam is what would let a path cross between the layers**, and until it lands the
fine water is allocated, rasterized and never entered.

**Phase 4's cost is the fine window's shape, and that is the open defect.** The fine layer is a tile spanning
the **whole corridor** with the band as a membership mask on it, so it is twenty-five times the coarse grid's
cells: for a Salis→Lérins pair ≈ 16 400 coarse cells become ≈ 410 000 fine ones, ≈ 15 s of rasterise (scaled
from the 1.16 s per 30 843 cells the properties file itself records) and ≈ 60–80 MB of heap, doubling again on
a grown rung. The plan's own code-health section rejects that shape in words — *a mask still rasterizes and
allocates the whole span, which is the cost this phase exists to remove* — and **no phase owns re-shaping it**,
because `GridTile(box, cellM)` cannot express a strip. Three smaller findings sit beside it: the band's
depth-gate arm is not evaluated, and `passableCount` still sums both layers, so a cell count there is no longer
an area.

**R96 shipped the same day.** Every rung's row, a candidate's included, carries a provisional distance and ETA
from its first taut line — the pulled line's length and one enforced-limit read at the pull → snap boundary —
and the review left four items: the unmarked provisional, which is the user's call, and three hygiene findings,
all four now in the epic's `## Todos`.

**The gate is the suite, as the implementing hops ran it** — `apk-build.bat` green and the full unit suite at
`878 / 1 / 10`, the single red still `theFineCellRatioShipsAtFortyPercentOfTheCoarseCell`,
`avoid`'s parked ratio residue and the user's to settle.

## Target Files
- `xTrack/Route/261004_FEAT_PLN_Route_hybrid-grid.md` — the plan of record: Phases 1–4 landed with Phase 4's exit met, the seam (Phase 5) and the local resolution (Phase 6) still in design
- `app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt` — the lattice family and the `(layer, row, col)` key
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt` — `EvolutiveGridPlan`'s two tiles, and its second pass answering no region
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt` — the build order, the ends' discs and the context the two layers arrive on
- `app/src/test/java/ykws/android/maro/spatial/multipass/LatticeFamilyTest.kt` — the nesting and the identity, the phase's two new cases
- `xTrack/Route/FEAT_DSC_Route.md`, `xTrack/GLOBAL_CONTEXT.md` — folded and dated by this bake

## Next Step
Phase 5's seam — the resolution-aware neighbour with its edge priced from the two cell centres at the pace —
with the fine window's shape settled in the same pass, since that is the moment its water becomes walkable and
the first moment the 25× would be paid on purpose rather than by accident.
