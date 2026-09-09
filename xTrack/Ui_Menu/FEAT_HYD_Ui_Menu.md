# Ui_Menu — Hydration

**Session:** menu-render-upt — planned + finalized (Ask-reviewed A1–A7). Branch `feature/menu-render-upt` created from origin/develop. Plan: normalize Menu drawer rendering to Settings theme (shared SectionHeader/Card/SectionDivider + token spacing, uniform 48dp rows, title-case strings). Not yet implemented.

**State:**
- `toggle-zones-marker-in-menu [x]` — implemented (prior session)
- `dashboard-clickability-reorder [x]` — implemented

**Key Files:**
- `MenuDrawerOverlay.kt` — drawer content to be rewritten onto shared Settings stencils
- `docs/ui-drawer-guidelines.md` — §8 56dp override to be removed; divider bullet to be scoped to §9
- `docs/ui-component-guidelines.md` — §2.0/§5.1 to be reconciled

**Plan:**
- `xTrack/Ui_Menu/260909_FEAT_PLN_Ui_Menu_menu-render-upt.md` — menu drawer → Settings theme normalization (finalized, Ask-reviewed)
- `xTrack/Ui_Menu/260718_FEAT_PLN_Ui_Menu_dashboard-clickability-reorder.md`
