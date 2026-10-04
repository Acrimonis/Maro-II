---
name: Ui_Menu
status: active
created: 2026-07-05 06:57
modified: 2026-10-04 12:16
---

# Feature: Ui_Menu

**Description:**
Hamburger menu drawer — right-side sliding panel (75% width) with the routing card
(ends, the two settings' quick access, the live summary), track recording, and marker
management sections. Rendered via `OverlayLayer` →
`DrawerSlot` → `MenuDrawerOverlay`; the drawer's read-only menu data travels in the
`MenuOverlayData` bundle (`OverlayLayerParams.kt`) rather than as individual `OverlayLayer`
params (callbacks stay individual). Uses `DrawerScaffold` for fixed-header +
scrollable body, and renders its three sections with the shared Settings stencils
(`SectionHeader` / `CardArea` / `SectionDivider` / `ToggleRow` in `ui/components`).


## Implemented

- **dropdown-box-extract (2026-10-04)** — the dropdown's box became its own component: `ui/components/DropdownBox.kt` holds the surface, the box's four metrics and the one statement of the style its value reads — the same style the `Text` draws with and `dropdownBoxWidth` measures in — while `DropdownRow` composes it and keeps the label, the description, the anchor, the popup and the wheel. **What an environment sets is a behaviour** (`DropdownSizing.Fill` takes the width it is given, `.Content` takes the width its longest entry needs, asked of the box itself), so neither a caller nor the pair hands over a number; the pair translates its own per-side choice into it. Its chrome was then trimmed — an 8dp field, a 4dp arrow gap and a 4dp pair gap, 28dp freed across the pair — because two boxes pay that chrome twice in one row and the second one's word was being cut → [`261004_FEAT_PLN_Ui_Menu_route-quick-access.md`](261004_FEAT_PLN_Ui_Menu_route-quick-access.md) §10–§11
- **route-quick-access (2026-10-04)** — the drawer's first card is titled `Routing` and holds three sub-sections in order: the route's two ends, the quick access to the mode's two settings, then the live summary when the mode has something to say. The pair — cruising speed beside driving preference, each a label-less `DropdownRow` whose list opens as the wheel — writes `routeFreeWaterPaceKn` / `routeSlowWaterAversion` through the same values the Settings sliders write, so the drawer is a second door and never a second home. The pace moves on a 5-knot grid, 5 … 35 kn, default 25, snapping to the nearest stop wherever it is loaded; the pair's width is a **behaviour, measured by the box itself**: the pace's side takes the width of its longest entry and the preference's the row's remainder → [`261004_FEAT_PLN_Ui_Menu_route-quick-access.md`](261004_FEAT_PLN_Ui_Menu_route-quick-access.md)
- **live-card-compact (2026-10-04)** — the TRACKS card's two sub-sections swapped, the Tracks row first and the live block under it: a state band tinted in the tracking colours with a 1dp edge in the same state's colour, the shared pulse disc and the notification's own read (`Recording • Idle|Moving`), over six readings in a two-column table whose label, separator and value columns size themselves → [`261004_FEAT_PLN_Ui_Menu_live-card-compact.md`](261004_FEAT_PLN_Ui_Menu_live-card-compact.md)
- **route-active-card (2026-10-04)** — the route summary takes the same treatment: a band in the route toggle's own colour at one shared faded level (`ui.band.fill.alpha`), the shared disc leading it and `Acquiring • <stage>` or `Routing • ETA: <min|sec>` beside it, over four readings (`Dist total` · `Dist route`, `ETA total` · `ETA route`) with the pending mark where a figure is missing; the card stands through the acquisition as well as the routing phase, the block moved below the route ends, and the panel's two marks read the same word → [`261004_FEAT_PLN_Ui_Menu_route-active-card.md`](261004_FEAT_PLN_Ui_Menu_route-active-card.md)
- **menu-render-upt** — Menu drawer body rewritten onto the shared Settings stencils + spacing tokens; stencils extracted to `ui/components` (sentence-case strings) → `xTrack/Ui_Menu/260909_FEAT_PLN_Ui_Menu_menu-render-upt.md`
- **toggle-zones-marker-in-menu** — "Show Zones on Map" switch in MARKERS card → `xTrack/Ui_Menu/260705_FEAT_PLN_Ui_Menu_toggle-zones-marker-in-menu.md`

## Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt` — menu drawer content (ROUTING, TRACKS, MARKERS sections)
- `app/src/main/java/ykws/android/maro/ui/components/DropdownBox.kt` — one dropdown's box alone: its surface, its metrics, its value style and `dropdownBoxWidth`, with `DropdownSizing` as the behaviour it is handed
- `app/src/main/java/ykws/android/maro/ui/components/DropdownRow.kt` — the labelled field over that box: label, anchor, popup, wheel
- `app/src/main/java/ykws/android/maro/ui/components/DropdownPairRow.kt` — two fields side by side, each side's width a behaviour
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — Layer 1 compositor; renders MenuDrawer via DrawerSlot
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayerParams.kt` — `@Immutable` read-only bundles (incl. `MenuOverlayData` and `RouteSummaryData`)
- `app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt` — fixed-header + scrollable body scaffold
- `docs/ui-drawer-guidelines.md` — canonical drawer reference

## Docs
- `docs/ui-drawer-guidelines.md`
- `xTrack/Ui_Menu/FEAT_DOC_Ui_Menu_decisions.md`
- `xTrack/Route/260926_FEAT_PLN_Route_menu-mode-summary.md` — the drawer's route action group retired in favour of a read-only mode summary (owned by the Route feature)
