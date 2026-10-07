# Context Hydration — Tracks — 2026-10-07

**Last Bake:** 2026-10-07 11:53 UTC — written by `#bake`
**Branch:** feature/tracks-rotes-norm — the path render engine's branch, cut from `origin/develop` with `--no-track`; the engine landed on it earlier, and this session shipped the speed/arrow display normalisation the same plan had left in design

**Directive trace:** no covered action stopped, and nothing was bent. No dependency was added, no machine-shaped data file was opened, and the device was the user's throughout — no deploy, no logcat, the device pass left owed. Every claim about the code came from a file read or a command's own output. Two test failures were read before the next edit rather than guessed at: the first exposed a real key-shape defect in the class-override reader (the qualifier landed before `enabled`, so the override would have silently missed its key), the second pinned the retired master/gate legend rule and was reconciled to the per-kind model. Work ran directly in Code on the user's `#impl` order; no `new_task` was spawned, and the git write the user has since ordered belongs to `#commit`, not to this bake.

## State

**The speed/arrow display normalisation is shipped on the branch.** Both display axes — arrows and speed colours — now read their **own kind alone**: the tracks pair (`trackArrows` / `trackColours`) and the route pair (`routeSpeedArrows` / `routeSpeedColor`), with the chips-as-master clause gone from `lineRenderPlan`, `routeLineRenderPlan` / `pinnedLineRenderPlan` made kind-scoped, and `bandedStrokeOnMap` asking each painted id's own pair. Each axis rides one `enabled` leaf with three tiers: `path.arrow.enabled` / `path.heatmap.enabled` (global) and the kind leaf **seed** the persisted setting at load — the kind seeds reproduce the shipped defaults, so **no stored value migrates** — and `path.arrow.enabled.<class>` / `path.heatmap.enabled.<class>` **override** it at runtime through `AppConfig.pathArrowEnabled` / `pathHeatmapEnabled` over the new `pathClassKeyCandidates`. The new `acquisition` class is the first such user: `RouteHost` gives the rung under the route selection `ACQUISITION` while the mode is choosing and `LIVE` once followed, so the search is silent on both axes through the ordinary cascade.

**The rest of the pass.** `path.gate.speedColor` / `path.gate.speedArrows` retired to `path.route.heatmap.enabled` / `path.route.arrow.enabled`, the Settings strings now name each kind (the drawer twin box governs the tracks kind alone), and the **drawer eye** became a **card-local, non-persisted override**: `AppSettings.trackSelectionBanded`, the `track_selection_banded` key and its `contains()` read all retire, the value living in `MapScreen` keyed on the open id and cleared with the card. `docs/maro-code.md` and `docs/ui-component-guidelines.md` carry the three tiers, and the plan's `## Speed and arrow display` header and the feature's `## Docs` pointer were corrected from "in design" to shipped.

**Open and recorded.** The **device pass is owed** for the engine and now also covers the eye and the per-kind axes. The parked `route.avoid.fine.cellRatio` red is untouched and remains the scoped suite's single red. The review hop's findings sit on the plan as R6–R9: **R6 (medium)** — the legend gate at `MapScreen.kt:3099` derives inside `remember(appSettings)` while the eye's state is recreated by `remember(highlightedTrackId)`, so the retained derivation closes over the discarded instance and an eye flip on an open card moves the stroke and not the scale; **R7 (low)** the class-override application is untested; **R8 (low)** two overlay parameters kept their old names; **R9 (low)** a redundant `eyeOverride == true` key read.

## Target Files

- `xTrack/Tracks/261007_FEAT_PLN_Tracks_path-render-engine.md` — the plan: the engine's `## Outcome`, the normalisation section corrected to shipped, and the findings R1–R9
- `app/src/main/assets/maro.properties` — the two axes' three tiers, the `acquisition` class leaves, the retired `path.gate.*`
- `app/src/main/java/ykws/android/maro/config/PathProperties.kt`, `config/AppConfig.kt` — `ACQUISITION`, `pathClassKeyCandidates`, the global/kind seeds and the class-override readers
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the four kind seeds; `trackSelectionBanded` and its key retired
- `app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt`, `ui/map/RouteHost.kt` — the per-kind axes and the acquisition rung
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`, `ui/map/MapOverlayData.kt`, `ui/map/OverlayLayerParams.kt` — the card-local eye
- `app/src/main/res/values/strings.xml` + `values-fr/strings.xml` — the per-kind labels
- `docs/maro-code.md`, `docs/ui-component-guidelines.md` — the axes' three tiers
- The tests — `PathKeyCandidatesTest` extended (the class leaf and a shipped-file guard), `TrackRouteRoleTest` / `TrackRenderFlagsPathTest` reconciled to the per-kind model

## Next Step

Take the owed device pass over the engine and the normalisation — a route's chevrons and bands, the pool's rebuild, the pin, and now the eye with the per-kind axes — and the branch's fate (its merge) is the user's call. The user then ordered three items assessed and planned in this session: the R6 fix, a **pulse red dot on the row that is the best candidate**, and the Routes Speed and Direction block re-shaped to the tracks block's two-choice multi-select control.
