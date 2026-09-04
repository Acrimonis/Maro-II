---
name: UI_Map
status: active
created: 2026-06-07 00:00
modified: 2026-09-04 18:08
active_subfeature: decenter-map
---

**Description:** Map display layer management — depth layer, color depth layer, orientation-aware rendering, marker highlight.

## Subfeatures

### marker highlight dual-outline  [x]

Dark under-stroke (`0xCC000000`, +6f) rendered before gold highlight geometry on markers — same pattern as track highlight. Applied to all 5 geometry builders in `MarkerOverlay.kt` (pin dots, circle outlines, corridor centerlines/parallels/caps). `isHighlighted` flag threaded through all overlay builders, defaults to `false`.

#### Todos
- [x] Add `COLOR_HIGHLIGHT_UNDER` + `HIGHLIGHT_UNDER_STROKE_ADD` constants
- [x] Add `isHighlighted` param to `addPinOverlay`, `addCircleOverlay`, `addCorridorOverlay`, `addCorridorParallels`, `addSemiCircleCaps`
- [x] Wire dark under-strokes before gold in each function
- [x] Compute `isHighlighted = marker.id == highlightedMarkerId` at call sites
- [x] Build: SUCCESS (`gradlew assembleDebug`)

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt`

#### Docs
- `xTrack/Markers/260711_FEAT_PLN_Markers_marker-hilite-dual-outline-plan.md`
- `docs/ui-component-guidelines.md` §5.4

### map refresh  [-]

Investigation into intermittent rendering issues: fan layer toggle inconsistency, marker effects not updating, markers disappearing until restart.

#### Todos
- [x] Add logging to toggle chain: onChildClick → settingsManager.update → CoastlineMapView.update
- [ ] Fix OverlayTracker reference staleness after ON→OFF→ON toggle cycles
- [ ] Verify StateFlow timing between SharedPreferences write and Compose collectAsState
- [ ] Add logging for markerLayerState transitions and mapView nullity
- [ ] Fix UserMarkerRepository error handling with fallback cache
- [ ] Verify marker list identity changes on CRUD for DisposableEffect restart
- [ ] Add marker generation counter to force MarkerOverlay DisposableEffect restart
- [x] Isolate mv.overlays mutations to prevent cross-effect race conditions — split monolithic AndroidView.update into 6 per-layer LaunchedEffect blocks, each keyed on only its layer's data; removed dead params boatPosition/headingDeg; fixed depth produceState over-keying; coastline LaunchedEffect excludes zoomLevel (drawCoastline takes no zoom param)
- [ ] Build + on-device verify all three issues are resolved

#### Docs
- `xTrack/UI_Map/260704_FEAT_PLN_UI_Map_map-refresh-troubleshooting.md` — full troubleshooting plan

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/OverlayTracker.kt`
- `app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`

### layer refresh  [x]

#### Todos
- [x] Refactor MapScreen to single Box parent — stable MapContent slot, overlaid DashboardPanel via Modifier.align()
- [x] Apply orientation-aware padding to MapContent (left in landscape, bottom in portrait)
- [x] Add android:configChanges to manifest — prevents Activity destruction/recreation on rotation
- [x] Verify overlays survive orientation switch (no spurious redraw)
- [x] Test both landscape→portrait and portrait→landscape transitions

#### Rules
- MapContent must remain at a stable Compose slot position — never inside an if/else branch
- `Modifier.align()` is the correct mechanism for dashboard overlay positioning
- Use `PaddingValues` for map content offset, not Row/Column structural swap

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

### depth color  [ ]

#### Todos
- [ ] Align DepthCard background color with DepthColorRamp palette
- [ ] Map depthM → ARGB using same interpolation as the map overlay

