<!-- scope: feature -->
# Ui_Menu — Menu drawer normalized to Settings theme

**Branch:** `feature/menu-render-upt`
**Feature:** Ui_Menu (active via `#focus ui menu`)
**Scope:** Visual-refactor only — make the Menu drawer render like a Settings tab body
(SectionHeader + Card + token spacing). No new behaviour, no layout/container change,
no tabs, no expanders. Discussion-approved 2026-09-09. Ask-reviewed 2026-09-09
(amendments A1–A7 incorporated below).

## Decisions (locked)

| # | Decision |
|---|----------|
| D-A | **Keep** the 64dp Settings-gear header action. |
| D-B | **Keep** the panel container: right-anchored full-height drawer, width = portrait 75% / landscape `0.75 × landscapeDashboardWidth × uiLandscapePanelWidthScale` (1.2) ([`OverlayLayer.kt`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:297)); empty bottom tail accepted. |
| D-C | **Keep** the 3-section structure: POSITION / TRACKS / MARKERS, one Card per section. |
| D-D | **No tabs** — the menu maps to a single Settings tab body, not the tab bar/pager. |
| D-E | **No Expander / NestedCard** — plain grouped Cards only. |
| D-F | **No footer** in the menu (version footer stays a full-screen Settings trait). |
| D-G | **Extract shared stencils** from MapScreen into `ui/components` so the menu reuses them (no duplicated drift). |
| D-H | **Drop the 56dp menu carve-out** → Settings natural-height rows, 48dp minimum touch target. |
| D-I | **Section titles** go title-case in strings; shared `SectionHeader` uppercases. Matches the *PositionSettings* (default `uppercase=true`) subset of Settings usage — other Settings tabs render title-case via `uppercase=false` and are NOT the parity target. |

## Mental model

```
MenuDrawerOverlay (container & layout untouched — D-A, D-B)
└─ DrawerScaffold body (scrollable)                      ← "one Settings tab body" (D-D)
   ├─ SectionHeader "Position mode"                      ← no trailing slot (no filters)
   │  └─ Card: inline GPS toggle (+ optional auto-show row)
   ├─ SectionHeader "Tracks" [filters…]                  ← SectionHeader + trailing slot
   │  └─ Card: nav row → SectionDivider → toggle → SectionDivider → import/export
   │          (+ live-stats block when recording)
   └─ SectionHeader "Markers" [filters…]
      └─ Card: nav row → SectionDivider → "Show zones" toggle
```

Compare to a Settings tab body ([`PositionSettings`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:4575)):
`SectionHeader` → `Spacer(uiSpacingHeaderBottom)` → `Card` → `Spacer(uiSpacingSectionGap)` → …

## Current state vs Target (per aspect)

| Aspect | Menu today | Target (Settings-style) |
|--------|-----------|--------------------------|
| Section titles | hand-rolled `Text` 17sp Bold accent 1.sp ([`MenuDrawerOverlay.kt:132`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:132), [`213`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:213), [`405`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:405)) | shared [`SectionHeader`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:5089) (17sp `uiFontSectionSize`, uppercase, 1.sp) |
| Card surface | raw `Column` clip(12) + `uiCardBackground` + pad 16×10 ([`MenuDrawerOverlay.kt:142`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:142)) | shared [`Card`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:5358) (12dp radius, `uiCardBackground`, **8dp vertical pad only — no horizontal inset**) |
| Rows | each row padded 16×10 by the card; no per-row pad | rows carry their own `horizontal = 16dp` + `vertical = uiPaddingToggleVertical` padding (Card has no horizontal inset) — applies to all rows incl. auto-show + live-stats blocks |
| Divider between blocks | `Spacer(2) + HorizontalDivider(0.5dp) + Spacer(2)` ([`MenuDrawerOverlay.kt:290`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:290)) | shared [`SectionDivider`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:5244) — 6dp gap + 16dp inset, **1dp** line (`ui.divider.height`). Note: 0.5→1dp thickness change is intentional. |
| Header → first card | `Spacer(2.dp)` | `uiSpacingHeaderBottom` (8dp) |
| Section → section | `Spacer(16.dp)` ([`MenuDrawerOverlay.kt:206`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:206), [`397`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:397)) | `uiSpacingSectionGap` (24dp) |
| Row heights | mixed: nav rows 56 ([`:256`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:256), [`:446`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:446)); Track-direction switch 48 ([`:296`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:296)); Show-zones switch 56 ([`:486`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:486)); POSITION + auto-show switches have no `heightIn` ([`:149`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:149), [`:181`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:181)); Import/Export outer 56 ([`:326`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:326)) | uniform natural height + `heightIn(min = 48.dp)` (D-H). Import/Export stay a single side-by-side pair at `min 48` (drop the 56 wrapper, keep 48 inner tap targets). |
| Typography | hardcoded 16sp/14sp/13sp | `uiFontToggleSize` (labels) / `uiFontValueSize` / `uiFontDescSize` |
| Live stats | `StatRow` label 13sp muted + value 14sp Medium ([`MenuDrawerOverlay.kt:511`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:511)) | **Decision (A4):** keep value at 14sp with an explicit comment — mapping to `uiFontValueSize` (16sp Bold) would be a visible change. Do NOT claim parity; tokenize only the label via `uiFontDescSize` if it matches 13sp. |

