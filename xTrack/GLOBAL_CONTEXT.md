# Global Context — Routing Table
  
> State only — routing map, feature summaries, focus history, global todos, doc index. Rules and instructions live in `AGENTS.md`.

## Focus History
- [2026-10-16 18:52 UTC] Route — one session, three orders and a device reading. **Plan 5 was reviewed and corrected** — ten stale anchors re-pointed, one of them pointing at deleted code, its premises re-tested, and the 2026-10-07 order recorded as still unmet on the two engines that remain — and attached to the feature's docs. **Then the depth band landed on `evolutive` alone**, depth-valued and charged per cell: `route.evolutive.depth.reachM` (25 m of depth above the gate's floor) and `route.evolutive.depth.priceSecPerM` (0.05), the law `depthBandPriceSecAt`, the seam `RouteGridPlan.depthBand()` — `null` for `avoid` — λ-free seconds per cell scaled by the rung's λ at read, its own `PriceArm.DEPTH`, the tile key carrying both values with the codec at v4, `avoid` untouched and pinned; `:app:testDebugUnitTest` green at 1299 tests with none failed and `apk-build.bat` packaging. **The two keys were then renamed for taxonomy** with the drawn line unmoved and still 1299 green. **And the user's own device pass** of two acquisitions at λ 1·3·5 on a 5 kn pace (`route_depth_pass.log`) reads the band as the only price in play and as a **pockets** price: the drawn edge carries a jagged run of ten bends with 20–40 m legs held by chords dearer by 16 ms and less, half the refusals being clearance rather than price — so the fix is filed **in design** as a windowed taunt with an allowance, generic to the pull and cost-bounded, with the band's reach against the ordered 45 m, its per-cell unit, its clearance cost and its gate-riding switch left open → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-16 16:27 UTC] Route — the warm-up's two unshipped pieces landed: the fine-tile store's disk ceiling rose 48 → 72 MiB at its one home `route.fineTile.maxBytes`, with `AppConfig` and `DirectoryFineTileStore` mirrored in step, and a new `route.prewarm.targetPct` (80, clamped 10-95) holds growth to a **share** of that ceiling so the rest stands as a reserve for water the seed area does not cover; the store's inequality gained one home — `StoreFullness` carries the size of the entry it wrote last and answers `hasRoomAt(share)`, both gates ask it, and `WarmStop.CEILING` became `TARGET` — while the abandoned candidate-dot plan was deleted as such material, `:app:testDebugUnitTest :app:assembleDebug` is green with the APK repackaged, the branch was then rebased onto `develop`'s seven commits with four conflicts resolved by hand (two Zone records, the properties file and the hydration) and the rebased tree re-gated green at 1286 tests, none failed, and the plan's `## Implemented`, the feature file, the engines doc and the hydration carry the record → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-16 13:43 UTC] Route — a session of three `#impl` landings. **The price report learned to say who charged it**: a marker-priced point names its own `PriceArm.MARKER` off the same lambda that charges it, and the two statements claiming a surface read the report were corrected. **Then it learned to speak presence beside the price**: `forcedCrossingNames` keeps filtering on the **price** while `crossedSlowZoneNames` names the zones the **drawn line enters** whose **displayed** limit stands below the pace, the two sharing one entry walk, carried as records (no proto, the saved-route sites empty, the loss mirror unchanged) and printed as `crossed=[…]` beside `forced=[…]`. **And the third engine is gone root and branch** — `selective`, its four collars, its depth price, its seven `route.selective.*` keys, its label and its two suites, on the user's word that its performance did not earn its place, with the depth gate's **wall** and its **margin** kept at the shared level for `avoid` and `evolutive`, the tile codec at version 3 so a tile it wrote is a miss, and a stored `selective` id witnessed to fall back to the shipped default; the retirement's two owed reads were taken first — the adaptive plan's halo was already zero, so its line could not move — and every hop ran the full pipeline with the unit suite and packaging green (1281 tests, none failed, at the last). The corpus followed each landing: decision 5 for the presence fact, decision 6 for the retirement and the 2026-10-07 scoping it reverses, three engine rows in the state doc, the engines doc's plan seam and depth sections rewritten, the Route card at three engines, and `docs/maro-code.md` trimmed. One question is left standing — whether a soft depth preference returns on `evolutive` — and the retirement's health notes are on decision 6 → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-15 15:09 UTC] Route — the acquisition's crown became **one number the user sets, on lines the panel now ranks**: the Driving preference and the slow-water budget are gone, replaced by a `Slow water` row whose six stops (**Fastest** · 1x · 2x · 3x · 4x · **Ignore time**) state how many minutes a minute in slow water counts as, `VerdictDial.kt` holds them with the sentinel as a `null` weight, `route.avoid.slowWater.weight` is the default (4) and the stored value one clamped stop index with the retired cursor mapped onto it once; the crown is **one sum** — trip time plus that weight times the line's own seconds under a limit, **each second weighted linearly by its own limit's shortfall**, `(pace − limit) / pace` floored at zero (0.8 at 5 kn, 0.2 at 20 kn on a 25 kn pace, nothing at or above the pace), defined once in `slowWaterSeverity()` and read at the engine's cost site where each per-limit bucket takes its own limit's severity and the ramp half rides the limit its slow run serves, a leg meeting no limit weighing nothing — while the published per-limit table stays plain seconds, so the panel's minutes, the drawing, the clock and the dial are unmoved; the terminal carries `RouteVerdict` — the crown and every landed rung's **distinct rank** `1..n`, the 30 s tolerance deciding what a tie is and the crown staying that group's head while the order inside a group runs the exact score, then fewer weighted seconds, then the shorter trip, then the row that landed first — and the panel closes the crowned row's name with a checkmark in the route accent, puts its **rank on the row's second line** (the ` so far ` word's own slot while the ladder computes, one slot and one clock), states the dial's word in the header, and keeps the selection's taken-choice face so the two marks can stand on different rows; the three rungs are renamed **Detour · Balanced · Coastal** (`Détour · Équilibré · Côtier`), the seat mapping, the budget's key, row, messages, predicates and dormant result field left end to end in both locales, and a consistency sweep cleared every residue its reviews named (the row's sentence, the ladder's code defaults to the file's 5 / 3 / 1, the fold tolerance's fallback to its 200 m with the perf harness re-anchored, three toothless checks repaired) with the corpus following; the severity law first went in without an order, was withdrawn, and was then ordered on `#impl` and re-landed with one correction — a leg meeting no limit counts for **nothing** rather than whole; branch `feature/rte-algo` cut from `origin/develop` by the user's `#new`, then **rebased onto `origin/develop`** on `#merge` with the three conflicts resolved under the matrix and pushed as `c71e7be6`, the whole unit suite green and the APK packaged at every hop → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-15 12:57 UTC] Route — the fine layer's pre-warm became the app's own background job: it starts once both baked layers can answer instead of waiting for the boat to be idle, seeds the walk on `route.prewarm.box` and grows the area **outward in rings** of the plan's own lattice until the store's ceiling, the baked region's edges or the pass's budget stop it (no radius dial), on a dispatcher of its own whose workers carry a background CPU priority and whose width is `route.prewarm.threads`, yielding to an armed route and to a depth-raster regeneration and **standing down** on battery-saver; the store answers **how full it is** as a live verdict on the tile-store seam (`fullness()`, unknown rather than zero where there is no store), the routing square's white dot now means the warm-up **alone** — the arm's per-lookup feed removed, lit per tile it really builds and dark while it waits — and it beats on the app's one shared pulse, the halved loading beat deleted; the Ask hop's eight defects are fixed, `:app:testDebugUnitTest :app:assembleDebug` is green at 1271 tests with the APK repackaged, the ceiling's rise to 72 MiB and the settled 80 % growth target stand **planned** in the plan of record, and the warm-up's device pass is the user's → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-14 13:37 UTC] Route — the tile store's first reclamation now says itself twice: the toast it always raised, beside a notification on the app's single channel (`TrackRecordingService.CHANNEL_ID`, made readable so the id keeps one literal), its own id 1002, silent and auto-cancelling with a tap opening the app, posted only while notifications are enabled and reached behind one `POST_NOTIFICATIONS` ask at startup fired from the composition's head on API 33+; and the store's disk ceiling moved from 16 to 48 MiB at its one home `route.fineTile.maxBytes`, with `AppConfig.routeFineTileMaxBytes` and `DirectoryFineTileStore.DEFAULT_MAX_BYTES` mirrored in the same write and a new `FineTileStoreCeilingPropertiesTest` holding all three; no string key joined either locale, the in-memory 8 MiB ceiling is untouched, both surfaces ride the store's one once-guard, `assembleDebug` and the scoped suites are green, and the alert's visibility is the user's own device pass → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-14 12:31 UTC] Route — the lever of each of the three acquisitions is named and moved into the file: the three rung λ became `route.avoid.ladder.around|best|fast.lambda`, read through three `AppConfig` accessors and published by `RoutePreference`, whose entries hold no number — and the aversion dial's role is stated where it is read, `seat not lever`, across the properties blocks, the `AppConfig` and `SettingsManager` KDocs, the avoid-algorithm doc's retired λ loop and four code comments; branch `feature/route-aqu-x3` cut from `origin/develop` by the user's `#new`, `:app:compileDebugKotlin` and the whole unit suite green → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-14 12:31 UTC] Route — the route's clock and its search now read **different limits**: a zone's `regulatedZone.speedRoute` override is a **price only**, so the search keeps its advantage while every reported figure — the leg speed, the trip's clock, the line's colour and the **Speed limits** attribution — times the **displayed** limit; one new read, `strictestDisplayLimitKnAt` in `ZoneGeometry.kt`, stands beside the untouched `strictestLimitKnAt`, reached through `MultipassWorld.zoneDisplayLimitKnAt` whose `null` default makes an unwired world fail loudly, and the single `limitAtFor` lambda was re-pointed, so the provisional pair, the settled profile clock, the attribution and the corner tally move together while the grid's stored `ZoneRing` limits and the guard's read do not; the fine-tile key, the price path and both laws' non-emptiness are unmoved, the line colour and the saved track derive from the clock's own times so neither needed a change, `ZoneClockLawTest` pins the split in six cases with six world fakes wired, the four statements were rewritten at their homes (the generated `zones.properties` header with them) and the six scoped suites plus `assembleDebug` are green; two readers stay deliberately apart and open — the forced-crossing list still filters on the price and the rung ranking moves with the clock — while a hand-edited zero or negative `speedOverride` now reaches the clock unguarded → xTrack/Route/FEAT_HYD_Route.md
- [2026-10-14 08:54 UTC] Tracks — the render count of either kind went from 0..20 to **0..30 plus one All value** that drops the cap, so every not-pinned track or route is drawn while a pinned one still escapes; the bound has **one home** in `AppConfig` (`PATH_COUNT_MAX`, the `PATH_COUNT_ALL` sentinel and the pure `pathRenderCap()`), read by the `path.count` clamp, `SettingsManager`'s two, both sliders (32 integer stops, the label reading `settings_count_all` at the sentinel) and every cap site, while the **fade denominator became the drawn not-pinned set**, so All — and any count larger than the library — spans the ladder instead of flattening every line to the newest look; both locales learned the All key, `PathRenderCapTest` joined the uncapped `MapSelectionPolicyTest` case, the scoped tests and `assembleDebug` are green, the device pass is owed, and the implementation ran ahead of the user's word — kept on their instruction — before `#bake` and `#commit` were ordered → xTrack/Tracks/FEAT_HYD_Tracks.md
- [2026-10-13 14:37 UTC] Route — the acquisition's cost was measured on device: warm, `selective` costs 1.39× and 2.12× `evolutive` on the short and long pairs, the cold arm's clock being the fine-layer build alone; the fine layer then became a **store** that survives a restart, and the boat's **pace left the tile** so one cache serves every cruising speed, forced by one honest move on marker-priced water; the corner pass arrived by **merge** from `develop`; a white dot on the routing square learned, over three failed device looks, to report the store's own build, and the **pre-warm** now fills the Cagnes-sur-Mer → Cannes box at startup while idle, paused on an arming; and the corpus' Feature Summaries became **26 cards** — a metadata line then one-sentence bullets, relevance rather than a count as the discipline — which closed the 4 646-byte Route row four bakes could not edit, its dates re-synced to the feature files' front-matter after `git show` settled the migration 26-for-26 → xTrack/Route/FEAT_HYD_Route.md · xTrack/WorkflowImprovement/FEAT_HYD_WorkflowImprovement.md
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

