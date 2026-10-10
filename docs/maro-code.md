<!-- scope: reference -->
# Maro-II Code Map

> Feature-to-code navigation map. Load when task involves code navigation, source structure,
> or package layout. Package-level skeleton + anchor classes — stable identifiers, low rot.

## Package Layout

| Package | Role | Key Files |
|---------|------|-----------|
| `data/model/` | Domain models — pure data classes, no logic | `LatLng.kt`, `BoundingBox.kt`, `DepthGrid.kt`, `CoastlineData.kt`, `CoastlinePoint.kt`, `CoastlineSegment.kt`, `Isobath.kt`, `Zone300Data.kt`, `GenerationProgress.kt`, `ListableItem.kt`, `RoutePoint.kt`, `RouteResult.kt`, `MapSelectionPolicy.kt`, `MapRenderFocus.kt`, `ListFilter.kt`, `ListAction.kt`, `ListSortOrder.kt`, `MultiActionSpec.kt`, `PointHazard.kt`, `RasterProgress.kt`, `RegionBounds.kt`, `ValidationReport.kt`, `CoastlineCache.kt`, `CoastlineDistanceResult.kt`, `CoastlineMetadata.kt`, `CoastlineState.kt`, `DepthState.kt` |
| `data/model/markers/` | User marker domain model | `UserMarker.kt` |
| `data/depth/` | Depth pipeline: generate, serialize, isobath extraction, raster caching (the validator lives in `validation/`) | `DepthGenerator.kt`, `DepthSerializer.kt`, `DepthRepository.kt`, `DepthIsobaths.kt`, `DepthMerge.kt`, `DepthZoneMask.kt`, `DepthConstants.kt`, `RasterCache.kt` |
| `data/depth/raster/` | Raster source parsers and clients | `AsciiGridParser.kt`, `EmodnetRestClient.kt`, `EmodnetWcsClient.kt`, `SourceRaster.kt` |
| `data/depth/validation/` | Depth control points and the validator that judges a generated grid | `DepthValidator.kt`, `ControlPoints.kt` |
| `data/coastline/` | Coastline pipeline: OSM fetch, serialize, hazard rings, seamarks | `CoastlineGenerator.kt`, `CoastlineRepository.kt`, `CoastlineSerializer.kt`, `HazardRings.kt`, `SeamarkParser.kt` |
| `data/regulation/` | Regulated zones: SHOM/IGN/INPN sources, aggregation, filtering | `RegulatedZonesRepository.kt`, `RegulationAggregator.kt`, `RegulationFilter.kt`, `RegulationSeeds.kt`, `ShomRegulationClient.kt`, `IgnCartoNatureClient.kt`, `InpnRegulationClient.kt`, `RegulatedZoneSerializer.kt`, `RegulatedZone.kt`, `SpeedZone.kt`, `SpeedZoneBuilder.kt` |
| `data/track/` | Boat tracking: record, persist, GPX export/import, merge, simplify | `TrackRecorder.kt`, `TrackRepository.kt`, `TrackViewModel.kt`, `TrackMerger.kt`, `TrackSimplifier.kt`, `GpxExporter.kt`, `GpxImporter.kt`, `TrackRecordingService.kt`, `BoatMarker.kt`, `Track.kt`, `TrackPoint.kt`, `TrackSample.kt`, `TrackEvent.kt`, `TrackPosition.kt`, `TrackSpeed.kt`, `TrackStats.kt`, `TrackFromCourse.kt`, `TrackGeofenceChecker.kt`, `IdleSessionContext.kt`, `IdleThresholdCallback.kt`, `StopRecordingReceiver.kt`, `WhereAmIProvider.kt` |
| `data/route/` | Route drawer's own models — one end's persisted selection, and the pace a route's trip figure plans at | `RouteEndSelection.kt`, `RoutePace.kt` |
| `data/markers/` | User markers CRUD (Pin, Circle, Corridor) + automatic marker creation | `UserMarkerRepository.kt`, `AutoMarkerManager.kt` |
| `data/location/` | GPS source, compass, adaptive policy | `GpsLocationSource.kt`, `CompassSource.kt`, `AdaptiveGpsPolicy.kt` |
| `data/settings/` | SharedPreferences wrapper | `SettingsManager.kt` |
| `data/power/` | Power management: framework-free screen-hold policy + its Android keeper | `PowerPolicy.kt`, `PowerKeeper.kt`, `BatteryExemption.kt`, `SpeedFreshness.kt` |
| `spatial/` | Spatial indexing and queries — the computational core | `CoastlineSpatialIndex.kt`, `MarkerMatcher.kt`, `SpeedZoneIndex.kt`, `NonSpeedZoneIndex.kt`, `PolygonIndexBase.kt`, `SpatialOperations.kt`, `Zone300Builder.kt`, `ZonePrimitives.kt`, `LandRingOrientation.kt`, `WhereAmIDebugger.kt`, `Units.kt`, `RouteEngine.kt`, `RouteEngineChoice.kt`, `RouteDummyEngine.kt`, `RouteAvoidEngine.kt`, `RouteEvolutiveEngine.kt`, `RouteSelectiveEngine.kt` |
| `spatial/multipass/` | The shared route machine every engine stands on — the unified cost field, the corridor grid, the A* and the taut pull | `RouteCostField.kt`, `MultipassWorld.kt`, `MultipassGrid.kt`, `MultipassSearch.kt`, `MultipassPull.kt`, `TangentCorners.kt`, `BerthCarve.kt`, `FineTile.kt`, `FineTileMap.kt`, `RouteCornerPass.kt`, `RouteCorridorChain.kt`, `RouteEta.kt`, `RouteFinePass.kt`, `RouteFineWater.kt`, `RouteGridBuilder.kt`, `RouteGridPlan.kt`, `RouteLogFormat.kt`, `RoutePassModels.kt`, `RoutePassPrimitives.kt`, `RoutePassRanking.kt`, `RoutePassRunner.kt`, `RoutePreference.kt`, `TileKey.kt`, `WalkLattice.kt`, `ZoneGeometry.kt` |
| `ui/map/` | Compose map screen, overlays, drawers, depth rendering, markers UI (72 files today — entry points only; the rest is discovered from `MapScreen.kt`) | `MapScreen.kt`, `MapSurface.kt`, `MapControls.kt`, `MapOverlays.kt`, `MapOverlayRenderer.kt`, `OverlayLayer.kt`, `OverlayLayerParams.kt`, `OverlayTracker.kt`, `OverlayZOrder.kt`, `InspectMode.kt`, `MapToggleFace.kt`, `MapPulseDot.kt`, `CardWalkPolicy.kt`, `CoastlineMapView.kt`, `TrackSharing.kt`, `DepthViewModel.kt`, `DepthBitmap.kt`, `DepthColorRamp.kt`, `DrawerSlot.kt`, `MarkerColors.kt`, `MarkerOverlay.kt`, `MarkerDrawer.kt`, `MarkersViewModel.kt`, `MarkerManagementOverlay.kt`, `WizardDrawer.kt`, `IconPickerDialog.kt`, `MenuDrawerOverlay.kt`, `TrackHistoryOverlay.kt`, `RegulatedZoneComponents.kt`, `FanLayout.kt`, `FanConfig.kt`, `NavigationViewModel.kt`, `MapOverlayData.kt`, `MapScreenChrome.kt`, `MapDashboardController.kt`, `MapLockLayer.kt`, `MapRouteEffects.kt`, `RouteViewModel.kt`, `RouteHost.kt`, `SettingsViewModel.kt` |
| `ui/components/` | Shared UI primitives | `CardArea.kt`, `ConfirmDialog.kt`, `DashboardBandGeometry.kt`, `DrawerScaffold.kt`, `DropdownBox.kt`, `DropdownPairRow.kt`, `DropdownRow.kt`, `DropdownWheel.kt`, `Expander.kt`, `ListOverlayScaffold.kt`, `ListSelectionCheck.kt`, `ListSelectionRail.kt`, `MarkerCreateAction.kt`, `MultiSelectRow.kt`, `NestedCard.kt`, `OptionRow.kt`, `PageDots.kt`, `PopupFamily.kt`, `SectionDivider.kt`, `SectionHeader.kt`, `SectionRow.kt`, `SegmentedRow.kt`, `SliderRow.kt`, `StatCell.kt`, `SubSectionHeader.kt`, `SwipePager.kt`, `SwipePolicy.kt`, `ToggleRow.kt`, `WheelPolicy.kt` |
| `ui/markers/wizard/` | Marker creation wizard — only the `steps/` form steps remain | `steps/TypeSelectStep.kt`, `steps/PositionStep.kt`, `steps/RoutingCostStep.kt`, `steps/SliderStep.kt`, `steps/TextInputStep.kt` |
| `ui/icons/` | Material Symbols as standalone ImageVector .kt files (21 today) | `ActivityZone.kt`, `AddLocationAlt.kt`, `FilterAlt.kt`, `LocationOn.kt`, `Route.kt`, `Visibility.kt`, etc. |
| `config/` | App-wide config constants and the `path.*` resolver | `AppConfig.kt`, `PathProperties.kt`, `HeatmapRamp.kt` |

