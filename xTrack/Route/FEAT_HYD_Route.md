# Context Hydration — Route — 2026-09-29

**Last Bake:** 2026-09-29 20:55 UTC — written by `#bake`; absence means never baked

**Directive trace:** A state-and-design session run on the user's own commands — `#status`, `#doc update` reframed as an epic rewrite, the engine-interface review with its passes, and `#bake` — plus the explicit "update doc", "extend plan", "review plan" and "update plan" orders. No dependency was added, no machine-shaped data file was opened, and the device was never touched; every claim about the code (the seam's members, the repair branch in `routeBetween`, the harness's factory providers) followed the file read that settled it.

## State

**The branch is `feature/route-apis`**; **nothing is built on it** — the session's changes are state and design alone.

**The epic was rewritten as a state doc** — concept · current code · delta, with the requirement list called history, no rule restated and no requirement number repeated; its `## Implemented` is now a one-line-per-pass pointer index, and its history was dropped outright on the user's word.

**The engine's interface was reworked, in design** — [`260929_FEAT_PLN_Route_engine-interface-rework.md`](260929_FEAT_PLN_Route_engine-interface-rework.md:1) carries the whole of it: `routesToCompute(origin, destination)` repairs the pair as its first step and returns the declared computations, `startLookup(id)` needs only the id, `cancelLookup(id)` is the only disposal an engine performs, and one update flow carries the id, the stage done, the next stage, the line, the result and the reason. The reason set is closed at `CANNOT_REPAIR` · `WORLD_NOT_READY` · `NO_PATH` · `OFF_WATER`; the repair is an 8-direction ring sweep to `route.repair.maxRadiusM=200` under 50 ms; the dismissal is parked; the two additions (the callback's fifth field and the sealed `RouteDeclarations`) and the comment leaving the panel's top are approved.

**The walk is open** on its top Level 1, cursor **16**; the session did not move it. An open level blocks the fold and any `#archive`.

**The Feature Summaries Route row stays beyond the read cap** — the bake's step 3 could not run, and the row is the standing global todo's subject.

## Target Files

- [`260929_FEAT_PLN_Route_engine-interface-rework.md`](260929_FEAT_PLN_Route_engine-interface-rework.md:1) — the session's one design, in design, nothing built
- [`FEAT_DSC_Route.md`](FEAT_DSC_Route.md:1) — rewritten as a state doc this session
- The code the plan will touch when it is built: `RouteEngine.kt` · `RouteDummyEngine.kt` · `RouteAvoidEngine.kt` · `RouteEngineChoice.kt` · `RouteViewModel.kt` · `MapScreen.kt` · `RouteHost.kt` · `RouteConfirmPanel.kt` · `RouteOverlay.kt`

## Next Step

Word the three avoid descriptions and the four reason labels in both locales, add the repair's own test that proves the 50 ms budget, then build the hops of the plan's §10 — each a build gate, the device pass staying the user's.
