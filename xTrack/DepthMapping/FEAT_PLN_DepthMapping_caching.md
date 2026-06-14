# Depth Layer Bitmap Caching

<!-- scope: feature -->

## Context

On every cold launch, two ~7M-cell bitmaps are rasterised from the prebaked depth grid:

- [`DepthBitmap.build()`](../app/src/main/java/ykws/android/maro/ui/map/DepthBitmap.kt:25) — depth colour map
- [`LowDepthWarningBitmap.build()`](../app/src/main/java/ykws/android/maro/ui/map/LowDepthWarningBitmap.kt:30) — magenta shallow overlay

Both are built inside `produceState` blocks in [`MapScreen.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:225) with `initialValue = null`, so they rebuild on every cold launch and every settings change.

## Measured Pipeline

Grid: **2,476 × 2,856 = 7,071,456 cells** (not the ~3M initially estimated). Protobuf file: 41,434 KB.

| Step | Median (2 cold launches) | Cost |
|---|---|---|
| Grid deserialization (protobuf) | 877 ms | I/O-bound |
| Isobath contour derivation | 1,887 ms | CPU |
| **Colour raster** (`DepthBitmap.build`) | **980 ms** | CPU |
| **Warning raster** (`LowDepthWarningBitmap.build`) | **5,178 ms** | CPU (isWater sub-cell checks) |

## Cache Format Benchmarks (Simulated In-Memory)

### Depth Colour Raster (27,622 KB raw)

| Format | Write | Read | Total | Size | Ratio |
|---|---|---|---|---|---|
| **RawBuf** | **13 ms** | **65 ms** | **78 ms** | 27,622 KB | 100% |
| PNG | 369 ms | 89 ms | 458 ms | 320 KB | 1% |
| WebP | 208 ms | 74 ms | 282 ms | 252 KB | 1% |

**RawBuf vs compute: 78 ms vs 980 ms → 12.6× faster.**

### Low-Depth Warning Raster (27,622 KB raw, mostly transparent)

| Format | Write | Read | Total | Size | Ratio |
|---|---|---|---|---|---|
| **RawBuf** | **12 ms** | **40 ms** | **52 ms** | 27,622 KB | 100% |
| PNG | 353 ms | 65 ms | 418 ms | 39 KB | 0.1% |
| WebP | 175 ms | 45 ms | 220 ms | 138 KB | 0.5% |

**RawBuf vs compute: 52 ms vs 5,178 ms → 99× faster.**

## Decision

**RawBuf (raw `IntArray` → `ByteBuffer` → disk → `Bitmap`) is the clear winner.**

- First launch: build + write RawBuf → same cost as today (~6 s) + negligible 25 ms to persist
- Every subsequent launch: read RawBuf → ~105 ms total for both bitmaps → **~6 s saved**
- Disk cost: 2 × 27 MB = ~54 MB in `context.cacheDir` — acceptable for the perf gain
- Compressed formats (PNG/WebP): write is too slow (369/208 ms) to justify the space saving; read is comparable to RawBuf

The 5-second warning raster is the killer — it makes the map appear unresponsive on cold launch. A 52 ms RawBuf read eliminates that entirely.

---

## Cache Implementation (Next Steps)

- **Target:** both raster `Bitmap` outputs → RawBuf files in `context.cacheDir`
- **Cache key:** compound of `grid.metadata.fetchTimestampMs` + `emodnetShallowCutoffM` + `lowDepthWarningMaxM` + `lowDepthWarningMinOpacityPct`
- **Write:** `IntArray` → `ByteBuffer.allocateDirect()` → `FileChannel.write()`
- **Read:** `FileChannel.read()` → `ByteBuffer` → `IntArray` → `Bitmap.createBitmap()`
- **Invalidation:** any key change → rebuild + overwrite
- **Fallback:** missing/stale cache → rebuild (same as today)

## Related

- Feature: [`DepthSafety`](../xTrack/FEATURE_SCOPE_DepthSafety.md)
- Subfeature: `caching`
