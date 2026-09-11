# Ui_Settings — Row family normalization (rename → extract → convert → normalize → document)

**Status:** planned, decision-complete (not implemented) · **Date:** 2026-09-11 · **Branch:** `feature/settings-menu-clean`
**Scope:** the control rows of the Settings overlay ([`MapScreenSettingsOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1)) plus the one row group that lives outside it ([`RegulatedZoneComponents.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt:1)). Follow-on to phase 1 (tab finalization + spacing rhythm). Not the `Ui_Menu` menu-normalization phase.
**Line references** are as of 2026-09-11 and drift — re-locate by symbol.

## Problem

| Composable | Renders | Naming style |
|---|---|---|
| `ToggleRowContent` | label + optional description + switch | `…RowContent`, no prefix |
| `SliderRowContent` | label + description + value + one-knob slider | `…RowContent`, no prefix |
| `SettingsFrequencyRow` | 3-stop discrete slider with captioned stops (GPS frequency) | `Settings…` + named after the **use case** |
| `SettingsLanguageRow` | 3-way segmented selector, and the only control painting its own surface | `Settings…Row` |
| `ColorSwatchRow` | label + one swatch | `…Row` named after the **widget** |
| `ColorSwatchPairRow` | label + from→to swatches | `…Row` named after the **widget** |
| `SingleColorSubSection` | one-colour group | `…SubSection` (a sub-section, not a row) |
| *(unnamed)* | card description, 13sp muted (§2.9) | — |
| `RegulatedZoneCategoryToggles` | a **group** of icon + label + switch rows ([`RegulatedZoneComponents.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt:310)), bespoke: 14sp labels, partial switch colours, `public` | named after the feature |
| ***(no composable)*** | **~13 hand-rolled toggle rows** and **~4 hand-rolled dividers** written inline inside cards | — |

Defects: three naming styles; the two-thumb `RangeSlider` has **no row** (8 inline copies); the segmented selector is half-factored and paints its own surface; the largest family — inline rows — has no composable at all, which phase 1's §4 anti-pattern already forbids; one toggle group is bespoke and `public`; **padding ownership is split** (`Card` pads vertically only, `NestedCard` pads both axes) with **35** `uiPaddingCardHorizontal` usages compensating; `SettingsFrequencyRow` is the only row bypassing the font tokens (`16/13/12.sp` + raw `4.dp`).

## Decisions (all closed)

| # | Decision |
|---|---|
| **A** | **One** `SegmentedRow` serves both segmented controls (language picker, arrow density) — already visually identical, so no visual parameters. M3 single-choice shape: connected (no gaps, outer ends only rounded), unselected segment **outlined** (1dp `0x40FFFFFF`) instead of `uiSettingsDivider`-filled, `selectableGroup()` + `selectable(role = Role.RadioButton)` per segment; accent fill kept; stock `SingleChoiceSegmentedButtonRow` not adopted. **Plus:** the row becomes **surface-free** and the Language section's call site gains a `Card` — today the picker is the only control painting its own surface. |
| **B** | Slider rows **always** show a value. `RangeSliderRow` renders it on its **own line** (right-aligned, bold, `uiSettingsValueText` / `uiFontRangeSize`); `SliderRow` keeps label-left / value-right on one line. |
| **C** | `CardDescription` is created **and** the six existing inline descriptions migrate onto it. Guideline states when to use it versus `SubSectionHeader(title, description = …)`. |
| **D** | GPS frequency becomes a 3-option `SegmentedRow` with a caption row; `SteppedSliderRow` is dropped entirely. **Deliberate behaviour change:** a free 1–4 s range becomes three presets. |
| **E** | The regulated-zone **category group** migrates onto `ToggleRow`, which gains an optional `leadingIcon`. Sizes unify (labels 14sp → 16sp, switch colours per `ToggleRow`). It keeps a **group** name — `CategoryToggleGroup`, not `…Row` — stays in [`RegulatedZoneComponents.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt:1), and its visibility narrows `fun` → `internal`. |
| **F** | **Branch:** keep `feature/settings-menu-clean`. The row work renames symbols phase 1 created, so it is stacked on phase 1's commit by construction; one PR covers both. |
| **G** | **Commits:** three — mechanical / padding normalization / docs. |

Naming rule: `<Control>Row` for a row, `<Thing>Group` for a group of rows, no `Settings` prefix, named after the **control**, never the widget or the use case. Unchanged: `SingleColorSubSection`, widgets (`ColorSwatchButton`, `ColorPickerDialog`), containers (`Card`, `NestedCard`, `Expander`, `SectionDivider`, `SectionHeader`, `SubSectionHeader`).

## Execution order

| # | Step | Risk |
|---|---|---|
| R1 | Rename only — **except** `SettingsFrequencyRow` | none (compiler-verified) |
| R2 | Extract `RangeSliderRow` (8 sites) | none |
| R3 | Extract `SegmentedRow` (incl. `captions`), make it surface-free, adopt at Arrow density, wrap the Language section in a `Card` | **small visible** — unselected segment outlined; the language block gains the card's 8dp vertical padding (its 16dp side inset arrives in R7) |
| R4 | GPS frequency → `SegmentedRow` (3 options + captions); delete `SettingsFrequencyRow` | **small visible** — the control changes |
| R5 | `CardDescription` (create + migrate, **keeps today's 16dp inset**) | none |
| R6 | Convert the remaining inline rows/dividers; add `ToggleRow(leadingIcon)` | none (the icon slot is additive) |
| R7 | **Padding normalization** (container-owned inset) + migrate the category group onto `ToggleRow` | **small visible** — category labels 14sp → 16sp, switches unified; plus the only *layout* change |
| R8 | Docs + tracking | none |

**Order rules.** (a) Renames first, so later diffs read against final names. (b) Until R7, every row follows today's convention — **no horizontal self-padding changes**; callers keep their `Column(padding)` wrappers and `Card` keeps zero horizontal padding. (c) Each step gets its own build. (d) R6 must precede R7 — only once every inline row is a shared row can R7's invariant be reached.

**Device passes:** after **R3** (language picker + arrow density), **R4** (GPS picker + captions), **R7** (divider-bearing cards **and** the category switches in the Regulated-zones expander).

## R1 — Rename only

| From | To |
|---|---|
| `ToggleRowContent` | `ToggleRow` |
| `SliderRowContent` | `SliderRow` |
| `SettingsLanguageRow` | `SegmentedRow` |
| `ColorSwatchRow` | `ColorRow` |
| `ColorSwatchPairRow` | `ColorPairRow` |

`SettingsFrequencyRow` and `RegulatedZoneCategoryToggles` are **not** renamed here — the first is deleted by R4, the second renamed by R6.

**Ripple to enumerate, not assume:** call sites of `SettingsLanguageRow`, `ColorSwatchRow`, `ColorSwatchPairRow`; every doc naming the old symbols — [`FEAT_DSC_Ui_Settings.md`](FEAT_DSC_Ui_Settings.md:16), [`FEAT_HYD_Ui_Settings.md`](FEAT_HYD_Ui_Settings.md:11), [`260911_FEAT_PLN_Ui_Settings_tab-finalization.md`](260911_FEAT_PLN_Ui_Settings_tab-finalization.md:1), guidelines §2.1/§2.2/§2.4/§2.7/§2.8/§4.
Acceptance: build clean; no `…RowContent`, `Settings…Row` or `ColorSwatch*Row` symbol remains — sole exception `SettingsFrequencyRow`, removed by R4.

## R2 — `RangeSliderRow` (new) — values always shown

Mirrors `SliderRow`, takes a value **range**, exposes `onValueChangeFinished` (several sites commit on release) plus optional local drag state. The value is mandatory, on its own line (decision B). Normalize while extracting: the 8 sites use `uiFontCommentSize` (12sp) for their description while `SliderRow` uses `uiFontDescSize` (13sp) — converge on 13sp.

| # | Site | Ref |
|---|---|---|
| 1 | Tracks → transparency (newest) | ~L429 |
| 2 | Tracks → transparency (pinned) | ~L480 |
| 3 | Tracks → direction gap (log-scale) | ~L601 |
| 4 | Tracks → direction speed floor (log-scale) | ~L634 |
| 5 | Markers → halo pinned | ~L732 |
| 6 | Markers → halo unpinned | ~L771 |
| 7 | 300 m band → transparency | ~L975 |
| 8 | Danger zones → two-depth warning | ~L1074 |

Acceptance: no bare `RangeSlider(` remains; each site keeps its mapping/drag/commit behaviour; the value line is present at all 8.

## R3 — `SegmentedRow` (new) — one look, M3-aligned, surface-free

Extract `SettingsLanguageRow` into `SegmentedRow(options, selected, onSelect, captions: List<String>? = null)`; `captions` is in this signature because R4 needs it — the language picker omits it. Adopt it at the **Arrow density** selector ([L554-584](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:554), Layers → Tracks → Speed and Direction). The two sites are visually identical today (6dp container padding, 6dp gaps, 8dp inner radius, 10dp vertical, 14sp Bold-on-selected) and differ only in option count (2 vs 3), label source (the language picker hardcodes the endonym "Français") and container (`Card` vs `NestedCard`).

Shape normalization (decision A): connected control, no gaps, outer-only rounding, unselected outlined, `selectableGroup()` + radio role, accent fill kept.

**Surface-free + Language section gets a card (decision A).** The language picker's call site ([L1642-1651](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1642)) renders the control directly in the tab column — no `Card` — and the control paints its own `uiCardBackground` + 12dp surface, which is why it *looks* like a card. R3 removes that surface (no `clip`, no `background`, no outer 6dp padding: it fills its container's content width) and wraps the Language section in `Card { SegmentedRow(…) }`. The Arrow-density site needs nothing — it already sits in a `NestedCard`. This makes both deployment modes identical and leaves **no control on the page painting its own surface**.

Acceptance: one implementation serving both sites; surface-free; Language section wrapped in a `Card`; a11y announces a single-choice group; no gap; unselected outlined. At R3 the card still has no horizontal padding, so the picker remains edge-to-edge inside it (exactly as wide as today); the 16dp side inset arrives with R7, like every other section.

## R4 — GPS frequency becomes a 3-option `SegmentedRow` (decision D)

**Position → GPS tuning → "GPS frequency"** stops being a slider and becomes the same picker as the language row: Élevée | Équilibrée | Éco, one accent-filled segment.

1. **Options carry their payload** — the existing `stops` list already holds `(label, intervalSec, minDistanceM)`, so a selection writes both `gpsActiveIntervalSec` and `gpsActiveMinDistanceM`.
2. **Keep the numbers** — per-stop captions ("2 s · 5 m") via `SegmentedRow(captions = …)`; without them the labels mean nothing. The recommended default stays the only **bold** option.
3. **Removed:** the discrete `Slider`, its hardcoded `16/13/12.sp` and raw `4.dp`, and the `SettingsFrequencyRow` composable. This is the one **deliberate behaviour change** in the plan (a free 1–4 s range becomes three presets).

Acceptance: language picker and GPS frequency are the same control; per-stop numbers visible; no hardcoded fonts.

## R5 — `CardDescription` (new)

The 13sp muted card description (§2.9) gains a name and owns its trailing spacer. **Migrate** the six existing inline descriptions ([L349](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:349), [L670](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:670), [L870](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:870), [L942](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:942), [L1033](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1033), [L1138](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1138)) onto it. **It keeps today's 16dp horizontal inset** — R7 removes it; dropping it here would push the descriptions to the card edge mid-sequence. Guideline states when to use it versus `SubSectionHeader(title, description = …)` (the 300 m band pattern): `CardDescription` = an explanation under a `SectionHeader`; `SubSectionHeader` = a titled sub-section inside a card. Both are kept.

## R6 — Convert the remaining inline rows/dividers + the category group

1. **Add the icon slot.** `ToggleRow` gains `leadingIcon: @Composable (() -> Unit)? = null`, rendered before the label column with the standard 8dp gap (documented in §2.1).
2. **The ~13 inline toggle rows** — written out longhand inside cards, duplicating `ToggleRow` exactly (same fonts, same 16/2dp padding, same 16dp label→control spacer, same `Switch`): e.g. [GPS mode ~L1336](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1336), [stop detection ~L1520](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1520), plus keep-screen-on / power-saving / regenerate-layers / window. Replace every one with `ToggleRow(...)` (description optional).
3. **The ~4 hand-rolled dividers** (`fillMaxWidth().padding(horizontal = 16dp).height(1dp).background(uiSettingsDivider)`, e.g. [~L1290](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1290), [~L1789](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1789)) → `SectionDivider()`.

Both conversions are 1:1 on inset: the inline rows already self-pad 16dp like `ToggleRow`, and the hand-rolled divider insets 16dp like `SectionDivider()`.

**Deliberately not here:** the category group's migration (decision E) waits for **R7**. Its rows sit inside a `NestedCard` — which already supplies 16dp — and carry no padding of their own, so rebuilding them as today's `ToggleRow` (self-padding 16dp) would give **32dp**, which R7 would then have to undo. After R7 the inset belongs to the container and the conversion is 1:1.

Acceptance: no inline label+`Switch` row and no hand-rolled divider remains; visible result identical. This is what makes R7 reachable.

## R7 — Padding normalization (the only layout change)

`Card` gains `horizontal = uiPaddingCardHorizontal` (matching `NestedCard`). **Every child stops insetting itself**, removing all 35 usages except the two containers:

- 6 card descriptions → `CardDescription` drops the inset it kept in R5.
- 10 `Box(Modifier.padding(horizontal = …))` wrappers around `Expander`s collapse; their compensating trailing `Spacer`s go too.
- the row definitions (`ToggleRow`, `SliderRow`, `RangeSliderRow`, `SegmentedRow`, `ColorRow`, `ColorPairRow`, `SingleColorSubSection`) keep **vertical** padding only.
- `SectionDivider()` drops its own inset (the double-inset defect); likewise the two `Column(Modifier.padding(horizontal = …))` wrappers around the boat-offset and FPS sliders.
- **The category group migrates here** (decision E): rename `RegulatedZoneCategoryToggles` → `CategoryToggleGroup`, keep it in [`RegulatedZoneComponents.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt:310), narrow `fun` → `internal`, and rebuild each row as `ToggleRow(label = item.label, leadingIcon = { …icon/emoji/strike… }, checked = …, onCheckedChange = …)`. The icon slot carries today's 28dp box, emoji, red "10" box and diagonal strike overlay; **labels go 14sp → 16sp** and switches adopt `ToggleRow`'s colours. Its "no self-drawn container" comment stays true — it still renders inside the parent's `NestedCard`, which now owns the inset for it like for every other child.

**Acceptance (grep invariant):** after R6 + R7, `AppConfig.uiPaddingCardHorizontal` appears in exactly **two** places in the overlay — inside `Card` and inside `NestedCard`. Any intentional exception carries a comment. (Reachable only because R6 converted the inline rows first.)
**Scope note:** `Card`/`NestedCard` are file-private — R7 cannot ripple into the drawer/menu.
**Rejected alternative:** rows self-pad and `NestedCard` stops padding horizontally — needs padding added to every non-row child inside the ~10 `NestedCard`s; strictly more churn.
**Device pass:** the divider-bearing cards (Orientation aids, Auto-show zones, Tracks, Markers, Regulated zones, 300 m band, Depth, Danger zones).

## R8 — Docs + tracking

- Guidelines: naming rule (`<Control>Row` / `<Thing>Group`) + container-owned inset (§1/§2.0); `ToggleRow`'s `leadingIcon` (§2.1); `SegmentedRow` incl. the caption variant and surface-free rule (§2.7); `RangeSliderRow` (§2.8); `CardDescription` (§2.9); §4 anti-patterns (hand-rolled `RangeSlider` block; hand-rolled rows/dividers; any row that pads itself horizontally or paints its own surface).
- Tracking: `FEAT_DSC_Ui_Settings.md` `## Implemented`, `FEAT_HYD_Ui_Settings.md`, plan pointers; `#doctor` + symbol sweep for stale names (`SettingsToggleRow`, `SettingsFrequencyRow`, `…RowContent`, `RegulatedZoneCategoryToggles`).

## Commit plan (3)

| Commit | Content | Why |
|---|---|---|
| 1 — row family unified | R1 → R6. Mechanical; the visible parts are the agreed deltas of R3 and R4. | One theme; every step inside is compiler-verified. |
| 2 — padding normalization + category group | R7, including decision E's group migration. | The only *layout* change; it must be readable on its own. |
| 3 — docs + tracking | R8. | Must be last, or the docs name symbols that do not exist yet. |

Each step still gets its own **build** — that is what catches mistakes; commits and builds are independent. The branch is unpushed, so the three can be squashed at PR time.

## Verification

- `apk-build.bat` SUCCESS with no new warnings at **every** step.
- Per-site acceptance for the 8 `RangeSlider` swaps, for R6's conversions, and for R7's padding sweep (grep invariant).
- Device passes: **R3** (language picker + arrow density), **R4** (GPS picker, captions, bold default), **R7** (divider-bearing cards **and** the category switches in the Regulated-zones expander).

## Out of scope

Other features' surfaces (drawer cards, list-item cards, dashboard tiles, popups) and the `Ui_Menu` phase. No control gains or loses a *setting*, and no spacing token changes (phase-1 rhythm stands) — the intended behaviour change is R4/D (GPS frequency: continuous range → three presets) and the intended visible deltas are those listed per step.
