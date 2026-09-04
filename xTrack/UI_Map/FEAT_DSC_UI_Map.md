---
name: UI_Map
status: active
created: 2026-06-07 00:00
modified: 2026-09-04 19:30
---

**Description:** Map display layer management — depth layer, color depth layer, orientation-aware rendering, marker highlight.

## Sections

### map refresh

Intermittent rendering issues: fan toggle inconsistency, marker effects not updating, markers disappearing until restart. Overlay mutations split into 6 per-layer LaunchedEffect blocks.

#### Todos
- [ ] Fix OverlayTracker reference staleness after ON→OFF→ON toggle cycles
- [ ] Verify StateFlow timing between SharedPreferences write and Compose collectAsState
- [ ] Add logging for markerLayerState transitions and mapView nullity
- [ ] Fix UserMarkerRepository error handling with fallback cache
- [ ] Verify marker list identity changes on CRUD for DisposableEffect restart
- [ ] Add marker generation counter to force MarkerOverlay DisposableEffect restart
- [ ] Build + on-device verify all three issues are resolved

#### Docs
- `xTrack/UI_Map/260704_FEAT_PLN_UI_Map_map-refresh-troubleshooting.md` — full troubleshooting plan

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`, `NavigationViewModel.kt`, `MarkersViewModel.kt`, `MarkerOverlay.kt`, `OverlayTracker.kt`, `FanLayout.kt`, `data/settings/SettingsManager.kt`

### depth color

#### Todos
- [ ] Align DepthCard background color with DepthColorRamp palette
- [ ] Map depthM → ARGB using same interpolation as the map overlay

#### Rules
- Dashboard depth tile color must match the map's hypsometric depth gradient
- Use DepthColorRamp.argb() as the single source of truth for depth→color mapping

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/DepthColorRamp.kt`

### layer-zone

Depth layer covers the full 6 NM navigable zone (sea within 6 NM of the coast, measured from the coastline).

#### Todos
- [ ] Re-bake the asset and verify on-device coverage to the shore + past capes

#### Rules
- Zone = sea within 6 NM of the coast; masked/out-of-zone cells are NaN → transparent

#### Key Files
- `app/src/main/java/ykws/android/maro/data/depth/DepthZoneMask.kt`, `DepthGenerator.kt`, `DepthConstants.kt`
- `app/src/test/java/ykws/android/maro/data/prebake/DepthPrebakeTest.kt`
- `tools/bake_emodnet.bat`, `tools/bake_litto3d.bat`

### config 300m auto display

Two per-mode toggles (`zone300AutoShowGps` / `zone300AutoShowDemo`) for the 300 m zone auto-show.

#### Todos
- [ ] Build + on-device verify (toggle off per mode suppresses auto-show; sliders hide)

#### Rules
- One boolean per mode (both default on); shared distance/time sliders render only when a toggle is on

#### Key Files
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`, `ui/map/MapScreen.kt`, `ui/map/CoastlineViewModel.kt`, `res/values*/strings.xml`

### layer-lowdepth

Bright magenta grounding-hazard overlay for water shallower than 1.5 m (runtime, no rebake).

#### Todos
- [ ] Pink-bleed fix — warning laps ~½ cell onto land at 25 m granularity (sub-cell water test or vector-clip)
- [ ] Verify on-device: offset gone + warning only on water; toggle + threshold persist

#### Rules
- Warn on ANY cell <1.5 m regardless of source/confidence; separate overlay above the depth raster
- No rebake — granularity is the baked 25 m grid

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/LowDepthWarningBitmap.kt`, `MapScreen.kt`, `data/depth/DepthConstants.kt`, `data/settings/SettingsManager.kt`, `res/values*/strings.xml`

### toggle-danger-layer

Map control button toggling the pink low-depth overlay, grouped above the 300 m toggle.

#### Todos
- [ ] Build + on-device verify (button toggles the pink layer; pair stays centred)

#### Rules
- All control-stack icons themed blue; danger button above 300 m toggle (8 dp), centred as a group; toggles `lowDepthWarningVisible` only

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `DangerLayerButton` + grouping
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — `toggleLowDepthWarningVisibility()`

### rotate

Manual two-finger map rotation in demo mode, deriving the cap-arrow bearing from rotation.

