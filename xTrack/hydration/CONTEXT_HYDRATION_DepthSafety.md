# Hydration — DepthSafety

**Last Bake:** 2026-06-08 19:16

## State (planning done; no DepthSafety code written yet)
New feature for 4 depth-safety branches off `feature/litto3d-shallow` (B1–B3 from base, **B4 from B3**):
- **B1 water-only** — per-cell runtime `isWater` guard found **INFEASIBLE** (~7 M-cell grid × a coastline
  spatial query = tens of seconds on-device). The **bake-time land-mask is the guard** (already committed
  `3c6ee3d`, in base — erases `!isWater` → transparent). B1 = **re-bake + verify**; optional cheap
  load-time integrity check; Canvas `PorterDuff.CLEAR` polygon-clear is the deferred fallback.
- **B2 isobar-precision** — `Isobath.lines` → `List<IsobathLine(points,source,confidence)>`; suppress fine
  contours over coarse source; dashed/dim low-confidence.
- **B3 danger-display** — configurable `dangerDisplayDepthM` (props + settings, default 1.5 m) →
  near-opaque magenta in `DepthColorRamp`; rebuild bitmap on setting change.
- **B4 danger-alert** (from B3) — configurable `dangerAlertDepthM` (default 2 m); `DepthSample.isBelow`;
  pulsing card + grounding banner; **sound stubbed** (app has no audio yet).
- Noted: the depth `GroundOverlay` linearly stretches an equirectangular bitmap → ~35 m mid-band Mercator
  approximation (0 at edges, smaller nearshore). Optional **B5 overlay-reproject** — user undecided.

## Next
1. **MERGE `feature/litto3d-shallow` → develop** (user driving). Commit the pending xTrack bake first if
   develop should carry it.
2. Create B1–B4 **from develop** (the plan says `feature/litto3d-shallow`; that = develop post-merge).
3. Decide B5 (overlay re-project) yes/no.

## Key files
`xTrack/FEATURE_SCOPE_DepthSafety.md`, `plans/depth-safety.md`; per-branch: `data/depth/DepthZoneMask.kt`,
`data/model/Isobath.kt` + `data/depth/DepthIsobaths.kt`, `ui/map/DepthColorRamp.kt` + `DepthBitmap.kt`,
`data/model/DepthGrid.kt` + `ui/map/DashboardPanel.kt`, `ZoneConfig.kt` + `data/settings/SettingsManager.kt`.
