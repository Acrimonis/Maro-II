<!-- scope: reference -->
# Maro-II Code Map

> Feature-to-code navigation map. Load when task involves code navigation, source structure,
> or package layout. Package-level skeleton + anchor classes — stable identifiers, low rot.

## Package Layout

| Package | Role | Key Files |
|---------|------|-----------|
| `data/model/` | Domain models — pure data classes, no logic | `LatLng.kt`, `BoundingBox.kt`, `DepthGrid.kt`, `CoastlineData.kt`, `CoastlinePoint.kt`, `CoastlineSegment.kt`, `Isobath.kt`, `Zone300Data.kt`, `GenerationProgress.kt`, `ListableItem.kt` |
| `data/model/markers/` | User marker domain model | `UserMarker.kt` |
| `data/depth/` | Depth pipeline: generate, serialize, validate, isobath extraction, raster caching | `DepthGenerator.kt`, `DepthSerializer.kt`, `DepthRepository.kt`, `DepthIsobaths.kt`, `DepthMerge.kt`, `DepthValidator.kt`, `RasterCache.kt` |
| `data/depth/raster/` | Raster source parsers and clients | `AsciiGridParser.kt`, `EmodnetRestClient.kt`, `EmodnetWcsClient.kt`, `SourceRaster.kt` |
| `data/coastline/` | Coastline pipeline: OSM fetch, serialize, hazard rings, seamarks | `CoastlineGenerator.kt`, `CoastlineRepository.kt`, `CoastlineSerializer.kt`, `HazardRings.kt`, `SeamarkParser.kt` |
| `data/regulation/` | Regulated zones: SHOM/IGN/INPN sources, aggregation, filtering | `RegulatedZonesRepository.kt`, `RegulationAggregator.kt`, `ShomRegulationClient.kt`, `IgnCartoNatureClient.kt`, `InpnRegulationClient.kt`, `RegulatedZoneSerializer.kt`, `RegulatedZone.kt`, `SpeedZone.kt`, `SpeedZoneBuilder.kt` |
| `data/track/` | Boat tracking: record, persist, GPX export/import, merge, simplify | `TrackRecorder.kt`, `TrackRepository.kt`, `TrackViewModel.kt`, `TrackMerger.kt`, `TrackSimplifier.kt`, `GpxExporter.kt`, `GpxImporter.kt`, `TrackRecordingService.kt`, `BoatMarker.kt` |
| `data/markers/` | User markers CRUD (Pin, Circle, Corridor) | `UserMarkerRepository.kt` |
| `data/location/` | GPS source, compass, adaptive policy | `GpsLocationSource.kt`, `CompassSource.kt`, `AdaptiveGpsPolicy.kt` |
| `data/settings/` | SharedPreferences wrapper | `SettingsManager.kt` |
| `spatial/` | Spatial indexing and queries — the computational core | `CoastlineSpatialIndex.kt`, `MarkerMatcher.kt`, `SpeedZoneIndex.kt`, `SpatialOperations.kt`, `Zone300Builder.kt` |
| `ui/map/` | Compose map screen, overlays, drawers, depth rendering, markers UI | `MapScreen.kt`, `MapOverlayRenderer.kt`, `DepthViewModel.kt`, `DepthBitmap.kt`, `DepthColorRamp.kt`, `OverlayLayer.kt`, `DrawerSlot.kt`, `MarkerOverlay.kt`, `MarkerDrawer.kt`, `MarkersViewModel.kt`, `MarkerManagementOverlay.kt`, `WizardDrawer.kt`, `MenuDrawerOverlay.kt`, `TrackHistoryOverlay.kt`, `RegulatedZoneComponents.kt`, `FanLayout.kt`, `FanConfig.kt`, `NavigationViewModel.kt` |
| `ui/components/` | Shared UI primitives | `DrawerScaffold.kt`, `ListOverlayScaffold.kt`, `ConfirmSheet.kt`, `IconPickerDialog.kt` |
| `ui/markers/wizard/` | Marker creation wizard (multi-step form) | `WizardTopBar.kt`, `WizardButtonRow.kt`, `steps/TypeSelectStep.kt`, `steps/PositionStep.kt`, `steps/SliderStep.kt`, `steps/TextInputStep.kt` |
| `ui/icons/` | Material Symbols as standalone ImageVector .kt files | `ActivityZone.kt`, `Add_location_alt.kt`, `FilterAlt.kt`, `Location_on.kt`, `WhereToVote.kt`, etc. |
| `config/` | App-wide config constants | `AppConfig.kt` |

