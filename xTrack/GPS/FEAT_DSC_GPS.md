---
name: GPS
status: active
created: 2026-06-07 00:00
modified: 2026-08-15 15:21
---

# Feature: GpsPlugin

**Description:**
Adds a "Source de position" toggle at the top of the settings page switching between Démo
(free-pan, north-up — the legacy behavior) and GPS mode. In GPS mode the device GPS drives the
map center (the screen-centered boat marker stays on the real position) and the map rotates
heading-up from GPS course over ground, falling back to the device compass when stationary.
Location uses the Android framework LocationManager + SensorManager (no Google Play Services);
ACCESS_FINE_LOCATION is requested when the toggle is enabled.

## Sections

### settings

#### Todos
- [x] Add "Source de position" Démo/GPS toggle at the top of the settings overlay
- [x] Persist `gpsMode` in SettingsManager (SharedPreferences)
- [x] GPS drives map center + recenter on each fix (gated on gpsMode)
- [x] Heading-up rotation: GPS course, compass fallback after 3 s
- [x] Runtime ACCESS_FINE_LOCATION request on enable; foreground gate via lifecycle
- [x] Compass rate-limit (sample 200 ms) + 1° jitter threshold
- [x] Reset GPS state on disable so demo free-pans and re-enable recenters
- [x] Adjustable GPS recenter delay setting (1–10 s) via a slider
- [ ] Build (apk-build.bat) + on-device verification of all four flows

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt` — LocationManager → Flow<GpsFix> (position + course)
- `app/src/main/java/ykws/android/maro/data/location/CompassSource.kt` — rotation-vector azimuth Flow
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — gpsPosition/mapBearing StateFlows + enabled collectors
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — toggle UI, permission launcher, recenter/rotation effects
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — gpsMode persistence

#### Docs

### dashboard

#### Todos
- [x] 2×2 grid layout (Distance, Zone 300 m, Profondeur, Vitesse)
- [x] Auto-size each value to fill its cell; labels/context small + subdued
- [x] GPS speed card in knots ("—" in demo); speed plumbed GpsFix → ViewModel → UI
- [x] Speed colour coding in the 300 m zone: <5 kn green, 5–10 orange, >10 red; default outside
- [x] Portrait dashboard height = ⅔ of the short side (mirrors landscape width)
- [ ] Build (apk-build.bat) + on-device verification (portrait sizing, auto-fit, colour thresholds)

#### Rules
- Dashboard is read-only — indicators only, no action controls (global rule).

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — 2×2 grid, AutoSizeValue, SpeedCard + colour coding
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — panel sizing (portrait ⅔), passes speedKnots/inZone300
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — speedKnots StateFlow
- `app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt` — GpsFix.speedMps
#### Docs

### gps-loss

#### Todos
- [x] Investigate and fix GPS tracking loss under good reception conditions
- [x] Replace dashboard GPS status bar with compact map-side icon

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt` — LocationManager GPS provider, empty onStatusChanged, no GNSS callback
- `app/src/main/java/ykws/android/maro/data/location/AdaptiveGpsPolicy.kt` — idle cadence may suppress updates
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — GPS collector, empty catch, no stale-fix timeout
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — lifecycle gating, GPS auto-follow LaunchedEffect

#### Docs
- `plans/gps-loss-investigation.md` — investigation report

### track-simplification

