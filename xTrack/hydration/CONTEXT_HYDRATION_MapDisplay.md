# Context Hydration — MapDisplay

**Last Bake:** 2026-06-08 20:23 (UTC)
**Branch:** feature/layout-lowdepth (off origin/develop)
**Active Subfeature:** layer-lowdepth

## State
Low-depth warning overlay complete: bright-magenta `GroundOverlay` above the depth raster for all
water shallower than a configurable threshold (Settings → Display slider 0.5–5.0 m, default 1.5,
persisted); own toggle. "Keep phone on" moved to top of Power saving. Mercator offset fixed by
latitude-banding BOTH depth + warning rasters (`addBandedOverlay`, 8 strips). **Land kept off all
depth layers at the DATA level:** `DepthZoneMask.apply` now also nulls `!isWater` cells; the
re-baked `nice-frejus.bin` is land-free, so the colour map (NaN→transparent) and isobaths (marching
squares skip NaN) avoid land for free, and the warning's `!isNaN` gate does too. Re-bake validation
`passed=true`, `datumMismatch=false`. assembleDebug green. Committed: DepthZoneMask + prebake-batch.md
(the `.bin` is gitignored — regenerate locally via `tools\bake-depth.bat`).

## Known issue (tracked todo)
Pink warning still laps ~½ cell (~12 m) onto land at the waterline — 25 m cells classified by
CENTRE, amplified by the warning's opaque paint (the colour map has the same residual but faint).
Fix: sub-cell water test (sample cell corners) OR vector-clip the warning bitmap to the coastline
polygon. Low-priority polish.

## Next
Decide + implement the bleed fix; on-device verify (offset gone, layers land-free, toggle/threshold persist).

## Target files
- `data/depth/DepthZoneMask.kt` (land mask, done); `ui/map/LowDepthWarningBitmap.kt` + `MapScreen.kt` (bleed fix)
- `spatial/CoastlineSpatialIndex.kt:403` `isWater`; re-bake via `DepthPrebakeTest` / `tools\bake-depth.bat`
- Doc: `docs/prebake-batch.md`
