---
name: BoatTrace
status: active
created: 2026-06-15 21:43
modified: 2026-06-17 21:45
active_subfeature: verification
---

# Feature: BoatTrace

**Description:**
Trace the boat's movement (position, speed) during active navigation. One trace = one 'tack' (Port Salis → Port Salis). Tacking pauses when boat is stationary. Tacks are persisted as protobuf binary and recallable — their polylines can be displayed on the map overlay.

## Subfeatures

### design  [x]


#### Todos
- [x] Define Tack and TackPoint data models
- [x] Design auto-detection of tack start/end (Port Salis geofence)
- [x] Design tack recording pipeline (GpsFix -> TackRepository)
- [x] Design persistence strategy (protobuf binary via kotlinx-serialization-protobuf)
- [x] Design tack recall UI (list + map display)
- [x] Design decisions on storage format (protobuf + GPX export), metadata (name, description, color), real-time drawing, per-phase testing
- [x] Document design decisions

#### Docs
- `plans/boat-trace-design-discussion.md` — final design and implementation plan

### data-model  [x]

#### Todos
- [x] Add `tack.originLat`, `tack.originLon`, `tack.geofenceRadiusM`, `tack.enabled.default` to `maro.properties`
- [x] Add BuildConfig fields for tack defaults in `app/build.gradle.kts`
- [x] Add tack fields to `AppSettings` in `data/settings/SettingsManager.kt`
- [x] Create `data/tack/TackPoint.kt` — `@Serializable @ProtoNumber data class TackPoint(lat, lon, speedMps?, bearingDeg?, timeOffsetSec)`
- [x] Create `data/tack/Tack.kt` — `@Serializable @ProtoNumber data class Tack(id, name, comment, startTimeMs, endTimeMs?, pausedDurationSec, fastestSpeedMps, trackColorArgb, tackPoints)` + `TackSummary`
- [x] Create `data/tack/TackEvent.kt` — `sealed class TackEvent(Started, Paused, Resumed, Stopped, PointCaptured)`
- [x] Add `timestampEpochMs: Long` field to `GpsFix` in `data/location/GpsLocationSource.kt`
- [x] **Test:** Protobuf round-trip encode/decode for TackPoint and Tack
- [x] **Test:** `assembleDebug` passes

#### Rules

#### Key Files

#### Docs

### recorder  [x]

#### Todos
- [x] Create `data/tack/TackGeofenceChecker.kt` — Haversine distance calculation, `isInsideGeofence(pos, origin, radiusM): Boolean`
- [x] Create `data/tack/TackRecorder.kt` — coroutine state machine: IDLE -> RECORDING <-> PAUSED -> FINALIZING -> IDLE
- [x] Create `data/tack/TackRepository.kt` — protobuf file CRUD on Dispatchers.IO (save, load, list, delete, update)
- [x] Create `data/tack/TackViewModel.kt` — StateFlow<TackUiState> bridge
- [x] Implement continuous tack stats accumulation: `runningMaxSpeedKn`, `cumulativeDistanceNm`, `speedSum`/`speedCount`
- [x] Implement process-death recovery: scan for orphaned `.bin` checkpoints on startup
- [x] **Test:** TackGeofenceChecker — inside, outside, edge at exact radius
- [x] **Test:** TackRecorder state machine — mock GpsFix flow, verify all transitions (incl. race safety)
- [x] **Test:** TackRepository — save, list, load, delete, verify files on disk
- [x] **Test:** `assembleDebug` passes

#### Rules
- Tack points recorded only when speed > 2.5 kn (matching Navigation cap arrow threshold)
- IDLE→RECORDING transition requires speed > 2.5 kn sustained for **10 seconds** (debounce against GPS glitches)
- Internal storage: protobuf binary via kotlinx-serialization-protobuf, not JSON
- Tack recording runs on Dispatchers.Default, file I/O on Dispatchers.IO
- 30s periodic checkpoint save during RECORDING state for crash recovery
- In demo mode, geofence check is bypassed — recording triggers on speed > 2.5 kn for 10s
- State machine is single-threaded (single coroutine collector) — no race between manual & auto triggers
- Tack stats (`distanceNm`, `maxSpeedKn`, `avgSpeedKn`) accumulated continuously in memory during recording, written to protobuf at finalize

#### Key Files

#### Docs

### ui-integration  [x]

