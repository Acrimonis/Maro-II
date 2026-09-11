<!-- scope: feature -->
# Ui_Menu — Menu drawer migrated to the Settings render model

**Revised:** 2026-09-11 — supersedes the 2026-09-09 draft. The Settings row model and the
guidelines changed underneath it, so every source anchor, token and casing rule was stale.
**Branch:** `feature/menu-twks` (current) — resolved (Q4).
**Feature:** Ui_Menu (active via `#focus ui menu`)
**Scope:** **Rendering-only migration.** Make the Menu drawer read like a Settings tab body:
`SectionHeader` + `CardArea` + token spacing + surface-free rows. No container/layout change, no
tabs, no expanders, no new controls.

---

## 1. Why this rewrite (drift since 2026-09-09)

| Old plan assumption | Reality after the Settings work |
|---|---|
| Stencils live in `MapScreen.kt` (`:5089`, `:5358`, `:5244`, `PositionSettings :4575`) | Moved to [`MapScreenSettingsOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt) — `SectionHeader :1245`, `SectionDivider :1500`, `Card :1613`; `MapScreen.kt` is now ~2600 lines, those anchors are dead |
| `MenuDrawerOverlay` anchors `:132/:213/:405`, `:290` | Drifted — titles now [`MenuDrawerOverlay.kt:139`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:139)/[`:220`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:220)/[`:422`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:422); dividers `:308/:337/:398/:508` |
| Header→card 8dp, section→section 24dp | Tokens are now **6dp** (`ui.spacing.header.bottom`) and **14dp** (`ui.spacing.section.gap`) |
| `SectionHeader` uppercases; strings go title-case (D-I) | §2.9: `SectionHeader` is **sentence case, 18sp, no casing variant** — nothing uppercases |
| Extract a `SettingsToggleRow` exception (A1) | `SettingsToggleRow` was deleted; [`ToggleRow()`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1344) is `internal` + surface-free |
| D-G: "extract to `ui/components`" | Confirmed — Q1 resolved: the four stencils move to `ui/components`, one file each, neutral names (`Card`→`CardArea`) |

Old anchors and amendments are dropped, not patched.

## 2. Scope

### In scope
- Rewrite the [`MenuDrawerOverlay`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:72) body onto the shared Settings stencils + tokens.
- Reuse [`ToggleRow`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1344) for the four switches (GPS mode, auto-show master, Show dir & speed, Show zones); add an optional `checkedColor` (default accent) so the GPS row keeps its status colour.
- **Extract** `SectionHeader` (+ `trailing` slot), `SectionDivider`, `ToggleRow`, and `Card`→`CardArea` out of [`MapScreenSettingsOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1245) into [`ui/components`](../../app/src/main/java/ykws/android/maro/ui/components) — one file per primitive, neutral names, `internal` visibility.
- **Dependency:** the 14-token `uiSettings*` → neutral rename is tracked separately — [`260911_FEAT_PLN_ColorManagement_ui-token-de-settings.md`](../ColorManagement/260911_FEAT_PLN_ColorManagement_ui-token-de-settings.md). Order: extraction first, rename second (both on `feature/menu-twks`).
- Normalise the menu strings (both locales) to sentence case — reuse `settings_section_*`, sentence-case the row labels, drop the dead `menu_import_export`.
- Truth-up the two guideline docs that still describe the old menu card ([`ui-drawer-guidelines.md`](../../docs/ui-drawer-guidelines.md), [`ui-component-guidelines.md`](../../docs/ui-component-guidelines.md)).

### Out of scope / anti-scope
- No panel footprint or container change; no tabs/pager; no `Expander`/`NestedCard`; no footer.
- No new controls, no reordering, and no new strings — the string work only normalises (rewrites/deletes).
- No Settings rendering change (index promotion is visibility-only; `trailing` defaults empty).
- No new libraries or dependencies.

## 3. Decisions

