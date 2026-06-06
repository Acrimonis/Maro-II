# Context Hydration — UiThingies

**Baked:** 2026-06-06

**Session Summary:** Settings cleanup — removed "Position par défaut" lat/lon text fields from the settings overlay and added a "Zone 300 m" toggle as the second row in the "Affichage" section. Added `zone300Visible: Boolean` to `AppSettings` with full SharedPreferences persistence. Branch renamed from `feature/merge-whatever` to `feature/cleanup-settings`.

**Target Files:**
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — added `zone300Visible` field, persistence key, load/save
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — removed lat/lon fields, added zone300 toggle, filter zone300 data in MapContent
- `xTrack/GLOBAL_CONTEXT.md` — focus switched to UiThingies
- `xTrack/FEATURE_SCOPE_UiThingies.md` — added `hideLAyers` subfeature

**Next:** Await user direction — possible next steps: further settings ergonomics, UI polish, or switching to another feature.