#### Todos
- [x] Wire TrackRecorder into `ui/map/CoastlineViewModel.kt` (collect GpsFix flow)
- [x] Create standalone track overlay logic — one osmdroid Polyline per visible track (tagged "track\_"), managed by LaunchedEffect outside CoastlineMapView
- [x] Protect track Polylines from CoastlineMapView cleanup (exclude by "track\_" title prefix)
- [x] Implement lazy loading: tracks loaded from protobuf only when visibleOnMap toggled on
- [x] Add Tack settings section (Settings -> General -> Tack) with enable toggle, origin lat/lon, radius
- [x] Create `TackStatusIcon` composable — 👣 in top-left icon row
- [x] Create new **Tack Drawer** (hamburger icon) — contains "Tack List" shortcut + Start/Stop Recording button
- [x] Implement auto-naming of tacks (format: `"yyyy-MM-dd HH:mm"`)
- [x] Create `ui/tack/TackHistoryOverlay.kt` — full-page LazyColumn with tack cards:
  - Card shows: date start, name (editable), description (editable), max speed, distance
  - In-place edit: tap name/description → inline TextField → Done commits to protobuf
  - Visibility toggle: 👁️ button per tack to show/hide trace on map
  - Swipe-to-delete with confirmation dialog
  - GPX share button per card
- [x] Add `visibleOnMap: Boolean` field to `Tack` and `TackSummary` data classes (default true)
- [x] Implement `TackViewModel.updateTack(id, name, comment)` + `setTackVisibility(id, visible)`
- [x] Create `res/xml/provider_paths.xml` + register `<provider>` in AndroidManifest.xml
- [x] Add GPX export function (`Tack.toGpx(): String`) + share intent via FileProvider
- [x] **Test:** TackViewModel StateFlow emits correct states during mock recording
- [x] **Test:** GPX export — validate XML structure against GPX 1.1 (trkpt, name, time, speed, course)
- [x] **Test:** No regression in existing map tests
- [x] **Test:** `assembleDebug` passes

#### Rules

#### Key Files

#### Docs

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

### hamburger-btn  [x]

#### Todos
- [x] Add hamburger `Button` with `Icons.Default.Menu` matching `MapControlButton` style (64dp, round, `ButtonColors.bg`/`ButtonColors.icon`)
- [x] Wire hamburger onClick to open TrackDrawerOverlay
- [x] Hamburger always visible — unconditional render
- [x] Top-aligned with status icons (`Alignment.Top` in Row)
- [x] Drawer title: "Maro II" (24sp Bold White)
- [x] Scrim on right 25% only (click to dismiss)
- [x] Drawer panel at 75% width with Settings-styled layout
- [x] Tight spacing (Settings-like): 8dp outer, 6dp items, 2dp gaps
- [x] Dynamic "Start Tracking" / "Stop Tracking" text
- [x] Stats card with grey background (`0x1AFFFFFF`, RoundedCornerShape 12dp)
- [x] TrackHistoryOverlay status bar padding
- [x] Real-time data via AnimatedVisibility (no outer if-guard)
- [x] BackHandler + back button to close drawer
- [ ] Verify: hamburger appears in top-left, opens TrackDrawer with controls

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

## Todos

## Rules
- Tack points only recorded while speed > 2.5 kn (drifting threshold from Navigation feature)
- IDLE→RECORDING transition requires speed > 2.5 kn sustained for **10 seconds**
- Internal storage: **Protobuf binary** via `kotlinx-serialization-protobuf` — not JSON
- Export format: **GPX 1.1** for compatibility with QGIS, Google Earth, OsmAnd, etc.
- Tack metadata captured: name (auto-generated), comment, start/end time, paused duration, fastest speed, track color
- Real-time trace: `Polyline.setPoints()` on active overlay, no recreation
- Auto tack detection via configurable geofence (origin + radius from maro.properties)
- `visibleOnMap: Boolean` on Tack data class — per-tack visibility toggle on map
- Manual Start/Stop available in new tack drawer (hamburger icon)
- Demo mode bypasses geofence — recording triggers on speed > 2.5 kn for 10s
- `timeOffsetSec` (seconds since tack start) stored per point instead of absolute timestamp to save ~5 bytes/point
- Tack recording runs on Dispatchers.Default, file I/O on Dispatchers.IO
- 30s periodic checkpoint save during RECORDING for crash recovery
- Tack stats (`distanceNm`, `maxSpeedKn`, `avgSpeedKn`) accumulated continuously in TackRecorder, written at finalize
- `index.json` rebuilt by scanning `.bin` files on startup if missing or corrupted
- Swipe-to-delete on TackHistoryOverlay with confirmation dialog

## Key Files

## Docs
- `plans/boat-trace-design-discussion.md` — final design and implementation plan
