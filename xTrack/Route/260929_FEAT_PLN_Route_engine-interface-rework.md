<!-- scope: feature -->
# Route — the engine's interface: it repairs the pair, declares its routes, the flow paints them

**Date:** 2026-09-29 · **Status:** in design — nothing built. Every requirement below is the user's word of 2026-09-29 (rev 4): the
fifteen functional expectations, their verdicts, the flow he set out, the answers he gave on the passes since, and the shuffle he made after
the independent review. Where a reading is the agent's it says so.

**What this plan is for:** the seam was shaped for a mode that placed a destination on the map and re-asked on a clock; neither is true any
more. This plan states the interface the flow needs — **the engine repairs the pair, owns which routes are computed and with which
parameters; the flow owns painting, pages and selection** — and what is retired with it.

## 1. The flow

- **Entering the acquisition**, the flow reads the drawer's pair and calls `routesToCompute(origin, destination)` with it; **the first step of that call is the repair**, so a new origin and a new destination come back computed, and the flow never sees the old ones.
- **The engine returns the set of routes it can compute, each carrying its own id** — first meaning main, the main running with all the default values. The list is read once, for now.
- **Starting any route of the set needs only its id**, because a set of acquisitions always runs on the same repaired pair — the user's word: *"The call to start the acquisition of any of the routes returned do only need the id."*
- **The main lookup paints at high opacity as its callbacks arrive and becomes selectable once its last stage is done**; the other routes paint in the background at the lower opacity.
- **Every started lookup gets a page**, walked with next/previous; the selected page is the main and is what the buttons act on.
- **Finished lookups feed an inventory**, and the panel's top area shows the comparison — *"Route #2 is taking x minutes less or more"* — the flow's arithmetic of each route's own duration against the selected route's.
- **The engine owns every value that says which routes exist and what they are**, the candidate configuration included; those fields move from the functional flow into the engine.
- **An engine that cannot answer does not gate the mode**: the toggle arms, and the status line says why.
- **Painting is the functional flow's responsibility, never the engine's.**
- **Dismissing a route is parked** — not in this interface, resumed only if the user asks.

## 2. The interface

- **`routesToCompute(origin, destination): RouteDeclarations`** — repairs the two points first, then returns the set. The return is a sealed shape the agent adds for the same reason the callback gained its fifth field: the repair can refuse, and a refusal has to be said. `Available(computations)` or `Refused(reason)`.
- **`startLookup(computationId): RouteLookupId`** — starts one declared computation by its id; the pair is the repaired one the whole set shares, so no points are passed.
- **`cancelLookup(id)`** — ends a lookup by id, and no further callback for it may arrive afterwards. This is the only disposal an engine performs; every other disposal is the flow asking.
- **One callback shape carries everything the flow learns about a lookup**:

```kotlin
routeComputingStageDone(
    routeId,                  // which lookup this is about
    stageName,                // the stage just finished — grid, pull, …
    nextStageOrNull,          // the stage about to run, or null when the job is done
    currentComputedRoute,     // the line computed so far, for the flow to paint
    reason                    // null on success, else why the lookup cannot be answered
)
```

