# Context Hydration — Tracks — 2026-09-14

**Last Bake:** 2026-09-14 17:05 UTC — written by `#bake`; absence means never baked
**Branch:** feature/track-speed (created from origin/develop 2026-09-14)

## State

The BoatTrace→Tracks rename is complete and committed (`322f4cc`). The selected-track speed heatmap shipped on `feature/track-speed` over three `#implement` passes (`ae0b3d6`): ramp v5 at 5/7/12/15/35/70, the declared `track.heatmap.scaleTicks` table whose printed labels may differ from their positions, no count key, a `track.heatmap.scaleMinKn` foot at 2 kn, and a drawer-header eye toggle whose stored choice is now read at cold start. Its walk closed with the device E2E **dropped** rather than deferred. The render-mode generalization is **designed, not implemented**: D1–D11 are settled in `260914_FEAT_PLN_Tracks_render-modes.md` and its §5 work package is the code still to write, so the map keeps the old selected-track branch and the menu keeps the "Show dir & speed" row. The walk's level 1 for that change is **open** (Active 13): item 3 (selection cue in Colours) and item 10 (per-track ramp cost) are parked, and its state lives in the feature file, not here.

## Target Files

- `app/src/main/java/ykws/android/maro/config/HeatmapRamp.kt` — `TrackHeatmapMode` becomes `TrackRenderMode` (SIMPLE/DIR_SPEED/HEATMAP)
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the `TrackRenderMode` field and key; the two legacy reads/writes go
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the `track.heatmap.mode` parse goes; ramp, tick table and scale minimum stay
- `app/src/main/assets/maro.properties` — `track.heatmap.mode` and its comment block removed
- `app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt` — the mode decides every stored track's path; the fade and width reach the bands; mode-aware key list
- `app/src/main/java/ykws/android/maro/ui/map/TrackSpeedHeatmap.kt` — fade and width parameters on the banded path
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt` — Tracks rendering caption + switch, the retired row, the live block to the card's head
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt`, `OverlayLayerParams.kt` — the mode plus the eye's scoped override in place of the arrow flag
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the legend condition; `MapScreenSettingsOverlay.kt` — the Default Colors heading
- `app/src/main/res/values/strings.xml`, `res/values-fr/strings.xml` — three new labels, two deletions

## Next Step

Implement the plan's §5 package to §§3 and D1–D11, then `apk-build.bat` and the scoped tests; the eye ships session-only per §3c/§4, and the walk's items 3 and 10 stay parked.
