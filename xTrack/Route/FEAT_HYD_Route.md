# Context Hydration — Route — 2026-10-14

**Last Bake:** 2026-10-14 14:17 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-14 12:31 UTC) the session ran on the user's own `#impl` — the `#implement` pipeline's Code hop, its build, its Ask pass and the Architect report. No dependency was added, no machine-shaped data file was opened, no work was started without an order, the device was never touched by the agent, and every claim about the code follows a file read.

**Directive trace — the session merged in from `origin/develop`:** It ran on *"in route aquisition, clarify the lever of each of the 3 aquisitions"*, opened with *"swtch to ocde for #new route-aqu-x3"*, its scope pinned by two questions and no guessing — the ladder's three rungs with the aversion setting only seating one, then where those values should live — and closed on `#bake`, `#commit` and `#merge`. No dependency was added, no machine-shaped data file was opened, no work was started without an order, the device was never touched, and every claim about the code follows a file read.

## State

**The store's first reclamation now says itself twice.** `reportFineTileReclaimed()` raises its toast and then `postFineTileReclaimNotification()`: the app's **single** channel — `TrackRecordingService.CHANNEL_ID`, made `internal` so the id keeps one literal — its own id 1002 beside the banner's 1001, silent, auto-cancelling, a tap opening `MainActivity`, and posted only while `areNotificationsEnabled()` so a denial degrades to the toast alone. Both surfaces ride the store's own once-guard, and neither locale gained a string: `app_name` and `startup_fine_tile_reclaimed` carry them.

**One permission ask, at startup.** `requestNotificationPermission` is bound at construction and launched from a `LaunchedEffect(Unit)` at the head of the composition, on API 33+ only and only while the grant is missing, so a granted app is never asked again.

**The ceiling is 48 MiB, three homes in step.** `route.fineTile.maxBytes=50331648` in `maro.properties`, with `AppConfig.routeFineTileMaxBytes` and `DirectoryFineTileStore.DEFAULT_MAX_BYTES` mirrored in the same write and `FineTileStoreCeilingPropertiesTest` holding all three to it; `FineTileMap.DEFAULT_MAX_BYTES`, the in-memory 8 MiB, is untouched. `assembleDebug` and the scoped suites are green, the plan closed with its `## Implemented`, and its `(implemented)` row sits in the feature file's `## Docs`.

**The lever of each acquisition is named, and it now lives in the file.** The three rungs' λ became three keys — `route.avoid.ladder.around.lambda=5`, `.best.lambda=2`, `.fast.lambda=0` — read by three `AppConfig` accessors clamped 0.0..5.0 through the dial's own ends, with the shipped ladder as the code fallback so a missing key cannot reshape it. `RoutePreference.lambda` became a per-entry getter over its own key, so every reader (`RouteAvoidEngine`'s `Computation.lambda`, `routeRungLambda`, both sliders and the drawer's quick access) kept its shape while the enum stopped holding a number. The branch `feature/route-aqu-x3` was cut from `origin/develop` by the user's own `#new`, carrying one commit — *each rung's lambda becomes its own route.avoid.ladder key* — rebased onto `origin/develop` on the user's `#merge`.

**The dial's role is stated where it is read.** `route.avoid.speedZone.softCostAversion` is the **Driving preference and the ladder's seat**: its one act is to name which of the three rungs the ranking leads on, and it prices no cell. `route.avoid.speedZone.timeBudgetPct` is **Best's gate**, a ranking rule and never a search instruction. The rewritten sites are the properties' aversion, ladder and budget blocks, `AppConfig`'s aversion KDoc, `SettingsManager`'s two field KDocs and one key KDoc, the avoid-algorithm doc's §The λ and the budget (its pipeline line, its fine-pass line, its growth reason and its heading), and four code comments (`RouteEta`, `RoutePassModels` ×2, `RoutePassRanking`, `RoutePassPrimitives`).

**The retired λ loop left the prose.** The engine says *no budget loop* at [`RouteAvoidEngine.kt:120`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:120), and the corner-pass and merge history had left the loop described as live in one doc and five comments; all are corrected, with the archive files left as history. What was **found and not touched**: `withinBudgetBand`, `budgetMet` and `ZONE_BUDGET_BAND` in [`RouteEta.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt:354) are unread on any shipped path, and `budgetUnmetZoneShare` is inert end to end — the engine's one answer passes `null` ([`RouteAvoidEngine.kt:651`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:651)). The seat's cuts (1.25 / 3.75) stay code constants written around the shipped ladder, on the user's own choice.

**Carried in from the session before this branch: a zone's price and its clock are now separate laws.** `regulatedZone.speedRoute.<key>` is a **price only** — the search prices a zone at `routeLimitKn` while every **reported** figure times the **displayed** limit. [`strictestDisplayLimitKnAt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/ZoneGeometry.kt:92) stands beside [`strictestLimitKnAt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/ZoneGeometry.kt:71), reached through [`MultipassWorld.zoneDisplayLimitKnAt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt:144), whose `null` default makes an unwired world fail loudly rather than time the price law; [`limitAtFor`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:234) is the only re-pointed read, and `ZoneClockLawTest` pins the split in six cases.

