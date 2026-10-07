<!-- scope: feature -->
# Route — the fine pass's dead scaffolding, retired

**Date:** 2026-10-06 · **Status:** landed 2026-10-07 · **Order:** the user's word of 2026-10-06 —
*"again we are dropping this fine pass step. You will retire everything that is linked to it and have
no impact on anything else. Plan it."* — plus, of 2026-10-07, *"the guard must stay"* and *"no regression
(perf or functional) allowed"*.

**Origin:** the instrument's own reading of 2026-10-06
([`261006_FEAT_PLN_Route_fine-pass-instrument.md`](261006_FEAT_PLN_Route_fine-pass-instrument.md:1)), whose
split showed the fine stage is [`finePass()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:47)
**alone** — the re-search walk is retired on every shipped plan — so the parked price-step plan
([`261006_FEAT_PLN_Route_fine-walk-price-step.md`](261006_FEAT_PLN_Route_fine-walk-price-step.md:1)) is
dropped rather than taken, and its scaffolding plus the walk it was written for are dead weight.

## Why — the step is dropped, so its scaffolding is dead

- **The step is dropped.** No change is made to the fine-only walk's price step; the parked plan's
  subject is abandoned, and its landed Phase 1 survives only as scaffolding for a walk that never runs.
- **The walk it targeted is already unreachable.** Both shipped plans answer no second-pass region, so
  [`fineReSearch()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:249)
  returns before it builds anything; the instrument's `reSearchMs` reads 0.0–0.1 on every rung and the
  trace says `FINE research box=empty`.
- **Its scaffolding is read by nothing that ships.** [`GridWalk.priceStepM`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassModels.kt:32)
  is only ever *set* away from its default by the retired [`fineWalk()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:308);
  every shipped walk already answers `cellM`, so the field holds no second value.
- **The default region is a latent trap.** [`RouteGridPlan.secondPassRegions()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:47)
  ships a **default** that returns a real region: a future plan overriding only its first walk would
  silently re-activate the retired walk. Removing the method closes that trap with the walk.

## The scope rule, and its two boundaries

**Retire exactly what leaves every shipped answer byte-identical** — no `LINE`, distance, duration,
`forced` set, crossing splice or reading moves, and **no perf change** — and keep everything a shipped
path or a named future user still reads.

- **Boundary 1 — the guard stays.** [`RouteFineReachabilityTest`](../../app/src/test/java/ykws/android/maro/spatial/multipass/RouteFineReachabilityTest.kt:1)
  is **kept and refined**, never retired: the walk that runs is [`finePass()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:47), so
  the test keeps its assertion that each shipped plan reaches the fine walk's refinement and drops only
  the re-search half, whose subject leaves.
- **Boundary 2 — impact-bearing code stays.** [`finePass()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:47) and its crossing re-solve
  are out of scope: the re-tension runs on every route and the splice can replace a zone's stretch, so
  removing them could move a drawn line.
- **The one deliberate exception to the rule:** [`corridorChain()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCorridorChain.kt:21) is
  test-only today and impact-free, so the rule would retire it — but its KDoc names the **two-layer
  first walk** as its future user, which is not the fine pass, so it is **kept** and this exception is
  recorded rather than hidden.

## What is retired

- **The retired fine re-search walk:** [`fineReSearch()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:249),
  [`fineWalk()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:308) and
  [`referenceWalk()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:399) — `RouteFinePass` keeping only
  [`finePass()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:47), `solveCrossing()` and `pricedLineCost()`.
- **The plan seam's region decision:** [`RouteGridPlan.secondPassRegions()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:47)
  and both plans' `emptyList()` overrides, whose only main reader is the retired walk, plus
  [`lineBBox()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:155), which loses its last main reader.
- **The walk's own price step:** [`GridWalk.priceStepM`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassModels.kt:32)
  and [`priceStepFor()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt:171) — with the fine walk gone, every walk prices
  at its own `cellM`, so the field and the accessor collapse into the cell they already equal.
- **The engine's dead limbs:** the `reSearched` step, the `fineReSearch` call and `reSearchMs` at
  [`solveAtLambda()`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:318) — with one part left, `refineMs` goes too and the fine
  stage is the single `fineMs` — and `instrumentCoarseWalk()`'s **reference half**: `referenceWalk`, the
  `DEVICE DEV` line, `refMs` and the `devRef*` figures.
- **The orphans that must go with them, named so the build stays clean:** the `deviationTo` import
  ([`RouteAvoidEngine.kt:41`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:41)), and the private helpers `deviationText()`
  and `corridorHalfWidthText()`, whose only readers are the `DEVICE DEV` line; `instrumentCoarseWalk`'s
  **`chainFine` parameter** becomes unused and leaves its signature; and `planName()` stays, still read by
  the `DEVICE PASS` line.
