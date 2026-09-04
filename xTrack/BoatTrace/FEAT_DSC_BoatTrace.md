---
name: BoatTrace
status: active
created: 2026-06-15 21:43
modified: 2026-09-02 13:10
---

# Feature: BoatTrace

**Description:**
Trace the boat's movement (position, speed) during active navigation. One trace = one 'track' (Port Salis → Port Salis). Point capture suspends when stationary via `isStill()` gate, but the recording state stays ON. Tracks persisted as protobuf binary and recallable — their polylines display on the map overlay.

## Sections

### design
#### Docs
- `plans/boat-trace-design-discussion.md` — final design and implementation plan

### data-model

### recorder

#### Rules
- Track points recorded only when speed > 2.5 kn (matching Navigation cap arrow threshold)
- OFF→ON transition: geofence exit (inside→outside, 10s debounce) or manual Start in drawer
- Internal storage: protobuf binary via kotlinx-serialization-protobuf, not JSON
- Track recording runs on Dispatchers.Default, file I/O on Dispatchers.IO
- 30s periodic checkpoint save during ON state for crash recovery
- In demo mode, geofence check is bypassed — triggers on speed > 2.5 kn for 10s (manual also available)
- State machine is single-threaded (single coroutine collector) — no race between manual & auto triggers
- Track stats (`distanceNm`, `maxSpeedKn`, `avgSpeedKn`) accumulated continuously in memory during recording, written to protobuf at finalize

### ui-integration

### verification

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
- `xTrack/BoatTrace/260618_FEAT_PLN_BoatTrace_TrackList_Design.md` — track list UI design (swipe-to-delete, inline snackbar, animation spec)

### track-list

#### Todos
- [ ] Review and refine track card layout per design spec
- [ ] Verify swipe-to-delete, inline snackbar, undo animations
- [ ] Verify inline editing (auto-focus, field-switch commit, back-to-revert)
- [ ] Verify human-readable formatting (comma decimal, durations)
- [ ] Verify compact padding and flush-left stats grid
- [ ] E2E: Create test tracks, verify all card fields display correctly

#### Rules
- Track list UI must follow 260618_FEAT_PLN_BoatTrace_TrackList_Design.md spec
- Styling must match Settings overlay patterns (AppConfig tokens)

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt`

#### Docs
- `xTrack/BoatTrace/260618_FEAT_PLN_BoatTrace_TrackList_Design.md` — track list UI design and 26 requirements

### track-list-render-indicator

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt`

#### Docs
- `xTrack/BoatTrace/260624_FEAT_PLN_BoatTrace_track-list-render-indicator.md` — design plan

### tracking-status-n-triggers

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

### adaptive-isstill

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
- `xTrack/BoatTrace/260618_FEAT_PLN_BoatTrace_adaptive-isstill.md` — full plan

### hamburger-btn

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

### tweaks
#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/TrackStatusIcon.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt`

### render-tracks
#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt`
- `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt`

### mtrack-setting-opacity

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

### gps-background

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
- `xTrack/BoatTrace/260620_FEAT_PLN_BoatTrace_gps-background.md`

### gps-line-acquisition

#### Rules
- Spike rejection uses fix.timestampEpochMs (GPS epoch), not System wall clock
- AdaptiveGpsPolicy still receives every fix (including spikes) for stillness detection
- lastValidPoint* tracks last recorded point, separate from lastPointLat/Lon (Haversine distance accumulators)
- Original 50kn hard cap replaced by spike-rejection-v2 (4-gate algorithm)

#### Key Files
- `app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt`
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`

#### Docs
- `xTrack/BoatTrace/260620_FEAT_PLN_BoatTrace_gps-line-acquisition.md`

### track now demo

### pinned-tracks

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
- `xTrack/BoatTrace/260622_FEAT_PLN_BoatTrace_pinned-tracks.md` — full design & implementation plan

### gps-recording-regression

**Purpose:** Service-owned recorder GPS producer crashed at registration — `RuntimeException: Can't create handler … Looper.prepare()` — because `startGpsSampling()` collected `GpsLocationSource.locationUpdates()` on `Dispatchers.Default`. Fixed with `.flowOn(Dispatchers.Main.immediate)`.

#### Key Files
- `app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt`
- `app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt`

### gps-switch-confirm

