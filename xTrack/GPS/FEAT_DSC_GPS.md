---
name: GPS
status: active
created: 2026-06-07 00:00
modified: 2026-06-23 08:09
active_subfeature:
---

# Feature: GpsPlugin

**Description:**
Adds a "Source de position" toggle at the top of the settings page switching between Démo
(free-pan, north-up — the legacy behavior) and GPS mode. In GPS mode the device GPS drives the
map center (the screen-centered boat marker stays on the real position) and the map rotates
heading-up from GPS course over ground, falling back to the device compass when stationary.
Location uses the Android framework LocationManager + SensorManager (no Google Play Services);
ACCESS_FINE_LOCATION is requested when the toggle is enabled.

## Subfeatures

### settings  [ ]

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

### dashboard  [ ]

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

### gps-loss  [x]

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

### track-simplification  [ ]

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
- `xTrack/GPS/FEAT_PLN_GPS_track-simplification.md` — design & implementation plan

### troubleshoot-gps-turns  [ ]

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
- `xTrack/GPS/FEAT_PLN_GPS_troubleshoot-gps-turns.md` — investigation & fix plan

### validation-idle  [ ]

#### Todos
- [ ] Define what "validation idle" means — GPS stale-fix timeout, no-fix grace period, or demo-mode idle detection
- [ ] Implement idle detection logic (no valid fix for N seconds)
- [ ] Expose idle state via StateFlow in ViewModel
- [ ] Surface idle state in UI (icon, text, or dashboard indicator)
- [ ] Build (apk-build.bat) + on-device verification

#### Rules

#### Key Files

#### Docs

### fix-track-extrapolation  [x]

#### Todos
- [ ] Investigate: dead-reckoning extrapolated positions leaking into track recording via MapScreen.kt combine flow
- [ ] Design fix: prevent extrapolated GpsFix objects from reaching TrackRecorder.addPoint()
- [ ] Implement fix
- [ ] Build (apk-build.bat) + on-device verification: stop boat, confirm no extrapolated spikes in recorded track

#### Rules
- Dead reckoning extrapolation is for display only — must not leak into persistent track recording.
- TrackRecorder must only receive real GPS fixes with actual satellite lock.

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — combine flow that feeds GpsFix to TrackRecorder (line 514-536)
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — startDeadReckoning(), _isEstimating, _gpsPosition
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — addPoint() receives GpsFix with hasLock=true

#### Docs
- `xTrack/GPS/FEAT_PLN_GPS_fix-track-extrapolation.md` — analysis & fix plan

## Todos


## Rules
- GPS mode: the GPS position stays at the center of the map.
- GPS mode: when a heading is tracked from GPS movement (sustained ~3 s), rotate the map so the boat always points to the top (course-up).
- GPS mode: when no GPS heading is tracked for 3 s, fall back to the device compass to keep the boat pointing to the top.

## Key Files

## Docs
- `xTrack/UI_Map/FEAT_DOC_UI_Map_marker-sizing.md` — sizing/behaviour of the centred boat marker that GPS positions on the map.
- `docs/MARO_ARCHITECTURE.md` — spatial-engine constraints (Nice–Fréjus bounding box, async/map rules) GPS positions operate within.
- `xTrack/Coastline/FEAT_DOC_Coastline_300m-line-design.md` — the 300 m regulatory band + 5-knot rule the dashboard speed colour-coding enforces.
- `xTrack/GPS/FEAT_PLN_GPS_demo-speed-tuning.md` — Demo mode speed tuning discussion
- `xTrack/GPS/FEAT_PLN_GPS_loss-investigation.md` — GPS tracking loss investigation report
- `xTrack/GPS/FEAT_PLN_GPS_loss-fix-plan.md` — GPS loss fix plan

## Implemented

**fix-track-extrapolation (2026-06-23):** Gated dead-reckoning extrapolated positions from leaking into track recording. Added `isEstimating` to `combine` inputs in `MapScreen.kt:514`, guard `if (estimating) return@combine null` before `GpsFix` creation, plus `filterNotNull()` downstream. Dead reckoning updates `_gpsPosition` for display only; track recording now only receives real GPS fixes with actual satellite lock. 1 file, 3 lines, no new dependencies. Build: ✅.

**troubleshoot-gps-turns (2026-06-22):** Spike rejection lock-in fix — added `STALE_FIX_TIMEOUT_MS` (10s), `lastAcceptedTimeMs` field, timeout check before Gates 0-3 in `TrackRecorder.addPoint()`. Breaks lock-in on sharp turns (stale `lastValidCourseDeg`/`lastValidPoint`) and silent GPS recovery (no `emitNoLock` → Gate 0 missed). BuildConfig:`tracking.checkpointIntervalMs=30000`, `tracking.staleFixTimeoutMs=10000` in `maro.properties`.

**track-simplification (2026-06-22):** Two-pass track simplification at finalize. `TrackSimplifier.kt`: Douglas-Peucker spatial (ε=3m) + speed-aware reinsertion (δ=3kn). Integrated in `TrackRecorder.finalizeTrack()` before save. AppSettings: `trackSimplifyEnabled`, `trackSimplifyEpsilonM`, `trackSimplifySpeedDeltaKn` with SharedPreferences persistence. Tunables in `maro.properties`. Expected 92-99% point reduction. Build: ✅.
