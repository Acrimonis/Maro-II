---
name: Route
status: active
created: 2026-08-16 10:44
modified: 2026-09-19 22:38
---

# Feature: Route

**Description:**
Set a destination point on the map and have the app compute and trace the fastest water-constrained route from the boat's current GPS position. The route always stays on water and minimises time spent inside speed-limit zones: it goes around a zone when that is faster, takes the shortest crossing when the destination lies inside the 300 m coastal band, and the shortest exit when starting inside it.

## Sections

### navigation-mesh

**Focus:** Prebake an adaptive navigation mesh (constrained Delaunay triangulation) for the corridor and serialize it as a bundled protobuf `.bin`; the app only loads and searches it. All builder code lives in `src/test` (prebake) so it can use `testImplementation` geometry libraries.

#### Todos
- [ ] Build the planar overlay of the 300 m contour + regulated-zone boundaries into disjoint regions that carry **no limit** — the effective limit is resolved at search time from the zone layer
- [ ] Generate the 300 m band at bake time from the analytic predicate isWater && distanceToCoast ≤ 300 (not from SpeedZone data)
- [ ] Derive a node-count budget from the ≤ 500 ms target before triangulating
- [ ] Build constrained Delaunay triangulation of coastline + cost-region boundaries (JTS for overlay/cleaning/buffer + poly2tri for triangulation; prebake-only testImplementation deps)
- [ ] Give every edge its length and its in-band mark, splitting edges on the band and zone boundaries so a crossing is priced exactly — the cost itself is computed at search time, never baked
- [ ] Ensure narrow passages and harbour entrances stay connected in the mesh
- [ ] Serialize mesh to protobuf `.bin`; output to data/app-assets/route/<region>.bin
- [ ] Consume the same regulated-zones `.bin` the app draws (no re-fetching SHOM)
- [ ] Models + protobuf mesh schema + RoutePointQueries/RouteGeometry interfaces
- [ ] RouteMeshPrebakeTest with its JVM A* benchmark assertion, plus tools/bake-route.bat

#### Rules
- App is a pure consumer: all mesh construction at build time (JUnit gated by -Dmaro.prebake), on-device search only
- Region = BuildConfig.REGION_LON_WEST/EAST + REGION_ID (`maro.region.id`, `nice-menton` today), the gradle props being the single source; the N/S bound and the 6 NM seaward band follow the depth envelope's own derivation (`DepthZoneMask.envelopeOf`)
- Reserve a forbidden/infinite-cost sentinel for future prohibited zones
- Bake-time geometry is file-based: RouteGeometry's prebake implementation reads coastline/regulated-zone `.bin` files directly via serializers (runnable in a JVM test)
- Refinement is adaptive but bounded: finer triangles near the coastline and the zone boundaries, coarser offshore, and the connectivity pass holds a **30 m minimum channel width** (agreed 2026-09-19, plan §14 C4)
- Depth gate, agreed 2026-09-19 (plan §18): water shallower than **2.5 m** is closed to routing — the mesh is simply not built there — so a passage no boat of this size can use is absent from the map entirely; the number is a depth value owned by the depth layer, `bake-depth` runs before `bake-route`, and a boat's own draught would replace it once such a value exists
- The route bake refuses by name when an input is missing — coastline, zone or depth `.bin` — rather than writing a partial mesh, and it runs after any coastline, depth or zone rebake: a mesh carries nothing that would detect a stale input (agreed 2026-09-19)

#### Mesh schema (protobuf, packed — decided 2026-09-19, walk item 1)
- Packed primitive arrays with CSR offsets, hand-built like `CoastlineSerializer` and `DepthSerializer`: `pointsLat[]`, `pointsLon[]`, `edgeFrom[]`, `edgeTo[]`, `edgeLengthM[]`, `edgeInBand[]`, `nodeOffsets[]`, `nodeEdges[]`, plus `regionId` and the box
- Edges store **length and the in-band mark, never time and never a limit**: `time = lengthM ÷ min(cruiseSpeed, zoneLimit)` is recomputed at search time, so neither the 3–40 kn slider nor a regulation change requires a re-bake
- The loading side fills the same flat arrays the search reads, so one representation serves file, memory and search; the object-shaped `RoutePoint`, `RouteNode`, `RouteEdge` and `RouteMesh` stay in the builder and the serializer