- **The tests that lost their subject:** [`RouteCorridorChainTest.bothPlansAnswerNoRegionNow`](../../app/src/test/java/ykws/android/maro/spatial/multipass/RouteCorridorChainTest.kt:110);
  `RouteAvoidEngineTest`'s `CountingPlan.secondPassRegions` and the region half of
  `aLookupTakesItsCellAndItsSecondPassRegionFromThePlan` (**renamed** — its subject leaves the name);
  `AvoidPriceWalkTest`'s `fineOnlyWalk()` fixture and the `priceStepFor` interior-cell pins beside it; and
  [`RouteFineReachabilityTest`](../../app/src/test/java/ykws/android/maro/spatial/multipass/RouteFineReachabilityTest.kt:1)'s re-search test and its
  `RegionPlan` decorator — the test itself stays, see Boundary 1.

## What stays, and why

- **[`finePass()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:47) and its crossing re-solve** — impact-bearing, per Boundary 2.
- **The guard, refined** — [`RouteFineReachabilityTest`](../../app/src/test/java/ykws/android/maro/spatial/multipass/RouteFineReachabilityTest.kt:1) keeps the refinement assertion
  for each shipped plan and drops the decorator; with the region method gone, the shipped plans are used
  directly, which is what *fails when a shipped plan reaches no fine walk*.
- **The instrument's pull tallies** — `FINE PULL`, `FINE settled` and `FINE LOCAL PULL` report
  [`finePass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:47)'s own walks, which stay.
- **[`corridorChain()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCorridorChain.kt:21)** — the one recorded exception above.
- **Everything the two-layer first walk reads** — `GridContext.fineCellM`, `clockSampleM`, `tailCellM`,
  `fineWaterReachM`, `WalkWindows`, the band's windows and [`RouteFineWindowShapeTest`](../../app/src/test/java/ykws/android/maro/spatial/multipass/RouteFineWindowShapeTest.kt:23).
- **`route.avoid.fine.cellRatio`** — the shipped plan's fine cell, read by the clock and the fine pull's
  own field, untouched by this retirement.
- **The `DEVICE PASS` line** — kept, minus the split it no longer needs; it still carries the coarse
  walk's cells, expansions and `fineMs`.

## Phases

**One change, five steps for review** — the removal is atomic, because each step removes a symbol the test
source set still names (`fineReSearch`, then `secondPassRegions`, then `GridWalk.priceStepM`), so no step
lands alone. The steps are ordered leaf-first: each subject becomes removable only because the step before
it removed that subject's one consumer.

1. **The second fine pass, whole.** Remove
   [`fineReSearch()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:249),
   [`fineWalk()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:308) and
   [`referenceWalk()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:399) — the walk nobody runs leaves end to end — with
   its engine call at [`solveAtLambda()`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:361) (`reSearched`, `reSearchMs`, `refineMs`, the
   fine stage now `finePass` alone) and the reference half of
   [`instrumentCoarseWalk()`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:618) (`DEVICE DEV`, `refMs`, `devRef*`), shedding the now-orphaned
   `chainFine` parameter, `deviationText()`, `corridorHalfWidthText()` and the `deviationTo` import.
2. **The plan seam.** Drop
   [`secondPassRegions()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:47) with both plans' `emptyList()` overrides and
   [`lineBBox()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:155), whose only main reader left in step 1.
3. **The walk's own step.** Drop
   [`GridWalk.priceStepM`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassModels.kt:32) and
   [`priceStepFor()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt:171), whose only non-default writer left in step 1; `runPass`'s
   price step is the walk's own **`cellM`** — the 100 m interior for the two-layer walk — so **Phase 4b's
   grouping win stands and the collapse is now structurally impossible**.
4. **The guard and the tests.** Refine
   [`RouteFineReachabilityTest`](../../app/src/test/java/ykws/android/maro/spatial/multipass/RouteFineReachabilityTest.kt:1) to the refinement assertion for each shipped
   plan, dropping the re-search test and the `RegionPlan` decorator; retire the orphaned tests —
   [`RouteCorridorChainTest.bothPlansAnswerNoRegionNow`](../../app/src/test/java/ykws/android/maro/spatial/multipass/RouteCorridorChainTest.kt:110),
   `RouteAvoidEngineTest`'s `CountingPlan.secondPassRegions` with the renamed region assertion, and
   `AvoidPriceWalkTest`'s `fineOnlyWalk()` fixture and its `priceStepFor` pins.
5. **The record.** Rewrite the surviving KDoc to match —
   [`RouteGridPlan`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:17)'s two decisions become one, both plans lose their second-pass
   text, `GridWalk`'s price-step KDoc leaves, `runPass`'s Phase 4b comment moves with it, and
   [`RouteFinePass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:9)'s class KDoc stops naming the re-search — then update the feature
   file's state lines (the plan seam's "second-pass region" clause and the `route.avoid.fine.cellRatio`
   limit line), append `## Outcome`, and archive the parked price-step plan.