**Route** · **status:** active · **created:** 2026-08-16 10:44 · **modified:** 2026-10-16 18:53
- Set a destination and the app draws the route from the boat to it, then saves that line as an ordinary track.
- Three engines stand on one `RouteEngine` seam — `dummy` (a straight line), `avoid` (the corridor A\* pipeline) and `evolutive` (the two-layer adaptive grid), the `selective` row and its depth price retired on 2026-10-16 — each arming a three-rung ladder named **Detour · Balanced · Coastal**, whose λ live in `route.avoid.ladder.around|best|fast.lambda`, while the `Slow water` dial states one weight on the seconds a line spends under a limit — each second weighted by its own limit's shortfall — and the crown is the lowest sum of its trip time and that weight times those weighted seconds, the panel ranking the rows `1..n` and checking the crowned one.
- A costed marker prices the sea as a well, full inside its body and falling to nothing at its proximity edge, while a Blocked marker is a body-only wall and a Pin is costable and never a wall; the price's own report names the **marker arm**, beside the ring, band and depth arms it now stands with.
- The fine layer is a pace-free, on-disk tile store behind the 72 MiB `route.fineTile.maxBytes` ceiling, its first reclamation both toasted and pushed as a notification on the app's single channel behind one `POST_NOTIFICATIONS` ask at startup, and a startup pre-warm that begins with the app, grows its area outward in rings until the `route.prewarm.targetPct` share of the store or the map stops it — the rest standing as a reserve for water the seed area does not cover — and owns the routing square's white dot on the app's one shared pulse.
- The corner pass rounds a line's bends from what the line gives it — the fit's reach takes the whole significant leg, a close same-way pair reads as one turn, two bends on one leg share the room by their own angles, the corner floor is a target the room may draw under, the coast test is relative to the corner it replaces, and the overlap guard measures bow to bow.
- The followed route mirrors its two ends in place when the time-to-go loses ground (R99) and raises the exit dialog under an arrival cue (R100).
- A zone's `regulatedZone.speedRoute` override is a **price only**: the search keeps its advantage while every reported figure — the clock, the attribution and the line's colour — times the **displayed** limit, through one lambda (`limitAtFor`) whose zone arm is `MultipassWorld.zoneDisplayLimitKnAt`; the report now carries **two facts** — the crossings the price forced, and the slow water the line entered, read off that same displayed limit on one shared entry walk — both records, neither reaching a proto, and no composable prints either of them yet.
- The depth band returned on `evolutive` alone — a cell pays `max(0, softDepthM − depth) × secPerM` for water shallower than the gate's floor plus its reach, λ-scaled and charged per cell, with the keys `route.evolutive.depth.reachM` and `route.evolutive.depth.priceSecPerM` — and the user's device pass reads the drawn edge's jagged run as its **pockets**, the pull stepping around them instead of leaning wide, with a windowed taunt and an allowance filed in design to smooth the drawing.
- Owed: the band's own device pass, the tight-line pass, the fine-tail and warm-up passes, with the live requirements R82 and R88–R100 and the engines reference held in the feature file.

