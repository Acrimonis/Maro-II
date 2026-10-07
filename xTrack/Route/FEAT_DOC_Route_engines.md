<!-- scope: feature -->
# Route — the acquisition engines: current state

**Scope:** the central reference for what the route acquisition engines are and do **today** — the seam, the
three shipped engines, the ladder, the pipeline, the plan seam, the shared `multipass` layer, the water they
price and the readings they publish. Facts only, present tense, **no history**: what a thing used to be is
not in this file, and a mechanism that no longer exists is named only as an absence. The **values** live in
[`maro.properties`](../../app/src/main/assets/maro.properties) behind
[`AppConfig`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt) — this file names the roles and
the keys, never the numbers.

## The seam

[`RouteEngine`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngine.kt) is the whole contract: an
engine declares **which routes it can compute between two points**, and every started lookup reports on one
flow.

| Member | Shape |
|---|---|
| `routesToCompute(origin, destination)` | Repairs the pair, then answers `RouteDeclarations.Available` (the computations, **first = main**) or `Refused(reason)`. Plain, not `suspend`. |
| `startLookup(computationId)` | Starts one declared computation by id, returns the lookup's id. No points travel: the whole set shares the repaired pair. |
| `cancelLookup(id)` | The only disposal an engine performs; no further update for that id may arrive. |
| `updates: Flow<RouteUpdate>` | One channel per engine, correlated by `RouteUpdate.routeId`. |

The types on the seam, all in the same file:

- `RouteId` — a value class over `Long`, minted by the owning engine.
- `RouteComputation(id, descriptionResId)` — one declared computation, its label a `@StringRes`.
- `RouteDeclarations` — `Available(computations)` or `Refused(reason)`, so a refusal is a value, never a throw.
- `RouteReason` — `CANNOT_REPAIR` and `WORLD_NOT_READY` answer at `routesToCompute`; `NO_PATH` and `OFF_WATER` answer on the flow.
- `RouteStage` — `CORRIDOR` · `GRID` · `SEARCH` · `PULL` · `SNAP`, the boundary set an engine publishes.
- `RouteUpdate(routeId, stageDone, nextStage, line, result, reason, readings, provisional, runningBest)` — the stage pair is **finished-then-next**, `nextStage = null` marks the terminal update, and `line` is a value to paint, never a drawing.
- `RouteProvisional(distanceM, durationSec)` — the pair a rung's first taut line already supports, replaced by the settled figures.
- `RouteRunningBest(lookupId, compared)` — the rung a running ranking currently names, and how many rungs it has compared; it rides **every terminal** of a ladder.
- `RouteStepReading(stage, labelResId, value, unitResId)` — one figure a stage reports about **its own** work.

**The registry** is [`RouteEngineChoice`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngineChoice.kt):
one row per algorithm (`id`, `labelResId`, a factory taking the pace, aversion, budget and world providers),
`all` in menu order, and `resolve(id)` answering the shipped default for an id nothing claims. A new algorithm
is one row there plus one class implementing `RouteEngine`.

## The three shipped engines

| id | Class | Its walk | What it reads |
|---|---|---|---|
| `dummy` | [`RouteDummyEngine`](../../app/src/main/java/ykws/android/maro/spatial/RouteDummyEngine.kt) | One straight segment, timed at a fixed 15 kn | **No layer at all** |
| `avoid` | [`RouteAvoidEngine`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt) with `UniformGridPlan` | One tile over the whole corridor, at `route.avoid.grid.cellM` | Coastline, depth gate, 300 m band, speed zones |
| `evolutive` | [`RouteEvolutiveEngine`](../../app/src/main/java/ykws/android/maro/spatial/RouteEvolutiveEngine.kt) with `EvolutiveGridPlan` | Two layers on one lattice family: the coarse interior and the fine coastal band | The same water as `avoid` |

- **`dummy`** declares one computation and emits one update (`stageDone` and `nextStage` null, the straight line as the result). Its repair is a no-op — it has no water test to run one with — and `cancelLookup` has nothing to cancel.
- **`avoid`** is the shipped default and the engine the adaptive one delegates to.
- **`evolutive`** is a composition, not a reimplementation: it holds a private `RouteAvoidEngine` built with `EvolutiveGridPlan` and forwards the seam's three calls, so the pipeline, the clock and the readings are shared and the algorithm differs **by its plan alone**.

## The ladder

One arming declares **three rungs**, each a full solve at its own fixed λ over **one shared grid**: `around`
(λ = 5), `best` (λ = 2.5) and `fast` (λ = 0) — the three stops of [`RoutePreference`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePreference.kt),
the ladder's one home for its λ values, its indices and its words. The first is the **main** and the only one
that narrates the stage line; every other rung publishes its terminal update alone, plus the provisional pair
it can already stand behind. There is **no budget loop**: a rung is computed at its own λ and never corrected.

