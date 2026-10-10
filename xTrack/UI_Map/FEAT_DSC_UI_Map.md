---
name: UI_Map
status: active
created: 2026-06-07 00:00
modified: 2026-10-10 12:50
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

Unified drawer framework (`DrawerSlot` + `OverlayLayer`) hosting all 7 transient surfaces. `OverlayLayer`'s
read-only params are grouped into six `@Immutable` bundles in `OverlayLayerParams.kt` (60 params total: 6 bundles +
explicit values/ViewModels + inline callbacks).

#### Todos
- [ ] On-device verify all 7 surfaces open/close with animations and shadows

#### Rules
- Layer 0 permanent, Layer 1 transient; drawers are pure content (no AnimatedVisibility/scrim/shadow); new drawers follow docs/ui-drawer-guidelines.md §5

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DrawerSlot.kt`, `OverlayLayer.kt`, `OverlayLayerParams.kt`, `WizardDrawer.kt`, `markers/wizard/`, `MapScreen.kt`

## Implemented

- **the menu chevron gate adopts its list's order (2026-10-10, `feature/listery`)** — the hamburger menu's three chevron doors no longer walk the stored collection's own order: each walks the corresponding list's own sort, while membership stays the map filter, which is what makes a map-filter write close a menu-opened card. `sortMarkers` and `sortSummaries` were lifted out of their view models to top-level pure homes read by both the list and the gate, `menuTrackIdsOf`/`menuRouteIdsOf` take the list sort state and sort through them, and `MapScreen` derives a menu-ordered marker id list for the chevron's walk world and for `firstMarkerId`. A list sort edit closes a menu-opened card of either kind — the close rule lives in the two guards (`scopeClosed`, `trackScopeClosed`), whose map-filter arm is unchanged. The map drawing is untouched: `_mapMarkers`, the stored-track ranking, the render cap and the inspect candidates keep their own order. The marker pin and icon tap suppressors left `MarkerOverlay.kt` in the same pass, so a marker tap now reaches osmdroid's own handler and may open its default info bubble — recorded in the Markers feature's `marker-click-remove` entry. `apk-build.bat` SUCCESSFUL and the scoped list, card-walk, selection-policy and marker suites green.

- **the layer fan as the route-and-tracks master gate (2026-10-10, `feature/rte-n-trck-list`)** — the fan's route-and-tracks child is now the family's **primary display gate**: a new `routeTracksVisible` is what child 1 writes — it no longer writes `tracksVisible`, which stays the TRACKS header eye's own — the render reads master ∧ kind-eye through the new pure `kindLayerOn`, and the pair is applied at the render pass with `routeTracksVisible` in its rebuild keys, at the inspect candidates and at the list preview. The open dashboard's own item is the gates' **one exception**: `storedTrackSelection` takes the open item's id and answers it alone when its half is gated off, and the marker overlay composes for its open card's marker while the layer is hidden — while the filter is never escaped, so the 2026-09-28 rule stands. The two header eyes are drawn dimmed yet stay tappable. Supersedes the split plan's D2. `apk-build.bat` SUCCESSFUL and the scoped `ui.map` suite green at 41 suites and 419 tests → `xTrack/UI_Map/261010_FEAT_PLN_UI_Map_route-tracks-master-gate.md`

- **map z-order** — deterministic overlay order (tile→base→tracks→markers) via `OverlayZOrder.reorder(mv)`
- **marker highlight dual-outline** — dark under-stroke behind gold highlight on all 5 marker geometry builders → `xTrack/Markers/260711_FEAT_PLN_Markers_marker-hilite-dual-outline-plan.md`
- **layer refresh** — single Box parent + `Modifier.align()` overlay; orientation-aware padding; `configChanges` manifest fix
- **zone proximity auto-reveal** — hybrid reveal (distance OR time-to-band) + auto-hide; pure `zone300Decision()` + tests
- **speed in demo** — pan-velocity-derived demo speed in SpeedCard (150ms cadence)
- **boat-center** — boat marker decoupled Image + cap-arrow Canvas; shifted down half-height
- **marker filter + dashboard close** — marker filter drives map overlay; panel auto-closes; list-context stacking removed. *Superseded in part 2026-09-28:* the map draws its own filter's set with no reveal, highlighted or pinned escape, and the map-opened panel now stands on a filter write — the rule lives in `xTrack/Ui_General/FEAT_DSC_Ui_General.md` `## Rules`
- **inspect mode** — drag-to-select by proximity from the marker point: the ⊕ square gated on having something inspectable, one radius derivation feeding both the ring and the pick gate, the layer-visible map-filtered candidate set warmed at arming, a candidate line the mode owns, a quiet-clock pick with one pick per gesture, and a frozen distance ladder walked by one inspect cursor that crosses between marker and track cards; the pick, the camera and the look are the canonical selection path, so the mode is an entry point rather than a second renderer → `xTrack/UI_Map/260917_FEAT_PLN_UI_Map_inspect-mode.md`
- **marker zoom scale configurable** — the growth exponent left the code for `map.marker.size.zoomExponent` (`maro.properties` → `AppConfig.mapMarkerSizeZoomExponent`, clamped 0–1) and moved to 0.35 for the settled 11–20 range, so the centre sprite stops outgrowing the screen offshore at level 20 (388 dp → 223 dp) while the mid levels barely move; the deleted constant's three readers — the sprite, the wizard's crosshair and the cap arrow — now share the one setting. The base pair followed the same day as `map.marker.size.boatBaseDp` (36.8) and `map.marker.size.dotBaseDp` (9.2), clamped 1–128, a 15 % raise on 32 / 8 as a first device test, with `BOAT_BASE_DP` / `DOT_BASE_DP` deleted and the sprite's base read from the pair; the reference zoom, the coast-shrink pair and the arrow's clamps stay code, and the marker-sizing doc carries the real curve with its tunables pointing at the keys → `xTrack/UI_Map/260919_FEAT_PLN_UI_Map_marker-zoom-scale.md`