**Ui_Dashboard** · **status:** active · **created:** 2026-06-06 00:00 · **modified:** 2026-10-12 08:58
- The bottom panel is a HUD for quick reading — distance from the coast, distance to the 300 m zone with a speed warning, depth under the boat with its source, and speed.
- Every portrait dashboard rides one auto-resizing frame with one base size and one map ceiling, a taller one pushing the map rather than covering it, with square corners at every size.
- On 2026-10-12 the Speed tile reads whole km/h while the dashboard position is on land and knots at sea, on one `KMH_PER_KNOT` / `knotsToKmh` home and a `dash_value_kmh_int` key in both locales.

**Ui_General** · **status:** active · **created:** 2026-06-08 16:43 · **modified:** 2026-10-10 08:25
- App-lifecycle UX — the back-exit guard, edge-to-edge insets, list normalization, the drawer framework and menu-drawer UX.
- The bottom band's action and undo rows wear the banner family's own skin, and the dropdown takes one drag to open, carries no arrow and centres its wheel on the box.
- Each list card is entered by a leading selection door, the picked card wearing a 2 dp accent border, the 15 % shift and a 24 dp check disc at the head of the first line.
- The delete family runs on one pending set with one hiding rule — a card's deferred delete hides its item on the map while a list swipe never does.
- The selected-item dashboard closes on exactly two conditions: a surface wanting its slot, or a change of the world its Prev/Next walks.

