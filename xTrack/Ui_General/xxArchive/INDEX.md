<!-- scope: feature -->
# Ui_General — Archived Plans

Retired plans for the Ui_General feature. This folder is read only by `#archive`; every other
feature-summarising command excludes it. Row form and the digest floor live in
[`docs/cmd_help_archive.md`](../../../docs/cmd_help_archive.md:1).

| File | Created | Archived | Status | Summary | Tags | Superseded-by |
|------|---------|----------|--------|---------|------|---------------|
| `260928_FEAT_PLN_Ui_General_map-cards-and-the-filter.md` | 2026-09-28 | 2026-09-28 | shipped | One rule for every card the map opens: the map draws its filter's set alone (reveal, highlighted escape and pinned carve-out deleted), R2's map-world close left the map-opened card, and the dead `WHERE_AM_I` went. Its behaviour is promoted into `FEAT_DSC_Ui_General.md` `## Rules`. | `ui`, `map`, `filter` | — |
| `260928_FEAT_PLN_Ui_General_edit-return-and-advance.md` | 2026-09-28 | 2026-09-28 | shipped | The editor hands its card back and an item's departure advances, through the one pure `advanceAfterDeparture` shared by both cards. Its behaviour is promoted into `FEAT_DSC_Ui_General.md` `## Rules`. | `ui`, `markers`, `dashboard` | — |
| `261021_FEAT_PLN_Ui_General_wheel-flick-stays-open.md` | 2026-10-21 | 2026-10-21 | shipped | The wheel splits its release by velocity: a flick keeps the popup open and browses, a slow lift commits and closes. | `ui`, `wheel` | — |
| `261017_FEAT_PLN_Ui_General_settings-slider-normalization.md` | 2026-10-17 | 2026-10-21 | shipped | One `SliderRow` for every settings slider, value bottom-aligned, `*_fmt` suffixes. | `ui`, `settings` | — |
| `261017_FEAT_PLN_Ui_General_wheel-commit-on-release.md` | 2026-10-17 | 2026-10-21 | superseded | The wheel commits on release and the popup fades/rises both ways. | `ui`, `wheel` | `261021_FEAT_PLN_Ui_General_wheel-flick-stays-open.md` |
| `261010_FEAT_PLN_Ui_General_delete-normalization.md` | 2026-10-10 | 2026-10-21 | shipped | One pending-delete set and one hiding rule across cards and list swipes. | `ui`, `lists` | — |
| `261010_FEAT_PLN_Ui_General_route-card-walk-scope.md` | 2026-10-10 | 2026-10-21 | shipped | The card's walk, delete-advance and close read its own kind. | `ui`, `lists`, `routes` | — |
| `260929_FEAT_PLN_Ui_General_filter-popup-dismissal.md` | 2026-09-29 | 2026-10-21 | shipped | A filter row writes live and the popup stays open. | `ui`, `filters` | — |
| `260929_FEAT_PLN_Ui_General_selector-entry-order.md` | 2026-09-29 | 2026-10-21 | shipped | Selector entry order: fixed first, then flagged by title; ignored prefixes. | `ui`, `lists` | — |
| `260929_FEAT_PLN_Ui_General_dropdown-wheel.md` | 2026-09-29 | 2026-10-21 | superseded | The dropdown wheel and its selection/landing fix. | `ui`, `wheel` | `261017_FEAT_PLN_Ui_General_wheel-commit-on-release.md` |
| `261005_FEAT_PLN_Ui_General_single-gesture-wheel.md` | 2026-10-05 | 2026-10-21 | superseded | One gesture, arrowless box, wheel on the box, shared text alignment. | `ui`, `wheel` | `261017_FEAT_PLN_Ui_General_wheel-commit-on-release.md` |
| `261003_FEAT_PLN_Ui_General_right-swipe-pin.md` | 2026-10-03 | 2026-10-21 | shipped | A right swipe pins a card; the left keeps delete. | `ui`, `lists` | — |
| `261001_FEAT_PLN_Ui_General_stop-detection-move.md` | 2026-10-01 | 2026-10-21 | shipped | Stop detection moved into Navigation. | `ui`, `settings` | — |
| `261001_FEAT_PLN_Ui_General_position-system-rehome.md` | 2026-10-01 | 2026-10-21 | shipped | The GPS-mode row rehomed; the Navigation group moved under System. | `ui`, `settings` | — |
| `261001_FEAT_PLN_Ui_General_navigation-route-top.md` | 2026-10-01 | 2026-10-21 | shipped | The Route section leads the Navigation tab. | `ui`, `settings` | — |
| `261001_FEAT_PLN_Ui_General_route-subsection.md` | 2026-10-01 | 2026-10-21 | shipped | Route render gates under a Route subsection. | `ui`, `settings` | — |
| `261001_FEAT_PLN_Ui_General_speed-display-subsection.md` | 2026-10-01 | 2026-10-21 | shipped | Display Tracks chips under a Speed Display subsection. | `ui`, `settings` | — |
| `261001_FEAT_PLN_Ui_General_tracks-card-tweaks.md` | 2026-10-01 | 2026-10-21 | shipped | Display Tracks moved; the chip renamed; the Route arm face. | `ui`, `settings`, `menu` | — |
| `261001_FEAT_PLN_Ui_General_menu-tracks-shuffle.md` | 2026-10-01 | 2026-10-21 | shipped | Tracks rendering and import/export moved into Settings. | `ui`, `menu`, `settings` | — |
| `260928_FEAT_PLN_Ui_General_active-action-face.md` | 2026-09-28 | 2026-10-21 | shipped | The action-face doctrine: the middle action's fill and rim. | `ui`, `buttons` | — |
| `260923_FEAT_PLN_Ui_General_dialog-option-row.md` | 2026-09-23 | 2026-10-21 | shipped | One `OptionRow` for checkbox-and-label rows. | `ui`, `dialogs` | — |
| `260923_FEAT_PLN_Ui_General_route-dialog-alignment.md` | 2026-09-23 | 2026-10-21 | shipped | The route panel rebuilt on the card lines; action roles. | `ui`, `routes`, `dialogs` | — |
| `260922_FEAT_PLN_Ui_General_filter-popup-scroll.md` | 2026-09-22 | 2026-10-21 | shipped | Popups bound their height and scroll past it. | `ui`, `filters` | — |
| `260921_FEAT_PLN_Ui_General_bottom-banner-centring.md` | 2026-09-21 | 2026-10-21 | shipped | One `MapBanner` and one clearance rule. | `ui`, `map` | — |
| `260919_FEAT_PLN_Ui_General_string-extraction.md` | 2026-09-19 | 2026-10-21 | shipped | No user-facing literal; 84 keys in both locales. | `ui`, `strings` | — |
| `260917_FEAT_PLN_Ui_General_dashboard-close-conditions.md` | 2026-09-17 | 2026-10-21 | shipped | The selected-item dashboard closes on two conditions. | `ui`, `dashboard` | — |
| `260907_FEAT_PLN_Ui_General_track-filter-date-range.md` | 2026-09-07 | 2026-10-21 | shipped | Track date-range filter: seven options, All last. | `ui`, `filters` | — |
| `260907_FEAT_PLN_Ui_General_marker-filter-remove-geometry.md` | 2026-09-07 | 2026-10-21 | shipped | The marker geometry filter axis removed. | `ui`, `filters` | — |
| `260907_FEAT_PLN_Ui_General_list-count-display.md` | 2026-09-07 | 2026-10-21 | shipped | Counts on the list title and on the menu rows. | `ui`, `lists` | — |
| `260904_FEAT_PLN_Ui_General_landscape-drawer-settings-sizing.md` | 2026-09-04 | 2026-10-21 | shipped | Landscape panels open at portrait widths. | `ui`, `drawers` | — |
| `260904_FEAT_PLN_Ui_General_scrim-strengths-and-dashboard-close.md` | 2026-09-04 | 2026-10-21 | shipped | A unified 0.50 scrim; the fan keeps the dashboard open. | `ui`, `drawers` | — |
| `260827_FEAT_PLN_Ui_General_touch-input-lock.md` | 2026-08-27 | 2026-10-21 | shipped | The splash-guard touch lock. | `ui`, `input` | — |
| `260816_FEAT_PLN_Ui_General_menu-drawer-rows.md` | 2026-08-16 | 2026-10-21 | shipped | Tracks/Markers rows open the first item. | `ui`, `menu` | — |
| `260816_FEAT_PLN_Ui_General_delete-advance-next.md` | 2026-08-16 | 2026-10-21 | shipped | Delete advances to the adjacent item; one undo stack. | `ui`, `lists` | — |
| `260816_FEAT_PLN_Ui_General_top-left-icons-reorder.md` | 2026-08-16 | 2026-10-21 | shipped | The top-left toggle order and icons. | `ui`, `map` | — |
| `260816_FEAT_PLN_Ui_General_landscape-menu-drawer.md` | 2026-08-16 | 2026-10-21 | shipped | The menu scrolls when overflowing, overscroll suppressed. | `ui`, `menu` | — |
| `260815_FEAT_PLN_Ui_General_notification-lifecycle.md` | 2026-08-15 | 2026-10-21 | shipped | The foreground notification follows the recording state. | `ui`, `notification` | — |
| `260702_FEAT_PLN_Ui_General_filter.md` | 2026-07-02 | 2026-10-21 | shipped | An extensible `ListFilter` and a normalized sort UX. | `ui`, `filters` | — |
| `260702_FEAT_PLN_Ui_General_filter-everywhere.md` | 2026-07-02 | 2026-10-21 | shipped | The map mirrors the filtered list; the fan is binary. | `ui`, `filters`, `map` | `260928_FEAT_PLN_Ui_General_map-cards-and-the-filter.md` |
| `260702_FEAT_PLN_Ui_General_list-extra-sort.md` | 2026-07-02 | 2026-10-21 | shipped | Per-type `CustomSortField` sort fields. | `ui`, `lists` | — |
| `260703_FEAT_PLN_Ui_General_tweak-drawer.md` | 2026-07-03 | 2026-10-21 | shipped | Shared `DrawerScaffold`/`DrawerHeader`. | `ui`, `drawers` | — |
| `260705_FEAT_PLN_Ui_General_click-n-move.md` | 2026-07-05 | 2026-10-21 | shipped | A marker tap closes the list, centres the map and opens the card. | `ui`, `markers` | — |
| `260706_FEAT_PLN_Ui_General_translation-survey.md` | 2026-07-06 | 2026-10-21 | shipped | 84 French strings across 14 files. | `ui`, `strings` | — |
