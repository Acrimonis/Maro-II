# Context Hydration — GpsPlugin — 2026-06-07

**Active Subfeature:** settings

## State
Démo↔GPS toggle implemented end-to-end (edits complete, not yet built/verified). GPS mode drives
the map center + heading-up rotation (GPS course → compass fallback after 3 s); Démo is free-pan,
north-up. Framework LocationManager + SensorManager only — no new dependencies. Permission
requested on enable; collectors gated on `gpsMode && foreground`. Switch-back hardening landed:
recenter is gated on gpsMode and GPS state (`_gpsPosition`/`_mapBearing`/`lastGpsBearingMs`) resets
on disable. Compass throttled to ~5 Hz via `sample(200 ms)` + 1° threshold.

## Target Files
- `MapScreen.kt` — toggle UI, permission launcher, recenter + rotation LaunchedEffects, lifecycle gate
- `CoastlineViewModel.kt` — gpsPosition/mapBearing, enabled collectors, state reset on disable
- `GpsLocationSource.kt` / `CompassSource.kt` — framework heading sources
- `SettingsManager.kt` — gpsMode persistence

## Next Step
Run `apk-build.bat`, then verify on device: enable GPS (permission), follow + course-up while
moving, compass fallback when stationary, switch back to Démo (north-up + free pan), deny path.