**Markers** · **status:** active · **created:** 2026-06-22 11:52 · **modified:** 2026-10-12 07:53
- User markers (Pin/Circle/Corridor) with a wizard shell, where-am-I and a management list, all drawn on osmdroid overlays.
- A marker may exist for its routing weight alone — `routingOnly` is additive, excluded from where-am-i by one pure predicate, set by the wizard's Only row and drawn dashed-and-hatched under one `MarkerAppearance.isRoutingOnly` flag.
- The marker import/export transfer writes one GPX per marker with a base64 protobuf payload and a readable JSON mirror, a ZIP batch that always skips, and the three doors (card share, list export and a Settings transfer card).
- A marker's dashboard open frames its body plus the proximity collar (the pin's zoom-in-only rule dropped), and a plain marker tap no longer opens the card.
- The card header's route-role marks are the 🚩 / 🏁 emoji beside the 💲 cost mark, and the marker filter's three routing axes stand under one `Routing` title.

**Tracks** · **status:** active · **created:** 2026-06-15 21:43 · **modified:** 2026-10-14 08:54
- Track the boat's movement during navigation: one recording per trip, points captured only while moving, persisted as protobuf and recallable on the map.
- A route and a recorded trip are named from the marker zones they met, the name a chain of origin, ranked body and destination, and an end at the home port never names either kind.
- The two render axes per kind run on a global → kind → class `enabled` leaf, and the path render engine is one painter for both kinds' lines over the `path.*` family.
- The render count of either kind runs 0–30 plus an **All** value that drops the cap, pinned items having always escaped it, and the newest→oldest fade spans the not-pinned set actually drawn.
- A trip's GPX carries the markers that own it (their payloads in the track's extensions plus one `wpt` each), re-pointed at the landed track id on import.

