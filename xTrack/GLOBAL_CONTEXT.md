# Global Context — Routing Table
  
> State only — routing map, feature summaries, focus history, global todos, doc index. Rules and instructions live in `AGENTS.md`.

## Focus History
- [2026-10-04 01:50 UTC] Route — three plans on `feature/avoid-adaptive-grid` and no code: the adaptive grid re-scoped as the algorithm of a second engine named evolutive, that engine's plan with its shared-and-derived code map and its step readings, and the per-point speed fix with its defect located in the clock's constant sampling step → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-04 00:34 UTC] WorkflowImprovement — five Claude Code agent adapters in `.claude/agents/` mirror the mode handoff, each a thin pointer to AGENTS.md §8 → xTrack/WorkflowImprovement/FEAT_HYD_WorkflowImprovement.md
- [2026-10-03 19:49 UTC] Route — the ladder reads Fast · Balanced · Fun ordered by ETA, the preference re-seats on its live value, and a collapsed preference parks on its survivor (R95) → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-03 16:13 UTC] Route — the curve fitter removed and the settled line drawn and clocked directly; the two post-passes shipped — the single-bend racing-line corner pass (clothoid–arc–clothoid, corner as apex, `route.turn.reachFraction` lever, vMin floor dropped) and the backward/forward speed profile with the enforced limit the hard ceiling → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-03 14:29 UTC] Ui_Dashboard — the whole bottom dashboard family on one auto-resizing frame with one base size and one map ceiling, its corners square at every size after the device look rejected the round-once-grown effect, then the walk's residue landed: the dead probe, the badge residue and the stray landscape measurement gone, and the inset tightened → xTrack/Ui_Dashboard/FEAT_HYD_Ui_Dashboard.md
- [2026-10-03 12:03 UTC] Route — a saved route draws dashed in both fill modes, the rhythm read from `map.track.width.route.dashOn` / `dashOff`, so it reads apart from a recorded track; the casing under-stroke was rolled back → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-03 08:29 UTC] Route — `feature/route-n-floOow`: every discard a two-phase gesture with an undo toast, the fan closing on acquire/follow, and every explicit arming re-searching (R91, R92) → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-03 03:14 UTC] Route — the three-route ladder shipped: three fixed-aversion rungs over one shared grid, the Driving-preference cursor, tolerance-based collapse, a three-stop Settings cursor, and the candidate-pass apparatus retired; committed `5e6211fa` → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-03 02:39 UTC] Route — the slow-water dials live and `origin/feature/route-dash-n-flow` merged in (`b3778e5b`): the early select/save, the shared-`DrawerScaffold` acquisition panel and the Routing tab, the aversion dial re-homed into its Tuning block; Phase C and the Driving-preference cursor owed → xTrack/Route/FEAT_HYD_Route.md

