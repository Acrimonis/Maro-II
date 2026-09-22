# Route — the design, settled

> **Digest floor — superseded 2026-09-22.** This is the record of the **mesh era**: the packed navigation
> mesh, its bake, its depth gate and its ship sequence, all of which were **removed with their engine**.
> What it decided that still lives — the route's placement in the app's own tree, the units fold, the
> zone-layer homes, the toggle as the single control, the trip figure in the dashboard's cell and the save
> as an ordinary track — is in the epic and in `## Implemented`. What the engine became, and what it cost, is
> [`260922_FEAT_DOC_Route_mesh-engine.md`](../260922_FEAT_DOC_Route_mesh-engine.md); the removal is
> [`260922_FEAT_PLN_Route_dummy-engine-and-engine-removal.md`](../260922_FEAT_PLN_Route_dummy-engine-and-engine-removal.md).
> Nothing in this file is pending, and the paths it backticks name files the tree no longer carries.

**Date:** 2026-09-19 · **Branch:** `feature/route` (cut from `origin/develop` at `9756a47`) · **Status:** in design — nothing implemented

**This file holds the whole design:** the registration, the drift against the code, the challenge and every decision taken on 2026-09-19. The epic carries the contract and the work; this carries why. It was consolidated the same day, so the narration of the challenge and of the discussions is now the register below.

**Section map, for pointers written before the consolidation:** the challenge's items, once §14's C1–C11, are the register in §3; the band discussion was §15, the trip figure §16, the entry point §17, the depth gate §18, the pending set §19 and saving a route as a track §20 — all of them now inside §3 and §4.

## 1. What the feature is

Set a destination point on the map and trace the fastest water-constrained route from the boat's position: the route always stays on water and minimises time spent inside speed-limit zones — around a zone when that is faster, the shortest crossing when the destination lies inside the 300 m coastal band, the shortest exit when starting inside it. Three parts: a prebaked constrained-Delaunay navigation mesh shipped as a protobuf `.bin`, an on-device A* over it, and the destination UI.

## 2. What the corpus gained

- `GLOBAL_CONTEXT.md` — a routing row (`route, routing, destination, waypoint, navigation mesh, cruise speed, fastest path, route mesh`) and a Feature Summaries row.
- `FEAT_DSC_Route.md` — `## Subfeatures` became `## Sections`; the `[ ]` heading markers and the retired `active_subfeature` key are gone; the file-level `## Todos` was folded into the sections on the one-home rule; a fourth section, `### route-saving`, was added; and every decision below lives in its rules or its todos.
- `FEAT_HYD_Route.md` — owed at the first bake.
- Two walks closed on 2026-09-19: level 1 over the challenge's items, level 2 over the pending set, nothing parked.
- **One error, made and reverted the same session:** the baked-mesh path was rewritten to the checked-in assets directory on a misread listing; the bake documents showed the gitignored `data/app-assets/**` tree to be the real home, so the epic's original path stands in both places.

## 3. The decisions

**The data file.** The mesh is written the way the coastline and depth data is — javalite with hand-built packed primitive arrays and CSR offsets, schema in `app/src/main/proto/`, first loaded straight into flat arrays. Per-element messages would cost several times the bytes and the parse time, and would put one dataset in three representations.

**What the file does not carry.** No limits and no values of any kind: the mesh holds geometry and connectivity only. The effective limit is resolved per edge crossing through the shipped zone model (`SpeedZoneIndex.query(lat, lon).mostRestrictiveSpeedKn`), cached per search, so a regulation fix or a cruise-speed change never invalidates a `.bin`. The 300 m band's **shape** is the exception that proves the rule: its boundary is respected by the triangulation and every edge carries an in-band mark, because the band changes only when the coastline does; its 5 kn stays live.

**The prerequisite this creates.** The band's value, today a literal in `NavigationViewModel.kt`, and the zone-to-effective-limit mapping, today written three times (`SpeedZoneBuilder.kt`, `RegulatedZoneComponents.kt` twice), each need one home before Route can consume them. That consolidation is RegulatedZones' work and is recorded as a prerequisite rather than absorbed here.

**Geometry and connectivity.** Refinement is adaptive but bounded — finer near the coastline and the zone boundaries, coarser offshore — and the connectivity pass holds a **30 m minimum channel width**, unnecessary in this corridor but assumed as the floor. Both ends of a route must sit in one stretch of connected water: an end resolving elsewhere, whether another stretch or land inside a port, is re-resolved to the closest point inside the boat's own stretch rather than failing.

**The box.** The mesh is built over exactly the water the app knows: `DepthZoneMask.envelopeOf(coastBBox, SIX_NM_M)`, reusing `data/model/BoundingBox`, because `CoastlineRepository.isWater` treats anything past 6 NM of coast as land. Whether the cut sits exactly on that boundary or a node spacing inside it is the implementer's call; a boat outside the box is reported as outside, never snapped inland.