#### Todos
- [ ] Create `TrackSimplifier.kt` — Douglas-Peucker (ε=3m) + speed-aware reinsertion (δ=3kn)
- [ ] Plumb `simplifyEnabled`, `simplifyEpsilonM`, `simplifySpeedDeltaKn` through TrackViewModel → TrackRecorder constructor
- [ ] Integrate into `TrackRecorder.finalizeTrack()` — simplify before `repository.save()`
- [ ] Add `tracking.simplifyEnabled`, `tracking.simplifyEpsilonM`, `tracking.simplifySpeedDeltaKn` to `maro.properties`
- [ ] Build (apk-build.bat) + verify simplified track quality (GPX export, QGIS/Google Earth)

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/data/track/TrackSimplifier.kt` — new file
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — integrate in `finalizeTrack()`
- `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt` — read properties, pass to constructor
- `app/src/main/assets/maro.properties` — tunables

#### Docs
- `xTrack/GPS/260622_FEAT_PLN_GPS_track-simplification.md` — design & implementation plan

### troubleshoot-gps-turns

#### Todos
- [ ] Investigate spike rejection lock-in on sharp turns: Gate 2 uses `lastValidCourseDeg`/`lastValidPointLat/Lon` that only update on accepted points — rejected turns lock out all subsequent fixes in new direction
- [ ] Investigate GPS-loss recovery without Gate 0: if no `emitNoLock()` arrives before GPS recovers, `lastHadLock` stays `true`, Gate 0 doesn't fire, recovery fix goes through Gates 1-3 and may be rejected
- [ ] Design fix: rejected points should update reference state (position + course) after N consecutive rejections in same direction, or use a timeout
- [ ] Design fix: add a stale-fix timeout that resets spike rejection state when no fix received for > N seconds
- [ ] Implement fix
- [ ] Build (apk-build.bat) + on-device verification with sharp turns and GPS shadow zones

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — spike rejection v2 (Gates 0-3), `captureAcceptedPoint`, `updateCourseHistory`
- `app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt` — `GpsFix.hasLock`, `emitNoLock`, GNSS status monitoring

#### Docs
- `xTrack/GPS/260622_FEAT_PLN_GPS_troubleshoot-gps-turns.md` — investigation & fix plan

### validation-idle

#### Todos
- [ ] Define what "validation idle" means — GPS stale-fix timeout, no-fix grace period, or demo-mode idle detection
- [ ] Implement idle detection logic (no valid fix for N seconds)
- [ ] Expose idle state via StateFlow in ViewModel
- [ ] Surface idle state in UI (icon, text, or dashboard indicator)
- [ ] Build (apk-build.bat) + on-device verification

#### Rules

#### Key Files

#### Docs

### fix-track-extrapolation

#### Todos
- [x] Investigate: dead-reckoning extrapolated positions leaking into track recording via MapScreen.kt combine flow
- [x] Design fix: prevent extrapolated GpsFix objects from reaching TrackRecorder.addPoint()
- [x] Implement fix
- [ ] Build (apk-build.bat) + on-device verification: stop boat, confirm no extrapolated spikes in recorded track

#### Rules
- Dead reckoning extrapolation is for display only — must not leak into persistent track recording.
- TrackRecorder must only receive real GPS fixes with actual satellite lock.

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — combine flow that feeds GpsFix to TrackRecorder (line 514-536)
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — startDeadReckoning(), _isEstimating, _gpsPosition
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — addPoint() receives GpsFix with hasLock=true

#### Docs
- `xTrack/GPS/260623_FEAT_PLN_GPS_fix-track-extrapolation.md` — analysis & fix plan

### background-recording

#### Todos
- [x] Gate `setGpsActive(false)` on `ON_PAUSE` behind recording state check
- [x] Build (apk-build.bat)
- [ ] On-device verification: start recording, background app, confirm track continues

#### Rules
- GPS must stay active when `TrackRecorderState.ON` — foreground service already keeps process alive.
- `isStopped` gate + dormant GPS cadence still apply in background.

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — ON_PAUSE lifecycle handler (line 779-785)
- `app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt` — foreground service, START_STICKY
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — isStopped gate at addPoint (line 326)

### yarefact

#### Todos
- [x] Phase A — Core Consolidation (C1 + C2): wire `isStopped` into TrackRecorder, remove duplicate policy, gate stale watchdog on IDLE
- [x] Phase B — Pipeline Simplification (C4): create `TrackSample`, remove virtual GpsFix combine
- [x] Phase C — Streaming Optimization (C5): incremental point streaming via SharedFlow + MapScreen polyline append

#### Rules
- Must not change recorded track semantics — consolidation only, no feature addition.
- Phase B must not regress demo mode recording (demo ticker must produce equivalent TrackSample).
- `isStopped` comes from CoastlineViewModel, which already derives it from `_acquisitionMode == IDLE`.

#### Key Files
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — remove duplicate policy, wire isStopped, GpsFix→TrackSample
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — gate stale watchdog on IDLE
- `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt` — wire isStopped, TrackSample
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — remove virtual GpsFix combine, wire TrackSample
- `app/src/main/java/ykws/android/maro/data/track/TrackSample.kt` — new file

#### Docs
- `xTrack/GPS/260625_FEAT_PLN_GPS_yarefact.md` — refined consolidation plan

**yarefact (2026-06-25, Phase A+B+C):** Removed duplicate `AdaptiveGpsPolicy` from TrackRecorder — unified stillness detection via single `isStopped` StateFlow from NavigationViewModel. Reordered `adaptivePolicy.onFix()` before `GpsSignalWatchdog` block, gated both on `_acquisitionMode != IDLE`. Added `feedDemoPosition()` for demo-mode stop detection with periodic 1Hz convergence. `setStoppedSource()` forwarding mechanism on TrackViewModel. Created `TrackSample` data class. Migrated entire recording pipeline from virtual `GpsFix` to `TrackSample`. Phase C: `_newPoint` SharedFlow emits each captured point for incremental polyline append in MapScreen — removed full-list `recordingPoints` copy from `_uiState.update`. Fixes: stale `remember` closure on `appSettings`, periodic demo position feed for stop convergence. Build: ✅.

**background-recording (2026-06-28):** One-line conditional in MapScreen `ON_PAUSE` handler — don't kill GPS (`setGpsActive(false)`) when track recording is active. GPS survives backgrounding while `TrackRecorderState.ON`; foreground service (`TrackRecordingService`, START_STICKY) keeps process alive. `isStopped` gate + dormant GPS cadence still apply. 1 file, 1 line. Build: ✅.

### gps-background

Harden background GPS to match Waze/GMaps best practices + recording-aware exit guard + crash-resilient track recording.


### fix-spike

#### Todos
- [ ] Investigate root cause of duplicate/spike points in recorded tracks (Bad-spikes.gpx: identical lat/lon/speed/course, different timestamps, ≤1ms apart)
- [ ] Design fix: deduplicate consecutive identical fixes in TrackRecorder before capture
- [ ] Implement fix
- [ ] Build (apk-build.bat) + verify with Bad-spikes.gpx scenario

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — addPoint(), spike rejection Gates 0-3
- `Bad-spikes.gpx` — reproduction data: duplicate fixes at 19:29:58.663–.246 (identical pos/speed/course)

#### Docs
- `xTrack/GPS/260714_FEAT_PLN_GPS_fix-spike.md` — root cause analysis & fix plans for all 3 anomaly categories

### checks

#### Todos
- [x] Confirm all spike gates are documented end-to-end (capture → buffer → finalize → save)
- [x] Identify adaptive cadence thresholds governing map update frequency
- [x] Check if dead reckoning interpolation is active during recording
- [x] Determine animateTo vs setCenter for track-capture map updates
- [x] Implement unified continuous dead reckoning + setCenter: _displayPosition StateFlow, 20 Hz DR coroutine, setCenter in MapScreen

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — spike rejection Gates 0-3, addPoint()
- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt` — _displayPosition, continuous DR coroutine (20 Hz), cameraUpdates source
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — setCenter in GPS follow, updated comments

