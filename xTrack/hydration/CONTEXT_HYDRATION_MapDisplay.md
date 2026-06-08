# Context Hydration — MapDisplay

**Last Bake:** 2026-06-08 19:23 (UTC)
**Branch:** feature/layout-lowdepth (off origin/develop)
**Active Subfeature:** layer-lowdepth

## State
Low-depth warning overlay shipped, building green (assembleDebug). It paints every depth
cell shallower than a **configurable threshold** (default 1.5 m; Settings → Display slider
0.5–5.0 m, persisted) as a bright-magenta `GroundOverlay` stacked above the depth colour
raster, with its own visibility toggle. "Keep phone on" moved to the top of Power saving.
Found + fixed a depth-overlay **Mercator offset** (equirectangular bitmap stretched on a
Web-Mercator map → ~43 m landward bow mid-grid): both depth + warning rasters now draw via
`addBandedOverlay()` as 8 latitude strips → sub-metre. Banding + threshold committed on
feature/layout-lowdepth.

## Next step (immediate)
Mask the warning to **water only**: in `LowDepthWarningBitmap.build`, for each <threshold
cell, call a `isWater(lat,lon)` predicate and skip land/island cells. Wire
`CoastlineViewModel.isOnWater` (→ `CoastlineSpatialIndex.isWater`, ~0.1 ms/call) in from
MapScreen's `produceState`; use `grid.cellCenterLat/Lon`; re-key the build on coastline-ready.
Then on-device verify: offset gone, no pink on land, toggle + threshold persist.

## Target files
- `ui/map/LowDepthWarningBitmap.kt` — add `isWater` predicate param (default `{_,_->true}`)
- `ui/map/MapScreen.kt` — pass `viewModel::isOnWater`; `addBandedOverlay`; produceState keys
- `spatial/CoastlineSpatialIndex.kt:403` `isWater()`; `ui/map/CoastlineViewModel.kt:561` `isOnWater()`
- `data/settings/SettingsManager.kt` — `lowDepthWarningVisible` / `lowDepthWarningMaxM` (done)
