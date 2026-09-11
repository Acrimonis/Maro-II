# Ui_Settings — Hydration (2026-09-11)

## Session — tab-finalization phase 1 (IMPLEMENTED)

Branch `feature/settings-menu-clean`. Plan: [`260911_FEAT_PLN_Ui_Settings_tab-finalization.md`](260911_FEAT_PLN_Ui_Settings_tab-finalization.md).

Delivered:

- **P1.1** — `SectionHeader(title)` with no `uppercase` parameter, sentence case, `letterSpacing = 0.sp`; the 9 `uppercase = false` arguments dropped; `ui.font.section.size` 17→18sp plus the `AppConfig.uiFontSectionSize` fallback 17f→18f.
- **P1.2** — `SettingsToggleRow` retired → box-less `ToggleRowContent` (label 16sp Medium + description 13sp muted + 16dp spacer + accent `Switch`; row padding 16dp horizontal × 2dp vertical). Coastline = `Card { ToggleRowContent(…) }`; Orientation aids = one `Card`, 3 rows, 2 `SectionDivider`s.
- **P1.3** — `docs/ui-component-guidelines.md` §1, §2.1, §2.3, §2.4, §2.5, §2.6, §2.9, §4 rewritten to the single "row + Card + functionally-defined sections" model; `ui.properties` comments refreshed.
- **P1.4** — settings tab strip → M3 `SecondaryScrollableTabRow` (`divider = {}`, `containerColor = uiSettingsBackground`, `edgePadding = 24.dp`, needs `@OptIn(ExperimentalMaterial3Api::class)` on `SettingsOverlay`) with **custom content-sized cells** (`Box` + `selectable(role = Role.Tab)`, 8dp horizontal / 14dp vertical) and M3's full-cell secondary indicator; label token `ui.font.tab.size=18sp`, SemiBold (Bold when selected) + `AppConfig.uiFontTabSize` (18f). Horizontal scrolling is a safety net for narrow screens and large font scale. Validated on device.

Verification: `apk-build.bat` SUCCESS (1m 05s), no new warnings. Ask review PASS. Committed as `0b01d1e` (phase 1) + `f579409` (spacing).

- **Spacing pass (same day)** — all four tabs now share one section boundary (`ui.spacing.section.gap`, 24 → 14dp) and one title→card gap (`ui.spacing.header.bottom`, 8 → 6dp); Layers' five 12dp / raw `12.dp` gaps were the outliers. §2.11 documents the tab strip. Validated on device.

## In progress — row family normalization (R1–R7 done, R8 pending)

Branch `feature/settings-menu-clean`. Plan: [`260911_FEAT_PLN_Ui_Settings_row-naming-normalization.md`](260911_FEAT_PLN_Ui_Settings_row-naming-normalization.md).

