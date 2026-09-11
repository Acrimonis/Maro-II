# Ui_Settings — Phase 1: Settings Tab Finalization

**Status:** planned (not implemented) · **Branch:** `feature/settings-menu-clean` · **Date:** 2026-09-11
**Scope:** the four settings tabs rendered by [`SettingsOverlay()`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:211). Phase 2 (menu drawer normalization) is a separate `Ui_Menu` plan.

## Problem

Section titles are cased two ways — Layers/Navigation title case, Position/System ALL-CAPS, and Navigation mixed inside itself. Separately, a lone toggle renders as its own card while a grouped toggle is a bare row: two treatments for one control.

## P1.1 — Section titles: one casing, +1sp

Files: `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt`, `app/src/main/java/ykws/android/maro/config/AppConfig.kt`, `app/src/main/assets/ui.properties`, `docs/ui-component-guidelines.md`

1. `SectionHeader` ([L2019-2030](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:2019)): remove the `uppercase` parameter; always title case, `letterSpacing = 0.sp`.
2. Drop the now-redundant `uppercase = false` arguments: L348, L669, L869, L941, L1018, L1030, L1135, L1182, L1207.
3. ALL-CAPS sites become title case via the default change: L1412, L1521, L1596, L1723, L1734, L1831.
4. `ui.properties`: `ui.font.section.size` 17sp → **18sp** (L63); fix the "uppercase tab titles" comment (L62). Also bump the code fallback `AppConfig.uiFontSectionSize` `17f` → `18f` ([AppConfig.kt:513](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:513)) so a missing/partial properties file cannot silently override the new size.
5. Acceptance: all 15 headers render identically; the `uppercase` parameter is gone from the signature and no call site passes a casing argument; no `.uppercase()` call and no conditional `letterSpacing` branch remains.

## P1.2 — Retire `SettingsToggleRow` → box-less `ToggleRowContent`

File: `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt`

1. Delete `SettingsToggleRow` ([L2078-2119](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:2078)) and add `ToggleRowContent(label, description, checked, onCheckedChange)` = its inner row only: label 16sp Medium `uiSettingsTextPrimary`; description 13sp `uiSettingsTextMuted`; `${ui.spacing.label.control}` (16dp) spacer; accent `Switch`; `.padding(horizontal = uiPaddingCardHorizontal, vertical = uiPaddingToggleVertical)` — the same row padding the existing inline rows use in the Auto-show zones card. Drop `clip` / `background` / `uiPaddingCardVertical`: `Card` ([L2289](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:2289)) owns the surface and pads **vertically only**, so the row must supply its own horizontal inset. Mirrors `SliderRowContent` ([L2123](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:2123)).
2. Call sites (4):
   - Coastline ([L1020](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1020)) → `Card { ToggleRowContent(...) }` (single-toggle card).
   - Orientation aids ([L1184/L1191/L1198](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1184)) → one `Card { ToggleRowContent(heading line); SectionDivider(); ToggleRowContent(cap arrow); SectionDivider(); ToggleRowContent(demo heading up) }`; keep all three descriptions; drop the three `uiSpacingCardGap` spacers between them.
