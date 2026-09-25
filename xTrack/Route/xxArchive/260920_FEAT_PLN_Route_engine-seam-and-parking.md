<!-- scope: feature -->
# Route — the engine seam, and parking the taut-string alternative

> **Digest floor — superseded 2026-09-22.** The seam this file argued for **survived and is what ships**
> ([`RouteEngine`](../../../app/src/main/java/ykws/android/maro/spatial/RouteEngine.kt)), while both engines it
> was written for are **removed**: the mesh engine it kept as the baseline and the parked tracer alike. The
> placeholder that now fills the seam is [`RouteDummyEngine`](../../../app/src/main/java/ykws/android/maro/spatial/RouteDummyEngine.kt),
> and the removal, with the field-by-field ruling on what the seam kept, is
> [`260922_FEAT_PLN_Route_dummy-engine-and-engine-removal.md`](../260922_FEAT_PLN_Route_dummy-engine-and-engine-removal.md).
> Nothing in this file is pending, and the paths it backticks name files the tree no longer carries.

**Date:** 2026-09-20 · **Branch:** `feature/route` · **Status:** steps 1–7 shipped 2026-09-20 (the seam, its review closed, and the instrument now able to measure any engine); **step 8 taken 2026-09-20** — the word came: the mesh engine is **deactivated** and the taut engine becomes **the replacement**, its first cut the drawn line. §3's couplings 5–6 go with the deactivated engine, and step 7's instrument, its columns and every comparison reading are **parked until the user says otherwise**
**Asked for:** abstract as much of the incumbent engine as possible so another one can replace it — the taut-string design of [`260920_FEAT_PLN_Route_taunt_algorithm-design.md`](260920_FEAT_PLN_Route_taunt_algorithm-design.md) being the candidate — while keeping the concept hydrated and documented, and parking that alternative for now.

## 1. What parking means here, in four lines

- The taut-string design stays a **documented alternative**, complete as it is, with no code from it and no engine decision taken.
- Its comparison against the incumbent is **pre-registered**, not forgotten: §14.5 of that file names the pairs and the six metrics, and step 7 below makes the instrument that would run it.
- The incumbent keeps working and keeps being maintained; un-parking is a decision to *add* an engine, never to break one.
- **Parking is not a decision to stop improving the incumbent** — the trajectory work continues underneath, and §14's four bypasses are still the incumbent's own debt.

## 2. The seam as it already is (verified by reading, not assumed)

Two thirds of the interface exists, which is why this plan is short rather than a rewrite.

