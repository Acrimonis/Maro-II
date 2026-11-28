<!-- scope: feature -->
# Zone info: hold a tag to toggle, and a water-source line

**Status:** implemented, device pass pending · **Branch:** `feature/zones-temp-info` · **Date:** 2026-11-28

## 1. Asked

- Holding a zone tag at the bottom left toggles the zone info labels.
- A temperature label joins those labels, naming where the water temperature comes from. A suggestion was asked for.
- The 300 m tag defect is fixed in the same work (D2).

## 2. Assessment

| Subject | Today | Consequence |
|---|---|---|
| Info labels | Gated by `regulationInfoVisible`, written only by the Settings switch | The hold flips the same key, so the switch stays in step |
| Zone tags | [`RegulatedZoneWarningStrip`](../../app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt) draws squares with no pointer input; [`MapSurface`](../../app/src/main/java/ykws/android/maro/ui/map/MapSurface.kt) ignores a long press without a tap | The gesture goes on the strip, not through the square |
| Info panel | `RegulatedZoneInfoText` returns early with no zone line | It has to render for the water line alone |
| Source values | [`AverageSeaFetcher`](../../app/src/main/java/ykws/android/maro/data/temperature/AverageSeaFetcher.kt) returns each source's value; only the refresh pill reads them | They must be cached |
| Cache | [`CacheEntry`](../../app/src/main/java/ykws/android/maro/data/temperature/TemperatureCache.kt) holds the blended value, the fetch time and the forced mark | One new field |
| 300 m tag | `regulatedZoneTags` answers empty before the band entry is injected | Fixed by §5 |

## 3. Hold a zone tag

- A long press anywhere on the tag column flips `regulationInfoVisible`, with the same vibration the temperature tile gives.
- A tap stays without effect and shows no ripple.
- With no zone tag on screen, the Settings switch is the one way (D1).
- Implementation, on the strip's column: `detectTapGestures(onLongPress)` reading the callback through `rememberUpdatedState`, plus an `onLongClick` semantics label. The callback comes from the screen through `MapContent` and writes through `viewModel.updateSettings`.
- The temperature tile is outside the strip and keeps its own tap and hold.
- A locked screen ignores the hold, [`LockScrim`](../../app/src/main/java/ykws/android/maro/ui/map/MapControls.kt) consuming every pointer event.
- Expected side effect, to confirm on the device: a map drag starting on the tag column no longer pans the map.

## 4. Water-source line (suggestion)

```
EN  🌊 Water — Marine 17.4° · ECMWF 17.1° · Copernicus 16.8° · 14:32
FR  🌊 Eau — Marine 17,4° · ECMWF 17,1° · Copernicus 16,8° · 14:32
```

- **Names:** Marine is Open-Meteo's marine model, ECMWF is ECMWF IFS through Open-Meteo, Copernicus is the Mediterranean satellite analysis.
- **Figures:** each source's own value, formatted as in the refresh pill. A source that failed or had nothing reads as the dash; the two cases are not told apart.
- **A dash stays:** when the other sources answer, that sample point is not asked again until its value expires or the temperature tile is held.
- **Time:** when the app fetched the value, not when the sea was measured. A value from an earlier day shows its date instead.
- **No blended figure:** the tile already shows it.
- **Place:** the topmost label, directly above the zone lines. The label column is bottom-aligned and its lines are shorter than the squares, so it sits level with the tile only when no zone tag shows.
- **Shown when:** info labels on, temperature on, marker on water, and source values recorded. It does not depend on the tile showing air or water, nor on any zone tag being present.
- **Outdated value:** muted text, as on the tile.
- Alternatives: one line per source (easier to read, three times as tall), or each source's weight in the blend (too technical).
- Strongest objection: on a narrow portrait screen the single line is likely to wrap onto two.

### Data changes

- A `SeaSource` enum, declared beside `AverageSeaFetcher`, fixes one order for the fetchers in `TemperatureViewModel` and the figures on the line.
- `CacheEntry` gains the source values, empty by default. Old cache files still load and the cache signature is unchanged, so offline values survive the update; the line appears from the next fetch, which a hold on the temperature tile forces.
- The engine keeps the source values beside the held-over water value. `TemperatureState` gains a separate water-source field (values, fetch time, outdated flag), filled from the same entry as the water reading. `TemperatureReading` is unchanged.

### UI changes

