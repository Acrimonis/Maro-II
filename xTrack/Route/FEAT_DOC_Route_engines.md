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
- `RouteStage` — `CORRIDOR` · `GRID` · `SEARCH` · `PULL` · `SNAP` · `FINE`, the boundary set an engine publishes; `FINE` is the refinement along the settled line, the boundary the coarse pass hands the fine pass.
- `RouteUpdate(routeId, stageDone, nextStage, line, result, reason, readings, provisional, runningBest)` — the stage pair is **finished-then-next**, `nextStage = null` marks the terminal update, and `line` is a value to paint, never a drawing.
- `RouteProvisional(distanceM, durationSec)` — the pair a rung's first taut line already supports, replaced by the settled figures.
- `RouteRunningBest(lookupId, compared)` — the rung a running ranking currently names, and how many rungs it has compared; it rides **every terminal** of a ladder.
- `RouteStepReading(stage, labelResId, value, unitResId)` — one figure a stage reports about **its own** work.

**The registry** is [`RouteEngineChoice`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngineChoice.kt):
one row per algorithm (`id`, `labelResId`, a factory taking the resolved pace — setting or boat — the verdict's weight, the marker strength and the world providers),
`all` in menu order, and `resolve(id)` answering the shipped default for an id nothing claims. A new algorithm
is one row there plus one class implementing `RouteEngine`.

## The shipped engines

| id | Class | Its walk | What it reads |
|---|---|---|---|
| `dummy` | [`RouteDummyEngine`](../../app/src/main/java/ykws/android/maro/spatial/RouteDummyEngine.kt) | One straight segment, timed at a fixed 15 kn | **No layer at all** |
| `avoid` | [`RouteAvoidEngine`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt) with `UniformGridPlan` | One tile over the whole corridor, at `route.avoid.grid.cellM` | Coastline, depth gate, 300 m band, speed zones |
| `evolutive` | [`RouteEvolutiveEngine`](../../app/src/main/java/ykws/android/maro/spatial/RouteEvolutiveEngine.kt) with `EvolutiveGridPlan` | Two layers on one lattice family: the coarse interior and the fine coastal band | The same water as `avoid`, plus the berth its own plan prices — and with it the gate's wall at the berth's floor |

- **`dummy`** declares one computation and emits one update (`stageDone` and `nextStage` null, the straight line as the result). Its repair is a no-op — it has no water test to run one with — and `cancelLookup` has nothing to cancel.
- **`avoid`** is the shipped default and the engine the second row delegates to.
- **`evolutive`** is a composition, not a reimplementation: it holds a private `RouteAvoidEngine` built with its own plan and forwards the seam's three calls, so the pipeline, the clock and the readings are shared and the algorithm differs **by its plan alone**. A second composition, `selective`, was retired on 2026-10-16 with its collars, its depth price and its keys.

## The ladder

One arming declares **three rungs**, each a full solve at its own fixed λ over **one shared grid**: `around`,
`best` and `fast` — the three stops of [`RoutePreference`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePreference.kt),
which maps a stop's index and its word onto the λ its rung is solved at. **The three values are keys of their
own** — `route.avoid.ladder.around|best|fast.lambda` in `maro.properties`, read through `AppConfig` — because
they are the ladder's one lever and the file is where every value lives. The first rung is the **main** and
alone drives the map's provisional line, but **every rung narrates its own stage chain** — opening on the wait
for the shared grid (`GRID`), one entry per lookup so the concurrent rungs never share a cursor — so each row
prints its own stage word, and every rung publishes a provisional pair at the SEARCH→PULL boundary (the cell
path's length and clock) and again at SNAP (the pulled line's). There is **no budget loop**: a rung is computed at its own λ and
never corrected, so the λ decide which line each rung drew and the crown is decided apart, by the verdict's
own weight below.

**The rungs are ranked, and the winner rides the terminal.** At every rung's terminal the engine folds the
settled costs of the rungs that have landed through [`RoutePassRanking`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRanking.kt)
under the **verdict's own weight** — the user's stop on the dial
([`VerdictDial`](../../app/src/main/java/ykws/android/maro/config/VerdictDial.kt), `route.avoid.slowWater.weight`):
the crown is one sum, `trip time + weight × slow-water seconds`, the lowest winning, its *Ignore time* stop
weighing the slow seconds alone and a tie tolerance (`route.avoid.ladder.tieToleranceSec`) guarding the flip.
The λ name which line each rung drew, never which rung leads. The result travels as `RouteRunningBest` on the
terminal update, with the verdict beside it: the running best until the last rung lands, so the ranks read live
over the rungs landed so far and the crown is final only with the last.

## The pipeline

[`RouteGridBuilder`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt) builds the
grid once per arm and the rungs share it; [`RoutePassRunner`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt)
runs one pass; [`RouteFinePass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt)
runs the after-the-loop refinement. In order:

1. **Corridor** — the start-aim box inflated by `route.avoid.corridor.reachM`, clamped to the region's bounds; a truncated box is accepted.
2. **Harvest** — `segmentsIn` (ring and basin edges with their orientation) and `openCoastIn` (ordered mainland polylines).
3. **Rasterize** — one tagged, costed cell per tile: `LAND`/`FREE`, the band's and the rings' **limits**, and the depth gate's **measured edge** — the wall painted from the traced 3 m line and each near cell's distance to it written exactly — never a priced time, so the grid stays λ-free and one rasterise serves all three rungs.
4. **Ends** — both end cells force-freed and their discs opened, all on the arm's own assembled grid, never a cached tile; the search picks the way out of the disc by cost. The disc reads the gate's own rule: its wall is reopened only within the gate's own 20 m disc, and no stretch past that disc is exempt from the gate. An end too near the coast or the shallows is first moved clear by the end repair.
5. **A\*** — `MultipassSearch`, eight neighbours, a metres-equivalent g-cost so the haversine heuristic stays admissible, a cancellation check between expansions, over one window or a chain of windows. A **step guard** refuses a step whose straight segment comes within the gate's wall, so every raw leg the pull may keep already keeps it.
6. **Taut pull** — the two-pointer string pull at the walk's clearance step, with the price walk beside it; the gate's standoff is measured on its own 20 m lattice, refined to 2 m only near the edge.
7. **Corner snap** — each bend moves onto its nearest tangent corner while both legs stay clear, then the line is pulled again.
8. **The fine pass** — the refinement along the settled line: a **crossing re-solve** per priced zone the line enters (a local A\* inside the zone's own box, spliced only where it answers and only where it is no dearer), then a **re-tension** pull on the fine field, then the **settled lookback** (`sweep`) that removes the vertices the forward pull's first-refusal commit left standing. Its pulls take the **coarse** price step — a fine one collapses the price walk's grouping to one interval a group, the collapse the runner's own pass avoids — while the clearance step stays the fine grid's own. There is **no re-search**: the fine stage is this refinement and its sweep, and no second pass follows it.
9. **Medial nudge** — on `evolutive`, where the settled line crosses a passage between two shallow banks, `RouteMedialNudge` moves that stretch onto the passage's middle, kept only where every new chord is clear and the stretch is no dearer.
10. **Corner pass** — `RouteCornerPass.round` rounds each snapped corner into an outward-bulging racing-line curve, slowed where the bulge would foul; a bow keeps the gate's preferred standoff, or its own corner's where that is less — never under the wall outside the ends' waiver — and every straight it draws that no earlier stage measured (a bow's chords, the chords joining it past the kinks it passes on either side, a merged pair's extensions to their crossing) is measured against the same walls; a kink a bow has passed is not drawn again.
11. **Clock** — `timeLineWithProfile` times the drawn line with anticipation and bounded acceleration, the enforced limit the hard ceiling.

Around those: a **forced-crossing probe** (a second A\* over a copy of the grid with the restrictive zones
blocked) decides whether the line had no way around, and a **corridor growth** retries once with the reach
doubled on no path or forced crossing, keeping the wider answer only where it forces fewer crossings.

## The plan seam

[`RouteGridPlan`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt) is the engine's
injection point — the one thing that separates one algorithm from another:

- `name` — the engine name the trace prints, read from the plan rather than special-cased at the caller.
- `firstWalkGrid(corridor, baseCellM): List<GridTile>` — the rectangles the first walk may use, each at its own cell size.
- `fineCellM(baseCellM)` — the fine cell, in **metres**, the clock steps at.
- `fineWater(query): FineWater` — **where the fine layer may stand, in geographic terms before any lattice snap**: the cut reaches the builder grows the coast's segments by, and the membership bands a cell's coast distance must fall in. The builder snaps the answer to the corridor's fine lattice and merges it into windows; the window's mask keeps the answer's own membership.
- `pricesBerth` — **whether this plan's water pays the gate berth**: `true` on `evolutive`, whose law ([`BerthLaw`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt)) then also sets the gate's wall at its floor; `false` on `avoid`, whose wall stays at the gate's margin.

Two plans ship: **`UniformGridPlan`** (one tile over the whole corridor, an empty fine-water answer, the
fine cell its own metres key `route.avoid.grid.fineCellM`) and **`EvolutiveGridPlan`** (two tiles — the interior at
`route.evolutive.grid.cellM` and the band at `route.evolutive.grid.fineCellM`, coarse first so the family's
layers index coarse-then-fine — with the fine-water answer reproducing today's coastal ribbon byte for byte).
A plan decides **where and at what size** work happens and **whether its water pays the berth**; the search and the
pull compute the seconds, so a plan never times an answer. It reads one switch, the depth gate's own, whose state
decides whether a berth exists at all.

### The gate's wall and the berth — measured off the 3 m line

The depth gate's standoff is **measured, never sampled**: [`ShallowEdgeIndex`](../../app/src/main/java/ykws/android/maro/spatial/multipass/ShallowEdgeIndex.kt)
traces the gate depth's contour on the raster's own bilinear field, chunk by chunk as it is first asked, and answers
the distance to it; every reader subtracts its pad (one sub-cell's diagonal), so a reading is a lower bound on
the true distance. One [`GateRule`](../../app/src/main/java/ykws/android/maro/spatial/multipass/ShallowEdgeIndex.kt)
per arm carries the three distances that bind on it — the **wall** (`hardM`), the **preferred standoff**
(`route.avoid.depthGate.marginM`) and the **ends' waiver** (the same 20 m) — and the raster, the search's step guard,
the pull, the ends, the corner pass and the trace all read it. **The waiver is read, never assumed**: the search reads
an end's own first step past the 20 m disc ([`GateRule.keepsWall`](../../app/src/main/java/ykws/android/maro/spatial/multipass/ShallowEdgeIndex.kt)),
and walks an end the shallows box in under the old whole-step waiver, each end on its own; a held walk that finds
nothing is walked again waived only where it refused a waivable step into water it never reached, so the rule never
turns a route into no route; the `LINE` trace's `strictStart` and `strictAim` say how the answering walk read each end.

On `avoid` the wall stands at the margin. On `evolutive` it stands at the berth's **floor**
(`route.evolutive.berth.floorM`), and the water up to the margin is the berth's **inner ramp**
(`route.evolutive.berth.innerSecPerM` at the floor, falling straight to `secPerM` at the margin); from the margin to
`route.evolutive.berth.outerM` the **outer ramp** falls to nothing. The rate never rises with the distance, so a
symmetric passage is cheapest in its middle, and a passage of 50 m or more always holds a legal grid row. The
price is λ-scaled at read, reported under its own `PriceArm.DEPTH`, and reaches neither the clock nor the ranking.
Inside the inner ramp the pull's price walk reads mark by mark rather than by a span, the ramp being steep.
Design and evidence: [`261017_FEAT_PLN_Route_gate-standoff-sampling.md`](261017_FEAT_PLN_Route_gate-standoff-sampling.md).

### The collars and the mask cache — retired with their engine

The four `route.selective.*` collars and the cache that held their portable union went with the engine, and the
zone-rim and depth-dilation marking passes left the builder with them. What survives of the fine layer is the
coastal ribbon the one two-layer plan answers, snapped per arm to the shared tile grid.

## The shared `multipass` layer

Everything the engines stand on, under
[`spatial/multipass/`](../../app/src/main/java/ykws/android/maro/spatial/multipass):

- **The world** — [`MultipassWorld`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt): the coastline segments and open coast, the water test, the coast distance, the depth sample, the depth gate's edge (`shallowEdges`), the speed zones, the region bounds and the band width. The live implementation is `LiveMultipassWorld`, whose edge index is kept for the session, keyed on the loaded raster, the EMODnet cutoff and the gate's depth.
- **The lattice** — `WalkLattice` / `LatticeFamily` / `WalkWindow` / `WalkWindows`: one origin and one cell size per layer, and a walk that is one window (the uniform pass) or several on one lattice (the two-layer walk), with the seam between two layers an index relation. **The walk reads the fine cell over the coarse copy**: where a **passable** fine cell stands over the same water as a coarse cell, that fine cell is the one the walk reads and the coarse cell is not read at all; where the fine grid is **land** at that coordinate, the coarse cell keeps its ordinary role. It is a rule of the reading alone — the walk's own resolution, never the rasterizer — so no grid's content moves and `avoid`'s single-grid walk is untouched; `evolutive`'s coastal ribbon and `selective`'s collars become fine-only water, and their lines and clocks move with it. An end standing in a collar resolves to a fine cell, and the berth carve follows that end's own window.
- **The fine layer's tiles** — [`FineTile`](../../app/src/main/java/ykws/android/maro/spatial/multipass/FineTile.kt), [`TileKey`](../../app/src/main/java/ykws/android/maro/spatial/multipass/TileKey.kt), [`FineTileCodec`](../../app/src/main/java/ykws/android/maro/spatial/multipass/FineTileCodec.kt), [`FineTileMap`](../../app/src/main/java/ykws/android/maro/spatial/multipass/FineTileMap.kt) and [`DirectoryFineTileStore`](../../app/src/main/java/ykws/android/maro/spatial/DirectoryFineTileStore.kt): the fine layer's marking is cached as **fixed tiles on the anchored fine lattice**, each tile a sparse block of its collar members (a `ByteArray` tag, six `DoubleArray`s and an `IntArray` of local indices) built by the existing `rasterizeWindow` inside a collar-wide **halo** and keyed on every value that rasterisation reads — the anchor and fine cell, the depth and coastline stamps, the EMODnet cutoff, the switches, the excluded-zone set, the geometry and the zone set with its own content stamp — **but never the pace**. A tile is **pace-free**: it is rasterised at a **unit base** with its own field (the depth gate's wall and the marker price at `1.0 × factor`), so its stored double is the per-member factor `f` (`1 + m`, the summed marker step-scale) and `writeInto` re-applies the **target grid's own base** in one multiply — so one tile serves every speed and a pace change is neither a new key nor a new byte. One `TileKey → Deferred<FineTile>` map builds each tile **once** behind a byte ceiling with LRU eviction and an in-flight guard, a store-served lookup counting a **hit**, so a per-arm fine window is assembled by **copying** the tiles that cover it; on a miss the map consults the on-disk `DirectoryFineTileStore` first — its entries kept per installed build, so a tile another build computed is never read — and writes each build back. **The coarse interior stays the per-arm corridor raster** — only the fine layer is tiled — and **the ends are out of the tile**: the arm's `forceFree`, `openEndDisc` and `openEndCell` write to the assembled window, never a tile.
- **The pre-warm** — [`FineTilePrewarm.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/FineTilePrewarm.kt) builds the fine layer's tiles over the seed box (`route.prewarm.box`) before an arm asks, through the **builder's own key and build** ([`RouteGridBuilder.fineTilesOver`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:458)), so a tile it makes is the tile an arm over that water asks for. [`FineTileWarm.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/FineTileWarm.kt) then grows that area **outward in rings** of the plan's own lattice — `route.prewarm.growthStepTiles` a ring, and **no radius dial exists** — stopping on the store's own room under `route.prewarm.targetPct` of its ceiling ([`StoreFullness.hasRoomAt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/TileStore.kt), a live verdict that cannot latch, asked per tile inside the pass and per ring by the walk), on the baked region's own edges, or on `route.prewarm.maxTiles` / `route.prewarm.maxMs`. The walk is store-first (a warm tile is skipped by name before any build, so a fully warm ring costs a listing, opens no window and shows no dot), ordered **own-ship outward**, and one walk at a time. It runs at the app edge ([`MapRoutePrewarmEffect`](../../app/src/main/java/ykws/android/maro/ui/map/MapRouteEffects.kt)) on **a dispatcher of its own** whose workers carry a background CPU priority (`route.prewarm.threads`) and started once both baked layers can answer, so a moving boat is no reason to leave the water cold; it **yields** to an armed route and to a depth-raster regeneration, re-asked per tile and per ring, and **stands down** on battery-saver. Its white dot is the warm-up's own `busy` window, opened for each tile the walk really builds and merged by the holder's grace — dark while the walk waits at its gate, and unlit where every ring is already warm.
- **The search** — [`MultipassSearch`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassSearch.kt): `search(grid, …)` and `searchWalk(windows, …)`, one loop over whichever walk it is handed.
- **The pull** — [`MultipassPull`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt): the clearance walk, the price walk (grouped behind a declaration the source makes, with a span-level proof above it), the corner snap's entry and the refusals it counts. **The settled lookback** (`sweep`) is the fixpoint pass over a settled line, dropping an interior vertex where the skipping chord is clear **and** no dearer — within the share of the legs' own field price — never their travel nor the gate berth — that `route.avoid.pull.allowanceShare` states — and it carries **two mechanisms**: a triple, and a window over a run whose intermediate turns all stay under the algorithm's own gate, capped by its own segment span, so a flat wiggle falls to one chord while a turned run is left alone. The clearance proof is read first in both, so a chord refused by land pays no price read.
- **The field** — `RouteCostField` and [`RoutePassPrimitives`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt): `costField(...)` builds the walls and the prices, `limitAtFor(world)` is the λ-free clock read, and `snapToCorners`, `zoneMetres`, `inZone`, `inBand` and `clampTo` sit beside them.
- **The seats** — `RouteGridBuilder` (the build), `RoutePassRunner` (one pass), `RouteFinePass` (the refinement) and `RoutePassRanking` (the preference-aware ranking, `RoutePreference` beside it as the ladder's one home), each composed once by the engine. `RoutePassModels` holds `GridWalk`, `GridContext` and `PassReading`.
- **The post-passes** — [`RouteCornerPass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCornerPass.kt) and the clock in [`RouteEta`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt).
- **The geometry** — `TangentCorners` (the tangent corner sets) and `ZoneGeometry` (rings, collars, the price law).
- **The ends** — [`BerthCarve`](../../app/src/main/java/ykws/android/maro/spatial/multipass/BerthCarve.kt): the end discs, their reach and the shore margin.

## The water the grid carries

One [`RouteCostField`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt) per solve,
a list of sources that may only **add** to an explicit base:

- **HARD walls** — the coastline, materialized by the rasterizer's geometry sweep, and the depth gate when it is enabled and the depth layer is ready: its interior is a bilinear read per cell centre against `route.avoid.depthGate.minM`, ANDed with the coastline's own water, and its standoff is painted from the measured 3 m line (the gate's wall above). A wall is a cell the route may never cross.
- **SOFT prices** — the 300 m band and the regulated speed zones, priced by **one law**: a cell's time excess over the pace at the limit in force, scaled by the pass's λ. The band's limit and a ring's are the same quantity through the same function, and two sources over one cell charge it once.
- **The berth, a price of the distance off the measured edge** — on `evolutive` alone, through the plan's own answer: the inner and outer ramps above, λ-scaled at read and reported under its own `PriceArm.DEPTH`. It is **not** a `Soft` source — a tile stores a soft source's cost as its pace-free factor, which would scale it by the pace — so the search reads it off the grid's stored distance and the pull off the edge index at the point itself.
- **The limit is written on the grid, the price is not.** The rasterizer stores each slow cell's **limit**, so the A\* prices it per expansion and a λ change costs one multiply. The clock reads that limit **whatever the price switches say** — a switch prices water, it never suspends a limit.
- **The margin** — the outside margin around a ring or the band is a **price, never a clearance**: the ring around the interior is priced at the configured fraction of the interior's excess.

## The readings

- **On the seam** — `RouteUpdate.stageDone`/`nextStage` carry the boundary just left and the one about to run; `readings` carry that stage's own figures (the search's expansions and passable cells, the pull's pulled-point count); `provisional` carries a rung's early distance and ETA.
- **In the log** — behind the `MaroRoute` tag's own level (`adb shell setprop log.tag.MaroRoute INFO`), the engine traces the corridor, the harvest, the grid's inventory, each pull and final line with its tallies, the snap, the pass's λ and forced crossing, the line's own figures, the fine pass's two pulls and every zone crossing, one `DEVICE PASS` line per rung carrying the walk's cells, expansions, path cells, refusals, the coarse duration and the fine stage's, and one **`TILE built`** line per fine-tile build **attempt** — traced at the miss, before the build runs, so a build that then throws is still logged as built (the ledger's D64) — so a second arming's reuse reads as a count rather than a timing. The on-disk store adds one **`STORE`** line per tile **load** and per **save** (its `hit=true`/`hit=false`) and one `STORE reclaim` line on its first disk-ceiling reclamation, all through an injected sink so they stay inert off-device. Nothing on a shipped path logs.
- **On the answer** — `RouteResult.Success` carries the points, the leg times and speeds, the distance, the duration, whether the start or the destination moved, the forced-crossing zone names and the per-limit slow seconds.
- **The pre-warm's own lines** — `PREWARM warm` (the pass found nothing to build), `PREWARM tile` / `PREWARM done` (the tiles it built and why it stopped) and `PREWARM box outside region` (the box overflows the baked bounds, named and not shrunk), all on the same `MaroRoute` tag.

## Values

The numbers ship in [`maro.properties`](../../app/src/main/assets/maro.properties); a reader looking for one
starts under the family that owns it:

| Family | What it governs |
|---|---|
| `route.engine.id` | The shipped engine row |
| `route.avoid.corridor.reachM` | The corridor's reach, and its doubling on growth |
| `route.avoid.grid.cellM`, `route.avoid.grid.fineCellM` | `avoid`'s walk cell and its fine cell |
| `route.avoid.obstacle.marginM` | The clearance margin around every edge |
| `route.avoid.depthGate.*` | The gate's switch, its minimum depth and its preferred standoff off the measured edge — the wall on `avoid` |
| `route.avoid.zone300.*` | The band's width, limit, outside margin, price fraction and switch |
| `route.avoid.speedZone.*` | The zones' switch, outside margin and price fraction |
| `route.evolutive.grid.cellM`, `route.evolutive.grid.fineCellM` | `evolutive`'s two layers |
| `route.evolutive.berth.*` | The berth: the wall's floor, the inner ramp's rate at it, the outer ramp's rate at the margin and its outer edge, before λ |
| `route.walk.maxCells` | The ceiling a walk is refused over before it is rastered |
| `route.fineTile.maxBytes` | The fine-tile store's on-disk ceiling — a sibling of the map's in-memory one; a **soft** bound, since the protected home area may hold the store above it, in which case the reclaim takes the least recently used unprotected entry and reports once when none is left |
| `route.prewarm.box`, `route.prewarm.maxTiles`, `route.prewarm.maxMs` | The pre-warm's own box (`south,west,north,east`) and its two caps — the tile budget and the time budget |
| `route.prewarm.growthStepTiles`, `route.prewarm.threads`, `route.prewarm.targetPct` | How many tiles one growth step adds to each edge, the workers the warm-up's dispatcher may run, and the share of the store's ceiling the warm area may fill — the rest standing as a reserve |
| `route.repair.maxRadiusM` | The end repair's sweep radius |
| `route.repair.clearanceM` | How far an end stands clear of the coast and the 3 m line (25) |
| `route.follow.swap.*` | **Not the pipeline's**: the heading-away mirror's dead-band, minimum speed and hysteresis (R99) — a `Following`-phase behaviour of the state machine that reads the boat's course against the armed destination. The engine, the ladder and the clock are untouched; the behaviour's own home is `FEAT_DSC_Route.md` R99 |

## Known gaps

Current state, not history — these are the open facts a reader should not be surprised by:

- **The fine layer's marker base is the fine lattice's own** — the tile's field prices its markers at the unit base and `writeInto` re-applies the fine base, where the per-arm coarse field priced them at the coarse base. On a world **with priced markers** the drawn line can therefore move where the fine layer decides; **on marker-free water nothing moves** (the harness's line is unmoved). The device pass is owed to see a marker-priced fine line.
- `FineTileMap.hitRate` still has **no production reader**: the `STORE` lines are the reading a pass has today, and an arm-end cache line (`hits`/`misses`/`rate`/`bytes`) would close the older finding.
- `LineDeviation.kt` and the `route.evolutive.fine.corridorHalfWidthM` key are **unread** by any shipped path.
- No test **drives `runPass` itself**, so a reverted call site that hands the pull the wrong step would not be caught.
- No unit test is **red on purpose-known grounds**.
