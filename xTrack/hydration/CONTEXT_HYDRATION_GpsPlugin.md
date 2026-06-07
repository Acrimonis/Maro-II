# Context Hydration — GpsPlugin — 2026-06-07

**Active Subfeature:** dashboard

## State
GPS feature complete pending build/verify. **settings** sub: Démo↔GPS toggle — GPS-driven map
center, heading-up rotation (GPS course → compass after 3 s), permission flow, foreground gate,
user-pan pauses auto-follow 5 s, persistence on exit. **dashboard** sub: 2×2 grid (Distance,
Zone 300 m, Profondeur, Vitesse) with values auto-sized to fill each cell; GPS speed in knots
("—" in demo); speed colour-coded inside the 300 m zone (<5 green / 5–10 orange / >10 red);
portrait panel height = ⅔ of the short side (mirrors landscape width).

## Target Files
- `DashboardPanel.kt` — 2×2 grid, AutoSizeValue, SpeedCard + colour coding
- `MapScreen.kt` — panel sizing, toggle UI, recenter/rotation/touch effects
- `CoastlineViewModel.kt` — gpsPosition/mapBearing/speedKnots StateFlows + collectors
- `GpsLocationSource.kt` / `CompassSource.kt` — framework heading/speed sources

## Next Step
Run `apk-build.bat`, then verify on device: GPS follow + heading-up, compass fallback, demo
north-up, 2×2 portrait sizing/auto-fit, speed colour thresholds in/out of the 300 m zone.
