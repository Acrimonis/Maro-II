# Hydration: Ui_General

**Session:** Color token rename + opacity bump — `ui.settings.card.background` → `ui.card.background`, 10%→20% white.

**State:**
- `card background rename [x]` — `ui.settings.card.background` → `ui.card.background` in colors.properties, AppConfig property `uiSettingsCardBackground` → `uiCardBackground`, 26 references across 4 Kotlin files
- `opacity bump [x]` — `#1AFFFFFF` (10%) → `#33FFFFFF` (20%) in colors.properties + AppConfig default
- Removed empty subfeatures ButtonColors + SVSpacing

**What happened:**
- Renamed `ui.settings.card.background` → `ui.card.background` in colors.properties — drops misleading "settings" scope since it's the universal card surface across all overlays.
- Bumped opacity from 10% white (`#1AFFFFFF`) → 20% white (`#33FFFFFF`) for better card definition against the dark navy background.
- Renamed `AppConfig.uiSettingsCardBackground` → `AppConfig.uiCardBackground` and updated 26 references across 4 files.
- Removed empty subfeatures ButtonColors and SVSpacing from DSC.

**Key Files:**
- `app/src/main/assets/colors.properties` — `ui.card.background=#33FFFFFF` (was `ui.settings.card.background=#1AFFFFFF`)
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — `uiCardBackground` property (was `uiSettingsCardBackground`)
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — 19 references
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt` — 3 references
- `app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt` — 3 references
- `app/src/main/java/ykws/android/maro/ui/map/TrackDrawerOverlay.kt` — 1 reference

**Last Bake:** 2026-06-24 11:44 UTC
