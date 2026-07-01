# Hydration: Ui_General

**Session:** list sort subfeature — implemented. BUILD SUCCESSFUL. New branch `feature/list-extra-sort` for per-type sort fields extension.

**State:**
- `list sort [x]` — implemented (see FEAT_DSC ## Implemented)
- `list extra sort [ ]` — next: extend `ListSortField` for per-type sort fields (track length, marker distance)

**Key Files:**
- `ListOverlayScaffold.kt`, `ListAction.kt`, `ListSortOrder.kt`, `ListableItem.kt`, `Track.kt`, `SettingsManager.kt`, `TrackViewModel.kt`, `MarkersViewModel.kt`, `TrackHistoryOverlay.kt`, `MarkerManagementOverlay.kt`, `OverlayLayer.kt`, `MapScreen.kt`

**Leftover:**
- Dead code in MarkersViewModel: pendingDeletes, softDeleteMarker, undoDeleteMarker, commitPendingDeletes
- docs/ui-lists-guidelines.md not yet created

**Last Bake:** 2026-07-01 22:30 UTC
