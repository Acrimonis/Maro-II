# Context Hydration — Route — 2026-09-30

**Last Bake:** 2026-09-30 16:08 UTC — written by `#bake`; absence means never baked

**Directive trace:** The implement session ran on the user's own `#implement`; no dependency was added, no machine-shaped data file was opened, and the device was never touched. Every claim about the code followed the file read that settled it; the commit and the push are the user's own commands.

## State

**The branch is `feature/route-apis`, and the engine-interface rework is built.** The seam is now `routesToCompute(origin, destination)` → declared computations → `startLookup(id)` → one id-carrying update flow, with the ring-sweep repair as the call's first step, the reason set closed at `CANNOT_REPAIR` · `WORLD_NOT_READY` · `NO_PATH` · `OFF_WATER`, the readiness gate and the refused crosshair retired, and the page set with the comparison in the panel. `gradlew :app:assembleDebug :app:testDebugUnitTest` is BUILD SUCCESSFUL with the whole unit suite green; nothing is device-validated.

## Target Files

- `260929_FEAT_DOC_Route_engine-interface.md` — the implementation spec this session built
- `spatial/RouteEngine.kt` · `RouteDummyEngine.kt` · `RouteAvoidEngine.kt` · `RouteEngineChoice.kt` — the seam and the two engines
- `ui/map/RouteViewModel.kt` · `RouteConfirmPanel.kt` · `RouteHost.kt` · `RouteOverlay.kt` · `MapScreen.kt` — the flow
- `config/AppConfig.kt` + `assets/maro.properties` + both `strings.xml` — the repair knob, the retired `route.target.*` keys and the new reason/computation strings

## Next Step

The device pass — the user's own: arm a route and read the repaired pair, the pages and the comparison; then shorten the Feature Summaries Route row so the next bake can write it.
