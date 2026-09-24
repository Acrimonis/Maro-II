# Context Hydration — Route — 2026-09-24

**Last Bake:** 2026-09-24 13:20 UTC — written by `#bake`

**Directive trace:** no covered action stopped since the last bake — no dependency was added, no machine-shaped data file was opened, the device was never touched, and every claim about the code came from a file read or an Ask hop's output. Two delegations are named: the two Ask reviews ran through `new_task(ask)` subtasks, and the plan edits landed in Architect (`.md`-only).

## State

The avoid engine's stage-1 design is decided end to end and implementation-ready — nothing is coded yet. The shape: a corridor-bounded grid A* plus a clearance taut pull, three stages over one rasterizer (avoid land, then the 300 m band, then regulated speed zones); the seam (`RouteEngine`) and the shipped harness stay as they are until the work lands. Two Ask reviews passed: a feasibility review (feasible-with-named-changes), then an implementation-grade review that grounded the ≤ 500 ms budget on the Lérins-to-Salis corridor (~120–250 ms realistic, ~350–450 ms pessimistic) and settled the five ambiguities. The walk then closed by decision — all nine items settled: the four keys locked at 25 / 50 / 1852 / 25 with the ≤ 500 ms budget, the land-ring classification as one read-only orientation query on the index (option A), and the grid, rasterizer, A*, pull, seam mapping, harness edit, tests and measurement all specified. The build is owed to implementation.

## Target Files

- `xTrack/Route/260924_FEAT_PLN_Route_avoid-land-stage.md` — the stage-1 plan, decided and implementation-ready
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — the engine to rewrite in stage 1
- `app/src/main/java/ykws/android/maro/spatial/RouteEngine.kt` · `RouteEngineChoice.kt` — the seam (untouched) and the factory to widen
- `app/src/main/java/ykws/android/maro/spatial/avoid/` — the new package (world, grid, search, pull)
- `app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt` — the read-only land-ring orientation query
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the world-provider wiring

## Next Step

Implement stage 1 per the plan — the `#implement` pipeline.
