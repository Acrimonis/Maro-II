name: BoatTrace
status: active
created: 2026-06-15 21:43
modified: 2026-06-21 08:45
active_subfeature: gps-background
---

# Feature: BoatTrace

**Description:**
Trace the boat's movement (position, speed) during active navigation. One trace = one 'track' (Port Salis → Port Salis). Point capture suspends when stationary via `isStill()` gate, but the recording state stays ON. Tracks persisted as protobuf binary and recallable — their polylines display on the map overlay.

## Subfeatures

### design  [x]
#### Docs
- `plans/boat-trace-design-discussion.md` — final design and implementation plan

### data-model  [x]

### recorder  [x]

#### Rules
- Track points recorded only when speed > 2.5 kn (matching Navigation cap arrow threshold)
- OFF→ON transition: geofence exit (inside→outside, 10s debounce) or manual Start in drawer
- Internal storage: protobuf binary via kotlinx-serialization-protobuf, not JSON
- Track recording runs on Dispatchers.Default, file I/O on Dispatchers.IO
- 30s periodic checkpoint save during ON state for crash recovery
- In demo mode, geofence check is bypassed — triggers on speed > 2.5 kn for 10s (manual also available)
- State machine is single-threaded (single coroutine collector) — no race between manual & auto triggers
- Track stats (`distanceNm`, `maxSpeedKn`, `avgSpeedKn`) accumulated continuously in memory during recording, written to protobuf at finalize

### ui-integration  [x]

### verification  [ ]

#### Todos
- [ ] Build + deploy to device
- [ ] E2E: Enable tacking -> leave Port Salis -> verify auto-start + real-time map trace
- [ ] E2E: Stop sailing -> verify pause -> sail again -> verify resume
- [ ] E2E: Return to Port Salis + stop -> verify auto-finalize + appears in tack history
- [ ] E2E: Open tack history -> tap tack -> verify trace renders on map
- [ ] E2E: Swipe-to-delete tack -> confirm dialog -> verify removed from list + file system
- [ ] E2E: Manual Start/Stop from tack drawer -> verify state matches auto-detection
- [ ] E2E: Export GPX -> copy to computer -> open in QGIS/Google Earth -> verify track/speed/course
- [ ] E2E: Verify settings persistence of tack fields across app restart

#### Rules
- Keep design plan in sync with implementation decisions discovered during development
- Do not switch to Code mode unless I give the explicit go-ahead. When code is done, switch back to Architect mode to discuss.

#### Key Files

#### Docs
- `plans/boat-trace-design-discussion.md` — full design & implementation plan (data model, state machine, UI components, design tokens)
- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_TrackList_Design.md` — track list UI design (swipe-to-delete, inline snackbar, animation spec)

### track-list  [ ]

#### Todos
- [ ] Review and refine track card layout per design spec
- [ ] Verify swipe-to-delete, inline snackbar, undo animations
- [ ] Verify inline editing (auto-focus, field-switch commit, back-to-revert)
- [ ] Verify human-readable formatting (comma decimal, durations)
- [ ] Verify compact padding and flush-left stats grid
- [ ] E2E: Create test tracks, verify all card fields display correctly

#### Rules
- Track list UI must follow FEAT_PLN_BoatTrace_TrackList_Design.md spec
- Styling must match Settings overlay patterns (AppConfig tokens)

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt`

#### Docs
- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_TrackList_Design.md` — track list UI design and 26 requirements

### tracking-status-n-triggers  [x]

#### Rules
- `isMoving = !policy.isStill()` where `policy.isStill()` reads `AdaptiveGpsPolicy.lastMode == IDLE`
- Tracking triggers: **manual** (Start/Stop from drawer) **OR geofence exit** (inside→outside).
- `isStill()` is UI-only — does not auto-start/stop.
- ON state persists through stationary periods — only point capture suspends
- `isStill()` uses the 30s window via `AdaptiveGpsPolicy` — 30s stillness before switching to IDLE
- **No auto-start on movement alone** — even when geofence disabled, manual start required.

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/TrackStatusIcon.kt`
- `app/src/main/java/ykws/android/maro/ui/map/TrackDrawerOverlay.kt`
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`
- `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt`

### adaptive-isstill  [x]

#### Rules
- Defaults: stopDetectionTimeSec=45 (range 10-90), stopDetectionDistanceM=15 (range 10-30)
- `isStill()` returns `true` iff boat hasn't moved > adaptiveDistance in the last adaptiveTime
- GPS dormant interval = `stopDetectionTimeSec * gpsDormantPct / 100`
- Must enforce `gpsDormantPct < 100` at config validation
- Position-only algorithm — no speed input to onFix()
- Re-anchor on displacement >= adaptiveDistance to allow subsequent stillness detection
- Old settings are **completely removed** — no migration, no fallback

#### Key Files
- `app/src/main/java/ykws/android/maro/data/location/AdaptiveGpsPolicy.kt`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- `app/src/main/assets/maro.properties`
- `app/build.gradle.kts`

#### Docs
- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_adaptive-isstill.md` — full plan

