# Context Hydration — Route — 2026-10-10

**Last Bake:** 2026-10-10 17:13 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-10 15:24 UTC) the session ran on the user's own words: `#new rte-mark-cost` cut `feature/rte-mark-cost` from `origin/develop` with `--no-track` (no upstream), then *"plan the integration of markers with route cost to route acquisition"* was planned into [`261010_FEAT_PLN_Route_marker-cost-integration.md`](261010_FEAT_PLN_Route_marker-cost-integration.md) — reviewed twice, its decisions settled iteratively on the user's word — and run through `#implement`. No dependency was added, no machine-shaped data file was opened, no work was started without an order, the device was not touched, and every claim about the code follows a file read. Git: no merge ran; the work stood uncommitted until `#commit`.

## State

**The branch.** `feature/rte-mark-cost`, cut from `origin/develop` with no upstream; the session's work committed by `#commit`.

**The marker cost is read into the acquisition.** A costed **Circle** or **Corridor** marker is one **λ-free soft price** — the base per-metre cost scaled by the marker's step and the user's strength (`base × step × strength`) — and a `ROUTING_COST_BLOCKED` (10) marker is one **rastered hard wall** in the depth gate's own shape. Both reach the grid field (so the rasterizer bakes the price into `sourceCostSec` and paints the wall) and the guard field (so the pull prices and refuses identically) through [`costField`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:29). A **Pin** carries no cost and an **auto** marker carries no route step at all.

**The plumbing.** [`RouteMarker.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteMarker.kt:1) is the one new home: the projection (`RouteMarker`, `RouteMarkerGeometry`), the price law (`markerPriceSec`) and the sources (`markerSources`, `ZONE`-tagged, added only when non-empty so the marker-free fast path holds). [`MultipassWorld.routeMarkers()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt:120) declares it; [`LiveMultipassWorld`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt:144) projects `markersProvider` (wired at [`MapScreen.kt:743`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:743) from `markersViewModel.allMarkers`), dropping Pins and `IDLE_AUTO`. [`TileKey`](../../app/src/main/java/ykws/android/maro/spatial/multipass/TileKey.kt:28) gained the costed marker set, so a marker move re-tiles and no stale fine tile draws through a wall.

**The strength rides the factory.** [`RouteEngineChoice.factory`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngineChoice.kt:26) gained a `markerStrength: () -> Double` provider beside the pace, forwarded by `RouteAvoidEngine`/`RouteEvolutiveEngine`/`RouteSelectiveEngine` and threaded into `RouteGridBuilder`/`RoutePassRunner`/`RouteFinePass` as a live value.

**The repair sees the wall.** [`validWater`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:635) now treats a point inside a wall marker as invalid, so an end standing on a wall moves sea-side like a land or shallow end.

**The settings.** `route.marker.enabled` gates the **price alone** — a wall stands whatever it says — and `route.marker.costPerStep` seeds a new `AppSettings.routeMarkerStrength` read live by the provider. A Routing-tab "Marker strength" row (half-steps 0–5) and its both-locale strings ship; `route.marker.enabled` is config-only, matching the band and zone switches.

**The marker side.** The Pin's wizard step now shows the Route role alone (its cost column hidden) and a Pin writes `routingCost = null`; an auto (🕐) marker's `RoutingCost` step is dropped whole by both step-sequence mirrors, and `CreateFormState` carries the marker's `origin` to drive that.

**No open walk.** The feature file holds no `## Walk` (and no `## Implemented`: it is state-only by design), so nothing bars a fold.

**What is owed.** The plan's **engine-level fixture** — a line detouring around a priced marker and refusing through a wall — is not written (only the law/field fixtures are); and the **device pass** (R97) reading that detour and refusal on real water is the user's own.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteMarker.kt` — the projection, `markerPriceSec`, `markerSources` (new)
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt` — `routeMarkers()`, `markersProvider`, the projection override
- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt` — `costField` gains `markerStrength` and adds the marker sources
- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassModels.kt` — `GridContext.markerStrength`
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt` — threads the strength; `TileKey` gains the marker set
- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt`, `RouteFinePass.kt` — the guard fields take the strength
- `app/src/main/java/ykws/android/maro/spatial/multipass/TileKey.kt` — the costed marker set field
- `app/src/main/java/ykws/android/maro/spatial/RouteEngineChoice.kt`, `RouteAvoidEngine.kt`, `RouteEvolutiveEngine.kt`, `RouteSelectiveEngine.kt` — the strength provider; `validWater` sees the wall
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`, `app/src/main/assets/maro.properties` — `route.marker.enabled`, `route.marker.costPerStep`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — `routeMarkerStrength` + its key
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`, `MapRouteEffects.kt` — the markers provider + the strength wiring
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt`, `app/src/main/res/values{, -fr}/strings.xml` — the Marker strength row
- `app/src/main/java/ykws/android/maro/ui/markers/wizard/steps/RoutingCostStep.kt`, `ui/map/MarkersViewModel.kt`, `ui/map/OverlayLayer.kt` — the Pin cost hidden, the auto route step dropped
- `app/src/test/java/ykws/android/maro/spatial/multipass/RouteMarkerCostTest.kt` (new), `TileKeyTest.kt`, `FineTileMapTest.kt`, `FinePriorityWalkTest.kt`, `RouteFineReachabilityTest.kt`, `app/src/test/java/ykws/android/maro/spatial/RouteEngineChoiceTest.kt`

## Next Step

No code task is open on the feature beyond the owed engine-level fixture; the device pass (R97) is the user's own.
