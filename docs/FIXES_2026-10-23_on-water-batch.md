<!-- scope: review -->
# Batch two: battery and responsiveness on the water (2026-10-23)

> The second batch after the review (`REVIEW_2026-10-22_response-to-assessment.md`, "Status"), on
> `feature/on-water-battery`, stacked on the first PR's branch because every item builds on it.
> **Nothing was compiled or run** (no JDK or Android SDK on the authoring machine); the build, the
> unit suite and a device pass remain the first checks. Paths are relative to
> `app/src/main/java/ykws/android/maro/`.

## 1. The six items

| Finding | Fix | Files |
|---|---|---|
| A10 The service held a 1 Hz GPS fix all day, recording or not | The service's GPS cadence follows the recorder: one fix a second while a track records, a standby cadence while nothing does (`tracking.standbyIntervalMs` = 5 s, `tracking.standbyMinDistanceM` = 10 m, new keys with the other `tracking.*` tunables) — enough for the geofence auto-start and the stop detector | `data/track/TrackRecordingService.kt`, `config/AppConfig.kt`, `app/src/main/assets/maro.properties` |
| A15 Every pan rewrote all ~150 settings keys and re-emitted the settings object once a second | `SettingsManager.persistViewport` writes the five viewport keys alone and touches no flow; `update()` no longer writes them (one writer, read once by `load()` at start); the ViewModel's centre, zoom and ON_PAUSE saves use it | `data/settings/SettingsManager.kt`, `ui/map/NavigationViewModel.kt` |
| C05 Selecting a marker re-rasterised every marker on the main thread | Two effects: a structural one keyed on the marker list and draw settings that builds every overlay into a cache, and a selection one keyed on the selected id and the where-am-I result that rebuilds only the markers whose paint state moved and splices them in place; dot, halo and icon bitmaps are pooled by value, so a selection toggle swaps existing bitmaps | `ui/map/MarkerOverlay.kt`, `ui/map/MarkerHalo.kt` (doc) |
| N09 Two raster builds and two bitmaps per layer on a cold cache; overlapping regenerations; a warning raster blind to a coastline change | `DepthViewModel` is the single owner: `rasterLayers` holds at most one bitmap per step, `ensureRasterLayers` reads the cache or builds once per key with single-flight jobs, the Settings regeneration keeps its progress behaviour; the warning raster's key carries the coastline's generation stamp (the colour key is unchanged, so existing cache files stay valid) | `ui/map/DepthViewModel.kt`, `ui/map/MapDepthRasterEffects.kt`, `data/depth/RasterCache.kt` |
| U04 A single tap unlocked the locked screen | Unlock is a long-press; a tap only raises the lock banner; the banner and the square's description now say "hold to unlock" in both locales | `ui/map/MapLockLayer.kt`, both `strings.xml` |
| U03 Icon buttons at 24–40 dp in lists, drawers, swatches and wizard chips | One token `ui.touch.min` = 48 dp (`AppConfig.uiTouchMin`); every named control's hit area is at least that while its glyph keeps its size; the list header label weights so a 48 dp cluster wraps instead of overflowing | `ui/components/ListOverlayScaffold.kt`, `ui/components/MarkerCreateAction.kt`, `ui/map/TrackHistoryOverlay.kt`, `MarkerManagementOverlay.kt`, `MenuDrawerOverlay.kt`, `MapScreenSettingsOverlay.kt`, `ui/markers/wizard/steps/TypeSelectStep.kt`, `app/src/main/assets/ui.properties`, `config/AppConfig.kt` |

New test: `MarkerPaintDiffTest` (which markers a selection change touches).

## 2. Behaviour that changed on purpose

- **Standby GPS is slower.** With nothing recording, the service samples every 5 s and 10 m instead of
  every second and metre. The geofence auto-start's 10 s debounce and the 45 s stop detector both
  still see enough fixes; the UI's own GPS pipeline (`NavigationViewModel`) is unchanged, so the
  boat's displayed position is as live as before.
- **A settings change no longer rewrites the viewport.** The map centre, zoom, water flag and shore
  distance are written only by the viewport path, so a Settings toggle cannot move the saved
  viewport back to a stale value.
- **Unlocking takes a hold, not a tap.**
- **A corrupt raster cache falls through inside the ViewModel.** The previous `corruptCacheTick`
  plumbing is gone with the single owner.

## 3. Known gaps

- `DepthBitmap.build` and `LowDepthWarningBitmap.build` are not cancellation-cooperative, so a
  superseded build finishes its loop before its result is discarded; bounded, not eliminated.
- Dragging the unconfirmed marker during creation still rebuilds every marker overlay (a third effect
  keyed on it alone would remove that).
- The marker overlay's in-place splice relies on osmdroid's overlay manager honouring indexed `add`;
  it does in 6.1.18, and a device pass should confirm the z-order after a selection.

## 4. Strongest objection

The marker overlay rewrite is the largest unverified change in the batch; if a device pass shows any
marker drawn out of band after a selection, the structural effect alone (which still rebuilds
everything correctly) is the safe fallback until the splice is proven.