- **The fifth field is the agent's addition, stated rather than hidden**: the user's four fields leave no slot for a search that cannot be answered, so the callback gains a nullable `reason`.
- **`RouteComputation` carries its own id, its description and its parameters**, all minted and held by the engine; the flow reads the array and hard-codes no count.
- **The stage pair means finished-then-next**, which flips today's meaning: the seam now names the boundary just *entered*, and this names the one just finished with the next promised.
- **The callbacks are a `Flow` of values carrying those five fields** — the repo is coroutines and `Flow` only; a listener passed into an engine is rejected, because it puts state there that the flow would have to unregister.
- **The line is a value, not a drawing**: the engine hands the computed route and nothing else.
- **One closed reason set, its variants closed as follows** — `CANNOT_REPAIR` (the repair could not move an end to valid water within the sweep's radius; origin and destination told apart by which end the status line names), `WORLD_NOT_READY` (the coastline or the depth the repair judges by has not loaded — the old `COASTLINE_NOT_LOADED` · `DEPTH_NOT_LOADED` folded in), `NO_PATH` (the search found no route between the repaired ends — the old `NoPath`), and `OFF_WATER` (the pair or the region the engine covers leaves no water to search — the old `OutsideWater`). The repair's two answer at `routesToCompute`, the search's two on the callback, and every variant keeps the `@StringRes` shape so no engine holds user-facing text.
- **Forced crossings arrive as an array**, and the refusals the flow resolves to copy stay named.

## 3. Who owns what

| Owner | Owns |
|---|---|
| **The engine** | the repair and the reason it gives when it cannot; how many routes there are, in what order, with which parameters; the candidate configuration and every value saying so; the pipeline's stages; the line computed so far; the reason a search cannot be answered |
| **The functional flow** | the anchor's lead — it hands the points, the engine repairs what it is handed; painting at the two opacities; the pages, their walk and the selected one; which lookups to start and when; every disposal, calling `cancelLookup`; the inventory and the comparison line; the buttons' target; displaying the reasons |

## 4. The repair

- **What it is** — a point that is land or shallower than the minimum depth is **moved to the nearest valid water on the sea side**, replacing the refused-end crosshair and the three `route.target.*` keys.
- **Where it runs** — the **first step of `routesToCompute`**, once per pair at the arming, before any computation is declared; a failure is `Refused(reason)` and the status line shows it.
- **The lead stays where it is** — the flow projects the boat's fix by `route.anchor.leadSec` and hands the resulting points over; the engine repairs exactly the two points it is handed. Nothing of the lead migrates.
- **The sea-side rule the agent picks** — a ring sweep outward from the invalid point: 8 directions at a 25 m step, tested with the engine's own water test, growing to a **maximum radius**; the first valid point is the repair, and because it is the *nearest* valid water it is on the sea side by construction.
- **One value in `maro.properties`** — `route.repair.maxRadiusM=200`, behind `AppConfig`, the only knob; the directions, the step and the sweep are constants of the algorithm, not keys.
- **The budget is the acceptance, not a value** — the repair must answer in **under 50 ms** for one point; the KDoc carries the number and the step's own test proves it.
- **The dummy reports it cannot repair**, which is its honest state — it reads no water — so a dummy acquisition whose end stands on land is refused with that reason.

## 5. The panel's top area

- Today the top of the acquisition panel holds three things: the phase title, the short state word, and the comment ([`RouteConfirmPanel.kt:53`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:53)).
- The comparison takes the comment's place; the title and the state word stay — the user's word given on 2026-09-29. So the top holds the **title, the stage or refusal word, and the comparison once an inventory exists**.

## 6. What goes

- **`state` · `prepare` · `RouteEngineState` · `RouteUnavailableReason`** — readiness, retired, and **not replaced by a gate**: the toggle arms and the status line carries the reason.
- **`onOriginPositionChanged` · `onDestinationPositionChanged`** — replaced by `routesToCompute` + `startLookup`; the "held the other end" session semantics leave with them.
- **a single returned `RouteResult?`** — retired; results arrive as callbacks under an id.
- **`isReadyToRecompute()`** — already uncalled.
- **`validatePoint` and its three reasons** — replaced by the engine-internal repair, and the reasons fold into the one set with a producer.
- **`progress` and `offers` as ambient flows** — replaced by the id-carrying callback; the run stays visible until something functional disposes of it, and the two existing keys `route.line.transparencyPct` and `route.dimmed.transparencyPct` already carry the high and the low, so no new drawing value is needed.
- **The flow's own read of the candidate configuration** — it moves into the engine, keys and all.
- **Parked:** what the answer says about where the line really ends; the dismissal of a route; any parameters object on the seam, now the engine's alone.

## 7. Assessment

- **The repair is the cheap half and its budget is to be proved, not assumed** — the worst case is ~72 water tests against a 50 ms ceiling, and the plan's §6 step will pin that with a test; until then the number is a target.
- **The seam change is mostly deletion and re-keying**: the pieces that stay — the stage set, the line, the reasons, the crossings array — already exist, and the new shape is `routesToCompute` plus a flow of update values; the heavy work is the flow's, not the engine's.
- **The heavy half is the flow's UI**: pages and their walk, the inventory, the comparison line, and the two-opacity painting. The pages can start from today's candidate rows, which already carry next/previous and a saving line, so the risk there is naming and wiring rather than layout.
- **The highest-risk piece is the removal of the readiness gate**, which drags the toggle's disabled face, the tap that retried preparation and the map's transient status line with it; that half must be one build step or the mode can spin on a dead engine.
- **The second risk is the two additions the plan makes to the user's shape** — the callback's fifth field and the sealed `RouteDeclarations` return — both exist only to say a refusal, and everything downstream reads them.

## 8. Build order

1. **The seam** — `RouteComputation` (with its id), `RouteLookupId`, `RouteDeclarations`, the reason set, the callback value with its five fields and the three members; the retired members deleted in the same hop, so the tree never holds two contracts.
2. **The dummy** — declares one computation with no parameters, answers in one callback whose `nextStageOrNull` is null, and refuses an acquisition whose end it cannot repair.
3. **The avoid engine** — repairs the pair inside `routesToCompute`, declares its computations from the candidate configuration it now reads itself, mints the computation ids there, runs a started computation and publishes the stage pair and the line as each stage finishes, and stops the job it owns on `cancelLookup`.
4. **The harness** — [`RouteEngineChoice.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngineChoice.kt) moves with the seam in the same hop as step 1, because its factory constructs the engines and its signature changes the moment the interface does.
5. **The view model** — `arm(ends)` reads the pair → `routesToCompute(origin, destination)` → on `Refused` shows the reason, on `Available` keeps the set, starts the main and later the rest; the lookup registry by id, the page collector, the inventory and the comparison arithmetic land, and the arm/ask pair, the worker and the two ambient flows go.
6. **The screen, the host and the panel** — one callback collector paints the two opacities, the pages take the next/previous walk inherited from today's candidate rows, the panel's top area is rebuilt as §5 states, and the toggle loses its readiness gate in the same step that removes its disabled face.
7. **The tests** — a foreign engine driven through `routesToCompute` → `startLookup` → callbacks → `cancel`, a repair case, a repair-failure case with the reason, a search-failure case with the callback's reason, and a case where an update for a cancelled id changes nothing; the repair test proves the 50 ms budget.

## 9. Review — the plan against itself

- **The sealed return and the callback's fifth field are approved** — the user's word of 2026-09-29 — so both additions stand and the review's first finding is closed.
- **The comment leaving the panel's top is still the plan's own proposal**, the one user-visible change it makes without the word.
- **The reason set is closed** — `CANNOT_REPAIR` · `WORLD_NOT_READY` · `NO_PATH` · `OFF_WATER`, the repair's two at `routesToCompute` and the search's two on the callback, each with a producer.
- **The 50 ms budget is an acceptance with no harness today**, and the plan now says so rather than claiming it is met.
- **The disposal sites are enumerated in §10**, and the acceptance still has to prove each one calls `cancelLookup`.

## 10. Conversion of the current flow, hop by hop

Each hop is a build gate — `apk-build.bat` green and the route-filtered suites green before the next one; two consecutive failures halt. Hops 1–5 land as **one change**, because the tree must never hold two contracts, and they are split here only for review.

### Hop 1 — the seam, the harness and its two engines (atomic)

- In [`RouteEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngine.kt:42): delete `state`, `prepare`, `validatePoint`, `onOriginPositionChanged`, `onDestinationPositionChanged` and `isReadyToRecompute`, with the types `RouteEngineState` and `RouteUnavailableReason`; the refusal reasons fold into the one set with a producer.
- Add `RouteComputation`, `RouteLookupId`, `RouteDeclarations`, the reason set, and the update value carrying **id · stage just finished · next stage or null · line so far · reason or null**; the interface becomes `routesToCompute(origin, destination)`, `startLookup(computationId)`, `cancelLookup(id)` and one `Flow` of update values.
- [`RouteEngineChoice.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngineChoice.kt) moves in the same hop, its factory taking the new interface.
- [`RouteDummyEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteDummyEngine.kt:65): declares one computation, refuses an acquisition it cannot repair, answers in one callback whose next stage is null.
- [`RouteAvoidEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:163): reads the candidate configuration itself, repairs the pair inside `routesToCompute`, declares its computations with their ids, publishes the stage pair and the line as each stage finishes, and stops the job it owns on `cancelLookup`; the old `_progress`/`_offers`/`offersScope`/`offersJob` machinery leaves with the two flows.

### Hop 2 — the view model

- [`RouteViewModel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:489): delete the arm/ask pair, the `prepareCurrent`/`prepareAgain` path, the `askJob` worker, `anchorTold`, and the `_progress`/`_offers` proxies.
- Add: `arm(ends)` reading the pair and calling `routesToCompute`, the lookup registry keyed by id, the collector that turns updates into the page set, the selected index, the inventory flow, and the comparison arithmetic against the selected route.
- The saved predicate and the session link stay; the disposal function is the one thing that calls `cancelLookup`.

### Hop 3 — screen, host and panel

- [`MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:654): delete the engine-readiness wiring, the `routeProgress`/`routeStage` collection, the tap that retried preparation, and the two panel call sites' progress argument; collect the update flow once and hand the page set down; the three exit doors and the fan's unasked Discard call the disposal function; the anchor's lead reading stays.
- [`RouteHost.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt:38): delete `ROUTE_TARGET_TITLE`, the refused crosshair branch and its beat loop, and the `refused` flag; keep the pool, the pin and the provisional line, now driven by the update's line.
- [`RouteConfirmPanel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:102): the page set replaces `candidates`, the selected page replaces `selectedIndex`, and the top area is rebuilt per §5 — title and state word stay, the comment leaves, the comparison takes its place.
- [`RouteOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:54): `routeAutoPickReady` reads the new state, `routeCandidateLines` merges into the page set, and `routeAnchorLead` stays — the ends are still read at the press.

### Hop 4 — the disposal sweep (everything retired)

| Retired | Disposed of as |
|---|---|
| `state` · `prepare` · `validatePoint` · `isReadyToRecompute` · `onOriginPositionChanged` · `onDestinationPositionChanged` | deleted from the seam |
| `RouteEngineState` · `RouteUnavailableReason` · `RouteRefusalReason`'s three variants | deleted; folded into the one reason set with a producer |
| `RouteEngine.progress` · `RouteEngine.offers` | replaced by the one update flow |
| `RouteViewModel._progress` · `_offers` · `askJob` · `anchorTold` · `prepareCurrent` · `prepareAgain` | deleted |
| `MapScreen`'s readiness gate, the prepared-tap, `routeProgress`/`routeStage` | deleted |
| `RouteHost`'s `ROUTE_TARGET_*` constants, the `refused` flag, the crosshair paint and its beat | deleted |
| `maro.properties` `route.target.color` · `route.target.widthDp` · `route.target.pulseMs` and the `AppConfig` accessors with their parse block | deleted; `route.repair.maxRadiusM=200` added |
| the refusal strings both locales only the crosshair read | deleted; the repair-failure string added in both locales |
| the flow's own read of `route.avoid.candidate.*` | deleted; the engine reads them |

### Hop 5 — pages, inventory and comparison (the net-new half)

- The page set — one page per started lookup — on the walk the current candidate rows already own, the selected page as the buttons' target, the inventory flow, the comparison line at the panel's top, and the two-opacity painting already wired in Hop 3.

### Acceptance

- The four disposal sites — the exit dialog's discard, the fan's unasked Discard, a new arming and a selection — each call `cancelLookup`, and a test asserts an update for a cancelled id changes no page.
- The repair's own test proves the 50 ms budget rather than asserting it.
- `apk-build.bat` green and the whole unit suite green after each hop; the device pass stays the user's, and no part of this plan ships without it.

## 11. Implementation detail — the concrete types and the code they replace

### The types

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
    val stageDone: RouteStage?,       // null for an engine with no stage to report
    val nextStage: RouteStage?,       // null = the job is done
    val line: List<RoutePoint>,       // the line computed so far, for the flow to paint
    val result: RouteResult.Success?, // null until done
    val reason: RouteReason?          // null on success
)

