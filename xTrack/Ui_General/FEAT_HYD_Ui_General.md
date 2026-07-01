# Hydration: Ui_General

**Session:** list extra sort — plan reviewed and updated. `CustomSortField` approach confirmed. Track fields reduced to 3 (Distance/Total Time/Moving Time), marker fields to 1 (Origin). `colorIndex` excluded (categorical, not ordinal). `idleDurationSec` and `isLive` fallback removed from ViewModel comparator. Ready for implementation.

**State:**
- `list sort [x]` — implemented (see FEAT_DSC ## Implemented)
- `list extra sort [ ]` — plan: `CustomSortField` + `customFieldKey`, 3 track fields + 1 marker field, 7 files, backward-compat serialization

**Key Files:**
- `ListOverlayScaffold.kt`, `ListSortOrder.kt`, `TrackViewModel.kt`, `MarkersViewModel.kt`, `TrackHistoryOverlay.kt`, `MarkerManagementOverlay.kt`, `OverlayLayer.kt`

**Plan:**
- `xTrack/Ui_General/FEAT_PLN_Ui_General_list-extra-sort.md`

**Last Bake:** 2026-07-01 22:52 UTC
