<!-- scope: feature -->

# DepthMapping — cold-start heap ceiling (app cannot start on a cleared data set)

## Problem

The app dies of `OutOfMemoryError` on a cold start. The device caps the process heap at 256 MB
(`growth limit 268435456`, and [`AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml:14)
sets no `largeHeap`), while the cold path of the depth layer allocates several times that.

Device evidence (crash buffer, 2026-09-21):

- `20:50:32 PID 26399` — OOM allocating a `Message` in `Choreographer.onVsync` (the UI thread dying).
- `20:49:18 PID 26193` — OOM inside `DepthSource.fromId`, called from
  [`maskCoarseSources()`](../../app/src/main/java/ykws/android/maro/data/depth/DepthIsobaths.kt:89).
- `20:49:07 PID 26091` — OOM at
  [`grid.depths.copyOf()`](../../app/src/main/java/ykws/android/maro/data/depth/DepthIsobaths.kt:85).
- Earlier the same day (11:03 → 20:40) the same ceiling took `parseFrom` instead; the uncommitted
  streaming rewrite of [`DepthSerializer.deserialize()`](../../app/src/main/java/ykws/android/maro/data/depth/DepthSerializer.kt:90)
  moves the failure forward without removing it.
- The load itself succeeds: `DepthGridLoad: 815ms rows=2854 cols=3627 cells=10351458`.

## Trigger

**Shipped (2026-09-21).** The cache key used to fold every raster colour into one hash, so touching
any of them discarded **both** cached rasters and forced the whole cold path. Each raster now carries
its own palette: `AppConfig.depthRasterColorsHash` (ramp, ramp alpha, NoData colour) and
`AppConfig.lowDepthRasterColorsHash` (the warning hue), with `RasterCache.keyFor(step, …)` the one
place a key is built — used by the pipeline when it writes and by both readers. The colour edits of
16 September (14727e3) and 19 September (7918c0f) would now miss one raster each.

**What remains.** The trigger the plan is written for is an **empty cache**: a first install, a
cleared data set, or a change to the grid timestamp or the two depth thresholds — cases where both
rasters genuinely have to be built. `initCache` runs unconditionally at startup
([`MainActivity`](../../app/src/main/java/ykws/android/maro/MainActivity.kt:152)), so this path is
taken regardless of whether the depth layer is shown.

**Correction to an earlier figure.** The September incident was quoted as now peaking near 185 MB.
That holds only if the rebuilt raster and the untouched raster's **read** do not overlap: grid 62 +
rebuild 83 + the read's surviving bitmap 41. The two `produceState` blocks in
[`MapDepthRasterEffects`](../../app/src/main/java/ykws/android/maro/ui/map/MapDepthRasterEffects.kt:63)
start together, and a read is itself `allocateDirect(41) + IntArray(41) + Bitmap(41)` = 124 MB of
transients, so with overlap the peak is 62 + 83 + 124 = **269 MB — still over the cap**. The key
split narrows what misses; it does not make the path fit, and serialising the two steps is what
makes the honest range resume at 186 MB.

## Current peak (derived from component sizes in source, 10,351,458 cells = 2854 × 3627; not observed)

| Allocation | Where | Heap |
|---|---|---|
| grid resident: depths + source + confidence | [`readBundled()`](../../app/src/main/java/ykws/android/maro/data/depth/DepthRepository.kt:71) | 62 MB |
| full-field copy for the fine mask, held across all 12 levels | [`maskCoarseSources()`](../../app/src/main/java/ykws/android/maro/data/depth/DepthIsobaths.kt:84) | 41 MB |
| boxed `HashMap<Int, GridPt>`, `HashMap<Int, MutableList<Int>>`, `HashSet<Long>` + geometry | [`marchingSquaresScalar()`](../../app/src/main/java/ykws/android/maro/spatial/SpatialOperations.kt:743) | unmeasured |
| colour raster: IntArray + Bitmap | [`DepthBitmap.build()`](../../app/src/main/java/ykws/android/maro/ui/map/DepthBitmap.kt:75) | 83 MB |
| warning raster: IntArray + Bitmap | [`LowDepthWarningBitmap.build()`](../../app/src/main/java/ykws/android/maro/ui/map/LowDepthWarningBitmap.kt:67) | 83 MB |
| per step for the cache write: `getPixels` array + `allocateDirect` | [`DepthViewModel`](../../app/src/main/java/ykws/android/maro/ui/map/DepthViewModel.kt:176), [`RasterCache.write()`](../../app/src/main/java/ykws/android/maro/data/depth/RasterCache.kt:62) | 83 MB |
| per raster for a cache read: `allocateDirect` + IntArray + Bitmap | [`RasterCache.read()`](../../app/src/main/java/ykws/android/maro/data/depth/RasterCache.kt:82) | 124 MB |

Two faults sit on top of the raw numbers: on a miss the two `produceState` builds run **live** while
the silent `LaunchedEffect` rebuilds both again through
[`generateRasterLayers()`](../../app/src/main/java/ykws/android/maro/ui/map/DepthViewModel.kt:127), so
each raster is built twice in the same window; and nothing is serialised, so build and read stack.

## Goal

A cold start on the phone's 256 MB heap completes, writes both raster caches and boots; the second
launch is warm. No visible change to the depth layer beyond what the decisions below record.

## Stage 1 — boot it (smallest change that ends the loop)

- `android:largeHeap="true"` on `<application>`. Its size is device-dependent and unmeasured here,
  so treat the boot as likely rather than certain and confirm with the dumpsys below — the evidence
  that would make it certain is the cap line from a post-start `dumpsys meminfo`.
- One owner per raster, and serialise the two: on a cache miss the pipeline owns the build and the
  live `produceState` stands down while `generatingStep` names that step (the merge already prefers
  cached over live at [`effectiveDepthBitmap`](../../app/src/main/java/ykws/android/maro/ui/map/MapDepthRasterEffects.kt:150)).
  Serialising is not cosmetic — see the 269 MB overlap above.

Acceptance: cleared data → app starts, both `.buf` files land, second start reads them.

## Stage 2 — one array per raster, streamed I/O (no visible change)

- `DepthBitmap.build` / `LowDepthWarningBitmap.build` return the pixel `IntArray`; the caller writes
  it to the cache, then cuts the strip bitmaps straight from that same array with the offset/stride
  `Bitmap.createBitmap` overload and drops it — the full-size bitmap never exists.
- **Ripple to cost, not just to state.** This changes the overlay seam, not only the two builders:
  [`addBandedOverlay()`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:313)
  must take pixels plus dimensions instead of a bitmap, and the depth bitmaps threaded through
  [`MapScreen`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3117) become strip
  sets. `RasterCache.read` gains a pixel-array variant so the warm path converges on the same shape.
- [`RasterCache.write()`](../../app/src/main/java/ykws/android/maro/data/depth/RasterCache.kt:62)
  streams the pixels in chunks instead of `allocateDirect(8 + size*4)` (−41 MB);
  [`read()`](../../app/src/main/java/ykws/android/maro/data/depth/RasterCache.kt:82) reads into the
  `IntArray` through a reused staging buffer instead of `allocateDirect(size)` (−41 MB per raster).
- Null the array between steps; the two cache reads serialise rather than overlap.
- Take the two key-coupling findings with it, since they are the same class in the same key: the two
  depth thresholds sit in **both** steps' keys though only the warning overlay reads them, and the
  warning key carries no coastline component although its water test comes from the coastline.

## Stage 3 — get the load path out of the way (needs D1)

- Publish `DepthState.Ready` with the grid alone by moving `DepthIsobaths.build()` out of
  [`setReady()`](../../app/src/main/java/ykws/android/maro/data/depth/DepthRepository.kt:83).
- **The publication path is the work, not a detail.** [`renderModel`](../../app/src/main/java/ykws/android/maro/ui/map/DepthViewModel.kt:64)
  is derived from `state` and reads `getRenderModel()` at that instant, so contours built after
  Ready need their own publication (a second flow, or a re-emit once the pass finishes). And
  [`RasterCache.has()`](../../app/src/main/java/ykws/android/maro/data/depth/RasterCache.kt:53)
  answers `true` for `ISOBATH` as an in-memory step while the caller only ever offers the two raster
  steps, so the pipeline can never run it today — either `Step.ISOBATH` carries the pass or the enum
  loses it.
- Drop the full-field copy: for fine levels, test the source inside the marching-squares accessor
  instead of materialising a second field (−41 MB).
- Slab the sweep into overlapping row bands so the boxed collections cover a band, not 10.35 M cells.

## Stage 4 — the durable lever (needs D2 and D4)

Adopt the settled design in
[`260609_FEAT_PLN_DepthMapping_oom-mmap-fix.md`](260609_FEAT_PLN_DepthMapping_oom-mmap-fix.md):
split the `.bin` into a scalars-only protobuf header plus raw `depths` / `source` / `confidence`
regions, map the file read-only and expose the arrays as views, so the 62 MB grid leaves the heap.

- That plan predates the Menton asset and the streaming decoder. Its Step 2 is not half-done by the
  streaming rewrite: once the arrays are raw file regions, a hand-written streaming decoder is
  redundant, so Stage 4 **deletes** that work rather than extending it — which is why the
  uncommitted [`DepthSerializer.kt`](../../app/src/main/java/ykws/android/maro/data/depth/DepthSerializer.kt)
  sitting in the tree (and already on the phone) needs a decision before either path is built on.

## Expected peaks (derived, not observed)

| After | Cold-start peak | Steady state, both layers attached (tiles and Compose excluded) |
|---|---|---|
| today | ≳270 MB → dies | ≈228 MB (grid 62 + two full rasters 83 + two strip sets 83) |
| key split alone (shipped) | 186–269 MB, overlap-dependent | ≈228 MB, unchanged |
| Stage 1 | fits the raised cap, shape unchanged | fits the raised cap, shape unchanged |
| Stages 1–2 | ≈145 MB (grid + one raster step) | ≈186 MB (no full raster survives its strips) |
| Stages 1–3 | ≈145 MB | ≈145 MB (grid + the two strip sets) |
| Stage 4 | ≈82 MB (grid off-heap) | ≈83 MB |

## Decisions for the user

- **D1** — Contours arrive a moment after the depth layer instead of with it (Stage 3). Visible; the
  alternative is leaving the pass on the load path.
- **D2** — Re-baking the `.bin` into the header+raw format (Stage 4) changes the shipped asset and
  invalidates the raster cache once; it needs `apk-bake.bat depth`.
- **D3** — `largeHeap` (Stage 1) is the fast lever, but it raises the ceiling instead of lowering the
  demand; Stages 2–4 are what make it stop being load-bearing.
- **D4** — Keep the streaming decoder as the interim, or scrap it for the format change; it is
  uncommitted and deployed on the device either way.
- **D5** — Fold the per-step key tidying (thresholds off the colour map's key, the coastline onto the
  warning key's) into Stage 2, or leave both as recorded defects.
- **D6** — Stage 1's second item (one owner per raster, the two paths serialised) removes the
  duplicate build, but on a **cold** start it also delays the depth layer until the pipeline
  finishes, where each live build shows its layer as soon as it lands. Not taken blind: the gate
  that would suppress a live build races the pipeline's own flag, so it wants a device check rather
  than a compile — and `largeHeap` already carries the boot without it.

## Verification

- `apk-deploy.bat`, clear the app's data, start: no `FATAL EXCEPTION`, both `RasterCache … write`
  lines present, second start logs both `… read` lines.
- `adb shell dumpsys meminfo ykws.android.maro` right after start, against the cap.
- Scoped test run (`ui.map` + `config`) for the touched seams. The key split ships with no test of
  its own; the properties-versus-default set is the only guard it has.

## Out of scope

- The map's own budget outside the rasters: osmdroid tiles and Compose, which none of the stages
  above reduces.
- A coarser bake (50 m quarters every number above), which trades nearshore precision the depth
  feature exists to provide.