## Routing Map
| Keyword | Feature File |
|---|---|
| documentation, docs, readme, faq, setup, git_workflow, maro_architecture | xTrack/Documentation/FEAT_DSC_Documentation.md |
| workflow, clinerules, xtrack, commands, memory, #doc, doccommands | xTrack/WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md |
| zone300, 300, bande 300m, water-only | xTrack/Coastline/FEAT_DSC_Coastline.md |
| depth, bathymetry, depthmapping, baro, seafloor, soundings, litto3d, shom, emodnet | xTrack/DepthMapping/FEAT_DSC_DepthMapping.md |
| coastline, trait de côte, fourmigue, hazard, obstruction, balisage, danger_isole, aton, seamark, lighthouse, reef | xTrack/Coastline/FEAT_DSC_Coastline.md |
| dashboard, ui, layout, hud, display, screen | xTrack/Ui_Dashboard/FEAT_DSC_Ui_Dashboard.md |
| gps, gpsplugin, gps mode, demo mode, heading, course, compass, location, geolocation | xTrack/GPS/FEAT_DSC_GPS.md |
| mapdisplay, map display, map, layer, depth layer, color depth, orientation | xTrack/UI_Map/FEAT_DSC_UI_Map.md |
| performance, battery, gps-tune, adaptive, compass-gate, map-refresh, battery-drain, power management, keep screen on, keep awake, screen-on, wakelock, screen lock, lock delay, movement gate | xTrack/Performance/FEAT_DSC_Performance.md |
| bake, baking, bake-script, bake-bat, apk-bake, apk-build, apk-deploy, deploy, prebake-pipeline, bake-env | xTrack/BakeNormalization/FEAT_DSC_BakeNormalization.md |
| depthsafety, depth-safety, danger-depth, shallow, grounding, isobar precision, isobath precision, depth alert, depth overlay, water-only | xTrack/DepthSafety/FEAT_DSC_DepthSafety.md |
| app-bak-flow, app-back-flow, back, back button, back handler, exit, double-back, press back, touch lock, splash guard | xTrack/Ui_General/FEAT_DSC_Ui_General.md |
| filters-link, filter link, linked filters, decouple list map filter, map filter referential | xTrack/Ui_General/FEAT_DSC_Ui_General.md |
| selected-item dashboard, selected item dashboard, dashboard close, close conditions, marker detail drawer | xTrack/Ui_General/FEAT_DSC_Ui_General.md |
| settings, preferences, config, scroll, options, land/water icon, show land/water icon, earth/water, zones transparency, regulated zone transparency, zone appearance | xTrack/Ui_Settings/FEAT_DSC_Ui_Settings.md |
| regulation, regulated zones, regulatedzone, regulation zone, speed zone, speed limit, anchoring, SHOM regulation, shom reg, maritime regulation, regulatory zone, réglementation maritime, zone réglementée, arrêté maritime, DIRM, cap d'antibes, lérins | xTrack/RegulatedZones/FEAT_DSC_RegulatedZones.md |
| arclayout, arc, arc-menu, layer-toggle, multi-btn, layer, toggle, fan-out | xTrack/ArcLayout/FEAT_DSC_ArcLayout.md |
| color, colour, color management, color-scheme, colors.properties, colour palette, colour scheme, colours, theme | xTrack/ColorManagement/FEAT_DSC_ColorManagement.md |
| zonetile, zone tile, zone info, zone ahead, zone cone, speed zone, speed zone display | xTrack/ZoneTile/FEAT_DSC_ZoneTile.md |
| boat, trip, track, tracks, recording, port-salis, journey | xTrack/Tracks/FEAT_DSC_Tracks.md |
| markers, pin, circle, corridor, usermarker, user marker, where am i | xTrack/Markers/FEAT_DSC_Markers.md |
| menu, menu drawer, hamburger, track drawer, position source, menu overlay | xTrack/Ui_Menu/FEAT_DSC_Ui_Menu.md |
| navigation, nav, heading arrow, direction arrow, speed arrow, cap arrow, course arrow, auto-show, auto show, redisplay on approach, zone reveal, demo auto-show | xTrack/Navigation/FEAT_DSC_Navigation.md |
| checkdev, dev branch, branch health, ahead behind, workflow hygiene | xTrack/CheckDev/FEAT_DSC_CheckDev.md |
| health, diagnostics, crash reporting, telemetry, memory monitoring | xTrack/Health/FEAT_DSC_Health.md |
| mergitur, merge, three-branch integration | xTrack/Mergitur/FEAT_DSC_Mergitur.md |
| tracks-import, tracksimport, gpx import cleanup, map track visibility, selection policy, visibleonmap | xTrack/TracksImport/FEAT_DSC_TracksImport.md |
| tasker, water state, intent broadcast, query receiver, automation | xTrack/Tasker/FEAT_DSC_Tasker.md |
| route, routing, destination, waypoint, cruise speed, fastest path, straight line, route placeholder, route dummy | xTrack/Route/FEAT_DSC_Route.md |

## Feature Summaries

