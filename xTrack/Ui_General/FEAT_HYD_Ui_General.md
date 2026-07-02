# Hydration: Ui_General

# Hydration: Ui_General

**Session:** filter — list filters + sort UX normalization complete.

**State:**
- `list sort [x]` — implemented
- `list extra sort [x]` — implemented
- `sort-list-cleanup [x]` — UPDATED removed, common logic extracted, tracks reactive
- `filter [x]` — complete: extensible ListFilter (Map-based), filter dropdowns (dark bg), direction toggle (active/inactive), reset button, pinnedGrouped removed, unfiltered backing list pattern, ButtonColors normalization, geometry→origin cascade, day-based date ranges (midnight), live track exempt+first, FilterList/FilterAlt/Refresh standalone icons

**Key Files:**
- `ListSortOrder.kt`, `ListFilter.kt`, `ListOverlayScaffold.kt`, `TrackViewModel.kt`, `MarkersViewModel.kt`, `TrackHistoryOverlay.kt`, `MarkerManagementOverlay.kt`, `OverlayLayer.kt`, `MapScreen.kt`, `SettingsManager.kt`, `FilterList.kt`, `FilterAlt.kt`, `Refresh.kt`

**Plan:**
- `xTrack/Ui_General/FEAT_PLN_Ui_General_filter.md`

**Last Bake:** 2026-07-02 18:28 UTC
