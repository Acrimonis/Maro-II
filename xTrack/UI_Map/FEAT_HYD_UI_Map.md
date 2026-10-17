# Context Hydration — UI_Map — 2026-10-17

**Last Bake:** 2026-10-17 12:37 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met — no dependency was added, no machine-shaped data file was opened, every write followed the user's own orders, the device was never touched (the build and the scoped suites are named below), and every claim about the code rests on a file read.

## State

**Inspect mode, two landings on `feature/inspect`.** The recentre's quiet now ships at `map.inspect.dwellMs=1499` (was 1234; `AppConfig.uiMapInspectDwellMs` mirrors it and `InspectModePropertiesTest` pins both), and the frozen ladder is filtered to the viewport on screen at the pick (`InspectRanking.rank` applies the same overlap test `nearest` reads), so a Prev/Next walks only the items the screen shows. The step/focus fault was then fixed: the acquire point is captured once at arming and `refreshMotion` no longer rewrites it, and once the pause freezes the ladder `MapInspectEffects` gains `frozen`/`frozenCandidate` — the sweep stops publishing, the quiet clock stops re-arming the pause, and the gold paints the cursor's item — while `inspectAcquireAction` refuses any live open or close on a frozen ladder. `:app:assembleDebug` is SUCCESSFUL and the scoped `ui.map` + `config` suites are green.

**Known limit on the walk.** The ladder is the warmed candidate set — map-filtered markers while the marker layer shows, and non-live routes/tracks whose own layer and map filter hold — intersected with the viewport read at the pause and frozen there. An item hidden by a filter or layer, whose geometry failed to load, or that becomes visible only after the recentre's zoom is therefore outside the walk; the render cap is not a limit.

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/InspectMode.kt` — anchor seed, `frozen`/`frozenCandidate`, sweep and quiet stand-down, `inspectAcquireAction`'s `ladderFrozen`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — frozen state, guarded `onSweep`, cursor-owned marker gold
- `app/src/main/java/ykws/android/maro/ui/map/InspectRanking.kt` — viewport-filtered `rank`
- `app/src/main/assets/maro.properties` — `map.inspect.dwellMs=1499`, mirrored by `AppConfig.uiMapInspectDwellMs`
- `app/src/test/java/ykws/android/maro/ui/map/InspectRankingTest.kt`, `InspectAcquireTest.kt`, `config/InspectModePropertiesTest.kt` — the pins
- `xTrack/UI_Map/FEAT_DSC_UI_Map.md` — the two `## Implemented` entries

## Next Step
A device pass on `feature/inspect`: step the walk and confirm each step frames the stepped item with its gold and card agreeing, the pause fires once at 1499 ms, and the anchor never drifts; the feature's older owed passes stand beside it.
