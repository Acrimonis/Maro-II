---
name: GPS
status: active
created: 2026-06-07 00:00
modified: 2026-06-07 00:00
active_subfeature: settings
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
- `plans/demo-speed-tuning.md` — Demo mode speed tuning discussion
