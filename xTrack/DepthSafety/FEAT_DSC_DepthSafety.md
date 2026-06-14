---
name: DepthSafety
status: active
created: 2026-06-08 18:25
modified: 2026-06-09 12:01
active_subfeature: caching
---

# Feature: DepthSafety

**Description:** The depth map is a navigation-safety surface but has four gaps: the colour layer can
paint on land; isobaths are drawn uniformly so coarse EMODnet contours look as precise as 1 m Litto3D;
there is no standout "you can ground here" mark; and there is no shallow-water alarm. Delivered as four
branches off `feature/litto3d-shallow` — B1, B2, B3 from the base, **B4 from B3** — one subfeature each.

## Subfeatures

### water-only  [x]
B1 · `feature/depth-warning-1` — no depth colour when `!isOnWater`. **Bake-mask is the guard** (per-cell runtime guard infeasible — grid is ~7 M cells).
#### Todos
- [x] Bake-time land-mask: `DepthZoneMask.apply` also erases `!isWater` cells (committed 3c6ee3d, in base)
- [x] Re-bake + on-device verify the tint stops at the waterline
- [ ] (optional) cheap load-time integrity check: sample `isWater`, log/flag a stale / un-masked asset
#### Rules
- Per-cell runtime `isWater` masking is infeasible (~7 M cells = tens of seconds on-device); the bake mask is authoritative. Canvas `PorterDuff.CLEAR` of land polygons is the fallback if a hard runtime guarantee is ever needed.
#### Key Files
- `ui/map/DepthBitmap.kt`, `data/depth/DepthRepository.kt`, `ui/map/MapScreen.kt`, `data/depth/DepthZoneMask.kt`

### isobar-precision  [x]
B2 · `feature/depth-warning-2` — isobaths reflect data precision (suppress fine-over-coarse + style by confidence).
#### Todos
- [x] `Isobath.lines` → `List<IsobathLine(points, source, confidence)>`
- [x] `DepthIsobaths.build`: per-line source/confidence sampling + fine-over-coarse suppression (mask coarse cells for fine levels)
- [x] `drawIsobaths`: stroke **colour by source** + `DashPathEffect` for low-confidence (≤35: GEBCO/interpolated)
- [x] Source→colour map in `zone.properties` via `ZoneConfig.isobarColor()` (litto3d `#228B22`, emodnet `#00008B`, default `#37474F`); add a source = add a line
- [x] Constants: `ISOBATH_FINE_LEVEL_MAX_M=10f`, `ISOBATH_FINE_MAX_RES_M=10.0`, `ISOBATH_LOWCONF_DASH_MAX=35`
- [x] Unit test `DepthIsobathsTest` (suppression + tagging) passes; `assembleDebug` green
- [ ] On-device: verify both hues read over the deep-navy fill + magenta band; tune `zone.properties` if needed
#### Rules
- Fine shallow contours (≤10 m levels) only where source res ≤10 m (Litto3D/SDB); never fake a 2 m line from 115 m EMODnet.
- **Isobath line colour = data source.** Now (2 sources): EMODnet → dark blue, Litto3D → forest green. Colours live in a **properties file**, one entry per source; adding a source = add a colour line there; unmapped source → muted grey. Colour is the primary precision cue (dashing reserved for low-confidence fill).
- Verify both hues read over the deep-navy fill + magenta danger band on-device (EMODnet dark-blue over deep navy is the contrast risk).
#### Key Files
- `data/model/Isobath.kt`, `data/depth/DepthIsobaths.kt`, `ui/map/MapScreen.kt`, `data/depth/DepthConstants.kt`