### hamburger-btn  [x]

#### Rules
- Hamburger always visible in the top-left icon row (first position)
- Hamburger styled as 64dp round Button with `ButtonColors.bg` and `ButtonColors.icon`
- Drawer title: "Maro II" (generic, extendable for future sections)
- Section header: "TRACK RECORDING" with blue `#1565C0`, uppercase
- Scrim covers right 25%, clickable to dismiss
- Drawer panel takes 75% of screen width
- AnimatedVisibility is the sole gate (no outer if-guard) for real-time state

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

#### Docs
- `plans/boat-trace-design-discussion.md` — §5b (Hamburger + Drawer spec, lines 284-361)

### tweaks  [x]
#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/TrackStatusIcon.kt`
- `app/src/main/java/ykws/android/maro/ui/map/TrackDrawerOverlay.kt`

### render-tracks  [x]
#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt`
- `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt`

### mtrack-setting-opacity  [x]

#### Todos
- [ ] Rename data class fields: `trackingOpacityNewest` → `trackingTransparencyNewest`, `trackingOpacityOldest` → `trackingTransparencyOldest`
- [ ] Rename SharedPreferences keys: `tracking_opacity_newest` → `tracking_transparency_newest`, `tracking_opacity_oldest` → `tracking_transparency_oldest` (old keys ignored — clean break)
- [ ] Update BuildConfig defaults: `TRACKING_TRANSPARENCY_FROM` 100→20, `TRACKING_TRANSPARENCY_TO` 30→80
- [ ] Invert rendering math in MapScreen.kt: `alpha = (100 - transparency) / 100f`
- [ ] Update settings UI label from "Opacity" to "Transparency"
- [ ] Update description text: "0% = opaque, 100% = invisible. Higher % = more transparent."
- [ ] Update value display format: "Newest %d%% – Oldest %d%%" (unchanged)
- [ ] Ensure settings-page-rules compliance: wrap in SettingsSliderGroup, use font tokens (R4), replace 0.5dp hairline dividers with Spacer(8.dp) (R2)
- [ ] Build: assembleDebug

#### Rules
- **Semantics**: 0% = fully opaque (visible), 100% = fully transparent (invisible)
- **Gradient direction**: newest track = low transparency (more visible), oldest track = high transparency (more faded)
- **Constraint**: newest ≤ oldest (left thumb ≤ right thumb). Reject or silently clamp if user drags past.
- **Defaults**: newest 20%, oldest 80%
- **Clean break**: no migration from old `tracking_opacity_*` keys — new keys with transparency semantics
- **Rendering**: `alpha = (100 - transparency) / 100f`, clamped 0..1
- **UI**: Use SettingsSliderGroup wrapper, `uiSettingsLabel` / `uiSettingsDescription` / `uiSettingsValue` tokens, 8dp spacers per settings-page-rules R1-R4

#### Key Files
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/build.gradle.kts`

### gps-background  [x]

#### Todos
- [ ] Update TrackRecordingService: intent-driven notification updates, two notification modes (Ready / Recording with live stats)
- [ ] Start service from MainActivity.onCreate, stop on double-back exit
- [ ] Send periodic notification updates from MapScreen (5s throttle) with recording state, speed, elapsed, distance
- [ ] Demo mode: show "(Demo)" suffix, skip GPS dependency
- [ ] Build: assembleDebug

#### Rules
- Service always runs while app is open — not just during recording
- Non-recording notification: "Maro II — Ready" (IMPORTANCE_LOW, silent, ongoing)
- Recording notification: "Maro II — Recording • {speed} kn • {elapsed} • {distance} nm"
- Demo mode (!gpsMode): add "(Demo)" to ready notification
- Updates via startService(intent) with ACTION_UPDATE + extras — throttled to 5s
- START_STICKY: service restarts if killed, rebuilds ready notification
- Stop service only on explicit app exit (double-back)

#### Key Files
- `app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt`
- `app/src/main/java/ykws/android/maro/MainActivity.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

