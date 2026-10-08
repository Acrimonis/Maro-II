<!-- scope: feature -->
# Route — the avoid / evolutive key taxonomy, audited

**Date:** 2026-10-07 · **Status:** landed 2026-10-07 — the four decisions implemented across the asset, `AppConfig`, the plan seam and the tests; unit suite green · **Order:** the user's word of 2026-10-07 — *"review the avoid settings and make sure that all of them are valid. Is the taxonomy logical? I seem to have a lot of void and very little of evolutive. Which ones are common? Aren't there some redundant ones? ex route.avoid.fine.cellRatio=0.3333 and route.avoid.grid.fineRatio=4."* — and the same day's steer: **fold** the review, **1 delete** the dead key, **2 normalize** the fine layer to one idiom, and **update the docs to match the current shipped values**.

## What the keys are, and who reads them

Every `route.*` key this audit covers, with its reader as measured on 2026-10-07:

| Key | Accessor | Code default | Asset | Reader |
|---|---|---|---|---|
| `route.avoid.grid.cellM` | `routeAvoidGridCellM` | 100 | 100 | `RouteGridBuilder` → `plan.firstWalkGrid(box, …)` |
| `route.avoid.grid.fineRatio` | — | — | — | **deleted — the dead key is gone** |
| `route.avoid.grid.fineCellM` | `routeAvoidGridFineCellM` | 33.3333 | 33.3333 | `UniformGridPlan.fineCellM(base) = the metres key` |
| `route.evolutive.grid.cellM` | `routeEvolutiveGridCellM` | 100 | 100 | `EvolutiveGridPlan.firstWalkGrid` |
| `route.evolutive.grid.fineCellM` | `routeEvolutiveGridFineCellM` | 20 | 20 | `EvolutiveGridPlan.firstWalkGrid` / `fineCellM` |
| `route.speed.accelMps2` | `routeSpeedAccelMps2` | 1.0 | 1.0 | `RouteEta.timeLineWithLimits` — the profile's own ramp |
| `route.turn.lateralAccelMps2` | `routeTurnLateralAccelMps2` | 0.33 | 0.33 | `RouteCornerPass.round` |
| `route.turn.transitionSec` · `minSpeedKn` · `reachFraction` | three accessors | 2.0 · 5.0 · 1.0 | 2.0 · 5 · 1.0 | `RouteCornerPass`, the profile clock |
| `route.avoid.obstacle.marginM` | `routeAvoidObstacleMarginM` | 50 | 50 | the grid builder, the corner pass, the berth carve |
| `route.avoid.corridor.reachM` · `repair.maxRadiusM` · `walk.maxCells` | three accessors | — | 3704 · 200 · 600000 | the corridor, the repair, both walks |
| `route.avoid.depthGate.*` · `zone300.*` · `speedZone.*` · `ladder.collapse.toleranceM` | per key | — | — | the cost field, the clock, the flow |

## What the audit found

- **One key is dead.** `route.avoid.grid.fineRatio` has no accessor and no parse anywhere in the tree, so its `4` has never reached a walk — while its sibling `route.avoid.fine.cellRatio` is live and says `1/3`. The asset already documents both correctly — `fineRatio` as *"no reader… kept rather than deleted"* ([`maro.properties`](../../app/src/main/assets/maro.properties:300)) and `cellRatio` as *"has its consumer"* ([`maro.properties`](../../app/src/main/assets/maro.properties:306)) — so the plan's earlier claim that the comment was wrong is **struck**.
- **Four drifts between the asset and the code default.** `grid.cellM` (100 shipped, 50 in code), `fine.cellRatio` (0.3333 shipped, 0.40 in code), `lateralAccelMps2` (0.33 shipped, 1.0 in code) and `speed.accelMps2` (1.0 shipped, 0.5 in code). The fine-cell one is precisely what the parked red pins: [`theFineCellRatioShipsAtFortyPercentOfTheCoarseCell`](../../app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt:638).
- **Two idioms for one thing.** `avoid` states its fine layer as a ratio of its coarse cell; `evolutive` states it as an absolute cell in metres. The split is a recorded design choice ([`261004_FEAT_PLN_Route_hybrid-grid.md`](261004_FEAT_PLN_Route_hybrid-grid.md:351)), and normalizing it reverses that choice.
- **Very little is `evolutive`'s own.** Three keys carry its name; every other key is shared, because `evolutive` is a composition over the same `RouteAvoidEngine` and differs by its plan alone. [`route.engine.id`](../../app/src/main/assets/maro.properties:171) ships `avoid`, so the adaptive engine is live but not the default.
- **The two acceleration comments contradict their own values.** The `lateralAccelMps2` block's feel line says *"2.2 … (shipped)"* while the key ships 0.33, with a commented-out `=1.0` below ([`maro.properties`](../../app/src/main/assets/maro.properties:145)); the `speed.accelMps2` block says *default 0.5* and *"0.7 shipped"* while it ships 1.0 ([`maro.properties`](../../app/src/main/assets/maro.properties:131)).