### route-search

**Focus:** On-device fastest-path search over the prebaked mesh.

#### Todos
- [ ] A* / Dijkstra over mesh edges with time-based edge cost
- [ ] effectiveSpeed = min(cruiseSpeed, zoneLimit); cruise speed 20 kn default, user-configurable
- [ ] Boundary crossings cost at min(limitA, limitB); units KNOTS_TO_MPS (own copy in RouteConfig)
- [ ] Snap start (GPS fix, or map centre in demo mode) and destination to nearest reachable water node
- [ ] Graceful no-route result; reject/clamp positions outside the corridor
- [ ] Cancellable search on Dispatchers.Default; return a ready polyline to the main thread
- [ ] Confirm the ≤ 500 ms worst-case target on-device (JVM assertion already runs in the prebake test)
- [ ] RouteMeshRepository — stream-deserialize the mesh on Dispatchers.Default
- [ ] AppSpatialAdapter — the Context-based read-only runtime implementation of RoutePointQueries
- [ ] Phase 2: replace the 2.5 m closing depth with the boat's own draught once such a value exists to replace it with
#### Rules
- Kotlin coroutines/Flow only
- Both ends of a route sit in one stretch of connected water: when either resolves into a different stretch, that end is re-resolved to the closest point inside the same stretch, and a destination resolving to land takes the same path (agreed 2026-09-19)
- The boat can leave the covered water while a route is active: the line stays, a recompute is skipped rather than failing, and the trip figure keeps its last value marked stale the way the app already marks a stale fix (review 2026-09-19)
- The heuristic is admissible by construction — `min(cruise, limit)` can never exceed the cruise speed, so a distance-over-cruise estimate never overstates the remaining time — and it is stated here so it is not "optimised" away (review 2026-09-19)
- Mesh load = stream-deserialize on Dispatchers.Default (matches depth/coastline precedent; not memory-mapped)

### destination-ui

**Focus:** Destination input, route preview, and overlay.

#### Todos
- [ ] The route toggle in the map control stack: on shows a screen-centred target with the map draggable and zoomable to aim it, and a live preview of the route as it is aimed (decided 2026-09-19, plan §17)
- [ ] Confirmation dialog shows start (auto = boat position), destination, distance + ETA
- [ ] While the dialog is shown, paint the preview route polyline and zoom the map to fit it
- [ ] The confirmation dialog: OK confirms the aimed route, and Cancel dismisses the draft while leaving the mode on — only the toggle ends a route — with an empty-map tap clearing nothing
- [ ] Render a visible destination marker pin alongside the polyline
- [ ] Route age and a manual Recompute, shown where the trip figure lives rather than in a panel of their own
- [ ] Menu-drawer entry to open the destination picker; the free-water pace control in Settings
- [ ] The free-water pace: a setting, 3–40 kn with a default of 28, and the observed pace that replaces it while a route is under way (window, quantile, fallback)
- [ ] The dashboard's distance-to-shore cell carries the trip's distance and time while a route is confirmed — a `Ui_Dashboard` rule with strings in both locales
- [ ] RouteViewModel — StateFlow state machine: draft destination, preview route, confirmed route
- [ ] RouteHost + RouteOverlay composables and the single MapScreen hook

