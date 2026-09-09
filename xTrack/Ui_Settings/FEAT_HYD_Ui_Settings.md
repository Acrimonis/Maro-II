# Ui_Settings — Hydration (2026-09-09)

## State
Settings page UI, persistence (SharedPreferences), settings widgets, and UX. **MapScreen settings
extraction (2026-09-09, `feature/filters-link`, commit b71a230):** the entire Settings overlay subtree was
moved out of the 5,841-line `MapScreen.kt` into a new same-package file
`MapScreenSettingsOverlay.kt` (~2,633 lines). `SettingsOverlay` stayed `internal` (OverlayLayer call
unchanged); page composables kept `private`; settings-only constants/labels made `internal`;
`RecordingExitSheet`/`ImportConflictSheet` remain in `MapScreen.kt`. Pure mechanical move — zero behavior
change; build SUCCESS. Plan: `260909_FEAT_PLN_Ui_Settings_mapScreen-settings-extract.md` (implemented).
Code-health sequencing: (1) this extraction done, (2) filters-link wiring next, (3) MapScreen orchestration
monolith refactor later.

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — Settings subtree removed (5,841 → 3,417 lines)
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — new, moved settings subtree
- `xTrack/Ui_Settings/260909_FEAT_PLN_Ui_Settings_mapScreen-settings-extract.md` — implemented

## Next Step
Finish the filters-link wiring on `feature/filters-link`, then refactor the MapScreen orchestration monolith.
