# Markers — Hydration Snapshot (2026-06-25 16:08 UTC)

**Baked:** 2026-06-24 11:38 UTC+2

## Active State
- **Subfeature:** create-zones-flow
- **Branch:** feature/markers

## What Changed This Session
1. **Marker layer toggle fix** — `removeAllMarkerOverlays()` now cleans up `Polygon` fills (zone painting) and `MapEventsOverlay` (tap handler). Previously, toggling the markers layer off left zone fill polygons and tap overlays orphaned on the map.
2. **#merge onto develop** — Rebased `feature/markers` onto `origin/develop` (13 commits). Resolved conflicts in `GLOBAL_CONTEXT.md`, `colors.properties`, `FanIconComponents.kt`, `cmd_help_git.md`. Post-rebase build fixes: Canvas imports restored, `uiSettingsCardBackground` → `uiCardBackground`.
3. **Restored 7-step #merge procedure** in `docs/cmd_help_git.md` from commit `5e083cb`.

## Design Decisions
- `--ours` used for `GLOBAL_CONTEXT.md` conflicts (keep develop's version for session pointers)
- `--theirs` used for `colors.properties` (adopt semantic tokens from ColorManagement)
- `--ours` + manual fix for `FanIconComponents.kt` (keep Material Icons migration, restore Canvas imports)

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt` — Polygon + MapEventsOverlay cleanup
- `app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt` — Canvas imports restored
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt` — uiCardBackground rename
- `app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt` — uiCardBackground rename
- `docs/cmd_help_git.md` — 7-step merge procedure restored
- `app/src/main/assets/colors.properties` — semantic tokens adopted
- `xTrack/GLOBAL_CONTEXT.md` — develop version kept

## Next Steps
- Deploy and test marker layer toggle on device
- Verify zone fills disappear when toggling markers layer off