## Verification

- **Green:** `apk-build.bat` and the suite carrying the same three known reds and no fourth, its count
  lower by exactly the retired tests and **higher by none** — the refined guard stays.
- **No functional regression:** the retired re-search returned the line unchanged on every shipped plan
  (both `secondPassRegions` answer empty), so the engine's `reSearched` → `refined` is identity; no drawn
  figure moves and the 2026-10-06 device pass on the same pairs is the acceptance half, **the user's own**.
- **No perf regression:** `runPass`'s price step is the walk's own `cellM`, so the two-layer walk still
  groups at the interior 100 m and Phase 4b's win stands; the fine walk's grouping is untouched because
  the fine walk that ran ([`finePass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:47)) keeps its own steps exactly as they are.
- **The guard has teeth:** the refined test is green for both shipped plans, and **red** when the fine cell
  is forced at or above the coarse one — the state in which a shipped plan reaches no fine walk.
- **The KDoc tells the truth:** no surviving comment describes a decision, a walk or a field this plan
  removed.

## Risks

- **The plan seam narrows.** Removing `secondPassRegions` leaves [`RouteGridPlan`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:30) with
  its first-walk decision alone; a future adaptive algorithm wanting a second pass would reintroduce the
  method. Stated, not hidden — the user's word drops the step this seam served.
- **Evidence leaves with the walk.** `DEVICE DEV` measured the coarse line's error against a fine
  reference — the figure the corridor's half-width rests on. Retiring it removes that reading; the one
  capture it produced is recorded in the mark-count plan, and a future half-width question would rebuild
  the instrument rather than read a stale one.
- **The Phase 4b lesson must survive its field.** The collapse the price step fixed is recorded in
  `priceStepFor()`'s KDoc; when the field goes, that lesson moves onto `runPass`'s own step choice.
- **A renamed test can hide a lost assertion.** Renaming `aLookupTakesItsCellAndItsSecondPassRegionFromThePlan`
  must drop only the region clause, keeping the cell and fine-cell assertions intact.

## Open questions

- **None blocking.** `corridorChain()`'s future is settled above (kept for the two-layer walk); if the user
  prefers it retired with the re-search, the exception line moves with that word.

## Parked

- The fine-only walk's price step itself, now **dropped** rather than parked; its plan is a retirement
  candidate for `#archive`, not a resume.
- The owed `runPass`-driving seam test, which stays owed on its own line and is untouched here.

## Outcome

**Landed 2026-10-07 as one change, its five steps together, on the user's `#impl`.**

- **The second fine pass left whole.** `fineReSearch()`, `fineWalk()` and `referenceWalk()` are gone from
  [`RouteFinePass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:1), whose
  seat now holds `finePass()`, `solveCrossing()` and `pricedLineCost()` alone and takes no constructor
  argument; the engine's `reSearched` · `reSearchMs` · `refineMs` step left
  [`solveAtLambda()`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:318), the fine
  stage now the single `fineMs`, and the instrument's reference half (`DEVICE DEV`, `refMs`, `devRef*`,
  `deviationText()`, `corridorHalfWidthText()`, the `deviationTo` import and the `chainFine` parameter) is
  gone — [`instrumentCoarseWalk()`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:609)
  keeps the `DEVICE PASS` line alone.
- **The plan seam is one decision.** `RouteGridPlan.secondPassRegions()` and both plans' `emptyList()`
  overrides left with `lineBBox()`, so the interface keeps `firstWalkGrid()` and `fineCellM()`.
- **The walk's own step collapsed.** `GridWalk.priceStepM` and `RoutePassRunner.priceStepFor()` are gone;
  [`runPass()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt:70) prices at
  the walk's own `cellM`, Phase 4b's lesson recorded on that step's own comment.
- **The guard is refined, not retired.** `RouteFineReachabilityTest` asserts the refinement for each shipped
  plan, the `RegionPlan` decorator and the re-search test gone; `RouteCorridorChainTest.bothPlansAnswerNoRegionNow`,
  `AvoidPriceWalkTest`'s four interior-cell pins with their `twoLayerWalk()` / `fineOnlyWalk()` fixtures, and
  `CountingPlan`'s region half with its renamed lookup test all left.
- **Gates.** `apk-build.bat` green; the suite reads **933 / 3 / 11** — the same three known reds and no
  fourth, lower by exactly the six retired/refined tests.
- **Named, not hidden.** The parked price-step plan was **not** moved: only `#archive` may enter
  `xxArchive/`, so it stays a retirement candidate for that command; `LineDeviation.kt` / `deviationTo()`
  and `route.evolutive.fine.corridorHalfWidthM` are now unread and await their own word.
- **Not verified here:** the on-device acceptance, which is the user's own.
