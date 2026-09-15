<!-- scope: feature -->
# Track outlines — the selection, the widths and the chevrons

**Status:** §2.1 and §2.2 shipped on `feature/no-black-casing` (`apk-build.bat` SUCCESS, 126 scoped tests green); **§2.3 shipped in the wrong shape and is open again** — the device showed the strip as wide as the whole toggle row and on a background token of its own, where it must be one button wide and on the disabled toggle's background; §5 carries the Ask hop's lows, planned as one hop too · **Filed** 2026-09-14, **rewritten implementation-only** 2026-09-15 with two review passes and then the implementation folded in · **Name** kept as `pinned-cue-casing` although the subject widened from a pinned-only casing to the whole selection rendering.

**Request:** in Colours mode the ramp owns the interior colour, so identity and selection live in the outline and the width — a casing beneath the selected track, per-type widths read from `maro.properties`, and chevrons that scale with their own line.

**Words:** a **banded stroke** is one painted from the ramp (`TrackRenderPath.BANDED`); **Colours** is the mode's label for `TrackRenderMode.HEATMAP`; the **tempered core** is the arrowhead's own width after `track.arrow.scaleKnee` and `track.arrow.temper` (`temperedCore`), which is never the line's width once the line is wider than the knee.

## 1. The contract — what the code must satisfy

- **The interior carries speed in Colours**, every stored track's stroke being the ramp, while the live recording line is excluded and keeps its own appearance.
- **The selection is opaque, cased and on top** — full alpha whatever its class transparency says, the legacy `#CC000000` casing beneath it sharing the track's title, and the whole thing drawn above every other overlay.
- **One rim weight across the whole selection.** The dark border is the selection's and never the stroke it edges, so the line and its arrowheads wear the same weight; only the selection is rimmed, every other track's chevrons being drawn bare.
- **Every width is the file's**, per type (`live`, `selected`, `newest`, `pinned`, `history`, `selected.casing`), read by all three modes, the newest track decided by recency rather than by loop position.
- **Arrows follow the mode alone** — Simple draws none — while the **drawer eye picks only the selection's fill**, its value persisted on the selection, written by the first tap and absent meaning "follow the mode".
- **The legend keys banded strokes and nothing else** — the mode with banded strokes painted on the map, or the selection the eye has banded — so it is on screen exactly while there is a ramp stroke to explain.
- **Every visible size is configuration**, and `maro.properties` is canonical: a code default mirrors it key for key, so the two cannot drift unseen.

**Order:** §2.1 first and on its own — it touches one overlay and its own test. Then §2.2, then §2.3: both edit the same block in `MapScreen.kt` around the legend call, §2.2 its arguments and §2.3 the modifier below them, so they are one hop or two strictly ordered hops, never two independent patches.

## 2. The work

### 2.1 The arrowhead rim at the line's weight

- **Change** — in `app/src/main/java/ykws/android/maro/ui/map/TrackDirectionOverlay.kt`, [`drawResolvedChevrons()`](app/src/main/java/ykws/android/maro/ui/map/TrackDirectionOverlay.kt:384) calls `chevronCasingOffset(casing.strokeWidth, core)` at [line 413](app/src/main/java/ykws/android/maro/ui/map/TrackDirectionOverlay.kt:413) with the tempered core; it passes the line's width instead — `metrics.strokeWidth`, the non-null local the geometry above is already read from, never the nullable parameter `chevronMetrics`.
- **Why** — the rim belongs to the selection, so the offset must be the very `(casing − core) / 2` the line's own casing uses. Read from the tempered core it gives the arrowhead a heavier border than the line it sits on and stands the dark vertex further ahead of the coloured tip.
- **Measured** — with the shipped pair (casing 22, selected 14) the offset becomes 4 against the tempered reading's 5, so the dark band's inner edge sits 1 px from the coloured centreline, and the arrowhead's own stroke stays at 6.
- **Safe because both paths are opaque where the dark could show** — the banded chevrons take `fade × 255` with a selection's fade forced to 1 by `storedTrackFade()`, the gold V is `0xFFFFD700`, and the casing pass runs before the coloured one.
- **Sites** — the one argument above; the comments that argue the tempered reading: `chevronCasingOffset`'s KDoc, `chevronV`'s KDoc and the `casingAppearance` parameter's KDoc in the same file, **the two body comments inside `drawResolvedChevrons` itself**, `selectedTrackCasing()` and `StoredTrackRendering.chevronCasing` in `MapTrackOverlayEffects.kt`, `trackWidthSelectedCasing` in `app/src/main/java/ykws/android/maro/config/AppConfig.kt`, and the casing prose in `app/src/main/assets/maro.properties` — comment text alone, no key's value moving.
- **Two figures recomputed with it** — `chevronCasingOffset`'s KDoc has the raw-core inner edge 0.5 px clear where the fixed 6 px stroke makes it 1 px, and `chevronV`'s has the dark vertex ~3.7 px ahead of the coloured apex where it becomes ~1.8 px.
- **Test** — `app/src/test/java/ykws/android/maro/ui/map/TrackDirectionOverlayTest.kt` needs no logic change (it exercises the pure functions, which do not move) but its shipped-pair literals do: the rim 5 becomes 4 and the inner edge 2 becomes 1, at lines 173 and 211, and the two tests that state the rule (`theCasingArmRunsOutsideTheColouredOneAtTheRimThickness`, `theRimIsMeasuredFromTheTemperedCoreHoweverTheKneeAndTemperMove`) are reworded onto the new reference. A case for the pair itself belongs there too: `chevronCasingOffset(casing 22f, core 14f) == 4f`.

