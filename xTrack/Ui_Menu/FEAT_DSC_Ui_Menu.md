---
name: Ui_Menu
status: active
created: 2026-07-05 06:57
modified: 2026-10-05 20:56
---

# Feature: Ui_Menu

**Description:**
Hamburger menu drawer — right-side sliding panel (75% width) with the routing card
(ends, the two settings' quick access, the live summary), the routes list access, the
track list access, and marker management sections. Rendered via `OverlayLayer` →
`DrawerSlot` → `MenuDrawerOverlay`; the drawer's read-only menu data travels in the
`MenuOverlayData` bundle (`OverlayLayerParams.kt`) rather than as individual `OverlayLayer`
params (callbacks stay individual). Uses `DrawerScaffold` for fixed-header +
scrollable body, and renders its four sections with the shared Settings stencils
(`SectionHeader` / `CardArea` / `SectionDivider` / `ToggleRow` in `ui/components`).


## Implemented

- **route-gate-clarification (2026-10-05, same branch)** — the route Speed/Direction switches were settled as **subordinate to the master Speed Colors and Arrows chips** (copy + comments, no logic change: a route shows speed colours or chevrons only while the master chip and its own switch are both on), and a **pinned route now obeys the arrows veto** ([`pinnedTrackRenderPlan()`](../../app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:599)) while a pinned track stays untouched and route colours keep the pinned pair; a `SectionDivider` then split the Layers card's track and routes groups → [`261005_FEAT_PLN_Ui_Menu_kind-visibility-toggles.md`](261005_FEAT_PLN_Ui_Menu_kind-visibility-toggles.md)

- **kind-visibility-toggles (2026-10-05, `feature/menu-kind-visibility`, stacked on `feature/list-tracks-routes`)** — the drawer's **ROUTES** and **TRACKS** headers gained a **map-visibility eye** as their first trailing control, standing outside the filter-axes gate so it never disappears with the filters: it shows or hides its whole kind on the map in one tap — pinned items and beyond-the-cap items included — and it is a **render switch alone**, so the count beside each row follows the filter and the lists keep their rows. The one `tracksVisible` gate split per kind: `tracksVisible` narrowed to recorded tracks and a new `routesVisible` gates routes, the pinned escape gated per kind inside `storedTrackSelection`; the legend gate, the inspect candidates and the open-from-list force-visible read the opened kind's own flag, and the rebuild keys and the list preview key follow the scope. A standalone `VisibilityOff` glyph was added under `ui/icons`. `apk-build.bat` SUCCESSFUL; the suite reads 930 with the one parked Route-engine failure and two `TrackOutlineTest` failures caused by an uncommitted `maro.properties` dash edit that is not part of this work → [`261005_FEAT_PLN_Ui_Menu_kind-visibility-toggles.md`](261005_FEAT_PLN_Ui_Menu_kind-visibility-toggles.md)

- **route-list-separation (2026-10-05, `feature/list-tracks-routes`)** — the drawer gained a **ROUTES section** mirroring TRACKS, the row moved out of the ROUTING card (D11, superseding D1): a `SectionHeader` titled Routes holds the section's own Link / Filter / Reset bound to the **route map referential**, and the card under it holds the **Routes row** — a label, its count and a chevron to the first route — opening a **routes list** through `chrome.showRouteHistory`; the ROUTING card keeps the route ends, the quick access and the gated summary. The two lists are hard-separated and share the one `TrackHistoryOverlay` through a `ListScope` (`listScopeOf`, the two chrome flags mutually exclusive at every opener), the Kind axis retired so a Tracks list holds recorded tracks alone and a Routes list saved routes alone; the routes list hides the live card and the Merge action, wears `route_history_title_fmt` / `route_history_section` and a routes empty state. The route settings moved home too: the routes count, the route ladder and every route colour left the Layers tab's Tracks card for the Routing tab's Appearance block, the **Active route** line colour with them, and a pinned route gained its own transparency and colour pair. A pinned route also stays **dashed** (D12): the dash reads the summary's route **identity** rather than the render role, so the shared pinned path keeps the route's dash while a pinned recorded track stays solid. `apk-build.bat` SUCCESSFUL; `gradlew :app:testDebugUnitTest` at 927 green with one pre-existing Route-engine failure unrelated to this work → [`261005_FEAT_PLN_Ui_Menu_route-list-separation.md`](261005_FEAT_PLN_Ui_Menu_route-list-separation.md)

- **dropdown-box-extract (2026-10-04)** — the dropdown's box became its own component: `ui/components/DropdownBox.kt` holds the surface, the box's four metrics and the one statement of the style its value reads — the same style the `Text` draws with and `dropdownBoxWidth` measures in — while `DropdownRow` composes it and keeps the label, the description, the anchor, the popup and the wheel. **What an environment sets is a behaviour** (`DropdownSizing.Fill` takes the width it is given, `.Content` takes the width its longest entry needs, asked of the box itself), so neither a caller nor the pair hands over a number; the pair translates its own per-side choice into it. Its chrome was then trimmed — an 8dp field, a 4dp arrow gap and a 4dp pair gap, 28dp freed across the pair — because two boxes pay that chrome twice in one row and the second one's word was being cut → [`261004_FEAT_PLN_Ui_Menu_route-quick-access.md`](261004_FEAT_PLN_Ui_Menu_route-quick-access.md) §10–§11
- **route-quick-access (2026-10-04)** — the drawer's first card is titled `Routing` and holds three sub-sections in order: the route's two ends, the quick access to the mode's two settings, then the live summary when the mode has something to say. The pair — cruising speed beside driving preference, each a label-less `DropdownRow` whose list opens as the wheel — writes `routeFreeWaterPaceKn` / `routeSlowWaterAversion` through the same values the Settings sliders write, so the drawer is a second door and never a second home. The pace moves on a 5-knot grid, 5 … 35 kn, default 25, snapping to the nearest stop wherever it is loaded; the pair's width is a **behaviour, measured by the box itself**: the pace's side takes the width of its longest entry and the preference's the row's remainder → [`261004_FEAT_PLN_Ui_Menu_route-quick-access.md`](261004_FEAT_PLN_Ui_Menu_route-quick-access.md)
- **live-card-compact (2026-10-04)** — the TRACKS card's two sub-sections swapped, the Tracks row first and the live block under it: a state band tinted in the tracking colours with a 1dp edge in the same state's colour, the shared pulse disc and the notification's own read (`Recording • Idle|Moving`), over six readings in a two-column table whose label, separator and value columns size themselves → [`261004_FEAT_PLN_Ui_Menu_live-card-compact.md`](261004_FEAT_PLN_Ui_Menu_live-card-compact.md)
- **route-active-card (2026-10-04)** — the route summary takes the same treatment: a band in the route toggle's own colour at one shared faded level (`ui.band.fill.alpha`), the shared disc leading it and `Acquiring • <stage>` or `Routing • ETA: <min|sec>` beside it, over four readings (`Dist total` · `Dist route`, `ETA total` · `ETA route`) with the pending mark where a figure is missing; the card stands through the acquisition as well as the routing phase, the block moved below the route ends, and the panel's two marks read the same word → [`261004_FEAT_PLN_Ui_Menu_route-active-card.md`](261004_FEAT_PLN_Ui_Menu_route-active-card.md)
- **menu-render-upt** — Menu drawer body rewritten onto the shared Settings stencils + spacing tokens; stencils extracted to `ui/components` (sentence-case strings) → `xTrack/Ui_Menu/260909_FEAT_PLN_Ui_Menu_menu-render-upt.md`
- **toggle-zones-marker-in-menu** — "Show Zones on Map" switch in MARKERS card → `xTrack/Ui_Menu/260705_FEAT_PLN_Ui_Menu_toggle-zones-marker-in-menu.md`

## Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt` — menu drawer content (ROUTING, ROUTES, TRACKS, MARKERS sections)
- `app/src/main/java/ykws/android/maro/ui/components/DropdownBox.kt` — one dropdown's box alone: its surface, its metrics, its value style and `dropdownBoxWidth`, with `DropdownSizing` as the behaviour it is handed
- `app/src/main/java/ykws/android/maro/ui/icons/VisibilityOff.kt` — standalone Material Symbols eye-off glyph for the two header visibility toggles
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
