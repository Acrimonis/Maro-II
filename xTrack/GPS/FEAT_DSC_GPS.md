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

Position source toggle (Démo/GPS) at top of settings; `gpsMode` persisted; GPS drives map center + heading-up rotation (course, compass fallback after 3 s); runtime permission + foreground gate; compass rate-limited (200 ms + 1° jitter); reset on disable; recenter delay slider (1–10 s).

#### Todos
- [ ] Build (apk-build.bat) + on-device verification of all four flows

#### Key Files
- `app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt` — LocationManager → Flow<GpsFix>
- `app/src/main/java/ykws/android/maro/data/location/CompassSource.kt` — rotation-vector azimuth Flow
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — gpsPosition/mapBearing StateFlows
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — toggle UI, permission launcher, recenter/rotation
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — gpsMode persistence

### dashboard

2×2 dashboard grid (Distance, Zone 300 m, Profondeur, Vitesse) with auto-sized values; GPS speed card in knots ("—" in demo) with speed-colour coding in the 300 m zone (<5 kn green, 5–10 orange, >10 red).

#### Todos
- [ ] Build (apk-build.bat) + on-device verification (portrait sizing, auto-fit, colour thresholds)

#### Rules
- Dashboard is read-only — indicators only, no action controls (global rule).

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — 2×2 grid, AutoSizeValue, SpeedCard
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — panel sizing, passes speedKnots/inZone300
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — speedKnots StateFlow
- `app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt` — GpsFix.speedMps

### validation-idle

#### Todos
- [ ] Define what "validation idle" means — GPS stale-fix timeout, no-fix grace period, or demo-mode idle detection
- [ ] Implement idle detection logic (no valid fix for N seconds)
- [ ] Expose idle state via StateFlow in ViewModel
- [ ] Surface idle state in UI (icon, text, or dashboard indicator)
- [ ] Build (apk-build.bat) + on-device verification

#### Key Files

### fix-track-extrapolation

Dead-reckoning extrapolation is display-only — gated out of persistent track recording so TrackRecorder only receives real fixes with satellite lock.

#### Todos
- [ ] Build (apk-build.bat) + on-device verification: stop boat, confirm no extrapolated spikes in recorded track

#### Rules
- Dead reckoning extrapolation is for display only — must not leak into persistent track recording.
- TrackRecorder must only receive real GPS fixes with actual satellite lock.

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — combine flow feeding GpsFix to TrackRecorder (line 514-536)
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — startDeadReckoning(), _isEstimating, _gpsPosition
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — addPoint() receives GpsFix with hasLock=true

#### Docs
- `xTrack/GPS/260623_FEAT_PLN_GPS_fix-track-extrapolation.md` — analysis & fix plan

### background-recording

GPS stays active while recording in background (ON_PAUSE gate behind recording state; foreground service keeps process alive).

#### Todos
- [ ] On-device verification: start recording, background app, confirm track continues

#### Rules
- GPS must stay active when `TrackRecorderState.ON` — foreground service already keeps process alive.
- `isStopped` gate + dormant GPS cadence still apply in background.

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — ON_PAUSE lifecycle handler (line 779-785)
- `app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt` — foreground service, START_STICKY
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — isStopped gate at addPoint (line 326)

### back-to-hold

Spring-back hold extended to track detail + track zoom-to-fit; auto-follow suppressed while viewing a track.

#### Todos
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
- **gps-loss** — GPS tracking-loss fix + compact map-side status icon → `xTrack/GPS/260611_FEAT_PLN_GPS_loss-fix-plan.md`
- **yarefact** — TrackRecorder consolidation (isStopped, TrackSample, streaming via SharedFlow) → `xTrack/GPS/260625_FEAT_PLN_GPS_yarefact.md`
- **auto-recenter (2026-07-03)** — drawer-aware pan-resume timer + recenter button (🎯)
- **fix-spike (2026-07-12)** — four-gate spike rejection hardening → `xTrack/GPS/260714_FEAT_PLN_GPS_fix-spike.md`
- **checks (2026-07-14)** — continuous dead reckoning + setCenter map smoothness → `xTrack/GPS/260714_FEAT_PLN_GPS_checks.md`
- **poor-reception (2026-07-17)** — 9-item multi-layer poor reception handling → `xTrack/GPS/260717_FEAT_PLN_GPS_poor-reception-handling.md`
- **resolution-display (2026-07-18)** — 7-item track resolution & display → `xTrack/GPS/260718_FEAT_PLN_GPS_resolution-display-plan.md`
- **position-dash (2026-08-08)** — dashboard-vs-marker offset mismatch fix → `xTrack/GPS/260808_FEAT_PLN_GPS_position-dash.md`
- **back-to-hold (2026-08-15)** — spring-back hold gap fix → `xTrack/GPS/260815_FEAT_PLN_GPS_back-to-hold-cases.md`
