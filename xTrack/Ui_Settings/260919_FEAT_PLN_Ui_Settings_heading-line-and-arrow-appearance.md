# FEAT_PLN — Ui_Settings — heading line and head arrow appearance

**Status:** implemented 2026-09-19 on `feature/extra-settings` through the `#implement` pipeline — Code, one Ask review, one repair hop; `apk-build.bat` SUCCESS and the scoped run holding its known six pre-existing reds, with the three new test classes green. The device pass is owed. Captures the 2026-09-19 discussion, which follows the strokes change shipped the same day.
**Feature:** Ui_Settings (settings UI + persistence); the two overlays themselves belong to Navigation's map surface.
**Related docs:** [`docs/ui-component-guidelines.md`](../../docs/ui-component-guidelines.md) (row families, Card/Expander/NestedCard), [`docs/color-scheme.md`](../../docs/color-scheme.md) §7.

## 1. Request

- **Thickness, colour and transparency** for both the head arrow and the heading (direction) line.
- **A colour mode for the arrow**, labelled Speed Colour, under which its colour follows the speed the boat carries; the manual row keeps its own name, Default colour, and stays active either way.
- Presented as **collapsible Appearance expanders** in the Navigation tab, each under the section that owns its item.

## 2. What the code carries today

| Overlay | Baked today | Already a key | Setting today |
|---|---|---|---|
| Head arrow — [`CapArrowOverlay`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlays.kt:314) | shaft `2.25.dp`, head `9.dp` at `0.5` rad half-spread ([`:341`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlays.kt:341)) | `map.navigation.arrow.color`, and its length scales by `map.marker.size.zoomExponent` | only its visibility, `capArrowVisible` ([`MapScreenSettingsOverlay.kt:964`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:964)) |
| Heading line — [`DirectionLine`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlays.kt:368) | width `1.dp`, dash `12f, 6f` px ([`:382`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlays.kt:382)) | `map.navigation.line.color`, whose packed `#4D` is the line's own 30 % | none |

- Both composables read `AppConfig` for their colour **inside** the canvas, so no settings plumbing exists for either beyond the arrow's toggle.
- Both colours already live in `maro.properties`, having moved there with the strokes change the same day — so a setting for each is a seed and a control, not a file move.
- Neither overlay is zoom- or layer-gated the way the osmdroid layers are: they are Compose canvases, so a value passed from `MapScreen`'s settings simply recomposes them.

## 3. The seven settings

| Property key | `AppSettings` field | Prefs key | Default | Control |
|---|---|---|---|---|
| `map.navigation.arrow.widthDp` | `navigationArrowWidthDp` | `navigation_arrow_width_dp` | 2.25 | thickness `SliderRow`, 1–8 dp, 0.25 steps |
| `map.navigation.arrow.transparencyPct` | `navigationArrowTransparencyPct` | `navigation_arrow_transparency_pct` | 0 | `SliderRow`, 0–100, 5 % snap, commit on release |
| `map.navigation.arrow.color` | `navigationArrowColor` | `navigation_arrow_color` | `#1565C0` | `ColorRow`, labelled **Default colour** |
| `map.navigation.arrow.followSpeedColour` | `navigationArrowFollowSpeedColour` | `navigation_arrow_follow_speed_colour` | false | `ToggleRow`, labelled **Speed Colour**, above the colour row |
| `map.navigation.line.widthDp` | `navigationLineWidthDp` | `navigation_line_width_dp` | 1 | thickness `SliderRow`, 0.5–4 dp, 0.25 steps |
| `map.navigation.line.transparencyPct` | `navigationLineTransparencyPct` | `navigation_line_transparency_pct` | 70 | `SliderRow`, 0–100, 5 % snap, commit on release |
| `map.navigation.line.color` | `navigationLineColor` | `navigation_line_color` | `#1565C0` | `ColorRow`, labelled **Default colour** |

- Same pattern as the strokes: the property is the home of the value, the setting is the user's override seeded from `AppConfig`, and each value clamps once — where the setting is read.
- **The line's 30 % leaves its colour value for its transparency.** The packed `#4D` is currently part of `map.navigation.line.color`; keeping it there while adding a transparency control would give one alpha two homes, so the key becomes the opaque hue and the transparency defaults to 70, which renders the same 30 %.
- **The arrow defaults to fully opaque** (transparency 0), matching its colour today, so neither default changes the shipped look.
- **The head stays derived from the shaft**, at today's 4 : 1 ratio (9 dp over 2.25 dp), so one knob moves a coherent arrow. The rejected alternative was a fourth control for the head, which a user could then set against a shaft it no longer matches.
- **The spans bracket the shipped values with headroom**, and 0.25 dp steps keep the arrow's 2.25 reachable rather than snapping it to 2.5 — a value change nobody asked for.
- **Units are dp**, because these are Compose strokes, and the key says so (`widthDp`), following the file's newer unit-suffixed keys.

