# Context Hydration — Markers — 2026-10-23

**Last Bake:** 2026-10-23 15:13 UTC — written by `#bake`. One session on `feature/color-picker`: the marker colour palette grew to twenty hues under a new perceptual rule, the two colour pickers became one raised onto the overlay ladder, and the selection was made legible in daylight.

**Directive trace:** All five covered action classes were met and none stopped the session — no dependency was added, no machine-shaped data file was opened, the work ran only on the user's own `#new`, `#impl`, `#bake`, `#commit`, `#merge` and `#push` rather than without an order, no device was touched by the agent (the device pass stays the user's), and every claim written here about the code carries a file read behind it, save the build and test results, which are carried forward as their own reports.

## State

**The palette's criterion became perceived difference (2026-10-23).** "40° of hue or 0.10 of lightness" was a proxy, and it had shipped a near-black ΔE 13 from Black and three entries inside one hue family. `MarkerColors` now states five clauses: every pair clears **ΔE ≥ 25** in CIELAB (CIE76); a hue family may carry more than one entry only as a **named** tier (Green/Light Green, Gold/Pale Yellow, Purple/Hot Magenta, Heavy Blue/Azure); nothing may read as water, which is the clause that now admits blues — a heavy navy and an azure, and no pale cyan; vivid over muted; and the eye overrules every number. Four hexes moved to satisfy it: Charcoal → **Heavy Blue `#0D47A1`**, Pale Pink → **Azure `#42A5F5`**, Coral back to `#FF5252`, Silver to `#A8A8A8`. `MarkerColorsTest` computes Lab from the shipped hexes and asserts the floor over all 190 pairs; the water clause is prose because the depth ramp is not a value this repo holds.

**One picker, raised rather than drawn (2026-10-23).** `MarkerColorPicker.kt` paints nothing itself: it hands a `ConfirmRequest` to `LocalConfirmDialogHost`, so the panel is composited above every drawer and cannot be clipped by the settings overlay or the wizard that opened it. The grid is 4×5 in portrait and 5×4 in landscape, its cells square and sized by whichever of panel width and screen height is tighter — portrait fills the panel edge to edge, landscape stays inside its cap — with `selectableGroup()` and a radio role per swatch. The panel's title is generic (`color_picker_title`, restored), while `marker_color_title` is the wizard row's caption and nothing else.

**The wizard sets a marker's colour for the first time (2026-10-23).** `TypeSelectStep`'s icon row splits into a weighted icon action and a trailing `Marker color` caption plus a 24 dp swatch in a 40 dp tap box, writing the `form.colorIndex` that no control had ever written — until now a marker's colour was only ever the `randomIndex()` drawn at creation.

**The orphan and the frame (2026-10-23).** `MarkerColorPickerDialog` — which had no call site at all — is deleted with its imports; its `color_picker_title` key outlived it once the generic title came back. `ConfirmDialog`'s frame was a `drawBehind` path painted **under** the Surface's own background, so only the two top arcs showed through the corner cut-outs; it now draws above the content, inset half a stroke, 2 dp thick in the new `ui.highlight` token.

**Selection is legible in daylight (2026-10-23).** The picked swatch wears a two-tone ring — pure white over the cell's 0–2.5 dp, `ui.highlight` over 2.5–4.5 dp, the tile starting at 6 dp so it loses only 2 dp — with the accent alone pulsing to a quarter, and the app's own `ListSelectionCheck` disc stamped at the tile's top-left, 12 dp in and 8 dp on a cell under 64 dp, silent because the swatch already announces its radio state.

**What is owed.** The device pass is the user's: the picker in both orientations, the palette in sun, the wizard row, and the pulsing ring. Two code items stand open from the review — `ShowMarkerColorPicker` raises a request with no dispose path, so a surface leaving composition while the panel is open leaves it standing; and the wizard swatch announces only its caption, never the current colour's name.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MarkerColors.kt` — the twenty hues, the rule's KDoc, `nameResOf` and `indexOf`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerColorPicker.kt` — the one picker, the grid, the ring and the disc
- `app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt` — the frame drawn above the panel, the `dense` chrome
- `app/src/main/java/ykws/android/maro/ui/components/ListSelectionCheck.kt` — the optional `announceSelection`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` · `MarkerDrawer.kt` · `MarkersViewModel.kt` · `IconPickerDialog.kt` — the settings door, the orphan's removal, the name lookup, the KDoc
- `app/src/main/java/ykws/android/maro/ui/markers/wizard/steps/TypeSelectStep.kt` — the `Marker color` row
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` · `app/src/main/assets/colors.properties` — `ui.highlight`
- `app/src/main/res/values/strings.xml` · `values-fr/strings.xml` — twenty colour names, the generic title
- `app/src/test/java/ykws/android/maro/ui/map/MarkerColorsTest.kt` — the ΔE gate
- `docs/color-scheme.md` · `docs/ui-component-guidelines.md` — the rule, the token, the picker paragraph
- `xTrack/Markers/261023_FEAT_PLN_Markers_color-picker-palette.md` — the plan of record

## Next Step

The device pass is the user's, over the picker in both orientations and the palette in daylight. The two open code items are the missing dispose effect and the swatch description that names only its caption.
