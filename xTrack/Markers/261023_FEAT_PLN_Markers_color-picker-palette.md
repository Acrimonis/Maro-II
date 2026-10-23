# Plan — marker colour palette: 20 hues, one picker, two grid shapes

**Feature:** Markers · **Branch:** `feature/color-picker` · **Created:** 2026-10-23
**Status:** shipped on `feature/color-picker` — §2–§11 built and green, the device pass the user's

## 1. Scope

Three changes travel together because each constrains the other: the palette grows from 16 to 20 slots, the
picker's grid re-flows per orientation instead of scrolling, and both duplicate picker dialogs become one
component on the app's own dialog idiom.

Out of scope: `IconPickerDialog` stays a 4×4 platform dialog; the `Orange` / `Amber` / `Gold` lightness
collision is named in §7, not fixed.

**The review reshaped this section (§8, R1):** [`MarkerColorPickerDialog`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:580)
has no call site anywhere in the app, so this is one live picker — the settings overlay's — and the orphan is
**removed with everything that hangs off it**, on the user's standing code-health rule rather than migrated.

## 2. The palette

Rule unchanged and already stated once in [`docs/color-scheme.md`](../../docs/color-scheme.md:17): every pair
clears 40° of hue or 0.10 of lightness, no hue in the water band (≈185–250°), vivid over muted.

Four dull slots re-hexed in place, four appended — a stored `colorIndex` keeps its slot, so only the four
re-hexed markers change colour.

| # | Name | Hex | Hue | Lightness | Origin |
|---|---|---|---|---|---|
| 0 | Black | `#000000` | — | 0 % | kept |
| 1 | Gold | `#FFD700` | 51° | 50 % | kept |
| 2 | Green | `#2E7D32` | 123° | 34 % | kept |
| 3 | Lime | `#76FF03` | 93° | 51 % | replaces Light Violet |
| 4 | Orange | `#FF6500` | 24° | 50 % | kept |
| 5 | Grey | `#616161` | — | 38 % | kept |
| 6 | Vivid Violet | `#7C4DFF` | 256° | 65 % | replaces Dark Violet |
| 7 | Red | `#D50000` | 0° | 42 % | kept |
| 8 | Spring Green | `#00F58F` | 155° | 48 % | replaces Olive |
| 9 | Purple | `#CD00CD` | 300° | 40 % | kept |
| 10 | Light Green | `#81C784` | 123° | 64 % | kept |
| 11 | Amber | `#FFAB00` | 40° | 50 % | kept |
| 12 | Azure | `#42A5F5` | 207° | 61 % | replaces Pink (§10) |
| 13 | Cyan | `#00838F` | 185° | 28 % | kept |
| 14 | Hot Magenta | `#FF1AB0` | 321° | 55 % | replaces Maroon |
| 15 | White | `#FFFFFF` | — | 100 % | kept |
| 16 | Pale Yellow | `#FFF59D` | 54° | 81 % | new |
| 17 | Coral | `#FF5252` | 0° | 66 % | new (§10 reverts R12) |
| 18 | Silver | `#A8A8A8` | — | 69 L* | new (§10) |
| 19 | Heavy Blue | `#0D47A1` | 217° | 34 % | replaces Charcoal (§10) |

Retired hexes: Light Violet `#B39DDB`, Dark Violet `#4A148C`, Olive `#827717`, Maroon `#880E4F`.

## 3. Grid shape — two layouts, no scroll

20 splits evenly both ways, so neither orientation leaves an orphan swatch.

| Orientation | Columns × rows | Grid size | Grid + chrome | Panel cap | Verdict |
|---|---|---|---|---|---|
| Portrait | 4 × 5 | 216 × 272 dp | 429 dp | ≈ 851 dp | fits |
| Landscape | 5 × 4 | 272 × 216 dp | 373 dp | ≈ 348 dp | over by 25 dp — title block trimmed |

The landscape shortfall is the review's arithmetic (§8, R5): chrome must come to ≤ 132 dp, and the title block
alone is ≈ 60 dp, so dropping it clears the cap with margin.

