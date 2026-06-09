# All 4 Pipeline Steps in Lazy Rasterization — Design Discussion

<!-- scope: feature -->

## Context

The 4-step pipeline:
| Step | Time | Weight |
|---|---|---|
| Grid deserialization | 877ms | ~10% |
| Isobath derivation | 1887ms | ~21% |
| Colour raster | 980ms | ~11% |
| Warning raster | 5178ms | ~58% |

Current `generateRasterLayers()` only reports steps 3-4 because 1-2 are pre-loaded by `DepthRepository`.

## Options

### A: Self-contained pipeline — `generateRasterLayers()` calls grid load + isobaths internally

- Pro: Single entry point, progress faithfully tracks all 4 steps end-to-end
- Con: Grid already loaded by `initCache()` → redundant; `DepthRepository` owns grid lifecycle; refactors the existing load flow

### B: Pre-loaded grid — report all 4 steps honestly

- Steps 1-2 complete instantly (grid + isobaths already in memory) → progress jumps 0%→31%
- Steps 3-4 with intra-step granularity via `onProgress` callback
- Pro: No architecture refactor; grid stays under `DepthRepository`
- Con: First 31% of progress bar flies by instantly (grid/isobaths already done)

### C: Hybrid — `generateRasterLayers()` accepts optional pre-loaded grid

- If grid already loaded → steps 1-2 report 100% instantly (31% weight)
- If grid not loaded → method loads it (calls `repository.loadDepth()`) with real progress
- Pro: Works for both cold-start (grid not loaded yet) and settings-regenerate (grid loaded)
- Con: More complex; `loadDepth()` is already called by `initCache()`

## Recommendation: Option B with smooth transition

Progress starts at 0%, immediately reports grid+isobaths as complete (ticks 0%→31% in ~1 frame), then smoothly tracks colour raster (31%→42%) and warning raster (42%→100%) with `onProgress` row-by-row granularity.

This requires:
1. `onProgress` callback on `DepthBitmap.build()` and `LowDepthWarningBitmap.build()`
2. `generateRasterLayers()` reports all 4 `RasterStep`s, with 1-2 at 100% instantly

## Files
- `DepthBitmap.kt` — optional `onProgress: ((Int) -> Unit)?`
- `LowDepthWarningBitmap.kt` — same
- `DepthViewModel.kt` — report grid+isobath as 100% instantly, wire raster callbacks
