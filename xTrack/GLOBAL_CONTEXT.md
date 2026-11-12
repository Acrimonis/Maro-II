# Global Context — Routing Table
  
> State only — routing map, feature index, focus history, global todos, doc index. Rules and instructions live in `AGENTS.md`.

## Focus History
- [2026-11-12 07:50 UTC] Ui_General — on `feature/notices-toasts`, the notice-host work and its plan moved out of the short-lived Notices feature into Ui_General, then built through all six steps: every transient map message is a pill on one host (pure arbiter, process-scoped centre, keys in one file), the exit and lock lines, temperature and speed-history notices and startup diagnostics included, no Android Toast left, the undo rows' queue rules a pure lane; `assembleDebug` and 1 875 tests green, a preliminary device pass read OK, a code review's seven findings open → xTrack/Ui_General/FEAT_HYD_Ui_General.md
- [2026-11-11 17:08 UTC] Ui_General (was Notices) — on `feature/notices-toasts` (cut from `origin/develop` at PR #404, `--no-track`), one notice host with a queue and priority for every transient map message planned: nine decisions taken (one pure arbiter with two lanes, pills above the drawer ladder and action rows under it, the notice family's figures moved into `maro.properties` first), guards and ranked risks written, nothing implemented yet → xTrack/Ui_General/FEAT_HYD_Ui_General.md
- [2026-11-11 16:15 UTC] Navigation — on `feature/track-dash-arriving-not-updated`, the dashboard's 300 m band state now comes from the boat's position: a landward harbour approach shows the 5 kn band instead of LIBRE (read OK on the device), and the zone look-ahead stops at land so a zone behind a headland raises no amber (1 774 tests and `apk-build` green, device pass owed); open points tracked in the new Ozers feature, then the four Navigation plans archived with their decisions promoted → xTrack/Navigation/FEAT_HYD_Navigation.md
- [2026-11-11 16:10 UTC] Ozers — on `feature/temperature`, the air and sea-water temperature tile shipped: a tile heading the zone-icon column over a two-grid sample-point cache fed by Open-Meteo (air AROME seamless, sea marine), nearest-square rule, sticky tap view, long-press refresh that restarts the request, error and refresh toasts, atomic cache file, no fetch interval; the local commit rebased onto develop at PR #401 with the lowercase `ozers` folded into develop's `Ozers`, committed; 56 temperature tests and `assembleDebug` green, two reviews clean, the latest rounds unseen on the device → xTrack/Ozers/FEAT_HYD_Ozers.md
- [2026-11-11 09:14 UTC] Route — on `feature/route-tweeks` (cut from `origin/develop`), the end of a followed route shipped: the exit dialog and arrival cue removed, the toggle's off and the back key ending through the deferred window with Undo, Go restarting silently, arrival a status pill held 1 s (`route.follow.arrival.settleSec`) in a new status-pill stack (`MapStatusBannerStack`); `apk-build.bat` and 1 762 tests green, the Ask hop reviewed twice and folded, the device pass read OK → xTrack/Route/FEAT_HYD_Route.md
- [2026-11-11 08:05 UTC] Tracks — on `feature/tracks-slow-water` (cut from `origin/develop` at PR #400), the slow-water card plan's eight open Ask findings triaged (six fixed, 4 / 7b–d / 8 dropped) and shipped: the catch-up walks off the recorder's write lock and merges by compare-and-set with a pass-done-for-source reference, a box-rejected containment read replaces the full zone query and rides with its stamp as one attachment, the dashboard reads the unfiltered summaries, the route table reads the band once per leg, three stale comments rewritten; plan and review folded once each, a second review's pairing window, early exit and done-after-write taken; 6 new tests, `apk-build.bat` and 1 770 tests green, the device pass owed → xTrack/Tracks/FEAT_HYD_Tracks.md
- [2026-11-10 17:40 UTC] Tracks — on `feature/rooouting`, step 1 of the home-circle forgotten-start plan implemented: the circle exit and its debounce replaced by the level rule (`AutoStartDetector`: rested, off 5 min, moved by the idle thresholds, on water, no recovery pending), the last stop persisted in the service prefs, the arrival prompt armed by where the start fell and a safety net for false starts; 28 + 18 new tests, `apk-build.bat` and 1 764 tests green, the Ask hop's race and stale-comment findings fixed and the rest open in the plan, read OK on the device, uncommitted → xTrack/Tracks/FEAT_HYD_Tracks.md
- [2026-11-10 17:08 UTC] Tracks — on `feature/rooouting`, the La Salis auto-start that never fired is fixed (the 10 s debounce was reset by the second fix outside the circle; re-armed on the crossing, cleared on re-entry or any state change; the dead `tracking.homePort.enabled` flag removed; 10 new tests, `apk-build.bat` green, uncommitted, device pass owed); then planned, not built: a home-circle forgotten-start rule replacing the circle exit (step 1, one review folded) and flagged-marker auto-start parked as step 2 → xTrack/Tracks/FEAT_HYD_Tracks.md
- [2026-11-01 13:48 UTC] Route — on `feature/rooouting`: Evolutive + made the shipped default and P2's tests landed (five suites on both searches, the lone-turn test, the harness flags forwarded); the task list cut from 21 to 7, 31 plans archived; then the Tracks slow-water card plan implemented P0–P4 — the `Open water · 300M · Zones` row on track and route cards, a recording's figure on its summary behind the attached zone-and-band classifier, a route's written at save, the route table counting every second on limited water with the crown unmoved; `apk-build.bat` and 1 717 tests green, the Ask hop's findings open in the plan → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-30 18:09 UTC] Route — on `feature/rooouting` (on `develop` at PR #398), two exact speed-ups shipped with every harness line identical: the coast distance read off a per-cell candidate list (the 24-pair survey −67–73 %, the default pair −13–23 %) and the pull's two walks side by side on memo forks (the survey −11–16 % more), each reviewed once; the rest measured under the noise bar; 1 692 tests and `apk-build.bat` green → xTrack/Route/FEAT_HYD_Route.md
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
| tracks-import, tracksimport, gpx import cleanup, map track visibility, selection policy, visibleonmap | xTrack/TracksImport/FEAT_DSC_TracksImport.md |
| tasker, water state, intent broadcast, query receiver, automation | xTrack/Tasker/FEAT_DSC_Tasker.md |
| route, routing, destination, waypoint, cruise speed, fastest path, straight line, route placeholder, route dummy | xTrack/Route/FEAT_DSC_Route.md |
| ozers, temp, temperature, map temperature, sea temperature, air temperature, water temperature, temperature tile, open-meteo | xTrack/Ozers/FEAT_DSC_Ozers.md |
| notices, notice host, toast, toasts, pill, pills, snack, snackbar, transient message, notice queue, banner priority | xTrack/Ui_General/FEAT_DSC_Ui_General.md |

## Features

| Feature | Status | Created | Modified | Link |
|---|---|---|---|---|
| Route | active | 2026-08-16 10:44 | 2026-11-11 14:53 | [FEAT_DSC_Route.md](Route/FEAT_DSC_Route.md) |
| Ui_Dashboard | active | 2026-06-06 00:00 | 2026-10-12 08:58 | [FEAT_DSC_Ui_Dashboard.md](Ui_Dashboard/FEAT_DSC_Ui_Dashboard.md) |
| Ui_General | active | 2026-06-08 16:43 | 2026-11-12 11:35 | [FEAT_DSC_Ui_General.md](Ui_General/FEAT_DSC_Ui_General.md) |
| Markers | active | 2026-06-22 11:52 | 2026-10-23 17:41 | [FEAT_DSC_Markers.md](Markers/FEAT_DSC_Markers.md) |
| Tracks | active | 2026-06-15 21:43 | 2026-11-11 08:58 | [FEAT_DSC_Tracks.md](Tracks/FEAT_DSC_Tracks.md) |
| Performance | active | 2026-06-07 00:00 | 2026-09-20 13:50 | [FEAT_DSC_Performance.md](Performance/FEAT_DSC_Performance.md) |
| Ui_Settings | active | 2026-06-09 15:28 | 2026-10-20 19:40 | [FEAT_DSC_Ui_Settings.md](Ui_Settings/FEAT_DSC_Ui_Settings.md) |
| UI_Map | active | 2026-06-07 00:00 | 2026-10-23 16:20 | [FEAT_DSC_UI_Map.md](UI_Map/FEAT_DSC_UI_Map.md) |
| WorkflowImprovement | active | 2026-06-03 00:00 | 2026-10-24 07:24 | [FEAT_DSC_WorkflowImprovement.md](WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md) |
| Navigation | active | 2026-06-10 08:40 | 2026-11-11 16:17 | [FEAT_DSC_Navigation.md](Navigation/FEAT_DSC_Navigation.md) |
| RegulatedZones | active | 2026-06-11 18:00 | 2026-10-14 10:35 | [FEAT_DSC_RegulatedZones.md](RegulatedZones/FEAT_DSC_RegulatedZones.md) |
| ColorManagement | active | 2026-06-16 14:05 | 2026-09-16 15:00 | [FEAT_DSC_ColorManagement.md](ColorManagement/FEAT_DSC_ColorManagement.md) |
| ZoneTile | active | 2026-06-17 09:45 | 2026-09-16 06:31 | [FEAT_DSC_ZoneTile.md](ZoneTile/FEAT_DSC_ZoneTile.md) |
| Tasker | active | 2026-09-12 08:50 | 2026-09-12 08:50 | [FEAT_DSC_Tasker.md](Tasker/FEAT_DSC_Tasker.md) |
| TracksImport | active | 2026-09-11 20:28 | 2026-10-10 07:19 | [FEAT_DSC_TracksImport.md](TracksImport/FEAT_DSC_TracksImport.md) |
| Ui_Menu | active | 2026-07-05 06:57 | 2026-10-20 19:40 | [FEAT_DSC_Ui_Menu.md](Ui_Menu/FEAT_DSC_Ui_Menu.md) |
| ArcLayout | active | 2026-06-13 07:34 | 2026-09-29 11:35 | [FEAT_DSC_ArcLayout.md](ArcLayout/FEAT_DSC_ArcLayout.md) |
| Documentation | active | 2026-06-11 06:42 | 2026-10-10 11:53 | [FEAT_DSC_Documentation.md](Documentation/FEAT_DSC_Documentation.md) |
| DepthMapping | active | 2026-06-04 00:00 | 2026-06-10 12:13 | [FEAT_DSC_DepthMapping.md](DepthMapping/FEAT_DSC_DepthMapping.md) |
| GPS | active | 2026-06-07 00:00 | 2026-08-15 15:21 | [FEAT_DSC_GPS.md](GPS/FEAT_DSC_GPS.md) |
| BakeNormalization | active | 2026-06-08 00:00 | 2026-06-08 17:10 | [FEAT_DSC_BakeNormalization.md](BakeNormalization/FEAT_DSC_BakeNormalization.md) |
| DepthSafety | active | 2026-06-08 18:25 | 2026-06-09 12:01 | [FEAT_DSC_DepthSafety.md](DepthSafety/FEAT_DSC_DepthSafety.md) |
| Coastline | active | 2026-06-05 00:00 | 2026-06-23 07:08 | [FEAT_DSC_Coastline.md](Coastline/FEAT_DSC_Coastline.md) |
| CheckDev | active | 2026-06-20 11:42 | 2026-06-20 11:42 | [FEAT_DSC_CheckDev.md](CheckDev/FEAT_DSC_CheckDev.md) |
| Health | active | 2026-06-20 11:42 | 2026-06-20 11:42 | [FEAT_DSC_Health.md](Health/FEAT_DSC_Health.md) |
| Ozers | active | 2026-11-11 15:48 | 2026-11-11 16:29 | [FEAT_DSC_Ozers.md](Ozers/FEAT_DSC_Ozers.md) |

## Global Todos


## Cross-Reference Docs
Docs available via `#doc read [name]` from any feature. Fuzzy-resolve searches this table.

| Doc | Owner Feature | One-Liner |
|-----|---------------|-----------|
| `color-scheme.md` | ColorManagement | Color tokens, palette, alias chains |
| `FEAT_DOC_Route_engines.md` | Route | The acquisition engines as they stand: seam, rows, ladder, pipeline, plan seam, water, readings |
| `material-icons-standalone-guide.md` | Ui_General | How to add Material Symbols icons as standalone ImageVector .kt files |
