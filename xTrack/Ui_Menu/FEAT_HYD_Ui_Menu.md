# Ui_Menu — Hydration

**Session:** menu-render-upt — implemented on `feature/menu-twks`. Menu drawer rendering normalized to the
Settings render model: shared `SectionHeader` / `CardArea` / `SectionDivider` / `ToggleRow` extracted to
`ui/components`, token spacing (header→card 6dp, section→section 14dp), 48dp-min rows, sentence-case strings
(`settings_section_*` reused; `menu_section_*` + `menu_import_export` deleted). Build green.

**Branch:** `feature/menu-twks` — resumed 2026-09-11; same branch also carries the ColorManagement token rename.

**State:**
- `menu-render-upt [x]` — implemented (this session)
- `toggle-zones-marker-in-menu [x]` — implemented (prior session)
- `dashboard-clickability-reorder [x]` — implemented

**Key Files:**
- `ui/components/SectionHeader.kt`, `SectionDivider.kt`, `CardArea.kt`, `ToggleRow.kt` — extracted stencils
- `MenuDrawerOverlay.kt` — body rewritten onto the shared stencils + tokens
- `MapScreenSettingsOverlay.kt` — originals removed; 15 `Card`→`CardArea` call sites + KDoc refs
- `docs/ui-drawer-guidelines.md` — §8 56dp override removed; 8dp inter-card gap retired; divider bullet scoped to §9
- `docs/ui-component-guidelines.md` — §2.0 example removed, §3 gap row dropped, §5.1 rewritten

**Plan:**
- `xTrack/Ui_Menu/260909_FEAT_PLN_Ui_Menu_menu-render-upt.md` — menu drawer → Settings theme normalization (implemented)
- `xTrack/ColorManagement/260911_FEAT_PLN_ColorManagement_ui-token-de-settings.md` — follow-on token rename
- `xTrack/Ui_Menu/260718_FEAT_PLN_Ui_Menu_dashboard-clickability-reorder.md`
