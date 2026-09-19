---
name: Ui_General
status: active
created: 2026-06-08 16:43
modified: 2026-09-17 14:27
---

# Feature: Ui_General

**Description:**
App-lifecycle UX for the Maro-II app: back-exit guard, edge-to-edge rendering, WindowInsets management, list normalization, drawer framework, and menu-drawer UX. (Keep-screen-on moved to the Performance feature 2026-09-12.)

## Sections

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
- **string-extraction (2026-09-19)** — the standing rule that no user-facing text is a literal, applied in one pass: `FilterOptionSpec` / `FilterAxisSpec` gained `labelResId` (the `CustomSortField` shape) with all nineteen filter labels read through `stringResource`, the sort-group default followed as an id, and every other label, title, section caption, wizard line, notification line, compass letter and depth phase moved into both locale files — 84 new keys with the French written for each surface, 15 existing keys reused rather than duplicated, and the notification resolved through the service's own context; the rule itself now sits in `AGENTS.md` §1, and the six Previous/Next literals in `OverlayLayer` closed with it; `apk-build.bat` SUCCESS with the scoped run at 380 tests and only the known reds → `xTrack/Ui_General/260919_FEAT_PLN_Ui_General_string-extraction.md`

- **dashboard-close-conditions** — the selected-item dashboard (marker detail, track detail) closes on exactly two conditions: a surface wanting its slot (the wizard, the other selected-item dashboard) or a change of the world its Prev/Next walks. The menu, settings and both lists keep the selection — the four detail slots stand down while a panel is open — the one-item guard lives inside the openers, and the ten referential callbacks close through two named helpers → `xTrack/Ui_General/260917_FEAT_PLN_Ui_General_dashboard-close-conditions.md`

- **track-filter-date-range** — Track Date Range filter extended+reordered: 7 options shortest→longest (Last week → Last 6 month) with All last + default; THIS_YEAR dropped → `xTrack/Ui_General/260907_FEAT_PLN_Ui_General_track-filter-date-range.md`
- **marker-filter-remove-geometry** — markers filter no longer exposes a Geometry (Pins/Circles/Corridors) axis; icon/pinned/origin remain, origin ungated → `xTrack/Ui_General/260907_FEAT_PLN_Ui_General_marker-filter-remove-geometry.md`
- **list-count-display** — filtered item counts: Track History title "· N" + menu Tracks/Markers row counts left of chevron, live track excluded → `xTrack/Ui_General/260907_FEAT_PLN_Ui_General_list-count-display.md`
- **compact-list-cards** — tighter list cards (14sp desc, reduced padding, no header→title divider)
- **landscape-drawer-settings-sizing** — landscape panels open at portrait widths (menu 75% / settings full short edge), shared scrim → `xTrack/Ui_General/260904_FEAT_PLN_Ui_General_landscape-drawer-settings-sizing.md`
- **scrim-strengths-and-dashboard-close** — unified 0.50 scrim on menu/settings/lists only; the fan keeps the selected-item dashboard open. The menu arm is superseded 2026-09-17 (see `## Rules`) → `xTrack/Ui_General/260904_FEAT_PLN_Ui_General_scrim-strengths-and-dashboard-close.md`
- **drawer-dynamic-height** — bottom-anchored drawers with card-height probe + animated height
- **drawer-vertical-rhythm** — uniform 12dp card padding / header vpad / footer rhythm
- **touch-lock** — 📵 splash guard blocking accidental touches (LockScrim + unlock toggle + zoom gated; `status.lock.*` tokens). Renamed from "screen-lock" 2026-09-12 so `screen lock` is free for the device-timeout feature owned by Performance → `xTrack/Ui_General/260827_FEAT_PLN_Ui_General_touch-input-lock.md`
- **menu-drawer-rows** — "Tracks"/"Markers" rows; chevron opens first filtered/sorted item → `xTrack/Ui_General/260816_FEAT_PLN_Ui_General_menu-drawer-rows.md`
- **delete-advance-next** — drawer delete advances to adjacent item + snackbar undo stack → `xTrack/Ui_General/260816_FEAT_PLN_Ui_General_delete-advance-next.md`
- **top-left-icons** — GPS→tracking→land/water order; GPS click-to-toggle; 🐾 icon; red idle dot → `xTrack/Ui_General/260816_FEAT_PLN_Ui_General_top-left-icons-reorder.md`
- **landscape-menu-drawer** — scroll-when-overflow, overscroll suppressed when fits → `xTrack/Ui_General/260816_FEAT_PLN_Ui_General_landscape-menu-drawer.md`
- **notification-lifecycle** — foreground notification follows recording state; recorder + GPS moved into service → `xTrack/Ui_General/260815_FEAT_PLN_Ui_General_notification-lifecycle.md`
- **filter** — extensible `ListFilter` (tracks=date+pinned, markers=pinned+geometry+origin), sort UX normalized → `xTrack/Ui_General/260702_FEAT_PLN_Ui_General_filter.md`
- **filter everywhere** — map mirrors filtered list; fan binary ON/OFF → `xTrack/Ui_General/260702_FEAT_PLN_Ui_General_filter-everywhere.md`
- **BackToExitConfirm** — double-back-to-exit guard
- **KeepScreenOn** — keep-screen-on setting (ownership moved to Performance 2026-09-12: the policy and keeper now live in `data/power/` and the setting is documented in `xTrack/Performance/FEAT_DSC_Performance.md`)
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
- The selected-item dashboard — the marker detail drawer and the track detail drawer — closes on exactly two conditions: a surface that wants its own slot (the marker/track wizard, the other selected-item dashboard), or a change of the scope of the world its Prev/Next walks.
- The menu, settings, track history and marker management are panels over the map: they never close it, and the selection returns when they close. Every other action — the layer fan, displays, zoom, lock, gestures — leaves it open.
- Action table, code sites and the open checks: `xTrack/Ui_General/260917_FEAT_PLN_Ui_General_dashboard-close-conditions.md`.

## Key Files

## Docs
- `docs/ui-lists-guidelines.md` — ListOverlayScaffold API, filter system, swipe-to-delete
- `docs/ui-component-guidelines.md` — canonical UI component patterns
- `docs/ui-drawer-guidelines.md` — DrawerScaffold API
- `xTrack/Ui_General/260701_FEAT_PLN_Ui_General_listable-item-interface.md` — ListableItem migration plan (marker + track lists under one interface)
- `docs/material-icons-standalone-guide.md` — standalone icon registry
- `docs/color-scheme.md` — canonical colour tokens
- `xTrack/UI_Map/260616_FEAT_PLN_UI_Map_map-overlay-layout-rationalization.md` — 2-column Row layout refactor
- `xTrack/UI_Map/260616_FEAT_PLN_UI_Map_map-overlay-layout-inventory.md` — overlay inventory