**Purpose:** While a track is recording, toggling the position source (GPS↔demo) now asks for confirmation via `ConfirmSheet` (bottom sheet, dashboard space) before switching. Guard lives at the single `onGpsModeChange` choke point, covering the drawer switch, settings switch, and top-left GPS icon.

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml`

### auto-marker-cleanup

**Purpose:** Harden 🕐 IDLE_AUTO marker lifecycle — move createTemp/confirm/delete into a recorder-owned `AutoMarkerManager` so cleanup is deterministic regardless of Activity state; fix merged-marker keepability, ghost pins, finalize fallback; keep startup cleanup as crash recovery only.

#### Todos
- [x] Add AutoMarkerManager with UserMarkerRepository + dedup parity in createTemp
- [x] Serialize repo writes (Mutex + service→UI change channel)
- [x] Wire recorder: createTemp on idle start, confirm/delete on idle end (confirm before persisting BoatMarker.autoMarkerId)
- [x] Durable finalize fallback delete in runBlocking(Dispatchers.IO)
- [x] Expose unfiltered all-marker id set; ghost-pin render-time existence check
- [x] Remove dead code (ACTION_SET_* intents, setActiveSessionAutoMarkerId, setBoatMarkerAutoMarkerId, IdleCaptureResult.autoMarkerId)
- [x] Scope startup cleanup to IDLE_AUTO non-keepable crash orphans only
- [x] Fix merged markers confirmed=true, keepable=true
- [x] Build (apk-build.bat) — BUILD SUCCESSFUL
- [ ] Deploy + E2E verify cleanup scenarios

#### Docs
- `xTrack/BoatTrace/260831_FEAT_PLN_BoatTrace_auto-marker-cleanup.md` — cleanup hardening plan (Ask-reviewed)

### marker-track-link

**Purpose:** Single canonical back-reference `UserMarker.trackId` (set at creation) replacing `BoatMarker.autoMarkerId`; one-time backfill migration; delete-track deletes its IDLE_AUTO markers; marker list badge + "Belongs to track" row.

#### Docs
- `xTrack/BoatTrace/260831_FEAT_PLN_BoatTrace_marker-track-link.md` — plan (Ask-reviewed)

### marker-track-nav (DEFERRED)

**Purpose:** Cross-navigation between track detail and marker detail via `>` links, with a minimal `OverlayBackStack`.

#### Docs
- `xTrack/BoatTrace/260831_FEAT_PLN_BoatTrace_marker-track-nav.md` — plan

### marker-export-import  [-]

**Purpose:** Track export hardening (unique names, Windows-safe sanitization, `yyyy_MM_dd_HH_mm-title-counter.gpx`) and track import modes (single GPX Skip/Update/New dialog, ZIP silent skip). Marker export/import pending.

#### Docs
- `xTrack/BoatTrace/260831_FEAT_PLN_BoatTrace_marker-export-import.md` — plan

### track-direction-arrows

**Purpose:** Direction chevrons along history + pinned tracks — custom osmdroid overlay, zoom-adaptive pixel spacing, exponential speed-based density, settings + drawer toggles.

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/TrackDirectionOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`

#### Docs
- `xTrack/BoatTrace/260901_FEAT_PLN_BoatTrace_track-direction-arrows.md` — plan

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

### idle-time-tracking

#### Key Files
- `app/src/main/java/ykws/android/maro/data/track/Track.kt`
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`
- `app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt`
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt`

#### Docs
- `xTrack/BoatTrace/260628_FEAT_PLN_BoatTrace_idle-time-always-zero.md` — root cause + fix plan + implementation record

### merge-tracks

#### Docs
- `xTrack/BoatTrace/260714_FEAT_PLN_BoatTrace_merge-tracks.md` — discussion plan: merge tracks vs resume track

### notif-lifecycle