**Performance** · **status:** active · **created:** 2026-06-07 00:00 · **modified:** 2026-09-20 13:50
- Battery/performance pass over the GPS plugin — tunable acquisition via presets and sliders, movement-adaptive idle, a capped map-refresh applier and compass gating.
- Power management is centralised in `data/power/`: `PowerPolicy` (framework-free) plus `PowerKeeper` hold the screen flag through a single mutation point, and a pushed speed is never read as a fresh one.
- The map layer cost pass removed the per-layer zoom rebuilds and attaches the contour polylines once, switched by their own `enabled` flag.

**Ui_Settings** · **status:** active · **created:** 2026-06-09 15:28 · **modified:** 2026-10-07 13:43
- Settings page UI, SharedPreferences persistence and the settings widgets, with all rendering rules deferred to `docs/ui-component-guidelines.md`.
- The four tabs derive their order, labels and pager from one ordered `SettingsTab` enum, and Position was renamed Routing and took the Route section.
- The Tracks and Routes card reads Tracks Appearance → Routes Appearance → Speed Arrows Settings → export/import, each Appearance block opening on its own two-chip Speed Display row.
- The Tracks/Routes appearance settings seed from the unified `path.*` family, and `maro.properties` was taxonomised into eight subject domains.

**UI_Map** · **status:** active · **created:** 2026-06-07 00:00 · **modified:** 2026-10-13 19:47
- Map display layer management — the depth and colour-depth layers, overlays, inspect mode, the toggle squares and the drawer ladder.
- The layer fan is the master display gate for routes and tracks, the open dashboard's own item its one exception, and the two header eyes stay dimmed but tappable beneath it.
- Inspect mode re-ranks the nearest inspectable item on every sweep and opens its dashboard at once, the pause recentring the camera and a drag resetting the card.
- The map dash family rides one frame (one header, one dissolve, square corners, one walk row) and the toggle squares resolve through one pure `MapToggleFace`.

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

