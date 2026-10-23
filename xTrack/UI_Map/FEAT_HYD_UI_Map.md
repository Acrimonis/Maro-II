# Context Hydration — UI_Map — 2026-10-23

**Last Bake:** 2026-10-23 16:20 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met — no dependency added, no machine-shaped data file opened, every write followed the user's orders (`#new`, then the inspect-mode fix and its instrumentation), the device was touched only through the user's own test and the agent read the resulting logcat, and every claim about the code rests on a file read.

## State
`feature/inspect-mode-fix` carries the inspect live-acquire fix and its instrumentation. The live acquire resolves a marker in `markersViewModel.mapMarkers`, the map-filtered world the sweep ranks, so a marker on the map but dropped by the list filter now opens its dashboard; `InspectFlow` logs the arming anchor, the frozen ladder, the recentre seat and each step. `apk-build.bat` green. The device logcat validated both issues: no `marker not found` line, and each session logged an `anchor`, a ladder ascending by distance, `recentre idx=0` equal to the ladder head, and steps walking strictly increasing distances — the anchor is preserved and the walk follows closest-out. Two non-blocking should-fixes from the review remain open: the map-world resolver is stated four times, and there is no log of the camera landing after the settle loop.

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — live-acquire marker world + log points
- `app/src/main/java/ykws/android/maro/ui/map/InspectMode.kt` — `INSPECT_LOG_TAG`, anchor and ladder logs
- `xTrack/UI_Map/261023_FEAT_PLN_UI_Map_inspect-mode-fix.md` — the plan
- `xTrack/UI_Map/FEAT_DSC_UI_Map.md` — `## Implemented` entry

## Next Step
Fold the two review should-fixes (resolver dedup, camera-landing log) the next time the branch is touched.
