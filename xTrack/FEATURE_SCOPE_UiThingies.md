---
name: UiThingies
status: active
created: 2026-06-06
modified: 2026-06-06
active_subfeature: none
subs_total: 3
subs_done: 3
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

## Todos

## Rules

## Key Files

## Docs
