# Hydration: Ui_General

**Session:** filter-sort-fixes — framework fix. Removed duplicate `SettingsManager` instances from MarkersViewModel + TrackViewModel. Replaced with shared `StateFlow<AppSettings>` injected from NavigationViewModel via `observeSettings()`. MarkersViewModel also receives `updateSettings` callback for writes. Filter/sort callbacks pass `filter` param directly. Also: `whereAmISync`/`whereAmI` use `_allMarkers`, init races gated, FilterControl popup closes on selection, pending deletes committed on disposal. BUILD SUCCESSFUL.

**State:**
- `click-N-move [x]` — complete (6/6 steps + bugfix + chevron + docs)

**Key Files:**
- `ListAction.kt`, `MarkerManagementOverlay.kt`, `MapScreen.kt`, `MarkersViewModel.kt`, `UserMarker.kt`, `MenuDrawerOverlay.kt`
- `docs/ui-lists-guidelines.md`, `docs/ui-drawer-guidelines.md`

**Plan:**
- `xTrack/Ui_General/260705_FEAT_PLN_Ui_General_click-n-move.md`

**Last Bake:** 2026-07-05 09:17 UTC+2
