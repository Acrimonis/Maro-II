# Translation Survey — Hardcoded String Inventory

**Feature:** ui-small-potatoes → translation
**Branch:** `feature/localization`
**Created:** 2026-07-06

## Scope

Audit of all .kt source files for hardcoded user-facing strings that should be
in `strings.xml` with French translations. Excludes: `Log.d()` calls, code comments,
variable names, regex patterns, and emoji-only strings (🕐 etc. which are universal).

## Already Localized (✅)

The codebase is ~80% localized. These areas consistently use `stringResource()`:

| Area | Files | Coverage |
|------|-------|----------|
| Settings page | `MapScreen.kt` GeneralSettings, NavigationSettings, SystemSettings | 100% `stringResource` |
| Dashboard tiles | `DashboardPanel.kt` | 100% `stringResource` |
| Dialogs (battery, recovery, bg location) | `MapScreen.kt`, `MainActivity.kt` | 100% `stringResource` |
| Track stats | `TrackHistoryOverlay.kt` | 100% `stringResource` |
| Exit warnings | `MapScreen.kt` | 100% `stringResource` |

## Hardcoded — Priority 1 (Section Headers & Labels)

Most visible strings — always on screen in menu drawer.

| # | File | Line(s) | Current Text | Proposed Key |
|---|------|---------|-------------|-------------|
| 1 | `MenuDrawerOverlay.kt` | 124 | `"POSITION SOURCE"` | `menu_section_position` |
| 2 | `MenuDrawerOverlay.kt` | 172 | `"TRACKS"` | `menu_section_tracks` |
| 3 | `MenuDrawerOverlay.kt` | 260 | `"MARKERS"` | `menu_section_markers` |
| 4 | `MenuDrawerOverlay.kt` | 219 | `"Manage Tracks"` | `menu_manage_tracks` |
| 5 | `MenuDrawerOverlay.kt` | 306 | `"Show Zones on Map"` | `menu_show_zones` |
| 6 | `MenuDrawerOverlay.kt` | 337 | `"Manage Markers"` | `menu_manage_markers` |
| 7 | `MenuDrawerOverlay.kt` | 240 | `"● Recording"` | `track_status_recording` |
| 8 | `MenuDrawerOverlay.kt` | 240 | `"● Idle"` | `track_status_idle` |
| 9 | `MenuDrawerOverlay.kt` | 240 | `"State"` | `track_stat_state` |
| 10 | `MenuDrawerOverlay.kt` | 241 | `"Elapsed"` | `track_stat_elapsed` |
| 11 | `MenuDrawerOverlay.kt` | 242 | `"Points"` | `track_stat_points` |
| 12 | `MenuDrawerOverlay.kt` | 243 | `"Distance"` | `track_stat_distance` |
| 13 | `MenuDrawerOverlay.kt` | 244 | `"Max Speed"` | `track_stat_max_speed` |
| 14 | `MenuDrawerOverlay.kt` | 245 | `"Avg Speed"` | `track_stat_avg_speed` |
| 15 | `MenuDrawerOverlay.kt` | 246 | `"Idle"` | `track_stat_idle` |

**FR translations:**
| Key | EN | FR |
|-----|----|----|
| `menu_section_position` | POSITION SOURCE | SOURCE DE POSITION |
| `menu_section_tracks` | TRACKS | TRACES |
| `menu_section_markers` | MARKERS | REPÈRES |
| `menu_manage_tracks` | Manage Tracks | Gérer les traces |
| `menu_show_zones` | Show Zones on Map | Afficher les zones |
| `menu_manage_markers` | Manage Markers | Gérer les repères |
| `track_status_recording` | ● Recording | ● Enregistrement |
| `track_status_idle` | ● Idle | ● En attente |
| `track_stat_state` | State | État |
| `track_stat_elapsed` | Elapsed | Écoulé |
| `track_stat_points` | Points | Points |
| `track_stat_distance` | Distance | Distance |
| `track_stat_max_speed` | Max Speed | Vitesse max |
| `track_stat_avg_speed` | Avg Speed | Vitesse moy |
| `track_stat_idle` | Idle | Arrêt |