- **R1 (done — committed `f8b380e`)** — `ToggleRowContent`→`ToggleRow`, `SliderRowContent`→`SliderRow`, `SettingsLanguageRow`→`SegmentedRow`, `ColorSwatchRow`→`ColorRow`, `ColorSwatchPairRow`→`ColorPairRow`, definitions and all call sites; no old symbol remains.
- **R2 (done — committed `f8b380e`)** — new `RangeSliderRow` (label/description optional, mandatory right-aligned value line, two-thumb slider, no surface of its own) now backs all **8** inline `RangeSlider` sites; the value line is tokenised (`uiFontRangeSize`, was a hardcoded `14.sp` twice) and descriptions normalised to `uiFontDescSize` (13sp, was 12sp three times). Deviation: the four sites that already sit under a `SubSectionHeader` pass no label, rather than having their heading replaced.
- **R3 (done — uncommitted)** — `SegmentedRow` generalised to `<T>` (`options`/`selected`/`onSelect` + optional `captions`) and made **surface-free**: connected segments with outer-only rounding, unselected segments outlined, `selectableGroup()` + `Role.RadioButton`. Adopted at Arrow density; the Language picker was wrapped in a `Card`, so no control on the page paints its own surface any more.
- **R4 (done — uncommitted)** — GPS frequency is a 3-option `SegmentedRow` (Élevée 1 s/1 m · Équilibrée 2 s/5 m · Éco 4 s/10 m) with per-stop captions; `SettingsFrequencyRow` deleted — no symbol remains. Intentional behaviour change: a free 1–4 s range became three presets.
- **R5 (done — uncommitted)** — `CardDescription` extracted (owns its trailing spacer, **keeps today's 16dp inset until R7**) and all six inline descriptions migrated: Tracks, Markers, Regulated zones, 300 m band, Danger zones, Depth.
- **R6 (done — uncommitted at bake time)** — `ToggleRow` gained `leadingIcon: @Composable (() -> Unit)? = null` (additive, 8dp gap). **13** hand-rolled inline label+`Switch` rows converted to `ToggleRow`: speed-zone/regulated-zone auto-show, map offset GPS/demo, GPS mode (keeps `onGpsModeChange` + `onDismiss`), stop detection enable + GPS delay, keep screen on, debug rays, and the four Regenerate-layers rows. Seven more sites (coastline, heading line, cap arrow, demo heading up, both re-display enables, 300 m band auto-show) were **already** `ToggleRow` — nothing to do. **1 row deliberately skipped**: `regulationInfoVisible` is genuinely bespoke (raw `14.sp` label, no `uiPaddingToggleVertical`, no `.weight(1f)` column, partial switch colours) and waits for R7 like the category group.
  **Divider finding:** the pattern quoted in the plan (`height(1dp)` + literal `16dp`) does not exist. The real one is `Spacer(uiDividerGap 6dp)` + `Box(fillMaxWidth → padding(horizontal = uiPaddingCardHorizontal) → height(uiDividerHeight) → background(uiSettingsDivider))` + `Spacer(6dp)` — byte-identical to `SectionDivider()`. Only **3** matched 1:1 (the Regenerate-layers dividers) and were converted; **4** were left for R7 (three sit in a `NestedCard` that already supplies the 16dp — a swap would double the inset — and the Re-display one is bracketed by `uiSpacingGroupedRowGap` 8dp, not 6dp).
- **R7 (done — uncommitted at bake time)** — the only *layout* change. `Card` now owns `horizontal = uiPaddingCardHorizontal` (matching `NestedCard`), so **14** inset-only wrappers collapsed (12 `Box` + the 2 `Column` around the boat-offset and FPS sliders) and the row definitions (`CardDescription`, `ToggleRow`) plus `SectionDivider()` dropped their own horizontal padding — this is what gives the **Language picker** its 16dp inset (the symptom reported on device). Grep invariant met literally: `AppConfig.uiPaddingCardHorizontal` occurs **exactly twice** in the overlay (`Card` + `NestedCard`); no `padding(horizontal = 16.dp)` and no `RegulatedZoneCategoryToggles` remain. `MapScreenSettingsOverlay.kt` **2415 → 2014** lines overall across R6+R7; the R7 diff is line-noisy by nature (each collapsed wrapper re-indents its body).
  **Dividers:** Tracks ×2 + regulated-zones info → `SectionDivider()`; the Re-display one stays inline (its neighbours use `uiSpacingGroupedRowGap` 8dp, not `uiDividerGap` 6dp) with an explanatory comment — flagged, not silent. The regulated-zones info divider's gaps did move 4dp → 6dp.
  **Category group ([`RegulatedZoneComponents.kt`](RegulatedZoneComponents.kt)):** `RegulatedZoneCategoryToggles` → `CategoryToggleGroup`, `fun` → `internal`, each row rebuilt on `ToggleRow` with the `leadingIcon` slot carrying the 28dp box / emoji / red "10" / strike overlay; labels 14sp → 16sp, switches adopt `ToggleRow` colours; still renders inside the parent `NestedCard` with no self-drawn container. The bespoke `regulationInfoVisible` row also moved to `ToggleRow`. Unused `Switch`/`SwitchDefaults`/`width` imports removed.
  **Deviation:** `ToggleRow` visibility `private` → `internal`, required because the group lives in the sibling file while `ToggleRow` stays in the overlay.
- Builds after each step: SUCCESS, no new warnings. **R8 pending** (guidelines §1/§2.0/§2.1/§2.7/§2.8/§2.9/§4, tracking, `#doctor` + stale-symbol sweep).
- **Backlog (agreed, not implemented)** — sub-section titles inside collapsible zones are `ui.settings.text.muted` (`#B0BEC5`) at 16sp SemiBold while expander labels and row labels are `ui.settings.text.primary` (`#FFFFFF`) at 16sp, so grey headings read as accidental and de-emphasis is inverted; `muted` also serves 13sp explanatory text. Option A chosen: promote `SubSectionHeader` + `SingleColorSubSection` titles to `primary` (2 composables; the 7 call sites inherit), update §2.9 and the `ui.settings.text.muted` comment. Plan file `260911_FEAT_PLN_Ui_Settings_section-title-color.md` captures the discussion.

## Open — phase-1 follow-ups

Logged in [`FEAT_DSC_Ui_Settings.md`](FEAT_DSC_Ui_Settings.md) under `### Tab finalization follow-ups`: **A** description optional on the toggle row (Auto-show zones rows are label-only) and **B** row-padding convention are closed by R6 and R7 respectively; **C** align §2.2 with §2.1/§2.3 remains open. **D** is resolved — custom tab cells inherit no M3 text style, so the `titleSmall` 0.1sp tracking no longer applies.

## Prior — C12 OverlayLayer param collapse (complete, awaiting push + PR)

`feature/refact-C12` holds 3 commits (`ec57458`, `a000c18`, `96259b5`), ahead of `origin/develop`; on-device functional test PASSED (2026-09-10); the ~30 invalidated doc anchors were repaired. Remaining: push the branch and open the PR into `develop` — the branch tracks `origin/develop`, so use an explicit refspec rather than a bare `git push`.

## Key Files

- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — settings overlay, 4 tabs, `SectionHeader`, `ToggleRow`, `SliderRow`, `RangeSliderRow`, `SegmentedRow`, `CardDescription`, `Card`, `SectionDivider`; `SettingsToggleRow` and `SettingsFrequencyRow` no longer exist
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — UI token accessors (`uiFontSectionSize` 18f, `uiFontTabSize` 18f)
- `app/src/main/assets/ui.properties` — token values
- `docs/ui-component-guidelines.md` — canonical UI rules
- `xTrack/Ui_Settings/260911_FEAT_PLN_Ui_Settings_tab-finalization.md` — phase-1 plan of record
- `xTrack/Ui_Settings/260911_FEAT_PLN_Ui_Settings_row-naming-normalization.md` — row-family normalization plan of record (R1–R5 done)
