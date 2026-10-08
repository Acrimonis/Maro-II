# Context Hydration — Route — 2026-10-08

**Last Bake:** 2026-10-08 21:46 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-08 20:59 UTC) the session ran on the user's own words: `#new route-evol-pull`, then `#impl`-style orders — the fine pass's reporting fix, its price-step fix, the status-word change, and the pin bug. No dependency was added, no machine-shaped data file was opened, no work was started without an order, the device was not touched, and every claim about the code follows a file read. Git: `feature/route-evol-pull` cut from `origin/develop` with `--no-track` (the tree's changes carried across, the base being content-identical at `bd8ba10f`); the session's changes were uncommitted at bake time with `#commit` and `#push` invoked.

## State

**The branch.** Everything below sits on `feature/route-evol-pull`, cut from `origin/develop`, which already contained the whole `feature/route-algo-selective-eval` line (`git rev-list --count origin/develop..HEAD` read `0` before the cut). Full `testDebugUnitTest` and `apk-build.bat` are green after every step; one `apk-build.bat` run failed once on a transient Gradle build-cache store (`Could not get file mode for …dexBuilderDebug…`) and the retry passed.

**The fine pass is named, and its pulls price coarse.** `RouteStage.FINE` — `Refine` / `Affinage` — joins the closed set, and [`RouteAvoidEngine.solveAtLambda`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt) publishes it before the fine pass instead of re-publishing `PULL`, so the panel's word names the refinement rather than a pull already done; both stage-set doc homes were reconciled and [`RouteAvoidEngineTest`](../../app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt) pins that the main's terminal closes on `FINE`. [`RouteFinePass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt) then took the coarse price step in **both** of its pull setups — the Phase 4b lesson the runner had learned and the fine pass never had — a value-preserving change whose span proof keeps the sum bit-identical.

**The status word tells the truth, and the readings line is gone.** The acquisition's status is now the stage and the **selected route's own number** — `Pull #2` — in the panel header and the drawer band alike, driven by one `routeNumber` on `RouteSummaryData` fed from the panel's own selected page, so the two surfaces cannot disagree. The header's step-readings line went, and with it the whole chain that fed it: the panel's block, parameter and import, `MapScreen`'s collected state and both call-site arguments, and `RouteViewModel._stepReadings`. R90 was rewritten, the band's two-message KDoc follows it, and the `ui-drawer-guidelines` scaffold row dropped `readings`. `RouteUpdate.readings` stays in the seam — the engines still publish it, only the app stopped reading it.

**The route card's pin flips.** The dashboard card is built from the loaded detail and mirrors it through [`MapScreen`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)'s refresh effect, which read `trackSummaries` — the list built as `all.filter { !it.route }`, so a route was never found and its detail never reloaded. The effect now keys on and looks up `allTrackSummaries`, the one list holding both kinds; the write itself was already sound, and the drawer's Routes list card, which reads `summary.pinned` off the flow, was never affected.

**What is owed.** The fine pass's own tail is **unmeasured**: the harness's fixture settles on a straight two-point line, so both fine pulls read `priceReads=0 marks=0` and only the device can price the saving. Also owed: the tile-layer device pass (unchanged), the corrected status words and the card's pin on device, and the findings left unfixed — `RouteStage.CORRIDOR`/`GRID` published by no engine, `RoutePinOption` and `route_pin_label` dead, no test on the Compose refresh effect.

**No open walk.** The feature file holds no `## Walk` section, so nothing bars a fold, and no `## Implemented` log stands for a retirement sweep to read.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/RouteEngine.kt` — the `FINE` stage member and its label
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — the fine pass's own boundary, published as `FINE`
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt` — the fine pass's coarse price step, in both pull setups
- `app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt` — the status word `stage #n`, and the readings line gone
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `routeNumber` fed to the summary; the readings chain removed; the card refresh reading `allTrackSummaries`
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — `_stepReadings` and its assignment removed
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayerParams.kt`, `MenuDrawerOverlay.kt` — `routeNumber` and the band's `stage #n`
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` — `route_status_stage_number` added, `route_status_acquiring_stage` retired, `route_stage_fine` added
- `app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt` — the `FINE` boundary pin
- `xTrack/Route/FEAT_DSC_Route.md` — R90 rewritten, the panel bullet and the removal list refreshed
- `xTrack/Route/FEAT_DOC_Route_engines.md`, `260929_FEAT_DOC_Route_engine-interface.md`, `docs/ui-drawer-guidelines.md` — the stage set and the panel row reconciled

## Next Step

The fine-tail measurement — whether the refinement still dominates the stage line now that its price step is coarse — is the user's device pass (R97), and the assessment that shapes it is settled: the grouping collapse at the fine cell, the per-zone crossing re-solve beside it, and a fixture that cannot price either.
