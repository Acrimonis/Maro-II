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

## In progress — row family normalization (R1–R5 done, R6–R8 pending)

Branch `feature/settings-menu-clean`. Plan: [`260911_FEAT_PLN_Ui_Settings_row-naming-normalization.md`](260911_FEAT_PLN_Ui_Settings_row-naming-normalization.md).

- **R1 (done — committed `f8b380e`)** — `ToggleRowContent`→`ToggleRow`, `SliderRowContent`→`SliderRow`, `SettingsLanguageRow`→`SegmentedRow`, `ColorSwatchRow`→`ColorRow`, `ColorSwatchPairRow`→`ColorPairRow`, definitions and all call sites; no old symbol remains.
- **R2 (done — committed `f8b380e`)** — new `RangeSliderRow` (label/description optional, mandatory right-aligned value line, two-thumb slider, no surface of its own) now backs all **8** inline `RangeSlider` sites; the value line is tokenised (`uiFontRangeSize`, was a hardcoded `14.sp` twice) and descriptions normalised to `uiFontDescSize` (13sp, was 12sp three times). Deviation: the four sites that already sit under a `SubSectionHeader` pass no label, rather than having their heading replaced.
- **R3 (done — uncommitted)** — `SegmentedRow` generalised to `<T>` (`options`/`selected`/`onSelect` + optional `captions`) and made **surface-free**: connected segments with outer-only rounding, unselected segments outlined, `selectableGroup()` + `Role.RadioButton`. Adopted at Arrow density; the Language picker was wrapped in a `Card`, so no control on the page paints its own surface any more.
- **R4 (done — uncommitted)** — GPS frequency is a 3-option `SegmentedRow` (Élevée 1 s/1 m · Équilibrée 2 s/5 m · Éco 4 s/10 m) with per-stop captions; `SettingsFrequencyRow` deleted — no symbol remains. Intentional behaviour change: a free 1–4 s range became three presets.
- **R5 (done — uncommitted)** — `CardDescription` extracted (owns its trailing spacer, **keeps today's 16dp inset until R7**) and all six inline descriptions migrated: Tracks, Markers, Regulated zones, 300 m band, Danger zones, Depth.
- Builds after each step: SUCCESS; the working tree is UP-TO-DATE against the last compile. **R6–R8 pending** (inline rows + dividers + `leadingIcon`; padding normalization + `CategoryToggleGroup`; docs + tracking).

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
