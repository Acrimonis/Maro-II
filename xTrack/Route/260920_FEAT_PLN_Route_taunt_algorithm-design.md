<!-- scope: feature -->
# Route — algorithm design discussion (feasibility, high level)

Opened 2026-09-20 on `feature/other-routing`, reviewed and amended 2026-09-20 (§15), and **the design of
record and the replacement since 2026-09-20**: the engine this file describes replaces the now-deactivated
`MeshRouteEngine`, whose tracing the app no longer draws with and whose files stay in the tree untouched.
Its first cut produces the drawn line alone, and every other consideration is parked until the user says
otherwise. Nothing is built yet. The starting point is the epic in
[`FEAT_DSC_Route.md`](FEAT_DSC_Route.md), which pre-supposes a prebaked navigation mesh; this plan
re-examines that choice against what the app already loads, and puts the two requirements the user
stated — shallow-water avoidance and speed-zone avoidance — at the centre.

## 1. The requirement as stated

- **A → B**, on water, with no single state where the path leaves water or crosses ground shallower than
  **2 m**; **> 3 m preferred**.
- **Avoid the 5 kn and 10 kn zones**; when a zone cannot be avoided, **traverse it as little as possible**.

Two things are asked at once and they are not the same quantity: one is geometric (shortest, straightest),
the other is legal (stay out of slow zones). Most of the design work is deciding how they are traded,
because a single path cannot be optimal for both whenever a detour is the way out of a zone.

## 2. What is already on the device

The decisive finding of this pass: **the router needs no new data and no new artifact to exist.** Everything
the two requirements need is already loaded by the app, and two of the three are already spatial indices.

| Primitive | Where | What it gives the router |
|---|---|---|
| `DepthGrid` | [`DepthGrid.kt`](../../app/src/main/java/ykws/android/maro/data/model/DepthGrid.kt:117) | `depthAt(lat, lon)` — bilinear metres below datum, `NaN` = NoData; per-cell **source** and **confidence 0..100**; `cellSizeDeg*`, `boundingBox` |
| `DepthRepository` | [`DepthRepository.kt`](../../app/src/main/java/ykws/android/maro/data/depth/DepthRepository.kt:96) | `depthAt()`, `getGrid()` — the grid the depth layer already loads and samples |
| `CoastlineSpatialIndex` | `spatial/` | `isOnWater()`, `distanceToCoastM()` — the land test and the 300 m band input |
| `SpeedZoneIndex` | [`SpeedZoneIndex.kt`](../../app/src/main/java/ykws/android/maro/spatial/SpeedZoneIndex.kt:39) | `query(lat, lon)` → inside zones (most restrictive first), `mostRestrictiveSpeedKn`, nearest boundary; polygons with holes in [`SpeedZone.kt`](../../app/src/main/java/ykws/android/maro/data/regulation/SpeedZone.kt:20) |
| `Zone300Builder` | `spatial/` | the 300 m band geometry, from the coastline predicate — **not** a speed zone (stated at [`SpeedZone.kt:10`](../../app/src/main/java/ykws/android/maro/data/regulation/SpeedZone.kt:10)) |

Two constants from the depth feature set the shape of the problem, both in
[`DepthConstants.kt`](../../app/src/main/java/ykws/android/maro/data/depth/DepthConstants.kt:8):

- `GRID_RES_M = 25.0` — the shipped grid is **25 m**, merged from Litto3D (1 m), SDB (10 m), EMODnet
  (115 m) and GEBCO (30 m) with a **per-cell source and confidence**, and a fine contour is only drawn
  where the source resolution is ≤ 10 m.
- `ISOBATH_LEVELS` already includes **2 m**, and `IGN69_ABOVE_LAT_M = 0.40` records the datum shift.

The depth feature has already answered the question the router now faces about trusting coarse data:
`gatedForEmodnetShallow(cutoffM)` treats an EMODnet cell reading shallower than the cutoff as **NoData**,
because a 115 m cell near rocks is not a navigation-grade reading. Any 2 m gate the router enforces must
inherit that distrust rather than contradict it.

## 3. Formulating the objective

Three ways to say what "best" means, in increasing order of fidelity to the request:

- **A — time-primary.** `cost(edge) = length ÷ effectiveSpeed`, with
  `effectiveSpeed = min(cruiseKn, zoneLimitOnEdge)`. Total cost is time; minimising it *automatically*
  minimises the distance spent inside a slow zone, because time inside the zone is exactly what is being
  priced. Free water falls back to a plain shortest path. One scalar, no weights to tune, and the cruise
  slider changes nothing structural.
- **B — distance-primary with penalties.** `cost(edge) = length × (1 + k_zone·inZone + k_band·inBand)`.
  This is what "straightest shortest" literally asks for, and `k` states the aversion as a number of
  metres traded per metre inside a zone.
- **C — tiered / lexicographic.** Feasibility first (water, depth ≥ min), then the preferred band
  (depth > 3 m), then zone avoidance, with distance as the tie-break.

**Recommendation: A as the engine, with B's multiplier exposed as a "zone aversion" term, and C's tiers
implemented as feasibility gates rather than as cost.** The reason is that A and B disagree only inside a
zone: A prices the *time* lost, B prices the *distance* spent. The user's own phrasing — "limit the
distance traversing those zones" — is B's quantity, while "straightest shortest" is also B's; but a pure-B
route will happily enter a 10 kn zone for 50 m to save 3 km of detour, which is the right answer, and a
pure-A route does the same thing for a slightly different reason.

**Superseded 2026-09-20 — the objective is one quantity, and the walk settled which.** At the user's word an
edge costs **the clock and nothing else**: its length at the speed in force, plus the time a corner's slowdown
costs. The zone-aversion multiplier above is therefore **dropped** — no `k_zone`, no distance term, no route
style to slide between — and what survives from the three forms is the split by kind: zones are a **rule**
(their interior is not traversable while a way around exists) rather than a price, the band's limit stays live
at search time, and feasibility stays a gate no weight can trade away.

**Objection to the recommendation, stated plainly:** a single weighted scalar hides a choice the skipper
may want to see. Two paths through the same water can differ by a knot of average speed and 200 m of
detour, and one number cannot say why the router preferred one. If that matters, the honest answer is to
compute two optima (time-optimal and zone-avoiding) and let the user pick between two drawn paths — more
UI, more compute, and for MVP probably not worth it. C's tiers are the cheap middle: the path is
time-optimal subject to "never inside a zone unless unreachable otherwise", which is expressible by
running the search twice — once with zones forbidden, once without — and keeping zone-free only when it
exists.

## 4. The depth constraint, which is harder than it looks

- **Land and `depth < 2 m` are feasibility, never cost.** An impassable cell is not a very expensive cell;
  the search must not be able to trade a rock for a shorter path at any weight. This is the one place the
  existing design's "forbidden sentinel" idea is right.
- **2–3 m is the preference band**, not a second hard gate: cost rises as depth falls, zero at ≥ 3 m.
- **The trust problem.** A 25 m cell is an average over 625 m². A single rock, a harbour sill, a
  drying patch is smaller than a cell, and bilinear sampling on a coarse EMODnet cell can report 4 m over
  a 1 m rock. So a router that promises "2 m clearance" on this data is making a safety claim the data
  cannot back. Three honest options, and this is a decision for the user:
  1. **Margin by source** — enforce 2 m only where the cell's source resolution is fine (Litto3D ≤ 10 m,
     following the existing `ISOBATH_FINE_MAX_RES_M` rule) and require a larger margin where it is not.
  2. **Confidence floor** — treat cells with confidence below a floor as shallower than they read, reusing
     the `gatedForEmodnetShallow` precedent at the cell level.
  3. **Distance-to-coast proxy** — near the shore, where Litto3D covers, use depth; where it does not,
     fall back to a minimum distance from the coastline. **Chosen 2026-09-20** by the walk, its one home
     being the obstacle set's own decision in 10.2.
- **Datum.** `IGN69_ABOVE_LAT_M = 0.40` means the grid's depths are already shifted; clearance should be
  stated below LAT, and there is no tide input. Micro-tidal, so acceptable — but worth saying in the UI
  rather than leaving implicit.
- **Grid coverage.** The depth grid stops 6 NM seaward; outside it there is no depth at all. A destination
  offshore has depth data only while the grid covers it, and a route that leaves the grid must say so
  rather than assume deep water.

## 5. How to represent the graph

| Option | Shape | Pros | Cons |
|---|---|---|---|
| **Uniform raster graph** over the shipped 25 m `DepthGrid` | ~1.2 M cells, 8- or 16-connected | No new data, no bake, no dependency; per-cell source, confidence, zone and band all evaluable at search time; zone updates change nothing structural | 1.2 M nodes; 8-connectivity zig-zags, so path geometry needs a post-pass; depth boundary is cell-quantised |
| **Prebaked navmesh (CDT)** — the epic's current baseline | adaptive triangles, open water = a few large triangles | Tens of thousands of nodes, not millions; region boundaries (zones, isobaths, band) become exact edges; straightest by construction, plus funnel through portals | A bake pipeline, a new `.bin` in app-assets, and **new dependencies**: JTS + poly2tri as `testImplementation`, which §4 of `AGENTS.md` makes the user's call; the mesh must be re-baked when regulatable data changes |
| **Hybrid — coarse grid → corridor → fine local search** | coarse pass at a stride of a few cells, then refine inside the corridor it found | Bounds work by the area of interest rather than the corridor; keeps the raster's simplicity; the coarse pass gives a cheap "no route" verdict | Two passes to reason about; the corridor width becomes a tuning constant |
| **Visibility graph over obstacle vertices** | nodes = coastline, isobath and zone vertices; edges = mutually visible | Provably shortest among polygonal obstacles; a path bends only where it must — the truest reading of "straightest" | Node count explodes with coastline vertices; needs the obstacles as polygons, but depth arrives as a raster; needs a corridor to trim it |
| **Sampling (RRT*, PRM)** | random trees / roadmaps | Handles arbitrary cost, little map structure needed | Non-deterministic: the same request can give a different path each run, which is fatal for a navigation aid that must be testable and predictable |

**Recommendation: start with the uniform raster graph, 8-connected, with a turn-aware state and a
string-pulling post-pass, and keep the mesh as a documented speed lever rather than the baseline.** The
raster approach's weakness is constant-factor performance and boundary quantisation; its strength is that
it needs nothing new at all — no bake, no artifact, no dependency, and the zone data can be refreshed
underneath it freely. The mesh is the right answer *if* measurement shows the raster misses the latency
target — and it is also the only option that makes the 2 m and 3 m boundaries exact rather than
cell-quantised, which is a quality argument, not a speed one. **Superseded in part by §10**, which moves
the node set onto the corridor's obstacle vertices and demotes the raster to contour source, validator and
fallback.

**Objection to the raster-first recommendation:** the epic currently says the opposite, so adopting it
means writing down that the prebaked mesh is deferred, not abandoned. The mesh's exact boundaries also
mean a corridor that is provably deep, which the raster cannot state — so if the depth gate must be
*guaranteed* rather than *estimated*, the mesh is not an optimisation but a requirement.

## 6. Search and path geometry

- **A\*** over the chosen graph, with `h` = straight-line distance ÷ cruise speed (admissible for a
  time-cost) or straight-line distance (for a distance-cost). Tie-breaking must be deterministic
  (a stable key, not a hash order) so tests can assert an exact path.
- **Turn cost — settled 2026-09-20.** A corner is not charged by a weight but by **the time its slowdown
  costs**, derived from the ceiling and the radius the water allows (12.1, 12.3) — which is why the state
  carries the entry direction even though the node count does not. The state space still multiplies by the
  directions the graph offers, and that multiplication is the one place the unmeasured latency could bite.
- **Straightening — struck 2026-09-20.** There is no post-search straightening, and none may be added: the
  graph's own edges *are* the straight lines, so a bend lands on an obstacle's corner by construction, and a
  string-pull over a node chain is the sampled visibility test §10 was written to replace.
- **Cancellation and threading.** Coroutines on `Dispatchers.Default`, a `StateFlow` for the result, the
  polyline handed back on the main thread — the epic's rule already, and it holds for either graph.
- **No-route cases.** Destination on land or in shallow water; destination outside the corridor; a start
  inside a zone whose exit is shorter than any detour. Each needs a stated answer, not a null.

## 7. Where the work happens: runtime or bake

Because all three inputs are already loaded on device, the cost of an edge can be **evaluated at search
time**: a bilinear depth read, a `SpeedZoneIndex.query`, and a band predicate are all cheap, and A* in
open water expands a small fraction of the grid. That is the option to try first — it keeps zone data
live, needs no `.bin`, no bake script and no dependency.

The alternative is to **precompute a cost overlay at bake time** (a per-cell class: impassable / slow /
band / free, ~150 KB as a bitfield at 25 m). It removes per-node evaluation and makes the search a pure
graph walk, at the cost of re-baking whenever zones, depth or the coastline move — and the bake pipeline
already regenerates depth, coastline and zones, so that coupling is real but not new.

**Recommendation: runtime evaluation first, a baked overlay only as a measured fix.** The one thing that
must be handled either way is that the depth grid currently loads **lazily, when the depth layer is
used**; a router that needs it must trigger that load and own the "grid not ready" state instead of
treating absent depth as deep water.

## 8. Phase plan

- **Phase 0 — feasibility harness, JVM only.** A `src/test` harness that runs the grid A* with the depth
  and zone cost function over the real corridor (the repo already runs prebake harnesses of this kind),
  asserting: a path exists between harbour pairs; no returned cell is land or below the minimum depth; a
  zone-heavy request spends measurably less distance inside zones than a naive shortest path; the result
  matches a reference Dijkstra on a small synthetic map; and nodes-expanded and milliseconds per query are
  recorded. **This needs no APK change, no UI, and no new dependency**, and it answers the feasibility
  question with numbers rather than opinion.
- **Phase 1 — on-device search.** The `route/` package with its own config and settings, the destination
  long-press, the preview polyline and pin, zoom-to-fit, and the confirmed-route panel. Still one-shot,
  no live re-planning.
- **Phase 2 — controls.** Route style and cruise speed in Settings, ETA in the confirmation dialog,
  automatic recompute on drift if the one-shot run measures comfortably fast.

## 9. Decisions the design waits on

