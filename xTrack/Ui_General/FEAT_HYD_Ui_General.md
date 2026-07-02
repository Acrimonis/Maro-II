# Hydration: Ui_General

**Session:** filter everywhere — full implementation + refinements + audit complete.

**State:**
- `list sort [x]` — implemented
- `list extra sort [x]` — implemented
- `sort-list-cleanup [x]` — UPDATED removed, common logic extracted, tracks reactive
- `filter [x]` — complete
- `filter everywhere [x]` — complete: map rendering reads ListFilter, marker fan ON/OFF, menu drawer filter icons on section headers (TRACKS/MARKERS, right-aligned), Refresh always visible (inactive alpha), track filter gains UNPINNED, isLive scoped to dateRange only, ui-lists-guidelines updated

**Key Files:**
- `ListSortOrder.kt`, `ListFilter.kt`, `ListOverlayScaffold.kt`, `TrackViewModel.kt`, `MarkersViewModel.kt`, `TrackHistoryOverlay.kt`, `MarkerManagementOverlay.kt`, `OverlayLayer.kt`, `MapScreen.kt`, `SettingsManager.kt`, `FilterList.kt`, `FilterAlt.kt`, `Refresh.kt`, `MenuDrawerOverlay.kt`, `FanIconComponents.kt`

**Plan:**
- `xTrack/Ui_General/FEAT_PLN_Ui_General_filter-everywhere.md`

**Last Bake:** 2026-07-02 22:28 UTC
