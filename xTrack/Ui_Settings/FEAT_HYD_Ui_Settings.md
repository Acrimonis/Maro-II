# Ui_Settings — Hydration (2026-09-02 16:47)

## State
Settings reorganized into 4 tabs (Layers / Navigation / Position / System). Layer master toggles moved to the map layer fan; Settings keeps per-layer params under section headers. Labels localized en/fr. Regenerate button closes the pane; Coastline gained a section header.

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — SettingsOverlay 4 tabs, LayersSettings, NavigationSettings, PositionSettings, SystemSettings
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — 4th scroll state threading
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — 8 expander flags removed
- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt` — orphaned toggleSpeedZonesVisibility removed
- `app/src/main/res/values/strings.xml` + `values-fr/strings.xml` — tab names + localized labels

## Next Step
None. BUILD SUCCESSFUL.