**Depth.** Water shallower than **2.5 m** is closed to routing — the mesh is simply not built there — so a passage no boat of this size can use is absent from the map. The number is a depth owned by the depth layer, `bake-depth` runs before `bake-route`, and a boat's own draught takes that number over once such a value exists. What is not promised: width, and no tide allowance, which costs little where the tide is a few decimetres.

**The search.** A* over the flat arrays with a metric heuristic, cancellable through `ensureActive()` so a flung map drops a search in flight; one decode on `Dispatchers.Default` into arrays the repository keeps for its life. The cost model is parameterised by exactly one speed, which is what makes the next decision cheap.

**Re-planning.** Recompute when the boat's cross-track distance from the confirmed line passes 100 m, floored at 15–30 s between searches, gated on 2 kn of speed, with 1.5×/0.5× hysteresis to stop a boat riding the threshold; the price is one search because time is recomputed from the mesh at search time. The MVP stays one-shot for UI reasons, so the seam to leave is a `RouteSearch` that is a pure function of mesh, start, destination and speed.

**The trip figure.** The dashboard's zone time-to-limit keeps dividing by the speed being made good; the trip figure uses a set free-water pace — `maro.properties` key, `AppConfig` accessor, `SettingsManager` field, **3–40 kn, default 28** — replaced while a route is under way by the boat's observed pace: sampled only outside the regulated zones and the band, over a ten-minute window, reduced by a high quantile rather than a mean, falling back to the set pace on thin samples. It lives in the dashboard's **distance-to-shore cell** while a route is confirmed, the zone cell keeping its compliance duty, and there is no separate panel.

**Placement.** The route joins the app's own slicing rather than taking a package of its own: domain types in `data/model/`, repository, serializer and spatial adapter in `data/route/`, point queries and search in `spatial/`, view model, overlay and host in `ui/map/`, the builder in `app/src/test/.../data/route/` beside the prebake baker it resembles. The `Route` prefix survives only where a name would be ambiguous outside its package, and `RouteConfig` does not exist — conversions come from one shared home and settings from `SettingsManager`.

**Units.** The four spellings of the knot and nautical-mile conversions fold into one pure-Kotlin units file beside the spatial helpers **before** this feature lands, because Route cannot consume a home that does not exist and a private copy would be the fifth.

**The entry point.** A **route toggle** in the control stack, gated the way the inspect toggle is: on shows a screen-centred target with the map draggable and zoomable to aim it and the route previewing continuously; **off ends the route** and cancels an unconfirmed draft. The preview is driven by movement, not by a clock — recompute when the target has moved past a threshold or the map settles, cancel in flight on a fling, cap near three a second. The start is the position the dashboard reads, never the map centre, and the line sits **above the tracks and below the markers**.

**Demo mode.** The start is the marker, which the dashboard's own position seam already returns there. What pauses is the sailing, not the camera: demo speed is derived from the map's pan, so aiming would sail the boat and extend its trace; the mode suspends that derivation and the dashboard's speed readout reads stationary while it is on. The observed pace stays a GPS-mode feature.

**The dialog.** One `ConfirmDialog` in the app's existing shape — stacked full-width actions above a bottom-most Cancel — with the target's details and a **pin toggle**, default off. Its four outcomes are three labelled actions: `Route` follows without saving, `Save & route` writes the track and then follows, `Save only` writes it and ends the mode with the camera returning to the start, and `Cancel` changes nothing. The save gets no checkbox, because a ticked box would mean different things depending on the button pressed and Save-only would have no button to hang on; a state belongs to a checkbox and an outcome to a button.

**Saving a route as a track.** The route becomes an ordinary track: vertices as `TrackPoint`s carrying the planned speed and the cumulative planned time, so distance, duration, average and fastest speed all come out right with no second code path; `Track` gains `plannedCourse` at a fresh `@ProtoNumber(19)` with a default, which an old blob ignores and an older build still reads — where adding a `PLANNED` point type would make an older reader refuse the whole file. Saving is explicit and one-way: the route keeps living, the track freezes, and saving again makes a second track. The pin is the track's existing field, offered as the save's initial value while the track list keeps its own control. The write belongs to `data/track/` — a `TrackFromCourse` factory beside the repository — which is the fourth shared surface the isolation accepts, after the MapScreen hook, the menu entry and the Settings row.

**The seams.** One `RouteHost` call in the MapScreen shell, one menu entry through the existing menu model, one Settings row from the shared row family; read-only route state crossing `OverlayLayer` goes into a bundle in `OverlayLayerParams.kt` rather than a new signature parameter. One file owns the map objects, so ordering and the pin's slot are decided in one place.

## 4. What a route costs, and the budget nobody may guess

