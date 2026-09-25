# Context Hydration — Route — 2026-09-25

**Last Bake:** 2026-09-25 13:40 UTC — written by `#bake`

**Directive trace:** no covered action stopped since the last bake — no dependency was added, no machine-shaped data file was opened, the device was never touched, and every claim about the code came from a file read or an Ask hop's output. Delegations are named: the plan reviews and the challenge ran through `new_task(ask)` subtasks, and the plan and feature-file edits landed in Architect (`.md`-only).

## State

Stage 1 (avoid land, islands and hazard rings) is shipped. A 2026-09-24 21:00 session opened phases 2–6 and shipped its first two steps — the unified cost field (`RouteCostSource` HARD walls / SOFT prices, one `RouteCostField.evaluate`) and the 3 m depth gate (`route.avoid.minDepthM`, `DEPTH_NOT_LOADED`) — settled the taut pull as grid A\* + verified corner snap, and closed the main-thread ANR with `withContext(Dispatchers.Default)`; phases 3–6 (the band, the zones, the markers, the curves) stay designed and unbuilt. This session then designed four changes and folded them as the plan's `## Amendments`: an on/off for the depth gate and for the 300 m zone, the band's tangent look-ahead, and coarse-to-fine precision; it reconciled the two plans and ran three independent reviews plus one adversarial challenge, every correction folded. The build of the four changes is owed.

## Target Files

- `xTrack/Route/260924_FEAT_PLN_Route_avoid-soft-sources-and-curves.md` — the canonical phases 2–6 plan, now carrying the `## Amendments` and the challenge findings
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` · `spatial/avoid/` (world, grid, search, pull, TangentCorners, RouteCostField) — the shipped internals
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` · `app/src/main/assets/maro.properties` — the keys

## Next Step

Implement the four amendments on the shipped cost field and depth gate.
