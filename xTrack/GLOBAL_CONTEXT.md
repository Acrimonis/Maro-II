# Global Context — Routing Table
  
> State only — routing map, feature index, focus history, global todos, doc index. Rules and instructions live in `AGENTS.md`.

## Focus History
- [2026-10-24 07:24 UTC] WorkflowImprovement — the GLOBAL_CONTEXT feature-summaries split shipped: a 25-row `## Features` index replaced the prose block, each `FEAT_DSC_` gained a `## Summary` (Route pruned), and the templates, §7a/§7b rows and eight cmd_help pages were re-pointed; the Ask hop returned clean → xTrack/WorkflowImprovement/FEAT_HYD_WorkflowImprovement.md
- [2026-10-24 06:50 UTC] Tracks — on `feature/track-comment-ignore-time-spend-at-la-salis`, the recorded comment's home-port line now reads departure → return times instead of a time-spent duration, the departure side persisted on the track; `compileDebugKotlin` and the `data.track` suite green, the device pass owed → xTrack/Tracks/FEAT_HYD_Tracks.md
- [2026-10-24 06:31 UTC] WorkflowImprovement — the GLOBAL_CONTEXT feature-summaries split was designed into a plan with its four points settled (prose to `FEAT_DSC_`, index row + Routing Map kept, `#bake` stays the writer), nothing implemented → xTrack/WorkflowImprovement/FEAT_HYD_WorkflowImprovement.md
- [2026-10-24 05:24 UTC] Route — on `feature/route-comment`, a saved route's track now carries a comment composed at save time: `#rank • rung • pace • pref`, a per-zone time line (`Open water • 300M • limit`, each `m:ss min`) and `Routed on date in s sec`, the rank read at the time of save and every word in the locale active then; `compileDebugKotlin` and the scoped route/track suites green, two review should-fix folded in → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-23 19:19 UTC] Tracks — on `feature/route-toggle-color-like-track`, the selected item now paints on its own `track_selected_*` overlay so a selection change swaps one line instead of rebuilding every stored line, removing the flicker; `compileDebugKotlin` and the scoped `TrackRouteRoleTest` / `TrackRenderFlagsPathTest` green, the device pass owed → xTrack/Tracks/FEAT_HYD_Tracks.md
- [2026-10-23 19:03 UTC] Tracks — on `feature/route-toggle-color-like-track`, the selected-item speed toggle became two disk-persisted per-kind booleans (off by default) in a private store outside settings and properties, so a route and a track each keep their own on/off across restarts without touching either kind's axes; `compileDebugKotlin` and the scoped `TrackRouteRoleTest` / `TrackRenderFlagsPathTest` green, the device pass owed → xTrack/Tracks/FEAT_HYD_Tracks.md
- [2026-10-23 16:37 UTC] Markers — on `feature/mrk-lst-edit`, a marker card's Edit now always rebuilds `_createForm` from the marker being edited, so a Prev/Next move no longer opens the first marker's data in the wizard; `apk-build.bat` green and the review hop approved with one non-blocking finding → xTrack/Markers/FEAT_HYD_Markers.md
- [2026-10-23 16:20 UTC] UI_Map — on `feature/inspect-mode-fix`: the live acquire resolves a marker in the map-filtered world the sweep ranked so a map-visible marker opens its dashboard even when the list filter drops it, and `InspectFlow` logs the anchor→ladder→recentre→step chain; `apk-build.bat` green and the device logcat read the anchor preserved with the walk stepping closest-out → xTrack/UI_Map/FEAT_HYD_UI_Map.md
- [2026-10-23 15:22 UTC] Ui_General — on `feature/toats-life-cycle`, the map's bottom-band snackbar/toast stack now composes before the drawer ladder, so the menu drawer and its scrim paint above a visible toast while dialogs stay above the menu; `MapSnackbarHost` moved ahead of `OverlayLayer` in `MapScreen.kt`, and `docs/ui-drawer-guidelines.md` §1 lists the stack in Layer 0; `compileDebugKotlin` and `assembleDebug` green, the device pass the user's → xTrack/Ui_General/FEAT_HYD_Ui_General.md
- [2026-10-23 15:13 UTC] Markers — the marker colour palette grew to twenty hues under a **perceptual** rule (every pair clears ΔE ≥ 25 in CIELAB, with the named tiers and the water clause relaxed to admit a heavy navy and an azure), Charcoal and Pale Pink giving way to Heavy Blue and Azure while Coral and Silver were re-hexed to meet the floor; the two colour pickers collapsed into one, raised through `LocalConfirmDialogHost` onto `ConfirmDialog` so no drawer can clip it, 4×5 portrait and 5×4 landscape with radio semantics and a generic title; the wizard gained a `Marker color` row writing the `colorIndex` no control had ever set, the orphan `MarkerColorPickerDialog` and its imports went, the panel's frame moved from behind the background to a 2 dp `ui.highlight` outline, and the selection now wears a white-edged ring plus the app's own check disc; full `:app:testDebugUnitTest` and `apk-build.bat` green, the device pass the user's → xTrack/Markers/FEAT_HYD_Markers.md
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

## Features

| Feature | Status | Created | Modified | Link |
|---|---|---|---|---|
| Route | active | 2026-08-16 10:44 | 2026-10-24 06:34 | [FEAT_DSC_Route.md](Route/FEAT_DSC_Route.md) |
| Ui_Dashboard | active | 2026-06-06 00:00 | 2026-10-12 08:58 | [FEAT_DSC_Ui_Dashboard.md](Ui_Dashboard/FEAT_DSC_Ui_Dashboard.md) |
| Ui_General | active | 2026-06-08 16:43 | 2026-10-23 15:22 | [FEAT_DSC_Ui_General.md](Ui_General/FEAT_DSC_Ui_General.md) |
| Markers | active | 2026-06-22 11:52 | 2026-10-23 17:41 | [FEAT_DSC_Markers.md](Markers/FEAT_DSC_Markers.md) |
| Tracks | active | 2026-06-15 21:43 | 2026-10-24 06:50 | [FEAT_DSC_Tracks.md](Tracks/FEAT_DSC_Tracks.md) |
| Performance | active | 2026-06-07 00:00 | 2026-09-20 13:50 | [FEAT_DSC_Performance.md](Performance/FEAT_DSC_Performance.md) |
| Ui_Settings | active | 2026-06-09 15:28 | 2026-10-20 19:40 | [FEAT_DSC_Ui_Settings.md](Ui_Settings/FEAT_DSC_Ui_Settings.md) |
| UI_Map | active | 2026-06-07 00:00 | 2026-10-23 16:20 | [FEAT_DSC_UI_Map.md](UI_Map/FEAT_DSC_UI_Map.md) |
| WorkflowImprovement | active | 2026-06-03 00:00 | 2026-10-24 07:24 | [FEAT_DSC_WorkflowImprovement.md](WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md) |
| Navigation | active | 2026-06-10 08:40 | 2026-09-16 17:10 | [FEAT_DSC_Navigation.md](Navigation/FEAT_DSC_Navigation.md) |
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

## Global Todos


## Cross-Reference Docs
Docs available via `#doc read [name]` from any feature. Fuzzy-resolve searches this table.

| Doc | Owner Feature | One-Liner |
|-----|---------------|-----------|
| `color-scheme.md` | ColorManagement | Color tokens, palette, alias chains |
| `FEAT_DOC_Route_engines.md` | Route | The acquisition engines as they stand: seam, rows, ladder, pipeline, plan seam, water, readings |
| `material-icons-standalone-guide.md` | Ui_General | How to add Material Symbols icons as standalone ImageVector .kt files |
