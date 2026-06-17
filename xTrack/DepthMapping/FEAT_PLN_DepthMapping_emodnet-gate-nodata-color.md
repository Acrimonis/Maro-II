<!-- scope: feature -->
# Plan: EMODnet Shallow Gate for All Depth Layers + Configurable NoData Color

## Overview

Two changes to the depth rendering pipeline:

1. **EMODnet shallow gate on all raster layers** — currently only the dashboard readout is gated; the colour map, low-depth warning overlay, and isobath contours all show raw EMODNET cells.
2. **Configurable NoData colour** — currently NaN cells render fully transparent; add a `zone.properties` property for a distinct colour.

---

## Feature 1: EMODnet Shallow Gate on All Layers

### Current state

| Layer | Source-aware? | Gated? |
|---|---|---|
| Dashboard readout (`DepthSample`) | Yes (`DepthSource` in `DepthSample`) | ✅ via `gatedForEmodnetShallow()` at `MapScreen.kt:224` |
| Colour map (`DepthBitmap` → `DepthColorRamp`) | **No** — only reads `grid.depthRaw(r,c)` → float | ❌ Raw EMODNET shallow cells painted |
| Low-depth warning (`LowDepthWarningBitmap`) | **No** — only reads `grid.depthRaw(r,c)` | ❌ Raw EMODNET shallow cells painted |
| Isobaths (`DepthIsobaths`) | **Partial** — `maskCoarseSources()` masks by nominal resolution, not by source+cutoff | ❌ No EMODnet-specific shallow gating |

The `DepthGrid` already stores per-cell `source: ByteArray` (see [`DepthGrid`](app/src/main/java/ykws/android/maro/data/model/DepthGrid.kt:84)) — the source identity is available at every cell, the builders just don't use it.

### Implementation approach

**Centralised gated accessor on `DepthGrid`** — add a single method that returns a gated depth, then pass the cutoff into each builder:

```kotlin
// On DepthGrid
fun depthGated(r: Int, c: Int, emodnetCutoffM: Float): Float {
    val d = depthRaw(r, c)
    if (d.isNaN() || d < 0f) return d
    if (emodnetCutoffM > 0f && sourceAt(r, c) == DepthSource.EMODNET && d < emodnetCutoffM) return Float.NaN
    return d
}
```

> **Design decision (confirmed):** The raster gate uses the **same setting** (`appSettings.emodnetShallowCutoffM`) as the dashboard readout. Changing the cutoff in settings triggers a full raster rebuild — ✅ acceptable.

**Changes required:**

| File | Change |
|---|---|
| [`DepthGrid`](app/src/main/java/ykws/android/maro/data/model/DepthGrid.kt:75) | Add `depthGated(r, c, cutoffM)` method |
| [`DepthColorRamp`](app/src/main/java/ykws/android/maro/ui/map/DepthColorRamp.kt:18) | No change — already handles NaN as transparent |
| [`DepthBitmap`](app/src/main/java/ykws/android/maro/ui/map/DepthBitmap.kt:26) | Accept `emodnetCutoffM: Float = 0f` param; use `grid.depthGated(r, c, cutoffM)` instead of `grid.depthRaw(r, c)` |
| [`LowDepthWarningBitmap`](app/src/main/java/ykws/android/maro/ui/map/LowDepthWarningBitmap.kt:25) | Accept `emodnetCutoffM: Float = 0f` param; use `grid.depthGated(r, c, cutoffM)` instead of `grid.depthRaw(r, c)` |
| [`DepthIsobaths`](app/src/main/java/ykws/android/maro/data/depth/DepthIsobaths.kt:25) | Accept `emodnetCutoffM: Float = 0f` param; add **separate masking pass** for EMODnet shallow cells (distinct from the existing resolution-based `maskCoarseSources()`) |
| [`DepthViewModel.generateRasterLayers()`](app/src/main/java/ykws/android/maro/ui/map/DepthViewModel.kt:125) | Pass `settings.emodnetShallowCutoffM` to all three builders |
| [`RasterCache.Key`](app/src/main/java/ykws/android/maro/data/depth/RasterCache.kt:38) | Already has `emodnetCutoffM: Float` — use the actual `settings.emodnetShallowCutoffM` instead of hardcoded `0f` |
| [`DepthSample.gatedForEmodnetShallow()`](app/src/main/java/ykws/android/maro/data/model/DepthGrid.kt:48) | No change — still gates the dashboard readout independently |

> **Design decision (confirmed):** Isobaths get a **separate masking pass** for EMODnet shallow — distinct from the existing resolution-based `maskCoarseSources()`. The two concerns (resolution vs reliability) remain independent.

---

## Feature 2: Configurable NoData Colour

### Current state

[`DepthColorRamp.argb()`](app/src/main/java/ykws/android/maro/ui/map/DepthColorRamp.kt:19):
```kotlin
if (depthM.isNaN() || depthM < 0f) return 0  // NoData or above datum → transparent
```

