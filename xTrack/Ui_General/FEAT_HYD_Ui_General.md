# Hydration: Ui_General

**Session:** list extra sort — localization pass. 6 files modified. Sort labels converted from hardcoded English to `stringResource()` (ResId approach). +16 string resources: 8 EN + 8 FR. `ListSortField.label` → `labelResId: Int`, `CustomSortField.label` → `labelResId: Int`.

**State:**
- `list sort [x]` — implemented (see FEAT_DSC ## Implemented)
- `list extra sort [x]` — implemented: CustomSortField, 3 track + 1 marker custom fields, localized EN+FR via ResId

**Key Files:**
- `ListSortOrder.kt`, `ListOverlayScaffold.kt`, `TrackViewModel.kt`, `MarkersViewModel.kt`, `TrackHistoryOverlay.kt`, `MarkerManagementOverlay.kt`, `values/strings.xml`, `values-fr/strings.xml`

**Plan:**
- `xTrack/Ui_General/FEAT_PLN_Ui_General_list-extra-sort.md`

**Last Bake:** 2026-07-02 10:59 UTC