## Hardcoded — Priority 2 (Drawer/Dialog UI)

| # | File | Line(s) | Current Text | Proposed Key |
|---|------|---------|-------------|-------------|
| 16 | `MarkerDrawer.kt` | 364 | `"Delete Marker"` | `marker_delete_title` |
| 17 | `MarkerDrawer.kt` | 365 | `"Delete \"%1$s\"? This cannot be undone."` | `marker_delete_confirm` |
| 18 | `MarkerDrawer.kt` | 365 | `"this marker"` (fallback) | `marker_unnamed` |
| 19 | `MarkerDrawer.kt` | 371 | `"Delete"` | `action_delete` |
| 20 | `MarkerDrawer.kt` | 376 | `"Cancel"` | `action_cancel` |
| 21 | `MarkerDrawer.kt` | 548 | `"Marker Color"` | `marker_color_title` |
| 22 | `IconPickerDialog.kt` | 60 | `"Marker Icon"` | `marker_icon_title` |
| 23 | `IconPickerDialog.kt` | 106 | `"None (✕)"` | `marker_icon_none` |
| 24 | `IconPickerDialog.kt` | 111 | `"Cancel"` | `action_cancel` |
| 25 | `MarkerManagementOverlay.kt` | 116 | `"No markers yet"` | `marker_empty` |
| 26 | `MarkerManagementOverlay.kt` | 123 | `"Create First Marker"` | `marker_create_first` |
| 27 | `ListOverlayScaffold.kt` | 130 | `"General"` | `filter_section_general` |
| 28 | `ListOverlayScaffold.kt` | 477 | `"No items match filters"` | `filter_no_match` |
| 29 | `ListOverlayScaffold.kt` | 480 | `"Clear filters"` | `filter_clear` |
| 30 | `MapScreen.kt` | 4906 | `"Pick color"` | `color_picker_title` |
| 31 | `MapScreen.kt` | 3549 | `"Depth"` | `settings_depth_label` |
| 32 | `MapScreen.kt` | 3550 | `"Show depth color layer on the map"` | `settings_depth_desc` |
| 33 | `MapScreen.kt` | 4538 | `"Regenerate"` | `action_regenerate` |

**FR translations:**
| Key | EN | FR |
|-----|----|----|
| `marker_delete_title` | Delete Marker | Supprimer le repère |
| `marker_delete_confirm` | Delete "%1$s"? This cannot be undone. | Supprimer "%1$s" ? Action irréversible. |
| `marker_unnamed` | this marker | ce repère |
| `action_delete` | Delete | Supprimer |
| `action_cancel` | Cancel | Annuler |
| `marker_color_title` | Marker Color | Couleur du repère |
| `marker_icon_title` | Marker Icon | Icône du repère |
| `marker_icon_none` | None (✕) | Aucune (✕) |
| `marker_empty` | No markers yet | Aucun repère |
| `marker_create_first` | Create First Marker | Créer un repère |
| `filter_section_general` | General | Général |
| `filter_no_match` | No items match filters | Aucun résultat |
| `filter_clear` | Clear filters | Effacer les filtres |
| `color_picker_title` | Pick color | Choisir une couleur |
| `settings_depth_label` | Depth | Profondeur |
| `settings_depth_desc` | Show depth color layer on the map | Afficher les fonds marins |
| `action_regenerate` | Regenerate | Régénérer |

## Hardcoded — Priority 3 (Content Descriptions / Accessibility)