## NOT mappable (kept as-is / out of scope)

| Aspect | Reason |
|--------|--------|
| Panel footprint (right drawer, portrait 75% / landscape width-scaled, full-height) | Container feature — D-B. |
| 4-tab bar + HorizontalPager + per-tab ScrollState | Layout feature — D-D (menu = one tab body, not a pager). |
| Expander / NestedCard disclosure | Menu rows are direct actions, no hidden sub-settings — D-E. |
| `SettingsToggleRow` (self-contained standalone toggle card) | §2.1 forbids nesting it in a grouped card ([`ui-component-guidelines.md:73`](../../docs/ui-component-guidelines.md:73)); menu uses grouped *inline* rows → NOT extracted, replicate the [`PositionSettings` inline-row pattern](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:4594) instead (A1). |
| Footer app-version line | Full-screen Settings trait only — D-F. |
| Colour swatches, sliders, segmented selectors | Menu has no such controls. |
| `SettingsViewModel.expanderStates` | Requires expanders — absent by design. |

## Implementation steps

1. **Extract shared stencils** (MapScreen → `ui/components`, e.g. new `SettingsComponents.kt` or existing shared file):
   - [`SectionHeader`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:5089) — convert its `Column(fillMaxWidth)` into `Row { title Column(weight 1f); trailing() }` with `trailing: @Composable RowScope.() -> Unit = {}`. Empty-default keeps all existing Settings call sites visually identical (regression note A7); composable-lambda default is proven in repo (`DrawerHeader.actions`).
   - [`Card`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:5358)
   - [`SectionDivider`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:5244)
   - **Do NOT extract `SettingsToggleRow`** (standalone card — see NOT mappable).
   - Visibility `private` → `internal` for cross-package use (precedent: `internal fun FilterControl` imported by the menu). The new file must redeclare `import … Color as ComposeColor` (precedent [`DrawerScaffold.kt:40`](../../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt:40)).
   - Update MapScreen call sites to the moved symbols (pure move — no behaviour change). Search-confirmed: no existing `Card`/`SectionHeader` symbol in `ui/components`, no collision.

2. **Rewrite `MenuDrawerOverlay` body** to compose like a Settings tab:
   - Replace 3 hand-rolled titles with shared `SectionHeader` (title-case strings; TRACKS/MARKERS pass filter controls + reset via `trailing`, only when axes non-empty, preserving reset-alpha logic at [`MenuDrawerOverlay.kt:221`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:221)).
   - Replace raw card `Column`s with shared `Card` + per-row 16×`uiPaddingToggleVertical` padding (A6 — Card has no horizontal inset; auto-show + live-stats blocks included).
   - Replace manual dividers with `SectionDivider`.
   - Replace magic spacers with `uiSpacingHeaderBottom` (8dp) / `uiSpacingSectionGap` (24dp).
   - Uniform row model: natural height, `heightIn(min = 48.dp)`; Import/Export single pair at 48, drop 56 wrapper and nested double-padding.
   - Keep header (back + "Maro II" + 64dp gear) and `DrawerScaffold`/`DrawerSlot` wiring unchanged.

3. **Strings** (`values` + `values-fr`): switch `menu_section_position/tracks/markers` to title-case (`Position mode`/`Tracks`/`Markers`; FR `Mode de position`/`Traces`/`Repères`) so `SectionHeader` uppercases from one source. Verify round-trip reproduces today's output: EN `POSITION MODE / TRACKS / MARKERS`, FR `MODE DE POSITION / TRACES / REPÈRES` (vs current [`values/strings.xml:255`](../../app/src/main/res/values/strings.xml:255), [`values-fr/strings.xml:254`](../../app/src/main/res/values-fr/strings.xml:254)).

