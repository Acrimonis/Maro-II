<!-- scope: feature -->
# Track outlines — the selection, the widths and the chevrons

**Status:** shipped on `feature/no-black-casing` (§4 and Outcome) except §2, which is the work that is left · **Filed** 2026-09-14, **rewritten implementation-only** 2026-09-15 with two review passes of that day folded in · **Name** kept as `pinned-cue-casing` although the subject widened from a pinned-only casing to the whole selection rendering.

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

### 2.3 The strip's chrome

- **Change, the width** — the row's geometry gets one home at file level in `MapScreen.kt`, beside `TOP_TOGGLE_ROW_HEIGHT` at line 210: the square (44 dp) and the gutter (6 dp), read by the row, by the locked mirror's `start = 6.dp + (44.dp + 6.dp) * 3` at line 2121, and by the strip. The strip's width is then `44 × count + 6 × (count − 1)` — **194 dp**, or **244 dp** when the recenter button joins under `appSettings.gpsMode && autoFollowSuppressed` ([line 2376](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2376), in scope at the legend site) — and its start stays the row's own `6.dp`, which the legend already uses at line 1401. Those are widths from the row's left edge; the 200 dp and 250 dp the plan named before are edge coordinates measured from the screen.
- **Change, the slack** — the strip's content aligns to the start (`Alignment.Start` where [`TrackSpeedLegend`](app/src/main/java/ykws/android/maro/ui/map/TrackSpeedLegend.kt:80) centres today), leaving the card's own 10 dp padding as the inner inset, so the bar sits on the row's left edge rather than centred in a wider card.
- **Change, the background** — a new token `ui.legend.background` in `app/src/main/assets/colors.properties`, beside `ui.text.scrim` and `ui.divider.color`, with `AppConfig.uiLegendBackground` defaulting to `0x4D16213E` so the look is unchanged, read through `AppConfig`'s `var … : Int` with `private set` plus one loader line, and used in place of `AppConfig.uiTextScrim` for the card only — that token keeping its other consumer in `RegulatedZoneComponents.kt`. A row is added for it in `docs/color-scheme.md`, whose `ui.*` table already lists its siblings, or the exemption is recorded rather than assumed.
- **Why the row cannot simply be read** — it is wrap-content (`padding(top, start = 6.dp)` and `spacedBy(6.dp)`, no width) and its squares live in `ui/map/MapControls.kt`, so no existing value gives the strip both edges.
- **Untouched** — the bar (14 dp × 150 dp), the 6 dp spacer, the 10 sp labels, the measured end insets and the 1 dp divider border keep exactly the geometry they have. No alias is needed for the token and no rebuild key is involved, the key list carrying widths, ramp, knee and temper only.

## 3. Verification

- `apk-build.bat` SUCCESS with the scoped `ui.map` and `config` tests green: the gate's rewritten cases, `chevronCasingOffset(22f, 14f) == 4f` with the retuned rim literals in `TrackDirectionOverlayTest`, and the width-key checks in `TrackOutlineTest` untouched.
- Device, on a track set with some transparency: in Colours unselect the track and the scale stays; switch the tracks layer off and it goes; tap the eye on a selection and it stays; leave the eye set with nothing selected and it goes.
- The strip's left and right edges line up with the toggle row above it in both orientations, with and without the recenter button, and its card reads as its own surface rather than as a dimmed button.
- Nothing else moves: the rim change is invisible beyond the selected track's arrowhead edging, and no key's value, no other mode and no other track is touched.

## 4. History — the decisions the code above came out of

- **The pinned-rim forks of 2026-09-14** — pinned-only, a glyph, a fade, dashes, a wider stroke — were superseded by: every track, all three modes, the type's own colour, a fraction of the core, and an opaque selection in place of a translucent one.
- **Two measured constraints:** an osmdroid `Polyline` carries one `outlinePaint`, so a rim is a second polyline and never a stroke outline; and a rim costs one polyline per track segment, about +100 draw calls at twenty-five stored tracks.
- **The rim is parked.** Its finding: a wider stroke under the interior composites with the interior's alpha across the whole width, so a translucent track read denser than its setting. Three shapes would repair it — offset parallel lines, a geometric ring by path difference, or a rim only where the interior is opaque.
- **The casing was removed and restored the same day in another shape:** the selection is drawn opaque first and cased after, which is what makes it a rim rather than a wash.
- **The arbitration of 2026-09-15 reversed an earlier closure.** The walk had closed "the casing offset on the tempered core"; argued again on the device's ask, it settled the other way — the border belongs to the selection and reads the line's width. The superseded argument, kept beside its cost: the tempered reference holds the dark band 2 px from the coloured centreline rather than 1, which matters only while a coloured arrowhead is translucent, and the selection's are not.
- **Two review passes of 2026-09-15** checked every site named above against the files: the first rewrote the gate's case list and the strip's premise, the second made the gate's input computable, gave the strip's width a unit and its geometry a home, and named the test class the rim change reaches.
- **The file-versus-code alignment is done:** the file is canonical and every default mirrors it key for key — 12 / 14 / 10 / 8 / 6 / 22, knee 10, temper 0.5, max spacing 400, the eight ramp families and the five tick rows — with `TrackOutlineTest` and `HeatmapRampPropertiesTest` holding the two sides against each other.

## Outcome

**Shipped 2026-09-15 on `feature/no-black-casing`**, branched from `b2f6d69`: the widths became configuration; the selection became opaque with its casing restored and the chevrons put back in proportion; the chevron rim became outside-only with its arms wrapped to the tips; and the controls were separated — arrows to the mode, the eye to the fill and persisted, the legend given the eye as a trigger.

- **Deviations from the plan as first written:** the casing width was set by hand to 22 rather than left at the 24 the first pass took, and the chevron rim's offset read the tempered core until the arbitration of 2026-09-15 reversed it — §2.1 is that reversal, unbuilt.
- **Left open:** §2's three items, and the device look at all three modes with a translucent track set.
- **Not in this plan, recorded so it is not lost:** the rim and its three shapes (parked above), and the scale's own foot — the bar runs 2 → 35 kn while the ramp paints eight families to 70, so the top half is painted and never labelled. That is a subject of its own, and it owns no walk item today.
