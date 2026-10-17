---
name: UI_Map
status: active
created: 2026-06-07 00:00
modified: 2026-10-17 13:06
---

**Description:** Map display layer management — depth layer, color depth layer, orientation-aware rendering, marker highlight.

## Sections

### map refresh

Intermittent rendering issues: fan toggle inconsistency, marker effects not updating, markers disappearing until restart. Overlay mutations split into per-layer `LaunchedEffect` blocks.

#### Docs
- `xTrack/UI_Map/260704_FEAT_PLN_UI_Map_map-refresh-troubleshooting.md` — full troubleshooting plan

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`, `NavigationViewModel.kt`, `MarkersViewModel.kt`, `MarkerOverlay.kt`, `OverlayTracker.kt`, `FanLayout.kt`, `data/settings/SettingsManager.kt`

### depth color

#### Rules
- Dashboard depth tile color must match the map's hypsometric depth gradient
- Use DepthColorRamp.argb() as the single source of truth for depth→color mapping

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/DepthColorRamp.kt`

### layer-zone

Depth layer covers the full 6 NM navigable zone (sea within 6 NM of the coast, measured from the coastline).

#### Rules
- Zone = sea within 6 NM of the coast; masked/out-of-zone cells are NaN → transparent

#### Key Files
- `app/src/main/java/ykws/android/maro/data/depth/DepthZoneMask.kt`, `DepthGenerator.kt`, `DepthConstants.kt`
- `app/src/test/java/ykws/android/maro/data/prebake/DepthPrebakeTest.kt`
- `tools/bake_emodnet.bat`, `tools/bake_litto3d.bat`

### config 300m auto display

Two per-mode toggles (`zone300AutoShowGps` / `zone300AutoShowDemo`) for the 300 m zone auto-show.

#### Rules
- One boolean per mode (both default on); shared distance/time sliders render only when a toggle is on

#### Key Files
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`, `ui/map/MapScreen.kt`, `ui/map/CoastlineViewModel.kt`, `res/values*/strings.xml`

### layer-lowdepth

Bright magenta grounding-hazard overlay for water shallower than 1.5 m (runtime, no rebake).

#### Rules
- Warn on ANY cell <1.5 m regardless of source/confidence; separate overlay above the depth raster
- No rebake — granularity is the baked 25 m grid

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/LowDepthWarningBitmap.kt`, `MapScreen.kt`, `data/depth/DepthConstants.kt`, `data/settings/SettingsManager.kt`, `res/values*/strings.xml`

### overlay-layer

Unified drawer framework (`DrawerSlot` + `OverlayLayer`) hosting all 7 transient surfaces. `OverlayLayer`'s
read-only params are grouped into six `@Immutable` bundles in `OverlayLayerParams.kt` (60 params total: 6 bundles +
explicit values/ViewModels + inline callbacks).