## Feature → Package Cross-Reference

| Feature | Primary Packages |
|---------|-----------------|
| **DepthMapping** | `data/depth/`, `data/depth/raster/`, `spatial/`, `ui/map/DepthViewModel.kt`, `ui/map/DepthBitmap.kt` |
| **Coastline** | `data/coastline/`, `spatial/CoastlineSpatialIndex.kt` |
| **RegulatedZones** | `data/regulation/`, `spatial/SpeedZoneIndex.kt`, `ui/map/RegulatedZoneComponents.kt` |
| **BoatTrace** | `data/track/`, `ui/map/TrackHistoryOverlay.kt`, `ui/map/TrackStatusIcon.kt` |
| **Markers** | `data/markers/`, `data/model/markers/`, `spatial/MarkerMatcher.kt`, `ui/map/MarkerOverlay.kt`, `ui/map/MarkerDrawer.kt`, `ui/map/MarkersViewModel.kt`, `ui/markers/wizard/` |
| **Zone300** | `spatial/Zone300Builder.kt`, `spatial/CoastlineSpatialIndex.kt`, `data/model/Zone300Data.kt` |
| **GPS** | `data/location/`, `config/AppConfig.kt` (GPS tuning constants) |
| **DepthSafety** | `ui/map/DepthViewModel.kt` (danger depth), `ui/map/LowDepthWarningBitmap.kt` |
| **ArcLayout** | `ui/map/FanLayout.kt`, `ui/map/FanConfig.kt`, `ui/map/FanIconComponents.kt` |
| **ZoneTile** | `ui/map/MapOverlayRenderer.kt` (zone tile rendering), `ui/map/RegulatedZoneComponents.kt` |
| **Ui_Settings** | `data/settings/SettingsManager.kt`, `config/AppConfig.kt` |
| **Ui_Menu** | `ui/map/MenuDrawerOverlay.kt`, `ui/map/DrawerSlot.kt` |
| **Ui_General** | `ui/map/MapScreen.kt` (back handler, keep-screen-on), `ui/components/` |
| **BakeNormalization** | Prebake tools in `app/src/test/` (JUnit prebake tests), `tools/` (bat scripts + GDAL) |

## Anchor Classes

> The first file to open for each domain. Stable entry points — not utility classes.

| Class | Package | Role |
|-------|---------|------|
| `MainActivity.kt` | `ykws/android/maro/` | Single-activity entry, Compose host |
| `AppConfig.kt` | `config/` | Central config constants — extents, thresholds, tuning |
| `MapScreen.kt` | `ui/map/` | Root Compose composable — all state wiring, overlay dispatch |
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
| `OverlayLayer.kt` | `ui/map/` | Map overlay composition framework — layer stack management |
| `MapOverlayRenderer.kt` | `ui/map/` | Renders overlays onto map (depth, zones, tracks, markers) |
| `SettingsManager.kt` | `data/settings/` | SharedPreferences read/write — all persisted config |
| `GpsLocationSource.kt` | `data/location/` | GPS location provider (real + demo mode) |

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
| Add a new map overlay | `ui/map/OverlayLayer.kt` → see existing overlay patterns |
| Add a new regulated zone source | `data/regulation/RegulationAggregator.kt` + new client class |
| Change how depth is rendered | `ui/map/DepthViewModel.kt` + `ui/map/DepthColorRamp.kt` |
| Add a track recording feature | `data/track/TrackRecorder.kt` → `TrackViewModel.kt` → `ui/map/TrackHistoryOverlay.kt` |
| Add a settings toggle | `data/settings/SettingsManager.kt` + `config/AppConfig.kt` + settings UI composable |
| Add a new Material icon | `ui/icons/` (see `docs/material-icons-standalone-guide.md`) |
| Change GPS behavior | `data/location/GpsLocationSource.kt` + `data/location/AdaptiveGpsPolicy.kt` |
| Add a marker type | `data/model/markers/UserMarker.kt` + `data/markers/UserMarkerRepository.kt` + `ui/map/MarkerDrawer.kt` |
| Modify spatial query logic | `spatial/CoastlineSpatialIndex.kt` (for coastline) or `spatial/SpeedZoneIndex.kt` (for zones) |
