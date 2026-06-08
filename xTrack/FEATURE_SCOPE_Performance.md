---
name: Performance
status: active
created: 2026-06-07 00:00
modified: 2026-06-07 00:00
active_subfeature: none
subs_total: 5
subs_done: 4
one_liner: Cut battery drain by making GPS acquisition tunable + movement-adaptive, capping map re-render rate, and powering the compass only when needed.
---

# Feature: Performance

**Description:**
Battery/performance pass over the GPS plugin. The app is an always-on, on-the-water navigation
tool, so the dominant drains are the GPS radio duty cycle (fixed 1 s / 1 m), an always-powered
rotation-vector compass, and map re-rendering (GPS-follow uses `animateTo`, which animates each
recenter at ~60 fps instead of a single repaint per fix). This feature: (1) makes acquisition
tunable via presets + advanced sliders, (2) adds an always-on movement-adaptive idle mode that
drops the fix rate when effectively stationary and wakes instantly on movement, (3) replaces the
animated follow with one capped `setCenter`+`mapOrientation` applier (user-set refresh ceiling),
and (4) registers the compass only when there's no valid GPS course. No master toggle — adaptive
and compass-gating are unconditional correct behaviour. Framework `LocationManager`/`SensorManager`
+ SharedPreferences only; no new dependencies; stays foreground-only. Full design in
`docs/PerformanceBatteryDesign.md`.

## Subfeatures

### tunable-acquisition  [x]

#### Todos
- [x] Add `gpsActiveIntervalSec` (Int=2) + `gpsActiveMinDistanceM` (Float=5f) to `AppSettings` + load/update/KEY_*
- [x] Thread settings into the GPS subscription: `GpsParams` stream feeding `flatMapLatest` (CoastlineViewModel)
- [x] Acquisition presets as setter shortcuts — Haute 1 s/1 m, Équilibrée 2 s/5 m, Économie 4 s/10 m
- [x] Advanced sliders: Intervalle GPS 1–10 s, Distance minimale 1–25 m

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — new fields
- `app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt` — `locationUpdates(minIntervalMs, minDistanceM)` (params already exist)
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — `GpsParams` upstream

#### Docs

### adaptive-frequency  [x]

#### Todos
- [x] New `AdaptiveGpsPolicy.kt` (pure Kotlin): anchor-displacement window → ACTIVE/IDLE, instant wake on speed/jump/drift
- [x] Settings `adaptiveWindowSec`=30, `adaptiveDistanceM`=20, `adaptiveIdleIntervalSec`=6 (+ Advanced sliders 15–60 s / 10–30 m / 4–15 s)
- [x] Wire `_acquisitionMode` into `GpsParams`; update mode in GPS `onEach`; policy lives OUTSIDE `flatMapLatest`
- [x] Unit test `AdaptiveGpsPolicyTest` — 6 cases green (ACTIVE→IDLE after full window, instant wake, re-anchor on drift, reset)

#### Rules
- Bias to responsiveness: drop to IDLE only after a full window below threshold; wake to ACTIVE on the first qualifying fix.

#### Key Files
- `app/src/main/java/ykws/android/maro/data/location/AdaptiveGpsPolicy.kt` — **new**
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — `_acquisitionMode` + wiring
- `app/src/main/java/ykws/android/maro/spatial/SpatialOperations.kt` — reuse great-circle distance helper
- `app/src/test/java/ykws/android/maro/data/location/AdaptiveGpsPolicyTest.kt` — **new**

#### Docs

### map-refresh-cap  [x]

#### Todos
- [x] Setting `mapRefreshFps` (Int=25, range 5–50) + "Rendu carte" slider with inline battery hint
- [x] `cameraUpdates` capped flow in CoastlineViewModel (combine pos+bearing, `sample(1000/fps)`)
- [x] Replace the two follow `LaunchedEffect`s with ONE applier: `setCenter` + `mapOrientation` + `invalidate` (drop `animateTo`)
- [x] Keep manual gestures full-rate; honour `autoFollowSuppressed`; keep 1° internal jitter deadband

#### Rules
- Cap applies to the GPS auto-follow path only — manual pinch/pan/fling stay full native rate.

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — `cameraUpdates` flow
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — single capped applier (replaces `:170-190`)

#### Docs

### compass-gating  [x]

#### Todos
- [x] `CompassSource.azimuthUpdates(samplingPeriodUs = SensorManager.SENSOR_DELAY_NORMAL)` default (16 Hz → ~5 Hz)
- [x] `_needsCompass` StateFlow; gate compass collector on `enabled && _needsCompass`
- [x] Drive `_needsCompass` from GPS-course presence; the 3 s fallback provides the on→off hysteresis

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/data/location/CompassSource.kt` — sample-rate param
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — `_needsCompass` gate

#### Docs

### settings-ui  [ ]

#### Todos
- [x] New "Acquisition GPS" section: preset row (new `SettingsPresetRow`) + Advanced expander (interval/distance + adaptive thresholds)
- [x] New "Rendu carte" section: refresh-rate slider 5–50 fps with battery hint
- [x] Reuse `SectionHeader` / `SettingsToggleRow` / `SettingsSliderRow`
- [x] Build green (`assembleDebug` + `testDebugUnitTest`); ⏳ on-device verification of all flows pending (needs a device)

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `SettingsOverlay` sections + `SettingsPresetRow`

#### Docs

## Todos

## Rules
- No new external dependencies — framework `LocationManager`/`SensorManager` + SharedPreferences only (AGENTS.md §4).
- No background/foreground-service, no wake locks, no `keepScreenOn` — stays foreground-only.
- No battery-saver master toggle; adaptive frequency and compass-gating are unconditional correct behaviour.
- Zone-300 pulse left unchanged (safety attention cue).
- Build with `apk-build.bat`.

## Key Files
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — GPS/compass orchestration, camera flow
- `app/src/main/java/ykws/android/maro/data/location/` — `GpsLocationSource`, `CompassSource`, new `AdaptiveGpsPolicy`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — new tuning fields
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — settings UI + capped follow applier

## Docs
- `docs/PerformanceBatteryDesign.md` — battery hotspot analysis, presets/defaults, adaptive-policy contract, refresh-cap mechanism.
- `docs/MARO_ARCHITECTURE.md` — spatial-engine constraints (async render rules) the map/refresh changes operate within.
