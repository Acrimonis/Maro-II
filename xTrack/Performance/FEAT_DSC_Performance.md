---
name: Performance
status: active
created: 2026-06-07 00:00
modified: 2026-09-12 10:57
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
- [ ] Device verification (plan §11): grace release with the slider at its 1-minute minimum, interaction reset at ~30 s, demo mode grace-governed, recording floor with the app backgrounded, plus the master-off check that isolates an external screen-awake source (Developer options "Stay awake while charging") from our own flag
- [ ] Phases 2–4 are gated: Tasker reconciliation, then the conditional service, then passive markers (4a) and the zone latch + notification segment (4b)

#### Rules
- The two channels never merge: `FLAG_KEEP_SCREEN_ON` is window-scoped and cannot act in the background; keep-alive is the service's job.
- `data/power/` must not depend on `ui/`. Speed arrives as a pushed input, never as a third GPS subscription.
- The window flag keeps exactly **one** mutation point (the Activity applier) — that is what avoids the Android 16 false→true reset regression.
- A keep-alive reason can only *sustain* a running service, never start one — detecting the condition requires GPS.
- Numbers live by nature: the user's chosen values in `SettingsManager` prefs, while the bounds, the default and the developer constants come from `maro.properties` via `AppConfig` (`power.screen.*`). `PowerPolicy` references neither — every number arrives through its inputs.
- **A push is not a reading.** Freshness must be sourced from the data — a required `isNewReading` flag tracked by `SpeedFreshness` — never inferred from a call arriving, or a re-published cached speed holds the screen forever. Pinned by `SpeedFreshnessTest`.
- Demo mode is grace-governed: it has no speed truth, so pan speed is not treated as motion.

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
- **power-management freshness fix (2026-09-12)** — a push is no longer treated as a reading: `PowerKeeper.onSpeed` requires an `isNewReading` flag, new `SpeedFreshness` tracks the newest genuine reading, GPS mode pairs the speed with the app's `gpsStale` signal so a lost fix ages out at the staleness bound, and demo mode became **grace-governed** (pan speed is not motion). 6 regression cases in `SpeedFreshnessTest`; build SUCCESS → `xTrack/Performance/260912_FEAT_PLN_Performance_power-management-centralization.md` (decision 15)
- **power-management cleanup + review findings (2026-09-12)** — Ask-reviewed pass: the grace KDoc corrected to 1–15, the grace ticker now re-evaluates only while a hold is live (so its KDoc finally matches the code), a dangling KDoc sentence repaired, dead imports removed from `MainActivity` and `PowerKeeper`, and the battery-exemption question consolidated into `data/power/BatteryExemption` — one `shouldPrompt(context, settings)` predicate shared by all four trigger sites, which also stops the MapScreen path prompting a user who is already exempt. APK SUCCESS
- **power-management Phase 1 (2026-09-12)** — centralised the screen-hold decision into `data/power/`: `PowerPolicy` (framework-free, **stateless** — speed gate above 1 kn **or** within grace of the last touch; unknown speed holds, stale speed releases; additive master-vs-gate semantics) + `PowerKeeper` (`StateFlow<PowerState>`, pushed settings/speed/touch inputs, 5 s grace ticker, recording floor, exemption query). The window flag is now driven by the keeper from a single mutation point, and `dispatchTouchEvent` feeds the interaction timestamp. Settings → System → Screen gained a renamed toggle ("Don't lock the phone while the app is open", French ambiguity removed) plus an expander holding the movement gate, threshold and grace sliders. Phase 0 corrected the stale power rule and the `keepScreenOn` default contradiction (`false`, no behaviour change). Phase 1c consolidated the battery-optimization prompt: the flag moved into `AppSettings` with a legacy migration, and the trigger moved to recording start with recovery kept as a secondary. 17 unit tests green; `apk-build.bat` SUCCESS → `xTrack/Performance/260912_FEAT_PLN_Performance_power-management-centralization.md`

- **power-management review findings (2026-09-12)** — second pass over the Ask findings: the keeper's KDoc now states it **delegates** the exemption query to `BatteryExemption` and keeps the method as the phase-3 service-lifecycle seam (decided **keep**, since deleting it would strand the keeper's `Context`), `MapScreen` imports the predicate instead of qualifying it twice, and plan §4.1/§4.2 were aligned so the same ownership split is described in the same words in all three places. No behaviour change; APK SUCCESS

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
