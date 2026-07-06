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

## Hardcoded — Priority 5 (Settings Page — Missed in Initial Pass)

Navigation tab + System tab strings that were previously missed.

| # | File | Line(s) | Current Text | Proposed Key |
|---|------|---------|-------------|-------------|
| 58 | `MapScreen.kt` | 3491 | `"Show zone info text beside the icon strip"` | `settings_reg_info_desc` |
| 59 | `MapScreen.kt` | 3502 | `"Info text visible"` | `settings_reg_info_visible` |
| 60 | `MapScreen.kt` | 3597 | `"Tracks"` | `settings_section_tracks` |
| 61 | `MapScreen.kt` | 3603 | `"Show recorded tracks on the map"` | `settings_tracks_desc` |
| 62 | `MapScreen.kt` | 3639 | `"Number of tracks"` | `settings_tracks_count_label` |
| 63 | `MapScreen.kt` | 3650 | `"Recent tracks to render (0-20)"` | `settings_tracks_count_desc` |
| 64 | `MapScreen.kt` | 3687 | `"Transparency"` | `settings_transparency_label` |
| 65 | `MapScreen.kt` | 3693 | `"Left thumb = newest track, right thumb = oldest. 0% = opaque, 100% = invisible."` | `settings_transparency_desc` |
| 66 | `MapScreen.kt` | 3698 | `"Newest %d%%  –  Oldest %d%%"` | `settings_transparency_value_fmt` |
| 67 | `MapScreen.kt` | 3735 | `"Pinned transparency"` | `settings_pinned_transparency_label` |
| 68 | `MapScreen.kt` | 3741 | `"Pinned tracks are always visible regardless of count. 0% = opaque, 100% = invisible."` | `settings_pinned_transparency_desc` |
| 69 | `MapScreen.kt` | 3746 | `"Newest %d%%  –  Oldest %d%%"` | `settings_transparency_value_fmt` (reuse) |
| 70 | `MapScreen.kt` | 3780 | `"Colors"` | `settings_colors_label` |
| 71 | `MapScreen.kt` | 3786 | `"Past tracks: color gradient from newest (From) to oldest (To). Pinned tracks: amber/orange gradient."` | `settings_colors_desc` |
| 72 | `MapScreen.kt` | 3836 | `"Markers"` | `settings_section_markers` |
| 73 | `MapScreen.kt` | 3842 | `"User-created pins, circles, corridors and auto-markers"` | `settings_markers_desc` |
| 74 | `MapScreen.kt` | 3879 | `"Show zone shapes (corridor edges, circle outlines) and proximity previews"` | `settings_zone_shapes_desc` |
| 75 | `MapScreen.kt` | 3890 | `"Zone shapes"` | `settings_zone_shapes_label` |
| 76 | `MapScreen.kt` | 4125 | `"Speed zone alert (GPS)"` | `settings_speed_alert_gps_label` |
| 77 | `MapScreen.kt` | 4131 | `"Auto-show speed zones when approaching in GPS mode"` | `settings_speed_alert_gps_desc` |
| 78 | `MapScreen.kt` | 4162 | `"Speed zone alert (Demo)"` | `settings_speed_alert_demo_label` |
| 79 | `MapScreen.kt` | 4168 | `"Auto-show speed zones when panning the map toward a zone"` | `settings_speed_alert_demo_desc` |
| 80 | `MapScreen.kt` | 4199 | `"Regulated zone alert (GPS)"` | `settings_reg_alert_gps_label` |
| 81 | `MapScreen.kt` | 4205 | `"Auto-show regulated zones when approaching a speed-enforced zone"` | `settings_reg_alert_gps_desc` |
| 82 | `MapScreen.kt` | 4236 | `"Regulated zone alert (Demo)"` | `settings_reg_alert_demo_label` |
| 83 | `MapScreen.kt` | 4242 | `"Auto-show regulated zones when panning toward a speed-enforced zone"` | `settings_reg_alert_demo_desc` |
| 84 | `MapScreen.kt` | 4494 | `"Regenerate Layers"` | `settings_regenerate_layers` |
| 85 | `MapScreen.kt` | 4857 | `"Pick"` | `color_picker_pick` |

**FR translations:**
| Key | EN | FR |
|-----|----|----|
| `settings_reg_info_desc` | Show zone info text beside the icon strip | Afficher le texte d'info à côté des icônes |
| `settings_reg_info_visible` | Info text visible | Texte d'info visible |
| `settings_section_tracks` | Tracks | Traces |
| `settings_tracks_desc` | Show recorded tracks on the map | Afficher les traces enregistrées |
| `settings_tracks_count_label` | Number of tracks | Nombre de traces |
| `settings_tracks_count_desc` | Recent tracks to render (0-20) | Traces récentes à afficher (0-20) |
| `settings_transparency_label` | Transparency | Transparence |
| `settings_transparency_desc` | Left thumb = newest track, right thumb = oldest. 0% = opaque, 100% = invisible. | Curseur gauche = plus récent, droit = plus ancien. 0% = opaque, 100% = invisible. |
| `settings_transparency_value_fmt` | Newest %d%%  –  Oldest %d%% | Récent %d%%  –  Ancien %d%% |
| `settings_pinned_transparency_label` | Pinned transparency | Transparence épinglés |
| `settings_pinned_transparency_desc` | Pinned tracks are always visible regardless of count. 0% = opaque, 100% = invisible. | Les traces épinglées sont toujours visibles. 0% = opaque, 100% = invisible. |
| `settings_colors_label` | Colors | Couleurs |
| `settings_colors_desc` | Past tracks: color gradient from newest (From) to oldest (To). Pinned tracks: amber/orange gradient. | Anciennes traces : dégradé du plus récent (De) au plus ancien (Vers). Épinglées : dégradé ambre/orange. |
| `settings_section_markers` | Markers | Repères |
| `settings_markers_desc` | User-created pins, circles, corridors and auto-markers | Repères, cercles, corridors et repères automatiques |
| `settings_zone_shapes_desc` | Show zone shapes (corridor edges, circle outlines) and proximity previews | Afficher les formes de zone et aperçus de proximité |
| `settings_zone_shapes_label` | Zone shapes | Formes de zone |
| `settings_speed_alert_gps_label` | Speed zone alert (GPS) | Alerte zone de vitesse (GPS) |
| `settings_speed_alert_gps_desc` | Auto-show speed zones when approaching in GPS mode | Afficher les zones de vitesse à l'approche en mode GPS |
| `settings_speed_alert_demo_label` | Speed zone alert (Demo) | Alerte zone de vitesse (Démo) |
| `settings_speed_alert_demo_desc` | Auto-show speed zones when panning the map toward a zone | Afficher les zones de vitesse en naviguant vers une zone |
| `settings_reg_alert_gps_label` | Regulated zone alert (GPS) | Alerte zone réglementée (GPS) |
| `settings_reg_alert_gps_desc` | Auto-show regulated zones when approaching a speed-enforced zone | Afficher les zones réglementées à l'approche |
| `settings_reg_alert_demo_label` | Regulated zone alert (Demo) | Alerte zone réglementée (Démo) |
| `settings_reg_alert_demo_desc` | Auto-show regulated zones when panning toward a speed-enforced zone | Afficher les zones réglementées en naviguant vers une zone |
| `settings_regenerate_layers` | Regenerate Layers | Régénérer les couches |
| `color_picker_pick` | Pick | Choisir |

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
