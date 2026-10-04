# Context Hydration — Route — 2026-10-04

**Last Bake:** 2026-10-04 12:10 UTC — written by `#bake`; absence means never baked

**Directive trace:** This session ran the adaptive grid's Phase 2 on the device and then cut what the reading
proved unnecessary, so of the five covered classes two were touched: orders were given and acted on — deploy
with the logcat, two `#impl` runs, this bake and the commit — and the device was touched **only on the user's
own word**, the logcat fetched after he reported the test had run, twice. No dependency was added and no
machine-shaped data file was opened. Every claim about the code in this record followed a read except the suite
and build figures, which rest on the implementing hops' own runs — named here, not closed.

## State

**Phase 2's reading is taken, and it moved a shipped decision.** Two device rounds on `evolutive` (100 m coarse,
20 m fine; five arms over two routes at 37, 20 and 5 kn) gave the open-water A\* cost — 31–31 129 expansions over
corridors of 6 930–46 428 cells, the λ=5 rung closing 59–99 % of the passable water — and the deviation that
pinned the corridor's half-width: **61–230 m forward and 84–185 m back**, four arms at or under 100 m, so **150 m
stands and the clamp never rose to 300**. The instrument that read it is dev-only, built behind the `MaroRoute`
tag's own level: `DEVICE PASS` per rung, and `DEVICE DEV` per rung comparing the kept line against a **fine
reference** walked over the line's own span rather than the plan's corridor, which no wall caps.

**Its by-product was a retirement, on the user's word.** The corridor re-walk was **kept once in five arms** —
that once on a λ=0 tie the priced comparison cannot refuse — while costing 2.0×–5.2× the coarse pass and, on one
grown 46 428-cell rung, **53.6 s against 12.2 s**. `EvolutiveGridPlan.secondPassRegions` therefore answers the
interface's own *no region can be cut*: `evolutive` runs no second pass, **`avoid` is untouched**, the fine cell
stays for the clock's step at its three sites and for the plan's metres answer, and `finePass`'s re-tension — the
cheap half, and the half that produced every visible change — stays live.

**R96 shipped in the same session.** Every rung's row, a candidate's included, carries a **provisional distance
and ETA** from its first taut line: the pair is taken at the pull → snap boundary from the **pulled** line alone,
rides `RouteUpdate.provisional` as an engine-neutral optional, lands per page, and is replaced by the settled
figures when that rung's terminal update arrives.

**The gate is the suite, as the implementing hops ran it** — `apk-build.bat` green and the full unit suite at
`876 / 1 / 10`, the single red still `theFineCellRatioShipsAtFortyPercentOfTheCoarseCell` at
`RouteAvoidEngineTest.kt:632`, `avoid`'s parked ratio residue and the user's to settle.

**What is open.** Four should-fix items the `#implement` review left: the haversine length sum duplicated in
`RoutePassRunner.pulledLengthM` and `RouteAvoidEngine.lineLengthM`; the seat's class KDoc not naming its
now-unconditional provisional emission; one clock read thrown away where `RouteFinePass` discards the pair it
computes; and **the unmarked provisional** — a figure that will move by up to a percent shown at settled weight,
which is a visible change and therefore the user's call rather than the code's. The parked corridor machinery —
the chain, the lattice and the window walk — stays in the tree as the two-layer first walk's next user, and
`route.evolutive.fine.corridorHalfWidthM` is now read by nothing in `app/src/main`, stated rather than implied.

## Target Files
- `xTrack/Route/261004_FEAT_PLN_Route_hybrid-grid.md` — the plan of record: Phases 1–3 landed, Phase 2's reading recorded, Phase 7 settled for `evolutive`, the two-layer walk and its seam still in design
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt` — the plan seam, and where `evolutive`'s second pass answers nothing
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt` — the surviving seat: the re-tension, the zone crossing re-solve, and the dev-only `referenceWalk`
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — the composition, the ladder of fixed-λ rungs, and the `DEVICE` readings behind `logEnabled`
- `app/src/main/java/ykws/android/maro/spatial/RouteEngine.kt`, `app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt`, `RouteViewModel.kt`, `RouteConfirmPanel.kt` — R96's provisional pair, from the seam to the row
- `xTrack/Route/FEAT_DSC_Route.md` — R96 added and the two stale `## Implemented` lines repaired by this bake

## Next Step
Build the two layers: Phase 4's fine band beside the coarse interior on one lattice, then Phase 5's seam priced
from the two cell centres — the first walk is now the only place `evolutive`'s 20 m can live.
