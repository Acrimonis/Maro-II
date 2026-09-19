# FEAT_PLN — UI_Map — the map's paint code: px to dp

**Status:** implemented — recorded in `## Implemented` of `xTrack/UI_Map/FEAT_DSC_UI_Map.md`. Captures the 2026-09-19 decision to stop deferring it, the review that preceded the write, and the independent review that followed it, whose one High (the marker circle's dash defaulting to density 1) was repaired in the same pipeline and whose one Medium (the row grid) remains open.
**Feature:** UI_Map owns the map's paint pipeline. The values it converts are owned by Tracks (track and chevron widths), Coastline (shoreline and the 300 m band), RegulatedZones (the outline) and Markers (circle, corridor, halo) — the routing map is untouched; this file records the ownership in §2's inventory instead.
**Related docs:** [`docs/marco-code.md`](../../docs/maro-code.md) for navigation, [`docs/color-scheme.md`](../../docs/color-scheme.md) for the swept colours, and the two plans that produced the current shape: [`260919_FEAT_PLN_Ui_Settings_stroke-widths-and-shoreline-colours.md`](../Ui_Settings/260919_FEAT_PLN_Ui_Settings_stroke-widths-and-shoreline-colours.md) and [`260919_FEAT_PLN_Ui_Settings_heading-line-and-arrow-appearance.md`](../Ui_Settings/260919_FEAT_PLN_Ui_Settings_heading-line-and-arrow-appearance.md).

## 1. What the change is, and what it is not

- **It is a unit change, not a re-tune.** Every converted value keeps its physical size on the device the numbers were chosen on, so the shipped look on that phone does not move.
- **It is one pass over the map's lengths** — stroke widths, dash lengths, offsets and paddings — not an invitation to revisit any of them.
- **It is not** the theme-doc trim, and not a change to zoom levels, speeds, depths or metres, each of which stays in its own unit.
- **It does carry the file-versus-code value drift this pass touches** — the ramp families and the flash alpha among the reds — because those disagreements live in the same two files. Reconciliation is a different act from conversion and the plan says so at every step: one makes two homes agree, the other changes a unit.

## 2. The inventory, and its classes

The first deliverable is the **complete** inventory, because a missed length is a silent unit mix rather than a compile error. Every value falls into exactly one class:

| Class | Example | Treatment |
|---|---|---|
| **Length, px** | the six `track.width.*` values, the coastline, band and regulated outline widths, the hazard's `6f`/`5f`, the isobath base pair with its bonuses, the marker's `4f`/`2f` with its multiplier, the halo border, the dash patterns, the offsets and paddings | convert to dp, in its composition group |
| **Threshold in px** | `track.arrow.scaleKnee=10` — a width that decides which tracks are tempered | convert **only** with the widths it compares against, and test the comparison |
| **Already dp** | any key or Compose literal already carrying its unit: `map.marker.size.boatBaseDp` / `.dotBaseDp`, `map.marker.tap.*Dp`, `map.navigation.arrow.widthDp` and `.line.widthDp`, and the key behind `trackDirectionMinSpacingDp` | untouched — the pass converges *toward* these. The class is a rule, not a key list, or a sweep for stray float literals cannot tell a dp candidate from a px one |
| **Not a length** | zoom levels, knots, metres, percentages, alphas, the heatmap thresholds | untouched, in their own unit |

## 3. What the pass produces

- **One conversion helper, pure and tested**, beside the alpha sibling the strokes change introduced: it takes a dp value and a density and returns px, so the osmdroid paint sites multiply and the Compose canvases keep `toPx()`, both through one named rule rather than two idioms. The idiom is not new — `MapTrackOverlayEffects.kt:121` already multiplies a dp key by `resources.displayMetrics.density` — so the helper extracts that site rather than inventing a second convention.
- **The caller owns the multiplication**, being the layer that holds the density; the renderer's parameters keep their `…Px` names and their contract of receiving an already-converted width, so no signature in `MapOverlayRenderer` moves for a unit.
- **Named constants for the raw literals** that are lengths today — the halo's `+ 4`, the selected under-stroke's `+ 6f`, the chevron's `min 2f` floor, the dash pairs — each carrying its unit in its name, in the shape `DASH_ON_WIDTH_RATIO` already established.
- **Keys with the unit in their names**: the three settings widths become `…widthDp`, matching the arrow and line keys; their `AppConfig` accessors, `SettingsManager` fields, prefs keys and both locales' value formats follow, the rows' readouts changing from `px` to `dp`.
- **The properties blocks state the reference density once**, where the track table's comment already says its numbers were chosen for a 3× device — so a later re-tune knows what it is re-tuning against.

## 4. Risks, and the mitigation for each

| # | Risk | Mitigation |
|---|---|---|
| R1 | **A composition group split across the change** — `MarkerHalo`'s ring beside its padding, the selected under-stroke beside its add, an isobath base beside its bonus — breaks a ratio while still compiling | Convert by **group**, enumerated in the inventory, and add one test per group asserting the ratio itself, not the numbers: casing over core, under-stroke minus core, inset over width |
| R2 | **A threshold converted alone**, changing behaviour (which tracks are tempered) while the appearance is meant to hold | The inventory's second class; the knee moves with the widths in the same edit, and a focused test asserts the tempering boundary at the converted value |
| R3 | **The look moves on the tuning device, and the reachable set shrinks** | The paint sites take floats, so the conversion itself loses almost nothing; the loss sits at the settings boundary, where an `Int` field and a one-step `1f..20f` row cannot express 3.33 dp. The three fields, their `toIntOrNull()` parse and their rows move to float and a dp grid in the same atomic step, or the widths a user could reach are gone |
| R4 | **The reference density is implicit**, so a later tuning drifts | One block comment states it, and the plan records it; every key says `Dp`, so a value without the suffix is visibly suspect |
| R5 | **Tests pin the old px** — the width pins among the drift reds | This pass owns them: their expectations become dp, and the file-versus-code disagreement they embody is settled here rather than carried further. §6 names the reds this pass touches rather than counting them, the survey having enumerated seven where an earlier draft wrote six |
| R6 | **Two conversion idioms** (osmdroid multiplication, Compose `toPx()`) | One pure helper taking density explicitly; the Compose paths keep their own `toPx()` and do not call it |
| R7 | **A sweep driven by grep catches the already-dp keys** and converts the target | The inventory lists both sets by key; the pass converts the px set only, and the already-dp keys appear in it marked as untouched |
| R8 | **The change is unattributable** if it rides with other work | It lands alone, after the cap-arrow repairs and the uncompiled tidy are settled, and it is the only content of its commits |
| R9 | **A regression hides inside a wide diff** | One conversion unit per commit-worthy step — tracks, then the three settings widths, then isobaths, then hazards, then markers — each building green on its own |

## 5. Work breakdown

1. Build the inventory: grep the paint pipeline for every numeric literal and key in px, classify each per §2, and land it as this plan's own table. No code change.
2. Add the conversion helper and its tests, with no call sites yet.
3. Convert the **track group** — the six widths, the casing, the chevron's tempering pair and its floor — as one unit, with the ratio tests.
4. Convert the **three settings widths** to `…widthDp` as **one atomic step per width**: the key in the properties, the parse (which becomes `toFloatOrNull`), the `AppConfig` field and default (float, not `Int`), the `SettingsManager` field and the prefs key, both locales' labels and value formats, the row's span and step as a dp grid, and every paint site that reads it. A key landing apart from its paint site compiles and paints dp as px, so the two never land apart.
5. Convert the **dash patterns**, by the rule their stroke imposes: the direction line's stays a ratio of its width, as it already is, and the isobath's low-confidence `8f, 6f` becomes a dp pair, because it rides a stroke that varies — 2f or 3f plus the source bonus — where a ratio would change the drawing. A dash on a fixed stroke converts by ratio; one on a varying stroke converts by value.
6. Convert the **coastline, band and regulated highlights** — the remaining baked strokes and the offset literals around them, the setting-fed widths having landed complete with their keys in step 4.
7. Convert the **isobath group** — base pair plus bonuses, with the clamp re-expressed in dp.
8. Convert the **marker group** — circle, corridor, proximity preview, halo border, and the two adds — with the ratio tests.
9. Reconcile every red this pass touches: the width pins rewritten as dp, and the ramp-families and flash-alpha disagreements settled with the file as the source of truth and the code's defaults following it. State the reference density once in the same edit.
10. Accept: build green, the scoped run green apart from the non-length reds, a check at another density on an emulator, and the tuning phone unchanged.

## 6. Acceptance, and what "look-preserving" is checked against

- **On the tuning device (3×):** the strokes are the same physical weight as before — the check is the phone, not a number.
- **On a second density (1× or 2×, emulator):** the strokes scale with the screen rather than staying fixed in pixels — the only evidence the pass did anything, since on the tuning phone it is invisible by design.
- **Tests:** the scoped run green, with the width-pinning classes asserting dp and the ratio tests added per group; the ramp families and the tap-flash alpha — the two non-length reds among the seven the survey enumerated — are settled by this pass rather than carried, per §7 and step 9.
- **One commit per conversion unit**, each building green before the next, so a regression is attributable to a group rather than to the pass.

## 7. Decisions and open questions

- **Decided** — lengths to dp with the unit in the key; thresholds convert only with what they compare against; the already-dp keys are the target and do not move; the reference density is stated once; one helper named once.
- **Decided 2026-09-19 — the family takes the `Dp` suffix in this pass**, so every width key carries its unit and no key's meaning depends on a comment. The rename spans `maro.properties`, `AppConfig`, `SettingsManager` and the three test classes that read those keys.
- **Decided 2026-09-19 — the ramp-families and flash-alpha reds are settled in the same pass**, since it rewrites those files anyway; they are contradictions between the file and the code rather than unit problems, and the file wins, `*.properties` being the source of truth for every value with the code's defaults following it.
- **Decided 2026-09-19 — a value and its reader convert in one step.** The review of this plan showed step 4 and step 6 splitting a setting's unit from the paint site consuming it, so the renderer would have taken a dp number in a px parameter and painted it unchanged: a green build with a wrong map. Every step carrying a unit now carries its consumers with it.
- **Decided 2026-09-19 — the caller converts and the renderer keeps its `…Px` parameters**, the caller being where the density lives; and a dash on a varying stroke converts by value rather than by ratio.
- **The strongest objection to the pass** — it touches every renderer and every test for a change invisible on the only device it runs on — is answered by the split it removes: two length units currently sit in one properties file, and a setting whose meaning depends on the screen it is read on is the defect, not the portability.

## 8. Explicitly out of scope

- Any re-tune of a value: the pass keeps each stroke's physical size.
- The theme doc's remaining restatements, the parked colour-file sweep, and the marker-size and tap families already in dp.
- The map's data units — metres, knots, zoom levels and the heatmap's thresholds — which are not lengths.

## 9. Session state

- Follows the strokes change at `217a405` (pushed, pull request open) and the arrow/line and colour-row work committed at `256dfc2`, neither of which this pass depends on beyond their pattern.
- **Nothing outstanding before it starts:** the cap-arrow repairs the Ask hop named were verified already in `256dfc2`, and the build is green — 235 tests completed with 6 failed.

## 10. The inventory (step 1, landed 2026-09-19)

Every length the map's paint pipeline reads, by class. **The shipped numbers are px chosen on a 3× device**
(the track table's own comment), so a conversion divides by 3 and the drawn size on that phone is
unchanged; the target dp value is named in the parenthesis.

| Class | Key / literal | Site | Owner | Becomes |
|---|---|---|---|---|
| Length, px | `track.width.live` = 12 | `maro.properties`, `AppConfig` | Tracks | 4 dp |
| Length, px | `track.width.selected` = 10 | idem | Tracks | 3.333 dp |
| Length, px | `track.width.newest` = 11 | idem | Tracks | 3.667 dp |
| Length, px | `track.width.pinned` = 9 | idem | Tracks | 3 dp |
| Length, px | `track.width.history` = 8 | idem | Tracks | 2.667 dp |
| Length, px | `track.width.selected.casing` = 16 | idem | Tracks | 5.333 dp |
| Length, px | chevron stroke floor `2f` | `TrackDirectionOverlay.kt:151` | Tracks | 0.667 dp |
| Length, px | cap overlap `0.5f` | `TrackDirectionOverlay.kt:183` | Tracks | 0.167 dp |
| Length, px | GAP dash `20f, 10f` | `MapTrackSegments.kt:89`, `MapTrackOverlayEffects.kt:719`, `MapScreen.kt:1056` | Tracks | 6.667, 3.333 dp (varying stroke → by value) |
| Threshold, px | `track.arrow.scaleKnee` = 10 | `maro.properties`, `AppConfig` | Tracks | 3.333 dp, moved with the widths it compares against |
| Length, px | `map.coastline.widthPx` = 10 | idem | Coastline | 3.333 dp, key `…widthDp` |
| Length, px | hazard disc ring `6f` | `MapOverlayRenderer.kt:105` | Coastline | 2 dp |
| Length, px | hazard outer ring / cross `5f` | `MapOverlayRenderer.kt:117,126,132` | Coastline | 1.667 dp |
| Length, px | `map.zone300.boundary.widthPx` = 6 | idem | 300 m band | 2 dp, key `…widthDp` |
| Length, px | `map.regulatedZone.outline.widthPx` = 3 | idem | RegulatedZones | 1 dp, key `…widthDp` |
| Length, px | isobath base pair `3f` / `2f` | `MapOverlayRenderer.kt:355` | Isobaths | 1 dp / 0.667 dp |
| Length, px | isobath floor `coerceAtLeast(1f)` | `MapOverlayRenderer.kt:355` | Isobaths | 0.333 dp |
| Length, px | `map.isobar.litto3d.width` = 1, `map.isobar.emodnet.width` = -1 | `maro.properties`, `AppConfig` | Isobaths | 0.333, -0.333 dp |
| Length, px | low-confidence dash `8f, 6f` | `MapOverlayRenderer.kt:360` | Isobaths | 2.667, 2 dp (varying stroke → by value) |
| Length, px | circle / corridor stroke `4f`, `2f` | `MarkerOverlay.kt:587,592,623,669,673` | Markers | 1.333 dp / 0.667 dp |
| Length, px | the two adds `HIGHLIGHT_UNDER_STROKE_ADD`, `CORRIDOR_UNDERLINE_HALO_ADD` = `6f` | `MarkerOverlay.kt:52,64` | Markers | 2 dp each |
| Length, px | marker dash `12f, 8f` | `MarkerOverlay.kt:836` | Markers | 4, 2.667 dp |
| Length, px | dot radius `DOT_RADIUS_DP * 3f` — the literal density | `MarkerOverlay.kt:898` | Markers | 6 dp × density |
| Length, px | dot/icon anchors `18f` / `30f`, ring `18f`–`60f`, border `4f`, padding `4` | `MarkerHalo.kt:18,21,24,27,30,69` | Markers | 6 / 10 dp, 6–20 dp, 1.333 dp, 1.333 dp |
| Already dp | `DIRECTION_ARROW_SPACING_DP` = 48 | `MapScreen.kt:265`, ×density at `MapTrackOverlayEffects.kt:126` | Tracks | untouched — already dp, the uniform-spacing provider multiplies it by the density |
| Already dp | `map.marker.size.*BaseDp`, `map.marker.tap.*Dp`, `map.navigation.*.widthDp`, `track.direction.min/maxSpacingDp` | — | — | untouched — the pass converges toward these |
| Not a length | `track.arrow.temper`, `DASH_ON/_OFF_WIDTH_RATIO`, `CAP_ARROW_*` ratios, `CAP_DP_PER_KNOT`, zoom, knots, metres, alphas, heatmap thresholds, the chevron's 2.5 / 0.6 / 0.5 relations, the 48 px view-cull margin | — | — | untouched, in their own unit |

**Reference density:** 3×, stated once in `maro.properties` beside the track table and once here.
**Composition groups** (R1) — each converted whole, with a ratio test: the track table and its casing;
the chevron's core, floor and cap overlap; the isobath base with its bonuses and its floor; the marker
circle/corridor with its two adds; the halo's ring with its border and padding.
**Two dashes, two rules** (step 5): the direction line's stays a ratio of its own width; the GAP bridge's
and the isobath's convert by value, riding strokes that vary.
