# Context Hydration — Tracks — 2026-09-16

**Last Bake:** 2026-09-16 10:05 UTC — written by `#bake`; absence means never baked
**Branch:** feature/no-black-casing, created from `b2f6d69`

## State

Two passes on this branch are shipped and recorded. The first tokenised the map's chrome: the five squares
read `ui.map.toggle.*`, both cards read `ui.map.overlay.*` over one shared `ui.map.surface.inactive`, and
the `ui.button.disabled.*` trio went with them. The second made the speed scale interactive — it keeps its
display rule exactly as it was, since `legendVisibleFor`, `legendVisibleForState` and `bandedStrokeOnMap`
carry no new parameter and no body change, while one persisted `trackLegendExpanded`
(`track_legend_expanded`, default true, so an untouched install shows the card) decides which of two faces
it wears. The expanded face keeps its geometry and is now its own collapse target; the collapsed face is
`LegendToggleButton`, one 44 dp square on the row's own recipe carrying the ⏱ stopwatch written
`\u23F1\uFE0F` with no active state of its own, both faces taking one hoisted `legendAnchor` value that the
card widens alone. The `#implement` pipeline's Ask findings were then closed in two Code hops, the first
correcting the shipped entry rather than extending it: the two-face predicate was deleted outright, its
`legendAllowed` arm having been dead behind the branch's own guard, which puts the scoped run back at 126
with `TrackRenderModePathTest` at its original 15.

**Open and recorded:** the device run — the stopwatch's weight beside the row and whether the selector
really yields the colour form, the square's landing in both orientations, both taps, the value across a
restart, and the gate interplay; F2 accepted, the card's gesture capture and merged semantics being the two
costs D3 buys; F5 a post-task suggestion, the collapsed square being the fifth hand-copied copy of the
row's recipe; and the previous pass's own residues — the readerless keys and `EarthWaterIcon`'s dead arm.

## Target Files

- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — `trackLegendExpanded`, its key, its read and its write
- `app/src/main/java/ykws/android/maro/ui/map/MapControls.kt` — `LegendToggleButton` on the row recipe with the stopwatch
- `app/src/main/java/ykws/android/maro/ui/map/TrackSpeedLegend.kt` — the card as the collapse target
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the two-face branch and the hoisted `legendAnchor`
- `app/src/main/res/values/strings.xml` and `values-fr/strings.xml` — the collapse and expand descriptions

## Next Step

Run the owed device pass against the shipped build, judging the stopwatch's weight and colour form beside
the row's four, the square's landing under the GPS square in both orientations, both taps, the value across
a restart, and the gate interplay when the banded stroke comes and goes — then clear F2 and F5 by decision.