### danger-display  [x]
B3 · **SUPERSEDED by develop's low-depth warning overlay** (merged into this branch 2026-06-08) — a *more complete* version than our plan:
- `LowDepthWarningBitmap.build(grid, maxDepthM, isWater)` — bright magenta GroundOverlay (~78% magenta) for water `0 ≤ depth < threshold`, stacked above the depth raster. **Water-only** via a cheap **depth-gated** `isWater` test — only the few shallow cells are checked, sidestepping the 7 M-cell cost that killed B1's runtime guard.
- **Configurable + persisted threshold** `AppSettings.lowDepthWarningMaxM` (default `DepthConstants.LOW_DEPTH_WARNING_MAX_M = 1.5`; 0.5 m-step Settings slider) + a **visibility toggle** (map warning-triangle button + settings switch).
- Bitmap **rebuilds on threshold change** (`produceState` keyed on `lowDepthWarningMaxM`).
So B3 (magenta + configurable + rebuild-on-change + water-only) is **done** — via a *separate overlay*, cleaner than editing `DepthColorRamp`.
#### Todos
- [x] (develop) magenta low-depth overlay: configurable+persisted threshold, toggle, water-only (depth-gated isWater), rebuild-on-change
- [ ] (optional) source the default threshold from `zone.properties`/`ZoneConfig` for consistency with the isobar tunables — low value (already settings-configurable)
#### Rules
- **Do not re-implement** — extend develop's `LowDepthWarningBitmap` / `lowDepthWarningMaxM`; no parallel ramp-magenta path.
#### Key Files
- `ui/map/LowDepthWarningBitmap.kt`, `ui/map/MapScreen.kt`, `data/settings/SettingsManager.kt`, `data/depth/DepthConstants.kt` (`LOW_DEPTH_WARNING_MAX_M`)

### danger-alert  [ ]
B4 · `feature/depth-warning-4` — the **ALARM** (pulse + banner; sound later). **Still needed** — develop shipped only the *display* overlay, not an at-the-boat alert. The display threshold (`lowDepthWarningMaxM`) already exists, so B4 adds only the alarm on top.
#### Todos
- [ ] `DepthSample.isBelow(minDepthM)`
- [ ] Derived `shallowAlert` = `isWater && depthAtCenter.isBelow(threshold)` (+0.3 m hysteresis) → pulsing `DepthCard` + grounding banner (reuse the Zone300Card pulse)
- [ ] Threshold: **reuse `AppSettings.lowDepthWarningMaxM`** (display = alert), OR add a separate `dangerAlertMaxM` if the alarm should trip before the visual — decide
- [ ] Stub `onShallowAlert()` hook where sound/vibration will later go (no audio in app yet)
#### Rules
- Display overlay exists (develop); B4 adds only the ALARM. Reuse `lowDepthWarningMaxM` unless a distinct alert threshold is wanted.
#### Key Files
- `data/model/DepthGrid.kt`, `ui/map/DashboardPanel.kt`, `ui/map/MapScreen.kt`, `ZoneConfig.kt`, `data/settings/SettingsManager.kt`