4. **Guideline docs** (truthfulness — D-H/D-G side effects):
   - [`docs/ui-drawer-guidelines.md`](../../docs/ui-drawer-guidelines.md:241) §8: remove/supersede the "Menu override 56dp" row; scope the adjacent "Divider internal spacing `Spacer(2.dp)`" bullet ([`:242`](../../docs/ui-drawer-guidelines.md:242)) to §9 list-item cards only — grouped menu cards now use `SectionDivider` (6dp gap).
   - [`docs/ui-component-guidelines.md`](../../docs/ui-component-guidelines.md:42) §2.0: reconcile the `16×10 … (menu slide panel)` Wide note.
   - **Add [`docs/ui-component-guidelines.md` §5.1 "Drawer Cards (MenuDrawerOverlay)"](../../docs/ui-component-guidelines.md:286):** rewrite the 16×10 row spec to the new grouped-card model (A2).
   - Update Menu row in the Surfaces/DrawerScaffold consumer table if the content contract changes (should not — still a `DrawerScaffold` consumer).

5. **Build & verify:** `apk-build.bat` → BUILD SUCCESSFUL. On-device visual check: menu POSITION/TRACKS/MARKERS now read with Settings' title/card rhythm; confirm no regressions to filter controls, auto-show master row, live-stats block, import/export taps, and 48dp+ touch targets. Also confirm all Settings tabs still render identically after the `SectionHeader` Row conversion (A7).

## Verification checklist

- [ ] Menu body renders via shared `SectionHeader`/`Card`/`SectionDivider` (no duplicated stencils remain in the drawer).
- [ ] Spacers read tokens (`uiSpacingHeaderBottom` 8dp, `uiSpacingSectionGap` 24dp); no magic 2/16dp gaps remain in the menu body.
- [ ] Row heights uniform (48dp min); Import/Export single side-by-side pair, no 56dp wrapper, no double padding.
- [ ] Section titles display identically to before in both locales (title-case strings + `SectionHeader` uppercase).
- [ ] Divider visually becomes 1dp `SectionDivider` (6dp gap) — accepted 0.5→1dp change (A6).
- [ ] All Settings `SectionHeader` call sites render identically after Row conversion (A7).
- [ ] Header gear stays 64dp; panel footprint unchanged (portrait 75% / landscape width-scale); no footer added.
- [ ] Filter controls, auto-show master, live stats, GPS/toggle colors unchanged.
- [ ] `apk-build.bat` → BUILD SUCCESSFUL.
- [ ] Guideline docs updated: ui-drawer §8, ui-component §2.0 + §5.1.

## Files touched

| File | Change |
|------|--------|
| `app/.../ui/map/MapScreen.kt` | Extract stencils (`SectionHeader`+trailing slot, `Card`, `SectionDivider`); call sites moved; `SettingsToggleRow` stays |
| `app/.../ui/components/` (new or existing) | Host the extracted stencils (`ComposeColor` alias redeclared) |
| `app/.../ui/map/MenuDrawerOverlay.kt` | Body rewritten onto shared stencils + tokens |
| `app/src/main/res/values/strings.xml` + `values-fr` | Menu section titles → title-case |
| `docs/ui-drawer-guidelines.md` | §8 56dp override removal; §8 2dp-divider bullet scoped to §9 list cards; consumer-table note |
| `docs/ui-component-guidelines.md` | §2.0 Wide note reconciliation; §5.1 Drawer Cards rewrite (A2) |

## Anti-scope (do NOT do)

- No tab bar, HorizontalPager, or per-tab scroll state.
- No Expander / NestedCard / expander-state persistence.
- No container/footprint change; no footer.
- No new libraries, no new dependencies.
- No menu content reordering or new controls.
- No `SettingsToggleRow` nesting in grouped menu cards.

## Ask review amendments (2026-09-09)

- **A1** — Drop `SettingsToggleRow` from extraction; menu uses grouped inline rows (replicate `PositionSettings` pattern).
- **A2** — Add `ui-component-guidelines §5.1` to step 4 + files-touched (names the menu directly).
- **A3** — In `ui-drawer-guidelines §8`, scope the 2dp-divider bullet to §9 list cards; grouped menu cards use `SectionDivider`.
- **A4** — StatRow: explicit decision — keep 14sp value with comment; no parity claim; no silent token remap.
- **A5** — Import/Export remain a single side-by-side pair at `min 48` (drop 56 wrapper, keep 48 inner targets).
- **A6** — State `Card` has no horizontal inset (rows self-pad 16) and the 0.5→1dp divider change; landscape width footnote in D-B.
- **A7** — `SectionHeader` Row conversion regression note (all existing Settings call sites render identically with empty default `trailing`).