**Ui_Menu** · **status:** active · **created:** 2026-07-05 06:57 · **modified:** 2026-10-05 20:56
- The hamburger menu drawer — the Routing card (ends, quick access and live summary), a Routes list, a Tracks list and marker management.
- The ROUTES and TRACKS headers each carry a map-visibility eye outside the filter gate, and the two lists are hard-separated by a `ListScope` over the one `TrackHistoryOverlay`.
- The route Speed/Direction switches are subordinate to the master Speed Colors and Arrows chips, and a pinned route stays dashed by its own route identity.

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

- [ ] **Tasker water-state bridge — must be resolved before the power service becomes conditional** — `TrackRecordingService` answers `ACTION_QUERY_WATER_STATE` from a runtime-registered receiver and holds `lastKnownOnWater`; the Performance power work plans to run the service only while a keep-alive reason holds, at which point the query goes unanswered while the app is idle. Detail in `xTrack/Performance/260912_FEAT_PLN_Performance_power-management-centralization.md` §6 and `xTrack/Tasker/260628_FEAT_PLN_Tasker_tasker-water-state-integration.md`.
- [ ] **`#new` should warn before switching and offer to overwrite the local branch** (raised 2026-09-19): pin what the warning says and what overwrite means, then land it in `AGENTS.md` §7b and `docs/cmd_help_git.md`.
- [ ] **`docs/xtrack-templates.md` tells plans to complete with `## Outcome` while the corpus uses `## Implemented`** (raised 2026-10-08): 28 `FEAT_PLN_` files carry `## Implemented` and none the template's section, and §7b's `#implement` row says "report + `## Implemented`" — reconcile the template with the corpus and the rule.

## Cross-Reference Docs
Docs available via `#doc read [name]` from any feature. Fuzzy-resolve searches this table.

| Doc | Owner Feature | One-Liner |
|-----|---------------|-----------|
| `color-scheme.md` | ColorManagement | Color tokens, palette, alias chains |
| `FEAT_DOC_Route_engines.md` | Route | The acquisition engines as they stand: seam, rows, ladder, pipeline, plan seam, water, readings |
| `material-icons-standalone-guide.md` | Ui_General | How to add Material Symbols icons as standalone ImageVector .kt files |