`0` = fully transparent (ARGB). NoData gaps show the basemap through with no visual indication.

### Implementation approach

1. **Add property** to [`zone.properties`](app/src/main/assets/zone.properties):
   ```
   nodata.color=#FFCCCCCC   # Light grey for no-data areas
   ```

2. **Load in [`ZoneConfig`](app/src/main/java/ykws/android/maro/ui/map/ZoneConfig.kt:29)** — new field:
   ```kotlin
   var nodataColor: Int = 0xFFCCCCCC.toInt()  // default=light grey
       private set
   ```
   Parse via existing `parseColorOrNull()` helper.

3. **Modify [`DepthColorRamp.argb()`](app/src/main/java/ykws/android/maro/ui/map/DepthColorRamp.kt:18)** to accept an optional NoData colour — covers both NaN and above-datum (`< 0f`):
   ```kotlin
   fun argb(depthM: Float, nodataColor: Int = 0xFFCCCCCC.toInt()): Int {
       if (depthM.isNaN() || depthM < 0f) return nodataColor
       ...
   }
   ```

4. **Wire in [`DepthBitmap.build()`](app/src/main/java/ykws/android/maro/ui/map/DepthBitmap.kt:26)** — accept `nodataColor: Int` param, pass to ramp.

5. **Call site at [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)** — pass `ZoneConfig.nodataColor` through.

> **Design decisions (confirmed):**
> - Default NoData colour: **`#FFCCCCCC`** (light grey) ✅
> - NoData colour also covers **above-datum** cells (`depthM < 0f`) ✅

### Scope

Only the **colour map** (`DepthBitmap`/`DepthColorRamp`) gets the NoData colour. The low-depth warning overlay already has its own NaN handling (transparent), and isobaths skip NaN cells naturally.

### Notes

- The NoData colour is **static** (from `zone.properties`, reloaded on app restart). It's not a runtime setting because NoData is a rare edge case and doesn't need per-trip tuning.
- Default `0xFFCCCCCC` (light grey) gives immediate visual feedback on depth gaps.

---

## File Change Summary

| File | Change |
|---|---|
| `app/src/main/java/.../data/model/DepthGrid.kt` | Add `depthGated(r, c, cutoffM)` |
| `app/src/main/java/.../ui/map/DepthColorRamp.kt` | Add `nodataColor` param to `argb()` |
| `app/src/main/java/.../ui/map/DepthBitmap.kt` | Accept `emodnetCutoffM`, `nodataColor` params |
| `app/src/main/java/.../ui/map/LowDepthWarningBitmap.kt` | Accept `emodnetCutoffM` param |
| `app/src/main/java/.../data/depth/DepthIsobaths.kt` | Accept `emodnetCutoffM`, extend `maskCoarseSources()` |
| `app/src/main/java/.../ui/map/DepthViewModel.kt` | Pass real `emodnetShallowCutoffM` + `ZoneConfig.nodataColor` |
| `app/src/main/java/.../data/depth/RasterCache.kt` | Use real cutoff in `Key` (remove `0f` hardcode) |
| `app/src/main/java/.../ui/map/ZoneConfig.kt` | Load `nodata.color` from properties |
| `app/src/main/assets/zone.properties` | Add `nodata.color=...` entry |
| `app/src/main/java/.../ui/map/MapScreen.kt` | Pass `ZoneConfig.nodataColor` to depth bitmap pipeline |

---

## Open Questions for Discussion

1. **Cache invalidation** — `RasterCache.Key` includes `emodnetCutoffM`. When the cutoff changes in settings, the key hash changes → automatic miss → rebuild. Is that the desired behaviour? (It means changing the cutoff triggers a full raster rebuild, which takes a few seconds.)

2. **Isobath masking** — `DepthIsobaths.maskCoarseSources()` currently masks by nominal resolution (> `ISOBATH_FINE_MAX_RES_M`). Should the EMODnet shallow gate be applied **in addition** to this, or **as a separate masking pass**? The existing coarse-source mask is about resolution (fine contours only on fine data). The EMODnet shallow gate is about reliability (shallow EMODnet is unreliable). These are different concerns but both result in NaN. I'd suggest a separate mask stage rather than conflating them.

3. **Above-datum cells** (`depthM < 0f`) — `DepthColorRamp` currently treats these as transparent (same as NaN). Should the NoData colour also apply to above-datum cells, or should they remain transparent? Above-datum means the seafloor is above the chart datum (negative depth, exposed at low tide) — arguably a distinct state from "no data available".

4. **NoData color defaults** — The default of `0` (transparent) preserves current behaviour. But if we're adding a colour, what's a good default? Light grey (`#FFCCCCCC`), a subtle hatch pattern, or transparent by default and only visible when explicitly configured?