Height is derived, never hardcoded: `rows × 48 + (rows − 1) × 8`, so the grid equals its content and the lazy
grid can never scroll. Orientation comes from `LocalConfiguration.screenWidthDp > screenHeightDp`, never from
the dialog's own `BoxWithConstraints` — inside a window those measure the window, not the screen.

## 4. Picker idiom

One component on [`ConfirmDialog`](../../app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt:182),
the grid passed through its documented `options` slot and Cancel as the single action. It replaces both
[`MarkerColorPickerDialog()`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:580) and the private
[`ColorPickerDialog()`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:2109), each
keeping its own contract — index in/out for markers, ARGB in/out for settings rows.

## 5. Work items

- **W1 — palette.** [`MarkerColors.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerColors.kt:17): the 20
  entries in index order, the KDoc's "16-colour / 0-15" restated as 20, `default` and the `of()` / `randomIndex()`
  bounds untouched.
- **W2 — names become resources (§8, R2).** The 20 names move to `marker_color_name_*` keys in both locales, read
  through a pure `@StringRes nameResOf(index)` added to `MarkerColors`, and `colorName()` in
  [`MarkersViewModel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:764) resolves it via
  `getApplication()`; the class already takes an `Application`, and the literal-in-code breach Rule 1 names
  disappears with the eight new entries instead of growing by four.
- **W3 — one picker.** A single composable in `ui/map/` holding the grid, the selection semantics and the
  orientation re-flow, ARGB in and out, replacing the private `ColorPickerDialog`. It must be **raised as a request
  to the ladder host**, not painted inline: the settings overlay is itself a drawer layer, so a panel drawn there
  would sit under the ladder scrim and be clipped by the drawer (§8, R3).
- **W4 — grid semantics.** `selectableGroup()` on the grid and `selectable(selected, role = Role.RadioButton)` per
  swatch in place of `clickable`, so the 3 dp border is announced.
- **W5 — chrome in landscape.** The picker drops its title block and divider in landscape to recover the 25 dp the
  cap is short by (§3); the narrowest form is a dense option on `ConfirmDialog`, default off, so no other caller
  moves — the alternative, a picker-drawn header, keeps the shared component untouched but duplicates spacing.
- **W6 — docs.** [`docs/color-scheme.md`](../../docs/color-scheme.md:13) stops saying "16 hues" and points at the
  palette as it does today; the rule paragraph is not restated.
- **W7 — tests.** A JVM test over `MarkerColors.all`: 20 entries, no duplicates, every pair clearing the rule, no
  hue in the water band, and `nameResOf()` defined for all 20 — testable only because W2 makes it pure.
