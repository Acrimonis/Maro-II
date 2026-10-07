# Context Hydration — Route — 2026-10-07

**Last Bake:** 2026-10-07 07:40 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-07 07:14 UTC) the work ran on the user's own words — the
retirement plan executed on `#impl` (Code → Ask → Architect), then *"update all the docs"* with `#commit`.
No dependency was added, no machine-shaped data file was opened, no work was started without an order, the
device was not touched, and every claim about the code follows a file read.

## State

**The fine pass's dead scaffolding is retired.** The unreachable re-search walk — `fineReSearch`,
`fineWalk` and `referenceWalk` — and the engine step that called it (`reSearched`, `reSearchMs` and
`refineMs`, the fine stage now one `fineMs`) left
[`RouteAvoidEngine`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:318);
[`RouteFinePass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:1) keeps
`finePass()`, `solveCrossing()` and `pricedLineCost()` alone, and the instrument's reference half
(`DEVICE DEV`, `devRef*`, `refMs`, `deviationTo`, `deviationText()`, `corridorHalfWidthText()` and the
`chainFine` parameter) is gone, so `instrumentCoarseWalk()` keeps the `DEVICE PASS` line by itself.

**The plan seam is one decision, and the price step collapsed into the cell.** `RouteGridPlan` keeps
`firstWalkGrid()` and `fineCellM()` — `secondPassRegions()`, both plans' `emptyList()` overrides and
`lineBBox()` left with the walk, closing the default-region trap. `GridWalk` lost `priceStepM` and
`RoutePassRunner` lost `priceStepFor()`: `runPass` now prices at the walk's own `cellM` — the two-layer
walk's interior 100 m — so Phase 4b's grouping win stands and the collapse is structurally impossible.

**The guard is kept and refined, and the orphaned tests are gone.** `RouteFineReachabilityTest` asserts
the refinement's own `FINE settled` for each shipped plan with the decorator dropped; the corridor-chain
region test, `AvoidPriceWalkTest`'s four interior-cell pins with their `twoLayerWalk()` / `fineOnlyWalk()`
fixtures, and `CountingPlan`'s region half (its test renamed `aLookupTakesItsCellAndItsFineCellFromThePlan`)
all left.

**The record, the two deviations, and the one reading owed.** The surviving KDoc was rewritten to drop the
re-search and the second pass (`RouteGridPlan`, both plans, `GridWalk`, `runPass`, `RouteFinePass`, and — in
a second pass on the user's *"update all the docs"* — `RoutePassRules`, `GridContext.fineCellM`,
`RouteEvolutiveEngine` and `corridorChain`); `LineDeviation.kt` / `deviationTo()` and the
`route.evolutive.fine.corridorHalfWidthM` key are now unread, and `RoutePassRules.fineSpliceBetter` and its
test stay though no shipped path reads them, both named rather than hidden. **The device acceptance is the
one reading owed, and it is the user's own** — the retirement returns the line unchanged on every shipped
plan (both plans answered no region), so the 2026-10-06 pass on the same pairs is the acceptance half.

**Gates green, the three known reds and no fourth** — the suite stands at **933 / 3 / 11** (the parked
`route.avoid.fine.cellRatio` test and the two `TrackOutlineTest` dash drifts) and `apk-build.bat` is green.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt`, `RoutePassModels.kt`, `RoutePassRunner.kt`, `RouteGridPlan.kt` — the retirement's code surface, landed
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — the fine-stage step and the instrument's reference half, removed
- `app/src/main/java/ykws/android/maro/spatial/RouteEvolutiveEngine.kt`, `spatial/multipass/RoutePassRules.kt`, `RouteCorridorChain.kt` — KDoc only, reconciled to the retired walk
- `app/src/test/java/ykws/android/maro/spatial/multipass/RouteFineReachabilityTest.kt` — the guard, kept and refined
- `xTrack/Route/261006_FEAT_PLN_Route_fine-pass-retirement.md` — **landed 2026-10-07**, its `## Outcome` written
- `xTrack/Route/261006_FEAT_PLN_Route_fine-walk-price-step.md` — **parked**, a `#archive` retirement candidate (its subject dropped)
- `xTrack/Route/FEAT_DSC_Route.md`, `xTrack/GLOBAL_CONTEXT.md` — this bake's record

## Next Step

**The line is landed and nothing else waits.** The parked price-step plan and the landed fine-pass-instrument
plan are both `#archive` retirement candidates, and `#archive` is the only command that moves one. One item
the bake leaves open is stated rather than hidden: the device acceptance of the retirement is the user's
own, unmeasured here.
