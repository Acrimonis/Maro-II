# Context Hydration — UiThingies

**State:** active — all 3 subfeatures complete (onwater-button, settings, zoom-and-position).
**Baked:** 2026-06-06

**Completed:**
- Moved `EarthWaterIcon` from `DashboardPanel` to `MapContent` top-left.
- Settings button (⚙) at top-right matching ZoomButton style.
- Full-screen settings overlay with proper back navigation (← button + `BackHandler`).
- `SettingsManager` with SharedPreferences persistence (`AppSettings` data class).
- Coastline visibility toggle wired to polyline rendering.
- Default map center lat/lon text fields in settings.
- Fixed disconnected StateFlow bug (bridge coroutine between `SettingsManager` and ViewModel).
- Settings overlay now wraps the entire screen (both map + dashboard).
- Map center lat/lon/zoom persisted on every pan/zoom via `updateMapCenter`/`updateZoomLevel`.
- Restored position on app start (map + marker).
- Fixed save-on-exit / restore-on-start race condition: ViewModel changed to `AndroidViewModel`, settings loaded in constructor before composition.
- Fixed boat marker zoom not synced on startup: zoom buttons push zoom immediately; factory calls `onZoomChanged` after MapView setup; `LaunchedEffect` re-syncs on mapView ready.
- Fixed boat marker wrong size on startup (isWater/distanceToShore defaults): persisted `isWater` and `distanceToShore` alongside position in `savePosition()`; seeded StateFlows from persisted values.

**Key files:**
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/MainActivity.kt`
