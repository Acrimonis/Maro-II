# Global Context — Routing Table
  
> State only — routing map, feature summaries, focus history, global todos, doc index. Rules and instructions live in `AGENTS.md`.

## Focus History
- [2026-10-20 20:59 UTC] Route — a pair now arms as one line from either end on `feature/route-sym-2`: the search leaves the end that sorts first, the two pulls run both ways and keep the cheaper, and a fine copy has its step back out, so over the baked-region survey the plan-pair runs over the bar went 5 → 0 and the worst line gap 985 → 0.4 m; the two-sided pull doubles the pull, making a ladder 64–79 % slower on a desktop JVM, the device's timing owed — full unit suite and `assembleDebug` green, no dependency added, the device pass the user's → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-20 19:40 UTC] Ui_Settings — the Layers card is retitled **Routes and Tracks** and Routes Appearance now leads the two collapsibles, the key renamed with expand state kept → xTrack/Ui_Settings/FEAT_HYD_Ui_Settings.md
- [2026-10-20 19:39 UTC] Ui_Menu — the routes list dropped its inline All/Pinned/Not pinned row, that axis now living only in the filter popup → xTrack/Ui_Menu/FEAT_HYD_Ui_Menu.md
- [2026-10-20 18:07 UTC] Route — the 20:01 device search's Closest line crossed 0.6 m water through the berth carve's unchecked hop; the carve is retired on `feature/route-low-depth-check`, so the gate waives only its 20 m end disc and no leg past it crosses water under 3 m, the boxed-in allowance kept as the only exit; the unrequested start note and the destination note under the route table removed — 1404 tests green, `apk-build.bat` packaged, the coast-boxed harbour end left open → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-19 20:08 UTC] Route — on `feature/route-low-depth-check`: the measured depth gate (committed `6475d327`), then enforced end to end; then for the Bacon – La Grenille channel the trace that reads a missed passage, route ends held 25 m clear of shore and shallows, and the router's depth read filling ignored EMODnet and missing nodes at the gate depth (the channel opens in the probe and the engine fixture), the EMODnet cutoff's default at 3.0 in `maro.properties`, and the fine-tile store kept per installed build with no format version — reviewed at every step, 1408 tests green, `apk-build.bat` packaged; the smoothing plan waits on the user's call, centring and the no-way start on the channel's device run → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-19 18:43 UTC] Route — the route search panel now reports each route's own search on `feature/route-gogo`: every rung narrates its stage (bold italic in the rank's slot until it lands), a landed row prints its live `#rank` with a merged route counted once, the header reads `Landed x/n · speed · preference`, a first provisional pair rides the search → pull boundary, the rows reorder only on a landing, refusal or merge, a hand pick holds until the last landing selects the top row with a toast, and the crown's check waits for the end; then the menu drawer lost its summary card during a search (it stays while a route is followed), *Save to track* waits for a landed line, the snack action reads *New*, and the fan's *Route (auto)* child went live mid-search to follow the winner at the end with a toast — full unit suite and `assembleDebug` green, no dependency added, the device pass the user's → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-19 07:40 UTC] Route — the menu's Go became a true restart on `feature/route-gogo` (R101): from any state it ends the standing mode and arms afresh, an unwritten followed line asking first through the exit dialog's new `RESTART` reason (*Save and restart* · *Keep following* · *Discard route*, the save arming only after the write lands), a too-short pair refused before anything ends, and a "new route search started" notice on the map banner; the copy pass renamed *acquisition* to *route search* in the user-facing strings and French to *route* throughout the app — `:app:compileDebugKotlin` and the `ui.map` suite green, no dependency added, eight shipped plans archived with their open points promoted, the device pass the user's → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-19 06:35 UTC] Ui_Settings — `maro.properties` gained 61 `# tldr;` lines, plain-language notes on what each technical key changes for the user, wrapped at 100 characters; comments only, no key or value moved → xTrack/Ui_Settings/FEAT_HYD_Ui_Settings.md
- [2026-10-18 13:08 UTC] Ui_Menu — the menu's Routing card gained a Go button right of the cruising-speed/slow-water prefs, arming a fresh acquisition on the standing pair through the same single trigger the map square and the fan child share: a new `onGo` on `MenuDrawerOverlay` threaded through `OverlayLayer.onArmRoute` to `MapScreen.armRouteMode(forceFresh = true)`, the button a dedicated `GoButton` wearing the wheel boxes' own height, and `route_go` in both locales — `:app:compileDebugKotlin` green, no dependency added → xTrack/Ui_Menu/FEAT_HYD_Ui_Menu.md
- [2026-10-18 12:44 UTC] Tracks — the two kinds' ends share one smallest-zone rule: `MarkerMatcher.topNamingMatch` picks the smallest zone (inside before near on equal size, a pin first), read by the track's `endAt` and the route's `routeEndName`, and a track's title body now carries only markers an idle stop stood in, dive-first then longest-idle — `testDebugUnitTest assembleDebug` green, no dependency added, the device pass the user's → xTrack/Tracks/FEAT_HYD_Tracks.md
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

