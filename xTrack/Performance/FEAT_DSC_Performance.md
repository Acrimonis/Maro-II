---
name: Performance
status: active
created: 2026-06-07 00:00
modified: 2026-06-10 06:14
---

# Feature: Performance

**Description:**
Battery/performance pass over the GPS plugin. The app is an always-on, on-the-water navigation
tool, so the dominant drains are the GPS radio duty cycle (fixed 1 s / 1 m), an always-powered
rotation-vector compass, and map re-rendering. This feature makes acquisition tunable via presets +
advanced sliders, adds a movement-adaptive idle mode, replaces the animated follow with one capped
applier (user-set refresh ceiling), and registers the compass only when there's no valid GPS course.

## Sections

### gps-refreshing

Bounded `animateTo(600ms)` for smooth GPS-follow glide, with a haversine guard (>3m) to skip animation on sub-threshold noise.

#### Todos
- [ ] On-device visual verification of smoothness at 1s / 2s / 4s intervals

#### Rules
- Animation duration (600ms) must remain < minimum GPS fix interval (1s) to avoid overlapping animations.

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `animateTo` in GPS auto-follow collector (lines ~198-222)

#### Docs
- `xTrack/GPS/260610_FEAT_PLN_GPS_refreshing-discussion.md` — GPS refresh rate: app vs chipset, real perf advantage
- `xTrack/Performance/260610_FEAT_PLN_Performance_animateTo-interaction-analysis.md` — `animateTo` interaction with `mapRefreshFps`

## Implemented

- **tunable-acquisition** — `gpsActiveIntervalSec`/`gpsActiveMinDistanceM` settings + presets (Haute/Équilibrée/Économie) + sliders
- **adaptive-frequency** — `AdaptiveGpsPolicy` (ACTIVE/IDLE window, instant wake); 6 test cases green
- **map-refresh-cap** — `mapRefreshFps` + capped `cameraUpdates` flow; single `setCenter`+`mapOrientation` applier (drop `animateTo`)
- **compass-gating** — `_needsCompass` StateFlow gates the compass on GPS-course absence
- **settings-ui** — "Acquisition GPS" presets + Advanced sliders + "Rendu carte" refresh-rate slider

## Rules
- No new external dependencies — framework `LocationManager`/`SensorManager` + SharedPreferences only.
- No background/foreground-service, no wake locks, no `keepScreenOn` — stays foreground-only.
- No battery-saver master toggle; adaptive frequency and compass-gating are unconditional correct behaviour.
- Zone-300 pulse left unchanged (safety attention cue).
- Build with `apk-build.bat`.

## Key Files
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — GPS/compass orchestration, camera flow
- `app/src/main/java/ykws/android/maro/data/location/` — `GpsLocationSource`, `CompassSource`, `AdaptiveGpsPolicy`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — new tuning fields
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — settings UI + capped follow applier

## Docs
- `xTrack/Performance/FEAT_DOC_Performance_battery-design.md` — battery hotspot analysis, presets/defaults, adaptive-policy contract, refresh-cap mechanism.
- `docs/MARO_ARCHITECTURE.md` — spatial-engine constraints (async render rules).
- `xTrack/GPS/260610_FEAT_PLN_GPS_refreshing-discussion.md` — GPS refresh rate: app vs chipset
- `xTrack/Performance/260614_FEAT_PLN_Performance_drag-stutter-event-chain.md` — Drag stutter complete event chain analysis
- `xTrack/Performance/260614_FEAT_PLN_Performance_drag-stutter-analysis.md` — Drag stutter performance analysis
