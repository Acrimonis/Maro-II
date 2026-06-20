---
name: BoatTrace
status: active
created: 2026-06-15 21:43
modified: 2026-06-20 09:00
active_subfeature: none
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

## Implemented

**Data model:** `Track`/`TrackPoint` protobuf with `@ProtoNumber`, `TrackSummary` lightweight index. Relative `timeOffsetSec` per point (~5 bytes saved). `visibleOnMap: Boolean` per-track. `distanceNm` accumulated in-memory, written at finalize.

**Recorder:** Coroutine state machine **OFF ⇄ ON** with geofence auto-detect (Port Salis, Haversine) + manual Start/Stop. Speed gate > 2.5 kn. `isStill()` via `AdaptiveGpsPolicy` position-only algorithm (anchor + time window). Process-death recovery with orphan checkpoint detection. `TrackRecordingService` foreground notification.

**Persistence:** `TrackRepository` — protobuf CRUD on `Dispatchers.IO`, 30s checkpoint saves, index rebuild on corruption. GPX 1.1 export via `FileProvider` share intent.

**Map rendering:** Active track polyline (`track_recording`, 10f, `tracking.color.active`). History tracks: `tracking.render.nb` (0-20), transparency RangeSlider, color gradient `pastFrom→pastTo` with linear RGB interpolation. `pinnedFrom/To` infrastructure only. Incremental overlay diff (`renderedTrackIds` set). First history track thicker (8f vs 6f). Track polylines tagged `track_` prefix survive CoastlineMapView cleanup.

**Layer toggle:** `TrackLayerIcon` in FanLayout (index 0). Controlled by `tracking.tracksVisible` (default true).

**TrackViewModel:** `StateFlow<TrackRecorderUiState>` bridge. LRU detail cache (`LinkedHashMap`, max 30). Track list sorted by `startTimeMs` desc. Auto-naming `yyyy-MM-dd HH:mm`.

**UI:** `TrackStatusIcon` — 3 states (OFF dimmed, ON+moving green+red dot, ON+idle blue+blue dot), click toggles recording. `TrackDrawerOverlay` — right panel (75% width), hamburger always visible (36dp), unified ON/OFF switch + Track List. `TrackHistoryOverlay` — LazyColumn with inline editing, visibility toggle, swipe-to-delete with snackbar undo, GPX share. `LiveTrackCard` at position 0 during recording with pulsing border.

**Settings:** Unified Tracking section in General → Display → Layers card; 3 expandable subsections (Number of tracks, Opacity RangeSlider, Colors). Canvas HSV color pickers (Active single, Past from→to, Pinned from→to). Opacity renamed from "Transparency" — 0=invisible, 100=opaque. Newest track gets `trackingOpacityNewest`, oldest gets `trackingOpacityOldest`. Past/pinned colors + opacity fields persisted to SharedPreferences (6 fields previously in-memory only).

**Stop detection (`AdaptiveGpsPolicy`):** Position-only algorithm. Settings: `stopDetectionEnabled`, `stopDetectionTimeSec` (15-60, default 45), `stopDetectionDistanceM` (10-30, default 15), `stopDetectionDelayGps`. GPS dormant interval = `stopDetectionTimeSec * gpsDormantPct / 100` (80%, enforced <100). Old settings fully removed.

**Settings fix (2026-06-20):** Added persistence for 6 tracking fields (`trackingColorPastFrom/To`, `trackingOpacityNewest/Oldest`, `trackingColorPinnedFrom/To`) — were StateFlow-only, reverted to BuildConfig defaults on restart. Renamed `trackingTransparencyFrom/To` → `trackingOpacityNewest/Oldest` with clarified semantics. Fixed From/To swap in interpolation (was: newest→To, oldest→From). Removed duplicate "Number of tracks" inner label. Added explanatory comments in Settings data class and MapScreen UI.

**Build:** ✅ `assembleDebug` passes