- **map paint lengths in dp** — the map's strokes, dashes, offsets and paddings left device pixels for dp, so a width tuned on one phone scales with the screen instead of being fixed in pixels: one pure density-explicit `dpToPx` helper beside the alpha sibling, the caller converting while the renderer's `…Px` parameters stayed put, one density accessor replacing two named forms plus five inlines, the three settings widths renamed `…widthDp` atomically with their parse, float fields, prefs keys, both locales, their row grid and every paint site that reads them, the track table and casing converted with the chevron knee moving alongside the widths it compares against, the dashes converted by stroke — the direction line's left a ratio of its width, the isobath's and the GAP bridge's by value because their strokes vary, and the marker circle's dash repaired after the review caught it defaulting to density 1 and painting a third of its intended dash — the isobaths with their floor re-expressed in dp, and the markers with their two adds; the review that followed found that one High, one Medium (the row grid) and several Lows, and the same pass settled the ramp-families, tap-flash-alpha and width-pin reds with `*.properties` as the source of truth and the code's defaults following it → `xTrack/UI_Map/260919_FEAT_PLN_UI_Map_px-to-dp-migration.md`

- **whereami tap zone + debug-ray clearing (2026-09-18, `feature/where-are-zone-trans`)** — the boat's touch box became a fixed radial circle on the sprite's visual centre with a gold pulse beneath it, the rays clearing on the drawer-state funnel and on the rays toggle, `MarkerMatcher`'s global debugger and its three writers retired for a per-call one, and `marker.debug.rays.enabled` dropped for the setting alone. This feature's half is the two map files the source plan names, whose owners' pointers it left to their next bake — this one → `xTrack/Markers/260918_FEAT_PLN_Markers_whereami-tap-zone-and-ray-clear.md`

- **mapscreen-health remedy (2026-10-01, `feature/mapscreen-health`)** — the remaining monolith decomposed in four self-validated migration phases with zero behavior change: Bodies hoisted the two eligible `OverlayLayer` callback bodies, Builders moved five data constructions to `MapOverlayData.kt`, Chrome folded the eight written chrome values into an `@Stable MapScreenChrome` with a `Saver` serialising `selectedTab` alone, and Close re-measured every anchor. `fun MapScreen` 3,000 → 2,967 (505–3471); `apk-build.bat` and the scoped `ui.map` + `config` unit run green each phase → `xTrack/UI_Map/260930_FEAT_PLN_UI_Map_mapscreen-health-monolith.md`