**The rungs are ranked, and the winner rides the terminal.** At every rung's terminal the engine folds the
settled costs of the rungs that have landed through [`RoutePassRanking`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRanking.kt)
under the Driving preference: **Fast** leads on the trip's total time, **Best** on the total time provided the
zone share stays within `route.avoid.speedZone.timeBudgetPct`, and **Fun** on the absolute zone seconds — the
clock entering only as the last resort. One shared tail settles a lead tie, and a total tie falls to the rung
nearest the preference. The result travels as `RouteRunningBest` on the terminal update: a **running best**,
not a verdict, so a surface reads *so far, the winner is…* until the last rung lands.

## The pipeline

[`RouteGridBuilder`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt) builds the
grid once per arm and the rungs share it; [`RoutePassRunner`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt)
runs one pass; [`RouteFinePass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt)
runs the after-the-loop refinement. In order:

1. **Corridor** — the start-aim box inflated by `route.avoid.corridor.reachM`, clamped to the region's bounds; a truncated box is accepted.
2. **Harvest** — `segmentsIn` (ring and basin edges with their orientation) and `openCoastIn` (ordered mainland polylines).
3. **Rasterize** — one tagged, costed cell per tile: `LAND`/`FREE` plus the band's and the rings' **limits**, never prices, so the grid is λ-free and one rasterise serves all three rungs.
4. **Ends** — both end cells force-freed, their discs opened, and the berth carve run at each end's local cell.
5. **A\*** — `MultipassSearch`, eight neighbours, a metres-equivalent g-cost so the haversine heuristic stays admissible, a cancellation check between expansions, over one window or a chain of windows.
6. **Taut pull** — the two-pointer string pull at the walk's clearance step, with the price walk beside it.
7. **Corner snap** — each bend moves onto its nearest tangent corner while both legs stay clear, then the line is pulled again.
8. **The fine pass** — the refinement along the settled line: a **crossing re-solve** per priced zone the line enters (a local A\* inside the zone's own box, spliced only where it answers and only where it is no dearer), then a **re-tension** pull on the fine field. There is **no re-search and no second pass**: the fine stage is this refinement alone.
9. **Corner pass** — `RouteCornerPass.round` rounds each snapped corner into an outward-bulging racing-line curve, slowed where the bulge would foul.
10. **Clock** — `timeLineWithProfile` times the drawn line with anticipation and bounded acceleration, the enforced limit the hard ceiling.

Around those: a **forced-crossing probe** (a second A\* over a copy of the grid with the restrictive zones
blocked) decides whether the line had no way around, and a **corridor growth** retries once with the reach
doubled on no path or forced crossing, keeping the wider answer only where it forces fewer crossings.

## The plan seam

[`RouteGridPlan`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt) is the engine's
injection point — the one thing that separates one algorithm from another:

- `firstWalkGrid(corridor, baseCellM): List<GridTile>` — the rectangles the first walk may use, each at its own cell size.
- `fineCellM(baseCellM)` — the fine cell, in **metres**, the clock steps at.

Two plans ship: **`UniformGridPlan`** (one tile over the whole corridor, the fine cell its own metres key
`route.avoid.grid.fineCellM`) and **`EvolutiveGridPlan`** (two tiles — the interior at
`route.evolutive.grid.cellM` and the band at `route.evolutive.grid.fineCellM`, coarse first so a
layer-agnostic lookup resolves the interior). A plan decides **where and at what size** work happens: it never
prices, never times and never reads a switch.

## The shared `multipass` layer

Everything the engines stand on, under
[`spatial/multipass/`](../../app/src/main/java/ykws/android/maro/spatial/multipass):

- **The world** — [`MultipassWorld`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt): the coastline segments and open coast, the water test, the coast distance, the depth sample, the speed zones, the region bounds and the band width. The live implementation is `LiveMultipassWorld`.
- **The lattice** — `WalkLattice` / `LatticeFamily` / `WalkWindow` / `WalkWindows`: one origin and one cell size per layer, and a walk that is one window (the uniform pass) or several on one lattice (the two-layer walk), with the seam between two layers an index relation.
- **The search** — [`MultipassSearch`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassSearch.kt): `search(grid, …)` and `searchWalk(windows, …)`, one loop over whichever walk it is handed.
- **The pull** — [`MultipassPull`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt): the clearance walk, the price walk (grouped behind a declaration the source makes, with a span-level proof above it), the corner snap's entry and the refusals it counts.
- **The field** — `RouteCostField` and [`RoutePassPrimitives`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt): `costField(...)` builds the walls and the prices, `limitAtFor(world)` is the λ-free clock read, and `snapToCorners`, `zoneMetres`, `inZone`, `inBand` and `clampTo` sit beside them.
- **The seats** — `RouteGridBuilder` (the build), `RoutePassRunner` (one pass), `RouteFinePass` (the refinement) and `RoutePassRanking` (the preference-aware ranking, `RoutePreference` beside it as the ladder's one home), each composed once by the engine. `RoutePassModels` holds `GridWalk`, `GridContext` and `PassReading`.
- **The post-passes** — [`RouteCornerPass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCornerPass.kt) and the clock in [`RouteEta`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt).
- **The geometry** — `TangentCorners` (the tangent corner sets) and `ZoneGeometry` (rings, collars, the price law).
- **The ends** — [`BerthCarve`](../../app/src/main/java/ykws/android/maro/spatial/multipass/BerthCarve.kt): the end discs, the carve reach and the shore margin.

