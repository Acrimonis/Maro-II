---
name: Ui_Menu
status: active
created: 2026-07-05 06:57
modified: 2026-09-09 06:52
---

# Feature: Ui_Menu

**Description:**
Hamburger menu drawer — right-side sliding panel (75% width) with position source,
track recording, and marker management sections. Rendered via `OverlayLayer` →
`DrawerSlot` → `MenuDrawerOverlay`; the drawer's read-only menu data travels in the
`MenuOverlayData` bundle (`OverlayLayerParams.kt`) rather than as individual `OverlayLayer`
params (callbacks stay individual). Uses `DrawerScaffold` for fixed-header +
scrollable body.


## Implemented

- **toggle-zones-marker-in-menu** — "Show Zones on Map" switch in MARKERS card → `xTrack/Ui_Menu/260705_FEAT_PLN_Ui_Menu_toggle-zones-marker-in-menu.md`

## Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt` — menu drawer content (POSITION SOURCE, TRACKS, MARKERS sections)
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — Layer 1 compositor; renders MenuDrawer via DrawerSlot
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayerParams.kt` — `@Immutable` read-only bundles (incl. `MenuOverlayData`)
- `app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt` — fixed-header + scrollable body scaffold
- `docs/ui-drawer-guidelines.md` — canonical drawer reference

## Docs
- `docs/ui-drawer-guidelines.md`
- `xTrack/Ui_Menu/FEAT_DOC_Ui_Menu_decisions.md`