### 2.2 The legend gate — banded strokes, not the focused fill

- **Change** — [`legendVisibleFor()`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:417) takes the four inputs below in place of `selected`, and its caller supplies them:

```kotlin
legendVisibleFor(mode: TrackRenderMode, storedOnMap: Boolean,
                 selectionOpen: Boolean, eyeOverride: Boolean?): Boolean =
    storedOnMap && (mode == TrackRenderMode.HEATMAP ||
                    (selectionOpen && eyeOverride == true))
```

- **`storedOnMap` — a banded stroke painted, not a track drawn.** It reads the ids the effect actually painted, history **and pinned**, and asks the same planner the map renders by:

```kotlin
storedOnMap = appSettings.tracksVisible && paintedIds.any { id ->
    trackRenderPlan(appSettings.trackRenderMode, id == highlightedTrackId,
                    appSettings.trackSelectionBanded).path == TrackRenderPath.BANDED
}
```

- **Where `paintedIds` comes from** — `renderedTrackIds` in `MapTrackOverlayEffects.kt` is a local, filled at the end of the effect from `desiredIds` and covering the history loop alone, so it is hoisted as a parameter or state of the effect and filled where overlays are actually added, the pinned loop included. Its name is the point: a summary whose detail fails to load paints nothing and must not count, which is exactly what `loadTrackDetailCached(summary.id) ?: continue` produces.
- **An empty painted set hides the scale in every mode**, which is reachable — `trackingRenderNb` coerces to 0 — and is why `storedOnMap` leads the predicate.
- **Not permitted** — recomputing the selection policy at the legend site: the effect calls `focus.highlight(highlightedTrackId)` before `TrackSelectionPolicy().select(...)`, so a caller-side recompute would repeat a stateful mutation inside composition. The drawn-track count at [`trackMapVisibleCount`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1607) is the nearest existing thing and is not the same question.
- **Cases** (row 3 is the shipped bug; the last rows are the states a bare "mode or eye" reading gets wrong):

| Mode | Selection | Eye | Banded strokes painted | Scale |
|---|---|---|---|---|
| Colours | open | any | yes — the others, or the selection when the eye leaves it banded | shows |
| Colours | none | any | yes | **shows** — unselecting must not take it away |
| Colours | open | false | only the selection, flipped gold | hides |
| Colours | any | any | none — count 0, or every summary's detail failed to load | hides |
| Simple or Dir & Speed | open | true | the selection | shows — the eye is the only thing banding |
| Simple or Dir & Speed | none | true | none | hides — the value is about a selection, and there is none |
| any | any | any | layer off | hides |

