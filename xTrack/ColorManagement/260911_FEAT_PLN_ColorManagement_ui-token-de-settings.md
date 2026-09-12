<!-- scope: feature -->
# ColorManagement — de-settings-ify the shared UI tokens

**Created:** 2026-09-11
**Branch:** `feature/menu-twks` (same session branch as the Ui_Menu migration)
**Feature:** ColorManagement
**Scope:** **Pure rename.** Strip the `settings` segment from the 14 shared UI colour tokens
(property keys + `AppConfig` accessors + every call site). Values, defaults and rendering are
**unchanged** — zero visual diff.

**Why now:** the shared stencils (`CardArea`, `ToggleRow`, `SectionHeader`, `SectionDivider`) were
moved to `ui/components` and renamed neutrally in the Ui_Menu migration, but the colours they read
still say "settings" (and are used app-wide — drawers, wizards, dialogs, lists, menu, map overlays).
This finishes the naming.

**Ordering:** run **after** the Ui_Menu extraction (which introduces the neutral file/symbol names),
so the grep end-state is clean. Both plans share branch `feature/menu-twks`.

**Related:** [`../Ui_Menu/260909_FEAT_PLN_Ui_Menu_menu-render-upt.md`](../Ui_Menu/260909_FEAT_PLN_Ui_Menu_menu-render-upt.md).

---

## 1. Rename map (14 tokens)

Rule: drop the `settings` segment from the key, drop `Settings` from the accessor.

| # | Property key (now) | Property key (new) | `AppConfig` (now → new) |
|---|---|---|---|
| 1 | `ui.settings.background` | `ui.background` | `uiSettingsBackground` → `uiBackground` |
| 2 | `ui.settings.toast.background` | `ui.toast.background` | `uiSettingsToastBackground` → `uiToastBackground` |
| 3 | `ui.settings.toast.text` | `ui.toast.text` | `uiSettingsToastText` → `uiToastText` |
| 4 | `ui.settings.text.primary` | `ui.text.primary` | `uiSettingsTextPrimary` → `uiTextPrimary` |
| 5 | `ui.settings.text.muted` | `ui.text.muted` | `uiSettingsTextMuted` → `uiTextMuted` |
| 6 | `ui.settings.text.secondary` | `ui.text.secondary` | `uiSettingsTextSecondary` → `uiTextSecondary` |
| 7 | `ui.settings.accent` | `ui.accent` | `uiSettingsAccent` → `uiAccent` |
| 8 | `ui.settings.value.text` | `ui.value.text` | `uiSettingsValueText` → `uiValueText` |
| 9 | `ui.settings.text.scrim` | `ui.text.scrim` | `uiSettingsTextScrim` → `uiTextScrim` |
| 10 | `ui.settings.divider` | `ui.divider.color` | `uiSettingsDivider` → `uiDividerColor` |
| 11 | `ui.settings.switch.track.inactive` | `ui.switch.track.inactive` | `uiSettingsSwitchTrackInactive` → `uiSwitchTrackInactive` |
| 12 | `ui.settings.input.border` | `ui.input.border` | `uiSettingsInputBorder` → `uiInputBorder` |
| 13 | `ui.settings.footer.text` | `ui.footer.text` | `uiSettingsFooterText` → `uiFooterText` |
| 14 | `ui.settings.danger` | `ui.danger` | `uiSettingsDanger` → `uiDanger` |

**#10 note:** the identifiers do *not* collide — `uiDividerGap`/`uiDividerHeight` are distinct names.
The reason for `ui.divider.color` is namespace clarity: a bare `ui.divider` key would sit above
`ui.divider.height` / `ui.divider.gap` in `ui.properties` and read as a namespace prefix rather than a
single colour. Hence the explicit `.color` segment.

**Not renamed:** `uiCardBackground` (`ui.card.background`) — already neutral. Strings are **not**
touched (settings-only copy keeps its prefix). The only settings-*namespaced*-but-shared string,
`settings_autoshow_master_label` (EN "Auto-show zones" / FR "Réaffichage des zones" — already sentence
case, so **not** a casing offender, only a mis-namespaced one), is left alone by decision.

## 2. Implementation steps

1. **[`colors.properties`](../../app/src/main/assets/colors.properties)** — rename the 14 keys
   (`:63`, `:65`, `:67`, `:149`, `:151`, `:153`, `:155`, `:157`, `:159`, `:161`, `:163`, `:165`,
   `:167`, `:169`). Keep the alias-interpolated values verbatim (`${semantic.info}`,
   `${semantic.inactive}`, `${semantic.danger}`) and update **both** section comments
   ([`:61`](../../app/src/main/assets/colors.properties:61) and `:147`).
