name: BoatTrace
status: active
created: 2026-06-15 21:43
modified: 2026-07-12 18:19
active_subfeature: merge-tracks
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

### track-list-render-indicator  [x]

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt`

#### Docs
- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_track-list-render-indicator.md` — design plan

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
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt`
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
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt`

### render-tracks  [x]
#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt`
- `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt`

### mtrack-setting-opacity  [x]

#### Rules
- **Semantics**: 0% = opaque, 100% = transparent
- **Gradient direction**: newest track = low transparency (visible), oldest track = high transparency (faded)
- **Constraint**: newest ≤ oldest. Clamp if user drags past.
- **Defaults**: newest 20%, oldest 80%
- **Clean break**: no migration from old `tracking_opacity_*` keys
- **Rendering**: `alpha = (100 - transparency) / 100f`, clamped 0..1

#### Key Files
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/build.gradle.kts`

### gps-background  [x]

#### Rules
- Service always runs while app is open — not just during recording
- Non-recording notification: "Maro II — Ready" (IMPORTANCE_LOW, silent, ongoing)
- Recording notification: "Maro II — Recording • {speed} kn • {elapsed} • {distance} nm"
- Demo mode (!gpsMode): add "(Demo)" to both notification states
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

#### Rules
- Spike rejection uses fix.timestampEpochMs (GPS epoch), not System wall clock
- AdaptiveGpsPolicy still receives every fix (including spikes) for stillness detection
- lastValidPoint* tracks last recorded point, separate from lastPointLat/Lon (Haversine distance accumulators)
- Original 50kn hard cap replaced by spike-rejection-v2 (4-gate algorithm)

#### Key Files
- `app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt`
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`

#### Docs
- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_gps-line-acquisition.md`

### track now demo  [x]

### pinned-tracks  [x]

**Purpose:** Replace the per-track visibility eye-icon with a pin-icon in the track list. Pinned tracks always render on map with their own color gradient + transparency. History (unpinned) tracks still render via `trackingRenderNb` count slider.

#### Todos
- [ ] Add `pinned: Boolean` to Track protobuf (default false) — replaces `visibleOnMap`
- [ ] Add `TrackRepository.setPinned(trackId, pinned)` for protobuf persistence
- [ ] Add `trackingTransparencyPinnedNewest/Oldest` to AppSettings, BuildConfig, SharedPreferences (defaults 0%→20%)
- [ ] Replace eye-icon with pin-icon toggle in TrackHistoryOverlay track card; persist on tap
- [ ] Add pinned transparency RangeSlider to settings UI (alongside existing history transparency)
- [ ] Relabel settings: "Number of tracks" → "Number of history tracks", "Transparency" → "History transparency"
- [ ] Update MapScreen rendering: split pinned vs history, compute alpha separately per group
- [ ] Z-order: active > pinned > past (pinned polylines between active and history)
- [ ] Update TrackViewModel: expose `pinnedTracks` / `historyTracks` filtered lists
- [ ] Build + deploy + E2E verify

#### Rules
- **Pin replaces eye-icon** — pin is the sole per-track map-visibility control
- **`trackingRenderNb` applies to history only** — pinned tracks always render regardless of count
- Pinned transparency defaults: **0%→20%** (more opaque than history's 20%→80%)
- Same alpha formula for all: `alpha = (100 - transparency) / 100f`
- Z-order: active > pinned > past
- Color infrastructure (`trackingColorPinnedFrom→To`) already exists — only transparency keys are new

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt` — eye→pin icon swap
- `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt` — pinned/history split
- `app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt` — setPinned()
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — rendering split + settings UI
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — new transparency keys
- `app/build.gradle.kts` — BuildConfig defaults (TRACKING_TRANSPARENCY_PINNED_FROM/TO)

#### Docs
- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_pinned-tracks.md` — full design & implementation plan

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

### idle-time-tracking  [x]

#### Key Files
- `app/src/main/java/ykws/android/maro/data/track/Track.kt`
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`
- `app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt`
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt`

#### Docs
- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_idle-time-always-zero.md` — root cause + fix plan + implementation record

### merge-tracks  [x]

#### Docs
- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_merge-tracks.md` — discussion plan: merge tracks vs resume track

### populate-track-info  [x]

## Docs
- `plans/boat-trace-design-discussion.md` — final design and implementation plan
- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_TrackList_Design.md` — track list UI requirements, animation design, component architecture
- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_render-tracks.md` — render-tracks implementation plan
- `xTrack/BoatTrace/FEAT_DOC_BoatTrace_decisions.md` — comprehensive functional/architectural decisions record (7 categories, 40+ decisions)
- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_gps-line-acquisition.md` — GPS point acquisition: outlier rejection (speed gate) + passive listener removal (ordering fix)
- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_gps-background.md` — Persistent foreground service: always-on notification with live recording stats, demo-aware
- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_spike-rejection-v2.md` — Spike rejection v2: 4-gate algorithm design plan
- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_adaptive-isstill.md` — adaptive GPS stillness detection plan
- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_pinned-tracks.md` — Pinned tracks: replace eye-icon with pin-icon, separate transparency, z-order

