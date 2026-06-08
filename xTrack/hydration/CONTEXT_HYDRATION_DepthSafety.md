# Hydration — DepthSafety

**Last Bake:** 2026-06-08 21:53

## State (on `feature/depth-warning-2`; develop merged in, compile green)
Subs **1/4** done. Branch contains all of develop (merge `a83cd12`).
- **B2 isobar-precision — DONE (committed `1819487`, pushed):** `IsobathLine(points, source, confidence)`;
  fine-over-coarse suppression (mask coarse cells for fine levels); colour + width by source from
  `zone.properties`/`ZoneConfig` (litto3d `#1B5E20` +1 px, emodnet `#00008B` −1 px, dash conf≤35);
  Chaikin smoothing of Litto3D lines (`SpatialOperations.chaikin`). Unit-tested. **On-device colour verify pending.**
- **B3 danger-display — SUPERSEDED/done by develop's low-depth overlay** (merged): `LowDepthWarningBitmap`
  (magenta, water-only via **depth-gated isWater** — cheap), **configurable+persisted** `lowDepthWarningMaxM`
  (Settings slider + map toggle), rebuilds on change. Don't re-implement; extend it. Optional leftover:
  move the default to `zone.properties`.
- **B4 danger-alert — STILL NEEDED:** develop has only the *display*, not an at-the-boat alarm. Add
  `DepthSample.isBelow`, derived `shallowAlert` (isWater && isBelow + 0.3 m hysteresis) → pulsing `DepthCard`
  + grounding banner; reuse `lowDepthWarningMaxM` (or a separate `dangerAlertMaxM`); stub `onShallowAlert()` for sound.
- **B1 water-only** — bake-mask is the guard; re-bake + on-device verify pending.
- **Parked:** option-3 `gdal_contour` on full-res Litto3D = real contour fidelity (own subfeature); optional B5 overlay-reproject.

## Next
1. On-device verify B2 colours; PR `feature/depth-warning-2` → develop when ready.
2. **B4 · danger-alert** (the alarm) — the main remaining DepthSafety work.
3. B1 water-only re-bake/verify.

## Key files
B2: `data/model/Isobath.kt`, `data/depth/DepthIsobaths.kt`+`DepthConstants.kt`, `ui/map/ZoneConfig.kt`+`assets/zone.properties`, `ui/map/MapScreen.kt`, `spatial/SpatialOperations.kt`.
B3 (develop): `ui/map/LowDepthWarningBitmap.kt`, `data/settings/SettingsManager.kt`. B4: `data/model/DepthGrid.kt`, `ui/map/DashboardPanel.kt`.
