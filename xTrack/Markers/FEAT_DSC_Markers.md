---
name: Markers
status: active
created: 2026-06-22 11:52
modified: 2026-06-26 06:07
active_subfeature: marker-pin-tri-state
---

# Feature: Markers

**Description:**
User-defined markers on the map — Pin, Circle, and Corridor geometries. Sea-distance-gated proximity matching (land-blocking with 10° sampling + 10m grazing tolerance). On-demand "where am I?" query via boat marker tap. Drawer panel (portrait=bottom, landscape=left, no scrim — close via back/Cancel) for creation, editing, and match results. Management page with swipe-to-delete (track list paradigm). OSMdroid native overlays for rendering (Polyline + Marker).

## Subfeatures

### data-model  [x]
### persistence  [x]
### land-blocking  [x]
### match-resolution  [x]
### overlay-rendering  [x]
### creation-ui  [x]
### management-page  [x]
### create-zones-flow  [x]
### clean-up-wizard-next  [x]  *(deprecated — superseded by whereami-rework)*
### whereami-rework  [x]
### debug-wia  [x]
### menu-markers-normalization  [x]
### marker-pin  [x]
### marker-pin-tri-state  [x]

## Key Files
- `app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt`
- `app/src/main/java/ykws/android/maro/data/markers/UserMarkerRepository.kt`
- `app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

## OwnedFiles
- `app/src/main/java/ykws/android/maro/data/model/markers/`
- `app/src/main/java/ykws/android/maro/data/markers/`
- `app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt`

## Docs
- `xTrack/Markers/FEAT_PLN_Markers_debug-wia.md` — debug mode for whereAmI(): per-marker diagnostic logcat, map-tap handler, visible-marker dump
- `xTrack/Markers/FEAT_PLN_Markers_whereami-rework.md` — "where am I?" rework: smallest-first depth-first tree traversal, flat result model
- `xTrack/Markers/FEAT_PLN_Markers_clean-up-wizard-next.md` — [deprecated] Phase 5+6 from create-zones-flow
- `xTrack/Markers/FEAT_PLN_Markers_create-zones-flow.md` — wizard creation flow design (step-by-step, keyboard handling, replace dashboard)
- `xTrack/Markers/FEAT_PLN_Markers_user-markers-design.md` — full design plan (data model, algorithm, UI, 6 phases)
- `xTrack/Markers/FEAT_PLN_Markers_discussion-notes.md` — implementation plan evaluation (7 gaps resolved)
- `xTrack/Markers/FEAT_PLN_Markers_ui-review.md` — UI shortcomings review (12 decisions)
- `xTrack/Markers/FEAT_PLN_Markers_delta-analysis.md` — plan vs implementation delta
- `xTrack/Markers/FEAT_PLN_Markers_fix-plan.md` — 9-fix plan (scrim, position tracking, etc.)
- `xTrack/Markers/FEAT_PLN_Markers_two-pass-review.md` — two-pass code + feature review (13 fixes)