2. **[`AppConfig.kt`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt)** — rename the
   14 `var`s (`:207`–`:382`), their KDoc text ("Set via `ui.settings.…`" → new key), the
   `── Settings panel colours ──` group comment, and the parse block reads (`:745`–`:747`,
   `:809`–`:821`).
3. **Call sites** — global rename `AppConfig.uiSettings<X>` → `AppConfig.ui<X>` across ~20 files
   (all imports/queries stay valid; no import changes needed since `AppConfig` is the same symbol).
4. **Doc/KDoc references** — comments that name the tokens literally:
   - [`DrawerScaffold.kt`](../../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt:61)
     KDoc (`:61`–`:63`, `:124`).
   - [`docs/ui-component-guidelines.md`](../../docs/ui-component-guidelines.md:113) (`:113,200,235,251,266-270,294-296,315`).
   - [`docs/ui-drawer-guidelines.md`](../../docs/ui-drawer-guidelines.md:152) (`:152-153,207-210,245-246,269-312,330-335,386`).
   - [`docs/ui-lists-guidelines.md`](../../docs/ui-lists-guidelines.md:239) (`:239-240,304,318,344,409-411,437-439`).
   - [`xTrack/Ui_Menu/FEAT_DOC_Ui_Menu_decisions.md`](../Ui_Menu/FEAT_DOC_Ui_Menu_decisions.md:25) (`:25-26`) and
     [`xTrack/Markers/260625_FEAT_PLN_Markers_wizard-drawerslot-separation.md`](../Markers/260625_FEAT_PLN_Markers_wizard-drawerslot-separation.md:36).
   - Then grep for residual `uiSettings` / `ui.settings` text.
5. **[`docs/color-scheme.md`](../../docs/color-scheme.md)** — re-sync listed `ui.settings.*` keys.
   ⚠️ [`docs/color-scheme.md:278`](../../docs/color-scheme.md:278) already lists a **stale**
   `ui.settings.danger` value (`#FFE53935` vs actual `#CCB71C1C`) — fix it while there, don't carry it forward.
6. **Build & verify:** `apk-build.bat` → BUILD SUCCESSFUL; grep `uiSettings` and `ui.settings` should
   return **zero** hits in `app/src` (source + assets + docs).

## 3. Verification checklist

- [x] Zero `uiSettings` occurrences remain in `app/src/main/java`.
- [x] Zero `ui.settings.` occurrences remain in `app/src/main/assets` + the docs in §2.4 (historical `xTrack/**` and `plans/**` excluded).
- [x] Grep is scoped to `app/src` — `app/build/intermediates/…/mergeDebugAssets/colors.properties` still carries the old keys and is not source.
- [x] `colors.properties` still holds the same 14 colours (aliases intact).
- [x] `AppConfig` defaults unchanged (a missing key still falls back to the same value).
- [x] No string resources renamed.
- [x] `apk-build.bat` → BUILD SUCCESSFUL.
- [x] On-device spot check: settings, menu, drawers, wizards, dialogs render identically.

**Outcome (2026-09-11):** implemented on `feature/menu-twks`; ~26 files renamed; 0 `uiSettings`/`ui.settings` hits in `app/src`; build SUCCESSFUL; Ask review PASS. Optional A3 (`AppConfig.kt` KDoc prose) outstanding.

## 4. Files touched

| File | Change |
|------|--------|
| `app/src/main/assets/colors.properties` | 14 keys renamed (+ both section comments) |
| `app/src/main/java/ykws/android/maro/config/AppConfig.kt` | 14 vars, KDocs, parse block |
| ~20 Kotlin files (`ui/components`, `ui/map`, `ui/markers/wizard`, `MainActivity`, …) | `AppConfig.uiSettings*` → `AppConfig.ui*` call sites |
| `docs/color-scheme.md` (+ ui-component/ui-drawer/ui-lists guidelines + FEAT_DOC_Ui_Menu_decisions + plans/wizard-drawerslot-separation-plan) | token-key references resynced |

## 5. Anti-scope (do NOT do)

- No colour/size value changes — rename only.
- No string-resource renames.
- No composable renames (already done in the Ui_Menu migration).
- No new tokens, no palette restructuring.

## 6. Residual risks (from the 2026-09-11 Ask review)

1. `ui.settings.*` survives in 3 guideline docs, `FEAT_DOC_Ui_Menu_decisions.md` and ~30 historical xTrack plans → A5/A6 scope the sweep explicitly; the "zero in docs" check cannot pass otherwise.
2. `docs/color-scheme.md:278` carries a pre-existing wrong danger value; the resync must fix rather than propagate it.
3. Doc/KDoc sweep has a wide blast radius — underestimating it leaves dead references behind.