3. Expected deltas (intended, not regressions): a single-toggle card grows ~4dp vertically (`Card` 8dp×2 + row 2dp×2 = 20dp, vs today's `SettingsToggleRow` 8dp×2 = 16dp); the Orientation-aids card shrinks (~72dp → ~52dp); the gap around a `SectionDivider` sitting between two rows is 2+6+1+6+2 = 17dp.
4. Acceptance: no `SettingsToggleRow` symbol remains; a toggle renders identically in a single-row card and a multi-row card, apart from the deltas above.

## P1.3 — Guideline rewrite

File: `docs/ui-component-guidelines.md` (+ `app/src/main/assets/ui.properties`)

- §1 (L15-26): drop the "Standalone toggle → `SettingsToggleRow`" branch; head becomes — a setting is a **row**, a `Card` groups rows, `SectionDivider` separates sections.
- §2.1 (L69-73): delete; its recipe becomes the canonical toggle row under §2.3.
- §2.3 (L87-108): rewrite as "`Card` = 1..N control rows separated by `SectionDivider`"; replace the `Row { Text + Switch }` snippet (which has no description slot) with the canonical row.
- §2.4 (L110-149): depth cap is **law** — `Card` → at most one `Expander` → `NestedCard` → controls; never a card / `uiCardBackground` surface inside a `NestedCard`. "Prefer a collapsible section" is **preference**, not rule. State that `NestedCard` is a surface treatment, not a nesting tier.
- §2.5 (L151-153): drop the `SettingsToggleRow` reference.
- §2.6 (L155-183): define **section** functionally — controls that belong together form one section; a stand-alone control is its own section. Rows inside a section → `${ui.spacing.grouped.row.gap}` (8dp); a `SectionDivider` at **every** section boundary. Remove the undefined "simple toggle rows that are not sections" clause. Worked examples: Orientation aids = one card, one section per control (3 controls → 2 dividers); Auto-show zones unchanged (its two On-approach rows are one section, 300 m the next).
- §2.9 (L211-215): rewrite to the single casing and the 18sp size — drop the `uppercase = true/false` description and the letter-spacing paragraph; keep the `SubSectionHeader` and Layers card-description bullets.
- §4 (L271-280): align anti-patterns — drop "never nest `SettingsToggleRow`", keep the depth cap.
- After the rewrite, re-verify `xTrack/Ui_Settings/FEAT_DSC_Ui_Settings.md` `## Rules` (it defers to §2.3/§2.4/§2.6).
- `ui.properties` comments at L8 and L40 name `SettingsToggleRow` → update.

## P1.4 — Tab strip: adopt the built-in M3 component

Files: `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt`, `app/src/main/java/ykws/android/maro/config/AppConfig.kt`, `app/src/main/assets/ui.properties`. No new dependency — `compose-material3` is already declared ([`app/build.gradle.kts:196`](../../app/build.gradle.kts:196)); material3 resolves to **1.4.0**, where `SecondaryTabRow` is stable (no `@OptIn`).

1. Replace the hand-rolled strip ([L258](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:258)) with `SecondaryTabRow(selectedTabIndex = selectedTab, …)`; delete the `drawBehind` indicator.
2. Override the defaults that would otherwise show through: `containerColor = uiSettingsBackground`, `divider = {}` (M3 draws a bottom hairline by default).
3. Tokenise the label: add `ui.font.tab.size=16sp` to `app/src/main/assets/ui.properties`; in `AppConfig.kt` add `var uiFontTabSize: Float = 16f; private set` next to `uiFontSectionSize` plus its parse line `uiFontTabSize = sp("ui.font.tab.size", uiFontTabSize)`; use it for the tab label (no hardcoded size).
4. Emit **custom cells** in the `tabs` slot instead of M3 `Tab`: `Box(Modifier.weight(1f))` + `selectable(selected = …, role = Role.Tab, onClick = …)` + 14dp vertical padding + centred `Text(maxLines = 1)`, colour = accent when selected else `uiSettingsTextSecondary`. Row inset removed — the row runs edge-to-edge.
5. Do not touch: pager sync ([L226-239](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:226)), `userScrollEnabled = false`, the four per-tab scroll states, `settingsTabLabels`, header, footer.
6. Expected visible deltas: indicator slides and has rounded ends; strip grows a few dp (M3 ~48dp text-only minimum); bounded ripple; tabs announce `Role.Tab` + selected state with 48dp targets.
7. Verify: tab↔pager sync both directions, both orientations, strip alignment with the pager's 24dp inset, and no clipping from the taller strip.
8. Resolved in implementation (material3 **1.4.0**): `PrimaryTabRow`'s default indicator is a fixed short stub (~24dp), and M3 `Tab`'s internal padding plus its 90dp minimum width caused the "Navigation" label wrap and the side gaps. Final design is `SecondaryTabRow` (full-width indicator) + custom cells, steps 1-4.

## Open points

1. **Decided** — tokenise the tab label size (`ui.font.tab.size=14sp`), see P1.4 step 3.
2. **Decided** — a section is defined **functionally**: controls that belong together share one section; a stand-alone control is its own section. Within a section → `${ui.spacing.grouped.row.gap}` (8dp); every section boundary → `SectionDivider`.
3. **Decided** — `ToggleRowContent` (box-less toggle row), mirroring `SliderRowContent`.
4. **Decided** — phase 1 lands first; phase 2 (menu) is reviewed once phase 1 is complete.
5. **Decided** — focus switched to `Ui_Settings` (`#focus ui settings`, 2026-09-11).
6. **Uncommitted** — the branch carries three files: `xTrack/GLOBAL_CONTEXT.md` and `xTrack/Ui_Menu/FEAT_HYD_Ui_Menu.md` (modified) plus this plan (new/untracked) — awaiting a `#commit` go-ahead.

## Out of scope

- Phase 2 — menu drawer normalization (`Ui_Menu`).
- Historical references in other features' docs (`xTrack/BoatTrace/260620_FEAT_PLN_BoatTrace_settings-page-rules.md` R1, `xTrack/Markers/260703_FEAT_PLN_Markers_setting-markers.md`, `xTrack/Performance/FEAT_DOC_Performance_battery-design.md`, `docs/map-lib-migration-plan.md`) — reference-only, not edited here.
