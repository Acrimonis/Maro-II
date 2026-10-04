# Ui_Menu — Hydration

**Session:** the drawer's cards, on `feature/menu-live-cards`. First the TRACKS card: its Tracks row moved above the
live block, the band tinted in the tracking status colour at one shared faded level with a 1dp edge in that colour,
the shared pulse disc and the notification's own read (`Recording • Idle|Moving`), and six readings in a two-column
table whose label, separator and value columns size themselves from one measured label. Then the route card in the
same treatment: a band in the route toggle's own colour — the line's while the engine searches,
`routeNavigateColor` while it follows — reading `Acquiring • <stage>` or `Routing • ETA: <min|sec>`, over four
readings (`Dist total` · `Dist route`, `ETA total` · `ETA route`), and both live blocks moved onto a `NestedCard`
sub-card. Then the card's own shape: titled **Routing**, its ends simplified to the two dropdowns — the arm button
removed for the map's own doors — and a **quick access** to the cruising speed and the driving preference as a
label-less wheel pair, the pace moving on a 5-knot grid from 5 to 35 kn with 25 as its default and the nearest stop
as its snap. The dropdown's box then became a component of its own (`DropdownBox`), with the width a **behaviour**
(`DropdownSizing`) rather than a number any caller passes, and its chrome trimmed to an 8dp field and a 4dp arrow
gap so both words of the pair stand whole. Builds green.

**Branch:** `feature/menu-live-cards` — cut from `origin/develop` at `d575c99`, 2026-10-04; commits `3487509`,
`f4a6201` and `ac80bf4` carry the first three pieces, and the quick access with the box extraction is the fourth.

**State:**
- `route-quick-access [x]` — implemented (this session)
- `dropdown-box-extract [x]` — implemented (this session)
- `route-active-card [x]` — implemented and committed (`f4a6201`)
- `live-card-compact [x]` — implemented and committed (`3487509`)
- `menu-render-upt [x]`, `toggle-zones-marker-in-menu [x]`, `dashboard-clickability-reorder [x]` — implemented (prior sessions)

**Key Files:**
- `ui/map/MenuDrawerOverlay.kt` — the Routing card: the route's ends, the quick-access pair, the live summary when the mode has something to say; the TRACKS card's banded block; one private `rememberLabelColumnWidth` measures both readings tables
- `ui/components/DropdownBox.kt` — the box alone: its surface, its four metrics, `boxValueStyle` (the one statement of the style the value reads, and of the width's measurement) and `dropdownBoxWidth`, with `DropdownSizing` as the behaviour it is handed
- `ui/components/DropdownRow.kt` — the labelled field over the box: label, description, the anchor, the popup and its wheel, and the `sizing` behaviour passed down unchanged
- `ui/components/DropdownPairRow.kt` — the pair: `DropdownField` per side and `DropdownPairWidth` translated into the field's behaviour, its own 4dp gap and vertical padding
- `ui/components/StatCell.kt` — one reading in two shapes, over `StatLabel` / `StatValue` and one `SEPARATOR`
- `config/AppConfig.kt` + `assets/maro.properties` — the pace's default 25, bounds 5/35, the 5-knot grid and `snapFreeWaterPaceKn`
- `data/settings/SettingsManager.kt` — the pace snapped onto that grid where it is loaded
- `ui/map/RouteOverlay.kt` — the ladder's own inverse and words: `routeRungLambdaOf`, `routeRungLabelRes`
- `ui/map/OverlayLayerParams.kt` + `ui/map/MapScreen.kt` — `RouteSummaryData`'s band colour and the pair's two values and callbacks
- `res/values/strings.xml` + `res/values-fr/strings.xml` — the `state_*` read, the band's ETA keys, the four cell labels, the pending mark and the two comments
- `docs/ui-component-guidelines.md` §2.12/§2.16 and `docs/ui-drawer-guidelines.md` §8a/§9 — the box's composition, the pair's behaviour and the drawer's card

**Plan:**
- `xTrack/Ui_Menu/261004_FEAT_PLN_Ui_Menu_live-card-compact.md` — implemented and committed
- `xTrack/Ui_Menu/261004_FEAT_PLN_Ui_Menu_route-active-card.md` — implemented and committed; a retirement candidate
- `xTrack/Ui_Menu/261004_FEAT_PLN_Ui_Menu_route-quick-access.md` — implemented (this session); §10 carries the box extraction and §11 the chrome trim; a retirement candidate

**Open points left with the user:**
- The card inset `ui.padding.card.horizontal` 16 dp → 12 dp was decided and is unapplied
- The notification splits its middle segment three ways, so the drawer's band says Moving where it says Navigating
- `dropdownBoxWidth(emptyList())` throws where a field is built from an empty option list — one `maxOfOrNull` guard would close it
- The settings write path is never snapped (only the load is), and `RoutePace`'s fitted pace is floored at 5 kn because it reads the same bounds
- The on-device read of the Routing card at arm's length is unrun
