# Hydration — Markers

**Last Bake:** 2026-07-14 08:50
**State:** auto-marker-dedup: implemented. `addTempAutoMarker()` scans IDLE_AUTO markers within 50m (configurable). Unconfirmed→update+reuse. Confirmed→skip. BUILD SUCCESSFUL.

## Summary
- **auto-marker-dedup** — `addTempAutoMarker()` proximity dedup: scans existing `IDLE_AUTO` markers within `dedupRadiusM` (50m default). Unconfirmed nearby → update position & reuse ID. Confirmed nearby → skip. 4 files, BUILD SUCCESSFUL.
- **auto-marker-proximity** — `addTempAutoMarker()` sets `proximityOverrideM = AppConfig.boatMarkerAutoMarkerProximityM` (300m default). Defensive fallback in `proximityRange()` for `IDLE_AUTO` + `null` → 300m. New config key `track.boatMarker.autoMarker.proximityM=300`.
- **wizard-cleanup-race** — `wizardFinish()` was nulling `_wizardStep`/`editingMarkerId`/`_selectedMarkerId` synchronously before the IO coroutine completed → wizard state inconsistency → markers invisible until restart. Moved cleanup into `saveMarker()`/`updateMarker()` coroutines after `_drawerState = Hidden`.

## Modified Files
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — `addTempAutoMarker()` proximity dedup before creation
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — guard `setActiveSessionAutoMarkerId` on non-empty return
- `app/src/main/assets/maro.properties` — `track.boatMarker.autoMarker.dedupRadiusM=50`
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — `boatMarkerAutoMarkerDedupRadiusM` field + loading
- `xTrack/Markers/FEAT_PLN_Markers_auto-marker-dedup.md` — plan
- `xTrack/Markers/FEAT_DSC_Markers.md` — front-matter, subfeature checkmark

## Pending
- `#todo markers: fix proximity of date points. Rays hit/test all of them.`
- `#todo gps: back to GPS point -> replace delay by swipe of card`
- `#todo: normalize localisation and fill holes`
