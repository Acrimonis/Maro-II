# Hydration — DepthSafety

**Last Bake:** 2026-06-08 21:44

## State (B2 done; on `feature/depth-warning-2`, committed+pushed)
4 depth-safety branches off the litto3d-shallow base (B1–B3 from base, **B4 from B3**):
- **B2 isobar-precision — IMPLEMENTED + unit-tested + builds (this branch):**
  - `Isobath.lines` → `List<IsobathLine(points, source, confidence)>`.
  - `DepthIsobaths.build`: **fine-over-coarse suppression** (fine levels ≤10 m trace on a copy with
    coarse-source cells masked to NaN) + per-line source/confidence sampling + **Chaikin smoothing of
    fine-source (Litto3D) lines** (`SpatialOperations.chaikin`, 2 passes; EMODnet stays angular).
  - **Colour + width by source** from `zone.properties` via `ZoneConfig` (litto3d `#1B5E20` dark green
    **+1 px**; emodnet `#00008B` blue **−1 px**, floored 1 px; dash if confidence ≤35). Unmapped → grey.
  - `DepthConstants`: `ISOBATH_FINE_LEVEL_MAX_M/FINE_MAX_RES_M/LOWCONF_DASH_MAX/SMOOTH_ITERATIONS`.
  - `DepthIsobathsTest` passes (suppression + tagging).
  - Open: on-device verify the hues read over the fill; smoothing is cosmetic only.
- **B1 water-only** — bake-mask is the guard (re-bake + verify); runtime guard infeasible.
- **B3 danger-display** — configurable `dangerDisplayDepthM` (1.5 m) → magenta overlay; rebuild bitmap on change.
- **B4 danger-alert** (from B3) — `dangerAlertDepthM` (2 m); `DepthSample.isBelow`; pulse + banner; sound stubbed.
- **Parked:** option-3 `gdal_contour` on full-res Litto3D `.asc` at bake = REAL contour fidelity (own subfeature); optional B5 overlay-reproject (~35 m Mercator).

## Next
1. On-device verify B2 colours; tune `zone.properties` if needed.
2. Merge `feature/depth-warning-2` → develop (PR).
3. B3 · danger-display from develop.

## Key files
`data/model/Isobath.kt`, `data/depth/DepthIsobaths.kt` + `DepthConstants.kt`, `ui/map/ZoneConfig.kt` +
`assets/zone.properties`, `ui/map/MapScreen.kt` (drawIsobaths), `spatial/SpatialOperations.kt` (chaikin).
