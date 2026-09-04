---
name: DepthSafety
status: active
created: 2026-06-08 18:25
modified: 2026-06-09 12:01
---

# Feature: DepthSafety

**Description:** The depth map is a navigation-safety surface but has four gaps: the colour layer can
paint on land; isobaths are drawn uniformly so coarse EMODnet contours look as precise as 1 m Litto3D;
there is no standout "you can ground here" mark; and there is no shallow-water alarm. Delivered as four
branches off `feature/litto3d-shallow` — B1, B2, B3 from the base, **B4 from B3** — one subfeature each.

## Sections

### water-only
B1 · `feature/depth-warning-1` — no depth colour when `!isOnWater`; bake-mask is the guard (per-cell runtime guard infeasible — grid is ~7 M cells).
#### Todos
- [ ] (optional) cheap load-time integrity check: sample `isWater`, log/flag a stale / un-masked asset
#### Rules
- Per-cell runtime `isWater` masking is infeasible (~7 M cells); the bake mask is authoritative. Canvas `PorterDuff.CLEAR` of land polygons is the fallback.
#### Key Files
- `ui/map/DepthBitmap.kt`, `data/depth/DepthRepository.kt`, `ui/map/MapScreen.kt`, `data/depth/DepthZoneMask.kt`

### isobar-precision
B2 · `feature/depth-warning-2` — isobaths reflect data precision (suppress fine-over-coarse + style by confidence).
#### Todos
- [ ] On-device: verify both hues read over the deep-navy fill + magenta band; tune `zone.properties` if needed
#### Rules
- Fine shallow contours (≤10 m levels) only where source res ≤10 m (Litto3D/SDB); never fake a 2 m line from 115 m EMODnet.
- **Isobath line colour = data source.** EMODnet → dark blue, Litto3D → forest green; colours live in `zone.properties`, one entry per source; unmapped → muted grey. Dashing reserved for low-confidence fill.
- Verify both hues read over the deep-navy fill + magenta danger band on-device.
#### Key Files
- `data/model/Isobath.kt`, `data/depth/DepthIsobaths.kt`, `ui/map/MapScreen.kt`, `data/depth/DepthConstants.kt`

### danger-display
B3 · magenta low-depth overlay (via develop's `LowDepthWarningBitmap`) — configurable+persisted threshold, toggle, water-only, rebuild-on-change.
#### Todos
- [ ] (optional) source the default threshold from `zone.properties`/`ZoneConfig` — low value (already settings-configurable)
#### Rules
- **Do not re-implement** — extend develop's `LowDepthWarningBitmap` / `lowDepthWarningMaxM`; no parallel ramp-magenta path.
#### Key Files
- `ui/map/LowDepthWarningBitmap.kt`, `ui/map/MapScreen.kt`, `data/settings/SettingsManager.kt`, `data/depth/DepthConstants.kt`

### danger-alert
B4 · `feature/depth-warning-4` — the **ALARM** (pulse + banner; sound later). **Still needed** — develop shipped only the display overlay, not an at-the-boat alert.
#### Todos
- [ ] `DepthSample.isBelow(minDepthM)`
- [ ] Derived `shallowAlert` = `isWater && depthAtCenter.isBelow(threshold)` (+0.3 m hysteresis) → pulsing `DepthCard` + grounding banner
- [ ] Threshold: **reuse `AppSettings.lowDepthWarningMaxM`**, OR add a separate `dangerAlertMaxM` if the alarm should trip before the visual — decide
- [ ] Stub `onShallowAlert()` hook where sound/vibration will later go
#### Rules
- Display overlay exists (develop); B4 adds only the ALARM. Reuse `lowDepthWarningMaxM` unless a distinct alert threshold is wanted.
#### Key Files
- `data/model/DepthGrid.kt`, `ui/map/DashboardPanel.kt`, `ui/map/MapScreen.kt`, `ZoneConfig.kt`, `data/settings/SettingsManager.kt`

### edonet false alert
Coarse EMODnet (115 m) sampling emergent rocks reads above chart datum → false-shallow "depth". Two-layer fix: bake-time drop of above-datum cells + a runtime, configurable cutoff. Branch `feature/pink-fix-edonet`.
#### Todos
- [ ] On-device verify: −1.7 m readout → "—" at Cap d'Antibes (needs deploy)
- [ ] Gate the depth colour map (`DepthBitmap`): EMODnet shallow cells render as gray
- [ ] (later) reuse the gate for the B4 alarm so coarse EMODnet can't trip a false grounding alert
#### Rules
- Gate applies to readout, colour map, magenta overlay, and all isobath levels. Gated EMODnet shallow: readout → "—"; colour map → gray; overlay → transparent; isobaths → mask to NaN.
- EMODnet is a deep backbone, **not** a shallow source; Litto3D/SDB are authoritative nearshore.
- Litto3D coverage is thin (~24 k cells); the gate is the readout safeguard.
#### Key Files
- `data/depth/DepthMerge.kt`, `data/model/DepthGrid.kt`, `data/settings/SettingsManager.kt`, `ui/map/MapScreen.kt`, `res/values*/strings.xml`

### caching
Runtime layer bitmap caching — RawBuf disk cache (IntArray→FileChannel), measured 78ms colour / 52ms warning vs 980ms / 5178ms compute.
#### Todos
- [ ] Measure end-to-end cold-start improvement after cache (needs on-device deploy)
#### Rules
- Grid is ~7 M cells (41 MB protobuf); cache targets only the raster Bitmap output, not the grid.
- Cache key = `grid.metadata.fetchTimestampMs` + `emodnetShallowCutoffM` + `lowDepthWarningMaxM` + `lowDepthWarningMinOpacityPct`. Any change → rebuild + overwrite.
- RawBuf: `IntArray` → `ByteBuffer.allocateDirect()` → `FileChannel.write()`; read reverses.
- Progress weighted by measured timings (grid 10%, isobaths 21%, colour 11%, warning 58% of 8922ms).
#### Key Files
- `ui/map/MapScreen.kt`, `ui/map/DepthBitmap.kt`, `ui/map/LowDepthWarningBitmap.kt`, `ui/map/DepthViewModel.kt`, `data/depth/DepthRepository.kt`, `data/settings/SettingsManager.kt`, `data/depth/RasterCache.kt`, `data/model/GenerationProgress.kt`
#### Docs
- `xTrack/DepthMapping/260609_FEAT_PLN_DepthMapping_caching.md` — measurement results and RawBuf decision
- `xTrack/DepthMapping/260609_FEAT_PLN_DepthMapping_caching-rawbuf.md` — implementation plan

## Todos
- [ ] **NEXT:** B4 · danger-alert (alarm: `isBelow` + pulsing card/banner + sound stub). On-device verify B2 colours + edonet-gate at Cap d'Antibes. Option-3 gdal_contour parked.

## Rules
- Branch-per-feature off `feature/litto3d-shallow`: B1, B2, B3 from the base; **B4 from B3**. Commit only on explicit instruction.
- Two distinct configurable thresholds — display-danger (magenta overlay) and alert-danger (alarm) — each in `zone.properties` **and** settings.
- Reuse, don't reinvent: per-cell `DepthSource.nominalResM`/confidence; the Zone300Card pulse + settings/ZoneConfig tunables for the alert.

## Key Files
- See per-section Key Files.

## Docs
- `xTrack/DepthSafety/260608_FEAT_PLN_DepthSafety_plan.md` — the branch-per-feature workflow + per-branch design.
