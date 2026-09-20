# Context Hydration — Route — 2026-09-20

**Last Bake:** 2026-09-20 19:30 UTC — written by the session that closed the engine seam's review, and the bake ran with it: the epic's shipped todos are folded into its `## Implemented` (`route-search` keeps its one open Phase 2 item), both plans that were in design are in its `## Docs`, its Key Files and Placement now name the seam's files, the feature summary moved, and the focus stack was pruned to ten

**Directive trace:** no covered action stopped in this session — no dependency was added without the ask, no machine-shaped data file was opened (the mesh `.bin` was read only through `RouteMeshSerializer`, the probe's own reader), no work began without an explicit order, the device was never touched, and every claim about the code came from a file read. Two orders were followed literally where a shortcut was tempting: the arrays were **not** made per-search (the claim was corrected, the scheduler left alone), and the band mark and the crossing walk were **reported, not fixed**.

## State

**Implemented, on `feature/route`, uncommitted.** The feature shipped end to end on 2026-09-20 (engine, UI, save, seams — 518 tests at that point), and the same day's later sessions took it through the trajectory-quality work and then the engine seam; this session closed the `#implement` pipeline's review of the seam with **all six of its should-fixes**, and the suite is green at **572 tests** with `assembleDebug` green. The full delivery histories live in the epic's `## Implemented`; this file carries the state a cold session needs.

- **The engine seam is real now, and injectable.** `RouteEngine` is the one contract; `MeshRouteEngine` is its only implementation and the only file that knows the mesh exists; `RouteViewModel` takes an engine **as a constructor parameter** and `MapScreen` builds the shipped one once at its own composition point from the coastline and regulation instances it already holds. A second engine is therefore one expression at one site, and a test can hand in a foreign one — `RouteEngineSeamTest` (6 tests) drives a straight-line fake through `beginDraft` → `preview` → `confirm` → `recompute`, through the readiness retry, and reads the plan, the trip figure and the save off the feature rather than off the fake.
- **`Ready` promises answering, not coverage.** The mesh engine's `prepare()` loads the mesh and the mesh alone; the coastline and regulation load per search, so the first route of a session can be priced as open water and no zone. That is now written in `RouteEngine.prepare()`'s KDoc, which is where a second engine must read it.
- **Both loads are single-flight.** `RouteMeshRepository.loadIfNeeded()` and `RouteSpatialAdapter.loadIfNeeded()` each hold a `Mutex` with a re-read under it, so the initial preparation and the readiness retry cannot decode the mesh or build an index twice.
- **The cancellation claim was corrected, not the scheduler.** `RouteViewModel` cancels a search and does not join it; the mesh engine's and the search's KDocs now say *one search is **started** at a time, never one **running***, and name why the shared scratch arrays are safe for the run that owns them.
- **One rule, one home in the tests.** `app/src/test/.../spatial/mesh/RouteMeshDetailsReadings.kt` states "no dossier reads as all zeros" once and offers the strict `meshDetailsRead` beside it, so a test that means to count can no longer pass on a missing dossier.
- **Open, recorded in the seam plan's own list of what the contract owes** (`260920_FEAT_PLN_Route_engine-seam-and-parking.md` §3, couplings 5–6): the **band mark** is derived from the mesh's own `edgeInBand` — and nothing in `ui/` reads it, it reaches `RoutePlan.inBand` and stops — and the **forced-crossing report** is a crossing walk over the mesh's chain, including the "every leg walked in at least two halved intervals" correction the confirm panel's truthful line depends on. A second engine re-implements both.
- **Still owed from earlier sessions:** the on-device pass over the aim, the fling cancellation, the confirmation panel and the saved course; and the trajectory study's §13/§14 items (the berth's remaining structural choice, and the drawn clock's remaining `inBand` reader, if one is ever wanted).
- **Parked, deliberately:** the taut-string alternative — documented in full at `260920_FEAT_PLN_Route_taunt_algorithm-design.md`, with no code from it in the tree.

## Target Files

- `xTrack/Route/FEAT_DSC_Route.md` — the epic: Description, four sections with their rules, the two closed walks, `## Docs` and `## Implemented`
- `xTrack/Route/260920_FEAT_PLN_Route_engine-seam-and-parking.md` — the seam plan: what the seam is, the couplings, the eight steps, and **(§3, couplings 5–6) what the contract still owes**; step 7 is the comparison instrument, step 8 the un-parking
- `xTrack/Route/260920_FEAT_PLN_Route_taunt_algorithm-design.md` — the parked alternative, with its pre-registered comparison (§14.5) and its §13 Stage 0 entry point
- `xTrack/Route/260920_FEAT_PLN_Route_trajectory-quality.md` — the trajectory study, its four rounds as run and its readings
- `app/src/main/java/ykws/android/maro/spatial/RouteEngine.kt` · `spatial/mesh/MeshRouteEngine.kt` — the seam and its incumbent
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — the engine is its constructor's parameter
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the one composition point that builds the shipped engine
- `app/src/test/java/ykws/android/maro/ui/map/RouteEngineSeamTest.kt` — the seam driven through the feature by a foreign engine

## Next Step

**The taut-string engine, un-parked** — the user's stated next step, on 2026-09-20. The entry point is the seam plan's step 8 and the taut design's own §13 Stage 0, and the acceptance case is already named there (Baie des Milliardaires → Port de la Salis). The instrument that makes the comparison a reading rather than a preference is the seam plan's step 7 — `RouteTrajectoryProbeTest` already measures **any** `RouteEngine` on the same pairs with the six metrics, the wall clock and `nodesExpanded`, so the two engines can be compared the day the second one exists. Two things the next session inherits and must not have to rediscover: the engine is a **parameter** (`RouteViewModel(engine)`), and the **two couplings above** are what the second engine still owes.
