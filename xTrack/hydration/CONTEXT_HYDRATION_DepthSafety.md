# DepthSafety — Session Hydration

**Last Bake:** 2026-06-09 12:01 UTC

## State Summary

DepthSafety is active (5/6 subfeatures done). Caching subfeature implemented: RawBuf disk cache with 4-step pipeline, sequential hide/show regeneration, persisted per-layer checkboxes, silent lazy-init on cold start.

## Active Work: Caching Subfeature — DONE

- `RasterCache` object with `Step` enum (GRID, ISOBATH, DEPTH_COLOUR, LOW_DEPTH_WARNING)
- RawBuf format: IntArray → ByteBuffer → FileChannel, ~78ms colour / ~52ms warning reads
- `generateRasterLayers()` self-contained 4-step pipeline with intra-step onProgress callbacks
- Sequential generation: hides layer before regenerating, shows immediately after
- `DepthViewModel.generatingStep` + `rasterCacheVersion` StateFlows for hide/show
- Settings: 4 persisted checkboxes (regenGrid/Isobaths/Colour/Warning) + "Regenerate" button
- Silent lazy-init LaunchedEffect populates cache on cold start without LoadingOverlay
- Live-build produceState blocks skip build when cache exists
- Warning layer no longer blocked on coastlineReady for cached reads
- Coastline loading overlay removed

## Remaining

- `danger-alert` subfeature (B4: pulsing alarm + sound stub)
- On-device verify + measure cold-start improvement after cache

## Target Files
- `data/depth/RasterCache.kt` — RawBuf disk I/O
- `data/model/RasterProgress.kt` — weighted progress
- `data/settings/SettingsManager.kt` — 4 regen flags
- `data/depth/DepthRepository.kt` — loadGridFromAssets()
- `ui/map/DepthViewModel.kt` — generateRasterLayers, generatingStep, rasterCacheVersion
- `ui/map/DepthBitmap.kt` — onProgress callback
- `ui/map/LowDepthWarningBitmap.kt` — onProgress callback
- `ui/map/MapScreen.kt` — silent lazy-init, hide/show, 4 checkboxes, cache-guarded live builds
