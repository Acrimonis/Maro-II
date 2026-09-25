# Context Hydration — Route — 2026-09-25

**Last Bake:** 2026-09-25 19:41 UTC — written by `#bake`

**Directive trace:** no covered action stopped since the last bake — no dependency was added, no machine-shaped data file was opened, the device was never touched, and every claim about the code came from a file read or a hop's own output. Delegations are named: the property, accessor and test hop ran through `new_task(code)`, while the plan and feature-file edits landed in Architect (`.md`-only).

## State

Stage 1 (avoid land, islands and hazard rings) is shipped, and so are the avoid engine's phases 2–4 — the 3 m depth gate, the 300 m band and the priced speed zones, each behind its own switch, the band chorded by its tangent look-ahead, the zones shipping disarmed. **Change 4's fine cell is settled and inert**: `route.avoid.fine.cellRatio=0.40` — 40 % of `route.avoid.grid.cellM`, hence 20 m at today's 50 m — was added to `maro.properties` with its `AppConfig` accessor, a clamp and a test pinning it, and **nothing reads it until the fine band is built**. The refinement's footprint is the coarse path's own cells plus one ring, about 150 m at 50 m cells, against the ~1 km band the plan's original budget assumed; the budget was re-priced at 20 m (~20 000 cells, ~23–125 ms). The fine band's **width** stays a starting value, and the user's call still stands between shipping the fine band and correcting the snap in place. Remaining: Change 4's mechanism, then markers (phase 5) and curves (phase 6).

## Target Files

- `xTrack/Route/260924_FEAT_PLN_Route_avoid-soft-sources-and-curves.md` — the canonical phases 2–6 plan, carrying the amendments (the fine-cell ratio among them), the challenge findings and the normalized keys
- `xTrack/Route/260925_FEAT_PLN_Route_avoid-switches-and-zone-tangent.md` — the follow-on plan: Changes 1–3 shipped, Change 4 in design with its cell size settled at 40 %
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` · `spatial/avoid/` (world, grid, search, pull, TangentCorners, RouteCostField, RouteEta, ZoneGeometry) — the shipped internals
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` · `app/src/main/assets/maro.properties` — the keys, `route.avoid.fine.cellRatio` among them
- `app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt` — the avoid family's own pins, the fine ratio's included

## Next Step

Implement Change 4 — coarse-to-fine precision: the fine band over the coarse path's cells plus one ring, its cell read from `route.avoid.fine.cellRatio`, so the line hugs the curved band instead of long chords.