### 3.1 Unit facts to carry

- **The dash becomes proportional to the line's thickness** (settled 2026-09-19, overruling the first draft's leave-it-in-px). Today's `12f, 6f` px beside a `1.dp` stroke is 4 : 2 against the stroke at 3× — and the same fixed px on a 1× screen draws 12 : 6 on a hairline, which is exactly the inconsistency a thickness control makes visible. So the dash becomes **4 × width on and 2 × width off**, taken through `toPx()` in the canvas: identical to today's look on this phone, and proportional at every thickness the slider offers.
- **Alpha needs a Compose sibling of the shared helper.** `transparencyPctToAlpha()` ([`MapOverlayRenderer.kt:35`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:35)) returns an Int for osmdroid paints; the canvases need a fraction. The sibling belongs beside it, so the concept keeps one home.

### 3.2 The arrow's colour mode

- **One toggle, default off**, under which the arrow's colour comes from the speed it is carrying rather than from its own colour row — so the arrow agrees with what the live trace paints at that speed.
- **The colour source is the ramp the tracks already paint from**, read through the two functions that already own it — `quantiseKn` for the band and `colorAt` for its colour ([`TrackSpeedHeatmap.kt:77`](../../app/src/main/java/ykws/android/maro/ui/map/TrackSpeedHeatmap.kt:77)) — so no second speed-to-colour rule enters the app.
- **The rejected alternative was the compliance colouring**, which the dashboard already speaks as safe at the limit, caution to 1.4 times it and danger beyond ([`DashboardPanel.kt:574`](../../app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt:574)). It is a colour of *compliance* rather than of speed alone, it needs the current zone's limit beside the speed, and it is already the subject of the parked dashboard-arrow todo — so if it lands, it lands there, reusing those thresholds rather than restating them.
- **The speed it reads is the one the arrow already has** — `navigationState.speedKnots ?: demoSpeedKnots`, the same value that drives its length — so the mode needs no new plumbing and works in demo mode as it does under GPS.
- **The mode is labelled Speed Colour and the manual row Default colour** (settled 2026-09-19). The names say which applies when, so the row needs no gating: **the Default colour row stays active whether the mode is on or off** — nothing greys out, nothing stands down.
- **The rejected alternative was disabling the row while Speed Colour is on.** It lost twice over: a greyed row cannot be set before the mode is switched off, and the two labels already state the precedence without introducing a disabled-row state the guidelines do not carry.
- **Transparency is independent of the mode** and still applies, so a speed-coloured arrow can be dimmed by the same row.
- The strongest objection to the mode survives its labels: the arrow has two colour sources, so a user can set Default colour while Speed Colour is on and see nothing happen until they switch it off — legible, but not removed, by the naming.

### 3.3 The cost of refreshing in real time

- **The mode adds no new refresh rate.** The overlay already takes the whole navigation state, so it recomposes whenever the speed that drives its length moves, and it redraws on each of those; the colour adds one lookup inside a draw that was already happening.
- **That lookup must be allocation-free** — read the precomputed ramp's band, build no list and no colour object per frame — or a cheap draw becomes garbage per frame.
- **A rate floor is not what fixes the visible problem.** The ramp is quantised into families, so the painted colour changes only when the speed crosses a band boundary, and what that produces is chatter when the boat sits on one; hysteresis at the boundary answers that, a throttle does not.
- **If a floor is wanted anyway, gate the colour rather than the frame:** keep the last applied band and its time and re-read only when the band differs — the pattern the inspect mode's quiet clock already uses, with its own millisecond key.
- **Staleness is already a signal the view model publishes.** `gpsStale` is combined with the speed for the notification path today ([`MainActivity.kt:104`](../../app/src/main/java/ykws/android/maro/MainActivity.kt:104)), so the arrow can take that flag as a parameter — no freshness notion needs inventing, and what it paints while the flag is set is the rule two bullets below.
- **A lost fix does not null the speed, so the arrow does not hide.** The navigation state is written only inside the fix callback ([`NavigationViewModel.kt:946`](../../app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt:946)), so a lost fix writes nothing and the last speed stands: the arrow **freezes at its last length** and keeps drawing, with no tell that the reading has died. A fix that carries no speed field does write null, and only that hides the arrow through its minimum-speed gate ([`MapOverlays.kt:323`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlays.kt:323)).
- **The freeze stays exactly as it is — decided 2026-09-19, and this work does not touch it.** The arrow keeps drawing at its last length through an outage; the one thing the mode adds is the colour. While `gpsStale` is set it paints the ramp's own **unknown colour** — `track.heatmap.unknownColor=#f5f5dc` ([`maro.properties:179`](../../app/src/main/assets/maro.properties:179)), the beige the ramp already carries for an unquantifiable speed ([`AppConfig.kt:1021`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:1021)) — so the arrow goes neutral instead of asserting a band it can no longer justify.
- **The rejected alternative was hiding the arrow while stale**, which would have removed a heading aid whose frozen length the user still reads; keeping the behaviour and changing only the colour leaves the overlay's shape untouched and its claim honest.
- **The input rate is the navigation state's own, and there is no gate.** The overlay is called with `navigationState` and `zoomLevel` ([`MapScreen.kt:3137`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3137)), and that state is a plain state flow written per location sample ([`NavigationViewModel.kt:282`](../../app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt:282), `:954`) — no `sample()`, no debounce, no animation between values, so the arrow steps once per fix and once per zoom change.
- **The `sample(...)` calls nearby belong to other surfaces** — the map centre at 333 ms, the shore recompute, the compass heading at its own interval and the camera target — so none of them gates this overlay.
- **The closest thing to a 3×/s floor is demo mode's own throttle**: its pan speed is recomputed at most every 333 ms (`MIN_DEMO_SPEED_INTERVAL_MS`), so under demo the arrow already changes at exactly that cadence, and under GPS the cadence is per fix.
- **What that means for a floor:** a 3-per-second minimum can only lower a rate, and the GPS path's own rate is at or below it already, so the floor would rarely or never bite — the gate that changes anything is the band-change gate with hysteresis above. The GPS sample interval is set by the app's adaptive tuning and is not read here; a ceiling that cannot bite needs no reading.
- **The mode adds no new visible cadence either way:** nothing animates between values, so a speed-coloured arrow steps between band colours per fix exactly as its length steps today.

