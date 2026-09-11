# Hydration: ColorManagement

**Last Bake:** 2026-09-11 15:12 UTC
**State:** Session complete — `ui-token-de-settings` implemented and building green (branch `feature/menu-twks`).

## Session Summary

De-settings-ified the 14 shared UI colour tokens (rename only — zero visual diff):
1. `colors.properties` — 14 keys renamed (`ui.settings.*` → `ui.*`, with `ui.settings.divider` → `ui.divider.color`); both section comments neutralised; alias-interpolated values untouched
2. `AppConfig.kt` — 14 accessors + KDocs + parse keys/assignments renamed; group comments neutralised
3. ~26 Kotlin files swept for `AppConfig.uiSettings*` → `AppConfig.ui*` call sites
4. Doc/KDoc sweep — ui-component / ui-drawer / ui-lists guidelines, `docs/color-scheme.md` (prefix line → `ui.*`, stale `ui.danger` value corrected to `#CCB71C1C`), `FEAT_DOC_Ui_Menu_decisions.md`, `plans/wizard-drawerslot-separation-plan.md`
5. Verified `uiSettings` / `ui.settings` = 0 hits in `app/src` (excl. `app/build/`); `apk-build.bat` BUILD SUCCESSFUL

## Target Files
- `app/src/main/assets/colors.properties`
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`
- `docs/color-scheme.md`, `docs/ui-component-guidelines.md`, `docs/ui-drawer-guidelines.md`, `docs/ui-lists-guidelines.md`
- `xTrack/ColorManagement/260911_FEAT_PLN_ColorManagement_ui-token-de-settings.md`

## Prior Session (2026-06-17)
Colour centralization pass: 46 hardcoded `ComposeColor.White` refs replaced, 12 stale `AppConfig` defaults synced, orphaned `zone.properties` colour fields removed, zone.properties merged into maro.properties and deleted.

## Next Step
On-device spot check (settings, menu, drawers, wizards, dialogs render identically), then commit.
