# Context Hydration — Route — 2026-10-14

**Last Bake:** 2026-10-14 12:31 UTC — written by `#bake`; absence means never baked

**Directive trace:** Two sessions meet in this file, the rebase having replayed the second onto the first. The earlier one ran an independent review the user ordered — *read the sources yourself, my summary is a claim* — and implemented its findings on `#impl` through the pipeline Code → Ask → Architect: the zone price/clock split. This one ran on *"in route aquisition, clarify the lever of each of the 3 aquisitions"*, opened with *"swtch to ocde for #new route-aqu-x3"*, its scope pinned by two questions and no guessing — the ladder's three rungs with the aversion setting only seating one, then where those values should live — and closed on `#bake`, `#commit` and `#merge`. No dependency was added, no machine-shaped data file was opened, no work was started without an order, the device was never touched, and every claim about the code follows a file read.

## State

**The branch.** `feature/route-aqu-x3`, cut from `origin/develop` by the user's own `#new`, carrying no upstream. One commit — *each rung's lambda becomes its own route.avoid.ladder key* — rebased onto `origin/develop` on the user's `#merge`, which is how the display-limit split below arrived here.

**The lever of each acquisition is named, and it now lives in the file.** The three rungs' λ became three keys — `route.avoid.ladder.around.lambda=5`, `.best.lambda=2.5`, `.fast.lambda=0` — read by three `AppConfig` accessors clamped 0.0..5.0 through the dial's own ends, with the shipped ladder as the code fallback so a missing key cannot reshape it. `RoutePreference.lambda` became a per-entry getter over its own key, so every reader (`RouteAvoidEngine`'s `Computation.lambda`, `routeRungLambda`, both sliders and the drawer's quick access) kept its shape while the enum stopped holding a number.

**The dial's role is stated where it is read.** `route.avoid.speedZone.softCostAversion` is the **Driving preference and the ladder's seat**: its one act is to name which of the three rungs the ranking leads on, and it prices no cell. `route.avoid.speedZone.timeBudgetPct` is **Best's gate**, a ranking rule and never a search instruction. The rewritten sites are the properties' aversion, ladder and budget blocks, `AppConfig`'s aversion KDoc, `SettingsManager`'s two field KDocs and one key KDoc, the avoid-algorithm doc's §The λ and the budget (its pipeline line, its fine-pass line, its growth reason and its heading), and four code comments (`RouteEta`, `RoutePassModels` ×2, `RoutePassRanking`, `RoutePassPrimitives`).

**The retired λ loop left the prose.** The engine says *no budget loop* at [`RouteAvoidEngine.kt:120`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:120), and the corner-pass and merge history had left the loop described as live in one doc and five comments; all are corrected, with the archive files left as history. What was **found and not touched**: `withinBudgetBand`, `budgetMet` and `ZONE_BUDGET_BAND` in [`RouteEta.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt:354) are unread on any shipped path, and `budgetUnmetZoneShare` is inert end to end — the engine's one answer passes `null` ([`RouteAvoidEngine.kt:651`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:651)). The seat's cuts (1.25 / 3.75) stay code constants written around the shipped ladder, on the user's own choice.

**Carried in from the session before this branch: a zone's price and its clock are now separate laws.** `regulatedZone.speedRoute.<key>` is a **price only** — the search prices a zone at `routeLimitKn` while every **reported** figure times the **displayed** limit. [`strictestDisplayLimitKnAt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/ZoneGeometry.kt:92) stands beside [`strictestLimitKnAt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/ZoneGeometry.kt:71), reached through [`MultipassWorld.zoneDisplayLimitKnAt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt:144), whose `null` default makes an unwired world fail loudly rather than time the price law; [`limitAtFor`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:234) is the only re-pointed read, and `ZoneClockLawTest` pins the split in six cases.

**Two readers stay deliberately apart, and open.** [`forcedCrossingNames`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:792) still filters on the price, so a zone its `speedRoute` prices to nothing is never named as a forced crossing, and [`RoutePassRanking`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRanking.kt:1)'s Best gate and Fun objective now move with the clock — so both judge such a zone on a limit the routing never obeyed. That is one cross-listed todo.

**One defect is found and not fixed.** A hand-edited `regulatedZone.speedOverride.<key>=0` or a negative value passes `toDoubleOrNull` with no positivity guard, and the clock — unlike the price path, which guards `interiorLimitKn > 0.0` — divides by it, so [`timeLineWithLimits`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt:87) would yield an infinite leg time; the split made that value reachable to the clock. Carried as well: the tile key carries the corridor's priced-zone set, so a pre-warm box whose zones differ from an arm's corridor keys a different tile — a rebuild, never a wrong tile.

**Gates.** `:app:compileDebugKotlin` and the whole `:app:testDebugUnitTest` suite are BUILD SUCCESSFUL, `RoutePassRankingTest`'s λ assertions (0.0 / 2.5 / 5.0) passing unchanged. The merged tree is rebuilt once more by the `#merge` itself, since the display-limit split arrived with it. **No device pass is owed on the λ-key change**: the shipped values are identical, so no line, clock or ranking moves.

**The plans of record.** [`261014_FEAT_PLN_Route_ladder-lambda-keys.md`](261014_FEAT_PLN_Route_ladder-lambda-keys.md:1), listed in the epic's `## Docs` as implemented — the epic keeps no `## Implemented` log by its own statement of 2026-10-07 — and [`xTrack/RegulatedZones/261014_FEAT_PLN_RegulatedZones_speedroute-clock-split.md`](../RegulatedZones/261014_FEAT_PLN_RegulatedZones_speedroute-clock-split.md:1) for the split.

## Target Files

- `app/src/main/assets/maro.properties` — the three ladder key blocks, the aversion block and the budget block
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the three accessors, their load reads, and the aversion KDoc
- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePreference.kt` — the per-entry λ read
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the two field KDocs and the key constant
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt`, `RoutePassModels.kt`, `RoutePassRanking.kt`, `RoutePassPrimitives.kt` and `spatial/RouteAvoidEngine.kt` — the comments that outlived the λ loop
- `spatial/multipass/ZoneGeometry.kt`, `MultipassWorld.kt` — the two laws and the one lambda, from the split
- `xTrack/Route/FEAT_DOC_Route_avoid-algorithm.md`, `FEAT_DOC_Route_engines.md`, `FEAT_DSC_Route.md` — the corpus

## Next Step

No code task is open on the feature. Two items are the user's own: the cross-listed decision on whether the forced-crossing list and the rung ranking should follow the clock as the rest of the reporting now does, and the feature's owed device passes — the fine-tail and `selective` ones — untouched by either session.

## Retirement candidates reported, not moved

- `261007_FEAT_PLN_Route_selective-engine.md` and `261007_FEAT_PLN_Route_rung-ranking-preselection.md` — both attached and both marked landed in the feature's own `## Docs`, as the previous bake already reported.