#### Rules
- Dashboard depth tile color must match the map's hypsometric depth gradient
- Use DepthColorRamp.argb() as the single source of truth for depth→color mapping

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/DepthColorRamp.kt`

### zone proximity auto-reveal  [x]

#### Todos
- [x] Track `zone300ManuallyHidden` (armed) + `zone300AutoRevealed` + `bandEnteredSinceReveal` flags (session-only)
- [x] Hybrid reveal in the shore pipeline: closing + (within distance OR time-to-band at SOG)
- [x] Auto-hide: stopped & not closing, compliant inside (≤ reg speed), exited seaward, retreated past margin
- [x] Suppress auto-show while inside the band (reveal only from outside, dist > 0)
- [x] Configurable distance/time in zone.properties + Settings → Avancé; reg/stop speeds in ZoneConfig
- [x] Demo support via pan-derived speed (paused = 0 kn inside / unknown outside)
- [x] Extract pure, unit-tested `zone300Decision()`; `toggleZone300Visibility()` manages the armed flag

#### Rules
- Reveal only while OUTSIDE the band (dist > 0) and closing; hybrid = distance (default 200 m) OR time-to-band (default 20 s at SOG), whichever fires first
- Auto-hide on any of: stopped & not closing (≤ 1 kn), compliant inside (≤ 5 kn = `ZoneConfig.zoneRegulatorySpeedKn`), exited seaward, retreated past the reveal margin
- `armed` persists through an auto-hide → re-approaching re-reveals; a manual toggle disarms
- Speed source: GPS real SOG; demo pan-speed (null/unknown is never read as stopped/compliant). Decision logic is shared across modes (no gpsMode branch)
- Thresholds live in `zone.properties` AND Settings → Avancé (distance/time); regulatory + stopped speeds in `ZoneConfig`
- Decision is a pure, side-effect-free `zone300Decision()` covered by `Zone300DecisionTest`; shore pipeline samples every 150 ms
- Known edge: anchored within the reveal margin with GPS distance jitter could flap (deadband/cooldown not yet added)
- Replaces the old single-shot / fixed-400 m heuristic (`ZONE_AUTO_REVEAL_M` removed)

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — shore pipeline + `zone300Decision()` + flags
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — Settings → Avancé sliders
- `app/src/main/java/ykws/android/maro/ui/map/ZoneConfig.kt` — defaults (distance/time/reg speed)
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — persisted thresholds
- `app/src/main/assets/zone.properties` — tunable defaults
- `app/src/test/java/ykws/android/maro/ui/map/Zone300DecisionTest.kt` — unit tests

### speed in demo  [x]

#### Todos
- [x] Compute simulated speed in knots from map pan velocity during demo mode
- [x] Display in SpeedCard instead of "—" when in demo mode
- [x] Throttle computation to the existing 150ms shore pipeline cadence
- [x] Handle pan start/stop transitions gracefully (zero speed when map settles)

#### Rules
- Only active in demo mode (gpsMode == false) — GPS mode uses actual GPS speed
- Use Haversine distance between successive map center samples ÷ elapsed time
- The speed should settle to zero shortly after the user stops dragging (no persistent phantom speed)
- Surface via a new StateFlow (e.g., `demoSpeedKnots`) in CoastlineViewModel
- Dashboard SpeedCard merges: GPS speed (non-null) → demo speed (non-null) → "—"

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

### layer-zone  [ ]

Depth layer must cover the full **6 NM navigable zone** (the user's licence limit), not the old
inset rectangle. Zone = all sea within 6 NM (11 112 m) of the coast, measured from the coastline
itself so bays and capes alike get a uniform 6 NM (no cape under-coverage).

#### Todos
- [x] Widen `WATER_BBOX` envelope to cover coastline + 6 NM seaward (lat 43.31–43.74, lon 6.66–7.34)
- [x] Add `DepthZoneMask` — erase grid cells >6 NM from coast at bake time (reuses `CoastlineSpatialIndex`)
- [x] Hook the mask into `DepthGenerator.generate()` (new `coastlineSegments` param, after merge, before validate)
- [x] Load coastline asset in `DepthPrebakeTest` and pass its segments to the generator
- [x] Widen GDAL clip box in `bake_emodnet.bat` / `bake_litto3d.bat` to the new envelope
- [ ] Re-bake the asset (`tools\bake_depth.bat` + `gradlew … -Dmaro.prebake=true`) and verify on-device coverage to the shore + past capes

#### Rules
- Zone = sea within 6 NM (= 6 × 1852 = 11 112 m, `DepthZoneMask.SIX_NM_M`) of the coast; distance measured from the coastline, never a straight baseline
- Route A: keep the axis-aligned grid + `GroundOverlay`; the mask only NoData-s out-of-zone cells — no rendering changes
- Masked / out-of-zone cells are `NaN` (`DepthSource.NONE`) → fully transparent via `DepthColorRamp`
- The mask needs `assets/coastline/nice-frejus.bin`; without it the grid ships as the full rectangular envelope
- Envelope grows the `.bin` (~18→~25 MB est.); NaN cells still occupy the FloatArray — revisit with a coarser offshore cell / oriented grid only if size becomes a problem

#### Key Files
- `app/src/main/java/ykws/android/maro/data/depth/DepthZoneMask.kt`
- `app/src/main/java/ykws/android/maro/data/depth/DepthGenerator.kt`
- `app/src/main/java/ykws/android/maro/data/depth/DepthConstants.kt`
- `app/src/test/java/ykws/android/maro/data/prebake/DepthPrebakeTest.kt`
- `tools/bake_emodnet.bat`, `tools/bake_litto3d.bat`

### config 300m auto display  [ ]

Two per-mode toggles to activate the 300 m zone auto-show independently for GPS and Demo;
the shared distance/time thresholds are only shown when at least one toggle is on.

#### Todos
- [x] Add `zone300AutoShowGps` + `zone300AutoShowDemo` to `AppSettings` (persisted, default on)
- [x] Two toggles in Settings → Avancé → 300 m zone alert (one GPS, one Demo)
- [x] Show the shared distance/time sliders only when GPS or Demo auto-show is on
- [x] Gate the shore-pipeline auto-reveal on the active mode's toggle (reset decision state when off)
- [x] FR + EN strings for both toggles
- [ ] Build (`apk-build.bat`) + on-device verify (toggle off per mode suppresses auto-show; sliders hide)

#### Rules
- One boolean per mode (`gpsMode` true→GPS, false→Demo); both default **on** to preserve the existing always-on auto-reveal
- The distance/time sliders are shared across modes and only rendered when GPS or Demo auto-show is enabled
- When the active mode's toggle is off, the shore pipeline leaves `zone300Visible` under manual control and resets `zone300AutoRevealed` / `bandEnteredSinceReveal`

#### Key Files
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the 2 persisted booleans
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — toggles + conditional thresholds
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — per-mode gate in the shore pipeline
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml` — toggle labels

