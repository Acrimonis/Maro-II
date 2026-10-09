<!-- scope: feature -->
# Route — the ladder's λ-free phases, shared once per arm

**Date:** 2026-10-08 · **Status:** in design — nothing built · **Order:** the user's discussion of
2026-10-08, opened on the acquisition's three queries and closed on *"the point is zones and coastal
routes"*.

**Origin:** the question of which acquisition phases could be shared across the ladder's three rungs
(`around` · `best` · `fast`) to lower the arm's overall cost. The grid build is already shared; two
**λ-independent** phases still run once per rung, and both sit on exactly the routes the user named —
those crossing regulated zones or needing a widened corridor.

## Already shared — do not duplicate

- **One grid per arm.** The corridor, the harvest, the one rasterise, the ends' berth carve and the
  corner sets are built once and awaited by all three rungs ([`sharedGrid`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:315),
  [`RouteGridBuilder.buildGrid`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:34)); three rungs already cost one rasterise and three A\* passes.
- **Why it is shareable.** The builder is **λ-free by construction** — the grid stores *limits*, never
  prices — so one rasterise serves every rung ([`RouteGridBuilder.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:20)).
- **`dummy` is out of scope.** It declares a single computation and reads no water, so it has no ladder
  to share between.

## Candidate 1 — the grown corridor (reach × 2)

- **The path.** [`searchRung`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:349) rebuilds the grid at `route.avoid.corridor.reachM × 2` in two cases: a rung that finds **no path**, and a rung whose answer carries a **forced crossing** and gets one wider corridor to find the way around. A saturated region is excluded.
- **Its inputs carry no λ.** The world, the repaired pair and `reach × 2` are shared by every rung, so the grown grid is **identical across the ladder**, yet each rung that needs one rebuilds it — up to three extra rasterises per arm.
- **Why it is expensive.** The grown path is a **second full solve** — `solveAtLambda(grown, …)` re-runs the A\*, the pull, the snap, the fine pass and the clock ([`RouteAvoidEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:355)) — over a box the one measured figure puts at roughly **2.5×** the cells (a long route's `476 700` fine cells against its grown retry's `1 178 555`, [`AppConfig.kt`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:188)).
- **The fix.** Mint the grown grid **once per arm** behind a `Deferred`, exactly as `sharedGrid` does; every rung that needs it awaits the same instance.

## Candidate 2 — the forced-crossing probe

- **The path.** [`forcedCrossingNames`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:583) takes a `blockedCopy(pace)` of the interior grid and runs a **second whole-grid A\*** ([`RouteAvoidEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:601)) once per rung, per grid.
- **λ enters only as a switch, never as a magnitude.** The restrictive set is `priced.filter { zonePriceSec(cellM, pace, it.limitKn, lambda) > 0.0 }` — the price is the cell's time excess over the limit, scaled by λ — so it is **identical for every λ > 0** and empty at λ 0. The `fast` rung therefore exits before doing any work ([`RouteAvoidEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:597)), and the `around` and `best` rungs compute the **same** blocked copy and the **same** probe A\*.
- **The fix.** Compute the blocked copy and its probe **once per arm (per grid)** and reuse it for the λ > 0 rungs; only the cheap `forcedCrossingZoneNames(waypoints, …)` intersection against each rung's own line stays per rung.
- **Duplication is worse than one-per-rung.** The probe runs on the base grid and again on the grown one, so a zone-crossing route that also grows runs it up to six times for work that is rung-independent — sharing removes as many as four.

## Candidate 3 — the guard field's walls (unproven)

- [`runPass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt:74) rebuilds a `costField` at the fine tail cell per rung; its **hard walls** are λ-free while its **soft prices** are not.
- Only the wall half could be cached, and the pull consumes the field whole — so the win is unproven and out of this plan's first cut.

## What can never be shared

- Everything λ-shaped: the A\* pass, the taut pull's price guard, the fine pass's crossing re-solve and re-tension ([`RouteFinePass.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:45)), the corner pass and the profile clock. A different λ means a different cost field, hence a different path.

