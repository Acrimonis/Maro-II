<!-- scope: feature -->
# Route — engine interface: implementation spec

**Scope:** the Route engine's next interface and the conversion of the current flow onto it. Facts only — this file is the
implementation reference, with no decision history.

## The interface

```kotlin
@JvmInline value class RouteId(val value: Long)

data class RouteComputation(val id: RouteId, val descriptionResId: Int)

sealed interface RouteDeclarations {
    data class Available(val computations: List<RouteComputation>) : RouteDeclarations
    data class Refused(val reason: RouteReason) : RouteDeclarations
}

enum class RouteReason(val labelResId: Int) { CANNOT_REPAIR, WORLD_NOT_READY, NO_PATH, OFF_WATER }

data class RouteUpdate(
    val routeId: RouteId,
    val stageDone: RouteStage?,        // null when the engine has no stage to report
    val nextStage: RouteStage?,        // null = the lookup is done
    val line: List<RoutePoint>,        // the line computed so far, for the flow to paint
    val result: RouteResult.Success?,  // null until done
    val reason: RouteReason?           // null on success
)

interface RouteEngine {
    fun routesToCompute(origin: RoutePoint, destination: RoutePoint): RouteDeclarations
    fun startLookup(computationId: RouteId): RouteId
    fun cancelLookup(id: RouteId)
    val updates: Flow<RouteUpdate>
}
```

- All three calls are plain, not `suspend`.
- The updates are one per-engine `MutableSharedFlow<RouteUpdate>`; the compute jobs run on an engine-owned scope; the id is the only correlation the flow reads.
- `RouteResult.Success` keeps `points` · `legTimesSec` · `distanceM` · `durationSec` · `forcedCrossingZoneNames`; its `offers` field is deleted.
- The stage set is the existing `RouteStage` (`CORRIDOR` · `GRID` · `SEARCH` · `PULL` · `SNAP`); a callback reports the stage just finished and the next one.

## The flow

1. On arming, the flow reads the drawer's pair and calls `routesToCompute(origin, destination)`.
2. The engine repairs the pair first, then answers `Available` — the declared computations, **first = main**, the main running all default values — or `Refused(reason)`.
3. The flow starts the main lookup and later the secondary ones; `startLookup` takes only the id, the whole set sharing the repaired pair.
4. Every started lookup gets a page; the selected page is the main and is what the buttons act on; next/previous walks the pages.
5. The main paints at high opacity, the rest at the low opacity — `route.line.transparencyPct` and `route.dimmed.transparencyPct`, no new drawing value.
6. Finished lookups feed an inventory; the panel's top area shows *"Route #n is taking x minutes less or more"*, the flow's arithmetic of each route's own duration against the selected route's.
7. Disposal: every site that drops a route — the exit dialog's discard, the fan's unasked Discard, a new arming, a selection — calls `cancelLookup`.
8. No engine holds a clock; `routesToCompute` is read once per arming; painting is the flow's alone.
9. The toggle is not gated: it arms, and the status line carries the refusal.

## The repair

- Engine-internal, the **first step of `routesToCompute`**, once per pair at the arming.
- A point that is land or shallower than the minimum depth is moved to the nearest valid water on the sea side.
- Rule: 8 directions at a 25 m step, a ring sweep growing to `route.repair.maxRadiusM` — default **200**, in `maro.properties`, behind `AppConfig`; the first valid point wins, and nearest means sea side.
- Budget: **under 50 ms** per point, carried in the KDoc and proven by a test.
- Failure: `Refused(CANNOT_REPAIR)`, or `Refused(WORLD_NOT_READY)` when the layers the judgement needs are absent.
- It replaces the branch in [`RouteAvoidEngine.routeBetween`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:293) that checks `world.isWater` and returns `RouteResult.OutsideWater`.
- The dummy reports it cannot repair.

## The computations

