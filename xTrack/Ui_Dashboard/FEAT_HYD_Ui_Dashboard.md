# Hydration — Ui_Dashboard

**Last Bake:** 2026-07-11 12:30 UTC
**Branch:** feature/landscape
**Build:** green

## Session State

Landscape dashboard status bar overlap fix: added `.windowInsetsPadding(WindowInsets.statusBars)` to landscape `DashboardPanel` modifier in MapScreen.kt so the dashboard renders below the system status bar instead of under it. Previously `fillMaxHeight()` started at y=0 with no top inset.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — landscape DashboardPanel modifier: + `.windowInsetsPadding(WindowInsets.statusBars)` at line 1623

## Next Step

Verify landscape dashboard renders with visible gap below status bar. Test on devices with different status bar heights (normal, notch, punch-hole).