## The water the grid carries

One [`RouteCostField`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt) per solve,
a list of sources that may only **add** to an explicit base:

- **HARD walls** — the coastline, materialized by the rasterizer's geometry sweep, and the depth gate (a bilinear read per cell centre against `route.avoid.depthGate.minM`, ANDed with the coastline's own water, when the gate is enabled and the depth layer is ready). A wall is a cell the route may never cross.
- **SOFT prices** — the 300 m band and the regulated speed zones, priced by **one law**: a cell's time excess over the pace at the limit in force, scaled by the pass's λ. The band's limit and a ring's are the same quantity through the same function, and two sources over one cell charge it once.
- **The limit is written on the grid, the price is not.** The rasterizer stores each slow cell's **limit**, so the A\* prices it per expansion and a λ change costs one multiply. The clock reads that limit **whatever the price switches say** — a switch prices water, it never suspends a limit.
- **The margin** — the outside margin around a ring or the band is a **price, never a clearance**: the ring around the interior is priced at the configured fraction of the interior's excess.

## The readings

- **On the seam** — `RouteUpdate.stageDone`/`nextStage` carry the boundary just left and the one about to run; `readings` carry that stage's own figures (the search's expansions and passable cells, the pull's pulled-point count); `provisional` carries a rung's early distance and ETA.
- **In the log** — behind the `MaroRoute` tag's own level (`adb shell setprop log.tag.MaroRoute INFO`), the engine traces the corridor, the harvest, the grid's inventory, each pull and final line with its tallies, the snap, the pass's λ and forced crossing, the line's own figures, the fine pass's two pulls and every zone crossing, and one `DEVICE PASS` line per rung carrying the walk's cells, expansions, path cells, refusals, the coarse duration and the fine stage's. Nothing on a shipped path logs.
- **On the answer** — `RouteResult.Success` carries the points, the leg times and speeds, the distance, the duration, whether the destination moved, the forced-crossing zone names and the per-limit slow seconds.

## Values

The numbers ship in [`maro.properties`](../../app/src/main/assets/maro.properties); a reader looking for one
starts under the family that owns it:

| Family | What it governs |
|---|---|
| `route.engine.id` | The shipped engine row |
| `route.avoid.corridor.reachM` | The corridor's reach, and its doubling on growth |
| `route.avoid.grid.cellM`, `route.avoid.grid.fineCellM` | `avoid`'s walk cell and its fine cell |
| `route.avoid.obstacle.marginM` | The clearance margin around every edge |
| `route.avoid.depthGate.*` | The gate's switch and its minimum depth |
| `route.avoid.zone300.*` | The band's width, limit, outside margin, price fraction and switch |
| `route.avoid.speedZone.*` | The zones' switch, outside margin, price fraction, aversion and budget |
| `route.evolutive.grid.cellM`, `route.evolutive.grid.fineCellM` | `evolutive`'s two layers |
| `route.walk.maxCells` | The ceiling a walk is refused over before it is rastered |
| `route.repair.maxRadiusM` | The end repair's sweep radius |

## Known gaps

Current state, not history — these are the open facts a reader should not be surprised by:

- The engine's `aversionKn` and `slowWaterBudgetPct` providers are **live**: the first names the ranking's stop, the second is Best's gate. Those are their only readers, and the ranking's tail (`betterPass`) has its one caller.
- `LineDeviation.kt` and the `route.evolutive.fine.corridorHalfWidthM` key are **unread** by any shipped path.
- No test **drives `runPass` itself**, so a reverted call site that hands the pull the wrong step would not be caught.
- No unit test is **red on purpose-known grounds**.