1. **Objective priority — settled 2026-09-20:** the clock alone, at the user's word; the zone-aversion
   multiplier and the distance term are dropped (§3's superseding note, §12.1).
2. **Depth gate semantics — settled 2026-09-20:** a hard 2 m exclusion where the soundings are fine, plus the
   shore offset where they are not; the router carries its own 2 m value rather than the low-depth warning
   pair, which answers a different question (10.2).
3. **Source-aware margin — settled 2026-09-20:** the distance-to-coast proxy, chosen by the walk and written
   once in 10.2.
4. **The 300 m band — settled 2026-09-20 at the user's word:** it is **priced at the implied 5 kn**, so a
   coastal line pays for the strip it runs in and leaves it wherever leaving is not much slower. The objection
   stays on the record rather than being argued away: on this coast most of a coastal leg lies inside the
   strip, so the route may now prefer long offshore detours, and that is a reading the harness has to show.
5. **Cruise speed source — settled 2026-09-20:** the app's own free-water pace key, one home, no Route-owned
   setting and no fourth spelling (11.1).
6. **Baseline graph — settled 2026-09-20 by the switch instruction:** the corridor's visibility graph is the
   graph, and the epic's prebaked mesh stays unadopted (13.1).

## 10. The taut-string "out route" — how to build it

Raised by the user on 2026-09-20: draw **A→B**; where the line crosses land, move it onto the **tangent
points** of the land until it lies in water; where it then crosses zones, move it onto the tangent points
of the zones until it lies outside them; and when A or B starts inside a zone, leave by its **closest exit**
(A′ / B′) and route A→A′ … B′→B. This section is about building that out route; alternative routes are a
later question, and §10.6 shows the same structure answers them.

### 10.1 What that algorithm actually is

- A taut string is the straight-line-preferring shortest path, and with polygonal obstacles its bends are
  obstacle vertices — so the proposal is the visibility-graph / taut-string class itself, not a cheap
  approximation of it.
- What it returns is the shortest path **within the side choices the straight line induces** — its
  homotopy class. Passing an island on its other side is a different class and a different route, which is
  precisely the "alternative routes" the user deferred.
- The two steps are not two algorithms. Land and depth are **exclusion** (never crossed); zones are
  **price** (crossable, at a cost). Solvable sequentially they can disagree — a path that clears the land
  tangents and then re-enters land while leaving a zone — so they belong in one graph, where that cannot
  happen by construction.

### 10.2 The obstacle set, and where each piece comes from

| Obstacle | Source | Kind |
|---|---|---|
| Land | `CoastlineSpatialIndex` segments | exclusion |
| Depth < 2 m | 2 m contour extracted from the 25 m `DepthGrid` (marching squares, as `DepthIsobaths` already does) | exclusion |
| 2–3 m band | a 3 m contour — **not** in `ISOBATH_LEVELS`, which starts 2, 4, 6, 8…, so it needs its own level | price |
| 5 kn / 10 kn zones | `SpeedZone.outerRing` | price |
| Zone holes | `SpeedZone.holes` — navigable, so inside-ness is outer ring **minus** holes | geometry, not an obstacle |
| 300 m band | `Zone300Builder` | price, if decision D4 says the band counts |

**The safety margin is a geometry step, not a rule.** Dilate the exclusion set by the clearance — a few
cells at 25 m in raster terms, a contour offset in vector terms — **before** extracting the contours, so a
tangent-touching path is already clear of the shore instead of lying exactly on the 2 m line.

**Where the soundings stop, the shore supplies the wall — decided 2026-09-20, the walk's choice 3.** A cell
with no depth reading is neither walled nor open by itself: it is resolved by **distance from the
coastline**, so the exclusion set is land, the 2 m contour where the source is fine, and a fixed offset off
the coast wherever the contour cannot be drawn. The offset is a number and it is **left open**; its size
decides how much silent sea stays reachable — a small one keeps the rule to a thin ribbon, a large one walls
a bay whose mouth has never been sounded. **Consequence, stated rather than discovered later:** the bay that
opened this study may stay sealed under this rule, its mouth lying close inshore, so the offset is the lever
and the harness's window probe is what measures it.

**The geometry's resolution is licensed by the buffer — proposed by the user and adopted 2026-09-20.** If no
line may come closer than the standoff, then wall detail smaller than that standoff cannot affect any route,
so the obstacles may be **abstracted into long segments** whose tolerance is a stated **fraction of the
buffer** — a quarter of it is the working figure — instead of being traced at the data's own resolution, and
the curve is put back afterwards by the fillet. Three conditions make that safe rather than merely
convenient: the simplification is **one-sided, outward**, so an abstracted ring never intrudes into the
buffer; the dilation is applied **after** simplifying, so the margin is measured from the abstracted ring and
absorbs the tolerance by construction; and the smoothing that restores the curve **consumes** clearance
rather than creating it — an arc is the widest that fits, water-tested at every inserted point, never a
licence to relax a wall. What it costs is stated with it: a coarser ring can **seal an inlet or a thin
passage**, so how much water the abstraction deletes is a printed reading rather than an assumption.

**And the outward direction is known rather than guessed — added 2026-09-20 on the user's point.** The
distance-to-shore the coastline index already answers gives the direction away from land, and the harvested
contour segments give the same distance to the shallow edge, so the two together answer *which way is out*
everywhere: the abstraction can therefore **only ever grow the wall**, which is what makes it safe by
construction. Two refinements follow. The one-sided move is made **per edge, not per vertex**, because a
vertex can be pushed outward while the edge joining it still bites into an inlet, and the test that catches
that is an exact **segment-to-segment distance** rather than the point sampling the retired string-pull
used. And a wall that only ever grows is a **cost** rather than a benefit: intricate coast loses water, so
the deleted area is the number this rule is judged by. In a narrow inlet the field's gradient is close to
ambiguous, so the ring is validated against the field and the offending edge pushed, rather than each vertex
shoved along a gradient it cannot trust.

**If a search comes out slow, this tolerance is the first lever — settled 2026-09-20.** Coarsening the
geometry is safe by construction, since the abstraction only ever grows the wall; narrowing the corridor is
not, because it risks not finding a way round at all. The corridor's width therefore moves only on a pair
where the grown-box re-run has itself already failed.

**Accepted 2026-09-20, at the user's word:** the water this abstraction deletes is **accepted for the first
cut**. The printed area is what would reopen the question, and a lost inlet is not a reason to hold the
engine back — the rule stops being re-argued from here.

**Accepted 2026-09-20, at the user's word:** the water this abstraction deletes is **accepted for the first
cut**. The printed area is what would reopen the question, and a lost inlet is not a reason to hold the
engine back — the rule stops being re-argued from here.

### 10.3 Build order

1. **Corridor — refined 2026-09-20.** The A→B segment inflated by a margin that is a *starting guess* rather
   than a proof (1 NM plus the largest obstacle's extent), clipped to the depth grid's bounding box, and
   **grown once and the search re-run when the first run finds nothing** — a second empty answer being a
   refusal reported by name, never a silent nothing. Rejected: a box sized from the whole sounded area, and
   one shaped around the coastline between the ends, each of them paying on every ordinary route for a case
   that only fires where the map is awkward.
2. **Harvest vertices** inside the corridor from the existing indices: coastline points, dilated 2 m contour
   points, zone outer rings and hole rings. Simplify per obstacle (Douglas–Peucker already exists in
   `DepthIsobaths`) and keep the points that can matter: the extreme vertices of each obstacle as seen from
   the corridor, plus every vertex of a non-convex obstacle.
3. **Edges** — a pair is visible when no exclusion segment blocks it. Land already has the test:
   [`segmentIntersectsLand(a, b)`](../../app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt:504);
   the 2 m contour uses the same test against its own segments; zone rings use
   [`segmentsIntersect()`](../../app/src/main/java/ykws/android/maro/spatial/SpatialOperations.kt:139).
4. **Edge cost — refined 2026-09-20, at the user's word: the clock, and nothing else.** An edge costs time:
   its length at the speed in force over it — the reference cruise outside a zone, the zone's legal limit
   inside one — plus the **time a corner's slowdown costs**, which is why the search's state carries the
   **entry direction** even though the node count does not. No berth price, no zone-aversion factor and no
   distance term: distance is not bought, and a line that sits at the margin is what *taut and fastest* means
   rather than something to pay against. The traversal stays a **rule** — a priced zone's interior is not
   traversable while a way around exists — so crossing is prevented rather than discouraged, and the price is
   the same quantity the trip figure reads, so the two agree by construction instead of by reconciliation.
   Ties break deterministically (shorter, then fewer turns), never by insertion order.
5. **A\*** with a straight-line lower bound. The result's bends *are* the tangent points.
6. **Smooth — struck 2026-09-20.** The only curve is the easing inserted at a corner (11.5), fit-checked
   against the walls and the standoff; a cosmetic Chaikin pass over it would spend exactly the clearance that
   check has just measured. The build order therefore ends at the search, the easing and the emitted chords.

### 10.4 The A-inside-a-zone exception

- Keep it as a **seed**, never as an authority: the closest exit is a cheap way to choose the homotopy, but
  it can save 50 m inside a zone at the cost of two kilometres outside it.
- The honest version: candidates are the zone boundary points facing B; take the one minimising in-zone
  distance plus the outside-all-zones distance to B, validate its second leg against land with the same
  visibility test, and fall back to the general graph when that fails — the shortcut never overrides
  feasibility.
- With nested zones (5 kn inside 10 kn) the exit target is the boundary of the **union**, not of the
  innermost ring.

### 10.5 Risks, stated plainly

- **A pulled string is not automatically the shortest path** when the pulls interact — a spiral bay, a
  chain of islets. The incremental move is order-dependent, which is why 10.3 is a graph and not a loop of
  tangent moves.
- **Corridor sufficiency is an assumption**: a route needing to leave the corridor is not found. Inflate
  and re-run is the fallback, and the corridor must be stated rather than implied.
- **Contour noise**: the 2 m contour from 25 m cells is blocky and can carry spurious loops, and
  Douglas–Peucker at `ISOBATH_EPSILON_M = 8.0` moves the boundary by up to 8 m — a safety-relevant
  transform whose tolerance must stay small against the margin.
- **A taut path hugs the shore at the margin**, which is correct and reads as "as close as allowed"; a
  comfort bias, priced, is the knob that pushes it off.
- **The out route can be long**: with a peninsula between A and B the tangent chain is still correct, and
  it produces a long coastal leg rather than a strait crossing — nothing in this algorithm proposes the
  other.

### 10.6 What this changes

- The node set becomes the **corridor's obstacle vertices** (thousands) instead of the 25 m grid (millions),
  which demotes the raster to three jobs: contour source, validator (sample the polyline at 25 m and assert
  water, depth and source trust) and fallback.
- The two primitives the algorithm needs already exist and are tested —
  [`segmentIntersectsLand()`](../../app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt:504)
  for line-of-sight over water and
  [`segmentsIntersect()`](../../app/src/main/java/ykws/android/maro/spatial/SpatialOperations.kt:139) for
  zone rings — so the new geometry is the corridor harvest and the two contours, not a new geometry engine.
- **Alternatives come for free later**: the same vertex graph holds every homotopy, so "the other side of
  that island" is one re-query with a passage forbidden, or a k-shortest-path ask — no second algorithm.

## 11. Standoff, speeds and the comfort ceiling — requirements added 2026-09-20

Stated by the user: keep a **minimum 25 m buffer** to the taut line, as a `maro.properties` variable;
**minimise sharp turns**; in-zone speeds are **reglementary**; outside a zone the assumed speed is
**25 kn** (property); the track must be curved so that **lateral force stays under 2 g** (property).

### 11.1 The properties

| Key | Meaning | Home |
|---|---|---|
| `route.freeWaterPaceKn` | the speed assumed outside any zone — **the same quantity, so the same key; the `route.cruiseSpeedKn` this section first proposed is withdrawn** | `maro.properties`, 3–40 kn, default 28, bounds and accessor in `AppConfig` |
| `route.zoneBerthM` | the clearance kept from a priced zone's boundary — **the standoff's zone half, and it already exists** | `maro.properties`, 0–200, read live |
| `route.turn.lateralAccelMps2` | lateral acceleration ceiling (m/s²), from which the minimum turn radius is derived — **the only home of this value; the g-figures of 11.3 and 12.2 are this same quantity in another unit, never a second key** | `maro.properties`, read through `AppConfig.routeTurnLateralAccelMps2` — not restated here |

**Amended 2026-09-20, on code health rather than on argument.** This table first proposed `route.standoffM`
and `route.cruiseSpeedKn` as new keys; both quantities are already in `maro.properties` under other names,
and a further spelling of a value that exists is precisely the wound the engine-seam plan's step 5 was
written to close. So the design creates **no new key** — the cruise reads `route.freeWaterPaceKn`, the zone
berth reads `route.zoneBerthM`, and the ceiling reads `route.turn.lateralAccelMps2`. The exclusion
geometry's own clearance (land and the 2 m contour) reads `route.zoneBerthM` as well, unless a shore's
clearance and a zone's are ever wanted different, in which case that is a second key with a second meaning
and the decision to split it is the user's.

`ONE HOME PER FACT` applies: `maro.properties` is the single home of the values, code follows it, and no doc
restates the numbers — which is why every radius below is derived at read time rather than quoted. Note the
coincidence that 25 m is exactly one `GRID_RES_M` cell, so in raster terms the standoff is a one-cell
erosion — which is also its weakness in 11.2.

**The ceiling's value — settled 2026-09-20 at the user's word: 0.3 g, about 2.94 m/s².** It is written into
`maro.properties` when the engine is built and **tweaked from there**, the setting being a value rather than
a decision; 11.3 and 12.2 below now quote that figure rather than the 0.5 the property used to carry. It also
wears a settings row — Gentle, Normal and Sport — writing this same key, so the panel and the property cannot
drift apart.

### 11.2 The standoff means two different operations, and they are not interchangeable

- On **exclusion geometry** (land, the < 2 m contour) it is a **dilation of the obstacle**: the contour is
  offset outward by the berth the app already ships (`route.zoneBerthM`) before the graph is built, so the
  hard constraint is geometric and cannot be traded away.
- On **speed zones** a dilation would contradict the requirement that a zone be crossable when
  unavoidable, and would make a harbour whose entrance sits inside a 5 kn zone unreachable. There the
  standoff is a **priced band** — the 25 m outside the boundary carries a penalty rising as the boundary
  nears — so the route stays out when it can and crosses when it cannot.
- **The narrowing objection**: a 25 m standoff applied on both sides closes every passage narrower than
  50 m plus the vessel's width — harbour entrances and islet gaps are exactly that size. A stated fallback
  is therefore part of the requirement, whether that is a second, lower standoff value or a failure with a
  reason; a silently relaxed route is not acceptable.

### 11.3 The 2 g figure does not do what it appears to do

- `r = v² ÷ a`. At 25 kn (12.86 m/s) and 2 g (19.62 m/s²) the minimum radius is **8.4 m** — narrower than
  a planing boat can turn and a constraint that forbids nothing, since every corner the taut string can
  produce is far wider.
- A comfort ceiling on a planing hull sits nearer 0.15–0.3 g: at 0.25 g and 25 kn the radius is
  **≈ 66 m**, which does reshape the route — a 90° course change then needs a 66 m arc and the corners are
  visibly rounded.
- The two cases differ by a factor of eight in the radius, so this single number decides whether the
  requirement is inert or decisive. **It is the first thing to confirm.**
- Speed and radius interact across a zone boundary: 5 kn at 0.25 g gives **≈ 3.5 m**, so turning inside a
  slow zone is almost free while turning at cruise is expensive.
- **Settled 2026-09-20 at the user's word: `route.turn.lateralAccelMps2` starts at 0.3 g, about 2.94 m/s²**,
  written into the property at implementation and tweaked from there. The derived radii are then **70 m at
  28 kn**, 56 m at 25 kn, 36 m at 20 kn, 20 m at 15 kn, 9 m at 10 kn and 2 m at 5 kn. The value the property
  used to carry, 0.5 m/s², gave 415 m at 28 kn and left nearly every inshore corner sharp — so this is the
  change that makes the easing exist in the water the boat actually sails.

### 11.4 What the two requirements do together

- Because the radius is speed-dependent and the edge cost is `length ÷ speed`, the least-cost route will
  tend to **start its turn inside the slow zone** — entering the zone slightly early to round the corner
  there — which answers the requirement as written and will look surprising on the map.
- That behaviour must be named and switchable, not accidental: the alternative is to price a crossing high
  enough that the route prefers the wide, fast, awkward turn. **Superseded by §12.4** — with the speed
  derived from the geometry, the wide-and-fast turn is what the router takes, and turning inside a zone for
  cheapness does not arise.

### 11.5 Two mechanisms, not one, for "minimise sharp turns"

- **Feasibility (hard)**: reject a transition between consecutive edges when `Δθ ÷ length > 1 ÷ r_min(v)`,
  evaluated with the speed of the edge being entered — a curvature cap, not a penalty.
- **Preference (soft)**: keep the heading change small with a cost `k_turn · Δθ`; with the cap in place
  much of "sharp turns" is already removed by feasibility, and the cost decides between the remainder.
- **Geometry**: insert an arc of radius `r_min` at each surviving vertex (a fillet) and re-validate every
  fillet against the standoff **and** the depth rule — a fillet cuts the corner, moving the track towards
  the inside of the turn, which is where a hazard would sit. A fillet that will not fit signals a corner
  the graph should not have chosen: re-run with a larger turn cost rather than emitting an over-tight arc.
- **The curve itself, chosen by the user on 2026-09-20: an easing spiral where the water allows, a circular
  arc where it does not, and a sharp vertex where neither fits** — curvature building from straight to the
  limit instead of jumping, which is what makes the drawn turn read as a trajectory rather than as a round
  turn. Three consequences travel with it. The easing **costs room**, so the fit test shortens the transition
  **before** it reduces the radius, and the radius — and therefore the speed — is what the tightest point
  sets in all three cases. The price a corner carries must be the **spiral's own length**, not the arc's, or
  the search and the drawn line disagree again. And the easing's geometry lives in **one home**, called by
  both the drawing and the price, because three copies of a turn's formula are what produced the retired
  engine's fillet that never drew a curve.
- **Its precision, decided 2026-09-20 on the numbers rather than on taste.** A curve is emitted as chords
  whose step is **the shorter of 10 m and 10° of arc**, which is a centimetre of deviation on the widest
  turns and a few centimetres on the tightest, and roughly **nine points per corner at any radius** — about
  two hundred points over a twenty-corner route. The length a chord loses against its arc is a few
  centimetres over a whole turn, under a hundredth of a second at cruising speed, so the sampling's own
  error sits far below the uncertainty in the assumed speed — the honest comparison, and the reason finer is
  wasted. Two numbers are printed with it: **the largest deviation between the emitted chords and the curve
  they describe, and the seconds that sampling costs**, so the density is a reading and not a hidden number.
  The wall geometry's tolerance is a different figure and lives where it belongs: a **quarter of the
  buffer**, outward only.
- The emitted track is then the filleted polyline; the Chaikin pass of §10.3 step 6 becomes optional
  cosmetic work on top of it, not the mechanism. **Revised by §12**: the cap is not a filter — a corner
  forced by a hard constraint is driven slower, never refused.

### 11.6 What this changes above

- §10.2's "dilate by the clearance" now has its value: `route.standoffM`, applied to the exclusion contours,
  with the zone band priced rather than dilated.
- §10.3 step 4's "small turn penalty" is superseded by 11.5's cap plus price pair, and step 2's
  simplification tolerance must be tied to the cap — a coarse Douglas–Peucker tolerance turns a smooth
  coastal tangent chain into corners the cap then refuses.
- A fillet-and-revalidate step joins §10.3 before the cosmetic smoothing.

### 11.7 Open points

1. The standoff's subject — **answered 2026-09-20:** land and the 2 m contour take a hard dilation, a zone
   boundary takes a price (11.2), and the one live branch is the water narrower than twice the margin.
2. The lateral ceiling — closed 2026-09-20: 0.2 g as the `route.maxLateralG` default recorded in 11.1,
   with a three-level settings row around it.
3. When the standoff makes a route impossible: relax to a second value, or fail with a reason?
4. Is the 300 m band a zone for the speed model — does 25 kn apply inside it?
5. May the route enter a zone to do its turning (11.4), or must that be suppressed?

## 12. Speed adapts to the geometry — added 2026-09-20

Stated by the user: where land or another hard constraint forces a sharp turn, **the constraint wins and the
speed is reduced** — the speed being computed so that the turn stays under the acceleration ceiling. This
replaces any reading of §11.5 in which curvature could refuse a route.

### 12.1 The rule as precedence, not as a new constraint

- Hard constraints — land, the 2 m contour, `route.standoffM` — decide the **geometry**; the comfort
  ceiling decides the **speed profile** the geometry is driven at. Comfort never makes a lawful route
  infeasible, it only makes it slower — and the ordering between the three is **feasibility, then speed,
  then distance and shape**.
- **Speed has priority wherever no obstacle prevents it** (user, 2026-09-20): the geometry is chosen so that
  cruise is preserved, and a reduced speed is permitted only where land, depth or the standoff force a
  corner tighter than `r_min(cruise)`. The speed is therefore *derived*, never optimised —
  `min(cruise, zone limit)` on a leg and `sqrt(a · r)` on an arc — and the search does not trade speed
  against distance.
- So the curvature cap of §11.5 stops being a filter in the graph, and `k_turn` of §10.3 loses its
  hand-tuned value: the price of a heading change becomes the time that corner actually costs, which is the
  same quantity the priority cares about.
- Cornering speed is `v = sqrt(a · r)` with `a = route.maxLateralG` and `r` the arc the geometry gives at
  that corner; the time spent on an arc of angle Δθ is `t = Δθ · sqrt(r / a)`.

### 12.2 What sets a forced corner's radius — the interlock with the standoff

- A fillet swings as wide as the water and the standoff allow, and never tighter than the standoff: rounding
  the sharp point of a cape at 25 m gives `r ≈ 25 m`, so `v = sqrt(a · 25)` — **about 16.7 kn at the 2.94 the
  ceiling now starts from**, where the 0.5 the property used to carry gave 6.9 kn.
- So the two settled requirements do interlock, and the sign of that interlock follows the ceiling: at 2.94 a
  cape rounded at the standoff costs about 17 kn — a real corner, not a crawl — so the "keep cruise at the
  corners" claim survives on this coast.
- A corner can also be widened rather than slowed — a larger radius costs distance and buys speed — and the
  router compares the two in time. Slowing is permitted, not preferred.
- The floor moves with whatever relaxation the standoff's failure case allows: at a 10 m standoff a cape
  corner is `r ≈ 10 m` → about 10.5 kn at the settled ceiling.
- **The fillet is the widest arc that fits** the water and the standoff, up to `r_min(cruise)` — the radius
  at which the corner costs no slowdown at all — so speed is preserved wherever the water allows and a
  slower corner is never chosen to shorten a path. A fillet that meets another obstacle is **narrowed,
  never moved**, and the path is re-validated after insertion.
- **Nothing is unnavigable because of curvature**, since the speed may fall towards zero; the practical floor
  is the user's patience, not the geometry.

### 12.3 What a corner actually costs in time

- The arc is the small term; the **brake-and-accelerate pair around it is the large one** — from 25 kn to
  13.6 kn and back at 0.2 g is roughly 8 s and 70 m, against a couple of seconds of arc.
- That pair belongs in the **search's cost from the first version**, not only in the ETA: without it the
  cheapest route would prefer the shorter, slower corner, which inverts the stated priority — the one place
  where a deferred term would quietly decide the design. It brings a longitudinal limit as the second
  property, shipping with the code that reads it and not before.

### 12.4 What this changes above

- §11.4's emergent behaviour disappears rather than weakens: the router does not slow down to turn cheaply,
  and does not enter a slow zone for that reason either — it turns as wide as the water allows to keep
  cruise, and accepts a lower speed only where an obstacle forbids the wide arc.
- §11.5 collapses to two mechanisms: a priced heading change whose price is the slowdown it implies, and a
  fillet whose radius is chosen by geometry.
- §10.3's edge cost gains one term — the speed of the segment, `min(cruise, zone limit, corner-limited)` — so
  the search and the emitted plan use the same numbers.
- **`RouteResult` needs no change of shape — amended 2026-09-20.** The profile this bullet demanded is
  already derivable: a leg carries its length and its time, so its speed is the quotient of the two, and the
  only requirement is that **legs are cut where the speed changes** — at an arc and at a zone boundary —
  which the fillet and the price regions already do. The shared result therefore keeps carrying points and
  per-leg times, and the app side (the panel, the trip cell and the saved course) is untouched by this
  engine's arrival.

### 12.5 Open

1. Is a corner's speed *shown*, or only reflected in the ETA and in how the track is drawn?
2. Does the router get a speed floor below which a corner counts as unnavigable, or is every corner
   navigable because the speed can fall towards zero?
3. May a voluntary slowdown ever shorten a route — as written, no: the only reductions are the ones the
   constraints force.

## 13. Implementation plan

Written 2026-09-20 on `feature/other-routing`, from the design of §10–§12. **Stage 0 waits on none of the
open decisions** — the harness parameterises them — which is why it is the stage to build first.

### 13.1 Staging

| Stage | Content | Touches the APK? | New dependency? |
|---|---|---|---|
| **0 — feasibility harness** | the corridor, the graph, the search, the speed profile, all in `src/test`, plus the measurements | No | No |
| **1 — on-device route** | the `route/` runtime package, long-press destination, preview polyline and pin, ETA | Yes | No |
| **2 — controls** | cruise speed, the comfort row, route style | Yes | No |

**The switch, planned 2026-09-20 at the user's instruction:** the app moves to this engine **as soon as it is
implemented**. The mesh engine is deactivated and is not restored, §14.5's comparison becomes a reading taken
when someone wants one rather than a gate, and a route this engine cannot answer is a defect to fix rather
than a reason to fall back. The node-set question this paragraph left to Stage 0's numbers is therefore
settled by the instruction: the corridor's visibility graph is the graph, and the epic's prebaked mesh, with
its JTS + poly2tri `testImplementation` pair, stays unadopted.

### 13.2 Files

Runtime, **in the app's own slicing rather than in a package of its own** — the epic settled that on
2026-09-19 and this table's roles change nothing about it: the plan type joins `data/model/`, the geometry,
graph and search join `spatial/`, and the view model, host and overlay join `ui/map/` (Stage 1; the shape
the epic proposed, minus the bake it no longer needs). One naming consequence, since the incumbent already
owns a `RouteSearch` in `spatial/mesh/`: one of the two carries a qualifier, and the second engine's is the
one to move, its name being the newcomer.

| File | Role |
|---|---|
| ~~`RouteConfig.kt`~~ | **dropped, per the epic's naming rule** — the values come from `maro.properties` through `AppConfig`, and the region bounds from `BuildConfig` |
| `model/RoutePlan.kt` | the speed profile: polyline, per-segment speed, corner arcs, distance and ETA |
| `obstacle/RouteContours.kt` | the 2 m and 3 m levels, and the standoff dilation |
| `obstacle/RouteObstacles.kt` | the exclusion set, harvested per corridor |
| `graph/RouteVisibility.kt` | the visibility test between two points over that set |
| `graph/RouteGraphBuilder.kt` | corridor → vertices → edges, with costs |
| `search/RouteSearch.kt` | A* over that graph |
| `search/RouteSpeedProfile.kt` | corner arcs and the speeds derived from them |
| — | **no Route-owned prefs**: the cruise is `route.freeWaterPaceKn` and the ceiling `route.turn.lateralAccelMps2`, both read through `AppConfig` per 11.1 |
| `ui/RouteViewModel.kt` · `ui/RouteHost.kt` · `ui/RouteOverlay.kt` | state, map hook, drawing and the dialog |

Dropped from the epic's list: `RouteMeshRepository.kt`, the `prebake/` package and `tools/bake-route.bat` —
this design ships no mesh, so there is nothing to bake. They return only if Stage 0 says the vertex graph is
too slow.

Tests, `app/src/test/java/ykws/android/maro/route/` (Stage 0): `RouteVisibilityTest`, `RouteCorridorTest`,
`RouteDilationTest`, `RouteCostTest`, `RouteSpeedProfileTest`, `RouteScenarioTest`, and `RouteHarness` — the
runner that loads the real assets and prints the measurements.

### 13.3 The Stage 0 harness

- **Inputs**: the baked region files already in the tree — `data/app-assets/coastlines/nice-menton.bin`,
  `data/app-assets/regulated-zones/nice-menton.bin` and `data/app-assets/depth/nice-menton.bin` — the same
  region `BuildConfig.REGION_ID` names in the epic **and the same three serializers the app itself reads
  them with**. Amended 2026-09-20: the depth side needs no Context after all —
  [`DepthSerializer.deserialize()`](app/src/main/java/ykws/android/maro/data/depth/DepthSerializer.kt:53)
  takes a `java.io.InputStream`, so the harness loads the shipped `.bin` through the app's own reader. The
  fallback this line first allowed — rebuilding the grid from the two `.asc` sources — is **withdrawn**:
  `.asc` is named among the files the rulebook forbids opening, and a harness reading its own copy of the
  world is not measuring the world the app ships.
- **Steps**: corridor → exclusion set (dilate land and the 2 m contour by `route.standoffM`) → vertices →
  visibility edges → costs → A* → fillets → speed profile.
- **Contours come free of new geometry**: [`DepthIsobaths.build()`](../../app/src/main/java/ykws/android/maro/data/depth/DepthIsobaths.kt:33)
  already takes an explicit `levels` list, so `listOf(2f, 3f)` is a call, not a feature — and its fine-level
  rule (a level ≤ 10 m is traced only where the source resolution is fine, never faked from 115 m EMODnet)
  is precisely the source trust §4 of the design asked for. The 3 m level is therefore admissible without
  new data work.
- **Harvest primitives exist**: [`segmentsInBbox()`](../../app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt:469)
  for the land side and [`segmentIntersectsLand()`](../../app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt:504)
  for line of sight; zone rings come from the zone list itself (`SpeedZone.outerRing` / `holes`), filtered
  by the corridor bbox — `SpeedZoneIndex` exposes no bbox query and needs none.
- **Measurements printed per scenario**: the incumbent's own six — vertices, total turn, worst single
  corner, minimum zone clearance, length, ETA — so the two engines' lines are read side by side, plus the
  figures this engine alone owes: nodes expanded, search milliseconds, the corridor's vertex and edge
  counts, the unsounded length the route admits, the distance inside each zone and the straight-line ratio.
- **Assertions**: no leg crosses land or the dilated 2 m contour; every vertex clears the standoff; the
  result matches a reference Dijkstra to 1e-6 relative; two runs are byte-identical (determinism); a
  zone-priced route spends less distance inside zones than the naive water-only path; **the traversal rule
  costs no route on the acceptance pair**, which is the incumbent's own adoption gate; and every no-route
  case of §6 returns a named reason rather than an empty answer.
- **Scenarios**: (1) **Baie des Milliardaires → Port de La Salis** as the acceptance case, with its two
  in-band endpoints; (2) a synthetic corridor whose answer is known by hand; (3) a boxed-in no-route case;
  (4) a passage narrower than twice the standoff, which is the failure case 11.7 leaves open; (5) a corridor
  the route has to leave, asserting the refusal is reported rather than returning nothing.

### 13.4 What each open decision would change in the code

- **The standoff's subject** — the only structural one: a hard dilation lives in `RouteObstacles`, a
  priced band lives in the cost function. The harness runs both and reports the difference.
- **The 300 m band** — one more priced region; a flag in the harness, a row in the settings later.
- **The ceiling, the corridor's width and the standoff's fallback** — weights and values, no structural
  change, and all three are the user's word rather than the design's.
- **The node set** — decided *by* Stage 0's numbers rather than before it, per 13.1.

### 13.5 Order of work

1. The three keys already exist in [`maro.properties`](../../app/src/main/assets/maro.properties:1) — `route.freeWaterPaceKn`, `route.zoneBerthM`, `route.turn.lateralAccelMps2` — and no `RouteConfig` type is built (11.1, 13.2).
2. `RouteContours` — the 2 m and 3 m levels and the standoff dilation, pure and tested.
3. `RouteObstacles` — the corridor harvest from the land index and the zone list.
4. `RouteVisibility` + `RouteGraphBuilder` — edges and costs, tested on the synthetic corridor.
5. `RouteSearch` — A*, deterministic tie-break, Dijkstra reference in the test.
6. `RouteSpeedProfile` — the easing at each corner, the speeds derived from it, and the ETA.
7. `RouteHarness` + the Cap d'Antibes scenario, then read the numbers before writing any runtime code.
8. Stage 1 — `RouteViewModel`, `RouteHost`, `RouteOverlay`, the long-press hook, and the strings in both
   locales (`res/values/strings.xml` and `res/values-fr/strings.xml`, per §1 of `AGENTS.md`).
9. Stage 2 — the settings rows.

### 13.6 Build and validation

- Stage 0 needs no build: `gradlew test --tests "*Route*"` is the whole validation, and it changes no APK.
- Stage 1 validates with `apk-build.bat`; the device pass stays the user's call.
- Nothing is committed, staged or pushed by any hop of this plan without the user's word.

## 14. The two engines, compared (2026-09-20)

Written after the shipped engine's fourth device cycle, on the user's words *this algo doesn't work*. The incumbent's own record is `260920_FEAT_PLN_Route_trajectory-quality.md`. **This section is a discussion: nothing below is a decision and no file outside this one is touched.**

### 14.1 What the incumbent actually is, and why its defects are structural

- Prebaked bed of triangles: about 46,600 nodes and 3.85 MB, water = the coastline oracle + a 2.5 m depth gate + a bridging corridor; A\* on the mesh's own edges with a cost of time plus a turn price plus a berth price, zones forbidden with a priced fallback; then **three geometric passes** — a merge, a shortcut walk over the bake's triangles, and a fillet at `R = v² / a` shipped at 0.5 m/s².
- **The fabric decides the line.** Mesh spacing is 60 m near a constraint and 500 m offshore, so inshore the path's vertices sit wherever the triangulation put them, and under a time-only cost *hugging* whichever constraint is cheapest is optimal — which is exactly what "jagged, hugging the zones" describes.
- **Geometry and cost live in two places, and every join between them is a place to get it wrong.** This feature has now recorded **four** instances of a pass bypassing the priced model: the sampled string-pull that could not see a hole the gate had closed, the fillet's speed derived from times that already carried the turn price, the shortcut that asked no limit, and the merge and shortcut that rotated corners they never re-priced. That count is not a run of bad luck; it is what an architecture produces when the line is built by passes that stand outside the cost model.

### 14.2 What this design changes, structurally

- **The path bends only where an obstacle forces it** — a visibility graph whose nodes are coastline, contour and zone-ring vertices — so **straightness is a property of the construction rather than of a pass afterwards**. There is nothing to straighten and no fabric to zig-zag along.
- **Exclusion and price are separated by kind:** land and the < 2 m contour are geometry, dilated by `route.standoffM`, and cannot be traded at any weight; zones and the band are crossable and priced. That is the incumbent's own P2 policy, reached from the other side.
- **Speed is derived from the geometry, never the geometry from a speed** (§12.2): the fillet is the widest arc the water and the standoff allow, and the speed is `sqrt(a · r)`. The incumbent does the reverse — a comfort cap fixes a radius the 60 m fabric usually cannot host, which is why at 0.5 m/s² the radius is 415 m at 28 kn inshore and the smoothing is inert exactly where the boat is.
- **No bake, no artifact, no new dependency:** the three inputs are already loaded by the app and two of the primitives the graph needs are already tested. A zone or depth change alters nothing structural.

### 14.3 Which reported symptom each explains

| Symptom from the device | Incumbent | This design |
|---|---|---|
| jagged, no straightening | the fabric plus a time-only cost; patched twice | by construction, with no pass to write |
| corners not rounded | the fabric cannot host the cap's radius; the fillet's fit is the whole fight | the arc *is* the geometry and the speed follows it |
| crosses the zones | a pass asked no price; now forbidden and reported | zones are obstacles with a price, in the one graph |
| the bay not routable | the gate reading NoData as shallow; patched by a bridging corridor | the same gate and the same risk, one contour instead of a fabric |
| the line hugs a zone edge | cheap to hug under time-only cost; patched by a berth price | still true — a taut path sits at the standoff, so a comfort bias is still owed |

### 14.4 Objections to this design, strongest first

- **Corridor sufficiency is an assumption, not a proof** (§10.5): the graph is clipped to the A→B corridor inflated by a margin, so a route that must leave it is simply not found. Inflate-and-re-run is a policy, and the corridor's width is a tuning constant.
- **Nobody has measured the node count or the latency.** "Thousands of vertices" is an estimate; a coast of islets and nested zone rings is the worst case, and the visibility edges are the quadratic term. The incumbent's worst search is a *measured* 127.9 ms, and that is the number to beat.
- **Contour error becomes visible geometry.** The 2 m contour from 25 m cells is blocky and the existing simplify tolerance is 8 m; on the incumbent those errors hide behind a 0.5 m inset and a fabric, while a taut path lies **on** the transformed boundary. A safety-relevant tolerance is more exposed here, not less.
- **It replaces the engine beside a working app side.** The bake, the 3.85 MB asset, the repository and its tests would go — but the toggle, the aim, the confirmation panel, the trip cell, `TrackFromCourse` and the save are engine-agnostic and stay, provided the seam keeps its shape. §12.4's speed profile is the one proposal that reaches back into the UI.
- **This file states it was opened on `feature/other-routing`** while the incumbent lives on `feature/route`, so adopting it is a branch decision — and which tree this document is standing in is not something this pass can verify from here.

### 14.5 The cheapest decisive step

- Run **this design's Stage 0 harness** — no APK change, no dependency, `gradlew test` only — on the **same pairs the incumbent's probe already measures**, printing the same six numbers (vertices, total turn, worst single corner, minimum zone clearance, length, ETA) plus nodes expanded and milliseconds.
- The incumbent's instrument is already fair: `RouteTrajectoryProbeTest` derives its inshore pairs by rule from the mesh and prints drawn against priced seconds. A new engine measured on the incumbent's own pairs and metrics is the only comparison that settles this without faith — and the acceptance case is already named in §13.3: **Baie des Milliardaires → Port de la Salis**, the pair whose failure opened the trajectory study.
- What would make this design lose on its own terms: a corridor that has to be inflated on the acceptance pair, a node count that puts the search above the measured ceiling, or an accepted route that touches the dilated 2 m contour — the last being a safety failure and the one result that would end the comparison outright.

### 14.6 What is not in dispute

- The traversal policy survives a change of engine: zones forbidden while a way around exists, priced and reported when there is none, is this design's price model stated as a rule.
- The properties 11.1 now lists, under the names the app already uses, and the requirement list of §1 and
  §11 are engine-independent, so the requirement set needs no rewriting — only a different machine to
  satisfy it.
- The incumbent's four bypasses each cost a device cycle to find. A design whose geometry *is* the
  answer has fewer joins to get wrong, and that is its strongest argument — as long as the corridor and the latency hold.

## 15. The technical resolutions taken on 2026-09-20

Written at the user's word, to address this design's own review findings, with feature and code health as
the test. Anything that changes what is drawn or what the trip figure reads stays out of this section and
is named as the user's.

- **No new property key, and the two this file proposed are withdrawn in 11.1.** The three values the
  design needs already have homes the app reads today; a fourth spelling of a value that exists is a defect
  whether or not a second engine ever ships.
- **The ceiling's value stays the user's**, because it sets the radius of every drawn corner: the shipped
  0.5 m/s² and the 1.96 the early arithmetic used differ fourfold in that radius, and 11.3 and 12.2 now
  carry the shipped value's own numbers so the difference is visible rather than buried.
- **The shallow contour's NoData policy, revised by the walk on 2026-09-20.** A cell with no sounding is no
  longer left open: the exclusion set is land, the 2 m contour where the source is fine, and **a fixed offset
  off the coast wherever the contour cannot be drawn** — 10.2 carries that decision, and this bullet keeps
  only the pointer to it. The bridge the trajectory study built for the mesh is therefore not what this
  design uses, and the bay it reconnected is not guaranteed to open here; the offset decides.
- **The traversal is two runs over the one graph**, which is what makes the app's promise and this design's
  price model the same machine: run one forbids the interior of every priced zone and, when it answers, that
  is the route; run two prices them and runs only when run one has none, and the plan then carries a
  **forced crossing**, read off the drawn line and named by zone. That is the incumbent's own arrangement,
  so no second mechanism is owed and the report the confirm panel already promises keeps its shape.
- **The comparison's columns belong to the harness, and it has to declare them.** The probe's six metrics
  travel with any engine, but the figures beside them — priced seconds, the smoothing counts, the raw-chain
  flag, the largest radius — are read off the mesh engine's dossier and read as zeros for anything else. A
  comparison is therefore only fair once this harness states its own counts, which is what 13.3's
  measurement list above is for.
- **The speed profile is derivable, so the shared result keeps its shape** — the resolution is in 12.4, and
  its one requirement is that legs are cut where the speed changes.
- **One question stays open, and it is the user's because it changes the line.** Where the water is
  narrower than twice the standoff, does the route pass close and report the exposure, or is the passage
  refused so that the route lengthens or is lost? The incumbent measured that water and found its passages
  a few tens of metres wide, and the scenario 13.3 gained exists to put the same numbers beside this
  design's choice.
- **The zone margin's form, resolved 2026-09-21 after the build.** 11.2's priced band is what stands, and its
  price is **expressed in seconds** — a leg inside the margin pays extra time — so it stays something the
  clock can weigh rather than a second quantity beside it, which is what keeps this section's "the clock and
  nothing else" true. The built engine's **hard collar is therefore the deviation**: refusing every edge that
  enters the margin walls a strip this design says is crossable, and §11.2 rules that option out by name.
  **Objection kept on the record:** a price is a preference and not a promise, so a line may still run along
  a boundary wherever leaving the margin costs more time than it saves.

## 16. The revision pass — planned 2026-09-21 from the review

The engine is built and switched in, and the review that followed it returned **revise**; its verdict, its six
blockers and its should-fixes are in the epic's `## Implemented`. This is the order the corrections are taken
in, each with the reading that closes it.

1. **Measure before fixing.** Split the printed cost into **harvest, graph build and search**, with the pair
   and node counts beside them, and stop printing a total as "search". Closes when three distinct figures are
   printed per pair — first, because every correction below is judged against them.
2. **Restore the corridor's margin.** The box is sized from the obstacles it must contain — the union of the
   harvested extents, clipped to the band — and keeps its single growth on an empty answer. Closes when the
   two refused pairs are re-run and each refusal is either a way found or a named absence, with the re-run
   printing which relaxation restored a way wherever one appears.
3. **The zone margin becomes a price, in seconds.** An edge inside the margin pays extra time rising as the
   boundary nears, and run one forbids only a zone's **interior** (15, resolved 2026-09-21). Closes when the
   acceptance pair's run one answers — or its crossing is real — with the crossing count and the in-margin
   distance printed beside it.
4. **Zone holes are navigable through one predicate.** The rule and the price read the same home: outer ring
   minus holes. Closes when a synthetic zone with a hole is crossable through it, and the test pins that.
5. **The corner's slowdown enters the cost.** The brake-and-accelerate pair 12.3 calls non-negotiable, carried
   by a **longitudinal property** that ships with the code reading it — the second key this feature adds and
   the last. Closes when the price and the drawn clock agree within the stated slack on every pair.
6. **The assertion set is built.** 13.3's list in full: no leg crosses land or the dilated contour; every
   vertex clears the berth; the traversal rule costs no route on the acceptance pair; the no-route cases
   return named reasons; the narrow-passage and corridor-leave scenarios exist; the synthetic corridor still
   matches a Dijkstra reference; two runs are byte-identical. Closes when each assertion is shown to fail on
   its own revert.
7. **The should-fixes, in the same pass.** One home for the price and the accumulation; one geometry home in
   metres rather than raw degrees; the zone ring's own first vertex; the unread members deleted; refusals
   named without the mesh's vocabulary; and the determinism test made able to fail.
8. **Performance, decided by the split reading.** If the build dominates, prune the visibility pairs by angle
   or distance and reuse the harvest across the growth pass; if the search itself is over target, apply this
   design's own first lever — the **abstraction tolerance**, which may only grow — before touching the
   corridor.
9. **Re-measure every pair, and only then the device.** The pass closes on numbers, not on a green suite.

**What this pass does not do:** it moves neither the ceiling, the berth's value nor the band's price, and it
adds no mechanism the settled stages do not already name.

## 17. The second revision pass — planned 2026-09-21 from the review

§16's nine steps are built and green, and the review that followed returned **revise** a second time: three
blockers, nine should-fixes. This is the order the corrections are taken in, each with the reading that closes
it. One caution travels with the whole pass: **the §16 figures were read by the reviewing hop rather than
re-run by it**, so item 1's re-measurement is what everything else — items 6 and 7's numbers included — is
judged against.

1. **One leg, one limit — and an edge's price is a time, summed, never a maximum.** A leg's limit is resolved at
   the leg's **midpoint alone** — the zone through
   [`mostRestrictiveZoneAt`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautGraph.kt:756) and the
   band through [`inBand`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautGraph.kt:750) — the clock
   inherits that value through [`legLimitKn`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautSearch.kt:321),
   and no pass cuts a leg where the limit changes, against §12.4 and
   [`RouteResult.Success.durationSec`](../../app/src/main/java/ykws/android/maro/data/model/RouteResult.kt:22),
   which promises each leg at the limit in force over it. **The correction, first half: an edge's price is its
   time summed over its own limit regions** — each region's length at the speed in force there — so the search's
   step and the drawn clock charge one number by construction rather than two that must be reconciled. Its **one
   home is the build**, beside [`legLimits.limitKn(a, b)`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautGraph.kt:526);
   a splitter inside [`assemble`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautSearch.kt:330) would
   be the second computation that file's own note warns against. A maximum would have been *dearer* than the line
   it prices and would have inflated item 4's figure while claiming to correct it.
   **The correction, second half: the segment test the item leaned on is not exact, and the hole is on the rule
   side.** [`entered()`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautGraph.kt:84) reads the leg's
   **midpoint** and then an **odd** parity of proper crossings, so a leg clipping a zone's **corner** crosses the
   ring twice, reads as *not entered*, is allowed in run one against the forbidden test and is counted **zero**
   by the crossing report — a crossing presented as an ordinary route, which is the one outcome that report
   exists to prevent. One segment test that answers whether the leg **shares an interval with the interior** and
   returns the **entry and exit parameters** closes it, with `covers`' boundary-is-water rule unchanged: the
   rule, the price and the splitter then read one answer, nothing is sampled, and no tolerance is borrowed —
   `GRID_RES_M` being the depth grid's resolution and not the zone geometry's, and a per-pair sample landing
   inside the very all-pairs loop item 7 names.
   Closes when a leg clipping a zone is charged at the zone's limit **and** the crossing report names it — the
   paired reading on one partially clipped edge being what makes the fix falsifiable — and when a boundary
   falling **inside an emitted arc** is cut and re-priced rather than left on its corner's single value, a drawn
   corner's limits being per chord off one value for the whole corner, so "the boundary is an ordinary vertex"
   holds on straight legs alone.
2. **A sharp corner is not a free corner — and it has four causes, not two.**
   [`sharp()`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautEasing.kt:228) zeroes the corner's
   speed, the assembly skips the term on that test, and
   [`longitudinalSec`](../../app/src/main/java/ykws/android/maro/spatial/RoutePlanTiming.kt:124) returns zero for
   it — so the corner the water forced tightest is the **cheapest**, and §12.3's term is only half answered.
   **The correction, in three parts.** *One* — the refusal is **counted per cause**, because `sharp()` is reached
   by the angle and degenerate guard, by `build` returning null, by the cutback against a leg too short between
   two vertices, **and** by the water: only the last is a water answer, and the cutback case is a geometry the
   graph should not have chosen, so counting two of the four apart would still misattribute the others. *Two* —
   the charge is written on [`Turn.speedMps`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautEasing.kt:87),
   the one field both readers already consult, the price through `priceSec`'s own `longitudinalSec` call and the
   assembly beside it; a fix landing only in the assembly, or only at the guard, would correct the drawn clock
   while the path was still chosen on a free corner. *Three* — the bound is stated for what it is: the radius
   that failed is the **ladder's floor**, [`MIN_RADIUS_M = 2.0`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautEasing.kt:54),
   so `v = sqrt(a · r_floor)` ≈ 4.7 kn is an **upper** bound on the corner speed for the **water** case alone —
   hence a **lower** bound on the real slowdown — and a bare number for the other three, named as such rather
   than presented as geometry. `r_floor` is a code constant, so a different corner speed wants a third key and
   that is the user's; the tripwire is the pair's own ETA.
   The charge's size is printed with it, because it is the largest single term in item 4's re-read: ≈ 15 s a
   side, ≈ 30 s a corner and ≈ 3.5 min over item 6's seven sharp corners at the floor radius.
   Closes when no corner is exempt from the pair, the four causes are counted apart, and the price and the drawn
   clock agree within the slack on every pair — a test that fails when the term is removed.
3. **Readiness gates on every layer the search reads — through both doors, and with the window said out loud.**
   [`prepare()`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautRouteEngine.kt:112) checks the depth
   box alone, against its own KDoc at
   [line 64](../../app/src/main/java/ykws/android/maro/spatial/taut/TautRouteEngine.kt:64), while
   [`isWater`](../../app/src/main/java/ykws/android/maro/data/route/RouteSpatialAdapter.kt:131),
   [`landPolylinesIn`](../../app/src/main/java/ykws/android/maro/data/route/RouteSpatialAdapter.kt:174) and
   [`distanceToCoastM`](../../app/src/main/java/ykws/android/maro/data/route/RouteSpatialAdapter.kt:226) answer
   permissively before the coastline lands — land not a wall at all, and the shore offset never applying.
   **The correction closes two doors and the window between them, not one.** [`route()`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautRouteEngine.kt:127)
   carries its **own** depth-only gate, so the seam's readiness member is read by **both**; the adapter triggers
   the **coastline's own load** as it already triggers the depth grid's — `loadIfNeeded()` reads the coastline's
   state and leaves it null today; and the case is **stated**, which costs a member on
   [`TautWorld`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautWorld.kt:40) that **every**
   implementation answers, the harness and the test fakes included, plus an entry on
   [`RouteUnavailableReason`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngine.kt:121) with its
   `@StringRes` id and one string in each locale. Nothing pins that closed set today — no exhaustive `when`, no
   test on its size — so the member is added where the set is declared and the string lands in
   [`values/strings.xml`](../../app/src/main/res/values/strings.xml:581) and
   [`values-fr/strings.xml`](../../app/src/main/res/values-fr/strings.xml:580) together.
   Closes when a route armed inside that window is refused with a **named** reason or prepared, with what the
   user sees meanwhile said on the panel rather than left as a silence.
4. **The ETA's rise is attributed before it is believed, with the numbers it is judged against.** The pass takes
   the acceptance pair from 14.96 to **22.79 minutes**, and the midpoint rule charges *less* time, not more, so
   the cause is still unmeasured. **Re-read after items 1–3 with the three defects' shares told apart:** the
   summed per-region price adds the time the midpoint rule was losing, item 2's charge adds ≈ 3.5 min over the
   seven sharp corners, and item 1's crossing detection may make run one refuse an edge it used to allow — so the
   figure is expected to sit **above** 22.79 minutes before it settles. It is reported beside the pair's
   vertices, total turn, worst corner, clearance and length, and a figure of that size inside the trip cell is
   the user's to accept knowingly rather than a rounding to explain away.
5. **The nine should-fixes, enumerated, each naming the finding it closes and the reading that shows it closed.**
   - **One home for the margin's price *and* for its own reading** — the price is read at the leg's closest
     approach while the printed in-margin distance counts only the margin's metres, so the two measure
     different quantities and the reading is the first thing to make agree.
   - **The five dead members** step 7 claimed to delete, named one by one as each goes, so "five" is checkable
     rather than asserted.
   - **One home for the in-band question**, asked in two places today.
   - **The assertion controls demonstrated by revert** — each assertion shown to fail when its own line is
     reverted — rather than by predicate.
   - **The mislabelled acceptance-pair test** corrected, so the pair it names is the pair it runs.
   - **The band path's own test**, which nothing covers today.
   - **The tolerance lever's cost printed rather than half-printed**, so the dial carries a reading beside its
     benefit.
   - **The 802 ms standing with its number named and its lever substitution stated**, rather than left as a
     figure whose levers are unnamed.
   - **The harness's world read as the app's**, so a harness reading is a reading of what ships.
6. **The tolerance decision goes back to the user with its number.** The abstraction tolerance moved 0.25 → 0.5
   grow-only, which is inside what §16 step 8 sanctions, and it is paid in the line: 218° → 448° of turn and
   **7 sharp corners against 2** on the acceptance pair. The dial is the user's if line quality outranks latency.
7. **Performance closes on the levers, against a named target.** The search reads **802 ms** against the 500 ms
   target and the refusals are the build's case at ~95 µs a pair over 780 625 pairs, so the sanctioned prune and
   the wall index's bounding-box cell walk are taken only with the measurement that shows what each buys — a
   geometry prune being able to delete the only way round. The step closes on **one of two stated outcomes and
   no third**: either the target is met, or the target moves and the reason is written beside the new number —
   so it can never close on whatever the levers happened to buy. Item 1's summed price and the splitter it
   replaces both land inside this bottleneck, so their own cost is carried with them and printed.
8. **What the corrections may not do — the feature set stands exactly as the epic describes it.** Every
   correction above is internal: the route stays on water, **never enters a priced speed zone while a way around
   exists**, reports a forced crossing **by name** where no way does, and its ETA is read off the line that is
   drawn, each leg at the limit in force over it. Nothing here adds a setting, a row, a panel or a key beyond the
   longitudinal property §16 already named, and no correction asks for a behaviour the epic does not already
   promise — the honest figures items 1, 2 and 4 produce are the whole of what a user sees differently.

**What this pass does not do:** it moves neither the ceiling, the berth's value nor the band's price, and it
does not touch the corridor's width.

### §17 as reviewed, 2026-09-21 — verdict **revise**, and the corrections folded into the items above

§17 was put to an independent review before anything was built. The three targets were the right defects and each
named the right line, but three of the mechanisms first written for them could not deliver the reading their own
"Closes when" sentence demanded. **Items 1–5 and 7 above now carry the corrections and item 8 states the limit they
may not cross, so nothing below is open work.** What follows is the review's own reading, kept as the reason each
item reads the way it does rather than as a verdict still to act on.

- **The per-edge quantity is a maximum, and a maximum is not the time a boat spends.** An edge read at the most
  restrictive limit anywhere along it is dearer than the drawn line that pays the zone's limit only inside the
  zone — which is the very disagreement item 1 exists to close, and it inflates the ETA item 4 then asks the
  user to accept. The honest quantity is the edge's **time summed over its limit regions**, each region's length
  at the speed in force there, homed in the build beside
  [`legLimits.limitKn(a, b)`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautGraph.kt:526) for the
  reason that file already states; a splitter inside [`assemble`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautSearch.kt:330)
  would be the second computation it warns against.
- **The segment test item 1 trusts is not exact, and the hole is on the rule side.** [`entered()`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautGraph.kt:84)
  reads the leg's **midpoint** and then an **odd** parity of proper crossings, so a leg clipping a zone's corner
  crosses the ring twice, reads as *not entered*, is allowed in run one and counted **zero** by the crossing
  report — a crossing presented as an ordinary route, which is the one outcome that report exists to prevent.
  What closes it is one segment test answering "does this leg share an interval with the interior" and returning
  the **entry and exit parameters**: the same answer the splitter needs, so nothing is sampled and no tolerance
  is borrowed. That also answers this review's own note that `GRID_RES_M` is the depth grid's resolution and not
  the zone geometry's, and that a per-pair sample would land inside the eager all-pairs loop item 7 names.
- **A sharp corner has four causes, not two.** [`sharp()`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautEasing.kt:228)
  is reached by the angle guard, by `build` returning null, by the cutback against a short leg **and** by the
  water, so separating two of the four still misattributes the others. And `r_floor` bounds the **ladder** —
  [`MIN_RADIUS_M = 2.0`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautEasing.kt:54) — not the water,
  so `v = sqrt(a · r_floor)` ≈ 4.7 kn is an upper bound on the corner speed for the **water** case alone and a
  bare number for the rest. The charge belongs on [`Turn.speedMps`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautEasing.kt:87),
  where the price and the assembly both read it, or the path stays chosen on a free corner; and its size belongs
  in the reading, ≈ 30 s a corner and ≈ 3.5 min over item 6's seven — the largest single term in item 4's re-read.
- **One readiness member moves the window instead of closing it.** [`route()`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautRouteEngine.kt:127)
  carries its own depth-only gate, so the member must be read by **both** gates, and the adapter — which today
  triggers a load for the depth grid alone — must trigger the coastline's own. The case also costs a member on
  [`TautWorld`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautWorld.kt:40) that **every**
  implementation answers, the harness and the test fakes included, plus an id on
  [`RouteUnavailableReason`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngine.kt:121) and a string
  in each locale. As written it turns a silent route over land into a refusal that says nothing about what the
  user sees meanwhile.
- **And item 5 is not yet actionable.** Its nine phrases name no findings — the five dead members least of all —
  so they must be enumerated before the pass runs; item 1's closing reading tests the drawn clock alone and
  cannot see a search-versus-clock divergence; the splitter says nothing about a boundary falling **inside an
  emitted arc**, where the limits are per chord off one value per corner; item 7 closes on no target; and item 4
  lacks the numbers it is judged against.

## 18. The crossing splitter — discussed 2026-09-21, from walk item 1

Stated by the user at the walk: **add a point at every crossing and split the leg in two — the 300 m band or any
regulated zone — because each leg's speed depends on it.** That is §12.4's "legs are cut where the speed changes"
and the drawn clock's own contract, and it is the shape walk item 1 asks for. What follows is what it takes, what
it changes, and the one objection that survives it.

**Where the cut comes from.** A zone ring is geometry the tracers already own, so its crossing is a segment test:
`TautZone.interiorSpans` returns the entry and exit parameters and the splitter cuts there today. The **band** is
not — `inCoastalBand` asks `distanceToCoastM(p) ≤ coastalBandWidthM`, a distance predicate — so its boundary is a
locus rather than a stored ring and its crossing is found by **bisection on the leg**: an inside sample, an outside
sample, halved until the width is met. That invents no tolerance, costs a handful of distance queries, and lands on
the true line rather than on a sample — the difference between this and the midpoint rule being exactly where the
cut sits. A leg may cross the band **more than twice** near a headland, and may clip two zones or the same ring
twice, so the splitter takes **every** crossing — and a leg becomes its **merged intervals + 1** pieces, not the
number of crossings + 1: two sources whose spans touch, or a ring crossed in and out where a neighbour's span
begins, are one cut and one piece, because what the splitter emits is the **union of the two sources'
intervals**, each piece carrying the most restrictive limit standing over it.

**One home, and it already exists.** `LegLimits.regionsOf` is where a leg's limit is resolved; it becomes the place
that returns a leg's **spans** — a list of (start, end, limit) covering the leg, most restrictive where two overlap
— read by the search's price sum, the drawn splitter and the crossing report alike. That is what keeps the price
and the drawn clock one quantity; a second crossing-finder written for the splitter alone would be the fifth
instance of the class this feature keeps recording.

**What it changes, and it is visible.** With the band cut, a coastal leg pays the band's limit over the metres that
really stand inside the strip, so the search itself now prefers a line that leaves the strip sooner, and the drawn
figure shows a slow leg on water that looks open. On this coast most of a coastal leg lies inside the strip — §9's
D4 said so when the band's price was decided — so inshore ETAs grow and the acceptance pair moves again. That is
the **faithful** reading of D4 and of the epic's "each leg at the limit in force over it", and it changes what the
trip cell shows, so it is the user's to accept.

**The objection that survives.** The band is a *compliance* shape and the boat's real pace is the quantile pace of
her own samples, so cutting the band makes the trip figure read slower than the boat will actually make good, on
water that looks like open sea. The alternative — keep the band as a price in the search and stop reporting it as
a leg's speed — re-opens the disagreement between the price and the drawn clock that three passes have closed, so
it is not free either. Honest slow leg, or unbroken promise: that is the one question this discussion does not
settle, and it is the user's.

**Cost, and it is the build's.** The band's predicate is one call per edge today; spans need two to six bisection
evaluations per crossing, per edge, over the 3 682 edges the acceptance pair's build walks — a build whose bill is
138 ms. It is affordable, and it must be **printed** rather than assumed: the build's own figure is the reading
that shows what the splitter costs.

**What it does not touch.** No vertex joins the graph the search walks — the cuts live on the drawn polyline, so the
corridor, the graph and the bake are unchanged. And a cut point is **collinear**, so the turn model must not charge
it as a corner: an inserted point whose angle is below `MIN_TURN_RAD` pays nothing, and the assertion that the price
and the drawn clock agree within the slack is what proves it.

### 18.1 What closes walk item 1, and the build order

The defect is one line deep, which is why the plan is short. [`LegLimits.regionsOf()`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautGraph.kt:972)
already splits a leg at **every zone span** — `TautZoneSet.limitSpans` supplies them, and each piece is emitted with
its own limit — but it computes `bandLimit` **once for the whole leg** from `inCoastalBand(world, a, b)`, a midpoint
(§914), and then hands that one value to every piece it emits. The band is the only source still asked at a midpoint,
so making it a **span source beside the zones** closes the item, and everything downstream follows because the price
and the drawn clock already read this one home.

1. **Settle what the band's predicate is, because it decides the geometry.** `distanceToCoastM` is the distance to
   the nearest coastline **segment** — a union of capsules around the segments — or to the nearest vertex. Read it
   before writing anything: if it is segment distance, the leg's crossing of the band is **closed-form** (offset slab
   plus the two endpoint circles, each a quadratic in the leg's parameter, intersected with the segment's own
   range), no march and no step; if it is not, the crossing is a bisection between two remembered samples.
2. **Give the band its own span source**, over the same leg, in the same shape as `TautZoneSet.limitSpans` — the
   intervals of the leg whose points lie inside the band. Each nearby segment contributes **at most one** interval,
   because the distance to a segment is convex along a line, which is what makes the union cheap and exact.
3. **Merge the two sources into one breakpoint list and evaluate each piece's limit once per piece.** A piece lies
   wholly inside or outside each source by construction, so one membership question per piece is exact — and the
   per-leg `bandLimit` boolean disappears with it. `regionsOf`'s signature and its role as the one home are unchanged.
4. **Nothing downstream needs editing, and that is the test of the change.** [`TautGraph.priceSec(edge)`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautGraph.kt:470)
   is computed from `regionsOf` at the build, and [`drawnClock()`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautSearch.kt:289)
   reaches the same splitter through `drawnPieces` — so the search begins paying the band over the metres really
   inside it, and the drawn line is cut there — with no edit in either. If an edit *is* needed in the search or the
   assembly, the change has been put in the wrong place.
5. **Prove the cut is free.** A split point is collinear, so the clock must charge it nothing: `RouteTurnGeometry`
   is asked for a zero-angle turn and must return zero, or the point must be guarded below `MIN_TURN_RAD`. Pin it,
   because an inserted point charged as a corner would add a phantom price to every cut.
6. **Pin the defect with a test that fails on its revert.** A synthetic world whose band boundary falls **mid-leg**
   with the leg's midpoint outside it: assert the leg emits as two pieces, the band's limit covers the inside metres
   alone, and the price equals the drawn clock within the slack. The band tests today cover a leg wholly inside and
   wholly outside, which is exactly why the midpoint rule survived them.
7. **Re-read every pair and the split, and print the build's own bill beside the splitter's cost.** The band's
   predicate was one call per edge; it is now a handful per leg, over the 3 682 edges of the acceptance pair's build,
   whose bill is 138 ms — the figure that shows what the spans cost, taken rather than assumed.
8. **Close the item on those figures**, and with the objection the discussion left standing stated rather than
   buried: with the band cut, the trip figure reads slower on a coastal leg than the boat will make good, on water
   that looks like open sea. That is the user's to settle, and it is not a reason to leave the leg uncut.

**Not in this build, though they share the level:** the rule's refusal counted apart (item 2 — one boolean in
`fits()` answering two questions), the margin's midpoint exclusion (item 3), the assertion that pins the corner's
reference limit (item 4), the harness's two unprinted quantities and the absence that reaches no user (item 5), and
the two stale seam KDocs (item 6).