#### Key Files
- `app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

#### Docs
- `xTrack/BoatTrace/260714_FEAT_PLN_BoatTrace_notif-lifecycle-hardening.md` — design & implementation plan

### populate-track-info

### tracks-paint-order

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

#### Docs
- `xTrack/BoatTrace/260717_FEAT_PLN_BoatTrace_tracks-paint-order.md` — design & implementation plan

## Docs
- `plans/boat-trace-design-discussion.md` — final design and implementation plan
- `xTrack/BoatTrace/260618_FEAT_PLN_BoatTrace_TrackList_Design.md` — track list UI requirements, animation design, component architecture
- `xTrack/BoatTrace/260618_FEAT_PLN_BoatTrace_render-tracks.md` — render-tracks implementation plan
- `xTrack/BoatTrace/FEAT_DOC_BoatTrace_decisions.md` — comprehensive functional/architectural decisions record (7 categories, 40+ decisions)
- `xTrack/BoatTrace/260620_FEAT_PLN_BoatTrace_gps-line-acquisition.md` — GPS point acquisition: outlier rejection (speed gate) + passive listener removal (ordering fix)
- `xTrack/BoatTrace/260620_FEAT_PLN_BoatTrace_gps-background.md` — Persistent foreground service: always-on notification with live recording stats, demo-aware
- `xTrack/BoatTrace/260622_FEAT_PLN_BoatTrace_spike-rejection-v2.md` — Spike rejection v2: 4-gate algorithm design plan
- `xTrack/BoatTrace/260618_FEAT_PLN_BoatTrace_adaptive-isstill.md` — adaptive GPS stillness detection plan
- `xTrack/BoatTrace/260622_FEAT_PLN_BoatTrace_pinned-tracks.md` — Pinned tracks: replace eye-icon with pin-icon, separate transparency, z-order
- `xTrack/BoatTrace/260717_FEAT_PLN_BoatTrace_tracks-paint-order.md` — Tracks paint order: flip z-order (newest-on-top) + highlight-to-top above active

## Implemented

- **Data model** — `Track`/`TrackPoint` protobuf, `TrackSummary` index, relative `timeOffsetSec`
- **Recorder** — OFF⇄ON state machine, geofence auto-detect, speed gate, orphan recovery
- **Persistence** — `TrackRepository` protobuf CRUD, 30s checkpoints, GPX 1.1 export
- **Map rendering** — active + history polylines, transparency/color gradient, overlay diff
- **Layer toggle** — `TrackLayerIcon` in FanLayout
- **TrackViewModel** — `StateFlow` bridge, LRU cache, sorted list
- **UI** — `TrackStatusIcon`, `MenuDrawerOverlay`, `TrackHistoryOverlay`, `LiveTrackCard`
- **Settings** — unified tracking section, HSV pickers, transparency semantics
- **Stop detection** — `AdaptiveGpsPolicy` position-only → `xTrack/BoatTrace/260618_FEAT_PLN_BoatTrace_adaptive-isstill.md`
- **Settings fix (2026-06-20)** — 6 tracking fields persisted, opacity naming fix
- **mtrack-setting-opacity (2026-06-20)** — transparency naming + inverted semantics
- **gps-line-acquisition (2026-06-20)** — removed `PASSIVE_PROVIDER` listener
- **gps-background (2026-06-20)** — foreground service rewrite → `xTrack/BoatTrace/260620_FEAT_PLN_BoatTrace_gps-background.md`
- **Demo track visibility (2026-06-21)** — off-by-one fix + `gpsMode` bypass
- **spike-rejection-v2 (2026-06-22)** — four-gate algorithm → `xTrack/BoatTrace/260622_FEAT_PLN_BoatTrace_spike-rejection-v2.md`
- **pinned-tracks (2026-06-22)** — pin icon + `pinned` proto field → `xTrack/BoatTrace/260622_FEAT_PLN_BoatTrace_pinned-tracks.md`
- **track-list-render-indicator (2026-06-24)** — `computeTrackPolylineAppearance()` shared utility
- **idle-time-tracking (2026-06-28)** — `idleDurationSec` accumulator
- **populate-track-info (2026-07-05)** — auto title/description from `whereAmI()`
- **resume-track (2026-07-12)** — resume finalized track as live recording
- **merge-tracks (2026-07-12)** — `TrackMerger` utility
- **checkmark-bottom-right (2026-07-14)** — badge position fix
- **notif-lifecycle-hardening (2026-07-14)** — tap-to-open, post-kill, recording-aware exit
- **tracks-paint-order (2026-07-17)** — newest-on-top + highlight-to-top → `xTrack/BoatTrace/260717_FEAT_PLN_BoatTrace_tracks-paint-order.md`
- **idle-reconciliation (2026-08-15)** — unified compound idle predicate
- **track-direction-arrows (2026-09-02)** — chevron overlay, density settings
- `xTrack/BoatTrace/260618_FEAT_PLN_BoatTrace_adaptive-isstill-settings-redesign.md` — Adaptive isStill settings redesign
- `xTrack/BoatTrace/260617_FEAT_PLN_BoatTrace_boat-trace-fresh-import-plan.md` — Boat trace fresh import plan
- `xTrack/BoatTrace/260618_FEAT_PLN_BoatTrace_boat-trace-ui-refinement-plan.md` — Boat trace UI refinement plan
- `xTrack/BoatTrace/260625_FEAT_PLN_BoatTrace_track-system-simplification-plan.md` — Track system simplification plan