| Existing piece | Home | What it already hides |
|---|---|---|
| [`RoutePointQueries`](../../app/src/main/java/ykws/android/maro/spatial/RoutePointQueries.kt:20) | `spatial/` | the whole world the search reads: `isWater`, `pricedZoneAt` (one answer carrying a zone's identity, its name and its limit), `distanceToZoneM`, `coastalBandSpeedLimitKn`. `RouteSpatialAdapter` is its only implementation and the only file allowed to import the coastline and regulation types |
| [`RouteResult`](../../app/src/main/java/ykws/android/maro/data/model/RouteResult.kt:10) | `data/model/` | a named failure rather than an empty success, and a success carrying points, per-leg times, length, duration and the drawn-versus-priced pair |
| [`RoutePlanTiming`](../../app/src/main/java/ykws/android/maro/spatial/RoutePlanTiming.kt:1) | `spatial/` | the drawn clock: what the line on the screen costs at the limits in force |
| `RoutePlan` (UI) | `ui/map/` | the reminder of a leg, the pace, the trip figure |
| `RouteViewModel` · `RouteHost` · `RouteOverlay` · `RouteConfirmPanel` | `ui/map/` | everything the user sees, already written against `RouteResult` and `RoutePlan` rather than against the search |

## 3. The seam as it should be — the couplings, and what the contract still owes

1. **The mesh gates the feature.** [`MapScreen.kt:584`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:584) reads `val routeAvailable = routeMeshState is RouteMeshState.Ready`, and [`RouteViewModel`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:198) exposes `meshState` for it. An engine needing no mesh would have to fake a mesh-ready state to be allowed to run — the single worst coupling in the feature.
2. **The mesh's vocabulary fills the shared result.** `RouteResult.Success` carries `nodeIndices`, `pricedSec`, `verticesBeforeShortcut`, `smoothedVertices`, `reducedVertices`, `sharpVertices`, `sharpNoRoom/NoWater/NoGeometry/NoPrice`, `largestTurnRadiusM` and `rawChainFallback`. Every one of those is a *mesh-engine* reading — a visibility graph has no node indices, no separate chain to price against, and no fillet to count. They are good instrumentation, in the wrong dossier.
3. **The search is a class the UI reaches through two more classes** (`RouteMeshRepository` → `RouteSearch`, plus the adapter). There is no interface with a single `route(start, aim)` on it, so there is nothing to implement twice.
4. **The drawn clock inherits its limits from the mesh.** [`drawnLegSeconds()`](../../app/src/main/java/ykws/android/maro/spatial/RoutePlanTiming.kt:52) takes each sub-leg's limit from the **chain edge it was cut from**. That is correct and exact for a mesh-engine chain, and meaningless to an engine whose path is not a chain of edges. It is the one *algorithmic* coupling, and it is small: the engine should hand each leg its limit, and the clock should stop knowing where the limit came from.

**Two more, recorded when the seam's delivery was reviewed and left unfixed (2026-09-20).** Both are *requirements* the feature already meets, derived inside the mesh path, so a second engine inherits the derivation and not merely the field:

5. **The band mark is read off the mesh's own edge flag.** `RouteResult.Success.inBand` comes from [`mesh.edgeInBand`](../../app/src/main/java/ykws/android/maro/spatial/mesh/RouteSearch.kt:836) walked along the chain the search itself took, and no reader in `ui/` reads the flag at all: it reaches `RoutePlan.inBand` (copied at [`RouteViewModel`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:105)) and stops there. A second engine must derive the mark for itself, and nothing on screen would notice if it did not.
6. **The forced-crossing report is a crossing walk over the chain.** `RouteResult.Success.forcedCrossingZoneNames` is produced by the walk at [`RouteSearch.crossingZoneNames`](../../app/src/main/java/ykws/android/maro/spatial/mesh/RouteSearch.kt:1174), including the correction that **every leg is walked in at least two halved intervals**, which is what the confirm panel's truthful crossing line depends on — `RouteConfirmPanel` and `DashboardPanel` are its two readers. A second engine re-implements the walk, not just the field.

## 4. The work — eight steps, each shippable on its own

Every step is a **no-op refactor**: the acceptance for all of them is the same, and it is the incumbent's own instrument.

> **Acceptance, identical for steps 1–6:** `apk-build.bat` green, the full suite green, and `RouteTrajectoryProbeTest` printing the same five pairs with the same vertices, turning, worst corner, clearance, length and ETA, and `rawChainFallback` still **0 of 8**. A step that moves a number is not a refactor and comes back as a finding.

1. **Name the contract.** A `RouteEngine` interface in `spatial/`: a readiness state (`NotReady` · `Ready` · `Unavailable(reason)`) and one cancellation-aware `route(start, aim, …) : RouteResult`. Its only implementation now is `MeshRouteEngine`, delegating to today's `RouteMeshRepository`, `RouteSearch` and `RouteSpatialAdapter` — three files behind one door, no behaviour change.
2. **Split the result by ownership.** Keep the engine-neutral fields on `RouteResult.Success` and move the mesh-engine readings into a details object the mesh engine owns. Then check the UI reads **none** of the moved fields — the trip figure and the panel read points, leg times and distance, and if anything else leans on `pricedSec` or a sharp-corner count, that is a finding, not a transport.
3. **Un-gate the feature from the mesh.** `MapScreen` and `RouteViewModel` switch from `RouteMeshState` to the engine's readiness; `Missing(assetPath)` becomes `Unavailable(reason)`, and the reason's user-facing text belongs to the engine that produced it, behind a `@StringRes` id in both locales.
4. **Make the bake a private matter of the mesh engine.** The asset's absence stops being "the feature is unavailable" and becomes a reason only `MeshRouteEngine` can give. This is what lets a bake-free engine run beside it.
5. **Sort the properties by ownership.** One home each in `maro.properties` behind an `AppConfig` accessor, split into engine-neutral (`route.cruiseSpeedKn`, the comfort cap, `route.standoffM`, the line's appearance, the pin) and mesh-only (the berth, any bake tuning). **One duplicate must be resolved here:** the comfort cap exists twice under two names in two units — `route.turn.lateralAccelMps2` and the taut design's `route.maxLateralG` — and a second engine reading the other spelling would be the fifth instance of the same wound. Pick one unit, one key, one accessor.
6. **Invert the drawn clock's limit supply.** `drawnLegSeconds(points, limitsPerLeg)`, with the *engine* producing `limitsPerLeg`; the mesh engine keeps producing them from its chain edges, and nothing else changes. Parts 1–6 are then a complete seam.
7. **Turn the probe into the comparison instrument — the abstraction's whole payoff.** `RouteTrajectoryProbeTest` already derives its inshore pairs by rule and prints the six metrics; lift that block so it can measure **any** `RouteEngine` on the same pairs — adding nodes expanded and milliseconds, which the incumbent's 127.9 ms worst case already gives it a figure for. Without this step the abstraction buys a swap nobody can justify; with it, §14.5's comparison is a test run rather than a project.
8. **Un-parking — taken 2026-09-20.** The taut engine as a second `RouteEngine`, the incumbent untouched, both measured by step 7's instrument; the first cut is scoped to **producing the drawn line**, the mesh engine being retired as the design of record and kept as the baseline. Its own §13 Stage 0 remains the entry point, and its acceptance case is already named: Baie des Milliardaires → Port de la Salis.

## 5. What this plan deliberately does not do

- **No code from the taut design** — still true, nothing from it is built — and the engine decision, which this line once left open, was **taken on 2026-09-20**: the mesh engine is retired as the design of record, and the tracer is what replaces its tracing.
- **No deletion.** The mesh, its bake, its asset, `tools/bake-route.bat` and `apk-bake.bat`'s `:do_route` step all stay exactly as they are; the taut design's §13.2 drops them only *if* it is built.
- **No fix to the line on the water.** Un-parking and fixing the incumbent compete for the same cycles, and that sequencing is the user's; this plan neither assumes nor forecloses it.
- **No UI redesign.** Steps 1–6 change what the UI *reads*, never what it shows.

## 6. What un-parks it, stated in advance

- **The device keeps rejecting the incumbent** — the line still not going around the zones, or still not reading as one straight passage — after the current round's fixes.
- **Or the mesh's upkeep becomes the constraint**: a rebake owed for every regulation or depth change, on a device cycle, is the cost the taut design removes entirely.
- **Or step 7's numbers say so**, once the instrument exists and the alternative has been run on the same five pairs.

None of the three is a reason to start now, and none is a reason to forget: the trigger is written here so the decision does not have to be re-derived later.

## 7. The strongest objection to this plan

- **It is speculative generality** — building a seam for a second implementation that may never exist, and the classic cost of that is abstraction nobody exercises. The honest defence is that six of the eight steps are debt the incumbent already owes: the mesh gates the feature, the mesh's counters fill the shared result, the bake's absence disables the feature, the drawn clock reaches into the chain, and the comfort cap is spelled twice in two units. Those are defects whether or not a second engine ever arrives.
- The one genuinely speculative piece is `RouteEngine` itself: an interface with a single implementation. That is a small and honest bet, and **the second engine is not needed to keep it honest — step 7 is**, because a seam nobody measures through is a seam nobody can trust.
- **The second objection, and the one that would sink it:** if the incumbent's line is fixed on the water before step 7 lands, the seam will have bought nothing but tidiness, and the tidy answer is to do the debt-paying steps and leave `RouteEngine` unbuilt. That is a legitimate outcome of this plan, not a failure of it.

## 8. Documentation and hydration — what keeps the concept alive

- This file and [`260920_FEAT_PLN_Route_taunt_algorithm-design.md`](260920_FEAT_PLN_Route_taunt_algorithm-design.md) (with its §14 comparison) are the parked concept's homes; nothing here restates their content.
- The epic needs three small edits when the word comes: its **Description** states the engine as a component behind a seam rather than as a prebaked mesh; its **Rules** gain the seam and the parked alternative with its trigger; its **Key Files** name `RouteEngine` and `MeshRouteEngine` in place of the repository and the search as the entry points; its **Docs** gains a pointer to this file.
- Its hydration should then carry the parked state in the `#bake`'s own words — the plan in design, the alternative parked, the trigger — rather than leaving the session to rediscover it.
