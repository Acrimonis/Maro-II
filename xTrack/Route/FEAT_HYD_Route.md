# Context Hydration — Route — 2026-10-15

**Last Bake:** 2026-10-15 12:57 UTC — written by `#bake`; absence means never baked

**Directive trace:** The session opened on *"clarify the different steps in route acquisition … but first code to `#new rte-algo`"* — the branch cut first, its worktree stashed with `-u`, the one pop conflict in the epic's `## Docs` list resolved on the user's word (the ladder-lambda-keys entry kept, the three archived ones dropped). It then walked the acquisition end to end in the user's own questions: the four dashboard stages against the ten spine steps, what each step reports, the warm cache's saving, the three rungs' concurrency, why the coast is cheaper than the band, and whether the prices could be cached. A device-ordered investigation of **one invalid bend** followed — the pull has no lookback, so a plan and a fixture were built, implemented through `#implement`, instrumented, and carried to three device passes; the arm split then named the **band** as the price that keeps the bend, and the price's own unit mislabel was found and fixed. The crown itself came next, from *"what is the best compromise"* to two `#implement` runs that replaced the preference and the budget with **one weight and one sum**, then a consistency sweep clearing every residue its reviews named. No dependency was added, no machine-shaped data file was opened, no work was started without an order, and the device was never touched — every reading came from a trace the user fetched.

## State

**The branch.** `feature/rte-algo`, cut from `origin/develop` by the user's `#new` on the session's first order, carrying no upstream until its first push. It was **rebased onto `origin/develop`** on the user's `#merge`, which brought in the two Route features merged there since — the tile store's reclaim notification and ceiling, and the warm-up's coverage and workplace.

**The crown is one sum over the lines the ladder drew.** [`RoutePassRanking.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRanking.kt:1) crowns the landed rung with the lowest `trip time + weight × the line's own seconds under a limit`, where a `null` weight is the **ignore time** stop comparing those seconds alone with the clock as its tie, and a difference under `route.avoid.ladder.tieToleranceSec` (30 s) leaves the crown where it stands. `PassCost` carries the clock and the slow-water seconds; the ring and the band part is read from the per-limit table and the ramps that serve a limit from the new [`rampSlowSeconds`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt:315), a bend's own floor leaving both out.

**The dial has its own home.** [`VerdictDial.kt`](../../app/src/main/java/ykws/android/maro/config/VerdictDial.kt:1) holds six stops — **Fastest**, 1x, 2x, 3x, 4x, **Ignore time** — with the sentinel a `null` weight carried whole across settings, engine and ranking; `route.avoid.slowWater.weight` (4) is the default, the persisted value is the stop index (`route_slow_water_stop`, clamped 0..5), and the retired cursor is mapped onto it once so an upgrade moves no behaviour. The row reads `Slow water` / `Eau lente`.

**The old machinery is gone.** The seat mapping (`RoutePreference.of`/`ofIndex`, its indices and its words), the aversion cursor, the budget key with its row, its messages and its predicates (`budgetMet`, `withinBudgetBand`, `ZONE_BUDGET_BAND`) and the dormant `budgetUnmetZoneShare` are all removed end to end, in both locales. The 300 m band and the ramps that serve a limit now count in the crown's figure — a deliberate reversal of the 2026-09-30 exclusion, written into the decisions doc.

**Two contradictions were found and closed.** The ladder's code defaults now read the file's 5 / 3 / 1 and the fold tolerance's fallback its 200 m, each re-pinned where a fixture had leaned on the old number; the selective perf harness anchors 4074 expansions where the shipped λ 0 gave 2820, because the fast rung now prices at λ 1.

**Merged in, not this session's work.** The fine layer's pre-warm is now the app's own background job — its own background-priority worker, outward rings until the store's ceiling or the map's bounds, a live fullness verdict on the store seam, and a white dot meaning the warm-up alone — and the store's first reclamation notifies beside its toast. Their state and their open items (the warm-up's device pass, D1's cache-folder question, the planned ceiling rise to 72 MiB and the settled 80 % growth target) live in [`261015_FEAT_PLN_Route_warm-area-coverage-and-lifecycle.md`](261015_FEAT_PLN_Route_warm-area-coverage-and-lifecycle.md) and in the feature file's own bullets.

**The bend investigation, still open at its last step.** The pull has no lookback; a fixpoint removability sweep at the fine pass's settled line and a `SWEEP`/`SWEEP arms`/`SWEEP mark` instrumentation shipped, and the third device pass named the **band** arm as what keeps the user's bend — the depth arm refuted. Plans: [`261014_FEAT_PLN_Route_pull-lookback-pass.md`](261014_FEAT_PLN_Route_pull-lookback-pass.md) and [`261014_FEAT_PLN_Route_acquisition-perf.md`](261014_FEAT_PLN_Route_acquisition-perf.md).

**Gates.** `.\gradlew.bat testDebugUnitTest` BUILD SUCCESSFUL (1265 tests, 12 skipped) and `apk-build.bat` packaged `app-debug.apk` before the rebase; the tree is rebuilt and re-run after the merge. No device pass has run on the new dial.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRanking.kt` — the one sum, the sentinel and the tolerance
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt` — `rampSlowSeconds`, and the predicates the sweep removed
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — the cost site, `rungSlowWater`, the weight provider
- `app/src/main/java/ykws/android/maro/config/VerdictDial.kt`, `AppConfig.kt`, `app/src/main/assets/maro.properties` — the dial, its key, its default and the crown's tolerance
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the one stored stop and the legacy mapping
- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePreference.kt` — the three λ alone
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` — the `Slow water` row in both locales
- `app/src/main/java/ykws/android/maro/ui/map/` — the row, the trip figure, the drawer's quick access and the dashboard cell
- `xTrack/Route/261015_FEAT_PLN_Route_verdict-exchange-rate.md` — the plan of record, P0–P3 and P5 shipped
- `xTrack/Route/FEAT_DOC_Route_avoid-algorithm.md`, `FEAT_DOC_Route_engines.md`, `FEAT_DOC_Route_decisions.md`, `FEAT_DSC_Route.md` — the corpus

## Next Step

One phase is in design: **P4**, the card's own figures — the weight's word and the line's minutes in slow water where the panel does not already print them, to be read before anything is added. Two of the session's own items are the user's: whether a minute under a 5 kn limit should weigh more than one under a 20 kn limit (severity by limit, unbuilt), and the **new device pass** the dial owes — one pair armed at Fastest, at 2x and at Ignore time — beside the feature's older owed passes and the warm-up's, all of which gate nothing.

## Retirement candidates reported, not moved

- None from this session: no plan was folded and no doc was retired.
