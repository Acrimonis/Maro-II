# Litto3D Regression Analysis

> Analysis of why Litto3D is ignored in the depth referential.

## Root Cause

The Litto3D source raster `.asc` file is **missing** from the data pipeline. Only GDAL metadata sidecars remain.

## File Inventory

### `data/app-assets/depth/` (build intermediates)

| File | Status | Notes |
|------|--------|-------|
| `emodnet-nice-frejus.asc` | ✅ Present | 851×535 cells, ~115 m |
| `emodnet-nice-frejus.aux.xml` | ✅ GDAL sidecar | |
| `emodnet-nice-frejus.prj` | ✅ Projection | |
| `litto3d-nice-frejus.asc.aux.xml` | ✅ GDAL sidecar — **red herring** | Metadata only, no data |
| `litto3d-nice-frejus.prj` | ✅ GDAL sidecar — **red herring** | |
| **`litto3d-nice-frejus.asc`** | **❌ MISSING** | The actual raster data |
| **`litto3d-nice-frejus.asc.gz`** | **❌ MISSING** | Gzipped alternative |
| `nice-frejus.bin` | ✅ Present | Cooked output — EMODnet only |

### `app/src/main/assets/depth/` (shipped app assets)

| File | Status |
|------|--------|
| `emodnet-nice-frejus.asc` | ✅ Present (587×337 cells, clipped to envelope) |
| `emodnet-nice-frejus.aux.xml` | ✅ |
| `emodnet-nice-frejus.prj` | ✅ |
| Any Litto3D files | ❌ None |

## Failure Chain

1. **`DepthPrebakeTest.prebakeDepth()`** checks for `litto3d-nice-frejus.asc` (or `.gz`) — not found
2. `shallow` variable is set to **`null`**
3. **`DepthGenerator.generate()`** skips the shallow merge block:
   ```kotlin
   if (shallowSource != null) {
       DepthMerge.mergeShallowShoalest(grid, shallowSource, SHALLOW_TIER_MAX_M)
   }
   ```
4. Baked `nice-frejus.bin` contains only EMODnet data — all cells tagged `DepthSource.EMODNET` (3)
5. App loads the `.bin` → no Litto3D cells in the grid → dashboard reads "EMODNET" everywhere

## To Fix

1. Run `tools\bake_litto3d.bat` to produce `litto3d-nice-frejus.asc` from raw tiles
2. Re-run `DepthPrebakeTest` with `-Dmaro.prebake=true` to merge Litto3D into the `.bin`
3. Rebuild the APK
4. On-device verify: nearshore cells should show `LITTO3D` source with shallower depths