**Route** · **status:** active · **created:** 2026-08-16 10:44 · **modified:** 2026-10-21 08:16
- The fan's *Route (auto)* child is live while a search runs: a press keeps the search, closes the fan, raises a toast and follows the winner when the last route lands; the menu drawer shows no summary card during a search, *Save to track* waits for a landed line, and the snack action reads *New*.
- Each route in the search panel reports its own stage (bold italic) until it lands, then its live rank — a merged route counted once — under a `Landed x/n · speed · preference` header; the rows reorder only when a route lands, is refused or is merged away, a hand pick holds until the last landing selects the top row with a toast, and the crown's check waits for the end.
- A saved route's two ends resolve through the smallest-zone rule shared with tracks, the home port excluded, so the name reads `A to B`, `To B` or `From A`, falling back to `Route <instant>` when neither end is named.
- The drawer's Go is a true restart from any state (R101): an unwritten followed line asks first through the exit dialog's `RESTART` reason, and the user-facing word is *route search*, French saying *route* throughout.
- Set a destination and the app draws the route from the boat to it, then saves that line as an ordinary track.
- Three engines stand on one `RouteEngine` seam — `dummy` (a straight line), `avoid` (the corridor A\* pipeline) and `evolutive` (the two-layer adaptive grid), the `selective` row and its depth price retired on 2026-10-16 — each arming a three-rung ladder named **Detour · Balanced · Coastal**, whose λ live in `route.avoid.ladder.around|best|fast.lambda`, while the `Slow water` dial states one weight on the seconds a line spends under a limit — each second weighted by its own limit's shortfall — and the crown is the lowest sum of its trip time and that weight times those weighted seconds, the panel ranking the landed rows `1..k` live and checking the crowned one once the last has landed.
- A costed marker prices the sea as a well, full inside its body and falling to nothing at its proximity edge, while a Blocked marker is a body-only wall and a Pin is costable and never a wall; the price's own report names the **marker arm**, beside the ring, band and depth arms it now stands with.
- The fine layer is a pace-free, on-disk tile store behind a **soft** 96 MiB `route.fineTile.maxBytes` ceiling that never reclaims the home area (`route.prewarm.box`), whose entry names the app edge derives from the seed ring and protects before the warm walk starts; its reclaim walks one access-ordered index rather than listing and sorting the directory, `list()` still reads the directory because the warm-up treats a name as proof a file stands, its first reclamation is both toasted and pushed as a notification behind one `POST_NOTIFICATIONS` ask at startup, and a startup pre-warm grows the area outward in rings until the `route.prewarm.targetPct` share or the map stops it — the rest standing as a reserve for water the seed area does not cover — owning the routing square's white dot on the app's one shared pulse.
- The corner pass rounds a line's bends from what the line gives it — the fit's reach takes the whole significant leg, a close same-way pair reads as one turn, two bends on one leg share the room by their own angles, the corner floor is a target the room may draw under, the coast test is relative to the corner it replaces, and the overlap guard measures bow to bow.
- The followed route mirrors its two ends in place when the time-to-go loses ground (R99) and raises the exit dialog under an arrival cue (R100).
- A zone's `regulatedZone.speedRoute` override is a **price only**: the search keeps its advantage while every reported figure — the clock, the attribution and the line's colour — times the **displayed** limit, through one lambda (`limitAtFor`) whose zone arm is `MultipassWorld.zoneDisplayLimitKnAt`; the report now carries **two facts** — the crossings the price forced, and the slow water the line entered, read off that same displayed limit on one shared entry walk — both records, neither reaching a proto, and no composable prints either of them yet.
- **The depth gate's standoff is measured on the traced 3 m line** (2026-10-20): every guard reads one `GateRule` — a 10 m wall on `evolutive` with the berth's steep 10–20 m inner ramp and its 20–50 m outer ramp, the 20 m margin itself the wall on `avoid` — so no line comes within the wall of water under 3 m except within 20 m of its own ends, a 50 m passage always holds a legal line on `evolutive`, and the medial nudge moves a passage's line onto its middle; each end's own first step keeps the wall past the waiver unless the shallows box that end in, every straight the corner pass draws is measured, and the sweep's allowance never trades the berth; the fine 20 m layer still follows shallow water to `depthCollarM` 170 m, a **Douglas–Peucker** pass still drops the settled line's surplus points, and the pull's guard still compares **total time**.
- The pull's settled lookback now carries a **window and an allowance** (`route.avoid.pull.allowanceShare`, a flat-run gate, a bounded span) so a flat wiggle falls to one chord where the shortcut is barely dearer, and the replay over that capture measures it at **three of forty-eight** refusals flipped, the plan's own ordered floor being the half that mattered and the half that was dropped.
- The cruising-speed scale opens on **`Any`**, the absence of a preference: the search then plans at the boat's own pace, the shipped `route.freeWaterPaceKn` standing in until the boat has evidence, while a numbered stop keeps today's behaviour — the Settings row says `Any` and the drawer wheel `--Any--`.
- The map's route fan now reads **Route auto · Route · Follow · Save · Discard** — the unused save-and-exit child is gone, Route arms the acquisition alone, and a new Follow child below it takes the settled or partial line (2026-10-17).
- **The route search ignores what the chart ignores** (2026-10-20): an EMODnet reading under the cutoff (shipped default `depth.emodnetShallowCutoffM` = 3.0) and a node with no data enter the router's blend at the gate depth, so they never wall a route nor drag a real sounding beside them under the gate — the Bacon – La Grenille channel, closed by such readings, opens; route ends stand 25 m clear of shore and shallows (`route.repair.clearanceM`), and the fine-tile store keeps one installed build's entries, with no format version.
- **A route's ends carry no escape hop** (2026-10-20): the berth carve is retired, so the depth gate waives only its 20 m disc round each end and every leg past it is measured — the device's Closest line had crossed 0.6 m water on the carve's unchecked hop; a gate-boxed end keeps its allowance as its only exit, while an end the 50 m coast margin hems in now fails as no path, and the route table carries no moved-end note.
- **A pair arms as one line from either end** (2026-10-21): the search leaves the end that sorts first, the two pulls run both ways and keep the cheaper line, and a coarse block's fine copy has its step back out, so the gated 24-pair survey's bar misses (same crown, 100 m, 30 s) went 5 → 0 with a worst line gap of 0.4 m; the two-sided pull doubles the pull, which is most of the coarse pass, so a ladder is 64–79 % slower on a desktop JVM and the phone's `PULL ms` is owed, and one `evolutive` pair still crowns a different rung each way on equal lines.
- Smooth driving waits on the user's call: half the bends stand as angles because a bow's floor is anchored at its corner while the legs beside it already come nearer the wall, and the plan proposes reading it off the stretch the bow replaces.
- Owed: the measured gate's device pass (the Bacon – La Grenille pair among it), the choice of whether `avoid` — the default row, now strict at 20 m on 100 m cells — takes the berth's floor and ramp, the inner ramp's rate on the device's `innerM`, and the straightener's epsilon on device.

