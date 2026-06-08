---
name: DepthSafety
status: active
created: 2026-06-08 18:25
modified: 2026-06-08 19:16
active_subfeature: water-only
subs_total: 4
subs_done: 0
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

### isobar-precision  [ ]
B2 · `feature/depth-warning-2` — isobaths reflect data precision (suppress fine-over-coarse + style by confidence).
#### Todos
- [ ] `Isobath.lines` → `List<IsobathLine(points, source, confidence)>`
- [ ] `DepthIsobaths.build`: sample per-line source/confidence; drop fine levels over coarse source
- [ ] `drawIsobaths`: dashed/dim for low confidence, solid/full for high
- [ ] Constants: `ISOBATH_FINE_LEVEL_MAX_M=10f`, `ISOBATH_FINE_MAX_RES_M=10.0`, `ISOBATH_LOWCONF_MAX=65`
#### Rules
- Fine shallow contours (≤10 m levels) only where source res ≤10 m (Litto3D/SDB); never fake a 2 m line from 115 m EMODnet.
#### Key Files
- `data/model/Isobath.kt`, `data/depth/DepthIsobaths.kt`, `ui/map/MapScreen.kt`, `data/depth/DepthConstants.kt`

### danger-display  [ ]
B3 · `feature/depth-warning-3` — configurable DISPLAY danger depth + very-visible magenta overlay in the colour layer.
#### Todos
- [ ] `dangerDisplayDepthM` in `zone.properties`/`ZoneConfig` (default 1.5 m) + `AppSettings`/`SettingsManager` + settings UI control
- [ ] `DepthColorRamp.argb`: near-opaque, maximally-visible magenta for `0 ≤ depth ≤ threshold`
- [ ] Thread the threshold settings → `DepthBitmap.build`; rebuild the bitmap when the setting changes
#### Rules
- Display threshold is independent of the alert threshold.
#### Key Files
- `ui/map/DepthColorRamp.kt`, `ui/map/DepthBitmap.kt`, `ui/map/MapScreen.kt`, `ZoneConfig.kt`, `data/settings/SettingsManager.kt`

### danger-alert  [ ]
B4 · `feature/depth-warning-4` (**from B3**) — configurable ALERT danger depth + alarm (visual now, sound later).
#### Todos
- [ ] `dangerAlertDepthM` in `zone.properties`/`ZoneConfig` (default 2 m) + `AppSettings`/`SettingsManager` + settings UI control
- [ ] `DepthSample.isBelow(minDepthM)`
- [ ] Derived `shallowAlert` (isWater && isBelow, +0.3 m hysteresis) → pulsing `DepthCard` + grounding banner (reuse the Zone300Card pulse)
- [ ] Stub `onShallowAlert()` hook where sound/vibration will later go
#### Rules
- Visual-only now; audio is a separate follow-up (new capability — design-deviation gate).
#### Key Files
- `data/model/DepthGrid.kt`, `ui/map/DashboardPanel.kt`, `ui/map/MapScreen.kt`, `ZoneConfig.kt`, `data/settings/SettingsManager.kt`

## Todos

## Rules
- Branch-per-feature off `feature/litto3d-shallow`: B1, B2, B3 from the base; **B4 from B3**. Commit only on explicit instruction.
- Two distinct configurable thresholds — display-danger (magenta overlay) and alert-danger (alarm) — each in `zone.properties` **and** settings.
- Reuse, don't reinvent: per-cell `DepthSource.nominalResM`/confidence (already on the grid); the Zone300Card pulse + settings/ZoneConfig tunables for the alert.

## Key Files
- See per-subfeature Key Files.

## Docs
- `plans/depth-safety.md` — the branch-per-feature workflow + per-branch design.
