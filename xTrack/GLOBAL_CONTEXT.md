# Global Context — Routing Table
  
> State only — routing map, feature summaries, focus history, global todos, doc index. Rules and instructions live in `AGENTS.md`.

## Focus History
- [2026-10-05 16:35 UTC] Route — the mark count's Phase 1 landed its lattice: one `MarkLattice` stands a walk's marks at `anchor + (j + 0.5) × step` with the last interval sized to the chord's remainder, used by `softPriceSec` and by `legClearCause`'s coarse marks, the price walk's intervals latticing on the fine sampling step while Phase 4b's price step keeps governing the grouping; `apk-build.bat` is green, the suite reads 918 with the one parked ratio red, three new fixtures are green and no verdict assertion moved, and **the lattice's error bound — at most one interval's price — is the word Phase 2's memo waits on** → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-05 10:19 UTC] Route — the span-level price proof landed its Phase 1, so the price walk now reads a provable chord once: one clearance read at a span's midpoint proves every interval where it reaches the span's half-length, an unproved span splits in half, and the recursion's floor at twice the price step leaves a short chord on the group path; the sum is one in-order accumulator so a proved span is the fine sum to the bit and the fine-step walk is untouched, `apk-build.bat` is green, [`AvoidPriceWalkTest`](app/src/test/java/ykws/android/maro/spatial/multipass/AvoidPriceWalkTest.kt:30) is 15/15 with four new fixtures and one read-count assertion retargeted, the suite reads 908 / 1 parked red / 10, and Phase 2's device reading is owed → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-05 09:53 UTC] Ui_General — the dropdown takes one drag to open, loses its arrow, centres its wheel on the box and shares one text alignment between the field and its rows, in three passes → xTrack/Ui_General/FEAT_HYD_Ui_General.md
- [2026-10-04 20:32 UTC] Route — the fine layer's ribbon windows were merged into exact-union rectangles so the rasterizer's count falls with the cells untouched, and the price walk's own cut landed behind a boundary each soft source declares with its price error proved zero; the device pass that followed reads 11 and 26 windows at ~0.013 ms a cell and says no proved group ever formed, because the step in force is the band's fine cell → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-04 17:40 UTC] Route — the pull's sampled clearance shipped in three phases and the fine layer's reshape with it: the coastline read is paid only where a coarse mark's 1-Lipschitz bound cannot prove the half-step clear, the fine layer is cut to windows over the coastal ribbon so the grown corridor stops exhausting the heap, and any walk over `route.walk.maxCells` is refused before it is rastered; the device says no crash and the line and clock unchanged, the clearance half down to 0.8–3.5 s, and **the price walk holding 80 % of the pull** — the parked item whose trigger that reading fired → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-04 14:02 UTC] Ui_General — the map's action and undo toasts joined the bottom banner family as its third full-width face, and on the device pass they took the band's own gutter and the dialog button's compact SECONDARY command face → xTrack/Ui_General/FEAT_HYD_Ui_General.md
- [2026-10-04 13:01 UTC] Route — the drawn tail took the local resolution (Phase 6), and the speeds a route reports were then put right four faults deep: the corner pass's spiral bowed twice as sharply as its own heading (its Fresnel pair read at τ, not τ/√2) so every bend fell to the 5 kn floor, two turn keys sat five times below their documented 1.0, a saved route labelled each vertex with the leg **arriving** at it so its speeds ran one leg out of step and read 30 kn inside a zone, and a leg was timed from its two ends alone — holding a bend's floor across 679 m — while `OVER LIMIT` proved no leg ever exceeded the lowest limit along it → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-04 12:16 UTC] Ui_Menu — the drawer's Routing card: the ends simplified to their two dropdowns, a quick access to the cruising speed and the driving preference as a wheel pair on a 5–35 kn grid defaulting to 25, the live blocks on a sub-card in one banded treatment, and the dropdown's box extracted as its own component with the width a behaviour → xTrack/Ui_Menu/FEAT_HYD_Ui_Menu.md
- [2026-10-04 12:11 UTC] Route — the adaptive grid's Phase 2 read on the device and the second pass cut with it: the corridor's half-width confirmed at 150 m, the re-walk kept once in five arms against a 2.0×–5.2× cost and retired for `evolutive` while `avoid` stays untouched, and every rung's row now carries a provisional distance and ETA from its first taut line (R96) → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-04 08:56 UTC] Route — on `feature/avoid-adaptive-grid`, the pass pipeline dissolved into four seats composed once by the engine and the shared layer renamed `multipass` on the user's word, with `apk-build.bat` green and the one parked ratio test still the only red; the review found `RoutePassRules` holding no production caller → xTrack/Route/FEAT_HYD_Route.md

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
| Route | Set a destination and have the app draw the route from the boat to it; the acquisition orders its ladder by ETA and names the slow water in its own first column, each rung's row reading a provisional pair from its first taut line until it settles, the selected line reinforced by a derived edge, every discard a two-phase gesture with an undo toast, a saved route dashed in both fill modes, and the settled line's corners rounded into a racing-line curve whose clock obeys the enforced limit; both real engines stand on the shared `multipass` seats — `evolutive` adding a two-layer first walk on one lattice family at exactly 1 : 5 whose layers meet at a seam priced from the two cell centres, the second pass's re-walk retired for both engines on their own device readings; the pull's coastline read is paid only where a coarse mark's 1-Lipschitz bound cannot prove the half-step clear, the fine layer is cut into merged exact-union ribbon windows behind a walk-wide cell ceiling (11 and 26 windows at ~0.013 ms a cell on the pass), and the price walk — the pull's remaining cost, 80 % of it — reads a proved group once behind a boundary each soft source declares with its price error proved zero, **its step now the walk's own interior cell** so a group forms where the band's fine cell allowed none, its clean device pass of 2026-10-05 carrying no figures so the saving stays unread; and **the span-level price proof's Phase 1 has landed** — the walk reads a provable chord once, its sum one in-order accumulator so the line holds bit-identical and the fine-step walk is untouched — and **the mark count's Phase 1 has landed** — one `MarkLattice` stands a walk's marks at `anchor + (j + 0.5) × step` with the last interval sized to the chord's remainder, the price walk's intervals on the fine sampling step and the coarse marks on the coarse step, **its error at most one interval's price**, the Phase 2 memo waiting on the user's word — detail in [`FEAT_DSC_Route.md`](xTrack/Route/FEAT_DSC_Route.md) | 2026-08-16 10:44 | 2026-10-05 16:35 | active |
| Ui_Dashboard | Main dashboard UI layout and HUD display — every portrait dashboard rides one auto-resizing frame with one base size and one map ceiling, a taller one pushing the map rather than covering it, and the corners square at every size after the device look rejected the round-once-grown effect — detail in [`FEAT_DSC_Ui_Dashboard.md`](xTrack/Ui_Dashboard/FEAT_DSC_Ui_Dashboard.md) | 2026-05-15 00:00 | 2026-10-03 17:51 | active |
| Ui_General | App-lifecycle UX — back, insets, lists, drawers, banners, the dashboard close rules; the bottom band's action and undo toasts wear the banner family's own skin, and the dropdown now takes one drag to open, carries no arrow, centres its wheel on the box and shares one text alignment between the field and its rows — detail in [`FEAT_DSC_Ui_General.md`](xTrack/Ui_General/FEAT_DSC_Ui_General.md) | 2026-06-08 16:43 | 2026-10-05 09:53 | active |
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
| Ui_Menu | Hamburger menu drawer — a Routing card holding the route's ends, a quick access to the cruising speed and the driving preference, the live recording and route blocks in one banded treatment on a sub-card, and marker management | 2026-07-05 06:57 | 2026-10-04 12:16 | active |
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

- [ ] **Tasker water-state bridge — must be resolved before the power service becomes conditional** — `TrackRecordingService` answers `ACTION_QUERY_WATER_STATE` from a runtime-registered receiver and holds `lastKnownOnWater`; the Performance power work plans to run the service only while a keep-alive reason holds, at which point the query goes unanswered while the app is idle. Detail in `xTrack/Performance/260912_FEAT_PLN_Performance_power-management-centralization.md` §6 and `xTrack/Tasker/260628_FEAT_PLN_Tasker_tasker-water-state-integration.md`.
- [ ] **`#new` should warn before switching and offer to overwrite the local branch** (raised 2026-09-19): pin what the warning says and what overwrite means, then land it in `AGENTS.md` §7b and `docs/cmd_help_git.md`.

## Cross-Reference Docs
Docs available via `#doc read [name]` from any feature. Fuzzy-resolve searches this table.

| Doc | Owner Feature | One-Liner |
|-----|---------------|-----------|
| `color-scheme.md` | ColorManagement | Color tokens, palette, alias chains |
| `material-icons-standalone-guide.md` | Ui_General | How to add Material Symbols icons as standalone ImageVector .kt files |