- Dummy: one computation; `startLookup` emits one update — `nextStage` null, `stageDone` null, `result` = the straight line.
- Avoid: the settled computation first (all default values), then one per `route.avoid.candidate.passes` entry; the candidate bodies reuse the current `offers(...)` pass ([`RouteAvoidEngine.kt:1057`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:1057)) under their own id.
- The avoid engine reads `route.avoid.candidate.*` itself; the flow reads none of it.
- The harness [`RouteEngineChoice.factory`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngineChoice.kt:26) keeps its three providers — pace, slow-water budget, world — and only the interface it returns changes.

## The reason set

| Reason | Produced at | String key | EN | FR |
|---|---|---|---|---|
| `CANNOT_REPAIR` | `routesToCompute` | `route_reason_cannot_repair` | No valid water near this point | Aucune eau valide à proximité de ce point |
| `WORLD_NOT_READY` | `routesToCompute` | `route_reason_world_not_ready` | Water data is still loading | Les données d'eau se chargent encore |
| `NO_PATH` | the callback | `route_reason_no_path` | No route found | Aucun itinéraire trouvé |
| `OFF_WATER` | the callback | `route_reason_off_water` | Outside the covered water | Hors de la zone d'eau couverte |

## The computation descriptions

| Computation | String key | EN | FR |
|---|---|---|---|
| main | `route_computation_main` | Full avoidance | Évitement complet |
| pass 1 | `route_computation_no_zones` | Ignore speed zones | Ignorer les zones de vitesse |
| pass 2 | `route_computation_no_zones_band` | Ignore zones and 300 m band | Ignorer les zones et la bande des 300 m |

## What goes

- From the seam: `state` · `prepare` · `validatePoint` · `isReadyToRecompute` · `onOriginPositionChanged` · `onDestinationPositionChanged`, and the types `RouteEngineState` · `RouteUnavailableReason`.
- `RouteRefusalReason`'s `OFF_WATER` · `TOO_SHALLOW` · `NO_APPROACH` — folded into the reason set above.
- `RouteEngine.progress` · `RouteEngine.offers` — replaced by the one update flow.
- From the view model: `_progress` · `_offers` · `askJob` · `anchorTold` · `prepareCurrent` · `prepareAgain`, and the arm/ask pair.
- From the screen: the readiness gate, the prepared-tap, `routeProgress`/`routeStage`.
- From the host: `ROUTE_TARGET_*` constants, the `refused` flag, the crosshair paint and its beat.
- From the properties: `route.target.color` · `route.target.widthDp` · `route.target.pulseMs` and their `AppConfig` accessors and parse block; the refusal strings only the crosshair read.
- The flow's own read of `route.avoid.candidate.*`.

## Build order

1. **Seam + harness + both engines, atomic** — the new types and the three members land, the retired members are deleted in the same hop so the tree never holds two contracts; the harness's factory moves with it.
2. **View model** — `arm(ends)` reads the pair → `routesToCompute` → `Refused` shows the reason, `Available` keeps the set, starts the main and later the rest; the lookup registry by id, the page collector, the inventory and the comparison arithmetic land.
3. **Screen, host, panel** — one update collector paints the two opacities, the pages take the next/previous walk inherited from today's candidate rows, the panel's top area holds the title, the stage or refusal word and the comparison (the comment leaves), and the toggle loses its readiness gate in the same step that removes its disabled face.
4. **Disposal sweep** — the table above.
5. **Pages, inventory, comparison** — the net-new UI half.

Each hop is a build gate: `apk-build.bat` green and the route-filtered suites green before the next; two consecutive failures halt.

## Tests

- A foreign engine driven through `routesToCompute` → `startLookup` → callbacks → `cancel`; an update for a cancelled id changes no page.
- A repair case; a repair-failure case with `CANNOT_REPAIR`; a search-failure case with `NO_PATH` or `OFF_WATER`.
- The repair's own test proving the **50 ms** budget.
- The dummy's one-update answer.

## Parked / out of scope

- Dismissal of a route.
- What the answer says about where the line really ends.
- Any parameters object on the seam — the parameters are the engine's own.
