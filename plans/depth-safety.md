<!-- scope: feature -->

# DepthSafety — branch-per-feature workflow

Make the depth layer navigation-safe, delivered as four branches off `feature/litto3d-shallow`
(B1, B2, B3 from the base; **B4 from B3**), one xTrack subfeature each.

## Base
`feature/litto3d-shallow` contains: litto3d-east merge, bold red→green source label, APK-OOM fix, and
the **bake-time land-mask** (`DepthZoneMask.apply` erases `!isWater` cells → baked grid is water-only).

## B1 · `feature/depth-warning-1` — no depth colour when `!isOnWater` (runtime guard + bake mask)
Bake mask is in the base; add a **runtime guard** so land never paints even from a stale grid: thread
the coastline `isWater` index into `DepthBitmap.build(grid, isWater)` → transparent where `!isWater`.
Wire coastline access into the depth render path (`DepthRepository`/`MapScreen`). Then re-bake + verify.

## B2 · `feature/depth-warning-2` — isobaths reflect data precision (Both)
- `Isobath.lines: List<List<LatLng>>` → `List<IsobathLine(points, source, confidence)>`.
- `DepthIsobaths.build`: sample each polyline's grid cells (`sourceAt/confidenceAt`) → dominant source +
  min confidence; **drop** fine levels (≤ `ISOBATH_FINE_LEVEL_MAX_M`) where source too coarse
  (`nominalResM > ISOBATH_FINE_MAX_RES_M`); tag survivors.
- `drawIsobaths`: confidence `≥ ISOBATH_LOWCONF_MAX` → solid/full alpha; below → `DashPathEffect` + dim.
- Constants in `DepthConstants.kt`; update all `Isobath.lines` consumers.

## B3 · `feature/depth-warning-3` — DISPLAY danger depth + very-visible overlay
- Configurable `dangerDisplayDepthM` in BOTH `zone.properties`/`ZoneConfig` (default 1.5 m) and
  `AppSettings`/`SettingsManager` (+ settings UI), mirroring the zone300 tunables.
- `DepthColorRamp.argb`: near-opaque magenta for `0 ≤ depth ≤ dangerDisplayDepthM`. Thread the threshold
  settings → `DepthBitmap.build` → ramp; **rebuild the bitmap when the setting changes**.

## B4 · `feature/depth-warning-4` (from B3) — ALERT danger depth + alarm (visual now, sound later)
- Configurable `dangerAlertDepthM` in BOTH `zone.properties`/`ZoneConfig` (default 2 m) and settings (+ UI).
- `DepthSample.isBelow(minDepthM) = hasData && !depthM.isNaN() && depthM < minDepthM`.
- Derived `shallowAlert = isWater && depthAtCenter?.isBelow(dangerAlertDepthM)` (+0.3 m hysteresis).
- Visual: pulsing `DepthCard` + full-width grounding banner (reuse Zone300Card pulse). Leave a stub
  `onShallowAlert()` for future sound/vibration — audio is a separate follow-up.

## Verification
Per-branch unit tests where pure (isobath suppression, `isBelow`, ramp thresholds) + `apk-build` /
`apk-deploy`. B1 also re-bakes (`apk-bake.bat depth`) and checks the tint stops at the waterline.
