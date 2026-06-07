# Context Hydration — GpsPlugin — 2026-06-07

**Active Subfeature:** settings

## State
GPS feature complete pending build/verify. **settings** sub: Démo↔GPS toggle (GPS-driven center,
heading-up rotation GPS-course→compass after 3 s, permission flow, foreground gate, user-pan pauses
auto-follow then recenters, persistence on exit) plus an **adjustable recenter-delay slider (1–10 s,
default 5)** wired to `notifyUserInteraction`. **dashboard** sub: 2×2 grid (Distance, Zone 300 m,
Profondeur, Vitesse), values auto-sized to fill cells; GPS speed in knots ("—" in demo); speed
colour-coded inside the 300 m zone (<5 green / 5–10 orange / >10 red); portrait panel = ⅔ short side.
Docs attached: MARKER_SIZING, MARO_ARCHITECTURE, 300MLineDesign.

## Target Files
- `DashboardPanel.kt` — 2×2 grid, AutoSizeValue, SpeedCard + colour coding
- `MapScreen.kt` — panel sizing, toggle + recenter-delay slider, recenter/rotation/touch effects
- `CoastlineViewModel.kt` — gpsPosition/mapBearing/speedKnots + collectors; recenter delay from settings
- `SettingsManager.kt` — gpsMode + recenterDelaySeconds persistence
- `GpsLocationSource.kt` / `CompassSource.kt` — framework heading/speed sources

## Next Step
Run `apk-build.bat`, then verify on device: GPS follow + heading-up, compass fallback, demo north-up,
2×2 portrait sizing/auto-fit, speed colour thresholds in/out of the 300 m zone, recenter-delay slider.