**Ui_Dashboard** · **status:** active · **created:** 2026-06-06 00:00 · **modified:** 2026-10-12 08:58
- The bottom panel is a HUD for quick reading — distance from the coast, distance to the 300 m zone with a speed warning, depth under the boat with its source, and speed.
- Every portrait dashboard rides one auto-resizing frame with one base size and one map ceiling, a taller one pushing the map rather than covering it, with square corners at every size.
- On 2026-10-12 the Speed tile reads whole km/h while the dashboard position is on land and knots at sea, on one `KMH_PER_KNOT` / `knotsToKmh` home and a `dash_value_kmh_int` key in both locales.

**Ui_General** · **status:** active · **created:** 2026-06-08 16:43 · **modified:** 2026-10-17 06:48
- App-lifecycle UX — the back-exit guard, edge-to-edge insets, list normalization, the drawer framework and menu-drawer UX.
- Every settings slider renders through one row — the two render-count rows and the boat size included — with the value bottom-aligned against its track and a unit suffix read from a `*_fmt` key rather than formatted in code.
- The popup takes one drag to open, carries no arrow and centres its wheel on the box; the band is the value, committed on the release by any door while back alone closes without a write, and the popup fades and rises 120 ms each way with its fading window refusing every touch.
- Each list card is entered by a leading selection door, the picked card wearing a 2 dp accent border, the 15 % shift and a 24 dp check disc at the head of the first line.
- The delete family runs on one pending set with one hiding rule — a card's deferred delete hides its item on the map while a list swipe never does.
- The selected-item dashboard closes on exactly two conditions: a surface wanting its slot, or a change of the world its Prev/Next walks.