- The corridor is 68.6 km east–west by 71.4 km north–south, of which roughly 45 % is water — about 2,000–2,500 km² to mesh, and ~200k–250k nodes at 100 m spacing, ~50k–60k at 200 m.
- A Lérins-to-Salis route — roughly 5.4 NM straight, 6–8 NM on water — is a few hundred nodes long, and A* with a metric heuristic expands thousands rather than the whole graph: single-digit to tens of milliseconds with flat arrays and no per-node allocation.
- The one-off load is larger: a few MB of protobuf decoded once on `Dispatchers.Default`, tens to a couple of hundred milliseconds, which is why the repository holds the result.
- **The verdict is comfortable, but it is an estimate with named assumptions** — spacing, water fraction and node count are unbuilt, so the prebake benchmark is what turns the ≤ 500 ms target into a measurement. The node budget is measured, never asserted.

## 5. The build, the bake and their inputs

- The bake selector is `apk-bake.bat`: `all`, `coastline`, `emodnet`, `litto3d`, `depth`, `regulatedzones`, `--fresh`, each label calling one `tools\bake-*.bat`. Route joins it as `tools\bake-route.bat` behind a `:do_route` label, in `all` and in the help text, ordered after the depth and zone steps.
- The builder is a JUnit test under `app/src/test`, gated by `-Dmaro.prebake=true` — the gate the build already propagates to the test JVM for the coastline and depth prebakes — and it writes `data/app-assets/route/<region>.bin`. The route bake refuses by name when an input `.bin` is missing rather than writing a partial mesh, and the docs carry the one line that a route bake follows any coastline, depth or zone bake: a mesh carries nothing that would detect a stale input.
- Packaging needs no Gradle change: `assets.srcDir(rootProject.file("data/app-assets"))` is already wired, and the APK-lean ignore patterns cover the `.asc`/`.prj` intermediates without touching `.bin`.
- **JTS and poly2tri, approved 2026-09-19,** are JVM libraries rather than native tools, so they ride the build files — a version entry and a library alias each in `gradle/libs.versions.toml`, then two lines in the existing `testImplementation` block. The builder lives in `app/src/test`, so the main source set cannot import them and `assembleDebug` packages neither; the first bake needs the build machine online once, to fetch them into the Gradle cache.

## 6. Open points, and the conditions that return as questions

- **The discarded commit `a1ca620`** — "Route: add feature epic, hydration and isolation design; corridor doc to gradle.properties" — is unreferenced but still in the reflog; recovering its hydration and its gradle.properties hunk is undecided.
- **The asset directory has two names:** the epic writes `data/app-assets/route/`, and an empty `data/app-assets/routing/` sits beside the other baked trees. `route/` is the recommendation, matching the epic and the package.
- **The empty `routing/` directory** is left alone rather than renamed under a live tree.
- **Two conditions return as questions rather than decisions:** if the benchmark cannot hold the ≤ 500 ms target at the chosen refinement, or the pace sampling proves too thin to trust, that is a performance fork and it comes back as one.
- **The guarantee the depth gate does not give:** a passage is usable only when a continuous line of soundings deep enough crosses it, which is a connectivity pass over the depth grid per threshold, cached; that work is out of scope, and the grid's own resolution is the other floor.
- **Arrival, the one hole the full review found in the design itself, now closed:** the state machine runs Idle → Draft → Confirmed and the toggle ends a route, so reaching the destination needs neither a state nor a cue — the trip cell reads zero while the line stays drawn, and only the toggle ends a route. Recorded in the epic's `destination-ui` rules.
- **Five gaps beside it were decided on the review and recorded in the epic's rules:** the boat leaving the covered water keeps the line with its recompute skipped and the trip figure marked stale; the route and inspect modes are mutually exclusive; the pin is drawn at the resolved destination rather than at the raw aim; the line's appearance lives as `maro.properties` keys with no Settings row yet; and a saved track takes the Tracks feature's standard auto-name with its planned flag distinguishing it.
- **One thing the review confirmed rather than fixed:** the heuristic is admissible by construction — the cost's minimum can never exceed the cruise speed — so A* stays optimal, and the epic now states it so the property is not optimised away.

## 7. The implementation sequence

1. The units fold, and the RegulatedZones prerequisites — both before any Route code.
2. Models, the packed schema, `RoutePointQueries` and the test-only `RouteGeometry`.
3. The mesh builder with its measured budget, then the prebake test, `tools/bake-route.bat` and the `apk-bake.bat` wiring.
4. `RouteMeshRepository` and `RouteSearch`, with the benchmark and the pace reduction.
5. `RouteSpatialAdapter`, the pace setting, `RouteViewModel` and the target mode.
6. The dashboard cell, `TrackFromCourse` and the save action.
7. The three app-side seams: the MapScreen hook, the menu entry and the Settings row.

## Outcome

Both walks closed on 2026-09-19 with nothing dropped and nothing parked: level 1 took the eleven challenge items and the depth gate, level 2 the thirteen pending items. Every decision above came out of them, and the plan was consolidated the same day so that the register — not the argument — is what a reader picks up. One error is on the record, the asset-path rewrite made and reverted in the same session; nothing under `app/` was touched, and no dependency beyond the two approved test-only libraries was added.