## 19. The drag pipeline and the search's cost — discussed 2026-09-21 from the device report

Reported by the user: the route takes **several seconds** to appear, a drag must not stack the searches, a new drag
must abort the one in flight, and if it is that slow the reason must be known and something must say so. What
follows is what the code says today, where the seconds go, and the choices — none of them taken here.

**What the cancellation actually is, read rather than assumed.** `ensureActive()` appears **twice** in the whole
tracer, both in the engine: once per corridor-growth attempt and once before the search. The harvest, the graph
build and the search are therefore each **one uninterrupted block**, and a cancel is observed only *between* them —
so aborting a drag waits for the phase in flight, and the graph build's all-pairs visibility loop over 92 665 pairs
has no check inside it at all. The KDoc's "checked between the steps of the build and of the search" is true of the
phases and false within them.

**And the state never stacks while the work does.** `RouteViewModel.preview()` cancels the previous job and only a
draft's result is ever accepted, so exactly one answer is kept — the requirement is met in the state machine. It is
not met in CPU terms: a cancel is not a join (the engine's own words), so the abandoned search keeps expanding while
the new one starts, and on a phone with a few cores the two compete — which is why the drag *feels* worse than a
single search. The fix for that is a check inside the two long loops, not a harder cancel.

**Where the seconds go, measured on the acceptance pair (desktop JVM, `apk-build` values):** harvest **350 ms**,
graph **258 ms**, search **1 181 ms**, wall clock **1 815 ms**. The graph is 431 vertices and **92 665 candidate
pairs** — a quadratic loop, each pair asking the wall index (1 211 segments) and the band (694) — and the search
expands **3 871 stations**, pricing every corner it considers with a water-tested easing fitted through a ladder.
A phone runs this several times slower and cold, so 2 s of desktop is easily 5–10 s of first search.

**And the request rate is the multiplier.** The preview is movement-driven with a 25 m threshold and a 333 ms floor,
so a drag asks for up to **three searches a second** while each costs ~2 s of desktop work. The engine is therefore
about six times oversubscribed during a drag, and the newest answer is always seconds behind the finger: what the
user reads as "slow" is not one slow search but a pipeline slower than its own request rate.

**What would actually buy time, in order of cost.** (a) **Cancel inside the loops** — `ensureActive()` in the pair
scan and in the station expansion, taking the abort from "one phase" to tens of milliseconds; one line each, no
behaviour change. (b) **Coalesce instead of cancel-and-redo** — while a search is in flight, remember the newest aim
and start exactly one more when it lands, so a drag costs a few searches rather than one per frame's worth of
movement. (c) **Reuse across aims** — the start is frozen and the aim moves, so the corridor, walls and graph are
nearly the same from one aim to the next; rebuilding them every preview spends the 608 ms that the aim's movement
usually does not change. This is the largest win and the largest change: it makes the engine stateful between
searches, which is what its own "one search started at a time" discipline was written to avoid. (d) **Make the
search itself cheaper** — direction quantisation, extending the pre-fit reject, or a coarse-then-fine two-phase
search — real design work, and the state space is the reason the search dominates. (e) **Raise the gates** — 25 m
and 333 ms are tuning values; asking less often is free and makes the aim lag more, which is the user's trade.

**And the feedback is a gap, not a detail.** `searching` exists on the draft and is read in exactly **one** place —
the confirm panel, which shows "no route" only when a search has landed and an aim hint while one is in flight. So
the app never says a search is running, never says how long it has been running, and the drawn line simply belongs
to an older aim with nothing marking it. The dossier already carries harvest · graph · search per search, and
nothing on device reads it, so the device's own numbers are not available to the user *or* to the next pass.

**The honest objection to all of it.** The cheapest items — the in-loop checks and the feedback — are measurable and
safe; the tempting one, reusing the graph across aims, changes the engine's architecture and its benefit depends on
how a drag moves, so taking it before the device numbers exist is betting on an assumption. Which is why the first
move is the cheapest of all: **print the three figures on the device** (they are already computed) behind the
existing debug-gated idiom, so the split is a reading — and the feedback the user needs and the numbers the next
pass needs arrive together, from one change.

### 19.1 The plan, and where the 431 vertices come from

**Where the count is made, in the code's own order.** `TautGraph.build` assembles its vertices at
[`:707`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautGraph.kt:707) — the frozen start, the resolved
aim, then **every distinct corner of every harvested wall** and **every corner of every zone ring**, deduped by a
centimetre key and filtered only by `traversable`. Those wall corners are `TautObstacles.harvest`'s own
`cornersOf(validated, …)` at
[`:279`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautObstacles.kt:279), computed on the **dilated,
simplified** wall of each clipped piece — so the coastline inside the corridor, the 2 m contour (1 211 wall
segments between them) and the zone rings all contribute, and the dilation is what turns a wall's own bends into
several corners each. Then the build's double loop at
[`:750`](../../app/src/main/java/ykws/android/maro/spatial/taut/TautGraph.kt:750) tests **every pair**:
`C(431,2) = 92 665`, exactly the figure the harness prints. So the count is a **build-time** number: it is fixed
before a single station is expanded, and it is what the quadratic loop and the search's state space are both sized
by. **§10.3 step 2 already asked for less than this** — "the extreme vertices of each obstacle as seen from the
corridor, plus every vertex of a non-convex obstacle" — and the build keeps every corner instead.

**What the numbers say about the search itself.** 3 871 stations expanded against 431 vertices means roughly nine
arrival directions tried at nearly every vertex — **A\* is walking essentially the whole state space**, which is a
Dijkstra wearing a heuristic. The bound it uses is the straight line over the cruise speed, and on this coast most
of a coastal leg is capped at the band's own limit, so the bound understates the remaining time and prunes almost
nothing. A bound that divides the straight-line distance by the **slowest limit the straight line itself lies in**
is still admissible and would prune far harder — the cheapest algorithmic win available, and invisible except in
latency.

**The order, each with its closing reading.**

1. **Say it on the device first.** `harvest · graph · search` and `stations expanded` are already computed and read
   by nothing on the phone, and the draft's `searching` flag exists and is read in one place. Surface both — the
   numbers behind the existing debug idiom, the state as text on the panel the draft already owns — so the user gets
   the feedback and the next pass gets its split from one change.
2. **Cancellation inside the loops.** `ensureActive()` appears twice in the whole tracer, so a cancel waits for a
   phase — and the 92 665-pair loop has no check at all. One check in the harvest's shape loop, one in the pair
   scan, one in the station expansion. Closes when a new aim aborts the running search in tens of milliseconds and
   the drag stops paying a whole phase per movement.
3. **Coalesce before cancelling.** A search in flight holds the newest aim, and exactly one more starts when it
   lands — so a drag costs a handful of searches instead of one per movement step. Closes on a printed
   searches-per-drag count and on the drawn line's lag.
4. **The vertex filter the design already asked for.** Keep the extreme vertices per obstacle as seen from the
   corridor plus every vertex of a non-convex obstacle, as §10.3 step 2 says, instead of every corner of every
   dilated wall and ring. Closes when the count falls while the pair's six metrics and both refusals stay put.
5. **The corridor is the multiplier.** The guess is 1 NM plus the berth around A→B, the clamp allows a further 1 NM
   of obstacle extent, and the growth pass can double both — every metre buys coastline and contour vertices, and
   every vertex enters the quadratic loop. It is a lever taken **after** item 4, on the same readings, because the
   corridor was once too small and the refusals are the guard.
6. **The search's own milliseconds, and NOT a tightened heuristic — corrected 2026-09-21 after being measured.**
   This item first read "a limit-aware heuristic, so A\* stops walking the graph": divide the straight-line
   distance by the slowest limit the straight line lies in. **That bound is not admissible** — a path may leave
   the band and come back, so the straight line's own limit does not bound the remaining time — and an
   inadmissible bound changes the answer, which §7a's route-search rule protects explicitly ("the heuristic is
   admissible by construction … stated here so it is not 'optimised' away"). What the measurement of the tolerance
   lever did say is where the search's time actually is: with vertices nearly unchanged (431 → 366) the search
   still cost 814 ms, so its cost is **per station, not per vertex** — 2 966 stations in 814 ms is ~275 µs a
   station, and what a station does is fit an easing through the ladder and price it through the drawn clock.
   So this item becomes: **cheapen the per-corner work** (the fit ladder, the clock evaluation per candidate
   turn, and the pre-fit reject §16 step 8 added), which closes on stations expanded *and* milliseconds per
   station — and any weighting of the heuristic is a deliberate trade of optimality for speed, which is the
   user's to make and not this pass's to assume.
7. **The engine's statefulness is the last question, not the first.** Reusing the corridor, walls and graph across
   aims is the largest single win and makes the engine stateful between searches, which its "one search started at a
   time" discipline exists to avoid. It is decided with the device split in hand.

**The tolerance: tried, measured, and it is not the lever — 2026-09-21.** The wall tolerance is a fraction of the
berth and it is the natural candidate for a vertex cut, the abstraction being one-sided outward so the error is
always the safe way. It was put to the test the user asked for — a value that would bring the search to half a
second — and the answer is **no value does**:

| tolerance | vertices | corners | pairs | search | deleted water | the line |
|---|---|---|---|---|---|---|
| 12.5 m (0.5, shipped) | 431 | 381 | 92 665 | 1 181 ms | 1.47 km² | 54 v · 307° · 27.40 min |
| 25 m (1.0) | 391 | 341 | 76 245 | 967 ms | 1.52 km² | 51 v · 327° · 28.35 min |
| 50 m (2.0) | 366 | 316 | 66 795 | 814 ms | 1.71 km² | 51 v · 326° · 28.35 min |

**Two things follow, and both contradict what this file assumed.** First, the response **flattens**: doubling the
tolerance bought 10 % of the vertices and 18 % of the search, doubling again 7 % and 16 %, while the water deleted
rose 1.47 → 1.52 → 1.71 km² and the drawn line gained a sharp corner and twenty degrees of turning — so a search
near half a second would need a tolerance of well over a hundred metres, which is a different coastline rather
than a different number. Second, and this is why the experiment was stopped: **the tolerance spends the berth's
own guarantee.** A harvested corner stands `berth − tolerance` off the undilated obstacle it came from, which
`TautAssertionsTest` asserts — so a tolerance of one berth makes that assertion vacuous, and setting it to 1.0 in
the experiment turned the suite red. **`TOLERANCE_FRACTION` is therefore a ceiling as much as a value**, and it is
back at 0.5.

**What the same run does say is where the search's time is.** Vertices fell 15 % while the search fell 31 %, and
the stations fell 3 871 → 2 966 in 814 ms — about **275 µs a station**. A station expands candidate corners, and
each candidate fits an easing through a ladder and prices it through the drawn clock; that work, not the vertex
count, is what the milliseconds are made of. Which re-orders the plan: item 6 below is now *cheapen the per-corner
work*, and the vertex levers (items 4 and 5) are worth taking for the **pair loop** and the memory rather than for
the search.

**What none of this touches:** the route's answer on the water. Items 1 to 3 and 6 change latency, cancellation and
the drawn figure's honesty; item 4 changes which bends are *offered*, so it is the one that must be measured against
the six metrics rather than assumed; item 5 moves the corridor, which the two refused pairs police; and the tolerance
changes the drawn line's *shape* — straighter and further offshore — which is the one thing about it a user would
notice, judged by running the same pair at 12.5 m and 30 m side by side.

### 19.2 The levers, priced — planned 2026-09-21 so the options stand side by side

**The method, before the levers.** Evaluate by **pricing the work, not the count.** The harness already prints the
phases, the stations, the geometry and the six metrics; what it does not print is the inside of a search, where the
seconds are. So the first item below is **counters, not a lever**: candidate turns built per station, fits attempted
and refused per cause (the causes exist), clock evaluations, and the milliseconds around the fit against those
around the clock — cheap ints, no per-call timers, since a timer changes what it measures. Everything after it is
read the same way: **one lever at a time, on the same pair, twice**, because the harness's own numbers move by about
ten per cent on identical code; and each lever is ranked by *milliseconds saved divided by what it changes* — metres
of geometry moved, corners lost, or the shift in the drawn line's turning and ETA — never by milliseconds alone.

| # | lever | what it touches | what it buys | what it costs | who decides |
|---|---|---|---|---|---|
| 1 | **the counters** | nothing that runs — measurement only | the split: less work, or cheaper work | nothing, once the baseline line is shown not to have moved | the agent |
| 2 | **cheaper per-corner work** — the fit ladder's widths, the pre-fit reject, the clock price per candidate | the search's inner loop | the largest measured share, 275 µs a station today | nothing visible **if** the same line is reproduced — that is the acceptance | the agent, on the line staying put |
| 3 | **prune candidate turns by angle** — try only near-forward turns | the state space | fewer candidate corners worked per station | the line may change: a turn no longer offered | measured; the user's if the line moves |
| 4 | **the §10.3 filter** — obstacle extremes plus non-convex corners, not every sampled vertex | which wall corners become bends | the corner count, at no cost in metres | a bend that mattered may vanish | measured on the six metrics |
| 5 | **smooth the source** — the 2 m contour arrives as a 25 m staircase | the wall itself | very likely the bulk of the count | the wall moves, so the clearance invariant (`berth − tolerance`) must still hold | the agent, inside that invariant |
| 6 | **a narrower corridor** | which walls exist at all | wall pieces and vertices, quadratically in the pair loop | the containment guarantee — the refused pairs are its guard | the user's, on the guard's reading |
| 7 | **coalesce the drag** | the preview pipeline, not the search | searches per drag, which is what the user actually feels | nothing the route shows; the aim's answer arrives at the last aim | the agent |
| 8 | **cancellation inside the loops** | the abort's latency | tens of milliseconds instead of a phase | nothing | the agent |
| 9 | **a weighted heuristic** | the bound A\* prunes with | stations expanded, possibly several-fold | optimality, bounded by the weight — the line may lengthen | the user's |
| 10 | **the device reading** | nothing | the true split, which a JVM split is not | nothing | the user's to take |

**Item 1, measured — and the split decides the rest.** On the acceptance pair the search's **792 ms** is
**6 274 fits at 593 ms** (75 %), **6 274 drawn-clock evaluations at 145 ms** (18 %), and the remaining ~54 ms the
expansion itself. So **93 % of a search is the per-corner work at about 95 µs a fit**, and there are only **1.6
fits per station** — the candidate pruning is already lean, which is why the vertex levers moved the total so
little. That answers the question the tolerance could not, and it points one way: **item 2 is the lever that can
reach a fast search**, and items 3 to 6 are worth taking for the pair loop and the memory rather than for the
search. The same reading is a caution about measurement itself: this configuration read 1 181 ms before and
**792 ms** now, which is a spread far wider than the ten per cent assumed above — so a lever's number is a pair of
runs, never a single one.

**And the instrument one level deeper says which half of a fit to attack.** Of a fit's **681 ms**, the curve's own
drawing came to **19 ms** and the **water test to 652 ms**, over **9 798 candidates built** — about 66 µs a test.
So the lever is the **judge, not the curve**: `curveOnWater` asks a point query for every emitted point and walks
the wall index for every chord, and the same points and chords recur across the thousands of candidates one run
builds. **Item 2 is therefore a cheaper judge** — memoising those two queries for the run is the first candidate,
it cannot change an answer if the key is exact, and it is the only optimisation so far with a measured target
attached: 652 ms of water tests standing between this search and half a second.

**Item 2, measured — and the measurement refused the shape the lever was planned in.** The judge's two questions are
memoised per harvest now, exact-keyed and counted, and the counters give the verdict rather than the milliseconds:
the run asks **161 051 questions and the memo answers 14 418**, so **91 % of them are about places no earlier
candidate asked about**, and the water figure moved 652 → **601 ms** — 8 %, which is inside the run-to-run spread
this section warns about. The wager on repetition **loses**, and that is the answer the counters exist to give: the
water's milliseconds are **new work per candidate and not repeated work**, so a cache is not where they live. The
planned target is therefore **re-stated rather than met**: 601 ms is the floor a *cheaper question* — the point
query's own cost, or the number of points the ladder asks about — would have to beat, and "652 ms of water tests
standing between this search and half a second" is withdrawn as a cache gain. What the same run also establishes is
that the memo is **free and answer-preserving**: the acceptance pair's line is the recorded one to the digit (54
vertices · 307° · worst 30° · 5.44 km · 27.40 min, crossings 30 · in-margin 0.17 km · berth 135.90 s), and its split
reads harvest 292 · graph 220 · **search 864 ms** over 431 vertices and 92 665 pairs — the 792 ms item 1 read being
the other end of the same spread.

