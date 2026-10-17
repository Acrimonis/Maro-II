# Context Hydration — Route — 2026-10-17

**Last Bake:** 2026-10-17 05:48 UTC — written by `#bake`; absence means never baked

**Directive trace:** The session opened on the user's `#focus roue` and `#new route-no-speed`, cutting the branch from `origin/develop` at `cdbd887f`, and ran as a discussion before any file moved: the pace's five roles were traced, a cost-only search weighed, and the two readings of *whatever speed* separated — a range of lines, or a scale whose first position is the absence of a preference. The user's word chose the second — *same dials + a `Any` in first position* — and `#impl` ran the pipeline. Two corrections came from the user mid-flight: *I did not authorized an implementation*, which voided two edits, saw them reverted and the loss reported, and then *try again and continue*, after which a clean rebuild rose above a poisoned Kotlin incremental state. The user's word also settled `route.avoid.pull.allowanceShare` at **0.1** across its test, its KDoc, its default and its comment. **No dependency was added, no machine-shaped data file was opened, the device was never touched, and every claim about the code stands behind a file read.**

## State

**The scale now opens on `Any`, and a numbered speed is untouched.** `AppConfig.ROUTE_FREE_WATER_PACE_ANY` (0 kn) sits before the six numbered stops, `ROUTE_FREE_WATER_PACE_OPTIONS_KN` is the one ordered list both doors read, and [`routePaceForSearchKn(setKn, observedKn)`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:109) is the one resolver: the boat's own pace under `Any` with evidence, the shipped `route.freeWaterPaceKn` (25) while there is none, and the setting's number otherwise.

**Both traps are closed and pinned.** [`SettingsManager`'s read](../../app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:663) passes the sentinel through unsnapped, and [`RouteViewModel.observePace`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:902) resolves the fallback before `RoutePace`'s own clamp — without either, a stored *no preference* would have become 5 kn, the slowest boat on the scale. [`FreeWaterPaceAnyTest`](../../app/src/test/java/ykws/android/maro/config/FreeWaterPaceAnyTest.kt:1) holds the list's first position, the predicate, the resolver's three cases and the snap guard.

**One resolved pace reaches the search.** `MapScreen` hoists `routeSearchPaceKn`, written only by the resolver effect, and both the engine factory and `MapRouteEngineEffect`'s new `paceProvider` read it, so the price law, the corner radii and the clock take one number. The trip card names the pace in force — the resolved one under `Any`, the setting otherwise.

**Two words, one stop.** The Settings row says `Any` (`settings_route_pace_any`), the drawer wheel says `--Any--` (`settings_route_pace_any_wheel`, `--Indifférent--` in French), and both locales carry the pair. The properties comment names the value as `Any`'s fallback.

**The gate and the drift.** `:app:testDebugUnitTest` is green at **1313 tests, 12 skipped**, and `:app:assembleDebug` packaged. `route.avoid.pull.allowanceShare` stays **0.1**, and its test, `AppConfig` KDoc, var default and properties comment were all brought to 0.1 in the same pass.

## Target Files

- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the sentinel, the option list, `isFreeWaterPaceAny`, `routePaceForSearchKn`, the corrected pace KDoc and the allowance default at 0.1
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the pace read passes the sentinel through unsnapped
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — `observePace` resolves the Any fallback before `RoutePace`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the hoisted `routeSearchPaceKn`, the factory lambda, the resolver effect and the trip card's pace
- `app/src/main/java/ykws/android/maro/ui/map/MapRouteEffects.kt` — the engine effect's `paceProvider`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — the slider opens on the sentinel and labels it `Any`
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt` — the wheel's first row, labelled `--Any--`
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml` — `settings_route_pace_any`, `settings_route_pace_any_wheel` and the rewritten pace description
- `app/src/main/assets/maro.properties` — the pace block names the Any fallback; the allowance comment now reads 0.1
- `app/src/test/java/ykws/android/maro/config/FreeWaterPaceAnyTest.kt` — the new suite
- `app/src/test/java/ykws/android/maro/config/PullAllowancePropertiesTest.kt` — the expected share moved to 0.1
- `xTrack/Route/261017_FEAT_PLN_Route_no-preferred-speed.md` — the plan of record, implemented
- `xTrack/Route/FEAT_DSC_Route.md`, `xTrack/Route/FEAT_DOC_Route_engines.md`, `xTrack/Route/FEAT_DOC_Route_avoid-algorithm.md` — the sentences that name the resolved pace

## Next Step

**The stop ships; the measurement of what `Any` is worth is the user's.** What stands open, none of it ordered:

- **Two tests with no harness** — a `SettingsManager` round-trip and a label-at-sentinel check were promised by the plan and cannot be written without one.
- **Demo mode freezes `Any` at 25 kn** — the pace window is fed in GPS mode only, so the settings description's promise holds once the boat moves and not before; the wording may want that clause.
- **The numbered stop keeps an older divergence** — the acquisition panel is handed the observed pace while the engine plans at the set number, unchanged by this session and now the only case where the two disagree.
- **The option list is built by a getter** — `ROUTE_FREE_WATER_PACE_OPTIONS_KN` allocates per recomposition of either door, as the older `STOPS_KN` does; a top-level value would remove it.
- **The pace-free design stays recorded and un-ordered** in the plan: effective metres, unranked proposals, and the per-limit calibration table that would replace the speed-derived dearness of a ring.
- **The device pass is the user's** — the visible subject is the row reading `Any`, the wheel reading `--Any--`, and a route armed cold planning at 25 kn until the boat has evidence.

## Retirement candidates reported, not moved

- [`261007_FEAT_PLN_Route_turn-impact-and-symmetry.md`](261007_FEAT_PLN_Route_turn-impact-and-symmetry.md) — its `## Outcome` says it settled on 2026-10-13 while its Status line still reads *in design, for discussion (point 3 of four)*.
- [`261007_FEAT_PLN_Route_coastal-fine-grid-engine.md`](261007_FEAT_PLN_Route_coastal-fine-grid-engine.md) — still in design, its third-engine ask mooted by the 2026-10-16 retirement.
- The 2026-10-16 report of a point-2 plan named nowhere still stands unresolved.
