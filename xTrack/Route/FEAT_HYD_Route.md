# Context Hydration — Route — 2026-10-20

**Last Bake:** 2026-10-20 19:15 UTC — written by `#bake` on `feature/route-low-depth-check`; everything since `6475d327` is uncommitted.

**Directive trace:** since the last bake no covered action class was met: no device touch, no data file opened, no dependency added; the work followed the user's `#impl` and "remove all" orders, and every claim about the code came from a file read.

## State

**The berth carve is retired.** The depth gate waives itself only within 20 m of an end; every leg past that is measured, so the 20:01 device case (0.6 m crossed on the carve's 100 m hop) cannot recur. The boxed-in allowance stays as an end's only exit. The route table's moved-start and moved-destination notes are removed, with the `originMoved` flag. 1404 tests green, APK packaged 21:04.

**Open:** an end the 50 m coast margin hems in beyond its disc (a narrow harbour arm) now fails as no path — against the user's only-exit decision, no fallback designed yet. The new engine test guards the outcome but is not proven to fail on the old code.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/multipass/BerthCarve.kt` — the end disc, now the ends' only allowance
- `app/src/main/java/ykws/android/maro/spatial/multipass/ShallowEdgeIndex.kt` — `GateRule.waives`, disc only
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt` — the ends' discs on both builders
- `xTrack/Route/261020_FEAT_PLN_Route_end-approach.md` — the plan, its `## Implemented` and open points

## Next Step

The device run on the Bacon start, then the coast-boxed end's fallback.