**The two invisible levers, built with it.** 7 (the coalescing) and 8 (the cancellation) landed with item 2, both
changing nothing the route shows: the preview writes its newest aim into one slot that a single drain loop serves, so
a drag starts one search per *completed* search rather than one per frame, and `cancelCheck` is asked inside the
harvest's shape loop, the pair scan and the corner door so an abandoned gesture stops inside a phase. Item 3's
device half is built too — one log line per answered route on the refusals' own channel, and `searching` as the
panel's third state in both locales — which leaves **the reading itself the user's**, a phone's split not being this
desktop's.

**The order the options are worth taking, and why.** 1 is done, and it decided the question. 2, 7 and 8 done, with 2
answering *no* to the shape it was planned in and 7 the one the complaint was actually about. 10 then, because a
phone is not a desktop and the numbers above are the desktop's. Only after that do the geometry levers
(3 to 6) and the optimality lever (9) compete, and each of those has a cost the user can see or the route can lose.

**What this section does not claim:** that any lever alone reaches half a second. The tolerance, tried and measured,
bought 367 ms for a berth's worth of clearance, and it is the only one whose curve is known. The rest are priced as
*questions with a way to answer them*, which is the point of putting them side by side.

### 19.3 The plan reviewed and refined — 2026-09-21, once items 1–5 were in