| Feature | One-Liner | Created | Modified | Status |
|---------|-----------|---------|----------|--------|
| Route | Set a destination and have the app draw the route from the boat to it; the acquisition orders its ladder by ETA and names the slow water in its own first column, the selected line is reinforced by a derived edge, every discard is a two-phase gesture with an undo toast, a saved route draws dashed in both fill modes, and the settled line's corners round into a racing-line curve whose clock obeys the enforced limit; a second engine and its adaptive grid are in design beside the shipped one — detail in [`FEAT_DSC_Route.md`](xTrack/Route/FEAT_DSC_Route.md) | 2026-08-16 10:44 | 2026-10-04 01:50 | active |
| Ui_Dashboard | Main dashboard UI layout and HUD display — every portrait dashboard rides one auto-resizing frame with one base size and one map ceiling, a taller one pushing the map rather than covering it, and the corners square at every size after the device look rejected the round-once-grown effect — detail in [`FEAT_DSC_Ui_Dashboard.md`](xTrack/Ui_Dashboard/FEAT_DSC_Ui_Dashboard.md) | 2026-05-15 00:00 | 2026-10-03 17:51 | active |
| Ui_General | App-lifecycle UX — back, insets, lists, drawers, banners, the dashboard close rules — detail in [`FEAT_DSC_Ui_General.md`](xTrack/Ui_General/FEAT_DSC_Ui_General.md) | 2026-06-08 16:43 | 2026-09-28 19:45 | active |
| Markers | User markers (Pin/Circle/Corridor), where-am-I, the wizard shell — detail in [`FEAT_DSC_Markers.md`](xTrack/Markers/FEAT_DSC_Markers.md) | 2026-06-22 11:52 | 2026-09-28 19:45 | active |
| Tracks | Track recording, the two render axes, the speed heatmap, the trace flag — detail in [`FEAT_DSC_Tracks.md`](xTrack/Tracks/FEAT_DSC_Tracks.md) | 2026-06-15 00:00 | 2026-09-23 06:50 | active |
| Performance | Battery optimization, adaptive GPS tuning, power management, the map layer cost pass — detail in [`FEAT_DSC_Performance.md`](xTrack/Performance/FEAT_DSC_Performance.md) | 2026-05-20 00:00 | 2026-09-20 13:50 | active |
| Ui_Settings | Settings page UI — row families, transparency paradigm, stroke widths, the px→dp migration — detail in [`FEAT_DSC_Ui_Settings.md`](xTrack/Ui_Settings/FEAT_DSC_Ui_Settings.md) | 2026-06-09 15:28 | 2026-09-19 13:50 | active |
| UI_Map | Map rendering, depth colour layer, overlays, inspect mode, the px→dp pass — detail in [`FEAT_DSC_UI_Map.md`](xTrack/UI_Map/FEAT_DSC_UI_Map.md) | 2026-05-10 00:00 | 2026-09-19 13:21 | active |
| WorkflowImprovement | xTrack `#` command system and rule-book governance, and the Claude Code agent adapters in `.claude/agents/` that mirror the mode handoff — detail in [`FEAT_DSC_WorkflowImprovement.md`](xTrack/WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md) | 2026-06-03 00:00 | 2026-10-04 00:34 | active |
| Navigation | Navigation aids — heading/speed arrow and direction line, auto-show zones | 2026-06-10 08:40 | 2026-09-16 17:10 | active |
| RegulatedZones | Maritime regulatory zones — multi-source normalization, sealed classification, icon mapping | 2026-06-11 18:00 | 2026-09-16 17:10 | active |
| ColorManagement | Centralised colour palette — all tokens in `colors.properties` with alias interpolation — [`color-scheme.md`](docs/color-scheme.md) | 2026-06-16 14:05 | 2026-09-16 15:00 | active |
| ZoneTile | Zone information tiles and map overlay rendering | 2026-06-17 09:45 | 2026-09-16 06:31 | active |
| Tasker | External automation bridge — publishes the boat's water state; architecture approved, not implemented | 2026-09-12 08:50 | 2026-09-12 08:50 | active |
| Mergitur | Three-branch integration COMPLETE into `feature/mergitur` | 2026-09-11 20:04 | 2026-09-11 20:57 | active |
| TracksImport | Derived map track visibility, shared `MapSelectionPolicy`, GPX off-route cleanup harness | 2026-09-11 20:28 | 2026-09-11 20:28 | active |
| Ui_Menu | Hamburger menu drawer — position source, track recording, marker management | 2026-07-05 06:57 | 2026-09-11 15:23 | active |
| ArcLayout | Layer toggle arc menu — pure-Compose semicircle fan-out | 2026-06-13 07:34 | 2026-09-06 21:19 | active |
| Documentation | README, FAQs, setup guides, architecture docs, and plans cleanup | 2026-06-11 06:42 | 2026-09-04 22:36 | active |
| DepthMapping | Bathymetry / depth mapping from Litto3D, SHOM, EMODnet sources | 2026-05-10 00:00 | 2026-09-04 22:36 | active |
| GPS | GPS plugin with demo mode, heading/COG compass, geolocation, auto-follow spring-back hold | 2026-05-10 00:00 | 2026-09-04 22:36 | active |
| BakeNormalization | APK bake/build/deploy pipeline and prebake data processing | 2026-06-01 00:00 | 2026-09-04 22:36 | active |
| DepthSafety | Danger depth alerts, shallow water grounding prevention, isobath precision | 2026-06-03 00:00 | 2026-09-04 22:36 | active |
| Coastline | Coastline extraction, spatial indexing, isOnWater, hazard rings, unified data store | 2026-05-10 00:00 | 2026-06-23 07:08 | active |
| CheckDev | Dev-branch health monitoring — remote branch state, ahead/behind analysis | 2026-06-20 11:42 | 2026-06-20 11:42 | active |
| Health | Application health monitoring — diagnostics, crash reporting, telemetry | 2026-06-20 11:42 | 2026-06-20 11:42 | active |

