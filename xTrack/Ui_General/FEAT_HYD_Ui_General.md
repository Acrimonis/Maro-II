# Hydration: Ui_General

**Session:** dashboard-close-conditions — the selected-item dashboard's autoclose was re-assessed and
replaced by two rules. Shipped, reviewed in both directions, build SUCCESS, uncommitted.

## State
- **The rule set.** The marker/track detail drawer closes on exactly two conditions: a surface wanting
  the dashboard's own slot (the marker/track wizard, the other selected-item dashboard), or a change of
  the world the open item's Prev/Next walks. Everything else keeps it.
- **The keep.** The menu, settings and both lists are panels over the map: `panelOwnsRegion` in
  `OverlayLayer` hides the four detail slots while one is open, so the panel wins the region and the
  selection returns on close. The menu no longer closes anything.
- **The seams.** One `closeSelectedItemDashboards()` for the slot rule; the one-item guard lives inside
  `openTrackDetail`/`openMarkerDetail`; the ten referential callbacks close through
  `closeDashboardsForScopeChange`; `scopeClosed()` in `MarkersViewModel` is the pure core, covered by
  `DashboardScopeClosedTest`.
- The device report that started it — a control closing the dashboard — traced to the menu button one
  slot above the fan anchor; the fan never closed it.

## Target files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`, `OverlayLayer.kt`, `MarkerDrawer.kt`,
  `MarkersViewModel.kt`
- `app/src/test/java/ykws/android/maro/ui/map/DashboardScopeClosedTest.kt`

## Plans
- `xTrack/Ui_General/260917_FEAT_PLN_Ui_General_dashboard-close-conditions.md` (status: shipped)

## Next
Device pass owed: the panel stacking in both orientations, the two three-stripe icons tapped with a
track open, and the action matrix (displays keep, list filter/sort closes, `+` replaces). Optional
`#bake` items: the feature summary and this file are current; nothing else queued.

**Last Bake:** 2026-09-17 14:27 UTC