## Feature → Package Cross-Reference

| Feature | Primary Packages |
|---------|-----------------|
| **DepthMapping** | `data/depth/`, `data/depth/raster/`, `spatial/`, `ui/map/DepthViewModel.kt`, `ui/map/DepthBitmap.kt` |
| **Coastline** | `data/coastline/`, `spatial/CoastlineSpatialIndex.kt` |
| **RegulatedZones** | `data/regulation/`, `spatial/SpeedZoneIndex.kt`, `ui/map/RegulatedZoneComponents.kt` |
| **Tracks** | `data/track/`, `ui/map/TrackHistoryOverlay.kt`, `ui/map/TrackStatusIcon.kt`, and the path render engine — `ui/map/LineRenderSeam.kt`, `ui/map/MapTrackSegments.kt`, `ui/map/MapTrackOverlayEffects.kt`, `ui/map/TrackDirectionOverlay.kt`, `ui/map/TrackSpeedHeatmap.kt` |
| **Markers** | `data/markers/`, `data/model/markers/`, `spatial/MarkerMatcher.kt`, `ui/map/MarkerOverlay.kt`, `ui/map/MarkerDrawer.kt`, `ui/map/MarkersViewModel.kt`, `ui/markers/wizard/` |
| **Zone300** | `spatial/Zone300Builder.kt`, `spatial/CoastlineSpatialIndex.kt`, `data/model/Zone300Data.kt` |
| **Route** | `spatial/RouteEngine.kt`, `spatial/RouteAvoidEngine.kt`, `spatial/multipass/`, `ui/map/RouteViewModel.kt`, `ui/map/RouteHost.kt` (painted by the shared path render engine — `ui/map/LineRenderSeam.kt`, `ui/map/MapTrackOverlayEffects.kt`) |
| **GPS** | `data/location/`, `config/AppConfig.kt` (GPS tuning constants) |
| **Performance** | `data/power/`, `data/location/`, `data/settings/SettingsManager.kt`, `config/AppConfig.kt` |
| **DepthSafety** | `ui/map/DepthViewModel.kt` (danger depth), `ui/map/LowDepthWarningBitmap.kt` |
| **ArcLayout** | `ui/map/FanLayout.kt`, `ui/map/FanConfig.kt`, `ui/map/FanIconComponents.kt` |
| **ZoneTile** | `ui/map/MapOverlayRenderer.kt` (zone tile rendering), `ui/map/RegulatedZoneComponents.kt` |
| **Ui_Settings** | `data/settings/SettingsManager.kt`, `config/AppConfig.kt` |
| **Ui_Menu** | `ui/map/MenuDrawerOverlay.kt`, `ui/map/DrawerSlot.kt` |
| **Ui_General** | `ui/map/MapScreen.kt` (back handler), `ui/components/` |
| **BakeNormalization** | Prebake tools in `app/src/test/` (JUnit prebake tests), `tools/` (bat scripts + GDAL) |