## The decisions (the user's word)

1. **Delete the dead** — `route.avoid.grid.fineRatio`, reversing the recorded keep ([`maro.properties`](../../app/src/main/assets/maro.properties:300), [`261004_FEAT_PLN_Route_hybrid-grid.md`](261004_FEAT_PLN_Route_hybrid-grid.md:351)), on the user's word.
2. **Normalize the fine layer to one idiom — metres** — the repo's own documented preference ([`RouteGridPlan.fineCellM`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:44)'s KDoc, the asset's evolutive comment). `route.avoid.fine.cellRatio` becomes `route.avoid.grid.fineCellM = 33.3333` (0.3333 × 100 m, the shipped line preserved byte-for-byte), [`UniformGridPlan.fineCellM`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:59) rewritten to read it, `evolutive` untouched.
3. **Align the code defaults onto the shipped asset** — the properties file is the source of truth and the asset is never silent on these keys, so this moves nothing a user sees: `grid.cellM` 50 → 100, the fine cell 0.40 → 33.3333 (through the rename above), `lateralAccelMps2` 1.0 → 0.33, `speed.accelMps2` 0.5 → 1.0, `obstacle.marginM` 25 → 50.
4. **Name what is shared in the doc** — [`FEAT_DOC_Route_engines.md`](FEAT_DOC_Route_engines.md) states that `route.avoid.*` names the pipeline, and lists `evolutive`'s own three keys against the shared rest.

## Feasibility and risks

- **Low risk.** Deleting a dead key and aligning code defaults move no shipped behaviour; the one behaviour-touching edit is the ratio→metres rename, designed to keep the 33.33 m fine cell identical.
- **The parked red ends green.** The test is rewritten to assert the new metres key and the shipped values (and renamed), because file and code then agree.
- **One open point.** Normalizing to metres preserves 33.3333 m; a cleaner 20 m (`evolutive`'s size) or 25 m would change `avoid`'s drawn line and is the user's call alone.
- **Two reversals stated.** Deleting the dead key and normalizing the idiom both reverse decisions recorded in the hybrid-grid plan; they are stated here, not swept.

## Phases

1. **Fold** — the review corrections landed in this write (the struck comment bullet, the fourth drift, the two comment contradictions, the recorded reversals).
2. **The sweeps** — delete the dead key and its comment; rename the ratio key to `route.avoid.grid.fineCellM = 33.3333`; align the five code defaults; correct the two acceleration comment blocks; rewrite `UniformGridPlan`; update [`FEAT_DOC_Route_engines.md`](FEAT_DOC_Route_engines.md)'s shared-vs-own list and [`FEAT_DSC_Route.md`](FEAT_DSC_Route.md).
3. **The tests** — rewrite the parked red to the metres key and the shipped values; retarget any fixture reading the removed keys.
4. **The build** — suite green with the parked red resolved, `assembleDebug` green.

## Open questions

- Should `avoid`'s fine cell stay 33.3333 m (shipped, byte-identical) or move to a clean 20 m / 25 m — a user-visible line change?
- Should `route.avoid.*` be renamed to a family name that says *shared*, now that the prefix names the pipeline rather than the `avoid` engine only?

## Outcome (landed 2026-10-07)

- **The dead key is deleted** — `route.avoid.grid.fineRatio` and its comment left [`maro.properties`](../../app/src/main/assets/maro.properties).
- **The fine layer is one idiom, metres** — `route.avoid.fine.cellRatio` became `route.avoid.grid.fineCellM=33.3333`, [`UniformGridPlan.fineCellM`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:59) reads it, and `routeAvoidFineCellRatio` + its ratio bounds became `routeAvoidGridFineCellM` + metres bounds in [`AppConfig`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:200).
- **The four code drifts are aligned onto the asset** — `grid.cellM` 50→100, the fine cell 0.40→33.3333 (through the rename), `lateralAccelMps2` 1.0→0.33, `speed.accelMps2` 0.5→1.0, `obstacle.marginM` 25→50; the shipped line is preserved byte-for-byte.
- **The docs match** — the two acceleration comment blocks in [`maro.properties`](../../app/src/main/assets/maro.properties:131) now state their shipped values, the [`RouteAvoidEngine`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:113) KDoc names the new key, and [`FEAT_DOC_Route_engines.md`](FEAT_DOC_Route_engines.md), [`FEAT_DOC_Route_avoid-algorithm.md`](FEAT_DOC_Route_avoid-algorithm.md) and [`FEAT_DSC_Route.md`](FEAT_DSC_Route.md) are updated.
- **The suite is green** — the parked `route.avoid.fine.cellRatio` red is resolved by the metres normalization; `gradlew testDebugUnitTest` is BUILD SUCCESSFUL.
