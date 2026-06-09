# Regenerate Layers — Full Rework Plan

<!-- scope: feature -->

## Requirements

1. **4 checkboxes** in settings: Depth grid, Isobath contours, Depth colour map, Low-depth warning overlay
2. **Persisted** checkbox states (survive app restart)
3. **No lazy auto-trigger** — only the settings "Regenerate" button triggers generation
4. **Sequential generation** with hide-before-show:
   - Before generating layer N: hide that layer's bitmap
   - After generating layer N: show new bitmap immediately
   - Then proceed to layer N+1

## Architecture Changes

### 1. Expand `RasterCache.Layer` → `PipelineStep`

Add GRID and ISOBATH steps alongside DEPTH_COLOUR, LOW_DEPTH_WARNING. Grid/isobaths aren't cached to disk but are part of the pipeline.

### 2. SettingsManager — 4 persisted flags

```kotlin
data class AppSettings(
    ...
    val regenGrid: Boolean = true,
    val regenIsobaths: Boolean = true,
    val regenColour: Boolean = true,
    val regenWarning: Boolean = true
)
```

### 3. DepthViewModel — sequential generation with per-layer hide/show

Add:
- `generatingStep: StateFlow<PipelineStep?>` — which step is currently being regenerated (null = idle)
- `rasterCacheVersion: StateFlow<Int>` — increments after each cache write, triggers produceState re-read

`generateRasterLayers()` flow:
```
for each selected step:
    generatingStep.value = step       // UI hides this layer
    execute step (load grid / derive isobaths / build raster)
    if raster step: write cache
    rasterCacheVersion.value++        // UI re-reads cache
    generatingStep.value = null       // UI shows layer
```

### 4. MapScreen — remove lazy-init, add hide/show

- **Remove** the `LaunchedEffect` that auto-triggers `generateRasterLayers` on cache miss
- Keep live-build `produceState` blocks as fallback
- Cache-read `produceState` blocks key on `generatingStep` + `rasterCacheVersion`:
  ```kotlin
  val depthBitmapCached by produceState<Bitmap?>(initialValue = null,
      depthGrid, ..., rasterVersion, generatingStep) {
      if (generatingStep == DEPTH_COLOUR) { value = null; return@produceState }
      value = depthViewModel.readCached(...)
  }
  ```
- Settings: 4 checkboxes with persisted states + "Regenerate" button

### 5. LoadingOverlay during generation

Shows "Caching Layers" title + current step name as subtitle. Progress 0→100% across all selected steps (weighted by timings). Only visible when `generatingStep != null`.

## Files Modified
- `RasterCache.kt` — rename Layer → PipelineStep, add GRID/ISOBATH
- `RasterProgress.kt` — progress weighting for all 4 steps
- `SettingsManager.kt` — 4 persisted regen booleans
- `DepthViewModel.kt` — generatingStep + rasterCacheVersion StateFlows, sequential generateRasterLayers
- `MapScreen.kt` — remove lazy-init, hide/show per layer, 4 checkboxes, regenerate button