**What the five built items changed about §19.2's own picture.** Three of that table's prices were written before the
instrument existed, and the readings correct them. (1) **The ladder is not the target.** The fit's 634 ms is **601 ms of
water judging against 21 ms of drawing the curve**, over **9 798 candidates** — about 1.6 a fit — so a shorter or
narrower ladder has almost nothing to shorten; what costs is the *question*, asked 161 051 times for 14 418 memo
answers. Row 2 named "the fit ladder's widths, the pre-fit reject" as the lever, and the measurement keeps only its
third clause, **the price of the question**; the clock, at 167 ms, is the search's other fifth and is item 1's own
reading. (2) **Row 4 drops out, and the walk's item 6 closes as built.** `protrudesIntoWater` **is** the non-convex
test, both harvests apply it, and step 2's next clause — "plus every vertex of a non-convex obstacle" — would keep
*more* than ships, so the extreme-vertex clause has nothing left to remove on this water. (3) **Rows 7 and 8 are done**,
so a drag now costs the work of **one** search — 864 ms of search, 292 of harvest, 220 of graph — rather than the
stacking of many.

**The order, re-priced on that.** A lever is worth taking where it moves the quantity the counters name, and every row
carries the guard that says it did not quietly change the line.

| step | what it touches | what it buys — a reading, or its own measurement | guard | whose |
|---|---|---|---|---|
| a · **the device reading** | nothing | the true split: 1 416 ms of wall clock = harvest 292 · graph 220 · search 864 on a desktop | none | the user's |
| b · **the box kept across searches** (item 8's technical half) | the harvest and the graph — **512 ms of the 1 416** | a drag moves the corridor by metres, not by cells: one superset box serves the next aims until a corridor leaves it | the two inshore pairs, the grown-corridor probe, and an assertion that a reused graph answers the **same** line | the agent's, inside an item the device split still owns |
| c · **a cheaper water question** | the 601 ms inside the fit — the point query and the per-chord wall walk | whatever the question's own cost falls by, on the **91 %** of questions that are new work | the six metrics, and the line unchanged | the agent's, on the metrics |
| d · **the corridor made narrower** (item 7) | the harvest **and** the graph — 512 ms — plus the pairs | pairs, walls and memory; **the search does not move** | **none as written**: the two refused pairs are refused by `START_OFF_WATER` *before* the corridor is built (§17 item 5), so a real guard — a pair whose only way round lies beyond the margin — has to be built first; and row **b reaches the same 512 ms without touching the containment guarantee** | the user's |
| e · **candidate turns pruned by angle** (§19.2 row 3) | the state space, at 1.6 candidates a fit | little: the ladder is already lean | the line may change | the user's if it moves |
| f · **the invented vertex prune** | the pair loop and the memory — **not** the search | priced below | the six metrics | the user's |
| g · **the weighted heuristic** (§19.2 row 9) | the stations expanded | several-fold of the ~63 ms the expansion costs | optimality, so the line may lengthen | the user's |

**What that order says in one paragraph.** The drag's latency is now **a rebuild and a judge**, and nothing else: the
clock, the curve's own drawing and the expansion are 250 ms between them, the ladder has nothing worth trimming, and
every geometry lever left either changes the line or buys memory. **b is the larger of the two** — half a second
against the search's own nine tenths — and it is the one whose decision §19.2 deferred to the device split, so it
stays open until that reading arrives, with the desktop's numbers as its prior.

**The vertex prune, as an option rather than a step — the gate §19.2's row 4 opened, re-asked and recorded here.** The
rule would be: keep a wall corner only if it is **extreme in the corridor's angular window**, tested per wall piece as
a support vertex for the two directions from that piece to the two ends — a cone, so the test keeps its two extremes
and may drop a vertex extreme only in an interior direction. Its acceptance is fixed before the writing: the same six
metrics, the two inshore pairs still refused **by name**, the grown-corridor probe still reading *only a priced
crossing*, and the vertex and pair counts printed falling. Its honest size is the pair loop and the memory — **the
search's 864 ms is 93 % per-corner work and does not move** — so it is worth taking for the map and the allocations,
and it is the user's because it can delete a bend. **The strictly safe variant** — drop a corner whose water side lies
outside the corridor box, the box being convex, so no leg from that corner can ever reach water — costs nothing in
metres and cannot change the line, and buys so little that it is recorded only to say it was considered.

**The measurement protocol, tightened by the instrument.** A lever that moves neither `candidatesBuilt` nor
`candidateWaterMillis` cannot move the search, so it is judged on that pair **before** the wall clock is read; and the
same code read 792 ms and 864 ms on two runs, so a wall-clock claim is a pair of runs or it is not made.

### 19.4 The terrain kept for a box — planned 2026-09-21 for the run on walk item 8

**What is kept, and what belongs to the box rather than to the aim.** A search's inputs divide cleanly. The **terrain**
of a corridor — `TautObstacles` (its walls, its corners, its index), the `TautZoneSet` and the `TautBand` — is a
function of the **box** and of the world and of nothing else, and it is `TautGraph.build`'s own top that harvests all
three (`harvestZones`, `TautBand.of`) before walking vertices that include the start and the aim. A drag moves the aim
and the corridor with it, but the next corridor usually lies **inside** the one already harvested, so the reuse rule is
one containment test — `new box ⊆ kept box` — and nothing more. What is never kept is the aim's own vertex, the pairs
that touch it, the search or the line: those *are* the answer, and they are recomputed on every move.

**Two cuts, measured one at a time.**
- **b1 — the terrain.** One entry, `TautTerrain.of(world, box, berthM, shoreOffsetM)` returning the obstacles, the zones
  and the band, the band built off **the obstacles' own frame** so the corridor keeps one projection as today, with
  `TautGraph.build` taking that terrain instead of harvesting it. It removes the harvest's 292 ms and the band's own
  coastline read from every search after the first, and it changes no input of the answer.
- **b2 — the corner-to-corner pairs.** The pair loop is 220 ms over 92 665 pairs, and only **two plus one per corner**
  of them involve a vertex the aim owns. Keeping the corner pairs' visibility and price for the box and computing only
  the ends' own pairs is exact for the same reason, but the pair rows are keyed by vertex index over a list whose two
  end vertices shift, so it is a **second step with its own reading** rather than part of b1.

**The two rules b1 cannot be written without.**
1. **Invalidation by generation.** A kept terrain is stale the moment its own inputs move, and those inputs are the
   world's layers *and* the numbers that shaped it — the berth, the shore offset, the abstraction tolerance, the last
   read from `AppConfig` and settable in Settings. `TautWorld` therefore gains one member, `generation: Int`,
   documented as *bumped whenever anything a route reads from this world changes*, `RouteSpatialAdapter` bumping it as
   each layer's load completes, and the entry is keyed on it beside the box and the numbers. Without a named
   invalidator the cache would answer about a coastline that no longer exists.
2. **Read-only to whoever is running.** A search is **started** one at a time and its predecessor is cancelled and
   *not joined* — safe today because a search owns nothing shared, which is exactly why the judge's memo built in §19.2
   is per harvest. A cached `TautObstacles` **is** shared, so its two memo maps must become concurrent-safe (a
   `ConcurrentHashMap`) before it is reused, and the racy increments of its counters under an overlap must be accepted
   and said so in place. A cache over state a cancelled search is still reading is the one way this change can corrupt
   an answer.

**The acceptance.** The line must not move: the acceptance pair's six metrics (54 vertices · 307° · worst 30° · 5.44 km
· 27.40 min), the two inshore pairs refused **by name**, the grown-corridor probe still *only a priced crossing*. The
change is invisible, so the reading that proves it *happened* is a **pair** — a dossier flag (`terrainReused`) beside
the harvest's milliseconds on a second search of one engine, asserted with the line identical to a cold engine's — and
a revert must turn that pair red.