- **W8 — the orphan, deleted (§8, R1; user's ruling 2026-10-23).** Dead code goes with its baggage: the
  composable, the KDoc cross-reference in
  [`IconPickerDialog.kt`](../../app/src/main/java/ykws/android/maro/ui/map/IconPickerDialog.kt:52) that calls it
  "the same pattern", and whatever imports the deletion orphans in `MarkerDrawer.kt` — the lazy-grid trio,
  `AlertDialog` and `TextButton` are the candidates. Its `marker_color_title` key **survives**, because W10's
  caption and the new picker's own title both read it (§8, R8): the orphan's one asset outlives it.
- **W9 — the doc that names it (§8, R4).**
  [`ui-component-guidelines.md`](../../docs/ui-component-guidelines.md:212) describes the index-versus-ARGB split
  through `MarkerColorPickerDialog`; one picker makes that paragraph point at the new component.
- **W10 — the wizard's `Marker color` row (ordered 2026-10-23; the second step, after the palette).**
  [`TypeSelectStep`](../../app/src/main/java/ykws/android/maro/ui/markers/wizard/steps/TypeSelectStep.kt:115) holds
  one full-width row whose click opens the icon picker; it becomes a weighted icon action on the left and a trailing
  group of caption + swatch on the right, the caption reading `marker_color_title` (W8 keeps it for this) and the
  24 dp swatch reading `form.colorIndex` through `MarkerColors.of()` — the row's existing 24 dp line, so the step
  grows by zero, which is the constraint that decides the shape. The swatch wears the settings rows' own form
  (24 dp, 1 dp `uiDividerColor` border) and sits inside a 40 dp touch box so the split does not shrink the target
  below what the row already gave, while its description names the current colour through W2's `nameResOf`. It
  writes the field that already exists — `form.colorIndex`, seeded at all three creation sites and copied by
  `updateMarker` — so there is no new state and no new wizard step, and the picker it raises is W3's, hoisted to the
  ladder host by W3's rule because the wizard is a drawer layer exactly as the settings overlay is.

## 6. Files

| File | Change |
|---|---|
| `app/src/main/java/ykws/android/maro/ui/map/MarkerColors.kt` | palette + KDoc |
| `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` | `colorName()` over 20 |
| `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt` | orphan picker deleted (W8) |
| `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` | picker call sites switched |
| `app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt` | dense chrome option |
| `app/src/main/java/ykws/android/maro/ui/map/MarkerColorPicker.kt` | new, the one picker |
| `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` | 20 colour names added; `marker_color_title` kept for W10 and the picker title |
| `app/src/test/java/ykws/android/maro/ui/map/MarkerColorsTest.kt` | new, the rule pinned |
| `docs/color-scheme.md` | count and pointer |
| `docs/ui-component-guidelines.md` | the picker paragraph (W9) |

## 7. Open points

- `Orange 24°`, `Amber 40°` and `Gold 51°` all sit at 50 % lightness — the palette's one remaining similarity,
  predating this work; darkening Amber to ≈ 35 % clears it and is a one-line change, left to the user.
- Coral shares Red's hue and earns its slot on lightness alone; the alternative is a near-black teal.
- `IconPickerDialog` keeps the platform-dialog shape until it is given a reason to move.
- W10's swatch carries a caption, so the "is this the icon's colour?" reading is settled by words rather than by
  layout; the caption costs ≈ 78 dp of row width, which still leaves ≈ 117 dp for the icon action inside a card's
  ≈ 296 dp on a 360 dp handset.

## 8. Review

Run against the tree, not the discussion — seven findings, one of which moves the plan.

- **R1 — the marker picker is dead code (blocker, resolved).** `MarkerColorPickerDialog(` appears once, at its own
  definition; every `onColorSelected` hit belongs to the settings `ColorRow` / `ColorPairRow`. So the "two
  pickers" of §4 are one live picker and one orphan, and no control in the app ever writes `form.colorIndex` —
  a marker's colour is only ever chosen by `randomIndex()` at creation. §1 and W8 were rewritten for this, and
  the user's ruling then fixed the outcome: the orphan and its strings go, no marker-colour control is added.
- **R2 — the names breach Rule 1 (resolved).** `colorName()` holds user-visible literals that land in the default
  marker name; adding four grows the breach. §8 also blocked W7, since a private `when` cannot be tested.
- **R3 — hosting is not a call-site swap (resolved).** The live picker is opened from inside the settings overlay,
  a drawer layer; the confirm panel is composited above every drawer by the ladder host. Inline it would be
  clipped and dimmed. W3 now carries the hoist.
- **R4 — a second doc names the old component (resolved).** Added as W9, so the index/ARGB paragraph does not
  outlive the code it describes.
- **R5 — the landscape budget was stated, not checked (resolved).** 373 dp of content against a ≈ 348 dp cap is a
  25 dp shortfall, closed by the title block; §3 now shows the arithmetic.
- **R6 — the narrowest width is exactly the grid (accepted).** The panel is portrait-width, so on a 320 dp-wide
  handset the landscape content area is 272 dp — the grid's own width, with no side margin. It fits; it simply
  cannot absorb a wider gap without a second sizing rule, which is not worth a rule.
- **R7 — settings colours keep their raw ARGB (accepted).** The picker compares by equality, so a stored colour
  outside the palette shows no selection — pre-existing and already documented at
  [`color-scheme.md`](../../docs/color-scheme.md:22). Growing the palette only enlarges the set that matches.
- **R11 — one hex moved during the build (deviation, reported).** Charcoal shipped as `#212121`, not the
  `#263238` first proposed: at `#263238` it is 19 % saturated, so it reads as a chromatic entry at hue 200°,
  15° from `Cyan` and 0.096 of lightness away — a rule breach the test would have caught. A true neutral sits
  on the palette's lightness rail instead, and no other colour moved.
- **R12 — Coral moved too, and the plan's own hue table was wrong (deviation, reported).** `#FF5252` sat 20°
  and 0.098 of lightness from `Pink`, which the first test run failed on; `#FF6E40` clears Pink, Red, Orange,
  Amber, Gold and Hot Magenta at once. The same run measured `Pink` at **340°**, not the 301° this plan first
  wrote, so the table above carries the measured values and `MarkerColorsTest` is the arbiter from here.

## 9. Follow-up — the picker's title (ordered 2026-10-23)

**Decision: one generic title for every caller.** The caption on the wizard row exists to tell the swatch
apart from the icon, not to name the dialog; the panel names only the task it performs, because the user
already knows what they tapped. The old generic key was deleted during W8 as orphaned — that deletion was
right about the setting and wrong about the wording, which is now needed again by a different surface.

- **W11 — restore the generic title.** `color_picker_title` — "Pick color" / "Choisir une couleur" — returns
  to both locales, and [`ShowMarkerColorPicker`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerColorPicker.kt:75)
  passes it as the `ConfirmRequest` title instead of `marker_color_title`. The wizard's caption keeps
  `marker_color_title`, so a field label and a dialog title stop sharing one key, and "Route line" is no
  longer announced as a marker colour.
- Files: `values/strings.xml`, `values-fr/strings.xml`, [`MarkerColorPicker.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerColorPicker.kt).
  No documentation states a picker title, so nothing follows it there.
- Landscape's `dense` chrome still drops the title — the height cap's price, unchanged by this order.
- Not included, and still carried in the feature's `## Todos`: the missing `DisposableEffect`, and the wizard
  swatch's description naming only its caption rather than the current colour.

## 10. Rule v3 and the palette it forces (ordered 2026-10-23)

**The criterion moves from a hue/lightness proxy to perceived difference, because the proxy is what shipped a
near-black and three entries in one hue family.** "40° of hue or 0.10 of lightness" let Charcoal sit ΔE 13 from
Black, and let both fuchsias through on a lightness gap the eye does not read as a different colour.

1. Every pair clears **ΔE ≥ 25** in CIELAB (CIE76) on the shipped hexes — the test's one numeric gate.
2. A pair inside one hue family (hues within 45°) is legal only when this record **names** the twin, so a twin is
   a deliberate sighting rather than an accident. Today's: Green/Light Green, Gold/Pale Yellow, Purple/Hot Magenta,
   Heavy Blue/Azure.
3. Nothing may read as water: an entry clears the sea fill and the depth ramp by ΔE ≥ 25. The ramp is a style value
   this repo does not hold, so the clause is checked by eye on the map and stated here rather than asserted.
4. Vivid over muted when two candidates tie on 1–3.
5. The eye is the final arbiter: a pair the user calls similar is replaced, and the reason is recorded in the
   palette's KDoc beside the exception.

**What that forces today** — two swaps, the other eighteen untouched:

- 19 Charcoal `#212121` → **Heavy Blue `#0D47A1`**: ΔE 13 from Black was the flagged near-black, and blue was the
  one large vivid region the palette refused on a clause the user has now relaxed to "distinct from the sea and the
  ramp", which ΔE 61 from Cyan and 53 from Vivid Violet satisfies.
- 12 Pale Pink `#F48FB1` → **Azure `#42A5F5`**: the pastel was the weakest of the pink trio on water, and blue's
  second tier sits ΔE 40 from the heavy blue, 43 from Cyan and 49 from Silver — a named twin, not a lookalike.
- Purple and Hot Magenta stay as the named fuchsia twin (ΔE 32, 21° apart: a true fuchsia against a hot pink).
  Dropping either leaves 18 swatches and breaks the 4×5 / 5×4 shapes; if the eye still rejects them, that is the
  trade to weigh, not a free win.

- **W12 — rule and palette together.** The criterion lands in three homes in one pass — the palette KDoc,
  [`color-scheme.md`](../../docs/color-scheme.md:17) and
  [`MarkerColorsTest`](../../app/src/test/java/ykws/android/maro/ui/map/MarkerColorsTest.kt:11), which becomes a ΔE
  gate plus the named-twin list — and the two hexes swap with their names: `marker_color_name_pink` retires while
  `marker_color_name_azure` and `marker_color_name_heavy_blue` join both locales.
- 17 Coral `#FF6E40` → back to **Coral `#FF5252`**: that hex was moved away on the old proxy, but under the new
  measure it sits ΔE 19.6 from Orange — the two are one vivid orange-red to the eye. The original clears Orange by
  35, Red by 26 and Hot Magenta by 62, so it returns and R12 stands corrected.
- 18 Silver `#BDBDBD` → **Silver `#A8A8A8`**: the ΔE gate's first catch, 23.4 against White; the mid-grey clears
  White by 31 and Grey by 28.

- W11 (§9, the generic picker title) rides the same build.

## 11. Making the selection obvious in daylight (ordered 2026-10-23)

**Assessment.** The current cue fails in sun for two structural reasons: it is hue-against-hue (a blue ring a
few dp from a swatch that may itself be blue), and the pulse fades it towards invisible exactly when it should
be loudest. Shape and a constant-contrast backing beat both, and this app already has that face.

- **The checkmark is the app's own.** [`ListSelectionCheck`](../../app/src/main/java/ykws/android/maro/ui/components/ListSelectionCheck.kt:33)
  is the canonical selection face — a 24 dp `uiAccent` disc bearing a white 16 dp `Check`, with
  `MultiSelectRow` and `SegmentedRow` wearing the bare glyph beside their labels. Reusing the disc is both the
  consistency the user asked for and the daylight answer: the white glyph always sits on a known dark disc,
  so no swatch colour can swallow it and no luminance rule is needed.
- **Its placement.** Top-left of the tile, inset by **half the disc** (12 dp), so the mark is a corner stamp
  rather than a cross over the colour. On a landscape cell the tile is ≈ 55 dp, where a 12 dp inset would push
  the disc near the middle — so the inset is clamped to 8 dp below a 64 dp tile, and that clamp is the one
  judgement the plan takes rather than passes on.
- **One shared tweak.** `ListSelectionCheck` announces `cd_selected`, which our swatch would then say twice
  beside its own radio state; it gains an optional description defaulted to today's string and the picker
  passes null.
- **The two-tone ring.** White **outside**, accent inside: the white path covers 0–2.5 dp of the cell, the
  accent path 2.5–4.5 dp, and the tile starts at 6 dp, so the ring gains its white edge while the tile loses
  only **2 dp in total** (margin 5 → 6 dp, 1 dp per side) — not 3.
- **The pulse rides the accent only**, white steady, so the ring breathes without ever fading to nothing.

- **W13 — selection made obvious.** `MarkerColorPicker.kt` gains the two-tone ring and the top-left
  `ListSelectionCheck`; `ListSelectionCheck.kt` gains the optional description. Nothing else moves: the cell
  arithmetic, the grid shapes and the chrome budget are untouched, so landscape still clears its cap.
- Not included: the colour's name in the title, parked until the glyph and ring have been seen on the water.
- **R8 — the caption rescues a key W8 would have deleted (resolved).** `marker_color_title` is "Marker Color" /
  "Couleur du repère" in both locales and the W10 order gives it a second life, so the deletion list shrinks and the
  caption needs no new key — the same string then serves the picker's own dialog title, one home, two readers.
- **R9 — the split row shrinks a touch target (resolved).** The whole row was one 40 dp-tall click; carving the
  swatch out would leave it a 24 dp target, under the 48 dp the guidelines set, so the swatch rides a 40 dp
  clickable box that pads it rather than enlarging the artwork — the same visual, the same row height.
- **R10 — the plan's own open point closed itself (resolved).** With a caption the swatch can no longer be mistaken
  for the icon's colour, so the third-labelled-section alternative is withdrawn rather than carried.