## Anchor Classes

> The first file to open for each domain. Stable entry points — not utility classes.

| Class | Package | Role |
|-------|---------|------|
| `MainActivity.kt` | `ykws/android/maro/` | Single-activity entry, Compose host |
| `AppConfig.kt` | `config/` | Central config constants — extents, thresholds, tuning |
| `MapScreen.kt` | `ui/map/` | Root Compose orchestration shell (chrome state holder, back handler, `MapContent` slot, `OverlayLayer` call); data builders, map overlays, and effect clusters extracted to sibling files (see MapScreen Decomposition below) |
| `CoastlineSpatialIndex.kt` | `spatial/` | Nearest-coastline queries, `isOnWater()`, distance-to-coast |
| `DepthRepository.kt` | `data/depth/` | Depth data load + query (memory-mapped, async) |
| `DepthViewModel.kt` | `ui/map/` | Depth state: color ramp selection, danger depth, rendering triggers |
| `CoastlineRepository.kt` | `data/coastline/` | Coastline data load + spatial index build |
| `RegulatedZonesRepository.kt` | `data/regulation/` | Multi-source zone aggregation (SHOM + IGN + INPN) |
| `RegulationAggregator.kt` | `data/regulation/` | Normalizes disparate zone formats into unified `RegulatedZone` |
| `TrackRecorder.kt` | `data/track/` | GPS fix → `TrackPoint` recording, idle detection, auto-marker |
| `TrackViewModel.kt` | `data/track/` | Track list state: CRUD, merge, export |
| `TrackRepository.kt` | `data/track/` | Track persistence (Room or flat file) |
| `UserMarkerRepository.kt` | `data/markers/` | User marker CRUD + proximity queries |
| `MarkerMatcher.kt` | `spatial/` | Proximity matching: which markers are near a given position |
| `SpeedZoneIndex.kt` | `spatial/` | Spatial index for speed zone lookup around boat |
| `Zone300Builder.kt` | `spatial/` | Generates 300m zone band from coastline |
| `RouteEngine.kt` | `spatial/` | The route's engine seam — declares the routes an engine can compute between two points and exposes one `Flow` of updates per lookup; its four implementations are `RouteDummyEngine`, `RouteAvoidEngine`, `RouteEvolutiveEngine` and `RouteSelectiveEngine`, picked by `RouteEngineChoice.kt` |
| `RouteCostField.kt` | `spatial/multipass/` | The shared layer's unified cost field — every world source (`RouteCostSource.Hard` walls / `Soft` prices) read through one evaluator, on a grid whose base cost a source may only add to |
| `OverlayLayer.kt` | `ui/map/` | Map overlay composition framework — layer stack management; its read-only data arrives via `@Immutable` bundles declared in `OverlayLayerParams.kt` |
| `MapOverlayRenderer.kt` | `ui/map/` | Renders overlays onto map (depth, zones, tracks, markers) |
| `SettingsManager.kt` | `data/settings/` | SharedPreferences read/write — all persisted config |
| `GpsLocationSource.kt` | `data/location/` | GPS location provider (real + demo mode) |
| `PowerKeeper.kt` | `data/power/` | Power management: gathers the inputs and publishes the screen-hold / keep-alive decision; its pure, framework-free half is `PowerPolicy.kt` |