**Files this step touches.** A new `TautTerrain` beside `TautObstacles`; `TautGraph.build`'s signature and its own
harvest calls; `TautObstacles`' memos for the sharing rule; `TautRouteEngine` for the entry and the reuse; `TautWorld`
and `RouteSpatialAdapter` for `generation`, with the three test worlds following; `TautRouteDetails` for
`terrainReused`; and the harness for the second-search reading.

**And the phone, which is what decides whether it stays.** b1's gain is the coast work *after* a move, so what settles
it is a drag on the device read against the log line built in item 3, whose figures stand beside this desktop's
292 · 220 · 864 ms. The clear instructions and the go are handed at the end of the run — the build carrying the line
ready, the deployment and the test the user's, and nothing asked of them before it.

### 19.5 As reviewed, 2026-09-21 — verdict **revise**, two blockers, and the corrections in the order they close

**What the review confirmed, so the next pass does not re-argue it.** The line did not move on the acceptance pair
(54 · 307° · worst 30° · 5.44 km · 27.40 min, crossings 30 · in-margin 0.17 km · berth 135.90 s); the two inshore pairs
are refused **by name** and not by a corridor accident; the grown-corridor probe still reads *only a priced crossing*;
the dropped `growth` parameter was unread; the `@Volatile` entry publishes whole terrains, its fields being `val`s; and
the terrain's own shape — three fields, one key, one containment predicate — is the change's best part.