- **Sites** — the function and its KDoc in `MapTrackOverlayEffects.kt`; the caller at [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1387), which passes `highlightedTrackId != null && appSettings.tracksVisible` today; the effect's painted-id set; and the eye's note in `ui/map/OverlayLayer.kt` at line 787, whose text already reads as the new predicate.
- **Test** — `app/src/test/java/ykws/android/maro/ui/map/TrackRenderModePathTest.kt` carries the gate's cases at line 140 but asserts the shipped reading, so the block at 140 to 199 is rewritten onto the predicate and the table above, and `theLegendAgreesWithTheSelectedTracksOwnPath` goes: it pins legend ≡ a banded selection, an equivalence the new rule breaks by construction.

### 2.3 The strip's chrome — as it must be

**What shipped is the defect this corrects:** the first pass read the row's two edges as meaning the *row's whole width*, so the strip is 194 dp (244 dp with the recenter button) with its content at the left and its background on a token of its own. The device rejected that shape, and the two rules below replace it.

- **Width and alignment — one button, not the row.** The strip is exactly one toggle button wide (`TOP_TOGGLE_SQUARE`, 44 dp) and its left edge is the row's own gutter (`TOP_TOGGLE_GUTTER`, 6 dp), so its two vertical edges are the leftmost button's two edges. The row's square and gutter keep a single home, which the third pass moved into `MapControls.kt` beside the buttons that actually wear the square; only the strip's width term changes, from `44 × count + 6 × (count − 1)` to the square alone, and the recenter button stops entering the arithmetic.
- **Background — the disabled toggle's own colour, and the three off-states must agree.** All three off-state boxes paint **white at 25 %**, from the shared `semantic.inactive` token copied at each button's own dimmed alpha — `status.gps.alpha.dimmed`, `status.tracking.alpha.dimmed`, `status.lock.alpha.dimmed`, 0.25 each. Two model corrections got the plan here, and both are worth keeping written down: `copy(alpha = …)` *replaces* the token's own 20 % rather than multiplying it, and the whole-box `.alpha(contentAlpha)` sits **after** `.background(...)` in the modifier chain, so it dims the glyph alone and never the fill — the 25 % is the background's own alpha in all three, and an earlier reading of it as 0.50 × 0.50 was wrong. The GPS DEMO branch was the one sitting at 50 %; it drops to the same 0.25, so the three are indistinguishable in weight, their glyphs staying at 0.50 throughout. The card reads the same reference at the same 0.25, making it the fourth surface in the disabled toggle's colour, and if that ever reads too faint the reference is what moves — for the buttons as well, never for the card alone.
- **The content inside 44 dp — the height does not move, the padding does.** The bar is 14 dp, the spacer 6 dp, and the widest shipped label is two digits (`35`) at roughly 11 dp in 10 sp type, so the content needs about 31 dp and the padding's ceiling is about 6 dp a side — today's 10 dp leaves 4 dp and the label overflows its box. The padding is therefore stated as **6 dp** rather than left to the device, the bar's 150 dp and the card's own **166 dp** (150 + 8 + 8 vertical padding) are unchanged, and if a raised font scale still overflows, the label size is the next knob and the width is not. Neither widening the strip nor growing it downward is an answer, and the label prints on one line so a misfit shows rather than silently wrapping.
- **Untouched** — the bar's 150 dp height, the 6 dp spacer, the 10 sp label size, the measured end insets and the 1 dp divider border keep exactly the geometry they have, and no rebuild key is involved, the key list carrying widths, ramp, knee and temper only.
- **Sites** — `MapScreen.kt`'s legend modifier: the **width and the inner padding only**, the start term already sitting on the row's gutter and staying untouched, plus the `toggleRowCount` local that loses its only reader with the width term and the comment above it that still names 194 dp and 244 dp. Then [`TrackSpeedLegend`](app/src/main/java/ykws/android/maro/ui/map/TrackSpeedLegend.kt:72) — its own padding, the one-line label and the comment at line 75 that argues the old token — and, for the removal, `app/src/main/assets/colors.properties` (the key and its comment), `AppConfig.kt` (the accessor and its loader line) and the `color-scheme.md` row. The hop that lands this also writes a corrected `## Implemented` entry, since the one on file still names the rejected shape and the removed token.
- **Second pass, decided on the device ask of 2026-09-15 — the label's side and its colour.** The horizontal padding becomes asymmetric: `start = 6.dp`, because that is what holds the bar on the button's left edge, and `end = 2.dp`, because the right side is slack — which hands the label box about 4 dp more room and makes a raised font scale far less likely to overflow. The labels stop reading `AppConfig.uiTextPrimary` and read `AppConfig.uiTextSecondary` (`#FF78909C`, the palette's own token for secondary information): white on a card that is white at 50 % is illegible, the dark scrim that used to sit under them is gone, and a mid blue-grey reads over both bright water and dark where pure white fails one way and pale greys the other. `ui.footer.text` (`#FF546E7A`) was the darker alternative; `semantic.inactive` and its alias `ui.dashboard.status.absent` are the trap — white at 20 %, near-invisible on this card.
- **Considered and dropped the same day:** halving the card's alpha (0.50 to 0.25) as a transparency trial. It would have been the card's own value rather than a change to `status.gps.alpha.dimmed`, which the three buttons share, but it was set aside before the device run, so nothing here changes the background.
- **Third pass, decided on the device ask of 2026-09-15 — the corners and the label's weight.** The card's corner radius drops from 12 dp to **8 dp**, the radius the toggle buttons themselves wear (`RoundedCornerShape(8.dp)` in `MapControls.kt` lines 66, 131, 157 and 187, and `TrackStatusIcon.kt` line 90), so the strip reads as one of the row's own squares rather than as a panel sitting beside them. The scale's values go bolder at the same 10 sp — `FontWeight.Bold`, with SemiBold as the step to take if the device finds Bold heavy — which also answers part of the contrast the second pass left open, a heavier stroke of the same colour reading stronger over pale water. The fit survives it: the label box is 16 dp against a two-digit label of about 11.1 dp, and bold costs at most a fifth of a dp on tabular figures, which moves the font scale at which a label starts to clip from about 1.44× to about 1.42× — the knob then being the label's size, never the card's width. The bar's end insets are measured from the same bold face the labels draw, `rememberedLabelHalfHeightDp()`'s `TextStyle` having been handed the weight, so the endmost labels stay whole. Neither the bar's 150 dp nor the card's 166 dp moves.

## 3. Verification

- `apk-build.bat` SUCCESS with the scoped `ui.map` and `config` tests green: the gate's rewritten cases, `chevronCasingOffset(22f, 14f) == 4f` with the retuned rim literals in `TrackDirectionOverlayTest`, and the width-key checks in `TrackOutlineTest` untouched.
- Device, on a track set with some transparency: in Colours unselect the track and the scale stays; switch the tracks layer off and it goes; tap the eye on a selection and it stays; leave the eye set with nothing selected and it goes.
- The strip's two vertical edges are the leftmost toggle button's two edges, in both orientations and whether or not the recenter button is on the row, and its distance below the row is the same gutter the buttons sit from each other.
- The three disabled toggles side by side paint one weight — white at 25 % — and the card matches them, its labels legible over the palest water, with Bold weighed against SemiBold.
- Nothing else moves: the rim change is invisible beyond the selected track's arrowhead edging, and no key's value, no other mode and no other track is touched.

## 4. History — the decisions the code above came out of

- **The pinned-rim forks of 2026-09-14** — pinned-only, a glyph, a fade, dashes, a wider stroke — were superseded by: every track, all three modes, the type's own colour, a fraction of the core, and an opaque selection in place of a translucent one.
- **Two measured constraints:** an osmdroid `Polyline` carries one `outlinePaint`, so a rim is a second polyline and never a stroke outline; and a rim costs one polyline per track segment, about +100 draw calls at twenty-five stored tracks.
- **The rim is parked.** Its finding: a wider stroke under the interior composites with the interior's alpha across the whole width, so a translucent track read denser than its setting. Three shapes would repair it — offset parallel lines, a geometric ring by path difference, or a rim only where the interior is opaque.
- **The casing was removed and restored the same day in another shape:** the selection is drawn opaque first and cased after, which is what makes it a rim rather than a wash.
- **The arbitration of 2026-09-15 reversed an earlier closure.** The walk had closed "the casing offset on the tempered core"; argued again on the device's ask, it settled the other way — the border belongs to the selection and reads the line's width. The superseded argument, kept beside its cost: the tempered reference holds the dark band 2 px from the coloured centreline rather than 1, which matters only while a coloured arrowhead is translucent, and the selection's are not.
- **Two review passes of 2026-09-15** checked every site named above against the files: the first rewrote the gate's case list and the strip's premise, the second made the gate's input computable, gave the strip's width a unit and its geometry a home, and named the test class the rim change reaches.
- **The file-versus-code alignment is done:** the file is canonical and every default mirrors it key for key — 12 / 14 / 10 / 8 / 6 / 22, knee 10, temper 0.5, max spacing 400, the eight ramp families and the five tick rows — with `TrackOutlineTest` and `HeatmapRampPropertiesTest` holding the two sides against each other.

## 5. Follow-ups — the Ask hops' findings

**Closed 2026-09-15, over five passes:** the strip's stale KDoc; the recenter predicate's duplicate, void because its local went with the width term; the derived-state read, now keyed; the painted id added only where a stroke actually landed; `TOP_TOGGLE_ROW_HEIGHT` reading the square; the three comments that read as before the change; the background comment's figure; the test's hand-mirrored wiring, production and the test now sharing one gate entry point; the square's single home, now `MapControls.kt`'s constant with the buttons' five literals reading it; the gate's unasserted case; the raw-state entry point's name, now `legendVisibleForState`; `colors.properties` line 158's description of `ui.text.scrim`; the measured line box, now the bold face's; the two KDoc links, retargeted with the canonical predicate made private; `MapScreen`'s padding comment naming the side; the `ui.text.scrim` row in `color-scheme.md`; `TOP_TOGGLE_ROW_GAP`, deleted once the strip's gap moved onto the row's gutter; and the disabled background normalised onto **one property**, `ui.button.disabled.background.alpha` at 0.33, read by the GPS DEMO, tracking OFF, lock OFF and the legend card alike — which retired `status.lock.alpha.dimmed` and `status.tracking.alpha.dimmed` as dead and kept `status.gps.alpha.dimmed` for its regulated-zone icon readers.

**Still open:**
- **The row's fourth box is a fifth off-state.** `EarthWaterIcon`'s inactive branch paints `status.earthWater.inactive` at the token's own 20 % with no copy, so it now sits 13 points under its three 33 % siblings; either it reads `AppConfig.buttonDisabledBackgroundAlpha` too, or the 20 % is recorded as deliberate.
- **`docs/ui-component-guidelines.md` still teaches the retired pattern** — its status-icon row gives "Dimmed bg alpha = `status.*.alpha.dimmed` = 0.50" and tells new icons to follow it, where the toggles now read `ui.button.disabled.background.alpha` at 0.33 on a `${semantic.inactive}` fill; point the row at the new key.
- **The strip's label-colour comment still says "white at 25 %"** — `TrackSpeedLegend.kt` line 69 — where the card is 33 %.
- **Nothing tests the new property** — the file-versus-defaults pattern in `TrackOutlineTest` is the home for it; a key and an accessor left unheld are the drift the widths already guard against.
- **Two doc nits beside it** — the DEMO swatch in `color-scheme.md` carries no `opacity` where its neighbours do, and the Earth/Water inactive row claims a default of `#EEFFFFFF` against the file's `${semantic.inactive}`.
- **`FEAT_DSC_Tracks.md`'s entry at line 372 still asserts 50 %** for the card and "twice as faint" for tracking and lock, both falsified by the property; it needs the superseded annotation its own predecessor carries.
- **Nothing guards the legend's look** — the radius, the weight, the colour and now the alpha are device-only invariants.

## Outcome

**Shipped 2026-09-15 on `feature/no-black-casing`**, branched from `b2f6d69`: the widths became configuration; the selection became opaque with its casing restored and the chevrons put back in proportion; the chevron rim became outside-only with its arms wrapped to the tips; and the controls were separated — arrows to the mode, the eye to the fill and persisted, the legend given the eye as a trigger.

- **Deviations from the plan as first written:** the casing width was set by hand to 22 rather than left at the 24 the first pass took, and the chevron rim's offset read the tempered core until the arbitration of 2026-09-15 reversed it — §2.1 is that reversal, unbuilt.
- **Left open:** §5's follow-ups, and the device look at all three modes with a translucent track set.
- **Not in this plan, recorded so it is not lost:** the rim and its three shapes (parked above), and the scale's own foot — the bar runs 2 → 35 kn while the ramp paints eight families to 70, so the top half is painted and never labelled. That is a subject of its own, and it owns no walk item today.
