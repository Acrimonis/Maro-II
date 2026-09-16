# Context Hydration — Tracks — 2026-09-16

**Last Bake:** 2026-09-16 12:32 UTC — written by `#bake`; absence means never baked
**Branch:** feature/no-black-casing, created from `b2f6d69`

## State

The speed scale is interactive and its chrome is no longer its own. One persisted `trackLegendExpanded`
(`track_legend_expanded`, default true, so an untouched install shows the card) decides which of two faces it
wears, the card collapsing to a 44 dp square carrying the ⏱ stopwatch written `\u23F1\uFE0F`; the display gate
is untouched, and the two arms share one hoisted `legendAnchor`. The map's painting then moved out from under
it: `ui.map.surface.*` is the single home for the fill, corner, padding, border and both alphas, `MapSurface`
and `MapToggleSquare` are the one path, `LegendToggleButton` was deleted into them, the fade is content-only,
and every surface wears one corner and one outline. The device report that followed found the scale's labels
breaking **inside the digit pair** — `softWrap = true` with `maxLines = 1` let Compose split "35" and keep one
line, printing only the first digit — so the labels now take `softWrap = false` and are centred in the full
bar-to-border space of a `weight(1f)` column.

**Open and recorded:** the label's 12 dp ceiling still binds, since two digits measure 11.2–11.4 dp against the
12 the 44 dp card leaves — real room means widening the card, narrowing the bar or shrinking the gap. The
device run owed over the collapse, the stopwatch's form, the square's landing, both taps, the value across a
restart and the gate interplay is now owed over the surface pass too: the outline on a 44 dp square, the
brighter off boxes and the recenter button's weight. `side_water` and `side_land` are unreferenced in Kotlin
since the dead description went, and the zone icon keeps its own 44 dp and 8 dp literals outside the family.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/TrackSpeedLegend.kt` — the collapse target and the flexible label column
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the two-face branch, the hoisted anchor and the row's readers
- `app/src/main/java/ykws/android/maro/ui/map/MapSurface.kt` — the shared path both faces now use
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — `trackLegendExpanded`, its key and its read

## Next Step

Run the owed device pass once, judging the collapse and the surface changes together, then decide the label's
ceiling — widening the card is the honest lever, and it would move `ui.map.toggle.square` with it.
