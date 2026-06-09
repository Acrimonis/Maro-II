---
name: DepthSafety
status: active
created: 2026-06-08 18:25
modified: 2026-06-09 00:14
active_subfeature: edonet false alert
subs_total: 5
subs_done: 3
one_liner: Make the depth layer navigation-safe — water-only colour, precision-aware isobaths, a very-visible sub-danger-depth overlay, and a configurable shallow-water alarm.
---

# Feature: DepthSafety

**Description:** The depth map is a navigation-safety surface but has four gaps: the colour layer can
paint on land; isobaths are drawn uniformly so coarse EMODnet contours look as precise as 1 m Litto3D;
there is no standout "you can ground here" mark; and there is no shallow-water alarm. Delivered as four
branches off `feature/litto3d-shallow` — B1, B2, B3 from the base, **B4 from B3** — one subfeature each.

## Subfeatures

### water-only  [ ]
B1 · `feature/depth-warning-1` — no depth colour when `!isOnWater`. **Bake-mask is the guard** (per-cell runtime guard infeasible — grid is ~7 M cells).
#### Todos
- [x] Bake-time land-mask: `DepthZoneMask.apply` also erases `!isWater` cells (committed 3c6ee3d, in base)
- [ ] Re-bake + on-device verify the tint stops at the waterline
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
- [ ] (later) reuse the gate for the B4 alarm so coarse EMODnet can't trip a false grounding alert
#### Rules
- Gate is **readout-only** (DepthViewModel→MapScreen `depthReadout`); the map colour raster + low-depth overlay read the raw grid (unchanged). The bake-time `v<0` guard only affects the shipped grid after a re-bake.
- EMODnet is a deep backbone, **not** a shallow source: below the cutoff it must not drive a nearshore depth/alert. Litto3D/SDB are authoritative nearshore.
- Litto3D coverage is thin (~24 k cells in the baked grid) → coarse EMODnet leaks shallow at many spots; the gate is the readout safeguard, real depth needs Litto3D.
#### Key Files
- `data/depth/DepthMerge.kt`, `data/model/DepthGrid.kt` (`DepthSample.gatedForEmodnetShallow`), `data/settings/SettingsManager.kt`, `ui/map/MapScreen.kt`, `res/values*/strings.xml`

## Todos
- [ ] **NEXT:** edonet-false-alert done on `feature/pink-fix-edonet` (off develop) — runtime EMODnet shallow gate + `emodnetShallowCutoffM` setting + bake-time `v<0` guard; on-device verify pending. Remaining DepthSafety work: **B4 · danger-alert** (alarm: `isBelow` + pulsing card/banner + sound stub) and **B1 · water-only** re-bake/verify. On-device verify B2 colours. Option-3 gdal_contour parked.

## Rules
- Branch-per-feature off `feature/litto3d-shallow`: B1, B2, B3 from the base; **B4 from B3**. Commit only on explicit instruction.
- Two distinct configurable thresholds — display-danger (magenta overlay) and alert-danger (alarm) — each in `zone.properties` **and** settings.
- Reuse, don't reinvent: per-cell `DepthSource.nominalResM`/confidence (already on the grid); the Zone300Card pulse + settings/ZoneConfig tunables for the alert.

## Key Files
- See per-subfeature Key Files.

## Docs
- `plans/depth-safety.md` — the branch-per-feature workflow + per-branch design.
