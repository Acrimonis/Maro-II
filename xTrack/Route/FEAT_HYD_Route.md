# Context Hydration — Route — 2026-10-15

**Last Bake:** 2026-10-15 15:35 UTC — written by `#bake`; absence means never baked

**Directive trace:** The session opened on *"clarify the different steps in route acquisition … but first code to `#new rte-algo`"* — the branch cut first, its worktree stashed with `-u`, the one pop conflict in the epic's `## Docs` list resolved on the user's word. It then walked the acquisition end to end: the four dashboard stages against the ten spine steps, what each step reports, the warm cache's saving, the three rungs' concurrency, why the coast is cheaper than the band, and whether the prices could be cached. A device-ordered investigation of **one invalid bend** followed — the pull has no lookback, so a plan and a fixture were built, implemented through `#implement`, instrumented, and carried to three device passes; the arm split named the **band** as the price that keeps the bend, and the price's own unit mislabel was found and fixed. The crown itself came next, from *"what is the best compromise"* to two `#implement` runs that replaced the preference and the budget with **one weight and one sum**, then a consistency sweep clearing every residue its reviews named. The last stretch was the user's own reading of the panel: a visible winner, the rank per row, the names, the tie broken by a chain, and the severity of a slow second settled **linear**. No dependency was added, no machine-shaped data file was opened, no work was started without an order, and the device was never touched — every reading came from a trace the user fetched.

## State

**The branch.** `feature/rte-algo`, cut from `origin/develop` by the user's `#new` on the session's first order. It was **rebased onto `origin/develop`** on the user's `#merge` — which brought in the two Route features merged there since, the tile store's reclaim notification and ceiling and the warm-up's coverage and workplace — with the three conflicts resolved under the matrix, and it is **pushed** as `c71e7be6` with its upstream set.

**The crown is one sum, and a slow second is weighted by its own limit.** [`RoutePassRanking.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRanking.kt:1) crowns the landed rung with the lowest `trip time + weight × the line's weighted seconds under a limit`, where a `null` weight is the **ignore time** stop comparing those seconds alone with the clock as its tie, and `route.avoid.ladder.tieToleranceSec` (30 s) keeps the crown steady. Each second's weight is **linear in its own limit's shortfall** — `(pace − limit) / pace`, floored at zero, 0.8 at 5 kn and 0.2 at 20 kn on a 25 kn pace, **nothing at or above the pace** — defined once in [`slowWaterSeverity()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt:247) and read by the engine's cost site, where each per-limit bucket takes its own limit's severity and the ramp half rides the limit its slow run serves; a leg meeting **no** limit weighs **nothing**. The published per-limit table stays plain seconds, so the panel's minutes, the drawing, the clock and the dial are unmoved, while the crown's figure becomes the line's own excess under its limits.

**The panel shows the order.** The terminal carries `RouteVerdict` — the crown and every landed rung's **rank**, distinct `1..n`: the tolerance decides what a tie is and the crown stays that group's head, while the order inside a group runs the **exact score**, then **fewer weighted seconds**, then the **shorter trip**, then the row that **landed first**. The crowned row's name is closed by a checkmark in the route accent, and its **rank** sits on the row's second line — the ` so far ` word's own slot while the ladder computes, one slot and one clock — while the selection keeps the taken-choice face, so the two marks can stand on different rows. The header states the dial's own word beside the stage.

**The dial has its own home.** [`VerdictDial.kt`](../../app/src/main/java/ykws/android/maro/config/VerdictDial.kt:1) holds six stops — **Fastest**, 1x, 2x, 3x, 4x, **Ignore time** — with the sentinel a `null` weight; `route.avoid.slowWater.weight` (4) is the default, the persisted value is the stop index (`route_slow_water_stop`, clamped 0..5), and the retired cursor is mapped onto it once. The row reads `Slow water` / `Eau lente`, and the three rungs are named for what their lines do: **Detour · Balanced · Coastal** (`Détour · Équilibré · Côtier`).

**The old machinery is gone.** The seat mapping, the aversion cursor, the budget key with its row, its messages and its predicates, and the dormant `budgetUnmetZoneShare` are all removed end to end in both locales; the 300 m band and the ramps that serve a limit count in the crown's figure, a deliberate reversal of the 2026-09-30 exclusion recorded in the decisions doc.

**Merged in, not this session's work.** The fine layer's pre-warm is the app's own background job — its own background-priority worker, outward rings, a live fullness verdict, a white dot meaning the warm-up alone — and the store's first reclamation notifies beside its toast; their state and open items live in [`261015_FEAT_PLN_Route_warm-area-coverage-and-lifecycle.md`](261015_FEAT_PLN_Route_warm-area-coverage-and-lifecycle.md) and the feature file's own bullets.

**Gates.** `.\gradlew.bat testDebugUnitTest` BUILD SUCCESSFUL and `apk-build.bat` packaged `app-debug.apk` at every hop — the last run on the severity law itself, after which only comments and records moved. The un-ordered first version of that law was withdrawn and the tree re-run green then, and the merged tree was green as well, one `dexBuilderDebug` pass failing transiently before a rebuild succeeded. No device pass has run on the new dial.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRanking.kt` — the sum, the tolerance, `ranks()` and the tie-break chain
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — the cost site, `rungSlowWater`, `limitSeverity`, `settledVerdict`
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt` — `rampSlowSeconds` and `RampSlowWater`, the per-limit table
- `app/src/main/java/ykws/android/maro/config/VerdictDial.kt`, `AppConfig.kt`, `app/src/main/assets/maro.properties` — the dial, its key, its default and the tolerance
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the one stored stop and the legacy mapping
- `app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt` — the check, the rank on the second line, the header's dial word
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` — the `Slow water` row and the renamed rungs in both locales
- `xTrack/Route/261015_FEAT_PLN_Route_verdict-exchange-rate.md` — the plan of record, P0–P5 shipped plus the severity law
- `xTrack/Route/FEAT_DOC_Route_avoid-algorithm.md`, `FEAT_DOC_Route_engines.md`, `FEAT_DOC_Route_decisions.md`, `FEAT_DSC_Route.md` — the corpus

## Next Step

**Where the backlog is read.** The epic's `## Todos` now opens with **one consolidated list** of everything planned and not yet implemented — the crown's residues, the marker arm's `OTHER` reading, the four prose debts from the bend sweep, then the feature's older items — and its `## Docs` list gained the three plans it had been missing; a new session reads that list whole.

No code task is open. One item of the plan's own is owed: the line's minutes in slow water on the card, where the panel does not already print them — the dial's word in the header, the check, the ranks and the chain all landed. Two smaller residues stand beside it: a ranked rung whose page the UI fold collapses drops out of the panel's rank map, and the panel's marks carry no unit test. The device passes are the user's own and gate nothing — the dial's new one (armed at Fastest, 2x and Ignore time) among the feature's older ones.

## Retirement candidates reported, not moved

- None from this session: no plan was folded and no doc was retired.
