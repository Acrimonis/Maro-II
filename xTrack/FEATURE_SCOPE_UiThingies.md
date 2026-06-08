---
name: UiThingies
status: active
created: 2026-06-06 00:00
modified: 2026-06-06 00:00
active_subfeature: none
subs_total: 6
subs_done: 6
one_liner: UI layout refinements for the Maro map — onwater toggle, settings, map position persistence, ergonomics
---

# Feature: UiThingies

**Description:**
UI layout refinements for the Maro map — reorganizing on-screen elements for better ergonomics.

## Subfeatures

### onwater-button  [x]

#### Todos
- [x] Move the isOnWater() icon from the bottom info panel to the top-left corner of the map area

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

#### Docs

### settings  [x]

#### Todos
- [x] Add SettingsButton matching ZoomButton style (64dp circle, white bg, blue icon) to top-right of MapContent
- [x] Implement settings page/overlay that opens on tap
- [x] Create SettingsManager with SharedPreferences persistence
- [x] Wire coastline visibility toggle to actual map rendering
- [x] Add default map center lat/lon text fields

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

#### Docs

### zoom-and-position  [x]

#### Todos
- [x] Persist map center lat/lon and zoom level on every pan/zoom via SharedPreferences
- [x] Restore persisted position on app start and after rotation

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

#### Docs

### hideLAyers  [x]

#### Todos
- [x] Remove initial positions (lat/lon fields) from settings UI
- [x] Add hide/show 300m Zone toggle to settings (second position in first section)

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

#### Docs

### remove-actions  [x]

#### Todos
- [x] Remove all buttons from the dashboard panel (cote, Bande, water/ground)

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt`

#### Docs

## Todos

## Rules

## Key Files

### compact-dash  [x]

#### Todos
- [x] Cherry-pick dashboard card compaction commit (fb4af4b) into branch

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt`

#### Docs

## Docs
