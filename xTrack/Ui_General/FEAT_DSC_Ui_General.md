---
name: Ui_General
status: active
created: 2026-06-08 16:43
modified: 2026-09-04 21:04
---

# Feature: Ui_General

**Description:**
App-lifecycle UX for the Maro-II app: back-exit guard, keep-screen-on, edge-to-edge rendering, WindowInsets management, list normalization, drawer framework, and menu-drawer UX.

## Sections

### map-print-layout

#### Todos

### track list colors

Track list (TrackHistoryOverlay) color review — ensure track cards/stats/labels/icons use correct `ui.card.background` + `colors.properties` tokens.

#### Todos

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt`

### multi-select

Long-press multiselect mode on list items: scaffold owns selection state + contextual bottom action bar; consumer-injected multi-actions (batch delete/export/pin).

#### Docs
- `xTrack/Ui_General/260712_FEAT_PLN_Ui_General_multiselect-list-plan.md`

## Implemented

- **compact-list-cards** — tighter list cards (14sp desc, reduced padding, no header→title divider)
- **landscape-drawer-settings-sizing** — landscape panels open at portrait widths (menu 75% / settings full short edge), shared scrim → `xTrack/Ui_General/260904_FEAT_PLN_Ui_General_landscape-drawer-settings-sizing.md`
- **scrim-strengths-and-dashboard-close** — unified 0.50 scrim on menu/settings/lists only; menu/fan open closes dashboard → `xTrack/Ui_General/260904_FEAT_PLN_Ui_General_scrim-strengths-and-dashboard-close.md`
- **drawer-dynamic-height** — bottom-anchored drawers with card-height probe + animated height
- **drawer-vertical-rhythm** — uniform 12dp card padding / header vpad / footer rhythm
- **screen-lock** — 📵 splash guard (LockScrim + unlock toggle + zoom gated) → `xTrack/Ui_General/260827_FEAT_PLN_Ui_General_touch-input-lock.md`
- **menu-drawer-rows** — "Tracks"/"Markers" rows; chevron opens first filtered/sorted item → `xTrack/Ui_General/260816_FEAT_PLN_Ui_General_menu-drawer-rows.md`
- **delete-advance-next** — drawer delete advances to adjacent item + snackbar undo stack → `xTrack/Ui_General/260816_FEAT_PLN_Ui_General_delete-advance-next.md`
- **top-left-icons** — GPS→tracking→land/water order; GPS click-to-toggle; 🐾 icon; red idle dot → `xTrack/Ui_General/260816_FEAT_PLN_Ui_General_top-left-icons-reorder.md`
- **landscape-menu-drawer** — scroll-when-overflow, overscroll suppressed when fits → `xTrack/Ui_General/260816_FEAT_PLN_Ui_General_landscape-menu-drawer.md`
- **notification-lifecycle** — foreground notification follows recording state; recorder + GPS moved into service → `xTrack/Ui_General/260815_FEAT_PLN_Ui_General_notification-lifecycle.md`
- **filter** — extensible `ListFilter` (tracks=date+pinned, markers=pinned+geometry+origin), sort UX normalized → `xTrack/Ui_General/260702_FEAT_PLN_Ui_General_filter.md`
- **filter everywhere** — map mirrors filtered list; fan binary ON/OFF → `xTrack/Ui_General/260702_FEAT_PLN_Ui_General_filter-everywhere.md`
- **BackToExitConfirm** — double-back-to-exit guard
- **KeepScreenOn** — keep-screen-on setting
- **page layout** — `enableEdgeToEdge()` + status-bar immersion + WindowInsets
- **immersive ui rework** — targeted insets only on overlays; map fills full screen
- **tweak drawer** — `DrawerScaffold`/`DrawerHeader` shared components → `xTrack/Ui_General/260703_FEAT_PLN_Ui_General_tweak-drawer.md`
- **list sort** — `ListOverlayScaffold` generic scaffold (sort/filter/swipe/undo) + `ListAction` contract
- **list extra sort** — `CustomSortField` per-type sort fields → `xTrack/Ui_General/260702_FEAT_PLN_Ui_General_list-extra-sort.md`
- **reg speed zone** — regulated-zone auto-show on approach (distance/time threshold) → `xTrack/ZoneTile/260617_FEAT_PLN_ZoneTile_speed-enforcement-zone-auto-show-plan.md`
- **fan tweak** — scrim removed, MapView touch listener dismisses fan
- **toast & progress dialog** — bottom overlays full-width (padding 6dp, LoadingOverlay full)
- **overlay styling** — toast/Loading/Error unified navy card (blue/red borders)
- **click-N-move** — marker tap closes list, centers map, opens Viewing drawer → `xTrack/Ui_General/260705_FEAT_PLN_Ui_General_click-n-move.md`
- **translation** — 84 FR strings; 14 files localized → `xTrack/Ui_General/260706_FEAT_PLN_Ui_General_translation-survey.md`
- **menu** — drawer menu items wrapped in card backgrounds

## Rules

## Key Files

## Docs
- `docs/ui-lists-guidelines.md` — ListOverlayScaffold API, filter system, swipe-to-delete
- `docs/ui-component-guidelines.md` — canonical UI component patterns
- `docs/ui-drawer-guidelines.md` — DrawerScaffold API
- `docs/material-icons-standalone-guide.md` — standalone icon registry
- `docs/color-scheme.md` — canonical colour tokens
- `xTrack/UI_Map/260616_FEAT_PLN_UI_Map_map-overlay-layout-rationalization.md` — 2-column Row layout refactor
- `xTrack/UI_Map/260616_FEAT_PLN_UI_Map_map-overlay-layout-inventory.md` — overlay inventory