**Markers** · **status:** active · **created:** 2026-06-22 11:52 · **modified:** 2026-10-12 07:53
- User markers (Pin/Circle/Corridor) with a wizard shell, where-am-I and a management list, all drawn on osmdroid overlays.
- A marker may exist for its routing weight alone — `routingOnly` is additive, excluded from where-am-i by one pure predicate, set by the wizard's Only row and drawn dashed-and-hatched under one `MarkerAppearance.isRoutingOnly` flag.
- The marker import/export transfer writes one GPX per marker with a base64 protobuf payload and a readable JSON mirror, a ZIP batch that always skips, and the three doors (card share, list export and a Settings transfer card).
- A marker's dashboard open frames its body plus the proximity collar (the pin's zoom-in-only rule dropped), and a plain marker tap no longer opens the card.
- The card header's route-role marks are the 🚩 / 🏁 emoji beside the 💲 cost mark, and the marker filter's three routing axes stand under one `Routing` title.

**Tracks** · **status:** active · **created:** 2026-06-15 21:43 · **modified:** 2026-10-18 12:44
- Track the boat's movement during navigation: one recording per trip, points captured only while moving, persisted as protobuf and recallable on the map.
- A route and a recorded trip are named from their ends and idle stops: an end resolves to the smallest zone it stands inside — or, only when strictly smaller, one it stands near, a pin first — the body carries only markers an idle stop stood in (dive-first, then longest-idle), a distinct marked destination closes the name, and an end at the home port never names either kind.
- The two render axes per kind run on a global → kind → class `enabled` leaf, and the path render engine is one painter for both kinds' lines over the `path.*` family.
- The render count of either kind runs 0–30 plus an **All** value that drops the cap, pinned items having always escaped it, and the newest→oldest fade spans the not-pinned set actually drawn.
- A trip's GPX carries the markers that own it (their payloads in the track's extensions plus one `wpt` each), re-pointed at the landed track id on import.

