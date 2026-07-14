# Hydration — Markers

**Last Bake:** 2026-07-14 08:47
**State:** auto-marker-dedup: plan written and reviewed — proximity dedup at creation time, 50m radius, skip-or-update strategy. Ready for implementation.

## Summary
- **auto-marker-proximity** — `addTempAutoMarker()` sets `proximityOverrideM = AppConfig.boatMarkerAutoMarkerProximityM` (300m default). Defensive fallback in `proximityRange()` for `IDLE_AUTO` + `null` → 300m. New config key `track.boatMarker.autoMarker.proximityM=300`.
- **wizard-cleanup-race** — `wizardFinish()` was nulling `_wizardStep`/`editingMarkerId`/`_selectedMarkerId` synchronously before the IO coroutine completed → wizard state inconsistency → markers invisible until restart. Moved cleanup into `saveMarker()`/`updateMarker()` coroutines after `_drawerState = Hidden`.

## Modified Files
- `app/src/main/assets/maro.properties` — `track.boatMarker.autoMarker.proximityM=300`
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — `boatMarkerAutoMarkerProximityM` field + loading
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — proximity on auto markers, wizard cleanup in coroutine
- `app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt` — `proximityRange()` fallback for IDLE_AUTO
- `plans/auto-marker-proximity-fix.md` — plan
- `xTrack/Markers/FEAT_DSC_Markers.md` — Implemented section, front-matter
- `xTrack/GPS/FEAT_DSC_GPS.md` — Todos section
- `xTrack/GLOBAL_CONTEXT.md` — Active Session Pointers, branch

## Pending
- `#todo markers: fix proximity of date points. Rays hit/test all of them.`
- `#todo gps: back to GPS point -> replace delay by swipe of card`
- `#todo: normalize localisation and fill holes`