interface RouteEngine {
    fun routesToCompute(origin: RoutePoint, destination: RoutePoint): RouteDeclarations
    fun startLookup(computationId: RouteId): RouteId
    fun cancelLookup(id: RouteId)
    val updates: Flow<RouteUpdate>
}
```

- All three calls are plain, not `suspend`: `routesToCompute` is the repair plus a local configuration read, `startLookup` launches the job and returns, and `cancelLookup` cancels the job.
- The updates are one per-engine `MutableSharedFlow<RouteUpdate>`; the compute jobs run on an engine-owned scope and the id is the only correlation the flow reads.
- `RouteResult.Success` keeps its points, leg times, distance, duration and forced crossings; its `offers` field retires with the ambient offers flow, the candidates being lookups of their own now.

### The repair replaces a branch that already exists

- [`RouteAvoidEngine.routeBetween`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:293) already checks both ends with `world.isWater` and returns `RouteResult.OutsideWater`. The repair **takes that branch's place**: `routesToCompute` repairs each end by the sweep, a failed repair answers `Refused(CANNOT_REPAIR)` — or `Refused(WORLD_NOT_READY)` when the layers the judgement needs are absent — and a repaired pair proceeds to the declarations.

### The avoid engine's three computations

- The settled computation runs the default parameters and is declared **first**, as the main; one computation follows per `route.avoid.candidate.passes` entry, each carrying that pass's own dropped sources. The candidate computations reuse the body of today's `offers(...)` pass ([`RouteAvoidEngine.kt:1057`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:1057)) under their own id, where today they run on the one `offersJob`.
- The three `descriptionResId` strings are added in both locales; their exact wording is the user's.

### The dummy's single answer

- `routesToCompute` returns `Available` with one computation; `startLookup` emits one update whose `nextStage` is null, `stageDone` is null — no stage exists to report — and `result` carries the straight line.

### The harness

- [`RouteEngineChoice.factory`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngineChoice.kt:26) keeps its three providers — the pace, the slow-water budget and the world — and only the interface it returns changes, so the dropdown and the persisted id are untouched.

### What still needs a value before the build

- The three avoid descriptions' wording, the four `RouteReason` labels' wording, and their strings in both locales.