## MapScreen Decomposition (2026-09 → 2026-10 refactor)

`ui/map/MapScreen.kt` is an orchestration shell whose concerns were extracted to same-package files
(step 1 settings extraction + step 2 orchestration-monolith refactor + step 3 mapscreen-health migration,
zero behavior change):

| File | Owns |
|------|------|
| `MapScreen.kt` | Orchestration shell: chrome state holder, click-n-move, back-handler ladder, `MapContent` stable slot, `OverlayLayer` invocation, snackbar/dialog/import state hoisting |
| `MapOverlayData.kt` | The five `OverlayLayer` data-construction builders (`MenuOverlayData`, `TrackListOverlayData`, `SettingsOverlayData`, `MarkerListOverlayData`, `TrackInfoOverlayData`) |
| `MapScreenChrome.kt` | The eight written chrome values in one `@Stable` holder (`showSettings`, `showTrackDrawer`, `showTrackHistory`, `showMarkerManagement`, `navigateToTarget`, `selectedTab`, `pendingResume`, `trackOpStatus`) with a `Saver` serialising `selectedTab` alone |
| `MapDashboardController.kt` | The snackbar stack (public `activeSnacks`, overflow queue, `enqueue`/`remove`) |
| `MapRouteEffects.kt` | Route engine/end effect clusters |
| `MapLockLayer.kt` | Screen-lock scrim, mirrored controls and lock banner |
| `MapScreenSettingsOverlay.kt` | Settings overlay subtree (4 tabs) |
| `MapGpsFollowEffects.kt` | GPS auto-follow DR, heading-up, zoom re-apply effect clusters |
| `MapTrackOverlayEffects.kt` | History/pinned track overlay diff + live-recording polyline effects |
| `MapMarkerEffects.kt` | Marker wiring (settings bridge, idle callback, cleanup) + debug-segment effects |
| `MapServiceEffects.kt` | Notification/water-state service intents + unconditional demo sample feed |
| `MapDepthRasterEffects.kt` | Depth/raster lazy-init (output contract) + regulated-zones loader |
| `MapDialogHost.kt` | Windowed dialogs/sheets: exit/stop-recording, recovery, permission, source-switch, battery, route-exit and resume dialogs |
| `MapSnackbarHost.kt` | Snackbar stack render (render-only; queue stays in `MapDashboardController`) |
| `MapImportConflictHost.kt` | GPX import Duplicate/Override/Cancel conflict path |
| `OverlayLayer.kt` | Transient drawer/scrim layer stack (Layer 1 — see `docs/ui-drawer-guidelines.md`); read-only params grouped into `@Immutable` bundles in `OverlayLayerParams.kt` |