#### Rules
- One-shot compute for MVP; live re-planning deferred
- Mode-entry is the route toggle in the map control stack, gated the way the inspect toggle is; the free-water pace control sits in Settings (accepts extra shared-file edits, the dashboard's distance-to-shore cell among them)
- The trip's time uses a set free-water pace (3–40 kn, default 28) until evidence exists, then the boat's observed pace outside the regulated zones and the band, reduced by a high quantile over a sliding window with a fallback to the set pace on thin samples (agreed 2026-09-19, plan §16)
- The dashboard's distance-to-shore cell shows the trip figure while a route is confirmed, the zone cell keeps its compliance duty, and no separate route panel exists (agreed 2026-09-19, plan §16)
- MapEventsOverlay.longPressHelper yields a GeoPoint directly; pin/polyline/zoom-to-fit are geo-native osmdroid overlays — no manual screen↔geo projection
- Ordering and start, agreed 2026-09-19: the line sits above the tracks and below the markers in the existing order, one file owns the map objects, and the start is the position the dashboard reads — the GPS fix in GPS mode, the same seam in demo mode — never the map centre
- The toggle is the single route control, agreed 2026-09-19 (plan §17): on aims and previews, OK confirms, and off ends the route and cancels an unconfirmed draft; the dialog's Cancel returns to aiming rather than ending anything. It replaces the long-press todo entirely, and the mode is gated the way the inspect toggle is
- The route toggle and the inspect toggle are mutually exclusive: entering one leaves the other, per the app's single-mode habit (review 2026-09-19)
- The pin is drawn at the resolved destination, never at the raw aim: an aim landing on land or in another stretch moves to the closest point of the boat's own stretch, and the pin must show where the route actually ends (review 2026-09-19)
- The line's appearance lives as `maro.properties` keys behind `AppConfig` — following the rule that every drawing value has one home — and gains no Settings row until one is asked for (review 2026-09-19)
- Arrival carries no state and no cue: reaching the destination is the trip cell reading zero while the line stays drawn, and only the toggle ends a route (agreed 2026-09-19)

### route-saving

**Focus:** Persist a confirmed route as an ordinary track, pinned or not, so it can be listed, exported and redrawn like any other journey.

#### Todos
- [ ] `TrackFromCourse` in `data/track/` — build a `Track` from the confirmed route's polyline and its per-leg speeds and durations: vertices as `TrackPoint`s carrying the planned speed and the cumulative planned time, `distanceNm` and `navigatingDurationSec` from the plan's own figures, name and pin from the save action
- [ ] `Track` gains `plannedCourse: Boolean` at a fresh `@ProtoNumber(19)` with a default, so an old blob reads unchanged and a new one still reads in an older build; `PointType` keeps `NORMAL`/`GAP` rather than gaining a planned value an older reader would refuse
- [ ] The save action itself: explicit and one-way — saving again makes a second track, and nothing links a saved track back to a live route
- [ ] The confirmation dialog carries the save as three stacked actions above the existing Cancel — `Route`, `Save & route`, `Save only` — each stating its outcome rather than relying on a state
- [ ] A pin toggle in that dialog, default off, giving the saved track its initial pin state; the track list's own control still flips it afterwards

#### Rules
- A saved route is a normal track in every respect — list, stats, GPX export, heatmap, replay — so nothing downstream gains a special case and only the flag records that the speeds were planned rather than measured
- The write goes through `data/track/`'s own repository and factory, which is the fourth shared surface the isolation design accepts, after the MapScreen hook, the menu entry and the Settings row
- The pin is the track's existing field: the save offers its initial value while the track list keeps its own control, so the field has one home and two ways to reach it
- The dialog's four outcomes, agreed 2026-09-19 (plan §20): `Route` follows without saving, `Save & route` writes the track and then follows, `Save only` writes it and ends the mode with the camera returning to the start as on Cancel, and `Cancel` changes nothing
- The save is an outcome and the pin is a state, so the buttons state outcomes and the dialog's one checkbox is the pin — which is also why the save itself gets no checkbox
- The saved track takes the standard auto-name the Tracks feature already uses, with `plannedCourse` distinguishing it from a recorded journey (review 2026-09-19)

## Isolation Design

**Principle (revised 2026-09-19):** the route is placed in the app's own tree rather than in a package of its own — the app already slices `data/` by subject (`depth`, `coastline`, `regulation`, `track`, `markers`, …) and keeps each prebake builder beside the subject it bakes for, so a parallel `route/` root would re-invent that taxonomy and hide the feature from the code map. The isolation principle survives as the three seams below, not as a folder.

1. `MapScreen.kt` — a single `RouteHost(mapView, boatPosition)` call.
2. Menu drawer (`Ui_Menu`/`OverlayLayer`) — one entry to open the destination picker.
3. Settings (`Ui_Settings`/`SettingsOverlay`) — one cruise-speed row.

**Collision surfaces:**

| Shared file | Risk | Strategy |
|---|---|---|
| `MapScreen.kt` (a ~2.5k-line orchestration shell) | highest | single `RouteHost()` call |
| `OverlayLayer.kt` / menu drawer | mode-entry | one menu item wired to a Route callback |
| `SettingsOverlay` | cruise speed | one row from the shared row family, its value held by `SettingsManager` — the home is still open (plan §6) |
| `MapOverlayRenderer.kt` / `OverlayZOrder` | overlay ordering | the route's own osmdroid polyline and pin, its slot still to be decided (plan §14 C10) |
| `spatial/` + `data/regulation` | read coupling | read-only `RouteSpatialAdapter`, the single sanctioned importer |
| `data/model/` | shared types | `LatLng` and `BoundingBox` reused, never re-declared |

**Placement — main (ships in the APK):**
```
data/model/   RoutePoint.kt, RouteMesh.kt, RouteNode.kt, RouteEdge.kt, RouteResult.kt
data/route/   RouteMeshRepository.kt, RouteMeshSerializer.kt, RouteSpatialAdapter.kt
spatial/      RoutePointQueries.kt, RouteSearch.kt
ui/map/       RouteViewModel.kt, RouteOverlay.kt, RouteHost.kt
```

**Placement — prebake (src/test, never in the APK), beside the domain it bakes for:**
```
app/src/test/java/ykws/android/maro/data/route/
  RouteMeshBuilder.kt, RouteGeometry.kt, RouteMeshPrebakeTest.kt
```

**Naming:** the `Route` prefix only where a name would be ambiguous outside its package — `RouteMesh`, `RouteSearch`, `RouteOverlay` — and plain names inside `data/route/`. `RouteConfig.kt` is dropped: conversions come from the shared units home and the settings from `SettingsManager` with their keys in `maro.properties`.

**Decoupling rules:**
1. Route depends only on `data/model`'s `LatLng` and `BoundingBox` plus its own `RoutePointQueries` interface and the test-only `RouteGeometry` — with `RouteSpatialAdapter` as the single sanctioned importer of spatial and regulation code.
2. `RouteSpatialAdapter` is the only file importing `CoastlineSpatialIndex` / `SpeedZoneIndex` / `RegulatedZonesRepository`; read-only, Context-based, runtime-only.
3. Route declares no constant and keeps no preference of its own: conversions come from the shared units file, and the cruise-speed home remains the open recommendation in the plan's §6.
4. All Route runtime state lives in `RouteViewModel` (StateFlow).
5. Tap capture via osmdroid `MapEventsOverlay`, never `setOnTouchListener`.

**Interaction flow (state):**
```
Idle → long-press → Draft (pin + preview route)
Draft → ✕ / Cancel → Idle
Draft → Route → Confirmed
Confirmed → Recompute → Confirmed (from current GPS)
Confirmed → ✕ (panel) / Cancel → Idle
```

**Loading + serialization:**
- Mesh load: stream-deserialize on `Dispatchers.Default` (depth/coastline precedent; not memory-mapped).
- Serialization: protobuf-javalite; output `data/app-assets/route/<region>.bin` (region id from BuildConfig).
- Prebake: JUnit gated by `-Dmaro.prebake=true`; `tools/bake-route.bat`; JTS + poly2tri as `testImplementation` (prebake-only, never in the APK).

## Rules
- Out of scope MVP: draft-aware routing beyond the depth gate's reference floor, prohibited zones (ACCESS_PROHIBITED etc.), live re-planning
- Placement, decided 2026-09-19 (walk item 8): the route joins the app's own slicing instead of taking a package of its own — model types in `data/model/`, repository, serializer and spatial adapter in `data/route/`, point queries and search in `spatial/`, view model, overlay and host in `ui/map/`, the prebake builder in `app/src/test/.../data/route/`; shared-file edits stay limited to the MapScreen hook, the menu drawer entry and the Settings cruise-speed row
- The mesh carries geometry and connectivity only, never a limit: the effective limit is resolved while a route is asked for, from the 300 m band's value and the regulated zones' own limits, cached per search. The mechanics — a lookup per edge crossing rather than stored zone ids — are the agent's call (2026-09-19)
- Prerequisite, decided 2026-09-19: the band's value and the zone-to-effective-limit mapping each need **one home** before Route can consume them — today the mapping is written three times (`SpeedZoneBuilder.kt`, `RegulatedZoneComponents.kt` twice) and the band's 5 kn is a literal in `NavigationViewModel.kt`; that consolidation belongs to RegulatedZones, not here
- The 300 m band's shape is baked — a boundary the triangulation respects plus an in-band mark per edge — while its limit stays live and is resolved at search time; agreed 2026-09-19, and only a benchmark showing the mark costs more than the query it replaces would drop it
- Conversions come from one home: a pure-Kotlin units file beside the spatial helpers owns knots-to-metres and miles-to-metres, that fold lands **before** this feature so no fifth spelling appears, and Route declares no conversion constant of its own (agreed 2026-09-19)
- Prebake dependencies, approved 2026-09-19: JTS and poly2tri ride the test classpath only — a version entry and an alias each in `gradle/libs.versions.toml` and two lines in the existing `testImplementation` block — so the app cannot import either and the APK's size and runtime are untouched

## Key Files
- `app/src/main/java/ykws/android/maro/data/model/RoutePoint.kt`, `RouteMesh.kt`, `RouteNode.kt`, `RouteEdge.kt`, `RouteResult.kt` — NEW. The route's pure domain types, beside `LatLng` and `BoundingBox`
- `app/src/main/java/ykws/android/maro/data/route/RouteMeshRepository.kt` — NEW. Stream-deserialize the mesh `.bin` on `Dispatchers.Default` and hold it as flat arrays
- `app/src/main/java/ykws/android/maro/data/route/RouteMeshSerializer.kt` — NEW. javalite with hand-built packed arrays, schema beside `coastline.proto` and `depth.proto`
- `app/src/main/java/ykws/android/maro/data/route/RouteSpatialAdapter.kt` — NEW. Context-based read-only adapter over `CoastlineSpatialIndex` / `SpeedZoneIndex` / `RegulatedZonesRepository`
- `app/src/main/java/ykws/android/maro/spatial/RoutePointQueries.kt` — NEW. Runtime interface: isWater, speedLimitKn, distanceToCoastM
- `app/src/main/java/ykws/android/maro/spatial/RouteSearch.kt` — NEW. On-device A* over the flat mesh, house-prefixed like `CoastlineSpatialIndex`
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — NEW. StateFlow: destination draft, preview route, confirmed route
- `app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt` — NEW. Hook composed in MapScreen; long-press capture, polyline/pin add-remove, zoom-to-fit
- `app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt` — NEW. Destination pin + polyline + confirmation dialog + age/Recompute
- `app/src/test/java/ykws/android/maro/data/route/RouteMeshBuilder.kt` — NEW. Prebake: planar overlay + CDT + edge costing (JTS + poly2tri), in the home `Zone300AssetBaker` already uses
- `app/src/test/java/ykws/android/maro/data/route/RouteGeometry.kt` — NEW, test-only. The bake interface — coastline segments, zone rings, band rings — so no bake-only interface ships in the APK
- `app/src/test/java/ykws/android/maro/data/route/RouteMeshPrebakeTest.kt` — NEW. Prebake test gated by `-Dmaro.prebake=true`, carrying the JVM A* benchmark assertion
- `tools/bake-route.bat` — NEW. Bake script, wired into `apk-bake.bat`

## Docs
- `xTrack/Route/260919_FEAT_PLN_Route_registration-and-drift.md` — the settled design: what the corpus gained, the register of every decision taken on 2026-09-19, what a route costs and how re-planning works, the bake wiring with its approved test-only libraries, the open points, and the implementation sequence (in design)

## Walk
**Level 1 — Date:** 2026-09-19 · **Source:** the ten challenge items of `260919_FEAT_PLN_Route_registration-and-drift.md` §14, plus the depth question tagged as item 11 · **Closed:** 2026-09-19 — exhausted, ten items resolved and the depth gate parked
- [x] 1 · C1 wire shape — resolved 2026-09-19 by the user's instruction: the mesh follows the coastline and depth mechanism — packed primitive arrays with CSR offsets under javalite, schema in a `.proto` beside `coastline.proto` and `depth.proto`, hand-built like their serializers — so one set of numbers is both the file and what the search reads while running, and the builder's objects never reach the wire
- [x] 2 · C2 limits — resolved 2026-09-19: a speed limit does not belong in the route data file at all; it belongs to the 300 m band and to the regulated zones, whose values are centralised and built to change; and per plan §15 the band's **shape** is baked while its number stays live — its boundary respected by the triangulation and an in-band mark on each edge
- [x] 3 · C3 lookup at search time — closed with item 2, the two being one subject split in two: the mesh carries geometry only, and the effective limit is resolved from the zone layer while a route is asked for, cached per search, so a changed limit or cruise speed never re-bakes
- [x] 4 · C4 resolution — resolved 2026-09-19: refine near the coast, bounded rather than uniformly fine, with a 30 m minimum channel width held by the connectivity pass — unnecessary in this corridor but assumed as the floor
- [x] 5 · C5 snapping — resolved 2026-09-19: the bake labels connected water, and when either end of a route resolves into a different stretch the end moves to the closest point inside the same stretch rather than failing or reaching across — the same mechanism that answers a land-resolving destination
- [x] 6 · C6 bounds — resolved 2026-09-19: the mesh box is the app's own water definition, derived as the depth envelope derives its own (coastline bbox + the 6 NM band) and reusing `data/model/BoundingBox`; a boat outside the box is reported rather than snapped inland, and whether the cut sits exactly on the boundary or a node spacing inside it is the implementer's call
- [x] 7 · C7 units — resolved 2026-09-19: the four spellings fold into one pure-Kotlin units file beside the spatial helpers, that fold lands first as its own small task, and Route consumes it while declaring no conversion constant of its own
- [x] 8 · C8 naming — resolved 2026-09-19: the route joins the app's own slicing (model in `data/model/`, repository, serializer and spatial adapter in `data/route/`, queries and search in `spatial/`, view model, overlay and host in `ui/map/`, prebake in `src/test/.../data/route/`); the `Route` prefix survives only where a name would be ambiguous outside its package, and `RouteConfig` is dropped
- [x] 9 · C9 two ETAs — resolved 2026-09-19: the zone figure keeps its speed-made-good basis, the trip figure uses a set free-water pace (default 28 kn) then the boat's observed pace outside zones and the band, and the trip figure lives in the dashboard's distance-to-shore cell rather than a panel of its own
- [x] 10 · C10 drawing path — resolved 2026-09-19: the line sits above the tracks and below the markers in the existing order, one file owns the map objects, and the start is the position the dashboard reads — the GPS fix in GPS mode — never the map centre; demo mode's own wrinkle, where the camera is the boat, is under discussion in plan §17
- [x] 11 · C11 depth gate — resolved 2026-09-19 by splitting it (plan §18): the reference half joins the MVP, so a passage the depth data rules out is absent from routing, and the per-boat half became item 12
- [x] 12 · C12 draught gate — resumed and resolved 2026-09-19: the closing depth is 2.5 m, applied at build time so that no route crosses water shallower than it, and a boat's own draught takes that number over later

- Resolutions: ten items closed by decision — the mesh packed like the coastline and depth data; limits left to the zone layer with the band's shape baked and its number live; connected water labelled, one stretch rule for both ends of a route; bounded coastal refinement with a 30 m floor; the box cut to the app's own water; conversions folded into one home first; the route placed in the app's own tree with the `Route` prefix kept only where it disambiguates; the trip figure in the distance-to-shore cell on a set pace that gives way to the observed one; the line above tracks and below markers, with demo mode pausing its sailing while a target is aimed. Then the depth gate, split on 2026-09-19 with its reference half taken into the MVP and its draught half resumed the same day and closed on the 2.5 m closing depth. Dropped: nothing. Parked: nothing — the level is exhausted.

**Level 1 — Date:** 2026-09-19 · **Source:** the pending set — the feature's open todos and the plan's unsettled points, in ship order · **Closed:** 2026-09-19 — exhausted, thirteen items settled and nothing parked
- [x] 1 · The destination entry point — resolved 2026-09-19: a route toggle in the control stack, on aiming a screen-centred target with a live preview, OK confirming, and off ending the route and cancelling an unconfirmed draft
- [x] 2 · JTS and poly2tri — approved 2026-09-19: both, on the test classpath only, versions pinned in the version catalogue, and the first bake fetches them once
- [x] 3 · The units fold — settled: one pure-Kotlin units file beside the spatial helpers, folded before this feature, Route declaring no conversion of its own
- [x] 4 · The zone-layer homes — settled as a prerequisite on RegulatedZones rather than work taken here; Route only consumes `SpeedZoneIndex.query()` and the band's value
- [x] 5 · Models, schema and interfaces — settled: pure domain types in `data/model/`, a packed `.proto` with CSR arrays in `app/src/main/proto/`, `RoutePointQueries` in `spatial/` and `RouteGeometry` test-only
- [x] 6 · The mesh builder — settled: bounded adaptive refinement with the 30 m floor, and a node budget measured by the prebake benchmark rather than fixed on paper
- [x] 7 · The prebake wiring — settled: `tools/bake-route.bat` under `-Dmaro.prebake=true`, a `:do_route` label in `apk-bake.bat` ordered after the depth step, writing `data/app-assets/route/`
- [x] 8 · The repository and serializer — settled: one decode on `Dispatchers.Default` into flat arrays kept by the repository, no other file opening the mesh
- [x] 9 · The search — settled: A* over the CSR arrays with a metric heuristic and `ensureActive()` cancellation, the pace reduction a high quantile over a ten-minute window sampled outside the zones and the band
- [x] 10 · The spatial adapter — settled: a thin Context-based delegator with no logic of its own, the single importer of the spatial and regulation types
- [x] 11 · The pace setting, view model and target mode — settled: a `maro.properties` key with its `AppConfig` accessor and `SettingsManager` field, default 28; one sealed state over `StateFlow`; the toggle already agreed
- [x] 12 · The dashboard cell — settled: the distance-to-shore cell carries the trip figure, as a `Ui_Dashboard` rule with strings in both locales
- [x] 13 · The three shared-file edits — settled: one `RouteHost` call, one menu entry, one Settings row, and read-only route state in an `OverlayLayerParams` bundle rather than a new parameter

- Resolutions: thirteen items settled — the toggle as the single route control, both prebake libraries approved test-only with the bake refusing on a missing input, the units fold landed first, the zone-layer prerequisites routed rather than absorbed, models and a packed schema with CSR arrays, a measured node budget under bounded coastal refinement, the prebake wiring through `apk-bake.bat`, one decode into flat arrays, movement-cancellable A* with a quantile pace, a logic-free adapter, the pace as an ordinary setting, the trip figure in the distance-to-shore cell, and the three app-side seams one line each. Dropped: nothing. Parked: nothing.
