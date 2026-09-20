<!-- scope: feature -->
# Route — algorithm design discussion (feasibility, high level)

Opened 2026-09-20 on `feature/other-routing`. **Design in discussion — nothing built, no file outside
this one touched.** The starting point is the epic in
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
pure-A route does the same thing for a slightly different reason. Implementing A and adding a
multiplicative aversion lets one number (`k_zone`, or equivalently per-zone) slide between them, and the
slider can later become a user-visible *route style*: Fastest · Zone-averse · Straightest.

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
     fall back to a minimum distance from the coastline.
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
- **Turn cost.** `cost += k_turn · |Δheading|` with the state carrying the entry direction. Without it, a
  grid path is a staircase; with it, the path prefers few, wide course changes — which is also what a boat
  actually wants. State space multiplies by 8, node count does not.
- **Straightening.** String-pulling (funnel) over the node sequence, each candidate segment validated
  against the *hard* constraints only — water and depth ≥ min — followed by one smoothing pass
  (Chaikin, as the isobaths already use) with a re-validation that discards a smoothing step that would
  cross a hard constraint. A smoothed route that is not re-validated is a defect.
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

1. **Objective priority** — time-primary with a zone-aversion multiplier (recommended), distance-primary,
   or two drawn alternatives to choose between.
2. **Depth gate semantics** — is 2 m a hard "never route here", or a warning-grade preference? And does
   the router reuse the existing pair (`lowDepthCrashDepthM` 0.5 m / `lowDepthStartWarningM` 1.5 m) or
   carry its own 2 m / 3 m pair?
3. **Source-aware margin** — does the router accept a shallower gate where the data is coarse (EMODnet),
   or refuse to route there at all?
4. **The 300 m band** — treated as an implied 5 kn zone (the French default the band stands for), or only
   as geometry the route prefers to leave?
5. **Cruise speed source** — a Route-owned setting (the epic says 3–40 kn, default 20), or the boat's
   recent observed speed?
6. **Baseline graph** — the prebaked mesh stays the design of record, or the corridor visibility graph of
   §10 is explored first as recommended? The mesh implies JTS + poly2tri as prebake-only test dependencies,
   which is the user's call under §4.

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

### 10.3 Build order