**B1 · the box the graph is built over, and the claim behind it.** On a reuse the engine hands the graph `kept.box`, so
a **moved** aim searches a *larger* graph than a cold search's, and A\* over a superset can return a different, never
dearer line — one that may bend outside the new corridor. The equality reading was taken on the **same** aim twice, the
one case where the two boxes coincide by construction. Worse, and unmeasured: the licence tests `rough` while a cold
search builds from `sizedFromObstacles(rough)`, whose clamp reaches past the kept box, so a reuse can refuse, or answer
over a *narrower* corridor, where cold finds a way. **§19.4's "changes no input of the answer" is wrong as written**,
and C2 and C3 below are what close it.

**B2 · the scratch beside the memos was not hardened.** `WallIndex.crossedBy` and `TautBand.spansOf` carry mutable
scratch — `stamp++` over a shared `seen` array, and two fields of their own — and b1 is exactly what made those
instances **shared** between a search and its cancelled, unjoined predecessor. A non-atomic `stamp++` lets two queries
carry one stamp, so a pass reads the other's marks as its own and can skip a segment, accepting a leg that crosses
land. That is the corruption §19.4's rule 2 names, left standing in the very objects the rule was applied to.

**The corrections, in the order they close.**
- **C1 — the shared scratch (B2).** The stamps become unique per query (`AtomicInteger.incrementAndGet()`), a unique
  stamp making any stale `seen` read harmless because two passes can no longer share one; the `TautObstacles`
  paragraph claiming that no attempt reads another's answers is corrected, a reused terrain being shared by
  construction; and a test interleaves `crossesWall` and `spansOf` from two threads on one instance for a
  known-crossing pair, asserting every answer equals the single-threaded one. **Closing reading:** that test, red
  before C1 and green after.