## Implemented

**Data model:** `Track`/`TrackPoint` protobuf with `@ProtoNumber`, `TrackSummary` lightweight index. Relative `timeOffsetSec` per point (~5 bytes saved). `visibleOnMap: Boolean` per-track. `distanceNm` accumulated in-memory, written at finalize.

**Recorder:** Coroutine state machine **OFF ⇄ ON** with geofence auto-detect (Port Salis, Haversine) + manual Start/Stop. Speed gate > 2.5 kn. `isStill()` via `AdaptiveGpsPolicy` position-only algorithm (anchor + time window). Process-death recovery with orphan checkpoint detection. `TrackRecordingService` foreground notification.

**Persistence:** `TrackRepository` — protobuf CRUD on `Dispatchers.IO`, 30s checkpoint saves, index rebuild on corruption. GPX 1.1 export via `FileProvider` share intent.

**Map rendering:** Active track polyline (`track_recording`, 10f, `tracking.color.active`). History tracks: `tracking.render.nb` (0-20), transparency RangeSlider, color gradient `pastFrom→pastTo` with linear RGB interpolation. Incremental overlay diff (`renderedTrackIds` set). First history track thicker (8f vs 6f). Track polylines tagged `track_` prefix survive CoastlineMapView cleanup.

**Layer toggle:** `TrackLayerIcon` in FanLayout (index 0). Controlled by `tracking.tracksVisible` (default true).

**TrackViewModel:** `StateFlow<TrackRecorderUiState>` bridge. LRU detail cache (`LinkedHashMap`, max 30). Track list sorted by `startTimeMs` desc. Auto-naming `yyyy-MM-dd HH:mm`.

**UI:** `TrackStatusIcon` — 3 states (OFF dimmed, ON+moving green+red dot, ON+idle blue+blue dot), click toggles recording. `MenuDrawerOverlay` — right panel (75% width), hamburger always visible (36dp), unified ON/OFF switch + Track List. `TrackHistoryOverlay` — LazyColumn with inline editing, visibility toggle, swipe-to-delete with snackbar undo, GPX share. `LiveTrackCard` at position 0 during recording with pulsing border.

**Settings:** Unified Tracking section in General → Display → Layers card; 3 expandable subsections (Number of tracks, Transparency RangeSlider, Colors). Canvas HSV color pickers (Active single, Past from→to, Pinned from→to). Transparency semantics: 0%=opaque, 100%=invisible. Newest track gets `trackingTransparencyNewest` (default 20), oldest gets `trackingTransparencyOldest` (default 80). Past/pinned colors + transparency fields persisted to SharedPreferences.

**Stop detection (`AdaptiveGpsPolicy`):** Position-only algorithm. Settings: `stopDetectionEnabled`, `stopDetectionTimeSec` (15-60, default 45), `stopDetectionDistanceM` (10-30, default 15). GPS dormant interval = `stopDetectionTimeSec * gpsDormantPct / 100` (80%, enforced <100). Old settings fully removed.

**Settings fix (2026-06-20):** Added persistence for 6 tracking fields. Renamed `trackingTransparencyFrom/To` → `trackingOpacityNewest/Oldest` with clarified semantics. Fixed From/To swap in interpolation.

**mtrack-setting-opacity (2026-06-20):** Renamed `trackingOpacityNewest/Oldest` → `trackingTransparencyNewest/Oldest` with inverted semantics (0%=opaque, 100%=invisible). Clean break — no migration. BuildConfig defaults: 20→80. Rendering: `alpha = (100 - transparency) / 100f`.

**gps-line-acquisition (2026-06-20):** Removed `PASSIVE_PROVIDER` listener — dual listeners caused zigzag artifacts. Original 50kn spike gate since replaced by spike-rejection-v2.

**gps-background (2026-06-20):** Rewrote `TrackRecordingService` as always-on foreground service — started in `MainActivity.onCreate`, stopped on double-back exit. Intent-driven notification updates (5s throttle) with live recording stats. Two notification modes: "Ready" / "Recording • {speed} kn • {elapsed} • {distance} nm". Demo suffix on both.

**Demo track visibility (2026-06-21):** Fixed `recordingPoints` off-by-one. Added `gpsMode` constructor parameter — bypasses `AdaptiveGpsPolicy` stillness gate in demo mode.

**spike-rejection-v2 (2026-06-22):** Four-gate algorithm replacing 50kn hard cap. Gate 0: GPS recovery (`hasLock` false→true). Gate 1: context-aware speed cap (32kn sea / 120kn land, auto-detected). Gate 2: course-aware direction multiplier (sea only, ±30° → ×1.5/×0.5). Gate 3: acceleration gate (10kn/s sea / 30kn/s land). Demo mode bypasses all. Self-contained in `TrackRecorder.kt`.

