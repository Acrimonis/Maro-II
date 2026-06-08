# Context Hydration — MapDisplay

**Last Bake:** 2026-06-08 19:34 (UTC)
**Branch:** feature/layout-lowdepth (off origin/develop)
**Active Subfeature:** layer-lowdepth

## State
Low-depth warning overlay complete, building green (assembleDebug). Paints every **water**
cell shallower than a configurable threshold (default 1.5 m; Settings → Display slider 0.5–5.0 m,
persisted) as a bright-magenta `GroundOverlay` above the depth raster, own visibility toggle.
"Keep phone on" moved to top of Power saving. Depth-overlay **Mercator offset** fixed by
latitude-banding both depth + warning rasters via `addBandedOverlay()` (8 strips, ~43 m→<1 m).
Warning now **masked to water only**: `LowDepthWarningBitmap.build` takes an `isWater` predicate
(fed `viewModel::isOnWater` → `CoastlineSpatialIndex.isWater`), skipping land/island cells; the
overlay rebuilds when the coastline becomes Ready. All committed+pushed on feature/layout-lowdepth.

## Next step
On-device verification only (no code pending): deploy (`.\apk-deploy.bat`), zoom to the Lérins
islands / a shallow near-shore spot and confirm (a) the shallow band hugs the real waterline
(offset gone), (b) no pink on land/islands, (c) toggle + depth slider persist across restart.
If a small *uniform* shift remains after banding, that's the separate datum component — measure
edge-vs-middle to confirm.

## Target files
- `ui/map/LowDepthWarningBitmap.kt` — `build(grid, maxDepthM, isWater)` (done)
- `ui/map/MapScreen.kt` — `addBandedOverlay`; warning `produceState` passes `viewModel::isOnWater`, keyed on coastline-ready (done)
- `spatial/CoastlineSpatialIndex.kt:403` `isWater()`; `ui/map/CoastlineViewModel.kt:561` `isOnWater()`
