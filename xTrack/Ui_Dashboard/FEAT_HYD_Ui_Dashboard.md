# Hydration — Ui_Dashboard

**Last Bake:** 2026-07-01 12:23 UTC
**Branch:** feature/markers-zones
**Build:** green

## Session State

WhereAmI match-result drawer overhaul: centered italic "in the middle of nowhere" empty state, per-row color accent bars (4dp) matching marker colors, icons (custom emoji or type-default 📍/⭕/🔴), flight-of-bird geometric distance for LineOfSightMatch, icon repositioned between name and distance ("NW of Cap Camarat · 🏖️ · 406 m"). Removed obsolete pin/unpin button from ViewingContent header. Unified all drawer header left padding to 24dp.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt` — MatchResultContent rewrite, buildMatchText icons+position, MatchRow composable, geometricDistanceToZone, DrawerHeader padding, removed pin/unpin button
- `app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt` — MarkerGeometry.iconFor() companion
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — typeIcon updated (📍/⭕/🔴)
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt` — markerFormatText uses iconFor()
- `app/src/main/java/ykws/android/maro/ui/markers/wizard/WizardTopBar.kt` — padding 16→24dp

## Next Step

Verify match-row rendering with multiple overlapping markers. Test no-icon, custom-icon, and type-default icon cases.