## Path Render Engine — one painter for tracks and routes

Stored tracks, saved routes and the live route are drawn by **one engine**, so every aspect of a
track's or a route's line is a key in `maro.properties` under the `path.*` family (see the PATH
banner in that file). The seam is a **value type, not an interface**: a recorded track's points and a
route plan's points are adapted at the UI edge to `RenderPoint` (lat/lon, speed, bearing, break) and
wrapped in a `LineRenderSpec` (kind, class, points, dashed, arrows); the domain models (`TrackPoint`,
`RoutePoint`) implement nothing. One painter reads a spec and returns the osmdroid overlays and the
chevron inputs — the door is `lineRendering(spec, …)` in `MapTrackOverlayEffects.kt`, over
`MapTrackSegments.kt` (segments), `TrackSpeedHeatmap.kt` (the ramp and its bands) and
`TrackDirectionOverlay.kt` (the chevrons).

| File | Owns |
|------|------|
| `ui/map/LineRenderSeam.kt` | `RenderPoint`, `LineRenderSpec`, and the adapters — a track's stored-or-derived speed, a route's leg-derived speed |
| `ui/map/MapTrackSegments.kt` | `splitTrackSegments` and the polyline builders — solid runs, GAP bridges, the route dash |
| `ui/map/TrackSpeedHeatmap.kt` | The ramp's colour and the per-speed bands (`bandedAppearances`, `bandTable`) |
| `ui/map/TrackDirectionOverlay.kt` | The chevron overlay and its pure geometry |
| `ui/map/MapTrackOverlayEffects.kt` | The stored-track diff loops, the render plan (`lineRenderPlan`), and the one painter door (`lineRendering`) |
| `ui/map/RouteHost.kt` | The live route — its pool, casing, travelled run and provisional line — painted through the same door; the pin stays attach-once |
| `config/PathProperties.kt` + `config/AppConfig.kt` | The `path.*` cascade (`pathKeyCandidates`) and the resolver that walks it |