#### Docs
- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_gps-background.md`

### gps-line-acquisition  [x]

#### Todos
- [ ] Remove passive listener from GpsLocationSource (fix 1 — ordering)
- [ ] Add implied-speed spike gate to TrackRecorder.addPoint() (fix 2 — outliers)
- [ ] Build: assembleDebug

#### Rules
- MAX_REALISTIC_SPEED_KN = 50.0 — hard cap; any fix implying faster travel is discarded
- Spike rejection uses fix.timestampEpochMs (GPS epoch), not System wall clock
- AdaptiveGpsPolicy still receives every fix (including spikes) for stillness detection
- lastValidPoint* tracks last recorded point, separate from lastPointLat/Lon (Haversine distance accumulators)

#### Key Files
- `app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt`
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`

#### Docs
- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_gps-line-acquisition.md`

## Todos
- [ ] E2E verification on device (build + deploy, run all test scenarios)
- [ ] Track list UI polish per design spec (FEAT_PLN_BoatTrace_TrackList_Design.md)
- [ ] Verify hamburger appears in top-left, opens TrackDrawer with controls
- [ ] Verify TrackStatusIcon visually matches GPS and EarthWater icons
- [ ] Add tooltip/label to TrackStatusIcon explaining current state
- [ ] Review drawer layout for polish

## Rules
- Feature-scoped plans go in `xTrack/[Feature]/FEAT_PLN_[Feature]_[topic].md`, NOT in `plans/`. The `plans/` directory is for cross-cutting or legacy plans only.
- Track points only recorded while speed > 2.5 kn (drifting threshold from Navigation feature)
- OFF→ON transitions: **geofence exit** (inside→outside, 10s debounce) or **manual** (Start in drawer). No speed-based auto-start.
- Internal storage: **Protobuf binary** via `kotlinx-serialization-protobuf` — not JSON
- Export format: **GPX 1.1** for compatibility with QGIS, Google Earth, OsmAnd, etc.
- Track metadata: name (auto-generated `yyyy-MM-dd HH:mm`), comment, start/end time, fastest speed, track color, distance
- Recording lifecycle: ON state persists through stationary — only point capture suspends via `isStill()` gate
- Auto-start on **geofence exit** only (Port Salis origin + configurable radius)
- `visibleOnMap: Boolean` on Track data class — per-track visibility toggle
- Manual Start/Stop in track drawer (hamburger icon)
- `timeOffsetSec` per point instead of absolute timestamp — saves ~5 bytes/pt
- Recording on Dispatchers.Default, file I/O on Dispatchers.IO
- 30s periodic checkpoint save during ON state for crash recovery
- Track stats (`distanceNm`, `maxSpeedKn`, `avgSpeedKn`) accumulated in-memory, written at finalize
- Index rebuilt by scanning `.bin` files on startup if missing or corrupted
- Swipe-to-delete on TrackHistoryOverlay with snackbar undo

## Key Files

## Docs
- `plans/boat-trace-design-discussion.md` — final design and implementation plan
- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_TrackList_Design.md` — track list UI requirements, animation design, component architecture
- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_render-tracks.md` — render-tracks implementation plan
- `xTrack/BoatTrace/FEAT_DOC_BoatTrace_decisions.md` — comprehensive functional/architectural decisions record (7 categories, 40+ decisions)
- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_gps-line-acquisition.md` — GPS point acquisition: outlier rejection (speed gate) + passive listener removal (ordering fix)
- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_gps-background.md` — Persistent foreground service: always-on notification with live recording stats, demo-aware

## Implemented

**Data model:** `Track`/`TrackPoint` protobuf with `@ProtoNumber`, `TrackSummary` lightweight index. Relative `timeOffsetSec` per point (~5 bytes saved). `visibleOnMap: Boolean` per-track. `distanceNm` accumulated in-memory, written at finalize.

**Recorder:** Coroutine state machine **OFF ⇄ ON** with geofence auto-detect (Port Salis, Haversine) + manual Start/Stop. Speed gate > 2.5 kn. `isStill()` via `AdaptiveGpsPolicy` position-only algorithm (anchor + time window). Process-death recovery with orphan checkpoint detection. `TrackRecordingService` foreground notification.

**Persistence:** `TrackRepository` — protobuf CRUD on `Dispatchers.IO`, 30s checkpoint saves, index rebuild on corruption. GPX 1.1 export via `FileProvider` share intent.

**Map rendering:** Active track polyline (`track_recording`, 10f, `tracking.color.active`). History tracks: `tracking.render.nb` (0-20), transparency RangeSlider, color gradient `pastFrom→pastTo` with linear RGB interpolation. `pinnedFrom/To` infrastructure only. Incremental overlay diff (`renderedTrackIds` set). First history track thicker (8f vs 6f). Track polylines tagged `track_` prefix survive CoastlineMapView cleanup.

**Layer toggle:** `TrackLayerIcon` in FanLayout (index 0). Controlled by `tracking.tracksVisible` (default true).

**TrackViewModel:** `StateFlow<TrackRecorderUiState>` bridge. LRU detail cache (`LinkedHashMap`, max 30). Track list sorted by `startTimeMs` desc. Auto-naming `yyyy-MM-dd HH:mm`.

**UI:** `TrackStatusIcon` — 3 states (OFF dimmed, ON+moving green+red dot, ON+idle blue+blue dot), click toggles recording. `TrackDrawerOverlay` — right panel (75% width), hamburger always visible (36dp), unified ON/OFF switch + Track List. `TrackHistoryOverlay` — LazyColumn with inline editing, visibility toggle, swipe-to-delete with snackbar undo, GPX share. `LiveTrackCard` at position 0 during recording with pulsing border.

**Settings:** Unified Tracking section in General → Display → Layers card; 3 expandable subsections (Number of tracks, Transparency RangeSlider, Colors). Canvas HSV color pickers (Active single, Past from→to, Pinned from→to). Transparency semantics: 0%=opaque, 100%=invisible. Newest track gets `trackingTransparencyNewest` (default 20), oldest gets `trackingTransparencyOldest` (default 80). Left thumb → newest, right thumb → oldest. Past/pinned colors + transparency fields persisted to SharedPreferences.

**Stop detection (`AdaptiveGpsPolicy`):** Position-only algorithm. Settings: `stopDetectionEnabled`, `stopDetectionTimeSec` (15-60, default 45), `stopDetectionDistanceM` (10-30, default 15), `stopDetectionDelayGps`. GPS dormant interval = `stopDetectionTimeSec * gpsDormantPct / 100` (80%, enforced <100). Old settings fully removed.

**Settings fix (2026-06-20):** Added persistence for 6 tracking fields (`trackingColorPastFrom/To`, `trackingOpacityNewest/Oldest`, `trackingColorPinnedFrom/To`) — were StateFlow-only, reverted to BuildConfig defaults on restart. Renamed `trackingTransparencyFrom/To` → `trackingOpacityNewest/Oldest` with clarified semantics. Fixed From/To swap in interpolation (was: newest→To, oldest→From). Removed duplicate "Number of tracks" inner label. Added explanatory comments in Settings data class and MapScreen UI.

**mtrack-setting-opacity (2026-06-20):** Renamed `trackingOpacityNewest/Oldest` → `trackingTransparencyNewest/Oldest` with inverted semantics (0%=opaque, 100%=invisible). New SharedPreferences keys `tracking_transparency_newest/oldest` — clean break, no migration from old opacity keys. BuildConfig defaults: `TRACKING_TRANSPARENCY_FROM` 100→20, `_TO` 30→80. Rendering: `alpha = (100 - transparency) / 100f`. UI label "Opacity" → "Transparency", description updated. Replaced 0.5dp hairline divider with 8dp spacer per settings-page-rules R2.

**gps-line-acquisition (2026-06-20):** Removed `PASSIVE_PROVIDER` listener from `GpsLocationSource` — dual listeners racing on one `callbackFlow` caused zigzag artifacts on the active track polyline. Added implied-speed spike gate to `TrackRecorder.addPoint()`: fixes implying >50 kn travel from last valid recorded point are discarded as GPS multipath outliers (uses `fix.timestampEpochMs`, not wall clock). `AdaptiveGpsPolicy` still sees all fixes for stillness detection.

**gps-background (2026-06-20):** Rewrote `TrackRecordingService` as always-on foreground service — started in `MainActivity.onCreate`, stopped on double-back exit from `MapScreen`. Intent-driven notification updates (5s throttle) with live recording stats. Added `currentSpeedKn` to `TrackRecorderUiState`. Two notification modes: "Maro II — Ready" (or "Ready (Demo)") and "Maro II — Recording • {speed} kn • {elapsed} • {distance} nm" (or "Recording (Demo)"). Demo suffix on both states.

**Build:** ✅ `assembleDebug` passes