### layer-lowdepth  [ ]

Highlight all charted water shallower than 1.5 m as a bright, near-opaque grounding-hazard
overlay, drawn as a second `GroundOverlay` above the depth colour raster. Pure runtime layer —
**no rebake**: the shipped `nice-frejus.bin` already carries continuous per-cell depth.

#### Todos
- [x] Add `DepthConstants.LOW_DEPTH_WARNING_MAX_M = 1.5` (now the default threshold)
- [x] New `LowDepthWarningBitmap` — paint cells <threshold bright magenta, else transparent (mirrors `DepthBitmap`)
- [x] `AppSettings.lowDepthWarningVisible` (default on) + SharedPreferences persistence
- [x] MapScreen: sibling `produceState` build, visibility gate, `drawLowDepthWarning()` overlay, Settings toggle row
- [x] EN/FR label strings (`settings_low_depth_*`); `assembleDebug` green
- [x] Configurable warning depth (slider 0.5–5.0 m, default 1.5, persisted; overlay re-rasterises on change)
- [x] Fix depth-overlay Mercator offset via latitude-banding (`addBandedOverlay`, 8 strips; both depth + warning)
- [x] Mask the warning to water only (runtime `CoastlineViewModel.isOnWater`)
- [x] Bake-time land mask: `DepthZoneMask` nulls `!isWater` cells → re-baked `nice-frejus.bin`; colour map + isobaths + warning all water-only at the data level (validation passed, datumMismatch=false)
- [ ] Pink-bleed FIX — warning laps ~½ cell (~12 m) onto land at 25 m granularity (cells classified by CENTRE; not a mask gap). How: sub-cell water test (sample cell corners, require ~all water) OR vector-clip the warning bitmap to the coastline polygon — pick the crisper. Low-priority polish.
- [ ] Verify on-device: offset gone + warning only on water; toggle + threshold persist