#### Docs
- `xTrack/GPS/260714_FEAT_PLN_GPS_checks.md` — spike gate audit & map smoothness fix design

### back-to-hold

#### Todos
- [x] Evaluate hold-on cases for the GPS auto-follow spring-back (track detail view, marker create/edit, lists, settings, menu)
- [x] Implement hold: add track info drawer + track zoom-to-fit to the drawer gate; suppress active follow while viewing a track
- [ ] On-device verification of pan→view-track and list→view-track flows

#### Rules
- Spring-back stays on hold while viewing/editing any entity (track detail, marker), not only drawers and lists.

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt` — notifyUserInteraction/setDrawerOpen/freezeFollow/recenterNow/startTimer
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — anyDrawerOpen gate (line 1621), trackDrawerState, trackNavigateState

#### Docs
- `xTrack/GPS/260815_FEAT_PLN_GPS_back-to-hold-cases.md` — case evaluation & gap analysis

## Todos
- [ ] back to GPS point → replace delay by swipe of card
- [ ] normalize localisation and fill holes

## Rules
- GPS mode: the GPS position stays at the center of the map.
- GPS mode: when a heading is tracked from GPS movement (sustained ~3 s), rotate the map so the boat always points to the top (course-up).
- GPS mode: when no GPS heading is tracked for 3 s, fall back to the device compass to keep the boat pointing to the top.

## Key Files

## Docs
- `xTrack/UI_Map/FEAT_DOC_UI_Map_marker-sizing.md` — sizing/behaviour of the centred boat marker that GPS positions on the map.
- `docs/MARO_ARCHITECTURE.md` — spatial-engine constraints (Nice–Fréjus bounding box, async/map rules) GPS positions operate within.
- `xTrack/Coastline/FEAT_DOC_Coastline_300m-line-design.md` — the 300 m regulatory band + 5-knot rule the dashboard speed colour-coding enforces.
- `xTrack/GPS/FEAT_DOC_GPS_spike-rejection.md` — Spike rejection architecture: gate pipeline, still-spike fix, land mode auto-detection
- `xTrack/GPS/260714_FEAT_PLN_GPS_still-spike-fix.md` — Still-spike fix plan (lastGenuine anchor, bearing sanity, idle gate)
- `xTrack/GPS/260610_FEAT_PLN_GPS_demo-speed-tuning.md` — Demo mode speed tuning discussion
- `xTrack/GPS/260611_FEAT_PLN_GPS_loss-investigation.md` — GPS tracking loss investigation report
- `xTrack/GPS/260611_FEAT_PLN_GPS_loss-fix-plan.md` — GPS loss fix plan

## Implemented

- **fix-track-extrapolation (2026-06-23)** — gated dead-reckoning positions out of track recording (`isEstimating` guard) → `xTrack/GPS/260623_FEAT_PLN_GPS_fix-track-extrapolation.md`
- **troubleshoot-gps-turns (2026-06-22)** — stale-fix timeout breaks spike-rejection lock-in on sharp turns → `xTrack/GPS/260622_FEAT_PLN_GPS_troubleshoot-gps-turns.md`
- **track-simplification (2026-06-22)** — Douglas-Peucker + speed-aware reinsertion at finalize → `xTrack/GPS/260622_FEAT_PLN_GPS_track-simplification.md`
- **auto-recenter (2026-07-03)** — drawer-aware pan-resume timer + recenter button (🎯)
- **fix-spike (2026-07-12)** — four-gate spike rejection hardening → `xTrack/GPS/260714_FEAT_PLN_GPS_fix-spike.md`
- **checks (2026-07-14)** — continuous dead reckoning + setCenter map smoothness → `xTrack/GPS/260714_FEAT_PLN_GPS_checks.md`
- **poor-reception (2026-07-17)** — 9-item multi-layer poor reception handling → `xTrack/GPS/260717_FEAT_PLN_GPS_poor-reception-handling.md`
- **resolution-display (2026-07-18)** — 7-item track resolution & display → `xTrack/GPS/260718_FEAT_PLN_GPS_resolution-display-plan.md`
- **position-dash (2026-08-08)** — dashboard-vs-marker offset mismatch fix → `xTrack/GPS/260808_FEAT_PLN_GPS_position-dash.md`
- **back-to-hold (2026-08-15)** — spring-back hold gap fix → `xTrack/GPS/260815_FEAT_PLN_GPS_back-to-hold-cases.md`
