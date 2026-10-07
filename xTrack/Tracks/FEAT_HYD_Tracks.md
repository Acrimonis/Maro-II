# Context Hydration — Tracks — 2026-10-07

**Last Bake:** 2026-10-07 10:53 UTC — written by `#bake`
**Branch:** feature/tracks-rotes-norm — the path render engine's branch, cut from `origin/develop` with `--no-track`; the seam, resolver and render-core work landed earlier on it and this session finished the remainder, then extended the plan with the settled next normalisation (design only)

**Directive trace:** no covered action stopped, and nothing was bent. No dependency was added, no machine-shaped data file was opened, and the device was the user's throughout — no deploy, no logcat, the device pass left owed. Every claim about the code came from a file read or a build's own output, and the run's only figures — the 653-test scoped suite and the green `apk-build.bat` — are the commands' own output, re-read as such. This session's plan extension ran no build and touched no code, so it carries no figures of its own. Work ran directly in Code on the user's order; no `new_task` was spawned and no git write ran before the commit that closes the session.

## State

**The path render engine — one painter for tracks and routes — is complete on the branch, its device pass the one thing owed.** The plan [`261007_FEAT_PLN_Tracks_path-render-engine.md`](261007_FEAT_PLN_Tracks_path-render-engine.md) carried D1–D9; this run recorded **D10** (the live route's lines rebuild each pass, the destination pin stays attach-once — banding yields a stroke count a fixed pool cannot hold) and **D11** (the live route's per-point speed is derived from the plan's leg times). The rename generalised `TrackRenderPlan` / `TrackRenderPath` / `trackRenderPlan` to the kind-agnostic `LineRenderPlan` / `LineRenderPath` / `lineRenderPlan`, with the role factories `routeLineRenderPlan` / `pinnedLineRenderPlan`; the painter gained a `LineRenderSpec` door, `lineRendering(spec, …)`, over the existing `storedTrackRendering`; and `RouteHost` migrated onto it — its pool, derived casing, travelled run and provisional line now built through the one door, its `OverlayZOrder` `route_*` titles unchanged.

**Parity and one-home.** The live route now derives per-point speed from the `RoutePlan`'s leg times, so its chevrons and speed banding run, and its dash reads `path.line.dash.*`. The three hardcoded literals the plan flagged — the selection gold, the casing colour and the pin's ring — were wired to `path.line.color.selected`, `path.line.casing.color` and `path.pin.ring.color`, and both joined the stored effect's rebuild keys. The grep guard for `map.track.*`, `route.line.*`, `tracking.*route` and `map.track.heatmap.*` is clean across `app/`, `docs/` and the live feature files, bar the dated R93 text in the Route book.

**Designed, not built — the speed/arrow display normalisation.** The plan's `## Speed and arrow display` section now records the settled next step, and **no code for it exists**: the two axes (arrows, speed colours) each gain three real cascade tiers (global → kind → class) on one `enabled` leaf, the two axes become **persisted settings, one pair per kind** (the keys already exist, so nothing migrates), a new **`acquisition` class** silences the search's chevrons through the ordinary cascade, `path.gate.*` retires to `path.route.heatmap.enabled` / `path.route.arrow.enabled`, and the Settings surface **splits per kind** with no shared control. The review findings R1–R5 below the section record the plan's own stale header, the Seam-shape contradiction and the two dead masters (`path.arrow.enabled` / `path.heatmap.enabled` read by nothing). The plan's header was corrected to shipped with an `## Outcome`, and the section stays **in design, nothing implemented**.

**Open and recorded.** The **device pass is owed**: a route's chevrons and bands on the water, the pool's rebuild not flickering, and the pin still landing on the resolved end. The parked `route.avoid.fine.cellRatio` red is untouched and remains the scoped suite's single red. Two tests were added — `PathKeyCandidatesTest` (the resolver cascade at each precedence level) and `LineRenderSeamTest` (the adapters, the neutral-speed draft, the both-kinds-equal case). Named rather than lost: the Route feature's Feature-Summaries row in `GLOBAL_CONTEXT.md` could not be re-dated because its one line exceeds the editor's match limit, its front-matter's `modified` alone carrying this bake.

## Target Files

- `xTrack/Tracks/261007_FEAT_PLN_Tracks_path-render-engine.md` — the plan: D10 and D11, the corrected header and `## Outcome`, and the in-design `## Speed and arrow display` section with review findings R1–R5
- `app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt` — the rename, `lineRendering`, the shared `directionSpacingProvider`, the `RenderPoint` chevron door, the two literals wired, the rebuild keys
- `app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt` — migrated onto the painter; `appSettings` threaded from `MapScreen.kt`
- `app/src/main/java/ykws/android/maro/ui/map/InspectMode.kt`, `MapScreen.kt` — the rename and the `appSettings` argument
- `config/AppConfig.kt`, `config/HeatmapRamp.kt`, `data/settings/SettingsManager.kt`, `ui/map/MapScreenSettingsOverlay.kt` — the stale-key KDoc sweep
- The tests — `TrackRenderFlagsPathTest`, `TrackRouteRoleTest` renamed; `PathKeyCandidatesTest`, `LineRenderSeamTest` added
- `docs/maro-code.md`, `docs/color-scheme.md`, `docs/ui-component-guidelines.md`, `docs/ui-drawer-guidelines.md` — the engine described

## Next Step

Take the owed device pass over the path render engine — the route's chevrons and bands, the pool's rebuild, the pin — and the branch's fate (its merge) is the user's call; the speed/arrow display normalisation stays parked in the plan, in design, until it is ordered.
