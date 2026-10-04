# Ui_Menu — Hydration

**Session:** two pieces on `feature/menu-live-cards`. First the TRACKS card: its Tracks row moved above the live
block, the block's band tinted in the tracking status colour at one shared faded level with a 1dp edge in that
colour, the shared pulse disc and the notification's own read (`Recording • Idle|Moving`), and six readings in a
two-column table whose columns size themselves from one measured label. Then the route card, in the same treatment: a
band in the route toggle's own colour — the line's while the engine searches, `routeNavigateColor` while it follows —
reading `Acquiring • <stage>` or `Routing • ETA: <min|sec>`, over four readings (`Dist total` · `Dist route`,
`ETA total` · `ETA route`), the card standing through the acquisition as well as the routing phase and the block
moved below the route ends. `StatCell` gained its columned shape and its internals were stated once. Builds green.

**Branch:** `feature/menu-live-cards` — cut from `origin/develop` at `d575c99`, 2026-10-04; commit `3487509` carries
the first piece.

**State:**
- `live-card-compact [x]` — implemented and committed (this session)
- `route-active-card [x]` — implemented (this session)
- `menu-render-upt [x]` — implemented (prior session)
- `toggle-zones-marker-in-menu [x]` — implemented
- `dashboard-clickability-reorder [x]` — implemented

**Key Files:**
- `ui/map/MenuDrawerOverlay.kt` — the two blocks: the Tracks row above its banded block, and the route ends above the route band and its four cells; one private `rememberLabelColumnWidth` measures both tables
- `ui/components/StatCell.kt` — one reading in two shapes, over `StatLabel` / `StatValue` and one `SEPARATOR`
- `ui/map/MapScreen.kt` — the drawer summary's gate widened to the search
- `ui/map/OverlayLayerParams.kt` — `RouteSummaryData.lineColor`, the band's hue
- `assets/colors.properties` + `config/AppConfig.kt` — `ui.band.fill.alpha`, the one faded level both bands read
- `res/values/strings.xml`, `res/values-fr/strings.xml` — the `state_*` read, the four cell labels, the band's ETA keys and the pending mark; `track_status_*`, `track_stat_state` and `route_trip_remaining` retired
- `docs/ui-component-guidelines.md` §5.8 and `docs/ui-drawer-guidelines.md` §9 — the pending mark's rule, and the block as the pattern's second wearer

**Plan:**
- `xTrack/Ui_Menu/261004_FEAT_PLN_Ui_Menu_live-card-compact.md` — implemented and committed
- `xTrack/Ui_Menu/261004_FEAT_PLN_Ui_Menu_route-active-card.md` — implemented (this session); a retirement candidate

**Open points left with the user:**
- The card inset `ui.padding.card.horizontal` 16 dp → 12 dp was decided and is unapplied
- The notification splits its middle segment three ways, so the drawer's band says Moving where it says Navigating
- A sub-card shape, like the collapsible zone sub-card, was raised for both live sections and is not designed yet
- The on-device read at arm's length is unrun