#### Rules
- Layer 0 permanent, Layer 1 transient; drawers are pure content (no AnimatedVisibility/scrim/shadow); new drawers follow docs/ui-drawer-guidelines.md §5

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DrawerSlot.kt`, `OverlayLayer.kt`, `OverlayLayerParams.kt`, `WizardDrawer.kt`, `markers/wizard/`, `MapScreen.kt`

## Implemented

- **inspect mode — the walk covers the whole filtered list (2026-10-17, `feature/inspect`)** — the viewport cut is withdrawn: `InspectBounds` and the candidate bbox leave `InspectRanking`, `rank()` and `nearest()` take just the anchor and the candidates, and both `boundingBox` reads leave the inspect effects, so the sweep's nearest and the walk cover every item the map holds while the filters and layer buttons still gate the set and the live track stays out.
- **inspect mode — one anchor at arming and a frozen walk owning the card (2026-10-17, `feature/inspect`)** — the acquire point is captured once at arming and never re-established, so each sweep ranks from where the mode was armed, while the pause's freeze stops the sweep publishing, stops the quiet clock re-arming and paints the cursor's item gold, a live open or close refused.
- **inspect mode — quiet at 1499 ms, its viewport ladder withdrawn the same day (2026-10-17, `feature/inspect`)** — `map.inspect.dwellMs` ships at 1499 with `AppConfig.uiMapInspectDwellMs` mirroring it and `InspectModePropertiesTest` pinning both homes; the viewport-only ladder it introduced was withdrawn by the entry above, so the walk again covers the map's full filtered set.
- **bottom-left tag column aligned to the zoom stack (2026-10-17, `feature/lyt-ui`)** — the bottom zone takes `ui.map.toggle.gutter` in both orientations, so the tag column and the zoom stack share one bottom line; the landscape navigation-bar inset and its import are gone, and the user's device pass read the two on that line. → `xTrack/UI_Map/FEAT_DOC_UI_Map_decisions.md`
- **routing square's loading dot reads the tile store's busy window (2026-10-13)** — a half-beat white mark while the fine-tile store works, cleared after a 400 ms grace, so one arming reads as one steady window. → `xTrack/UI_Map/FEAT_DOC_UI_Map_decisions.md`
- **the menu chevron gate adopts its list's order (2026-10-10, `feature/listery`)** — each chevron walks its own list's sort while membership stays the map filter, and a list sort edit closes a menu-opened card of either kind. → `xTrack/UI_Map/FEAT_DOC_UI_Map_decisions.md`
- **the layer fan as the route-and-tracks master gate (2026-10-10, `feature/rte-n-trck-list`)** — one master flag plus the two eyes read through `kindLayerOn`, the open card's item the gates' one escape and the filter never escaped. → `xTrack/UI_Map/261010_FEAT_PLN_UI_Map_route-tracks-master-gate.md`
- **the toggle squares' colour normalization (2026-10-09)** — every square resolves through one pure `TopToggleFace` home, six readerless palette keys deleted, and the route square no longer reads the user's line colour. → `xTrack/UI_Map/261009_FEAT_PLN_UI_Map_toggle-colour-normalization.md`
- **inspect mode — live acquire (2026-10-07)** — the nearest inspectable item is re-ranked on every sweep and its dashboard opens at once, the pause's expiry recentring and freezing the ladder a drag then resets. → `xTrack/UI_Map/261007_FEAT_PLN_UI_Map_inspect-live-acquire.md`
- **the map dash family on one frame (2026-10-07)** — one header padding, a scrollable body, the body-only dissolve, square corners in both orientations and one walk row across the marker card, the track card and the route panel. → `xTrack/UI_Map/261007_FEAT_PLN_UI_Map_inspect-live-acquire.md`
- **mapscreen-health remedy (2026-10-01, `feature/mapscreen-health`)** — Bodies, Builders, Chrome and Close took the callback bodies, the data constructions, an `@Stable MapScreenChrome` and the anchors' re-measure, with zero behaviour change. → `xTrack/UI_Map/260930_FEAT_PLN_UI_Map_mapscreen-health-monolith.md`
- **map paint lengths in dp (2026-09-19)** — the strokes, dashes, offsets and paddings convert at the caller through one `dpToPx`, and the three settings widths are `…Dp` end to end. → `xTrack/UI_Map/260919_FEAT_PLN_UI_Map_px-to-dp-migration.md`
- **marker zoom scale configurable (2026-09-19)** — the growth exponent is `map.marker.size.zoomExponent` (0.35) with the base pair `map.marker.size.boatBaseDp` / `.dotBaseDp` as its own keys, read by the sprite, the wizard's crosshair and the cap arrow alike. → `xTrack/UI_Map/260919_FEAT_PLN_UI_Map_marker-zoom-scale.md`, `xTrack/UI_Map/FEAT_DOC_UI_Map_marker-sizing.md`
- **inspect mode (2026-09-17)** — drag-to-select by proximity over the canonical selection path, the ⊕ square gated on having something inspectable, with no second renderer. → `xTrack/UI_Map/260917_FEAT_PLN_UI_Map_inspect-mode.md`
- **whereami tap zone + debug-ray clearing (2026-09-18, `feature/where-are-zone-trans`)** — the boat's touch box is a fixed radial circle with a gold pulse, and the rays clear on the drawer-state funnel and on the rays toggle. → `xTrack/Markers/260918_FEAT_PLN_Markers_whereami-tap-zone-and-ray-clear.md`
- **marker filter + dashboard close (2026-09-04)** — the marker filter drives the map's own set and the map-opened panel stands on a filter write; superseded in part 2026-09-28, the rule living in `xTrack/Ui_General/FEAT_DSC_Ui_General.md`. → `xTrack/UI_Map/260904_FEAT_PLN_UI_Map_marker-filter-map-and-dashboard-close.md`
- **demo heading-up rotation** — the two-finger gesture rotates `mapOrientation` while `demoBearingDeg` stays unset and the auto-show cone keeps the nav heading; north-up by default. → `xTrack/UI_Map/FEAT_DOC_UI_Map_decisions.md`
- **map offset (de-centring)** — a dual geo + screen offset on the `map.offset.lookahead.*` keys, suppressed while stationary, panned or gated. → `xTrack/UI_Map/260711_FEAT_PLN_UI_Map_map-offset-dynamic-plan.md`, `260712_FEAT_PLN_UI_Map_map-offset-phase2-properties-settings.md`
- **map z-order** — deterministic tile→base→tracks→markers order through `OverlayZOrder.reorder(mv)`. → `xTrack/UI_Map/260903_FEAT_PLN_UI_Map_z-order-fix.md`
- **layer refresh** — per-layer `LaunchedEffect` blocks over one Box parent with `Modifier.align()`, orientation-aware padding and the `configChanges` manifest fix. → `xTrack/UI_Map/260704_FEAT_PLN_UI_Map_map-refresh-troubleshooting.md`
- **zone proximity auto-reveal** — hybrid distance-or-time reveal with auto-hide, derived at the marker point rather than the boat. → `xTrack/UI_Map/FEAT_DOC_UI_Map_decisions.md`
- **speed in demo** — pan-velocity-derived demo speed in the SpeedCard. → `xTrack/UI_Map/FEAT_DOC_UI_Map_decisions.md`
- **boat-center** — the boat marker decoupled from the cap-arrow Canvas and shifted down half its height. → `xTrack/UI_Map/260614_FEAT_PLN_UI_Map_boat-marker-offset-discussion.md`
- **marker highlight dual-outline** — a dark under-stroke behind the gold highlight across the five marker geometry builders. → `xTrack/Markers/260711_FEAT_PLN_Markers_marker-hilite-dual-outline-plan.md`

## Todos
- [ ] `map refresh` — fix OverlayTracker reference staleness after ON→OFF→ON toggle cycles
- [ ] `map refresh` — verify the StateFlow timing between a SharedPreferences write and the Compose `collectAsState`
- [ ] `map refresh` — add logging for markerLayerState transitions and mapView nullity
- [ ] `map refresh` — fix UserMarkerRepository error handling with a fallback cache
- [ ] `map refresh` — verify the marker list identity changes on CRUD so the DisposableEffect restarts
- [ ] `map refresh` — add a marker generation counter to force the MarkerOverlay DisposableEffect restart
- [ ] `map refresh` — build + on-device verify all three issues are resolved
- [ ] `depth color` — align the DepthCard background colour with the DepthColorRamp palette
- [ ] `depth color` — map depthM → ARGB with the map overlay's interpolation
- [ ] `layer-zone` — re-bake the asset and verify on-device coverage to the shore and past the capes
- [ ] `config 300m auto display` — build + on-device verify: a toggle off suppresses its mode's auto-show and the sliders hide
- [ ] `layer-lowdepth` — pink-bleed fix: the warning laps ~½ cell onto land at 25 m granularity (sub-cell water test or vector clip)
- [ ] `layer-lowdepth` — verify on-device: the offset gone and the warning on water only; toggle and threshold persist
- [ ] `overlay-layer` — on-device verify all 7 surfaces open and close with their animations and shadows
- [ ] Inspect live-acquire device pass — a drag whose panel swaps as the nearest changes, the pause's recentre, a post-recentre drag resetting everything, the toggle / Back / boat-icon exits, the anchor staying off while armed, a marker↔route swap settling without a jump, and the walk row holding one height across the three panels → `xTrack/UI_Map/261007_FEAT_PLN_UI_Map_inspect-live-acquire.md`
- [ ] Device pass against the plan's two tables — the sprite at levels 19 and 20 offshore and inshore, and the 15 % raise on the base pair at each level → `xTrack/UI_Map/260919_FEAT_PLN_UI_Map_marker-zoom-scale.md`
- [ ] Second-density check for the dp pass — an emulator at 1× or 2× showing the strokes scale, since on the 3× tuning phone this change is invisible by design → `xTrack/UI_Map/260919_FEAT_PLN_UI_Map_px-to-dp-migration.md`
- [ ] Open Medium from the dp review — the width rows' half-dp grid cannot reach the coastline's shipped 3.333 dp default, so a dragged row loses the 10 px look → `xTrack/UI_Map/260919_FEAT_PLN_UI_Map_px-to-dp-migration.md`

## Docs
- `xTrack/UI_Map/FEAT_DOC_UI_Map_decisions.md` — the shipped decisions grouped by subject, with rationale and sources
- `xTrack/UI_Map/FEAT_DOC_UI_Map_marker-sizing.md` — Marker sizing curve and its tunable keys
- `xTrack/UI_Map/260614_FEAT_PLN_UI_Map_boat-marker-offset-discussion.md` — Boat marker offset discussion
- `xTrack/UI_Map/260616_FEAT_PLN_UI_Map_right-edge-gap-asymmetry.md` — Right edge controls gap asymmetry
- `xTrack/UI_Map/260616_FEAT_PLN_UI_Map_overlay-layout-inventory.md` — Map overlay layout inventory
- `xTrack/UI_Map/260620_FEAT_PLN_UI_Map_decenter-map-discussion.md` — Decenter map design discussion
- `xTrack/UI_Map/260711_FEAT_PLN_UI_Map_map-offset-dynamic-plan.md` — Map offset dynamic plan
- `xTrack/UI_Map/260712_FEAT_PLN_UI_Map_map-offset-phase2-properties-settings.md` — Map offset phase 2
- `xTrack/UI_Map/260712_FEAT_PLN_UI_Map_map-offset-scroll-fix-clean.md` — Map offset scroll fix
- `xTrack/UI_Map/260903_FEAT_PLN_UI_Map_z-order-fix.md` — Map overlay z-order fix
- `xTrack/UI_Map/260904_FEAT_PLN_UI_Map_marker-filter-map-and-dashboard-close.md` — the marker filter's map consequence and the panel's close, superseded in part 2026-09-28
- `xTrack/UI_Map/260917_FEAT_PLN_UI_Map_inspect-mode.md` — Inspect mode plan (walk source)
- `xTrack/UI_Map/260919_FEAT_PLN_UI_Map_px-to-dp-migration.md` — px to dp: the paint-length inventory, the risk table and the review's findings
- `xTrack/UI_Map/260919_FEAT_PLN_UI_Map_marker-zoom-scale.md` — Marker sizing curve and its tunable keys
- `xTrack/UI_Map/260930_FEAT_PLN_UI_Map_mapscreen-health-monolith.md` — MapScreen remaining-monolith remedy: Bodies, Builders, Chrome, Close
- `xTrack/UI_Map/261007_FEAT_PLN_UI_Map_inspect-live-acquire.md` — Inspect mode: live acquire and pause-to-recentre
- `xTrack/UI_Map/261009_FEAT_PLN_UI_Map_toggle-colour-normalization.md` — the toggle squares' colour normalization
- `xTrack/UI_Map/261010_FEAT_PLN_UI_Map_route-tracks-master-gate.md` — the layer fan as the route-and-tracks master gate