Precedence, most specific first: `path.<kind>.<group>.<class>.<leaf>` → `path.<group>.<class>.<leaf>`
→ `path.<kind>.<group>.<leaf>` → `path.<group>.<leaf>` → the code default; a class **outranks** the
kind. The one statement of the rule lives in the `maro.properties` PATH banner.

**The two display axes** — speed colours and direction arrows — are per-kind **persisted settings**: the
tracks kind's pair (`trackArrows` / `trackColours`) and the route kind's (`routeSpeedArrows` /
`routeSpeedColor`), each governing its own lines alone, with **no kind master over the other** (2026-10-07).
Each axis rides one `enabled` leaf with **three tiers** — `path.arrow.enabled` / `path.heatmap.enabled`
(global), the kind leaf over it, and `path.arrow.enabled.<class>` / `path.heatmap.enabled.<class>` — where
the global and kind leaves **seed** the persisted setting and only the class leaf **overrides** it at read
time ([`AppConfig.pathArrowEnabled`](../app/src/main/java/ykws/android/maro/config/AppConfig.kt) /
`pathHeatmapEnabled`). The **`acquisition`** class is the first such override: the route search's rung under
the selection is silent on both axes until the route is followed.

## Dependency Flow

```
ui/map/  ──depends on──▶  spatial/  +  data/*/
                                  │
                                  ▼
                            data/model/
```

- **`ui/`** depends on spatial indexes + data repositories. Never depends on raw data sources.
- **`spatial/`** depends on `data/model/` (pure data classes). No UI dependency.
- **`data/*/`** depends on `data/model/`. Repository layer talks to serializers + sources.
- **`data/model/`** depends on nothing — pure Kotlin data classes.
- **`config/AppConfig.kt`** is a constants file — imported everywhere, depends on nothing.

## Where to Look

| Task | Start Here |
|------|------------|
| Add a new map overlay | `ui/map/OverlayLayer.kt` → see existing overlay patterns; read-only state belongs in a bundle in `ui/map/OverlayLayerParams.kt`, never as a new signature param |
| Change the boat's tap zone or its accepted-tap flash | `ui/map/MapOverlays.kt` + `config/AppConfig.kt` + the `map.sprite.tap.*` keys (the whole family — zone, beat, colour and alpha — in `maro.properties`) |
| Add a new regulated zone source | `data/regulation/RegulationAggregator.kt` + new client class |
| Change how depth is rendered | `ui/map/DepthViewModel.kt` + `ui/map/DepthColorRamp.kt` |
| Add a track recording feature | `data/track/TrackRecorder.kt` → `TrackViewModel.kt` → `ui/map/TrackHistoryOverlay.kt` |
| Change how a track's or a route's line is drawn | `ui/map/LineRenderSeam.kt` (the seam) → `ui/map/MapTrackOverlayEffects.kt` (`lineRendering`) → the `path.*` keys in `maro.properties` |
| Add a settings toggle | `data/settings/SettingsManager.kt` + `config/AppConfig.kt` + settings UI composable |
| Add a new Material icon | `ui/icons/` (see `docs/material-icons-standalone-guide.md`) |
| Change GPS behavior | `data/location/GpsLocationSource.kt` + `data/location/AdaptiveGpsPolicy.kt` |
| Add a marker type | `data/model/markers/UserMarker.kt` + `data/markers/UserMarkerRepository.kt` + `ui/map/MarkerDrawer.kt` |
| Modify spatial query logic | `spatial/CoastlineSpatialIndex.kt` (for coastline) or `spatial/SpeedZoneIndex.kt` (for zones) |
| Change the route's algorithm, or add a world source it must avoid or price | `spatial/RouteAvoidEngine.kt` (the engine and its four seats) + `spatial/multipass/RouteCostField.kt` (the one evaluator — a new source is a `RouteCostSource.Hard` wall or `.Soft` price) |