- **inspect mode — live acquire (2026-10-07)** — the mode's trigger was replaced: while armed the nearest inspectable item is re-ranked on every sweep and its dashboard opens at once, with no lift and no dwell, a change of nearest swapping the panel and the anchor losing its target closing it while the mode stays armed. The quiet is now the pause: its expiry recentres the camera on the acquired item through the one framer and freezes the ladder the walk steps, and a drag after that recentre resets the card, the ladder and the captured frame. The card's own close is the mode's exit, the armed map-marker tap went with `MarkerOverlay`'s proximity receiver, and the centre hold is the armed flag alone. New pure rules `inspectAcquireAction` / `inspectUserMoveAction` / `inspectActivityAfterMotion` → `xTrack/UI_Map/261007_FEAT_PLN_UI_Map_inspect-live-acquire.md`
- **the map dash family on one frame (2026-10-07)** — the selected-item slot's churn was taken out: the marker card, the track card and the route panel now share one header padding (12 dp horizontal, one vertical value), a scrollable body, the body-only dissolve, square corners in both orientations, and one walk-row frame at 8/8/8 in the row's own `padding(horizontal = 12.dp, vertical = 8.dp)`; the hand-rolled pills went for the route footer's own tier-1 `ConfirmActionButton` pair (Previous `SECONDARY`, Next `PRIMARY`), which is also what stops a cross-type swap lifting the row. The panel trade is damped by `map.inspect.switchMarginPct`, a selected path wears a gold casing on its own `path.line.casing.selected` colour and `.width`, and the slot's incoming card is pre-sized at the outgoing one's height so a route↔marker swap settles once instead of stepping through the floor — findings of the plan's §8 → `xTrack/UI_Map/261007_FEAT_PLN_UI_Map_inspect-live-acquire.md`

## Todos
- [ ] Inspect live-acquire device pass — a drag whose panel swaps as the nearest changes, the pause's recentre, a post-recentre drag resetting everything, the toggle / Back / boat-icon exits, the anchor staying off while armed, a marker↔route swap settling without a jump, and the walk row holding one height across the three panels → `xTrack/UI_Map/261007_FEAT_PLN_UI_Map_inspect-live-acquire.md`
- [ ] Device pass against the plan's two tables — the sprite at levels 19 and 20 offshore and inshore, and the 15 % raise on the base pair at each level → `xTrack/UI_Map/260919_FEAT_PLN_UI_Map_marker-zoom-scale.md`
- [ ] Second-density check for the dp pass — an emulator at 1× or 2× showing the strokes scale, since on the 3× tuning phone this change is invisible by design → `xTrack/UI_Map/260919_FEAT_PLN_UI_Map_px-to-dp-migration.md`
- [ ] Open Medium from the dp review — the width rows' half-dp grid cannot reach the coastline's shipped 3.333 dp default, so a dragged row loses the 10 px look → `xTrack/UI_Map/260919_FEAT_PLN_UI_Map_px-to-dp-migration.md`

## Docs
- `xTrack/UI_Map/260614_FEAT_PLN_UI_Map_boat-marker-offset-discussion.md` — Boat marker offset discussion
- `xTrack/UI_Map/260616_FEAT_PLN_UI_Map_right-edge-gap-asymmetry.md` — Right edge controls gap asymmetry
- `xTrack/UI_Map/260616_FEAT_PLN_UI_Map_overlay-layout-inventory.md` — Map overlay layout inventory
- `xTrack/UI_Map/260616_FEAT_PLN_UI_Map_overlay-layout-rationalization.md` — Map overlay layout rationalization
- `xTrack/UI_Map/260612_FEAT_PLN_UI_Map_icon-rendering-overhaul.md` — Icon rendering overhaul
- `xTrack/UI_Map/260620_FEAT_PLN_UI_Map_decenter-map-discussion.md` — Decenter map design discussion
- `xTrack/UI_Map/260711_FEAT_PLN_UI_Map_map-offset-dynamic-plan.md` — Map offset dynamic plan
- `xTrack/UI_Map/260712_FEAT_PLN_UI_Map_map-offset-phase2-properties-settings.md` — Map offset phase 2
- `xTrack/UI_Map/260712_FEAT_PLN_UI_Map_map-offset-scroll-fix-clean.md` — Map offset scroll fix
- `xTrack/UI_Map/260917_FEAT_PLN_UI_Map_inspect-mode.md` — Inspect mode plan (walk source)
- `xTrack/UI_Map/260919_FEAT_PLN_UI_Map_px-to-dp-migration.md` — px to dp: the paint-length inventory, the risk table and the review's findings
- `xTrack/UI_Map/260919_FEAT_PLN_UI_Map_marker-zoom-scale.md` — Marker sizing curve and its tunable keys
- `xTrack/UI_Map/260904_FEAT_PLN_UI_Map_marker-filter-map-and-dashboard-close.md` — the marker filter's map consequence and the panel's close, superseded in part 2026-09-28 by the map-opened exception
- `xTrack/UI_Map/260930_FEAT_PLN_UI_Map_mapscreen-health-monolith.md` — MapScreen remaining-monolith remedy (implemented): four migration phases — Bodies, Builders, Chrome, Close — with the ladder staying in MapScreen
- `xTrack/UI_Map/261007_FEAT_PLN_UI_Map_inspect-live-acquire.md` — Inspect mode: live acquire and pause-to-recentre (implemented)