## Engine scope — inherited by every avoid-derived engine

- Both candidates live inside [`RouteAvoidEngine`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:126), so a fix there is inherited.
- [`RouteEvolutiveEngine`](../../app/src/main/java/ykws/android/maro/spatial/RouteEvolutiveEngine.kt:28) is a **composition**: it holds a private `RouteAvoidEngine` built with `EvolutiveGridPlan` and forwards the seam's three calls, so it runs the same ladder, the same growth and the same probe unchanged.
- The plan seam cannot block it — a `RouteGridPlan` decides only *where and at what size* work happens, never prices or switches ([`FEAT_DOC_Route_engines.md`](FEAT_DOC_Route_engines.md:92)).
- **`evolutive` gains the most**: its grown rebuild is the **layered** build, whose window sweep is the one cost the code quantifies — *361 tiles at ~12 ms each* ([`RouteGridBuilder.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:375)).

## The measurement gap

- **Nothing times either phase.** The only route timing is the `DEVICE PASS` line's `coarseMs` and `fineMs` ([`instrumentCoarseWalk`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:676)), and `coarseStartNs` is taken **after** the grid is awaited ([`RouteAvoidEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:380) following :286) while `fineMs` is the fine pass alone.
- **Therefore no percentage exists**, and the size of the win cannot be read from the tree; it must be judged by workload — the routes that cross a regulated zone or need the widened corridor — or measured by a device pass.

## Risks

- **Concurrency.** The three rungs launch together on `Dispatchers.Default`, so each shared unit needs a **mint-once** guard. The existing `sharedGrid` check-and-set is a plain `?.let` and is **not** atomic — the new units must not copy that shape.
- **The walk ceiling.** The grown build may be refused by `route.avoid.walk.maxCells`; the shared unit must carry a `null` exactly as the per-rung call does, or a refused growth becomes a crash.
- **Ordering.** The greedy "first rung to reach it builds it" already applies to `sharedGrid`; the new units inherit the same semantics, so a rung that lands before the builder finishes simply awaits.

## Phases

1. **The grown grid, minted once.** Add a per-arm `Deferred<GridContext?>` for the `reach × 2` build, keyed on the repaired pair, and route both growth sites in [`searchRung`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:349) through it. Exit: a fixture with two failing rungs shows **one** grown build in place of two, and each rung still answers its own line.
2. **The probe, computed once per grid.** Cache the `blockedCopy` plus its `MultipassSearch.search` result per grid and per restrictive set, leaving `forcedCrossingZoneNames` per rung. Exit: a fixture with two λ > 0 rungs shows **one** probe search, the reported names unchanged per rung, and the `fast` rung still probing none.
3. **The record.** The ladder section of [`FEAT_DOC_Route_engines.md`](FEAT_DOC_Route_engines.md:54) and the feature file's state lines name the two shared units and the one-build-per-arm rule.

## Verification

- **No line, no clock and no name moves.** The three rungs are computed exactly as today; only *how many times* the two shared units are built changes — so every existing engine fixture stays green.
- **The saving is structural, not numeric.** The fixtures count built grids and probe searches, not milliseconds, since nothing in the tree times them.
- **The growth refusal survives.** A grown build over `route.avoid.walk.maxCells` still yields the rung's first answer.
- **Suite and build green**, the parked `route.avoid.fine.cellRatio` red the only one.

## Assessment

- **The win is real but conditional and bounded.** It is exactly zero on an open-water route, where neither phase runs, and non-trivial where zones or a widened corridor fire — at most two spare rasterises and two spare probe A\*s per arm, each a large unit rather than noise.
- **It ranks below trimming the per-rung work.** The bulk of every arm is the three A\* passes and their refinements, which no sharing can remove; this plan is a bounded saving on the failure paths.
- **Open: is a device reading owed before building?** The tree cannot quantify the win; the user owns the device, and a pass on a zone-crossing coastal route would decide whether this outranks other Route work. Nothing here gates a code landing (R97).
- **Open: whether candidate 3 joins.** The guard field's wall half is unproven and deliberately excluded from the first cut.