| # | File | Current Text | Proposed Key |
|---|------|-------------|-------------|
| 34 | `WizardTopBar.kt` | `"Cancel"` | `cd_cancel` |
| 35 | `DrawerScaffold.kt` | `"Close"` | `cd_close` |
| 36 | `MenuDrawerOverlay.kt` | `"Settings"` | `cd_settings` |
| 37 | `MenuDrawerOverlay.kt` | `"Reset track filter"` | `cd_reset_filter` |
| 38 | `MenuDrawerOverlay.kt` | `"View track list"` | `cd_view_tracks` |
| 39 | `MenuDrawerOverlay.kt` | `"Reset marker filter"` | `cd_reset_filter` |
| 40 | `MenuDrawerOverlay.kt` | `"Manage markers"` | `cd_manage_markers` |
| 41 | `MarkerDrawer.kt` | `"Set icon"` | `cd_set_icon` |
| 42 | `MarkerDrawer.kt` | `"Edit"` | `cd_edit` |
| 43 | `MarkerDrawer.kt` | `"Delete"` | `cd_delete` |
| 44 | `MarkerManagementOverlay.kt` | `"Set icon"` | `cd_set_icon` |
| 45 | `MarkerManagementOverlay.kt` | `"Edit"` | `cd_edit` |
| 46 | `MarkerManagementOverlay.kt` | `"View marker"` | `cd_view_marker` |
| 47 | `TrackHistoryOverlay.kt` | `"Export GPX"` | `cd_export_gpx` |
| 48 | `TrackHistoryOverlay.kt` | `"View track"` | `cd_view_track` |
| 49 | `ListOverlayScaffold.kt` | `"Sort by"` | `cd_sort` |
| 50 | `ListOverlayScaffold.kt` | `"Filter"` | `cd_filter` |
| 51 | `ListOverlayScaffold.kt` | `"Reset"` | `cd_reset` |
| 52 | `MapScreen.kt` | `"Menu"` | `cd_menu` |
| 53 | `TypeSelectStep.kt` | `"Pin this marker"` | `cd_pin_marker` |

## Hardcoded — Priority 4 (Misc / Edge Cases)

| # | File | Current Text | Issue |
|---|------|-------------|-------|
| 54 | `DashboardPanel.kt` | `"EMODnet"`, `"GEBCO"`, `"—"` | Depth source labels — `EMODnet` and `GEBCO` are proper nouns (no translation), but `"—"` could use a resource |
| 55 | `MapScreen.kt` | `"English"` | Language selector — should use `stringResource` for consistency |
| 56 | `ListOverlayScaffold.kt` | `"\"%s\" deleted"`, `"Undo"` | Snackbar messages |
| 57 | `RegulatedZoneComponents.kt` | `"10"` | Boat size slider min label — likely a numeric constant, not user-facing text |

## Files Requiring No Changes

These already use `stringResource` consistently or contain no user-facing strings:
- `MainActivity.kt` — already localized except `rememberLocalizedContext` (infrastructure)
- `TrackHistoryOverlay.kt` — stats already use `stringResource`
- `DashboardPanel.kt` — tiles already use `stringResource` (except source labels)
- `MapScreen.kt` — Settings, dialogs, toasts all use `stringResource`
- `NavigationViewModel.kt` — no user-facing strings
- `SettingsManager.kt` — no user-facing strings

## Implementation Order

1. **Priority 1** — Menu drawer section headers + labels + live stats (15 items). Most visible, always on screen.
2. **Priority 2** — Drawer/dialog titles and messages (18 items). High interaction frequency.
3. **Priority 3** — Content descriptions / accessibility (20 items). Screen-reader impact.
4. **Priority 4** — Misc edge cases (4 items). Low impact.

## Key Files Modified

| File | Change |
|------|--------|
| `app/src/main/res/values/strings.xml` | Add ~55 new string resources (EN) |
| `app/src/main/res/values-fr/strings.xml` | Add ~55 French translations |
| `MenuDrawerOverlay.kt` | Replace 15 hardcoded strings with `stringResource()` |
| `MarkerDrawer.kt` | Replace 6 hardcoded strings |
| `IconPickerDialog.kt` | Replace 3 hardcoded strings |
| `MarkerManagementOverlay.kt` | Replace 2 hardcoded strings |
| `ListOverlayScaffold.kt` | Replace 5 hardcoded strings |
| `MapScreen.kt` | Replace 4 hardcoded strings |
| `WizardTopBar.kt` | Replace 1 hardcoded string |
| `DrawerScaffold.kt` | Replace 1 hardcoded string |
| `TypeSelectStep.kt` | Replace 1 hardcoded string |
| `TrackHistoryOverlay.kt` | Replace 2 hardcoded strings |
| `DashboardPanel.kt` | Replace 1 hardcoded string (dash) |
