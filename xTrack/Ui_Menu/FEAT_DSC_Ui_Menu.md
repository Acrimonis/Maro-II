---
name: Ui_Menu
status: active
created: 2026-07-05 06:57
modified: 2026-08-27 12:12
---

# Feature: Ui_Menu

**Description:**
Hamburger menu drawer — right-side sliding panel (75% width) with position source,
track recording, and marker management sections. Rendered via `OverlayLayer` →
`DrawerSlot` → `MenuDrawerOverlay`. Uses `DrawerScaffold` for fixed-header +
scrollable body.

## Sections

### toggle-zones-marker-in-menu

Add a "Show Zones on Map" Switch toggle to the MARKERS card in the menu drawer,
mirroring the POSITION SOURCE GPS toggle pattern. Controls `AppSettings.markerZonesVisible`
— same state as the Settings page "Zone shapes" toggle.

#### Plan
- `xTrack/Ui_Menu/260705_FEAT_PLN_Ui_Menu_toggle-zones-marker-in-menu.md`

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` (no change — reads existing state)

## Implemented

- **toggle-zones-marker-in-menu** — "Show Zones on Map" switch in MARKERS card → `xTrack/Ui_Menu/260705_FEAT_PLN_Ui_Menu_toggle-zones-marker-in-menu.md`

## Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt` — menu drawer content (POSITION SOURCE, TRACKS, MARKERS sections)
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — Layer 1 compositor; renders MenuDrawer via DrawerSlot
- `app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt` — fixed-header + scrollable body scaffold
- `docs/ui-drawer-guidelines.md` — canonical drawer reference

## Docs
- `docs/ui-drawer-guidelines.md`
- `xTrack/Ui_Menu/FEAT_DOC_Ui_Menu_decisions.md`