#### Rules
- Warn on ANY cell <1.5 m regardless of source/confidence (design choice)
- Separate overlay above the depth raster, below isobaths/zone/coastline; reuse `DEPTH_MAP_MIN_DRAW_ZOOM` (z≥11)
- No rebake: granularity is the baked 25 m grid (shoalest-wins → conservatively over-flags shallow, correct for a hazard); finer outlines would need a smaller `GRID_RES_M` rebake

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/LowDepthWarningBitmap.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/data/depth/DepthConstants.kt`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml`

### toggle-danger-layer  [ ]

Map control button to toggle the pink low-depth/danger overlay (`lowDepthWarningVisible`), placed
just above the 300 m zone toggle in the right-edge control stack — the two grouped and centred
together (as the 300 m toggle was on its own).

#### Todos
- [x] `DangerLayerButton` composable — warning-triangle icon (themed blue, mirrors `LayerButton`)
- [x] Group it above `LayerButton` in an inner Column (8 dp gap), centred by the parent SpaceBetween
- [x] Thread `onToggleLowDepthWarning` through `MapContent` → `CoastlineViewModel.toggleLowDepthWarningVisibility()`
- [x] Polish: 300 m zone icon → circular ring; danger icon → blue; tighter control-stack padding (top/bottom/right 12→6 dp)
- [ ] Build (`apk-build.bat`) + on-device verify (button toggles the pink layer; pair stays centred)

#### Rules
- All control-stack button icons are themed blue (`0xFF1565C0`) for consistency (not the overlay magenta)
- Danger button sits ABOVE the 300 m toggle; the two stay close (8 dp) and centred as a group
- Toggles `AppSettings.lowDepthWarningVisible` only — no rebake, no depth-data change

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `DangerLayerButton` + control-stack grouping
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — `toggleLowDepthWarningVisibility()`

### rotate  [ ]

Allow manual two-finger rotation of the map in demo mode, and derive the heading/cap arrow bearing from that rotation instead of defaulting to north-up (0°). This transforms demo mode from a purely north-up free-pan mode into a heading-capable reference tool.

#### Todos
- [ ] Design and document the approach (see `plans/rotate-map-demo-mode-implications.md`)
- [ ] Determine bearing source in demo mode: user gesture rotation vs pan-direction-derived
- [ ] Allow non-zero `mapOrientation` in demo mode (remove the `mv.mapOrientation = 0f` hard reset at `MapScreen.kt:262`)
- [ ] Add two-finger rotation gesture to MapView in demo mode (osmdroid rotation support or custom gesture detector)
- [ ] Wire rotated bearing through `NavigationState.bearingDeg` in demo mode
- [ ] Verify cap arrow draws correctly at all rotation angles in demo mode
- [ ] Verify direction line (dashed heading line) works at rotated angles
- [ ] Verify depth overlays, isobaths, 300m zone, regulated zones render correctly at non-zero orientation
- [ ] Add setting toggle: "Demo mode heading-up" (opt-in) in Settings → Display
- [ ] Performance: measure invalidate() repaint cost of rotation in demo mode
- [ ] Build (apk-build.bat) + on-device verification

