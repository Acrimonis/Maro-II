# Markers — Hydration Snapshot (2026-06-25 16:08 UTC)

**Baked:** 2026-06-24 16:12 UTC+2

## Active State
- **Subfeature:** menu-markers-normalization
- **Branch:** feature/markers

## What Changed This Session
1. **#merge onto develop** — Rebased `feature/markers` onto `origin/develop` (19 commits). Resolved conflicts in `MapScreen.kt` (4 regions: pinned tracks, stop detection, pinned transparency), `colors.properties`, `cmd_help_git.md`, `docs/settings-page-guidelines.md` (deleted upstream). Build ✅.
2. **Pushed feature/markers** — force push after rebase.
3. **PR link** — https://github.com/Acrimonis/Maro-II/compare/develop...feature/markers
4. **MARKERS section normalization** — Rebuilt MARKERS section in `TrackDrawerOverlay.kt` to match drawer card guidelines: `uiCardBackground` + 12dp radius + 16×10dp wide density padding + trailing `KeyboardArrowRight` chevron + removed ellipsis from label. Removed spurious `HorizontalDivider` before section header.

## Design Decisions
- Removed pinned tracks rendering from `MapScreen.kt` (referenced undefined `pinnedSummaries` — not part of feature/markers scope)
- Took incoming (97be575) on all settings-related conflicts to adopt `SettingsToggleRow` composable pattern
- Took HEAD on refactored `computeTrackPolylineAppearance` helper (fixed `historyTotal` → `total`)

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/TrackDrawerOverlay.kt` — MARKERS section normalization
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — conflict resolution, removed pinned tracks

## Next Steps
- Deploy and test marker layer toggle on device
- Verify zone fills disappear when toggling markers layer off