### 3.4 The head hides the shaft's end

- **The shaft's round cap currently shows above the head's apex** (reported 2026-09-19): the stroke is drawn to the apex with `StrokeCap.Round`, so its semicircle — radius half the shaft's width — pokes past the triangle's point.
- **The fix is an inset on the shaft, not a shift of the head**, so the arrow's length, which encodes speed, stays exactly what it was: the shaft's polyline ends at `endY + ((strokeWidth / 2) / sin(halfSpread)) + margin`. The divisor is **`sin`, not `tan`** — the distance from a circle's centre to a line it touches is `depth × sin(h)` — and the first draft's `tan` was short by about 12 %, which left a sliver of cap visible beside the flanks; the small named margin over true tangency keeps anti-aliasing from drawing a seam.
- **The inset is derived from the shaft's width and the head's half-spread**, so it holds for every thickness the slider offers; with the head derived at 4 : 1 an inset of about `1.04 × width` always lands inside the triangle's own length. It is extracted as a pure function beside `capArrowHeadDp()` so both ends of the 1–8 dp span can be asserted.
- **The head is capped against the arrow's own length** — `min(4 × width, half the arrow's length)` — because that ratio is independent of how short the arrow gets at low speed: without the cap, the slider's top end draws a 32 dp head over a few dp of shaft.
- The rejected alternative was shifting the whole triangle up by the cap's radius, which hides the cap but lengthens the drawn arrow by half its width — a change to the very value the arrow exists to state.

## 4. UI placement

- **Navigation tab** — [`NavigationSettings`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:942).
- **Both items already share one card: Orientation aids** ([`:953`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:953)), which today holds three toggles — heading line (`headingLineVisible`, `:957`), cap arrow (`capArrowVisible`, `:964`) and demo heading up (`demoHeadingUp`, `:971`). The line already has its visibility toggle, which closes the open question the first draft carried.
- **Each expander sits directly below its own toggle** (settled 2026-09-19, overruling the first draft): **Heading Line Appearance** under the Heading line toggle, **Arrow Appearance** under **Variable arrow**, so each pair reads on/off and then how, item by item. The rejected alternative was collecting both expanders below all three toggles, which separates a control from the switch it belongs to. The toggle keeps its existing **Variable arrow** name beside an **Arrow Appearance** expander; the pairing is accepted rather than renamed.
- **Row order inside each expander: thickness, then transparency, then colour** — the order the coastline card took the same day, so the tab's appearance cards read alike. The arrow's expander carries one extra row, the **Speed Colour** toggle, above the **Default colour** row it deliberately does not disable.
- **Strings, in both locales:** the two expander labels, the two thickness labels with a `%.2f dp` value format, the two colour row labels, and the transparency rows reusing the shared `settings_transparency_border_fill_label` vocabulary where it fits a single stroke.

## 5. Work breakdown

1. Add the seven fields to `AppSettings` with their prefs keys and their load and write paths, each seeded from `AppConfig`; clamp the two widths and the two transparencies once, on read.
2. Add the five new keys to `maro.properties` — `map.navigation.arrow.widthDp`, `.transparencyPct` and `.followSpeedColour`, and `map.navigation.line.widthDp` and `.transparencyPct` — and strip the packed `#4D` from the line's colour key so its alpha has one home.
3. Add the Compose alpha sibling beside `transparencyPctToAlpha()`, the head derivation as a pure function so it can be tested, and the ramp's band lookup as an allocation-free read.
4. `CapArrowOverlay` takes its width, colour, transparency, colour mode and the staleness flag as parameters and deletes its `AppConfig` reads; derive the head from the shaft, paint the ramp's unknown colour while the flag is set, and end the shaft at the inset that hides its round cap inside the head. `DirectionLine` takes its three and draws its dash at 4 × and 2 × its own width.
5. Pass the seven values plus the staleness flag from `MapScreen`, where `appSettings` and `gpsStale` already reach both overlays.
6. Add the two collapsible Appearance expanders to the Orientation aids card, each directly below its own toggle — Heading Line Appearance under the Heading line toggle, Arrow Appearance under Variable arrow — with **Speed Colour** above the arrow's **Default colour** row, which stays active in both states.
7. Write the EN and FR strings.
8. Add unit coverage: the seven seeds, the clamps, the round-trip, the head derivation at the shipped ratio, and the band lookup.
9. Run `apk-build.bat` and the scoped `ui.map` + `config` run, confirming the red set is unchanged.