**Build:** ✅ `assembleDebug` passes

**pinned-tracks (2026-06-22):** Replaced eye-icon with pin-icon in TrackHistoryOverlay track card. Pin toggle → `TrackViewModel.setPinned()` → `TrackRepository.setPinned()`. Added `pinned: Boolean` to Track protobuf (ProtoNumber 14) and TrackSummary (ProtoNumber 12). Map rendering split: pinned tracks always render, history tracks capped by `trackingRenderNb`. Z-order: active > pinned > history. Pinned transparency defaults 0%→20% (amber→orange). Settings UI: "Number of history tracks" / "History transparency" / "Pinned transparency" RangeSliders. Fan button `mv.invalidate()` fix for toggle-off.

**track-list-render-indicator (2026-06-24):** Extracted `computeTrackPolylineAppearance()` pure utility — shared ARGB+stroke computation between map rendering and card indicator. Refactored both history and pinned polyline loops in MapScreen to use it. Added 4dp left-edge accent bar to each TrackCardContent card previewing the track's exact polyline color+alpha. Threaded 10 render settings (`tracksVisible`, `trackingRenderNb`, transparency/color pairs for past+pinned) through TrackHistoryOverlay. Non-visible tracks (beyond renderNb or `tracksVisible=false`) show muted grey bar.

**idle-time-tracking (2026-06-28):** Added `idleDurationSec` to Track/TrackSummary (proto #15/#14). Transition-based accumulator in TrackRecorder.addPoint() — timestamps idle-entry/exit and accumulates delta. Flushes open idle period on finalizeTrack(). Fixed `navigatingDurationSec = totalElapsedSec - idleDurationSec`. Mapped through TrackRepository rebuildIndex and orphan finalization. Display in 3 locations: track history summary cards, live track card (Nav corrected, Idle now real), menu drawer recording status. Real-time UiState refresh during idle via per-sample update. Build: ✅

**populate-track-info (2026-07-05):** Auto-populated track title and description from silent `whereAmI()` calls at idle-stop positions. Title uses 3-tier priority: 🤿 diving pinned marker > MANUAL BoatMarker > longest IDLE duration. Description is a living bullet log recomputed from full BoatMarker history on every trigger (idle start/end, manual marker, 3-min poll, marker add/edit via `Flow<Unit>`). IDLE BoatMarkers query `whereAmI()` for zone names; MANUAL BoatMarkers use pre-captured `MarkerSnapshot` names. Track finalize title: `[loc1 -> loc2]` from top 2 named stops per priority tier. Fixed `BoatMarker.startTimeMs` bug (was timer-fire time, now actual idle start). Error surface via existing `ErrorOverlay` with 8s auto-dismiss. Added `infoError: String?` to `TrackRecorderUiState`, `clearInfoError()`, `hasDivingPinnedMarker()`, `divingLocationName()`, `topSnapshotNames()`. Build: ✅

**resume-track (2026-07-12):** Resume a finalized track as a live recording. `TrackRecorder.resume()` extended with `fromCheckpoint` flag (default true for backward compat) — computes `resumeGapDurationSec` from inter-session wall-clock gap, clears `endTimeMs` on finalized tracks. `finalizeTrack()` subtracts gap from navigating duration (D10 prevents 50h inflation on 2-day gap), pattern-guards title recompute to preserve user-edited names (D6), forces `visibleOnMap=true` (D7). New `TrackViewModel.resumeTrack(trackId)` — 9-step setup mirroring `resumeOrphanedCheckpoint()`, only diff: `fromCheckpoint=false`. `TrackHistoryOverlay`: ▶ `PlayArrow` icon between pin and share on finalized track cards, visible when `endTimeMs != null && !isRecording`. Post-fix: `stopRecording()` invalidates `trackDetailCache` to prevent stale map polylines; `onResumeTrack` dismisses overlay. Wired via `OverlayLayer.kt`. Build: ✅

**merge-tracks (2026-07-12):** Merge 2+ finalized tracks into a single new track. New `TrackMerger` utility — concatenates points with `timeOffsetMs` rebasing to earliest start, GAP markers between segments, renumbered BoatMarkers, synthesized stats (sum per-track distance/idle/navigating, weighted avg speed, max fastest). `TrackViewModel.mergeTracks(ids, name, keepOriginals)` — load, merge, save, optional delete originals with cache invalidation. `TrackHistoryOverlay`: `MultiActionSpec("merge")` reuses existing `ListOverlayScaffold` multi-select; self-contained `AlertDialog` with auto-generated name (`"A + B"` / `"A ... Z"`) and "Keep original tracks" checkbox (default checked). New `ListAction.MergeTracks` + `TrackEvent.TracksMerged`. Build: ✅