**Performance** · **status:** active · **created:** 2026-06-07 00:00 · **modified:** 2026-09-20 13:50
- Battery/performance pass over the GPS plugin — tunable acquisition via presets and sliders, movement-adaptive idle, a capped map-refresh applier and compass gating.
- Power management is centralised in `data/power/`: `PowerPolicy` (framework-free) plus `PowerKeeper` hold the screen flag through a single mutation point, and a pushed speed is never read as a fresh one.
- The map layer cost pass removed the per-layer zoom rebuilds and attaches the contour polylines once, switched by their own `enabled` flag.

**Ui_Settings** · **status:** active · **created:** 2026-06-09 15:28 · **modified:** 2026-10-20 19:40
- Settings page UI, SharedPreferences persistence and the settings widgets, with all rendering rules deferred to `docs/ui-component-guidelines.md`.
- The four tabs derive their order, labels and pager from one ordered `SettingsTab` enum, and Position was renamed Routing and took the Route section.
- The Layers card is titled **Routes and Tracks** and reads Routes Appearance → Tracks Appearance → Speed Arrows Settings → export/import, each Appearance block opening on its own two-chip Speed Display row.
- The Tracks/Routes appearance settings seed from the unified `path.*` family, and `maro.properties` was taxonomised into eight subject domains.
- `maro.properties` carries a `# tldr;` line under every technical comment, stating in plain words what the value changes for the user.

**UI_Map** · **status:** active · **created:** 2026-06-07 00:00 · **modified:** 2026-10-17 13:06
- Map display layer management — the depth and colour-depth layers, overlays, inspect mode, the toggle squares and the drawer ladder, with the shipped calls grouped by subject in `FEAT_DOC_UI_Map_decisions.md`.
- The layer fan is the master display gate for routes and tracks, the open dashboard's own item its one exception, and the two header eyes stay dimmed but tappable beneath it.
- Inspect mode measures from the point under the marker captured at arming, opens the nearest item at once, and after a 1499 ms pause recentres and freezes the ladder over every filtered item the map holds — the walk then owns the card and the gold, with no viewport cut.
- The map dash family rides one frame (one header, one dissolve, square corners, one walk row) and the toggle squares resolve through one pure `MapToggleFace`.
- The bottom band ends on the map gutter in both orientations, so the bottom-left tag column and the zoom stack share one bottom line and landscape adds no navigation-bar inset.

**WorkflowImprovement** · **status:** active · **created:** 2026-06-03 00:00 · **modified:** 2026-10-14 07:59
- The xTrack `#`-command system and rulebook governance, with the Claude Code agent adapters in `.claude/agents/`.
- The rulebook gained a five-tier legend (🛑 · ⛔ · 💬 · 🧹 · 🟢), and `#rule` was rebuilt around it — bare prints the legend, a tier reloads it with a fingerprint, and `all` reports staleness.
- `#new` cuts `feature/[branch]` with `--no-track` so the first `#push` writes its own upstream, and `#rename` carries a published rename to the origin on one question.

**Navigation** · **status:** active · **created:** 2026-06-10 08:40 · **modified:** 2026-09-16 17:10
- Navigation aids on the map overlay — the heading/speed cap arrow and the dashed direction line from the boat marker, plus auto-show of zones on approach.
- The arrow hides below 2.5 kn, draws straight up (heading-up rotation aligns heading with screen-up), and scales its length with speed and zoom from `maro.properties` keys.
- Auto-show drives memory-only overlay flows and never the user's persisted visibility setting, and the dashboard's shore pipeline reads `dashboardPositionFor(marker, boat, gpsMode)` so a dragged map cannot move it.