## 6. Verification

- `apk-build.bat` SUCCESS with no new warnings, and the scoped run holding its known red set.
- Device pass: both thicknesses at both ends of their spans, both colours, both transparencies at 0 and 100, the arrow's colour mode on and off, and a simulated fix loss — where the arrow must hold its last length and turn the ramp's neutral colour rather than disappear. The transparencies also show that 100 hides its overlay, which is why the line needed no new visibility toggle.

## 7. Decisions and open questions

- **Decided** — dp units with the unit in the key; the line's alpha moved into its transparency default of 70; the arrow opaque at 0; the head derived from the shaft and capped against a short arrow; the dash proportional at 4 × and 2 × its width.
- **Settled by the code rather than by choice** — the line already has a visibility toggle (`headingLineVisible`) and both items already share the Orientation aids card, so the two expanders have a home and no new section is invented for them.
- **Answered from the code rather than left open** — the mode reads the track ramp through `quantiseKn` and `colorAt`; the compliance colouring stays the parked todo's subject, its thresholds already living in the dashboard; and the stale case is `gpsStale`, already published by the view model, with a lost fix freezing the arrow rather than hiding it.
- **Decided** — the mode is labelled **Speed Colour**, the manual colour row **Default colour**, and that row stays active in both states, so no disabled-row treatment is created for it.
- **Settled in implementation** — the two transparency rows reuse the shared `settings_transparency_border_fill_label`, the key the coastline row shipped the same day already uses; its name is broader than a single stroke, and that residue is accepted rather than renamed here.
- **Ruled by the Ask hop and confirmed** — the stale neutral tint governs the speed-derived colour alone. The Default colour asserts nothing about the reading, so staleness has no claim to retract there; recolouring it would mutate a value the user set deliberately and make its meaning depend on a mode that is switched off. The strongest objection, that a stale arrow then looks live with the mode off, is answered by staleness having its own indicator on the GPS icon rather than needing the arrow to repeat it.
- **Decided** — while the fix is stale the arrow keeps its frozen length and paints the ramp's unknown colour, `track.heatmap.unknownColor`; nothing about its visibility or its length moves, so the only new thing is the colour it wears once the reading has died.
- **Decided 2026-09-19 — four corrections from the device review.** Each Appearance expander moves directly below its own toggle, Heading Line Appearance under Heading line and Arrow Appearance under Variable arrow; the line's dash becomes proportional to its thickness rather than a fixed 12/6 px; and the shaft is inset so its round cap hides inside the head, the arrow's length left untouched. The toggle keeps its **Variable arrow** name beside the **Arrow Appearance** expander, that pairing accepted rather than renamed.

## 8. Explicitly out of scope

- The arrow's speed scaling — `CAP_DP_PER_KNOT`, its dp clamp pair, its speed floor and the reference zoom are recorded as deliberate constants in [`maro.properties:265`](../../app/src/main/assets/maro.properties:265), so exposing the arrow's length would overturn a written decision.
- The parked px-to-dp pass over the whole paint code; the direction line's dash is no longer part of it, having become proportional to its own stroke.
- The dashboard heading arrow's colour-by-speed-compliance item, which stays in global todos.

## 9. Session state

- Follows the stroke-width and shoreline-colour change at `217a405` on `feature/extra-settings` — rebased onto `origin/develop`, pushed, pull request open at `https://github.com/Acrimonis/Maro-II/pull/new/feature/extra-settings`.
- The device pass over that earlier change's three widths, transparency and two colours is still owed.
