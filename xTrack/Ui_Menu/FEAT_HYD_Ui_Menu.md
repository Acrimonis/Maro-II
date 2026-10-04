# Ui_Menu — Hydration

**Session:** live-card-compact — implemented on `feature/menu-live-cards`. The TRACKS card's two sub-sections
swapped, the Tracks row first and the live block under it; the live block is a state band tinted in the tracking
status colour at the taken-choice 30 % with a 1dp edge in the same state's colour, the app's shared pulse disc and
the notification's own read (`Recording • Idle|Moving`), over six readings in a two-column table whose label,
separator and value columns size themselves from one measured label width. `track_status_recording` and
`track_status_idle` retired with the reword, `track_stat_state` before them; two colour tokens added. Build green.

**Branch:** `feature/menu-live-cards` — cut from `origin/develop` at `d575c99`, 2026-10-04.

**State:**
- `live-card-compact [x]` — implemented (this session)
- `menu-render-upt [x]` — implemented (prior session)
- `toggle-zones-marker-in-menu [x]` — implemented
- `dashboard-clickability-reorder [x]` — implemented

**Key Files:**
- `ui/map/MenuDrawerOverlay.kt` — the TRACKS card: the Tracks row first, then the tinted state band and the readings table
- `ui/components/StatCell.kt` — one reading in two shapes: the cards' 33/66 split and the columned one, over `StatLabel` / `StatValue` and one `SEPARATOR`
- `ui/map/MapPulseDot.kt` — untouched; the band still leads with the app's one disc
- `assets/colors.properties` + `config/AppConfig.kt` — `status.tracking.container.recording` / `.idle` at 30 %
- `res/values/strings.xml`, `res/values-fr/strings.xml` — the band reads the `state_*` family; two keys retired
- `docs/ui-drawer-guidelines.md` §9 — the live block as the pattern's second wearer

**Plan:**
- `xTrack/Ui_Menu/261004_FEAT_PLN_Ui_Menu_live-card-compact.md` — implemented (this session); a retirement candidate

**Open points left with the user:**
- The notification splits its middle segment three ways — Idle, Navigating, Moving — while the drawer knows only
  `isMoving`, so the band says Moving where the notification says Navigating while a route is followed
- The drawer's measuring site re-states the label's 11 sp, its lineHeight and its colour instead of taking them
  from `StatCell`
- The card inset `ui.padding.card.horizontal` 16 dp → 12 dp was decided and is unapplied
- The on-device read at arm's length is unrun
