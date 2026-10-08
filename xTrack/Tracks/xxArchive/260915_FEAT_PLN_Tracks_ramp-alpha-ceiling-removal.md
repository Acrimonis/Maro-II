<!-- scope: feature -->
# Ramp alpha — drop the coreAlpha ceiling

**Date:** 2026-09-15 · **Status:** shipped the same day — `apk-build.bat` SUCCESS, 63 heatmap tests with the three pre-existing reds unchanged
**Request:** remove the `track.heatmap.coreAlpha` ceiling so a banded stroke is drawn at the track's own transparency value and nothing else.

## 1. What exists today

- [`bandedAppearances`](../../app/src/main/java/ykws/android/maro/ui/map/TrackSpeedHeatmap.kt:105) multiplies two alphas: `ramp.coreAlpha` times the track's own fade, so no banded stroke reaches the opacity the user's transparency sliders ask for.
- The ceiling is R4's resolution from the selected-track heatmap plan — a fixed 0.9 "so the ramp is never washed out" ([line 56](260914_FEAT_PLN_Tracks_selected-track-speed-heatmap.md:56)) — re-scoped into a multiplier by **D8** when the ramp gained every stored track ([line 28](260914_FEAT_PLN_Tracks_render-modes.md:28)).
- As shipped the value is a ceiling, not the floor R4 asked for: the multiplication can still take a heavily faded track towards invisible, so the legibility it was written for is not actually guaranteed.

## 2. Decision

- A band's alpha is the track's own fade alone; the multiplication goes.
- The key, its `AppConfig` parse and `HeatmapRamp.coreAlpha` all go with it — a knob with one sane value is dead state every caller must read to learn it does nothing. Rejected alternative: keep the key defaulted to 1.0, which preserves tunability at that price.
- Supersedes R4's resolution line and D8's "times `ramp.coreAlpha`" clause; both plans otherwise stand as shipped.
- Not a cue change: two tracks sharing one transparency value were already indistinguishable, cap or no cap, and removing the cap widens the range rather than narrowing it, so the parked Colours cue item (walk item 3) is unaffected in kind.

## 3. Work package

- `ui/map/TrackSpeedHeatmap.kt` — `bandedAppearances` takes the fade as its alpha; the ramp parameter stays for colour and span, `coreAlpha` references and their KDoc go.
- `config/HeatmapRamp.kt` — `coreAlpha` leaves the data class and the KDoc.
- `config/AppConfig.kt` — the `track.heatmap.coreAlpha` parse and the default constructor argument go.
- `app/src/main/assets/maro.properties` — the key and its comment line go.
- Tests — `TrackSpeedHeatmapTest`'s fade/alpha expectations and `HeatmapRampPropertiesTest`'s parsed-versus-defaults equality, which constructs the ramp with `coreAlpha`.

## 4. Verification

- `apk-build.bat` green and the scoped heatmap tests green.
- Three `HeatmapRampPropertiesTest` reds predate this change (the file's family values and tick rows drifted); they stay red and are not evidence against this work.
- Device: with the newest transparency at 0 the newest banded track reads fully opaque, and every other track reads exactly what its own slider says.

## 5. Out of scope

- The `##4CAF50` truncation in the same properties block, still awaiting confirmation of the symptom.
- The families, the tick table, the carry window and the legend.

## Outcome

**Shipped 2026-09-15** through a Code pass on the same day the decision was taken.

- **Built:** `bandedAppearances` takes the track's own fade as its alpha; `HeatmapRamp.coreAlpha`, its `AppConfig` parse and the `maro.properties` key are gone; `TrackSpeedHeatmapTest` and `HeatmapRampPropertiesTest` were retuned, the former's two test names no longer carrying the removed concept.
- **Deviations, both recorded:** the key had no comment line of its own, so only the shared group header's stale wording was trimmed; two KDoc clauses outside the field list named the removed value and were corrected with the code.
- **Void in §5:** the `##4CAF50` note no longer applies — the properties block was rewritten during the same session and now holds eight families on its own palette, so nothing there carries a doubled hash. What keeps the three reds alive is a different drift: the file's ramp against `AppConfig`'s seven-family default, and its `unknownColor=#00FF00` against the `#FF90A4AE` this change's sibling plan specified.
