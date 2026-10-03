# Context Hydration — Route — 2026-10-03

**Last Bake:** 2026-10-03 02:39 UTC — written by `#bake`

**Directive trace:** One session on `feature/more-routing`, every step on the user's own words — `#focus route`, then a discussion that became the plan, then a chain of `#impl` and `#impl fix` invocations each scoped to one named action, and the adb logcat reads only after the user's "done, fetch". Of the five covered action classes none was met unasked: no dependency was added, no machine-shaped data file was opened (the baked `.bin`/`.asc` assets stayed unread), no file was written without one of those words, the device was only reached through adb on the user's word, and no git write ran until `#commit`. The one deviation of record is that the earlier claim "λ = 1 means fastest route" was corrected mid-session to the user's own model — λ is the **effort** to avoid slow zones, and the "fastest" reading is only the formula's consequence at λ = 1.

## State

**The slow-water dials are live end to end, and the model that survives is one cursor.** Phases 0/A/B/D of [`261002_FEAT_PLN_Route_aversion-and-slow-water-model.md`](261002_FEAT_PLN_Route_aversion-and-slow-water-model.md) shipped: the aversion is a Settings dial seeded from `route.avoid.speedZone.softCostAversion`, read live by the engine at every solve; the budget overrun now reaches the confirmation panel and the dashboard through `budgetUnmetZoneShare`; the empty `route.avoid.candidate.passes` no-op was fixed so an empty key declares no candidates; the fine pass was made λ-respecting so it only splices a local re-solve when its λ-priced cost is no worse than the stretch it replaces; and the three providers were re-pointed at `viewModel.settings.value` so a slider move reaches the next solve instead of freezing on the first value.

**What the device run proved.** λ = 0 crosses the zones and hugs the band; λ ≥ 1 goes around, with only a few hundred metres between 1 and 5 — the linear scale compressing the visible range because the 10 kn zone is 2.8× slower than the 28 kn pace, so even λ = 1 already avoids. The plan's Phase C — the iterative λ calibration, the band in the budget, the unavoidable floor and the corrected-λ clamp at 50 — stays deferred to the device measurement of one re-solve pass.

**The consolidated design, settled but not built.** The three dials collapse into one: a fixed effort ladder (as many routes as the user asks, each its own aversion from none to maximum), the cruising speed as the open-water ceiling, and a **Driving preference** cursor whose only job is to auto-pick the rung — through slow water on one end, around it on the other. The aversion and budget rows are superseded; the budget's surviving role is the auto-pick criterion, ranked by slow-time share.

**Verification.** `apk-build.bat` green at every hop; the scoped `spatial`, `config` and `ui.map` suites green; the live-read and fine-pass fixes each built and tested. Nothing of this session is device-validated beyond the trace the user read.

**The record.** The `## Feature Summaries` Route row still stands beyond the tools' 2 000-character line cap, so neither it nor the Focus History prune could run; the shorter-row remedy remains the standing item.

## Target Files

- [`AppConfig.kt`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:93) — `parseCandidatePasses` now answers an empty list for a blank value
- [`SettingsManager.kt`](../../app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:159) — `routeSlowWaterAversion` seeded and clamped 0..5
- [`RouteEngineChoice.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngineChoice.kt:26) · [`RouteAvoidEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:141) — the aversion provider, the λ-priced splice guard, the per-stage traces
- [`MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:616) · [`MapRouteEffects.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapRouteEffects.kt:22) — the live settings providers
- [`RouteViewModel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:52) · [`RouteOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:141) · [`RouteConfirmPanel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:426) · [`DashboardPanel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt:359) — the surfaced overrun
- [`maro.properties`](../../app/src/main/assets/maro.properties:308) — candidates emptied for the single-line baseline
- [`261002_FEAT_PLN_Route_aversion-and-slow-water-model.md`](261002_FEAT_PLN_Route_aversion-and-slow-water-model.md) — the plan, D1–D9 resolved, Phase C deferred

## Next Step

On `feature/more-routing`: `#commit` the standing work (unstaged until then), merge in code, then build the consolidated Driving-preference model per the plan's `## Final arbitration` — the fixed effort ladder, the auto-pick and the single through-versus-around cursor — with Phase C's iterative calibration still waiting on the device measurement of one re-solve pass. The plan of record is [`261002_FEAT_PLN_Route_aversion-and-slow-water-model.md`](261002_FEAT_PLN_Route_aversion-and-slow-water-model.md), and the open walk items 1 and 2 in the epic stay as they are.