### edonet false alert  [x]
Coarse EMODnet (115 m) sampling emergent rocks reads above chart datum → after the elevation→depth negation it surfaces as a negative / false-shallow "depth" (the −1.7 m off Cap d'Antibes) that reads as a false grounding hazard. Two-layer fix: bake-time drop of above-datum cells + a runtime, configurable cutoff on the readout. Branch `feature/pink-fix-edonet` (off develop).
#### Todos
- [x] Bake-time guard: `mergeDeep` / `fillGaps` / DepthGrid `mergeShallowShoalest` drop `v<0` (above-datum), mirroring the shallow-raster guard
- [x] Runtime gate `DepthSample.gatedForEmodnetShallow(cutoff)` — EMODnet shallower than cutoff → no-data ("—")
- [x] Persisted setting `emodnetShallowCutoffM` (0–5 m, default 2.0) + slider at end of Advanced (EN+FR strings)
- [x] Unit tests `DepthSampleGateTest` + `DepthMergeTest` deep-negative; `testDebugUnitTest` + `assembleDebug` green
- [ ] On-device verify: −1.7 m readout → "—" at Cap d'Antibes (needs deploy)
- [ ] Gate the depth colour map (`DepthBitmap`): EMODnet shallow cells render as gray instead of depth colour
- [ ] (later) reuse the gate for the B4 alarm so coarse EMODnet can't trip a false grounding alert
#### Rules
- Gate applies to the **readout**, **depth colour map**, **magenta low-depth overlay**, and **all isobath contour levels**. Gated EMODnet shallow cells: readout → `"—"`; colour map → semi-transparent gray (`DepthBitmap.GATED_GRAY_ARGB`); overlay → transparent; isobaths → mask to NaN (fine levels ≤10 m already mask coarse sources; coarse levels get additional `maskEmodnetShallow`). All three bitmaps rebuild reactively on cutoff change (`produceState` key); isobaths re-derive via `DepthRepository.recomputeIsobaths` triggered by `LaunchedEffect`. The bake-time `v<0` guard only affects the shipped grid after a re-bake.
- EMODnet is a deep backbone, **not** a shallow source: below the cutoff it must not drive a nearshore depth/alert. Litto3D/SDB are authoritative nearshore.
- Litto3D coverage is thin (~24 k cells in the baked grid) → coarse EMODnet leaks shallow at many spots; the gate is the readout safeguard, real depth needs Litto3D.
#### Key Files
- `data/depth/DepthMerge.kt`, `data/model/DepthGrid.kt` (`DepthSample.gatedForEmodnetShallow`), `data/settings/SettingsManager.kt`, `ui/map/MapScreen.kt`, `res/values*/strings.xml`

## Todos
- [ ] **NEXT:** B4 · danger-alert (alarm: `isBelow` + pulsing card/banner + sound stub). On-device verify B2 colours + edonet-gate at Cap d'Antibes. Option-3 gdal_contour parked.

## Subfeatures

### caching  [x]
Runtime layer bitmap caching — avoid rebuilding the ~7 M-cell depth colour map and low-depth warning overlay on every cold launch. RawBuf disk cache (IntArray→FileChannel), measured at 78ms colour / 52ms warning vs 980ms / 5178ms compute.
#### Todos
- [x] Measure pertinence of a disk cache — RawBuf wins decisively. See `plans/caching.md`.
- [x] Implement `RasterCache` object: write/read RawBuf (IntArray→ByteBuffer→FileChannel) with compound key (grid fetchTimestampMs + all threshold settings)
- [x] Add `generateRasterLayers(layers: List<RasterLayer>)` to DepthViewModel — builds requested rasters + persists cache + reports progress via StateFlow<RasterProgress>
- [x] Add settings control: "Regenerate Rasters" button in Advanced section (regenerates both layers; per-layer selection is follow-up)
- [x] Lazy instantiation on MapScreen start: if cached raster(s) missing → auto-trigger `generateRasterLayers()` for the missing ones
- [x] Show `LoadingOverlay` during raster generation with progress % computed from measured per-step timings (grid 877ms + isobath 1887ms + colour 980ms + warning 5178ms → total 8922ms baseline)
- [x] Remove timing simulation code (`simulateCacheFormats`) after cache is operational
- [ ] Measure end-to-end cold-start improvement after cache (needs on-device deploy)
#### Rules
- The grid is ~7 M cells (2,476×2,856), 41 MB protobuf. Cache targets only the raster Bitmap output, not the grid itself.
- Cache key = `grid.metadata.fetchTimestampMs` + `emodnetShallowCutoffM` + `lowDepthWarningMaxM` + `lowDepthWarningMinOpacityPct`. Any change → rebuild + overwrite.
- RawBuf format: `IntArray` → `ByteBuffer.allocateDirect()` → `FileChannel.write()`; read reverses. Zero encode/decode overhead.
- Progress during `generateRasterLayers()` is weighted by measured timings: grid load ~10%, isobaths ~21%, colour raster ~11%, warning raster ~58% of total 8922ms.
- `LoadingOverlay` reuses the existing composable (spinner + LinearProgressIndicator + phase label) but driven by a `depthProgress: GenerationProgress` parameter, not the coastline progress.
#### Key Files
- `ui/map/MapScreen.kt` (produceState blocks, LoadingOverlay wiring, lazy-init trigger)
- `ui/map/DepthBitmap.kt`, `ui/map/LowDepthWarningBitmap.kt` (raster builders)
- `ui/map/DepthViewModel.kt` (generateRasterLayers, progress StateFlow)
- `data/depth/DepthRepository.kt` (grid load, isobath build)
- `data/settings/SettingsManager.kt` (regeneration control)
- `data/depth/RasterCache.kt` (new: RawBuf disk I/O)
- `data/model/GenerationProgress.kt` (reused for progress reporting)
#### Docs
- `xTrack/DepthMapping/FEAT_PLN_DepthMapping_caching.md` — measurement results and RawBuf decision
- `xTrack/DepthMapping/FEAT_PLN_DepthMapping_caching-rawbuf.md` — implementation plan for RawBuf cache with progress UI

## Rules
- Branch-per-feature off `feature/litto3d-shallow`: B1, B2, B3 from the base; **B4 from B3**. Commit only on explicit instruction.
- Two distinct configurable thresholds — display-danger (magenta overlay) and alert-danger (alarm) — each in `zone.properties` **and** settings.
- Reuse, don't reinvent: per-cell `DepthSource.nominalResM`/confidence (already on the grid); the Zone300Card pulse + settings/ZoneConfig tunables for the alert.

## Key Files
- See per-subfeature Key Files.

## Docs
- `xTrack/DepthSafety/FEAT_PLN_DepthSafety_plan.md` — the branch-per-feature workflow + per-branch design.
