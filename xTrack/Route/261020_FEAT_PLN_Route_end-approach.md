<!-- scope: feature -->
# Plan — a route's ends: no escape hop, no self-waiving approach

**Status:** v2 (2026-10-20) · implemented 2026-10-20, device pass owed · F5 decided · **Feature:** Route · **Created:** 2026-10-20

## The report

> The Closest route is invalid.

The device run of 2026-10-20 20:01 (process 3574), Pointe du Bacon side, at 10 kn on `evolutive`.

## The user's decisions (2026-10-20)

- **F5 — keep the allowance for a boxed-in end.** Where the strict rule leaves an end no exit, the whole-step allowance stays, so the end still routes rather than failing as no path.

## What the trace shows

- **Closest (λ 1) and Balanced (λ 3) cross 0.6 m water**, 48 m from the start: `LINE minDepth=0.6m minDepthAt=43.56878,7.14038 minDepthWaiver=carve gateEdge=5.0m`. Balanced folds into Closest (`FOLD dispersion=26.3m`). Detour (λ 5) is clean.
- **The start was already clear:** `REPAIR end=start … rule=clear movedM=13.8 edge=25.0m coast=48.2m`.
- **Its carve still ran:** `CARVE end=start dir=NE cells=0 length=141.4m`, on the 100 m interior grid (`GRID … start=(r=37,c=44 …)`).
- **The line's shape:**
  1. The repaired start, 43.56834,7.14055.
  2. 50 m **west**, toward the coast, to its square's centre, 43.56846,7.13994 (`CORNER idx=1 why=BOW_DEPTH turn=119.6deg`).
  3. One 100 m diagonal NE to 43.56936,7.14118, crossing the 0.6 m reading.
- **Item 5's evidence:** the corners stand exactly 100 m apart on both axes, so they are coarse cell centres.

## The flaws

The end handling makes fixed choices, samples only cell centres, and exempts its own approach from the gate.

1. **The carve's direction is the first in compass order** (`BerthCarve.kt` `carveBerth`: N, NE, E … NW), never the cheapest or nearest. The search already chooses by cost; the carve overrides it.
2. **The carve samples cell centres only.** `legalWater` and `marginLand` read the centres; the water between them is never read. With `stepsToWater=1` no intermediate is read at all.
3. **The carve's approach waives itself.** Every point on `EndApproaches` is waived by `GateRule.waives` (`ShallowEdgeIndex.kt`). `keepsWall` drops waived pieces, so the step guard's own end leg (`StepGuard.endLegClear`) skips it too. No reader checks the approach. This is why the 0.6 m reading reached the line as `carve`, not as a breach.
4. **The carve is tested at the cell's centre, not at the end** (`carveEnd` passes `grid.center(cell)` as the anchor). A repaired, clear end still gets a carve when its cell's centre is not legal.
5. **The start anchored on the 100 m cell.** `WalkWindows.endCellAt` prefers the finest passable cell holding the end, and the start lies inside a fine window (43.56639..43.57538 N). Why it fell back to coarse is not yet known.
6. **The end's whole cell is freed** (`MultipassGrid.forceFree`), and the walk leaves through its centre. On a coarse cell that is up to 70 m from the end, here 50 m toward the coast.
7. **A boxed-in end waives whole steps.** Under `strictStart=false` / `strictAim=false`, a step with a centre in the waiver is waived whole (`StepGuard.clear`), unsampled. Not triggered in this run.
8. **Two answers to one problem.** The end repair (W, `route.repair.clearanceM`) and the carve both answer *an end too near the coast or the shallows*. They do not read each other, and the older one undid the newer.
9. **The repair finds *a* clear point, not the nearest.** Already recorded in [`261020_FEAT_PLN_Route_channel-visibility.md`](261020_FEAT_PLN_Route_channel-visibility.md) *Still open*; not repeated here.

## The fix

- **F1 — retire the carve.**
  - Remove `carveBerth`'s use by the grid (`RouteGridBuilder.carveEnd`/`carveEndOf`), the opened carve cells, `EndApproaches` and the `carve` waiver.
  - The gate waives itself only in the 20 m disc around each end (`GateRule.waiverM`), as the gate rule already states.
  - This removes flaws 1–4.
  - **The aiming ring also reads `carveBerth`** (`metricCarveLattice`, `BerthCarve.reachable`) to decide a red target. It must read the same rule the route uses, see F4. That keeps a green target and a refused end from disagreeing.
- **F2 — an end anchors on its own finest cell.** First find why `endCellAt` fell back to coarse here (flaw 5), then fix that cause. With a 20 m cell the centre stands at most 14 m from the end, inside the disc (flaw 6).
- **F3 — the way out is the search's choice.** `openEndDisc` stays the one opening around an end; the search picks the way out by cost.
  - Every leg past the disc is measured along its length, including the end's leg to its cell's centre (`endLegClear`), which no approach exempts any more.
  - **Water under 3 m is never waived past the disc.** The wall's 10 m is the only thing the disc relaxes.
- **F4 — the repair is the one end mechanism.**
  - An end the repair clears needs nothing more.
  - An end it cannot clear (a basin narrower than 50 m) keeps only the disc.
  - The ring reads the repair's verdict and the disc, not a carve.
