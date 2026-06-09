# Hydration — DepthSafety

**Last Bake:** 2026-06-09 00:14 UTC · branch `feature/pink-fix-edonet` (off `origin/develop` #46) · commit `a55b914`

## State — `edonet false alert` DONE (implemented, unit-tested, APK builds)
Fixes coarse EMODnet (115 m) reading above chart datum near rocks → negative / false-shallow "depth" (the −1.7 m off Cap d'Antibes) that looked like a grounding hazard.

- **Runtime gate** `DepthSample.gatedForEmodnetShallow(cutoff)` (`data/model/DepthGrid.kt`) — EMODnet shallower than cutoff → `DepthSample.NONE` ("—"). Wired in MapScreen as `depthReadout`, fed to both DashboardPanel call sites. **Readout-only** (map raster + low-depth overlay untouched).
- **Setting** `emodnetShallowCutoffM` (0–5 m, default 2.0; `SettingsManager`) + 0.5 m slider at the END of the **Advanced** settings section (`MapScreen`), EN+FR strings.
- **Bake-time guard:** `DepthMerge.mergeDeep` / `fillGaps` / DepthGrid shallow overload drop `v<0` (above-datum) — mirrors the shallow-raster guard. Affects the shipped grid only after a re-bake.
- **Tests:** `DepthSampleGateTest` (6) + `DepthMergeTest` deep-negative. `testDebugUnitTest` + `assembleDebug` green.

## Next
1. **On-device verify:** build + install APK → the Cap d'Antibes spot reads "—", not −1.7 m. **No re-bake needed** for the readout (runtime gate). Re-bake only to clean the map raster via the `v<0` guard.
2. PR `feature/pink-fix-edonet` → develop (branch is develop + 1 commit, clean merge).
3. Other DepthSafety work still open: **B4 danger-alert** (the alarm), **B1 water-only** re-bake/verify, on-device verify B2 colours.

## Context
Litto3D coverage in the baked grid is thin (~24 k cells / ~1.8 % of covered cells), so EMODnet leaks shallow at many nearshore spots — the gate is the readout safeguard; true ~5 m depth needs better Litto3D coverage (separate data-ingestion problem; user has a newer 678 MB tile staged in `app/src/main/assets/depth/`, NOT in the baker's `data/app-assets/depth/`).

## Key files
`data/depth/DepthMerge.kt`, `data/model/DepthGrid.kt` (`DepthSample.gatedForEmodnetShallow`), `data/settings/SettingsManager.kt`, `ui/map/MapScreen.kt` (`depthReadout` + Advanced slider), `res/values*/strings.xml`. Tests: `data/model/DepthSampleGateTest.kt`, `data/depth/DepthMergeTest.kt`.
