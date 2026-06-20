<!-- scope: feature -->
# BoatTrace — Functional Decisions Record

> **Purpose:** Capture every functional and architectural decision made during the BoatTrace feature.  
> **Scope:** Boat movement tracking — record, persist, render, export, manage tracks.  
> **Last updated:** 2026-06-20

---

## 1. Data Model Decisions

### 1.1 Storage Format: Protobuf Binary over JSON
- **Decision:** Use `kotlinx-serialization-protobuf` with `@ProtoNumber` annotations instead of JSON.
- **Rationale:** Binary protobuf is ~30–40% smaller per point, faster to serialize/deserialize, and avoids JSON parsing overhead on mobile. The `@ProtoNumber` schema is version-tolerant (field additions don't break old files).
- **Filed:** [`Track.kt`](app/src/main/java/ykws/android/maro/data/track/Track.kt), [`TrackPoint.kt`](app/src/main/java/ykws/android/maro/data/track/TrackPoint.kt)

### 1.2 Relative Time Offsets (timeOffsetSec)
- **Decision:** Store `timeOffsetSec: Int` (seconds since tack start) per point instead of absolute Unix timestamps.
- **Rationale:** Saves ~5 bytes/point vs storing `Long timestampEpochMs` — significant over thousands of points. Absolute start time is stored on the `Track` record.
- **Filed:** [`TrackPoint.kt`](app/src/main/java/ykws/android/maro/data/track/TrackPoint.kt:19)

### 1.3 TrackSummary as Lightweight Data Class
- **Decision:** `TrackSummary` (7 fields, non-@ProtoNumber-annotated for loading) separate from full `Track` (11 fields, @ProtoNumber).
- **Rationale:** The track list displays ~7 fields from dozens of tracks — no need to deserialize full point arrays. `TrackSummaryList` wrapper enables single-file index serialization.
- **Filed:** [`Track.kt`](app/src/main/java/ykws/android/maro/data/track/Track.kt:46)

### 1.4 visibleOnMap: Per-Track Visibility
- **Decision:** `visibleOnMap: Boolean` (default `true`) on the `Track` data class.
- **Rationale:** Users can hide individual tracks from the map without deleting them. Persisted in protobuf so survives restarts.
- **Filed:** [`Track.kt`](app/src/main/java/ykws/android/maro/data/track/Track.kt:38)

### 1.5 distanceNm Accumulated at Finalize
- **Decision:** `distanceNm: Float` computed continuously in `TrackRecorder` during recording, written to protobuf at finalize time.
- **Rationale:** Avoids recomputing cumulative Haversine distances on every load. The value is accumulated in memory as points are added.
- **Filed:** [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:242)

---

## 2. Recording Subsystem Decisions

### 2.1 Coroutine State Machine: OFF ⇄ ON (2-State)
- **Decision:** Simplified from 5-state (`IDLE → RECORDING ⇄ PAUSED → FINALIZING → IDLE`) to 2-state (`OFF ⇄ ON`).
- **Rationale:** The PAUSED and FINALIZING states added complexity without user-facing benefit. `isStill()` gates point capture within ON state — recording never truly pauses, it just stops collecting points. See [`tracking-states-triggers` plan](FEAT_PLN_BoatTrace_tracking-states-triggers.md).
- **Filed:** [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:41)

### 2.2 Geofence Auto-Detection (Port Salis)
- **Decision:** Configurable geofence origin + radius from `maro.properties` (`tack.originLat`, `tack.originLon`, `tack.geofenceRadiusM`). Haversine distance used for inside/outside check.
- **Rationale:** Auto-start on leaving Port Salis, auto-stop on return. Default origin: 43.55°N, 7.00°E with 500m radius.
- **Filed:** [`TrackGeofenceChecker.kt`](app/src/main/java/ykws/android/maro/data/track/TrackGeofenceChecker.kt)

### 2.3 Speed Gate: 2.5 kn Minimum
- **Decision:** Track points only recorded when speed > 2.5 kn (matches Navigation feature's cap arrow threshold).
- **Rationale:** Prevents recording of drift/bob while stationary. 2.5 kn ≈ 1.3 m/s — consistent with the Navigation feature's drifting threshold.
- **Filed:** [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:182)

### 2.4 30-Second Checkpoint Save
- **Decision:** Periodic checkpoint saves every 30s during ON state.
- **Rationale:** Crash recovery — if the app is killed during recording, at most 30s of points are lost. Checkpoints are `.bin` files with `_checkpoint` suffix.
- **Filed:** [`TrackRepository.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:172)

### 2.5 Process-Death Recovery
- **Decision:** Scan for orphaned `*_checkpoint.bin` files on startup. Offer user to save or discard.
- **Rationale:** If the app crashes mid-recording, the checkpoint is recovered on next launch. User gets an AlertDialog to decide.
- **Filed:** [`TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:132)

### 2.6 Demo Mode: Geofence Bypass
- **Decision:** In demo mode, recording triggers on speed > 2.5 kn for 10s (no geofence check).
- **Rationale:** Demo mode has no real GPS position, so geofence would never fire. Speed-based trigger enables testing.
- **Filed:** [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:161)

### 2.7 Index.json Fallback
- **Decision:** `index.bin` rebuilt by scanning `.bin` files on startup if missing or corrupted.
- **Rationale:** Self-healing — no manual recovery needed if index file is deleted or corrupted.
- **Filed:** [`TrackRepository.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:80)

### 2.8 Track Stats: In-Memory Accumulation
- **Decision:** `distanceNm`, `maxSpeedKn`, `avgSpeedKn` accumulated continuously in `TrackRecorder` as points are added.
- **Rationale:** Avoids re-scanning all points on finalize. Written to protobuf only when the track is finalized (auto-stop or manual stop).
- **Filed:** [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:242)

---

## 3. Tracking Triggers & State Decisions

### 3.1 Manual-Only Start/Stop (Primary)
- **Decision:** Primary trigger is manual (Start/Stop in TrackDrawer). Geofence exit is secondary auto-trigger.
- **Rationale:** Users should have full control. Auto-start on movement alone is NOT used — even when geofence is disabled.
- **Filed:** [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:105)

### 3.2 No Auto-Start on Movement
- **Decision:** Even when geofence is disabled, the user must start manually. No speed-based auto-start.
- **Rationale:** Prevents accidental recordings. The 10s debounce only applies to the geofence-triggered start.
- **Filed:** [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:105)

### 3.3 RECORDING Persists Through Stationary Periods
- **Decision:** State stays ON when stationary. Only point capture suspends (gated by `isStill()`).
- **Rationale:** Simplifies UX — no automatic pause/resume. The recording is always "on" once started, just not collecting data while stationary.
- **Filed:** [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:200)

### 3.4 isStill() via AdaptiveGpsPolicy (Position-Only)
- **Decision:** `isStill()` uses `AdaptiveGpsPolicy` with pure position-only algorithm — no speed input.
- **Rationale:** Speed from GPS is noisy at low values. Position displacement is more reliable for detecting stationarity. Algorithm: anchor position + time window, re-anchor on displacement ≥ threshold.
- **Filed:** [`AdaptiveGpsPolicy.kt`](app/src/main/java/ykws/android/maro/data/location/AdaptiveGpsPolicy.kt)

### 3.5 isStill() Default Thresholds
- **Decision:** `stopDetectionTimeSec = 45` (range 10-90), `stopDetectionDistanceM = 15` (range 10-30).
- **Rationale:** 45s window accounts for GPS position jitter. 15m displacement threshold prevents false IDLE from GPS drift while anchored.
- **Filed:** [`SettingsManager.kt`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:240)

### 3.6 Old Adaptive Settings: Clean Removal
- **Decision:** Old settings (`adaptiveWindowSec`, `adaptiveDistanceM`, `adaptiveIdleIntervalSec`) completely removed — no migration, no fallback.
- **Rationale:** Clean cutover avoids maintaining backward compat code paths. Users will need to reconfigure if upgrading.
- **Filed:** [`SettingsManager.kt`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:186)

### 3.7 GPS Dormant Interval
- **Decision:** GPS dormant interval = `stopDetectionTimeSec * gpsDormantPct / 100`. Default pct = 80%. Must enforce `< 100` at config validation.
- **Rationale:** When `isStill()` is true, GPS can poll less frequently to save battery. Percentage of adaptive time keeps it proportional.
- **Filed:** [`maro.properties`](maro.properties:65)

---

## 4. UI Decisions

### 4.1 TrackStatusIcon: 3-Visual-State Model
- **Decision:** Three visual states — OFF (dimmed white, no dot), ON+moving (green, red pulsing dot), ON+idle (blue, blue pulsing dot).
- **Rationale:** Matches `GpsStatusIcon` and `EarthWaterIcon` styling. Color coding gives instant state recognition at a glance. Pulsing dot animation via `InfiniteTransition`.
- **Filed:** [`TrackStatusIcon.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackStatusIcon.kt)

### 4.2 TrackStatusIcon Click = Toggle Recording
- **Decision:** Clicking the footprint icon starts/stops recording directly (both GPS and demo modes).
- **Rationale:** Quick access without opening drawer. Matches GPS icon click behavior.
- **Filed:** [`TrackStatusIcon.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackStatusIcon.kt:45)

### 4.3 TrackDrawerOverlay: Animated Right Panel
- **Decision:** Right-side animated panel at 75% width with scrim on right 25%. Title "Maro II". Section header "TRACK RECORDING".
- **Rationale:** Hamburger icon in top-left icon row. Drawer styled consistently with Settings overlay patterns (8dp outer, 6dp items, 2dp gaps). Close via back button or scrim tap.
- **Filed:** [`TrackDrawerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackDrawerOverlay.kt)

### 4.4 Hamburger: Always Visible
- **Decision:** Hamburger icon (64dp round, `ButtonColors.bg`/`ButtonColors.icon`) always visible in top-left icon row, first position.
- **Rationale:** Always-accessible entry point to tracking controls, even when not actively recording. Icon is 36dp Canvas (increased from original 28dp spec).
- **Filed:** [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1269)

### 4.5 Track Drawer: Unified ON/OFF Toggle + Track List
- **Decision:** Single row with "Track List..." label + colored Switch (ON/OFF) replaces separate "Start Recording" button.
- **Rationale:** Merges two controls into one intuitive toggle. Switch color reflects state (green=moving, blue=idle/waiting, grey=off).
- **Filed:** [`TrackDrawerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackDrawerOverlay.kt:202)

### 4.6 TrackHistoryOverlay: Full-Screen LazyColumn
- **Decision:** Full-screen overlay with `LazyColumn`, swipe-to-delete with snackbar undo, inline editing for name/comment.
- **Rationale:** LazyColumn handles large track lists efficiently. Swipe-to-delete matches Material3 patterns. Inline editing avoids dialog complexity.
- **Filed:** [`TrackHistoryOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt)

### 4.7 Live Track Card
- **Decision:** During RECORDING, insert a live track card at position 0 in history list. Pulsing border + dot, editable name/comment, live stats from `TrackRecorderUiState`.
- **Rationale:** Users can edit name/comment in real-time during recording. Stats update live via StateFlow. Pulsing border indicates active recording.
- **Filed:** [`TrackHistoryOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:604)

### 4.8 GPX 1.1 Export via FileProvider
- **Decision:** Export as GPX 1.1 XML via `FileProvider` share intent. XML includes `<trk>`, `<trkseg>`, `<trkpt>` with `<ele>`, `<time>`, `<speed>`, `<course>`.
- **Rationale:** GPX is universal — compatible with QGIS, Google Earth, OsmAnd, etc. FileProvider avoids WRITE_EXTERNAL_STORAGE permission.
- **Filed:** [`GpxExporter.kt`](app/src/main/java/ykws/android/maro/data/track/GpxExporter.kt), [`provider_paths.xml`](app/src/main/res/xml/provider_paths.xml)

### 4.9 Auto-Naming Format
- **Decision:** Auto-generated track name format: `"yyyy-MM-dd HH:mm"` (e.g. "2026-06-18 14:32").
- **Rationale:** Human-readable, chronologically sortable, locale-independent.
- **Filed:** [`TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:117)

---

## 5. Render Subsystem Decisions

### 5.1 Color Gradient (pastFrom → pastTo) Instead of Single Color
- **Decision:** Replaced planned single `tracking.color.history` with gradient: `tracking.color.pastFrom` (newest) → `tracking.color.pastTo` (oldest) via linear RGB interpolation.
- **Rationale:** Visual differentiation of track age at a glance. Newest tracks rendered in `pastFrom` color, fading to `pastTo` for oldest. Was `tracking.color.history` in original plan.
- **Filed:** [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:612)

### 5.2 Transparency RangeSlider Instead of Fixed Steps
- **Decision:** Configurable `tracking.transparency.from`/`to` via RangeSlider (0-100%), replacing planned fixed 90%→10% step.
- **Rationale:** User control over fade range. RangeSlider setting mapped to `trackingTransparencyFrom`/`To` in AppSettings.
- **Filed:** [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2521)

### 5.3 Pinned Colors as From/To Pair
- **Decision:** `tracking.color.pinnedFrom`/`pinnedTo` (pair, like past tracks). Infrastructure only — behavior reserved for future use.
- **Rationale:** Consistent API with past tracks. No pinned track behavior implemented yet — reserved for future "pin track for permanent display" feature.
- **Filed:** [`SettingsManager.kt`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:296)

### 5.4 Incremental Overlay Diff (Performance)
- **Decision:** `renderedTrackIds: MutableState<Set<String>>` tracks currently-rendered polylines. On each `trackSummaries` change, compute diff → add new, remove stale, leave unchanged.
- **Rationale:** Avoids full teardown+rebuild on every emission (especially during 30s checkpoint saves). Critical for map performance with many tracks.
- **Filed:** [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:552)

### 5.5 LRU Track Detail Cache
- **Decision:** `LinkedHashMap<String, Track>` with max 30 entries in `TrackViewModel`. Invalidated on `updateTrack()`, `deleteTrack()`.
- **Rationale:** Avoids repeated protobuf deserialization from disk for the same tracks. Cache hit avoids coroutine hop to `Dispatchers.IO`.
- **Filed:** [`TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:85)

### 5.6 Track Polylines: tag Prefix for Cleanup Protection
- **Decision:** Track polylines tagged with `track_` prefix (`track_hist_{id}`, `track_recording`). CoastlineMapView cleanup excludes `track_`-prefixed overlays.
- **Rationale:** Prevents accidental removal during layer rebuild (depth map, regulated zones, etc.).
- **Filed:** [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:574)

### 5.7 Most Recent Track: Thicker Stroke
- **Decision:** First (most recent) history track gets thicker stroke (8f vs 6f for older tracks).
- **Rationale:** Visual emphasis on the newest track. Active recording track is 10f (differentiated from history).
- **Filed:** [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:619)

### 5.8 TrackLayerIcon in FanLayout (Index 0)
- **Decision:** `TrackLayerIcon` as first FanLayout child (index 0, bottom of arc). `tracksVisible: Boolean` AppSettings field controls visibility.
- **Rationale:** Consistent with other layer toggles. Positioned at bottom for accessibility (thumb reach).
- **Filed:** [`FanIconComponents.kt`](app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt:238)

### 5.9 Default tracksVisible = true
- **Decision:** `tracksVisible` defaults to `true`.
- **Rationale:** Tracks layer visible out of the box so users immediately see their recordings.
- **Filed:** [`SettingsManager.kt`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:240)

---

## 6. Settings Organization Decisions

### 6.1 Unified Tracking Section in Navigation Tab
- **Decision:** All track settings unified under "Tracking" collapsible section in General → Display → Layers card. Includes: tracks toggle, number of tracks slider, transparency RangeSlider, color pickers for active/past/pinned.
- **Rationale:** Originally planned split (General tab + Navigation tab) was fragmented. Unified section improves UX.
- **Filed:** [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2423)

### 6.2 Canvas-Based Color Picker
- **Decision:** Custom Canvas-based HSV color picker (~100 lines, zero new Gradle deps).
- **Rationale:** Matches project's extensive Canvas usage. Avoids adding third-party libraries (per AGENTS.md §4 Design Deviation Gate).
- **Filed:** [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2564)

### 6.3 Color Settings: 3 Swatches + Picker Dialog
- **Decision:** Three color swatches in Tracking section: Active track (single color), Past tracks (from→to pair), Pinned tracks (from→to pair). Tapping opens color picker dialog.
- **Rationale:** Color pairs (from→to) for gradient-based rendering. Single color for active track. Consistent pattern for all three.
- **Filed:** [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2564)

---

## 7. Tech Stack Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Serialization | `kotlinx-serialization-protobuf` | Binary efficiency, version-tolerant schema |
| State exposure | `StateFlow<TrackRecorderUiState>` | Reactive Compose integration |
| Async execution | Kotlin Coroutines (`Dispatchers.Default`/`IO`) | Structured concurrency, no raw threads |
| Map rendering | osmdroid `Polyline` | Existing project dependency, no new libs |
| Export format | GPX 1.1 XML | Universal compatibility |
| Sharing | `FileProvider` + `Intent.ACTION_SEND` | No storage permissions needed |
| Color picker | Custom Canvas/Compose | Zero new dependencies |
| Track cache | `LinkedHashMap` (max 30) | LRU eviction, no external cache lib |