**Two readers stay deliberately apart, and open.** [`forcedCrossingNames`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:792) still filters on the price, so a zone its `speedRoute` prices to nothing is never named as a forced crossing, and [`RoutePassRanking`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRanking.kt:1)'s Best gate and Fun objective now move with the clock — so both judge such a zone on a limit the routing never obeyed. That is one cross-listed todo.

**One defect is found and not fixed.** A hand-edited `regulatedZone.speedOverride.<key>=0` or a negative value passes `toDoubleOrNull` with no positivity guard, and the clock — unlike the price path, which guards `interiorLimitKn > 0.0` — divides by it, so [`timeLineWithLimits`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt:87) would yield an infinite leg time; the split made that value reachable to the clock. Carried as well: the tile key carries the corridor's priced-zone set, so a pre-warm box whose zones differ from an arm's corridor keys a different tile — a rebuild, never a wrong tile.

**Gates.** `:app:compileDebugKotlin` and the whole `:app:testDebugUnitTest` suite are BUILD SUCCESSFUL, `RoutePassRankingTest`'s λ assertions (0.0 / 2.5 / 5.0) passing unchanged; the reclaim-alert/ceiling session's `assembleDebug` and scoped suites are green too. The merged tree is rebuilt once more by the `#merge` itself, since the λ keys and the display-limit split arrived with it. **No device pass is owed on the λ-key change**: the shipped values are identical, so no line, clock or ranking moves.

**The plans of record.** [`261014_FEAT_PLN_Route_ladder-lambda-keys.md`](261014_FEAT_PLN_Route_ladder-lambda-keys.md:1), listed in the epic's `## Docs` as implemented — the epic keeps no `## Implemented` log by its own statement of 2026-10-07 — [`xTrack/RegulatedZones/261014_FEAT_PLN_RegulatedZones_speedroute-clock-split.md`](../RegulatedZones/261014_FEAT_PLN_RegulatedZones_speedroute-clock-split.md:1) for the split, and [`261014_FEAT_PLN_Route_reclaim-alert-cache-ceiling.md`](261014_FEAT_PLN_Route_reclaim-alert-cache-ceiling.md:1), closed with its `## Implemented`.

## Target Files

- `app/src/main/java/ykws/android/maro/MainActivity.kt` — the alert beside the toast, and the one permission ask
- `app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt` — `CHANNEL_ID`, the app's single channel, now readable
- `app/src/main/assets/maro.properties` — the 48 MiB ceiling's one home, and develop's three ladder key blocks, aversion block and budget block
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the 48 MiB mirror and its load read, the three ladder accessors, their load reads, and the aversion KDoc
- `app/src/main/java/ykws/android/maro/spatial/DirectoryFineTileStore.kt` — the ceiling's second mirror
- `app/src/test/java/ykws/android/maro/config/FineTileStoreCeilingPropertiesTest.kt` — the pin over all three
- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePreference.kt` — the per-entry λ read
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the two field KDocs and the key constant
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt`, `RoutePassModels.kt`, `RoutePassRanking.kt`, `RoutePassPrimitives.kt` and `spatial/RouteAvoidEngine.kt` — the comments that outlived the λ loop
- `spatial/multipass/ZoneGeometry.kt`, `MultipassWorld.kt` — the two laws and the one lambda, from the split
- `xTrack/Route/261014_FEAT_PLN_Route_reclaim-alert-cache-ceiling.md` — the plan of record, closed
- `xTrack/Route/FEAT_DOC_Route_avoid-algorithm.md`, `FEAT_DOC_Route_engines.md`, `FEAT_DSC_Route.md` — the corpus

## Next Step

No code task is open on the feature. Two items are the user's own and gate nothing: the alert's device pass (R97) — the grant taken on the prompt, the notification read in the shade on a reclaim forced with a temporary small ceiling, and the new bound watched across a pre-warm — and the cross-listed decision on whether the forced-crossing list and the rung ranking should follow the clock as the rest of the reporting now does. The feature's own open items are unchanged: the corner pass keeps its one item about the 50 m coast margin, and the fine-tail and `selective` device passes stay owed, untouched by either session.

## Retirement candidates reported, not moved

- `261007_FEAT_PLN_Route_selective-engine.md` and `261007_FEAT_PLN_Route_rung-ranking-preselection.md` — both attached and both marked landed in the feature's own `## Docs`, as the previous bake already reported.
