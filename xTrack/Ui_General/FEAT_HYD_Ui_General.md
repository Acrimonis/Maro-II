# Hydration: Ui_General

**Session:** Track/marker drawers — pinned Prev/Next footer (no divider, 8dp above + 6dp below
the buttons, 12dp horizontal padding to align with the card), card bottom-anchored via
`DrawerScaffold.bottomAnchoredContent`, and dynamic portrait height: fixed `.height(animatedHeight)`
driven by a hidden `MeasureHeight` card probe (min = dashboard height, animated). Earlier this
session: compact list cards (lineHeight 14sp etc.) and the new stop-line description format.
BUILD SUCCESSFUL.

**Target files:**
- `DrawerScaffold.kt`, `MeasureHeight.kt`, `OverlayLayer.kt`, `MarkerDrawer.kt`,
  `TrackHistoryOverlay.kt`, `MarkerManagementOverlay.kt`, `TrackRecorder.kt`

**Last Bake:** 2026-09-03 20:53 UTC
