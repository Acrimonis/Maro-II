# Hydration: Dashboard — baked 2026-06-10 15:24 UTC

## State
Session completed: "tile subdued font" subfeature done.

### Changes
- Added `DashboardColors.dullAlpha = 0.33f` — unified alpha for all subdued states
- Added `titleColor` param to `DashboardCard` (default textPrimary)
- Zone300Card far-from-zone: grey `zoneNormal` bg, dimmed title+value at 33%, gate = 2× alertDistanceM
- DepthCard "Deep!": grey `zoneNormal` bg, dimmed title+value at 33%
- DepthCard no-data: grey `zoneNormal` bg, dimmed title+value at 33%
- DepthCard on-land & Zone300Card on-land: dimmed title at 33% (was hardcoded textPrimary)
- All empty/dash states: textPrimary @ 33% instead of textMuted solid
- Added `alertDistanceM` param to `DashboardPanel`, wired from `MapScreen`

### Target Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `plans/dashboard-tile-titles.md`

### Remaining Dashboard Todos
- tweak subfeature (dashboard resize, land tile caption)
- readability subfeature (padding/weight/string updates)
