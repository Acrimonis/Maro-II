# Intra-Raster Progress — Fix Plan

<!-- scope: feature -->

## Gap

`generateRasterLayers()` only reports 0→100 at start/end of each raster build. The plan requires linear interpolation within each band (e.g. colour raster progress 31%→42% as `row/h` goes 0→1). Also, grid+isobath steps are pre-loaded but progress starts at 0% instead of 31%.

## Fixes

### 1. Add `onProgress` callback to raster builders

`DepthBitmap.build(grid, onProgress: ((Int) -> Unit)? = null)` — calls `onProgress(row * 100 / h)` every 256 rows.

`LowDepthWarningBitmap.build(grid, maxM, isWater, minOpacity, onProgress: ((Int) -> Unit)? = null)` — same pattern.

### 2. Wire progress in generateRasterLayers

```kotlin
val bmp = DepthBitmap.build(grid) { stepProgress ->
    report(RasterStep.COLOUR_RASTER, "Depth colour map", stepProgress)
}
```

### 3. Start progress at 31%

```kotlin
_rasterProgress.value = RasterProgress("", 0, RasterTimings.globalProgress(RasterStep.COLOUR_RASTER, 0))
```

## Files
- `DepthBitmap.kt` — optional `onProgress` parameter
- `LowDepthWarningBitmap.kt` — optional `onProgress` parameter  
- `DepthViewModel.kt` — wire progress + fix start offset
