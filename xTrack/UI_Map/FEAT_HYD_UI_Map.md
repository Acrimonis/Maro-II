# Context Hydration — UI_Map — 2026-10-17

**Last Bake:** 2026-10-17 13:06 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met — no dependency was added, no machine-shaped data file was opened, every write followed the user's own orders, the device was never touched (the build and the scoped suites are named below), and every claim about the code rests on a file read.

## State

**Inspect mode, three landings on `feature/inspect`.** The recentre's quiet ships at `map.inspect.dwellMs=1499` (was 1234; `AppConfig.uiMapInspectDwellMs` mirrors it and `InspectModePropertiesTest` pins both). The step/focus fault is fixed: the acquire point is captured once at arming, and once the pause freezes the ladder `MapInspectEffects` gains `frozen`/`frozenCandidate` — the sweep stops publishing, the quiet clock stops re-arming the pause, the gold paints the cursor's item, and `inspectAcquireAction` refuses a live open or close. The viewport cut is then withdrawn entirely: `InspectRanking` loses `InspectBounds` and the candidate bbox, and `nearest()`/`rank()` take `(anchor, candidates)` — the sweep's true closest and the walk both cover every filtered item the map holds, the live track excepted. `:app:assembleDebug` is SUCCESSFUL and the scoped `ui.map` + `config` suites are green.

**Candidate gates.** The set is the map-filtered markers while the marker layer shows, plus non-live routes/tracks whose own kind layer and map filter hold; a track whose geometry fails to load or has no points is dropped at warm time. The render cap is not a limit.

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/InspectMode.kt` — anchor seed, `frozen`/`frozenCandidate`, sweep and quiet stand-down, `inspectAcquireAction`'s `ladderFrozen`, full-list sweep and ladder
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — frozen state, guarded `onSweep`, cursor-owned marker gold
- `app/src/main/java/ykws/android/maro/ui/map/InspectRanking.kt` — full-list `rank`/`nearest`, no `InspectBounds`
- `app/src/main/assets/maro.properties` — `map.inspect.dwellMs=1499`, mirrored by `AppConfig.uiMapInspectDwellMs`
- `app/src/test/java/ykws/android/maro/ui/map/InspectRankingTest.kt`, `InspectAcquireTest.kt`, `config/InspectModePropertiesTest.kt` — the pins
- `xTrack/UI_Map/FEAT_DSC_UI_Map.md` — the three `## Implemented` entries

## Next Step
A device pass on `feature/inspect`: step the walk and confirm each step frames the stepped item with its gold and card agreeing, the walk covers every filtered item, the pause fires once at 1499 ms, and the anchor never drifts; the feature's older owed passes stand beside it.