**RegulatedZones** · **status:** active · **created:** 2026-06-11 18:00 · **modified:** 2026-10-13 19:47
- French coastal regulated zones (Nice–Fréjus corridor) — speed limits, anchoring and access restrictions — gathered offline, aggregated into a baked asset and drawn as a map overlay.
- The INSPIRE bake parses the real shapes: the whole `restrn` list with speed-wins typing, `ACHARE` anchorages, `Point` buffering and `MultiPolygon` splitting, with a server-side bbox.
- The set is app-controlled by the `zones.properties` asset — `regulatedZone.speedOverride.<key>` replaces a limit (or makes a zone a speed zone), `regulatedZone.speedRoute.<key>` steers only the route engine, and `regulatedZone.ignore.<key>=true` drops a zone.
- The warning strip sits bottom-left, fed by the zones containing the marker rather than the boat and never gated by the layer's visibility.

**ColorManagement** · **status:** active · **created:** 2026-06-16 14:05 · **modified:** 2026-09-16 15:00
- All colour tokens live in `colors.properties` with `${key}` alias interpolation, and every change is reflected in `docs/color-scheme.md`.
- One painting path and one property block (`ui.map.surface.*`) now own every map surface, the toggle squares and both overlay cards included.

**ZoneTile** · **status:** active · **created:** 2026-06-17 09:45 · **modified:** 2026-09-16 06:31
- Zone information tiles and map overlay rendering — the zone-ahead cone/line, the information cards, speed-zone display and ETA.
- The zone-info line now reads the `ui.map.overlay.*` family over the shared `ui.map.surface.inactive`, so both bottom-left cards share one surface.

**Tasker** · **status:** active · **created:** 2026-09-12 08:50 · **modified:** 2026-09-12 08:50
- An external automation bridge publishing the boat's water state — architecture approved, not implemented.

**Mergitur** · **status:** active · **created:** 2026-09-11 20:04 · **modified:** 2026-09-11 20:57
- The three-branch integration epic that consolidated `feature/tracks-recording`, `feature/tracks-import` and `feature/global` into `feature/mergitur`, one merge commit per hop (TR → TI → GL).
- Complete; what remains is pushing the branch and opening the PR to `develop`, plus a device smoke test.

**TracksImport** · **status:** active · **created:** 2026-09-11 20:28 · **modified:** 2026-10-10 07:19
- Map track visibility is derived rather than persisted — the `visibleOnMap` flag replaced by a shared, unit-tested `MapSelectionPolicy` driven by `MapRenderFocus`.
- The open card's item is drawn past the map filter and past the render cap, one id and only while its card stands (reversing 2026-09-28 in part).
- The GAP-split polyline rendering was extracted into `MapTrackSegments`, and an on-demand GPX off-route cleanup harness ships with four test classes.

**Ui_Menu** · **status:** active · **created:** 2026-07-05 06:57 · **modified:** 2026-10-20 19:40
- The hamburger menu drawer — the Routing card (ends, quick access and live summary), a Routes list, a Tracks list and marker management.
- The Routing card's quick-access pair gained a Go button on its right, arming a fresh acquisition on the standing pair through the same trigger the map square and fan child share.
- The ROUTES and TRACKS headers each carry a map-visibility eye outside the filter gate, and the two lists are hard-separated by a `ListScope` over the one `TrackHistoryOverlay`.
- The route Speed/Direction switches are subordinate to the master Speed Colors and Arrows chips, and a pinned route stays dashed by its own route identity.
- The routes list carries no inline pin filter — its All/Pinned/Not pinned choice lives only in the filter popup.

**ArcLayout** · **status:** active · **created:** 2026-06-13 07:34 · **modified:** 2026-09-29 11:35
- The map's layer toggle is one anchor button fanning out into a pure-Compose arc menu holding the layer toggles.
- Fan geometry is θ-parameterised with the parent at centre and equal radii, and a momentary mode (`toggleChildren = false`) makes a fan whose children are actions, as the Route fan uses.