1. **Corridor** — the A→B segment inflated by a margin that provably contains the answer (1 NM plus the
   largest obstacle's extent is the starting guess), clipped to the depth grid's bounding box.
2. **Harvest vertices** inside the corridor from the existing indices: coastline points, dilated 2 m contour
   points, zone outer rings and hole rings. Simplify per obstacle (Douglas–Peucker already exists in
   `DepthIsobaths`) and keep the points that can matter: the extreme vertices of each obstacle as seen from
   the corridor, plus every vertex of a non-convex obstacle.
3. **Edges** — a pair is visible when no exclusion segment blocks it. Land already has the test:
   [`segmentIntersectsLand(a, b)`](../../app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt:504);
   the 2 m contour uses the same test against its own segments; zone rings use
   [`segmentsIntersect()`](../../app/src/main/java/ykws/android/maro/spatial/SpatialOperations.kt:139).
4. **Edge cost** — length, integrated through the price regions it crosses (band, 5/10 kn, 300 m), in the
   epic's `crossedRegions` shape, plus a small turn penalty so a straight course beats a zig-zag of nearly
   equal length.
5. **A\*** with a straight-line lower bound. The result's bends *are* the tangent points.
6. **Smooth** (Chaikin, as the isobaths do) with every smoothed step re-validated on the exclusion set;
   a step that breaks one is discarded, and the tangent vertices stay as waypoints.

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

| Key | Meaning | Proposed default |
|---|---|---|
| `route.standoffM` | minimum clearance kept from the exclusion geometry | 25.0 |
| `route.cruiseSpeedKn` | speed assumed outside any zone | 25.0 |
| `route.turn.lateralAccelMps2` | lateral acceleration ceiling (m/s²), from which the minimum turn radius is derived — **the shipped key, and the only home of this value; the g-figure of 11.3 is this same quantity stated in another unit, never a second key** | `maro.properties`, read through `AppConfig.routeTurnLateralAccelMps2` — not restated here |

`ONE HOME PER FACT` applies: `maro.properties` is the single home of the three values, code follows it, and
no doc restates the numbers. Note the coincidence that 25 m is exactly one `GRID_RES_M` cell, so in raster
terms the standoff is a one-cell erosion — which is also its weakness in 11.2.

Of the three, the lateral ceiling is the one a user may want to move, so it also wears a settings row —
Gentle 0.15 g · Normal 0.20 g · Sport 0.30 g — writing this same key; the other two stay properties-only
until asked for otherwise.

### 11.2 The standoff means two different operations, and they are not interchangeable

- On **exclusion geometry** (land, the < 2 m contour) it is a **dilation of the obstacle**: the contour is
  offset outwards by `route.standoffM` before the graph is built, so the hard constraint is geometric and
  cannot be traded away.
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
- **Settled 2026-09-20**: the ceiling is the default recorded in 11.1, taken from the gentle half of the
  0.15–0.3 g band because a softer cap both reads better aboard and holds the track further off whatever it
  is rounding at cruise. At that value the derived radii are **84 m at 25 kn**, 54 m at 20 kn, 30 m at
  15 kn, 13.5 m at 10 kn and 3.4 m at 5 kn.

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

1. The standoff's subject: land and the 2 m contour, zone boundaries, or both? And hard or priced where it
   applies? *(asked)*
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
  the sharp point of a cape at 25 m gives `r ≈ 25 m`, so `v = sqrt(1.96 × 25) ≈ 7.0 m/s ≈ 13.6 kn`.
- So the two settled requirements interlock: **`route.standoffM` sets the floor on a forced corner's radius,
  and therefore on its speed — about 13.6 kn rather than a crawl.**
- A corner can also be widened rather than slowed — a larger radius costs distance and buys speed — and the
  router compares the two in time. Slowing is permitted, not preferred.
- The floor moves with any relaxation D3 allows: at a 10 m standoff a cape corner is `r ≈ 10 m` → `≈ 8.6 kn`.
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
- **`RouteResult` changes shape**: the route is no longer a polyline but a **speed profile** — a polyline with
  a speed per segment and the corner arcs — because the ETA, the drawn track and anything later built on it
  all need those numbers. This is the single data-model consequence of the requirement.

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

Stages 1 and 2 stay unbuilt until Stage 0 has numbers: its result is what decides D6, and the epic's
prebaked mesh — with its JTS + poly2tri `testImplementation` pair — stays unadopted until then.

### 13.2 Files

Runtime, `app/src/main/java/ykws/android/maro/route/` (Stage 1; the shape the epic proposed, minus the
bake it no longer needs):

| File | Role |
|---|---|
| `RouteConfig.kt` | the three values read from `maro.properties`; region bounds from `BuildConfig` |
| `model/RoutePlan.kt` | the speed profile: polyline, per-segment speed, corner arcs, distance and ETA |
| `obstacle/RouteContours.kt` | the 2 m and 3 m levels, and the standoff dilation |
| `obstacle/RouteObstacles.kt` | the exclusion set, harvested per corridor |
| `graph/RouteVisibility.kt` | the visibility test between two points over that set |
| `graph/RouteGraphBuilder.kt` | corridor → vertices → edges, with costs |
| `search/RouteSearch.kt` | A* over that graph |
| `search/RouteSpeedProfile.kt` | corner arcs and the speeds derived from them |
| `data/RouteSettings.kt` | Route-owned prefs: cruise speed and comfort level |
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
  region `BuildConfig.REGION_ID` names in the epic. Reading them directly is the epic's own stated split
  (`RouteGeometry` reads the bins in a JVM test); whether the depth serializer is Context-free is the first
  thing to check, and the depth grid may instead be rebuilt from the two `.asc` sources if it is not.
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
- **Measurements printed per scenario**: vertices, edges, nodes expanded, milliseconds, route length, route
  time, distance inside each zone, and the straight-line ratio.
- **Assertions**: no leg crosses land or the dilated 2 m contour; every vertex clears the standoff; the
  result matches a reference Dijkstra to 1e-6 relative; two runs are byte-identical (determinism); a
  zone-priced route spends less distance inside zones than the naive water-only path.
- **Scenarios**: (1) **Baie des Milliardaires → Port de La Salis** as the acceptance case, with its two
  in-band endpoints; (2) a synthetic corridor whose answer is known by hand; (3) a boxed-in no-route case.

### 13.4 What each open decision would change in the code

- **D3, the standoff's subject** — the only structural one: a hard dilation lives in `RouteObstacles`, a
  priced band lives in the cost function. The harness runs both and reports the difference.
- **D4, the 300 m band** — one more priced region; a flag in the harness, a row in the settings later.
- D1, D2, D5 — cost weights and settings values, no structural change.
- **D6, node set** — decided *by* Stage 0's numbers rather than before it.

### 13.5 Order of work

1. `RouteConfig` and the three keys in [`maro.properties`](../../app/src/main/assets/maro.properties:1).
2. `RouteContours` — the 2 m and 3 m levels and the standoff dilation, pure and tested.
3. `RouteObstacles` — the corridor harvest from the land index and the zone list.
4. `RouteVisibility` + `RouteGraphBuilder` — edges and costs, tested on the synthetic corridor.
5. `RouteSearch` — A*, deterministic tie-break, Dijkstra reference in the test.
6. `RouteSpeedProfile` — the arcs, the derived speeds and the ETA.
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
- The properties (`route.standoffM`, `route.cruiseSpeedKn`, `route.turn.lateralAccelMps2`) and the requirement list of §1 and §11 are engine-independent, so the requirement set needs no rewriting — only a different machine to satisfy it.
- The incumbent's four bypasses each cost a device cycle to find. A design whose geometry *is* the answer has fewer joins to get wrong, and that is its strongest argument — as long as the corridor and the latency hold.