- **C2 — the licence corrected to the sound superset (B1's first half).** Reuse is licensed only where the kept box
  **holds the largest box a cold search could build from this rough corridor** — `rough` inflated by the obstacle
  reach, the berth and the epsilon, which the sizing's own clamp proves sufficient. A reused corridor is then never
  narrower than cold's and its line never dearer, so both failure modes above become impossible **by construction**
  rather than by argument. **Closing reading:** the licence's own unit test, and a moved-aim run whose warm answer is
  never dearer than the cold one's.
- **C3 — the reading that decides the rest (B1's second half).** N aims along one drag, **warm engine against cold
  engine**, printing on every aim `points`, `legTimesSec`, `distanceM`, `durationSec`, the six metrics and the reuse
  flag. Its verdict picks the way out: if the line is equal on every aim, the divergence is nil in practice and the
  cache stands as it is; if it differs on any aim, the difference is **the user's to accept or to refuse** — accepting
  it re-scopes §19.4 and §19.2's row-b guard from *the same line* to *never dearer*, while refusing it means building
  the **slice** (the kept terrain filtered to the new box in memory — walls, corners, zone rings, the band's segments
  and the wall index re-taken, with the dossier's harvest readings named as the terrain's own). The slice is **not**
  taken before this reading, because its exactness would itself have to be proved by this same test.
- **C4 — the generation hole (should-fix).** A depth refresh installs a new grid **without** bumping the generation —
  the contour self-heals by identity, a kept terrain does not — so the two invalidators disagree. Either the grid's
  replacement bumps, with a test asserting it, or the contract states the invariant that makes the bumper complete by
  construction — *each layer is built once per adapter lifetime* — and the contour argument is dropped with it.
- **C5 — the second entrance (should-fix).** `over`'s KDoc gains its obligation — the obstacles must have been
  harvested for `box` — and `of` either gains a production caller or a line saying it is the cold entrance the tests
  use, since both `TautGraph` and `TautRouteDetails` point readers at it.
- **C6 — the three code-health items.** One factory builds the key, which is built in two places today, duplicating
  five fields and the tolerance product; `terrainReused` becomes true only when the terrain actually **answered**,
  rather than on any attempt before a growth re-harvest; and `of`'s `cancelCheck` promise is honoured by the zone and
  band harvests inside `over`, or narrowed to what it does.

**What is not decided here:** whether the cache may keep a line that differs from a cold search's. C3 measures the
divergence, and the choice between accepting it and building the slice is the user's, because it is the line they see.

### 19.6 The device reading, and what it re-prices — 2026-09-21, from the log line on the phone

**What was read, and its provenance.** Item 3's one line per answered route (tag `MaroTautRoute`) was read twice on a
Pixel 7, five answers in all, and **the build is a debug one**: `apk-build.bat` runs `gradlew assembleDebug`, and the
module declares no release build type and no signing config, so every figure below is `app-debug.apk`'s and none of them
may be read as the ship build's. A release reading is a build change of its own — a build type and a keystore — and not a
step of this plan.

**Step a closes as measured, and the phone is not the desktop's shape.** The acceptance pair's own corridor on the phone
reads **425 vertices · 3 493 edges · 90 100 pairs → harvest 2 671 · graph 4 816 · search 56 079 ms**, against the
desktop's 431 · **3 645** · 92 665 → 280 · 138 · 1 003, the 3 682 first written here being stale — the same world
re-reading 3 645 on the rebuilt loop; the largest drag reads 857 · 18 637 · 366 796 → 6 782 · 19 167 ·
57 951. So the ratio is not one number: the harvest ~10×, the graph ~35×, the search ~56×. The search's inside moves with
it — `fits 10 341 (34 257 ms)`, `clock 10 341 (18 770 ms)`, and of the 14 415 candidates built, **water 32 625 ms against
1 183 ms of drawing**, the desktop's 95 %-per-corner shape at ~54× the milliseconds. The memo is refused again on the
phone: **35 %** of 217 111 questions answered on the acceptance-size pair and **8 %** of 484 357 on the largest drag,
against the desktop's 9 %.

**Row by row, what the reading re-prices.** §19.3's **b** claims the harvest *and* the graph for its 512 ms, and the phone
splits the pair: the terrain cache does for the harvest exactly what it claims — a warm answer reads `harvest 0 ms` where
the same corridor harvested at 561 ms — and **touches the graph not at all** (`graph 6 403 ms` warm against `5 495 ms`
cold on a smaller box). So b's second half is where the phone's remaining phase lives, and the plan's item 3 takes it.
Row **c** is unchanged as a lever and tripled as a magnitude: 601 ms of water judging on the desktop, **32 625 ms** on the
phone, with a hit rate lower there than on the desktop. Rows **d–g** keep their prices and their guards, the search being
the same shape at a bigger constant.

**The graph's dependencies, read rather than assumed — and the decision taken.** `TautGraph.build(world, terrain, start,
aim, cruiseSpeedKn)` lists its vertices as the two ends, then the traversable obstacle corners and the zone corners, and
runs the **quadratic pair loop over every vertex**; its per-pair work (`allowed`, `zones.limitSpans`,
`legLimits.priceOf`, `zones.berthFraction`) reads **only the pair**, while `cruiseSpeedKn` enters once, in `LegLimits`.
The corner-to-corner edge set is therefore a function of the terrain, the box, the zones and the cruise speed — **not of
the ends** — and a kept graph is reachable: keep the corner set and its edges, add the two end vertices and scan **their
rows only**, which is O(N) where the build is O(N²), under a licence of terrain, box, zones and cruise speed unchanged in
the same generation sense §19.4 gave the terrain. **Decided, the build owed:** the graph is kept beside the terrain with
the ends' rows rebuilt per search, the same-line assertion as its guard, and a rebuild exactly as today wherever the
licence fails.

**Two readings this log gave that are not timings.** Three of the five answers came out degenerate — `search 0 ms ·
fits 0 · candidates 0` — which is what `TautSearch.run()` returns the moment the settled vertex is the aim's own vertex,
so they are answers for an aim that never left the start's corner and are set aside from any timing comparison. And the
judge's tallies on a warm line belong to the **kept terrain** rather than to the search (`526 775 asked, 81 675 from the
memo` beside a 0 ms search), which is why a degenerate answer can print a large one.

**What stays the user's.** The phone's own target — how fast a route must answer on the phone, and whether the phone is
the machine that target belongs to — which is the plan's first item and the only gate on the rest; and the metres between
the warm line and the cold one, which this channel cannot carry, its line holding no length, C3's harness being where
that reading lives.