## Global Todos

- [ ] Validate the intermittent Overpass-outage theory — confirm the coastline OSM fetch failures are transient (succeeded 13:52, failing ~16:52 on 2026-06-08), not a persistent network / cert / IPv6 block. Quick checks: retry `bake-coastline` later; `curl -sk https://overpass-api.de/api/status`; race other mirrors.
- [ ] **Change direction arrow color by speed compliance** — arrow in heading-ahead display (↑/↗→/→) should reflect speed-vs-limit ratio: green ≤ limit, orange ≤ limit×1.4, red > limit×1.4
- [ ] **Tasker water-state bridge — must be resolved before the power service becomes conditional** — `TrackRecordingService` answers `ACTION_QUERY_WATER_STATE` from a runtime-registered receiver and holds `lastKnownOnWater`; the Performance power work plans to run the service only while a keep-alive reason holds, at which point the query goes unanswered while the app is idle. Detail in `xTrack/Performance/260912_FEAT_PLN_Performance_power-management-centralization.md` §6 and `xTrack/Tasker/260628_FEAT_PLN_Tasker_tasker-water-state-integration.md`.
- [ ] **`#new` should warn before switching and offer to overwrite the local branch** (raised 2026-09-19): pin what the warning says and what overwrite means, then land it in `AGENTS.md` §7b and `docs/cmd_help_git.md`.
- [ ] **Two startup crashes sit in the device's crash buffer from before the Route work** — `DepthSerializer`/`DepthProtos` (2026-09-18 → 2026-09-21 20:50) and `SettingsManager.load` (2026-09-17), each belonging to the feature that owns its load (DepthMapping; Ui_Settings).
- [ ] **The `propInt`-ARGB twins, and one wrong sentence the trace work left (2026-09-22)** — the surviving colour pairs still read their ARGB through `propInt`, and the trace plan's §8 sentence about Resume does not hold.

## Cross-Reference Docs
Docs available via `#doc read [name]` from any feature. Fuzzy-resolve searches this table.

| Doc | Owner Feature | One-Liner |
|-----|---------------|-----------|
| `color-scheme.md` | ColorManagement | Color tokens, palette, alias chains |
| `material-icons-standalone-guide.md` | Ui_General | How to add Material Symbols icons as standalone ImageVector .kt files |
