# Context Hydration — Route — 2026-10-21

**Last Bake:** 2026-10-21 18:58 UTC — written by `#bake` on `feature/route-not-found` (cut from `origin/develop` by the user's `#new route-not-found`).

**Directive trace:** the device was read on the user's word (`adb logcat -d`, after "grab the logcat again" and "grab the log") and never written to, deployed or launched. The user's `#impl` stood behind the code change, and `#bake` / `#commit` / `#push` were their own invocations. No dependency was added; no baked spatial file was opened (the saved logs are text); and every claim about the code came from a file read.

## State

**A destination in open water found no route because the engine read it on the fine layer, where the ribbon has no water (2026-10-21).** The device trace read `aimLayer=1 aimOpen=0`: the aim anchored on a one-cell fine island. The fine grid is the coastal ribbon only (`gateDistanceM <= depthCollarM`, 170 m); the aim at 191 m off the coast stands beyond it, so its fine cell is LAND. The fine-window loop force-freed that cell anyway, manufacturing a passable island, and `endCellAt` anchored the aim on it; the passable-fine-cell-supersedes-coarse rule then hid the reachable coarse cell, so the search answered `NO_PATH` on every rung while a point 80 m away (111 m off the coast, in the ribbon) routed fine. **Fixed:** the fine-window opening is layer-scoped — a fine cell opens only where the ribbon already has water, so `endCellAt` falls back to the coarse cell; layer 0 keeps its unconditional shore-margin free. The `PASS … NO PATH` line now prints `aimLayer`/`aimOpen`. Two `FinePriorityWalkTest` pins (beyond-ribbon → coarse, inside-ribbon → fine), the full unit suite and `assembleDebug` are green.

**Open:** the device pass on the La Salis → Enfer de Dante pair, and the fine pass's own `forceFree` at `RouteFinePass.kt:212`.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt` — the layer-scoped fine-window end opening
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassSearch.kt` — `aimLayer` / `aimOpenSteps` on `SearchOutcome`, read in `searchWalk`
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — the `PASS … NO PATH` line prints `aimLayer` / `aimOpen`
- `app/src/test/java/ykws/android/maro/spatial/multipass/FinePriorityWalkTest.kt` — the two end-anchoring pins
- `xTrack/Route/261021_FEAT_PLN_Route_end-fine-island.md` — the plan, with `## Implemented`

## Next Step

Run the La Salis → Enfer de Dante pair on the device and read the `PASS` line's `aimLayer=0` / `aimOpen`; confirm the full pipeline still lands the route and the fine pass's own force-free causes no splice refusal.