| # | Decision |
|---|----------|
| D-A | **Keep** the 64dp Settings-gear header action ([`MenuDrawerOverlay.kt:120`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:120)). |
| D-B | **Keep** the panel container: right full-height drawer, portrait 75% / landscape width-scaled. |
| D-C | **Keep** the 3-section structure, one `CardArea` per section. |
| D-D | **No tabs** — the menu is one Settings tab body, not the tab bar/pager. |
| D-E | **No Expander / NestedCard** — grouped `CardArea`s only. |
| D-F | **No footer** (app-version footer stays a full-screen Settings trait). |
| D-G | **Reuse** the Settings stencils — do not re-implement. **Hosting resolved (Q1):** extract all four into `ui/components`, one file per primitive. |
| D-H | **Drop the 56dp menu carve-out** → 48dp minimum touch target, natural height. |
| D-I | Section titles are **sentence case** per §2.9; `SectionHeader` renders as-is. **Replaces** the old title-case+uppercase rule — this is a visible change (Q3). |
| D-J | **Hosting/extraction:** `SectionHeader.kt`, `SectionDivider.kt`, `CardArea.kt` (`Card` renamed), `ToggleRow.kt` in `ui/components` — all `internal`. |
| D-K | **`ToggleRow` gains `checkedColor`** (default accent); the GPS row passes `gpsToggleColor` — dynamic status colour preserved (must not be `remember`ed). |
| D-L | **Section→section gap = `uiSpacingSectionGap` (14dp)** — Settings rhythm; drawer §8's 8dp inter-card gap is retired (verified: no other drawer stacks legacy §8 cards). |
| D-M | **All menu strings normalised to sentence case in both locales**; `menu_section_*` deleted in favour of the existing `settings_section_*`; `menu_show_zones`/`menu_show_tracks_direction` sentence-cased; dead `menu_import_export` removed. |
| D-N | **Branch = current `feature/menu-twks`** (branched from post-PR#227 `develop`; already carries the extracted stencils). |

## 4. Current vs target

| Aspect | Menu today | Target (Settings render model) |
|---|---|---|
| Section titles | hand-rolled `Text` 17sp Bold accent 1.sp ([`:139`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:139), [`:220`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:220), [`:422`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:422)) | shared `SectionHeader` (moved from [`MapScreenSettingsOverlay.kt:1245`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1245) → `ui/components/SectionHeader.kt`) — 18sp Bold accent, no letter-spacing, sentence case, `trailing` slot for filters |
| Card surface | raw `Column` clip(12) + `uiCardBackground` + pad 16×10 ([`:148`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:148), [`:262`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:262), [`:464`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:464)) | shared `CardArea` (moved from [`MapScreenSettingsOverlay.kt:1613`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1613) → `ui/components/CardArea.kt`) — 12dp radius, `uiCardBackground`, `ui.padding.card.horizontal` 16dp + `ui.padding.card.vertical` 8dp, **container owns the inset** |
| Rows | hand-rolled `Row` + `Text` + `Switch` | `ToggleRow` (moved to `ui/components/ToggleRow.kt`) — 16sp Medium label, vertical pad only (`ui.padding.toggle.vertical` 2dp), Switch colour = `checkedColor` (default accent; GPS passes `gpsToggleColor`) |
| Count text | `Text("$trackCount")` / `"$markerCount"`, 14sp `uiSettingsTextMuted` ([`:288`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:288), [`:489`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:489)) | keep `14.sp` (or `uiFontRangeSize`, also 14sp) — **not** `uiFontValueSize` (16sp) |
| Dividers | `Spacer(2) + HorizontalDivider(0.5dp) + Spacer(2)` = **4.5dp** ([`:308`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:308), [`:337`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:337), [`:398`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:398), [`:508`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:508)) | `SectionDivider` (moved to `ui/components/SectionDivider.kt`) — 6dp + 1dp + 6dp = **13dp**. Both changes intentional: thickness 0.5→1dp **and** total gap 4.5→13dp |
| Header→first card | POSITION `Spacer(2.dp)` ([`:146`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:146)) and TRACKS `Spacer(2.dp)` ([`:260`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:260)); MARKERS `Spacer(8.dp)` ([`:462`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:462)) — already nearer the 6dp target | `uiSpacingHeaderBottom` (6dp) for all three |
| Section→section | `Spacer(16.dp)` ([`:212`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:212), [`:414`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:414)) | `uiSpacingSectionGap` (**14dp**) — Settings rhythm; drawer §8's 8dp inter-card gap is retired (no other consumer, verified) |
| Row heights | mixed 56 ([`:273`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:273), [`:343`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:343), [`:474`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:474), [`:514`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:514)) / 48 ([`:313`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:313)) | natural height + `heightIn(min = 48.dp)`; Import/Export single 48dp pair, drop the 56dp wrapper. Switch rows get the 48dp floor from Material3 `Switch`'s minimum interactive size; **non-switch rows need an explicit `heightIn`** |
| Typography | hardcoded 16/14/13sp | `uiFontToggleSize` / `uiFontValueSize` / `uiFontDescSize` |
| Live stats | `StatRow` label 13sp muted + value 14sp Medium ([`:540`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:540)) | **Keep value 14sp with an explicit comment** — remapping to `uiFontValueSize` (16sp) would be a visible change; tokenize the label only |

## 5. Implementation steps

1. **Extract the stencils to `ui/components`** (Q1 resolved), one file per primitive, all `internal`:
   - `SectionHeader.kt` — move `SectionHeader` and convert `Column(fillMaxWidth)` into `Row(verticalAlignment = Alignment.CenterVertically) { title Column(weight 1f); trailing() }` with `trailing: @Composable RowScope.() -> Unit = {}` (empty default keeps every existing Settings call site identical — regression check; `CenterVertically` matters for the menu's 40dp trailing `IconButton`s vs the 18sp title, and is inert for the empty-trailing Settings sites). Precedent for an empty-default composable lambda: [`DrawerHeader.actions`](../../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt:71).
   - `SectionDivider.kt` — move `SectionDivider` unchanged.
   - `CardArea.kt` — move `Card` and rename it `CardArea` (avoids shadowing Material3 `Card`); update the **15** `Card {` call sites in [`MapScreenSettingsOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:215) (`:215,427,581,627,684,698,774,813,841,922,978,1052,1125,1144,1186`) plus the 4 KDoc `[Card]` references (`:1260,1339,1391,1445`).
   - `ToggleRow.kt` — move `ToggleRow`; add `checkedColor: ComposeColor = ComposeColor(AppConfig.uiSettingsAccent)`. It drives **both** `checkedThumbColor = checkedColor` and `checkedTrackColor = checkedColor.copy(alpha = 0.4f)`. (The moved file carries `import androidx.compose.ui.graphics.Color as ComposeColor`, so the type must be spelled `ComposeColor`.) Do **not** `remember` it — `gpsToggleColor` is a recomposition parameter and must stay dynamic.
   - Remove the originals from `MapScreenSettingsOverlay.kt`, add the new imports in every consumer, and add the `ToggleRow` import to [`RegulatedZoneComponents.kt:316`](../../app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt:316) (a genuine import addition — it is cross-package after the move).
2. **Rewrite the [`MenuDrawerOverlay`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:72) body** (container + `DrawerScaffold` wiring unchanged):
   - Three titles → shared `SectionHeader`; TRACKS/MARKERS pass the link/filter/reset group via `trailing`, only when axes are non-empty (keep the reset-alpha logic).
   - Raw card `Column`s → shared `CardArea`; drop the 16×10 padding (the card owns the inset).
   - Switches → `ToggleRow` (GPS row passes `checkedColor = gpsToggleColor`).
   - Manual dividers → `SectionDivider`; magic spacers → `uiSpacingHeaderBottom` / `uiSpacingSectionGap`. **Include the POSITION in-card divider** ([`:180`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:180)–[`:186`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:186): inline 1dp `Box` + 6dp spacers), not only the four cited in §4.
   - **Import/Export** → one side-by-side pair at `heightIn(min = 48.dp)` (drop the 56dp wrapper). **Preserve:** the Upload/Download `Icon(24.dp, ButtonColors.icon)`, the per-button `RoundedCornerShape(8.dp)` + `clickable` + `padding(horizontal = 12.dp)` + `semantics(mergeDescendants = true) {}`, and the pair's `Arrangement.SpaceBetween`.
   - **Nav rows** (`Manage Tracks`/`Manage Markers`) → surface-free rows, vertical pad only, explicit `heightIn(min = 48.dp)`. **Preserve:** count text (`"$trackCount"`/`"$markerCount"`, 14sp `uiSettingsTextMuted`, [`:288`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:288)/[`:489`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:489)) left of the chevron; the "open first item" `IconButton(40dp)` → `onOpenFirstTrack`/`onOpenFirstMarker` with `enabled = != null` and icon alpha `1f`/`0.35f`; the filter **guard** at [`:244`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:244) (marker [`:446`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:446)) and the **alpha** applied at [`:254`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:254) (marker [`:456`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:456)).
   - **Keep** `contentPadding = PaddingValues(horizontal = 24.dp)` ([`:119`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:119)) — matches the Settings body's 24dp padding ([`MapScreenSettingsOverlay.kt:174`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:174)), so the `CardArea` 16dp inset aligns both surfaces (40dp text inset).
   - Keep the header (`back` + "Maro II" + 64dp gear) unchanged.
3. **Strings** (`values` + `values-fr`):
   - **Delete** `menu_section_position/tracks/markers` (both locales); the menu calls the existing `settings_section_*` keys. That also removes the ALL-CAPS titles — `settings_section_*` is already sentence case (EN `Position mode`/`Tracks`/`Markers`; FR `Mode de position`/`Traces`/`Repères`), so the old uppercase values are retired, not copied (D-I/Q3).
   - **Sentence-case the row labels:** `menu_show_zones` `Show Zones`→`Show zones`; `menu_show_tracks_direction` `Show Dir & Speed`→`Show dir & speed` (FR already sentence case).
   - **Delete** `menu_import_export` (both locales) — verified unused (no `.kt` reference; the body renders `action_export`/`action_import`).
4. **Docs & tracking** (mandatory, not deferred):
   - [`docs/ui-drawer-guidelines.md`](../../docs/ui-drawer-guidelines.md:243) §8: remove the 🔴 "Menu override 56dp" bullet; scope the "Divider internal spacing `Spacer(2.dp)`" bullet ([`:244`](../../docs/ui-drawer-guidelines.md:244)) to §9 list-item cards only; **retire the "Between cards: `Spacer(8.dp)`" rule** ([`:241`](../../docs/ui-drawer-guidelines.md:241)) — verified no other drawer stacks legacy §8 cards.
   - [`docs/ui-component-guidelines.md:336`](../../docs/ui-component-guidelines.md:336) — drop/supersede the **"Drawer-internal card gap — 8dp"** row here (this is the real location; drawer §3 is the Surfaces table).
   - [`docs/ui-component-guidelines.md:47`](../../docs/ui-component-guidelines.md:47) §2.0: **keep** the Wide `16×10` row — it is still live elsewhere ([`MapControls.kt:302`](../../app/src/main/java/ykws/android/maro/ui/map/MapControls.kt:302)/`:340`, [`MapScreen.kt:284`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:284)/`:2272`, [`CoastlineMapView.kt:68`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt:68)/`:135`); delete only the `(e.g. menu slide panel)` example.
   - [`docs/ui-component-guidelines.md:359`](../../docs/ui-component-guidelines.md:359) §5.1: rewrite the Drawer Cards spec to the grouped-card model; update every `Card {` reference in the doc (`:52,121,134-146,182`) to `CardArea {`.
   - **xTrack notes (mandatory):** refresh [`FEAT_HYD_Ui_Menu.md`](FEAT_HYD_Ui_Menu.md) — currently three-way stale (`feature/menu-render-upt` at `:3`, `feature/settings-menu-clean` at `:5`, "title-case strings" at `:3`) → one branch `feature/menu-twks` + sentence case — and the [`FEAT_DSC_Ui_Menu.md`](FEAT_DSC_Ui_Menu.md) summary line.
5. **Build & verify:** `apk-build.bat` → BUILD SUCCESSFUL; on-device visual pass — menu reads with the Settings title/card rhythm; no regression to filter controls, auto-show row, live stats, import/export taps, 48dp+ targets; all Settings tabs unchanged after the `SectionHeader` Row conversion.

## 6. Verification checklist

- [x] Menu body uses shared `SectionHeader`/`CardArea`/`SectionDivider`/`ToggleRow` (all imported from `ui/components`) — no duplicated stencils left in the drawer.
- [x] Spacers read tokens (`uiSpacingHeaderBottom` 6dp, `uiSpacingSectionGap` 14dp); no magic 2/16dp gaps.
- [x] Rows surface-free, vertical pad only, natural height with 48dp min; Import/Export single 48dp pair (chrome preserved per step 2).
- [x] Section titles sentence case in both locales; no uppercase transform anywhere.
- [x] `menu_section_*` removed and `settings_section_*` reused; row labels sentence-cased; dead `menu_import_export` dropped.
- [x] Divider is `SectionDivider` (6+1+6 = 13dp) — accepted thickness 0.5→1dp **and** gap 4.5→13dp.
- [x] Header gear stays 64dp; panel footprint unchanged; no footer.
- [x] Filter controls, auto-show master, live stats unchanged; the GPS switch still recolours live via `checkedColor = gpsToggleColor`.
- [x] All Settings `SectionHeader` call sites render identically after the Row conversion.
- [x] `apk-build.bat` → BUILD SUCCESSFUL.
- [x] Guidelines updated: ui-drawer §8, ui-component §2.0 (example only) + §3 gap row + §5.1 + `CardArea` refs.
- [x] Ui_Menu hydration + DSC notes refreshed (mandatory).
- [x] Nav-row extras preserved (count text 14sp, chevron enabled-state + alpha, reset guard+alpha); content padding stays 24dp.

**Outcome (2026-09-11):** implemented on `feature/menu-twks`; builds SUCCESSFUL; Ask review verdict PASS (doc-only A1/A2 applied; optional A3 = `AppConfig.kt` KDoc prose); on-device pass confirmed.

## 7. Files touched

| File | Change |
|------|--------|
| `app/.../ui/components/SectionHeader.kt` (new) | Moved `SectionHeader` + `trailing` slot + `CenterVertically` |
| `app/.../ui/components/SectionDivider.kt` (new) | Moved `SectionDivider` |
| `app/.../ui/components/CardArea.kt` (new) | Moved `Card`, renamed `CardArea` |
| `app/.../ui/components/ToggleRow.kt` (new) | Moved `ToggleRow` + optional `checkedColor` |
| `app/.../ui/map/MapScreenSettingsOverlay.kt` | Originals removed; imports added; 15 `Card`→`CardArea` call sites + 4 KDoc refs |
| `app/.../ui/map/RegulatedZoneComponents.kt` | `ToggleRow` import added |
| `app/.../ui/map/MenuDrawerOverlay.kt` | Body rewritten onto shared stencils + tokens |
| `app/src/main/res/values/strings.xml` + `values-fr` | `menu_section_*` deleted (reuse `settings_section_*`); `menu_show_zones`/`menu_show_tracks_direction` sentence-cased; dead `menu_import_export` removed |
| `docs/ui-drawer-guidelines.md` | §8 56dp override removed; divider bullet scoped to §9; 8dp inter-card rule retired |
| `docs/ui-component-guidelines.md` | §2.0 example removed (Wide row kept); §3 gap row dropped; §5.1 rewritten; `Card`→`CardArea` refs |
| `xTrack/Ui_Menu/FEAT_HYD_Ui_Menu.md` + `FEAT_DSC_Ui_Menu.md` | Branch/state/plan-of-record refresh (mandatory) |

## 8. Review decisions (all resolved 2026-09-11)

| # | Question | Proposed default |
|---|----------|------------------|
| ~~Q1~~ | **Resolved** — extract all four to `ui/components`, one file per primitive, neutral names (`Card`→`CardArea`) | done |
| ~~Q2~~ | **Resolved** — 14dp (Settings rhythm); drawer §8's 8dp inter-card gap retired (verified no other consumer) | done |
| ~~Q3~~ | **Resolved** — yes; menu titles normalise to sentence case via the shared `settings_section_*` keys; row labels sentence-cased; dead `menu_import_export` removed | done |
| ~~Q4~~ | **Resolved** — work on the current `feature/menu-twks` | done |

## 9. Residual risks (from the 2026-09-11 Ask review)

1. Visual-only regressions from the disclosed rhythm changes (section gap 16→14dp, title→card 2→6dp, divider total 4.5→13dp, title 17sp/1sp-ls → 18sp/0, row min 56→48dp). The on-device pass is the only gate.
2. `ToggleRow` is consumed cross-package by [`RegulatedZoneComponents.kt:316`](../../app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt:316) — a real import addition, not a re-point.
3. `ui.settings.*` survives in 3 guideline docs, [`FEAT_DOC_Ui_Menu_decisions.md`](FEAT_DOC_Ui_Menu_decisions.md:25) and ~30 historical xTrack plans; the ColorManagement "zero in docs" check excludes `xTrack/**` history.
4. 48dp minimum on switch rows comes from Material3 `Switch`'s minimum interactive size, not an explicit `heightIn`; non-switch rows need their own min (nav rows have it).
5. `checkedColor` must not be `remember`ed — `gpsToggleColor` is a recomposition parameter and must stay dynamic.
