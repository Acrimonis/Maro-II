# FEAT_PLN Ui_General — settings slider rows onto the shared component

**Feature:** Ui_General · **Date:** 2026-10-17 · **Branch:** `feature/sp-ui` · **Status:** in design — ordered in Architect mode after a layout review; implementation under way
**Owner surface:** `ui/map/MapScreenSettingsOverlay.kt` and `ui/map/RegulatedZoneComponents.kt`; the shared control is [`SliderRow`](../../app/src/main/java/ykws/android/maro/ui/components/SliderRow.kt:36) (component guidelines §2.2).

## 1. The four sites

| # | Site | Divergence today | Visual consequence of normalising |
|---|------|------------------|------------------------------------|
| S1 | Tracks count — `MapScreenSettingsOverlay.kt:327` through its `Slider(` at 351 | A bare `Text` label, a hand-rolled `Row(Alignment.CenterVertically)` holding the description at `weight(1f)` and the value beside it, then a bare `Slider` carrying its own `SliderDefaults.colors` | One `SliderRow` call: label and description share a column and the value bottoms out with that block instead of floating beside a one-line description; the track and knob keep the same accent/inactive pair, now read from `SliderControl` |
| S2 | Routes count — `MapScreenSettingsOverlay.kt:475` through its `Slider(` at 499 | The same hand-rolled block, duplicated | The same consequence, site by site; the `SectionDivider()` calls and every neighbouring control stay where they stand |
| S3 | Boat size — [`BoatSizeSlider`](../../app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt:353), called at `MapScreenSettingsOverlay.kt:975` | A `Row` title/value pair with the value at **14 sp `uiAccent`** and the description-less layout, then a bare `Slider` whose inactive track is `uiAccent` at 30 % alpha | The row family's value face — bold `ui.value.text` at `ui.font.value.size` — bottom-aligned with the label, and the shared inactive track colour (`uiSwitchTrackInactive`); this is the user-accepted visible change, the rule being one home per row |
| S4 | Value suffixes — `"%d%%".format(…)` at lines 727 and 741, `"${…}%"` at 1586, and the boat size's `"${…} m"` | The unit is formatted in Kotlin, so no locale owns it | No visual change: the same digits, `%` and `m`; only their source moves into `settings_value_pct_fmt` / `settings_value_m_fmt` beside `settings_value_dp_fmt`, read through `stringResource` |

## 2. Change set

1. **S1** — replace the block with one `SliderRow`: `settings_tracks_count_label`, `settings_tracks_count_desc`, `countValueLabel(settings.trackingRenderNb)`, `settings.trackingRenderNb.toFloat()`, `0f..AppConfig.PATH_COUNT_ALL.toFloat()`, `steps = AppConfig.PATH_COUNT_MAX`, and the unchanged clamp through `roundToInt().coerceIn(0, AppConfig.PATH_COUNT_ALL)`.
2. **S2** — the same replacement naming `settings_routes_count_label`, `settings_routes_count_desc`, `countValueLabel(settings.routeRenderNb)` and `settings.routeRenderNb`.
3. **S3** — `BoatSizeSlider` keeps its name, its parameters and its call site; its body becomes one `SliderRow` on `settings_boat_length_label`, `valueRange = 3f..25f`, `steps = 21`, the same `boatSizeM` write and clamp.
4. **S4** — two keys added beside `settings_value_dp_fmt` in both locale files (`settings_value_pct_fmt` = `%1$d%%` / `%1$d %%`, `settings_value_m_fmt` = `%1$d m` / `%1$d m`), read at the four literals named above; the boat size's `m` becomes its `valueLabel`.
5. **Corpus** — `docs/ui-component-guidelines.md` gains the missing §4 anti-pattern bullet for a hand-rolled one-knob slider, and §2.2 states that a value suffix is read from a `*_fmt` key rather than formatted in code.

## 3. Non-goals

- [`RouteConfirmPanel.ColumnDivider`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt) — untouched.
- The inset hairline at [`TrackHistoryOverlay.kt:821`](../../app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:821) — untouched.
- The card sub-action `DropdownMenu` at [`ListOverlayScaffold.kt:917`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:917) — untouched.
- `RangeSliderRow` — untouched; two-thumb rows keep their own control.
- `ScaledSliderRow`'s `"${shown.toLong()} $unit"` template — untouched.
- No dependency, no commit, no push, no device action.

## 4. Verification

`.\gradlew.bat :app:compileDebugKotlin`, result recorded below.

## Implemented

- **2026-10-17, `feature/sp-ui`** — S1 and S2 now call [`SliderRow`](../../app/src/main/java/ykws/android/maro/ui/components/SliderRow.kt:36) with the count labels, descriptions and values unchanged, the clamp untouched; S3's `BoatSizeSlider` keeps its name and call site and renders as one `SliderRow` (its value now the row family's), S4 moved the `%` and `m` suffixes into `settings_value_pct_fmt` / `settings_value_m_fmt` in both locales, and §2.2 + §4 of the component guidelines record the rule. `.\gradlew.bat :app:compileDebugKotlin` → **BUILD SUCCESSFUL** (29 s; the warnings shown are pre-existing in untouched files).
