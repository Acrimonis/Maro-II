---
name: MapDisplay
status: active
created: 2026-06-07 00:00
modified: 2026-06-08 21:36
active_subfeature: none
---

**Description:** Map display layer management — depth layer, color depth layer, and orientation-aware rendering.

## Subfeatures

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

## Todos

## Rules

## Key Files

## Docs