#### Todos
- [ ] Design and document the approach
- [ ] Determine bearing source: user gesture rotation vs pan-direction-derived
- [ ] Allow non-zero `mapOrientation` in demo mode
- [ ] Add two-finger rotation gesture (osmdroid or custom detector)
- [ ] Wire rotated bearing through `NavigationState.bearingDeg`
- [ ] Verify cap arrow / direction line / overlays at rotated angles
- [ ] Add "Demo mode heading-up" opt-in toggle
- [ ] Measure rotation repaint cost
- [ ] Build + on-device verification

#### Rules
- Cap arrow always draws straight up; demo bearing distinct from GPS bearing; rotation must not interfere with pan
- Default north-up preserved; toggle OFF by default

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`, `CoastlineViewModel.kt`, `data/settings/SettingsManager.kt`, `res/values*/strings.xml`

#### Docs
- `plans/rotate-map-demo-mode-implications.md`

### decenter-map

Shift boat position from screen centre to lower third when moving (dual offset: geo `setCenter` shift + screen `CenterMarkerOverlay` shift).

#### Todos
- [ ] Design dynamic offset behaviour (speed threshold, max fraction, animation)
- [ ] Implement map centre offset in GPS auto-follow
- [ ] Implement `CenterMarkerOverlay` downward screen offset
- [ ] Animate offset transitions (500–800 ms)
- [ ] Suppress offset when stationary / demo / heading unknown / manual pan
- [ ] Verify overlays remain fixed
- [ ] Verify tile loading at edges
- [ ] Build + on-device verification

#### Rules
- Offset only in GPS auto-follow with speed >5 kn (ramps 0→max over 5–15 kn); max fraction 0.25
- Dual correction; overlay controls stay fixed; smooth tween

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`, `CoastlineViewModel.kt`, `data/settings/SettingsManager.kt`

### overlay-layer

Unified drawer framework (`DrawerSlot` + `OverlayLayer`) hosting all 7 transient surfaces.

#### Todos
- [ ] On-device verify all 7 surfaces open/close with animations and shadows

#### Rules
- Layer 0 permanent, Layer 1 transient; drawers are pure content (no AnimatedVisibility/scrim/shadow); new drawers follow docs/ui-drawer-guidelines.md §5

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DrawerSlot.kt`, `OverlayLayer.kt`, `WizardDrawer.kt`, `markers/wizard/`, `MapScreen.kt`

## Implemented

- **map z-order** — deterministic overlay order (tile→base→tracks→markers) via `OverlayZOrder.reorder(mv)`
- **marker highlight dual-outline** — dark under-stroke behind gold highlight on all 5 marker geometry builders → `xTrack/Markers/260711_FEAT_PLN_Markers_marker-hilite-dual-outline-plan.md`
- **layer refresh** — single Box parent + `Modifier.align()` overlay; orientation-aware padding; `configChanges` manifest fix
- **zone proximity auto-reveal** — hybrid reveal (distance OR time-to-band) + auto-hide; pure `zone300Decision()` + tests
- **speed in demo** — pan-velocity-derived demo speed in SpeedCard (150ms cadence)
- **boat-center** — boat marker decoupled Image + cap-arrow Canvas; shifted down half-height
- **marker filter + dashboard close** — marker filter drives map overlay; panel auto-closes; list-context stacking removed

## Todos

## Rules

## Key Files

## Docs
- `xTrack/UI_Map/260614_FEAT_PLN_UI_Map_boat-marker-offset-discussion.md` — Boat marker offset discussion
- `xTrack/UI_Map/260616_FEAT_PLN_UI_Map_right-edge-gap-asymmetry.md` — Right edge controls gap asymmetry
- `xTrack/UI_Map/260616_FEAT_PLN_UI_Map_overlay-layout-inventory.md` — Map overlay layout inventory
- `xTrack/UI_Map/260616_FEAT_PLN_UI_Map_overlay-layout-rationalization.md` — Map overlay layout rationalization
- `xTrack/UI_Map/260612_FEAT_PLN_UI_Map_icon-rendering-overhaul.md` — Icon rendering overhaul
- `xTrack/UI_Map/260620_FEAT_PLN_UI_Map_decenter-map-discussion.md` — Decenter map design discussion
- `docs/map-lib-migration-plan.md` — osmdroid → MapLibre GL migration plan
- `xTrack/UI_Map/260711_FEAT_PLN_UI_Map_map-offset-dynamic-plan.md` — Map offset dynamic plan
- `xTrack/UI_Map/260712_FEAT_PLN_UI_Map_map-offset-phase2-properties-settings.md` — Map offset phase 2
- `xTrack/UI_Map/260712_FEAT_PLN_UI_Map_map-offset-scroll-fix-clean.md` — Map offset scroll fix