- `RegulatedZoneInfoText` takes the water line as an optional slot and composes when the slot or any zone line is there. The line's card is extracted and shared by both.
- The water line is its own composable collecting the temperature state, so a temperature change does not recompose the map content.
- Strings in both locales: the line format, the hold's accessibility label, and a reworded `settings_reg_info_desc`, which names zone text only today.

## 5. 300 m tag fix

- In `regulatedZoneTags`, the two empty answers ahead of the band injection go: no containing zone, or no zone passing the filters, still yields the band entry. Outside the band the answer stays empty.
- This restores the behaviour before `b0fc429d` and the [feature rule](../RegulatedZones/FEAT_DSC_RegulatedZones.md).
- No tag on land: [`isIn300mZone`](../../app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt) requires water.
- Visible result: inside the band and outside every regulated zone, the band's speed tag, its text line and the banners' inset are back.

## 6. Decisions

| # | Question | Answer (2026-11-28) |
|---|---|---|
| D1 | With no zone tag on screen there is nothing to hold. Is Settings enough there? | Yes |
| D2 | Fix the 300 m tag defect in the same work? | Yes |

## 7. Finding, not in scope

- `temperature.air.expiry.min` and `temperature.water.expiry.min` differ from their own `tldr` lines, the `AppConfig` defaults and the temperature plan. Present since `6f63f1a5`.

## 8. Steps

1. The 300 m fix and its tests.
2. The hold gesture, its callback, the accessibility label.
3. `SeaSource`, the cached source values, the engine's water-source state.
4. The water line, the shared card, the strings.
5. Docs: the temperature functional decisions plan, and the temperature entries of `docs/maro-code.md`.
6. Tests, then `assembleDebug`.

**Files:** `ui/map/RegulatedZoneComponents.kt`, `ui/map/MapScreen.kt`, `ui/map/TemperatureViewModel.kt`, a new `ui/map/TemperatureSourcesLine.kt`, `data/temperature/AverageSeaFetcher.kt`, `data/temperature/TemperatureEngine.kt`, `data/temperature/TemperatureCache.kt`, `res/values/strings.xml`, `res/values-fr/strings.xml`, and their tests.

## 9. Verification

- Unit: a marker in the band with no containing zone, and with no zone set, yields the band tag; source values survive a cache round trip and an old file still loads; the source field follows the shown water value, held-over one included; a missing source formats as the dash in both decimal marks.
- Device: the hold toggles and the Settings switch follows; nothing happens when locked; the 300 m tag shows in the band away from regulated zones; line wrapping in portrait and landscape; panning near the tag column.

## 10. Review (2026-11-28)

Self-review by the authoring session, claims re-read against the code.

- **R1, corrected:** the line was said to sit level with the tile; it does only with no zone tag (§4).
- **R2, added:** the water line collects its own state (§4).
- **R3, added:** the Settings description would no longer cover what the switch controls.
- **R4, added:** a failed source's dash lasts as long as the value.
- **R5, added:** a bare clock time would mislead for an earlier day.
- **R6, confirmed:** the lock claim, and the §5 fix staying on water.

## Implemented

Shipped 2026-11-28: §§3-5 as designed. `assembleDebug` and 1 907 unit tests green, 27 of them skipped. The §9 device items have not been run.

Deviations:

- The water-source field carries an earlier-day mark instead of an outdated flag: the water reading already says outdated, and with the day owned by the engine the line changes at midnight.
- The info panel composes with no zone line, its head line drawing nothing when there is nothing to say.
- From the Ask review: a tap on the tag stack closes an open fan; the stack is one node to a screen reader, so the hold is offered on it; the cache signature takes its source names from `SeaSource`; the Settings description is reworded again, "water source" reading as a spring.

Open from the review:

- Outdated water-source text is lighter than normal text on the pale card, so harder to read. The colour is the user's choice.
- A slow drag that stays inside the tag stack counts as a hold, the platform's own long-press behaviour. Add it to the device pass.

Follow-up, same day, replacing §4 where they differ:

- The line reads `🌊 Marine 17.4° · ECMWF 17.1° · Copernicus 16.8°`: no "Water" word, no time or date, and a source with no value left out rather than dashed. The engine publishes the source values alone.
- A hold on the temperature tile raises no pill when the refresh goes well: the "Refreshing" line and the values line are gone, the failure and no-connection lines stay.
- The line follows the tile's view: while the tile shows air, on land or tapped on water, it reads `🏔️ AROME 17.3°` in place of the sea sources.
- `assembleDebug` and 1 902 unit tests green, 27 of them skipped.
