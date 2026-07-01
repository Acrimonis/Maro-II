# Hydration: Ui_General

**Session:** list sort subfeature — implemented. BUILD SUCCESSFUL.

**State:**
- `list sort [x]` — implemented
  - `ListOverlayScaffold<T : ListableItem>` — generic scaffold composable (sort dropdown, swipe-to-delete, snackbar, overlay shell)
  - `ListAction` sealed class — 8 variants: SoftDelete, UndoDelete, PermanentDelete, SelectItem, EditItem, ExportGpx, RefreshList, RefreshLayer
  - `ListSortState` — field (TITLE/CREATED/UPDATED) + descending + pinnedGrouped
  - Sort dropdown: field selector with direction arrow inside, "Group pinned items" toggle with separator
  - Deferred batch delete for both lists — scaffold owns pendingDeletes
  - TrackHistoryOverlay + MarkerManagementOverlay thinned to scaffold consumers
  - Map polylines refresh on sort change via mapView?.invalidate()
  - Animations: platform spring() defaults, snackbar tween(250)

**Key Files:**
- New: `ListOverlayScaffold.kt`, `ListAction.kt`, `ListSortOrder.kt` (→ `ListSortField` + `ListSortState`)
- Modified: `ListableItem.kt` (+isLive), `Track.kt` (+isLive var), `SettingsManager.kt` (+trackListSort/markerListSort), `TrackViewModel.kt` (sort + isLive), `MarkersViewModel.kt` (sort + refreshSort), `TrackHistoryOverlay.kt`, `MarkerManagementOverlay.kt`, `OverlayLayer.kt`, `MapScreen.kt`

**Leftover:**
- Dead code in MarkersViewModel: pendingDeletes, softDeleteMarker, undoDeleteMarker, commitPendingDeletes
- docs/ui-lists-guidelines.md not yet created

**Future (#todo):** Extend scaffold for per-type sort fields (e.g., track length)

**Last Bake:** 2026-07-01 22:25 UTC