#### Rules
- The cap arrow must always draw straight up (screen-top) — same rule as GPS mode — map rotation aligns heading with screen-top
- Demo mode bearing source must be clearly distinct from GPS bearing (can't mix the two)
- Rotation gesture must not interfere with single-finger pan
- The `mapOrientation = 0f` reset in `MapScreen.kt:262` must be relaxed, not removed — keep a default north-up for non-rotated demo mode
- A setting toggle controls the feature; default OFF preserves existing north-up behavior
- Demo speed computation (pan-velocity to knots) is independent of rotation — both features coexist

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — orientation effect (`LaunchedEffect` at line 260), rotation gesture handling
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — `NavigationState.bearingDeg` update path for demo mode, speed computation
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — demo mode heading-up toggle persistence
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml` — toggle labels

#### Docs
- `plans/rotate-map-demo-mode-implications.md` — this analysis

### boat-center  [x]

#### Todos
- [x] Decouple boat marker Image and cap arrow Canvas in `CenterMarkerOverlay`
- [x] Shift boat marker down by half its height so top-center = map center (GPS antenna position)
- [x] Cap arrow stays centered at map center, drawn from canvas midpoint upward
- [x] Remove `BOAT_TIP_OFFSET` constant (no longer needed)
- [x] Land dot stays centered (no offset — a dot has no direction)
- [x] `DirectionLine` unchanged (already draws from screen center)
- [x] Build: SUCCESS (`gradlew assembleDebug`)

#### Rules
- Boat marker offset only applies on water (boat); land dot stays centered
- Cap arrow always originates from map center, not the boat image top

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `CenterMarkerOverlay` decoupled Image + Canvas

### decenter-map  [ ]

Shift the boat position from screen centre to the lower third of the viewport when the boat is moving, giving proportionally more screen area ahead. Implemented via a dual-offset approach: shift the osmdroid `setCenter` target upward in geo-space (so the forward area moves into view), and shift the `CenterMarkerOverlay` composable downward in screen-space to realign the boat icon with its true geo-position.

#### Todos
- [ ] Design and agree on the dynamic offset behaviour (speed threshold, max fraction, smooth animation)
- [ ] Implement map centre offset in GPS auto-follow path — adjust `setCenter`/`animateTo` target by projecting boat+offset to geo, using `mv.projection.toPixels`/`fromPixels`
- [ ] Implement `CenterMarkerOverlay` downward screen offset — `Modifier.offset(y = offsetFraction * height)` to align boat icon with its true position
- [ ] Animate offset transitions smoothly (0 ↔ maxOffset) over 500–800 ms when boat crosses speed threshold
- [ ] Suppress offset when: stationary, demo mode, heading unknown, or manual pan override (auto-follow suppressed)
- [ ] Verify overlays (Dashboard, control stack) remain fixed — they should NOT move with the map
- [ ] Verify tile loading at edges — osmdroid's default tile margin may show grey if offset is large
- [ ] Build + on-device verification

#### Rules
- Offset applies only in GPS auto-follow mode with meaningful speed (>5 kn); linearly ramps 0→max between 5–15 kn
- Max offset fraction: 0.25 (lower quarter) — configurable
- Dual correction: map centre shifts upward in geo-space, boat icon shifts downward in screen-space
- Overlay controls must remain at fixed screen positions — only map content and boat marker move
- Manual pan (auto-follow suppressed) disables offset; re-engage re-enables
- Smooth tween animation on offset change, not snapping

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — GPS auto-follow `LaunchedEffect`, `CenterMarkerOverlay` modifier, `MapContent` layout
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — speed state exposure for dynamic offset decision
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — optional offset toggle / max-offset setting

### overlay-layer  [x]

Unified drawer framework: `DrawerSlot` reusable animation wrapper + `OverlayLayer` self-contained
Layer 1 compositor. All 7 transient surfaces (scrim, Wizard, Menu, Marker, TrackHistory,
MarkerManagement, Settings) now live in `OverlayLayer`. Individual drawer composables are pure
content — no `AnimatedVisibility`, scrim, or shadow of their own. Wizard steps extracted to
`markers/wizard/` package.

#### Todos
- [x] Create `DrawerSlot.kt` — reusable `AnimatedVisibility` wrapper, `SlideDirection` + `ShadowEdge` enums, spring enter + tween exit, 8dp gradient shadow via `drawBehind`
- [x] Create `OverlayLayer.kt` — unified Layer 1 compositor rendering all 7 transient surfaces + unified scrim
- [x] Extract Wizard steps to `markers/wizard/` — `WizardTopBar`, `WizardButtonRow`, `TypeSelectStep`, `PositionStep`, `SliderStep`, `TextInputStep`
- [x] Fix Wizard blank — `WizardDrawer` receives `step` as non-null parameter
- [x] Strip scrim + shadow + `AnimatedVisibility` from `MarkerDrawer`, `MenuDrawerOverlay`, `TrackHistoryOverlay`, `WizardDrawer`
- [x] Wire `OverlayLayer` call in `MapScreen.kt` (line 1036)
- [x] Build: SUCCESS (`assembleDebug`)
- [ ] On-device verify all 7 surfaces open/close correctly with animations and shadows

#### Rules
- Layer 0 (Dashboard, controls, map) is permanent — never conditional
- Layer 1 (OverlayLayer) is transient — any new overlay fits into this framework
- Every drawer composable must be pure content: no `AnimatedVisibility`, no scrim, no shadow
- `DrawerSlot` provides animation + shadow; scrim is unified once in `OverlayLayer`
- New drawers follow the 6-step checklist in `docs/ui-drawer-guidelines.md` §5

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DrawerSlot.kt` — reusable slot
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — unified compositor
- `app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt` — thin shell (~228 lines)
- `app/src/main/java/ykws/android/maro/ui/markers/wizard/` — extracted step composables
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — OverlayLayer call at line 1036
- `docs/ui-drawer-guidelines.md` — canonical reference

## Implemented

### map z-order  [x]

Deterministic native overlay ordering: `OverlayZOrder.reorder(mv)` partitions
`MapView.overlays` into tile → base data layers → tracks → markers (top), preserving
within-band order, and is called after every overlay mutation.

#### Todos
- [x] Add `OverlayZOrder.kt` — `reorder()` + `isTrackOverlay`/`isMarkerOverlay` classification (title-prefix + type)
- [x] Wire `reorder()` into MapScreen track effects (history/pinned/active/highlighted, live restore, active trace, trailing) — 5 sites
- [x] Wire `reorder()` into MarkerOverlay rebuild — 1 site
- [x] Wire `reorder()` into CoastlineMapView base-layer effects (zone300, regulatedZones, depth, lowDepth, isobaths, coastline) — 6 sites
- [x] Build: SUCCESS (`assembleDebug`)

#### Rules
- `MapView.overlays` paints in index order; enforce order physically — OSMdroid has no per-overlay z-index
- After ANY overlay mutation, call `OverlayZOrder.reorder(mv)` before `mv.invalidate()`

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/OverlayZOrder.kt` — new helper
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt`

## Todos

## Rules

## Key Files

## Docs
- `xTrack/MapDisplay/FEAT_DOC_MapDisplay_marker-sizing.md` — centred boat marker sizing & behaviour on the map
- `xTrack/Performance/260610_FEAT_PLN_Performance_animateTo-interaction-analysis.md` — animateTo interaction with map refresh FPS analysis
- `xTrack/UI_Map/260614_FEAT_PLN_UI_Map_boat-marker-offset-discussion.md` — Boat marker offset discussion
- `xTrack/UI_Map/260616_FEAT_PLN_UI_Map_right-edge-gap-asymmetry.md` — Right edge controls gap asymmetry analysis
- `xTrack/UI_Map/260616_FEAT_PLN_UI_Map_overlay-layout-inventory.md` — Map overlay layout inventory
- `xTrack/UI_Map/260616_FEAT_PLN_UI_Map_overlay-layout-rationalization.md` — Map overlay layout rationalization
- `xTrack/UI_Map/260612_FEAT_PLN_UI_Map_icon-rendering-overhaul.md` — Icon rendering overhaul plan
- `xTrack/UI_Map/260620_FEAT_PLN_UI_Map_decenter-map-discussion.md` — Decenter map: dynamic downward offset analysis and design discussion
- `docs/map-lib-migration-plan.md` — Full migration plan: osmdroid → MapLibre GL, with CameraOptions.padding for decenter and pitch for tilt
- `xTrack/UI_Map/260711_FEAT_PLN_UI_Map_map-offset-dynamic-plan.md` — Map offset dynamic plan
- `xTrack/UI_Map/260712_FEAT_PLN_UI_Map_map-offset-phase2-properties-settings.md` — Map offset phase 2 properties settings
- `xTrack/UI_Map/260712_FEAT_PLN_UI_Map_map-offset-scroll-fix-clean.md` — Map offset scroll fix clean
- `xTrack/UI_Map/260616_FEAT_PLN_UI_Map_map-overlay-layout-inventory.md` — Map overlay layout inventory
- `xTrack/UI_Map/260616_FEAT_PLN_UI_Map_map-overlay-layout-rationalization.md` — Map overlay layout rationalization
- `xTrack/UI_Map/260616_FEAT_PLN_UI_Map_right-edge-controls-gap-asymmetry-analysis.md` — Right edge controls gap asymmetry analysis
