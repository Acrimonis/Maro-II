# Hydration — Markers

**Last Bake:** 2026-07-18 06:11
**State:** multi-select-merge: implemented. Marker management list re-enabled with 3 multi-actions (Delete, Pin dropdown, Merge auto-markers). BUILD SUCCESSFUL.

## Summary
- **multi-select-merge** — Re-enabled multi-select on marker list with Delete (destructive + confirm), Pin (dropdown: all/unpin/toggle), Merge (auto-only, distance dialog + name + keep-originals). `mergeAutoMarkers()` computes centroid, consolidates data, conditionally deletes originals. 5 files, BUILD SUCCESSFUL.
- **auto-marker-dedup** — `addTempAutoMarker()` proximity dedup: scans existing `IDLE_AUTO` markers within `dedupRadiusM` (50m default). Unconfirmed nearby → update position & reuse ID. Confirmed nearby → skip. 4 files, BUILD SUCCESSFUL.
- **auto-marker-proximity** — `addTempAutoMarker()` sets `proximityOverrideM = AppConfig.boatMarkerAutoMarkerProximityM` (300m default). Defensive fallback in `proximityRange()` for `IDLE_AUTO` + `null` → 300m. New config key `track.boatMarker.autoMarker.proximityM=300`.
- **wizard-cleanup-race** — `wizardFinish()` was nulling `_wizardStep`/`editingMarkerId`/`_selectedMarkerId` synchronously before the IO coroutine completed → wizard state inconsistency → markers invisible until restart. Moved cleanup into `saveMarker()`/`updateMarker()` coroutines after `_drawerState = Hidden`.

## Modified Files
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt` — 3 MultiActionSpecs replacing emptyList(); onMergeMarkers param; MergeMarkersDialog
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — `mergeAutoMarkers(ids, name, keepOriginals)`
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — onMergeMarkers param + pass-through
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — wire onMergeMarkers → ViewModel
- `app/src/main/res/values/strings.xml` — `confirm_delete_markers`
- `xTrack/Markers/260718_FEAT_PLN_Markers_multi-select-and-merge.md` — plan
- `xTrack/Markers/FEAT_DSC_Markers.md` — front-matter, subfeature checkmark

## Pending
- `#todo markers: fix proximity of date points. Rays hit/test all of them.`
- `#todo gps: back to GPS point -> replace delay by swipe of card`
- `#todo: normalize localisation and fill holes`