**Documentation** · **status:** active · **created:** 2026-06-11 06:42 · **modified:** 2026-10-10 11:53
- Cross-cutting project documentation — the README as orientation only, machine onboarding and troubleshooting, the git detail, the feature-to-code map and the maintenance of `docs/`.
- `docs/maro-code.md` is the single source of truth for feature-to-code mapping and was re-verified against the tree on 2026-10-10, and no doc restates a version or an inventory the build files hold.

**DepthMapping** · **status:** active · **created:** 2026-06-04 00:00 · **modified:** 2026-06-10 12:13
- Bathymetry and depth mapping across 0–60 m from Litto3D, SHOM and EMODnet, with shoalest-wins precision tiers and per-cell source and confidence.
- Rendering is a hypsometric `DepthColorRamp` plus isobaths, and an EMODnet shallow gate applies to the colour map, the warning overlay and the isobaths, while the cold-start heap work stays in design.

**GPS** · **status:** active · **created:** 2026-06-07 00:00 · **modified:** 2026-08-15 15:21
- A position-source toggle between Démo (free-pan, north-up) and GPS mode, where the device GPS drives the map centre and heading-up rotation with a compass fallback when stationary.
- Location uses the framework `LocationManager` / `SensorManager` (no Play Services), and dead-reckoning extrapolation is display-only, gated out of persistent track recording.

**BakeNormalization** · **status:** active · **created:** 2026-06-08 00:00 · **modified:** 2026-06-08 17:10
- The asset pipeline is split into granular `bake-[type].bat` scripts behind a single interactive `apk-bake.bat`, an `apk-build.bat` that only packages and an `apk-deploy.bat` that installs and relaunches.
- One region bounding box source of truth (the W/E gradle props), the depth envelope derived as coastline bbox plus 6 NM, and no baked data in git.
- Litto3D coverage is fetched west only, so the east (Antibes→Nice) shallow tier is still missing.

**DepthSafety** · **status:** active · **created:** 2026-06-08 18:25 · **modified:** 2026-06-09 12:01
- Four gaps of the depth map as a safety surface, delivered as four branches — water-only colour, isobath precision by source, the magenta low-depth display, and the shallow-water alarm (B4, still owed).
- Isobath colour states the data source (EMODnet dark blue, Litto3D forest green), and a coarse-EMODnet shallow gate covers the readout, the colour map, the overlay and the isobaths.

**Coastline** · **status:** active · **created:** 2026-06-05 00:00 · **modified:** 2026-06-23 07:08
- The coastline pipeline ingests continuous land polygons (OSM `natural=coastline`) and now also buffers isolated point hazards into micro-circle rings merged into the land set.
- Hazard rings come from the Shom Aton WFS with hard-coded seed fallbacks (Fourmigue, Batéguier, Tradelière, Chrétienne), and the region extends east to Menton.

**CheckDev** · **status:** active · **created:** 2026-06-20 11:42 · **modified:** 2026-06-20 11:42
- Dev-branch health monitoring — remote branch state, ahead/behind analysis and workflow hygiene, scoped with its todos still open.

**Health** · **status:** active · **created:** 2026-06-20 11:42 · **modified:** 2026-06-20 11:42
- Application health monitoring — diagnostics, crash reporting and telemetry — scoped, with no work landed yet.

## Global Todos


## Cross-Reference Docs
Docs available via `#doc read [name]` from any feature. Fuzzy-resolve searches this table.

| Doc | Owner Feature | One-Liner |
|-----|---------------|-----------|
| `color-scheme.md` | ColorManagement | Color tokens, palette, alias chains |
| `FEAT_DOC_Route_engines.md` | Route | The acquisition engines as they stand: seam, rows, ladder, pipeline, plan seam, water, readings |
| `material-icons-standalone-guide.md` | Ui_General | How to add Material Symbols icons as standalone ImageVector .kt files |