- **F5 — a boxed-in end keeps its allowance, as its only exit** (flaw 7, the user's decision).
  - The strict rule is always tried first.
  - The whole-step allowance (`strictStart=false` / `strictAim=false`) applies only to an end the strict rule leaves with no exit — today's re-walk on `StepGuard.heldRefused`.
  - It is never granted to an end that has a strict exit, so a cleared end never reaches it.

## Tests

- **The device's own case, as a fixture:**
  - A repaired start in clear water.
  - Its coarse cell's centre nearer the coast and not legal.
  - A shoal under 3 m between that centre and its NE neighbour.
  - Every rung keeps `minDepth ≥ 3.0` past the disc, with no corner behind the start.
- **A clear end draws no approach:** the start's first leg leaves from the end, not from a cell centre beyond the disc.
- **An end inside a fine window anchors on its fine cell** (flaw 5's cause, pinned once found).
- **The ring and the route agree:** a target the ring calls green routes, and one it calls red does not.
- **A basin narrower than 50 m:** a boxed-in end still routes, under the allowance.
- **An end with a strict exit never takes the allowance.**
- **Rewritten on purpose:**
  - every test pinning the carve (`BerthCarve`, `CARVE` trace, the `carve` waiver kind in `GateEndStepTest`);
  - `GateEnforcementEngineTest`'s carve-at-engine-level run.

## Trace

- The `CARVE` line goes.
- `LINE minDepthWaiver` reads `disc` or `none`.
- `REPAIR` stays the one line about an end's placement.

## Strongest objection

- A berth in a basin narrower than 50 m, where only the 100 m grid covers it, can lose its route entirely.
- Today the carve is what gets it out, wrongly or not. F2 shrinks the case, and F5 keeps the allowance for it.
- **The cost of F5:** a boxed-in end's first steps can still cross water under 3 m, unchecked, as today. It stays limited to ends with no other exit.

## Gaps

- Why the start fell back to the coarse cell (flaw 5).
- Why the carve's north direction failed: the trace prints only the winning direction.

## Implemented

**2026-10-20, on `feature/route-low-depth-check`, uncommitted.** One review ran after the build; its small findings are fixed and the rest are listed below. The suite passes (1404 tests, 0 failures, 12 skipped as before) and `apk-build.bat` packages; the device run is the user's.

- **F1 — the carve is gone.**
  - Removed: the scan, its value and its trace line, the approach polyline (`EndApproaches`) and every parameter that carried it.
  - `GateRule.waives` reads the 20 m disc alone, and `waiverOf` answers `disc` or `none`.
  - `BerthCarve.kt` keeps only the end disc (`openEndDisc`, `endDiscReachCells`) and `depthClearsGate`. `MultipassGrid.openCarve` is now `openEndCell`.
- **F2 — no code change; the cause was the carve's own lookup.**
  - `WalkWindows.endCellAt` already anchors the search on the end's finest passable cell.
  - The carve read the coarse cell's central fine copy (`walk.slotOf`), found it not passable, and ran on the 100 m grid.
  - The `GRID layer=coarse … start=` line always prints the interior cell, so it was never evidence of a coarse anchor.
- **F3:** with no exemption past the disc, the end leg (`StepGuard.endLegClear`) and every reader measure the whole leg.
- **F4 — deviation: nothing to change.** No aiming-ring code called the carve; only tests did. The plan's reading came from a KDoc.
- **F5:** the boxed-in allowance is unchanged. `GateEndStepTest.aBoxedStartReleasesOnlyItself` and `aStartWhoseShortestExitGrazesTheWallLeavesByAnother` pin both halves.
- **Tests:**
  - Five carve-pinning tests removed.
  - `GateEndStepTest.theWaiverNamesItsKind` pins `disc`/`none`.
  - New `GateEnforcementEngineTest.aClearStartLeavesWithoutCrossingAShoal`.
- **Docs:** the engines doc's ends step and tile note, and the feature file's three gate/tile lines.

**Still open:**
- **An end hemmed in by the 50 m coast margin beyond its disc now fails as no path.**
  - The carve used to open, and the pull to exempt, a line out of a narrow inlet or harbour arm.
  - The F5 allowance relaxes only the depth gate, not the coast margin.
  - This is the "closing an only exit" case the user's F5 decision rules out, so it needs a coast-side fallback. That is a design not yet made, and no test pins it.
- **The new engine test is an outcome guard, not a proven regression test.** Its fixture has no coastline, and the plan's coarse-centre-not-legal setup was not built. Whether it would have failed with the carve present is undetermined.
- **Not pinned:** a clear end's first leg leaving from the end itself (covered only indirectly), `endCellAt` choosing the fine cell, and the ring agreeing with the route.
- **Unused locals predating this work:** `RouteGridBuilder.buildLayeredGrid`'s `berth` and `index`, and `fineTilesOver`'s `fineCellM` and `berth`.
- **Naming:** `BerthCarve.kt` and `BerthCarveTest.kt` now hold only the end disc; renaming a file waits on the user's word.
