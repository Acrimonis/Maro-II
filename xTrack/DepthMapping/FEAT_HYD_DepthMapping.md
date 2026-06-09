# Session State — DepthMapping (emodnet-gate)

**Last Bake:** 2026-06-09 20:53 UTC

## State
- **Subfeature `emodnet-gate`:** [x] Done
- Tests: DepthSampleGateTest (11 tests) + DepthColorRampTest (5 tests) — all green
- Build: `assembleDebug` + `testDebugUnitTest` pass

## Key Decisions
- EMODnet shallow gate uses the same `emodnetShallowCutoffM` setting for all layers (colour map, warning overlay, isobaths, dashboard readout)
- Water/land discrimination for NoData colour uses `grid.source` byte check (not coastline spatial index) — avoids millions of expensive spatial queries
- `DepthColorRamp.argb()` kept as pure function returning 0 for NaN (transparent) — NoData colour handling moved to `DepthBitmap`
- Isobaths get a separate `maskEmodnetShallow()` pass (distinct from resolution-based `maskCoarseSources()`)
- Default NoData colour: `#FFCCCCCC` light grey, configurable via `zone.properties` → `ZoneConfig.nodataColor`

## Files Changed
- `data/model/DepthGrid.kt` — `depthGated()` method
- `ui/map/DepthBitmap.kt` — `emodnetCutoffM`, `nodataColor` params + water-aware via `grid.source` byte check
- `ui/map/DepthColorRamp.kt` — reverted to pure transparent
- `ui/map/LowDepthWarningBitmap.kt` — `emodnetCutoffM` param
- `data/depth/DepthIsobaths.kt` — `emodnetCutoffM` + separate `maskEmodnetShallow()` pass
- `ui/map/DepthViewModel.kt` — passes real cutoff + `ZoneConfig.nodataColor`
- `ui/map/ZoneConfig.kt` — loads `nodataColor` from properties
- `ui/map/MapScreen.kt` — wired `ZoneConfig.nodataColor`
- `assets/zone.properties` — `nodata.color=#FFCCCCCC`
- `ui/map/DepthColorRampTest.kt` — updated assertions

## Next Step
- Rebuild APK and on-device verify: EMODnet shallow gate on map layers, NoData colour on water gaps only (not land), isobaths skip gated cells
