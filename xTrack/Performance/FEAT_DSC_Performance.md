---
name: Performance
status: active
created: 2026-06-07 00:00
modified: 2026-09-12 09:40
---

# Feature: Performance

**Description:**
Battery/performance pass over the GPS plugin. The app is an always-on, on-the-water navigation
tool, so the dominant drains are the GPS radio duty cycle (fixed 1 s / 1 m), an always-powered
rotation-vector compass, and map re-rendering. This feature makes acquisition tunable via presets +
advanced sliders, adds a movement-adaptive idle mode, replaces the animated follow with one capped
applier (user-set refresh ceiling), and registers the compass only when there's no valid GPS course.
It also **owns power management**: the screen-hold policy (`PowerPolicy`), its Android keeper
(`PowerKeeper`), and the battery-optimization prompt trigger.

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

### power-management

Screen-hold policy + keeper — Phase 1 of `260912_FEAT_PLN_Performance_power-management-centralization.md`.
Two **independent** channels: the screen flag (window-scoped, front-only by construction) and keep-alive
(service-scoped).

#### Todos
- [ ] Device verification (plan §11): grace release with the slider at its 1-minute minimum, interaction reset at ~30 s, speed gate in demo mode, master-off regression, recording floor with the app backgrounded
- [ ] Close the exemption-query gap: `MapScreen.kt` gates on `batteryOptimizationPrompted` alone while `MainActivity.kt` also checks `PowerKeeper.isExemptFromBatteryOptimizations()`, so an already-exempt user can still be prompted on the MapScreen path
- [ ] Remove the two dead imports in `MainActivity.kt` (`SharedPreferences`, `PowerManager`)
- [ ] Ticker nit: `PowerKeeper.start()` recomputes every 5 s even once the screen has been released — gate it on `_state.value.screenOn` as well
- [ ] Phases 2–4 are gated: Tasker reconciliation, then the conditional service, then passive markers (4a) and the zone latch + notification segment (4b)

#### Rules
- The two channels never merge: `FLAG_KEEP_SCREEN_ON` is window-scoped and cannot act in the background; keep-alive is the service's job.
- `data/power/` must not depend on `ui/`. Speed arrives as a pushed input, never as a third GPS subscription.
- The window flag keeps exactly **one** mutation point (the Activity applier) — that is what avoids the Android 16 false→true reset regression.
- A keep-alive reason can only *sustain* a running service, never start one — detecting the condition requires GPS.
- Numbers live by nature: the user's chosen values in `SettingsManager` prefs, while the bounds, the default and the developer constants come from `maro.properties` via `AppConfig` (`power.screen.*`). `PowerPolicy` references neither — every number arrives through its inputs.

#### Key Files
- `app/src/main/java/ykws/android/maro/data/power/PowerPolicy.kt` — framework-free decision
- `app/src/main/java/ykws/android/maro/data/power/PowerKeeper.kt` — input gathering, `StateFlow<PowerState>`, grace ticker, exemption query
- `app/src/test/java/ykws/android/maro/data/power/PowerPolicyTest.kt` — 17 cases
- `app/src/main/java/ykws/android/maro/MainActivity.kt` — window-flag applier + `dispatchTouchEvent` touch feed

#### Docs
- `xTrack/Performance/260912_FEAT_PLN_Performance_power-management-centralization.md` — plan of record: order of work and open items

## Implemented

- **tunable-acquisition** — `gpsActiveIntervalSec`/`gpsActiveMinDistanceM` settings + presets (Haute/Équilibrée/Économie) + sliders
- **adaptive-frequency** — `AdaptiveGpsPolicy` (ACTIVE/IDLE window, instant wake); 6 test cases green
- **map-refresh-cap** — `mapRefreshFps` + capped `cameraUpdates` flow; single `setCenter`+`mapOrientation` applier (drop `animateTo`)
- **compass-gating** — `_needsCompass` StateFlow gates the compass on GPS-course absence
- **settings-ui** — "Acquisition GPS" presets + Advanced sliders + "Rendu carte" refresh-rate slider
- **power-management Phase 1 (2026-09-12)** — centralised the screen-hold decision into `data/power/`: `PowerPolicy` (framework-free, **stateless** — speed gate above 1 kn **or** within grace of the last touch; unknown speed holds, stale speed releases; additive master-vs-gate semantics) + `PowerKeeper` (`StateFlow<PowerState>`, pushed settings/speed/touch inputs, 5 s grace ticker, recording floor, exemption query). The window flag is now driven by the keeper from a single mutation point, and `dispatchTouchEvent` feeds the interaction timestamp. Settings → System → Screen gained a renamed toggle ("Don't lock the phone while the app is open", French ambiguity removed) plus an expander holding the movement gate, threshold and grace sliders. Phase 0 corrected the stale power rule and the `keepScreenOn` default contradiction (`false`, no behaviour change). Phase 1c consolidated the battery-optimization prompt: the flag moved into `AppSettings` with a legacy migration, and the trigger moved to recording start with recovery kept as a secondary. 17 unit tests green; `apk-build.bat` SUCCESS → `xTrack/Performance/260912_FEAT_PLN_Performance_power-management-centralization.md`

## Rules
- No new external dependencies — framework `LocationManager`/`SensorManager` + SharedPreferences only.
- **Power posture, as shipped today:** one foreground service (`TrackRecordingService`, manifest type
  `location`) started unconditionally when the app opens, the battery-optimization exemption prompt, and
  a `keepScreenOn` window flag. **No wake locks, and no `WAKE_LOCK` permission in the manifest** — the
  foreground service plus the exemption is the sanctioned mechanism; revisit only with field evidence of
  GPS gaps while the screen is off. (This rule previously claimed the app stayed foreground-only with no
  service and no `keepScreenOn`. That stopped being true; corrected 2026-09-12. The *conditional* service
  is a later phase and is deliberately not described here yet.)
- No battery-saver master toggle; adaptive frequency and compass-gating are unconditional correct behaviour.
- Zone-300 pulse left unchanged (safety attention cue).
- Build with `apk-build.bat`.

## Key Files
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — GPS/compass orchestration, camera flow
- `app/src/main/java/ykws/android/maro/data/location/` — `GpsLocationSource`, `CompassSource`, `AdaptiveGpsPolicy`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — new tuning fields
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — settings UI + capped follow applier
- `app/src/main/java/ykws/android/maro/data/power/PowerPolicy.kt` — power decision, framework-free
- `app/src/main/java/ykws/android/maro/data/power/PowerKeeper.kt` — power keeper (screen channel, phase 1)

## Docs
- `xTrack/Performance/FEAT_DOC_Performance_battery-design.md` — battery hotspot analysis, presets/defaults, adaptive-policy contract, refresh-cap mechanism.
- `xTrack/Performance/260912_FEAT_PLN_Performance_power-management-centralization.md` — power management plan of record: two-channel model, order of work, open items.
- `docs/MARO_ARCHITECTURE.md` — spatial-engine constraints (async render rules).
- `xTrack/GPS/260610_FEAT_PLN_GPS_refreshing-discussion.md` — GPS refresh rate: app vs chipset
- `xTrack/Performance/260614_FEAT_PLN_Performance_drag-stutter-event-chain.md` — Drag stutter complete event chain analysis
- `xTrack/Performance/260614_FEAT_PLN_Performance_drag-stutter-analysis.md` — Drag stutter performance analysis
