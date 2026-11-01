# Context Hydration — Route — 2026-11-01

**Last Bake:** 2026-11-01 18:29 UTC — written by `#bake` on `feature/rooouting`.

**Directive trace:** no dependency was added, no binary file opened and the device not touched; the code moved on the user's orders — Evolutive + made the default (*"make evolutive+ the default"*), P2's tests and the small fixes and the boxed-end and bend measurements through agents (*"spawn an agent"*), the harness and docs fixes (*"fix the not fixed"*) — and the feature file, plans and archive on *"yes"*, *"go"*, `#archive` and *"close all that can be closed"*; the `ask` hop reviewed the Tracks card plan once; no git write ran before this bake's `#commit`; none of the covered action classes stopped.

## State

`feature/rooouting` ships Evolutive + as the default engine (`route.engine.id=evolutive_plus`), the Evolutive + rule read in one place (`AnyAngleRule.shipped()`), P2's tests on both searches, the harness flags forwarded (`maro.harness.anyAngle`, `.corners`, `.paceKn`, `maro.boxedEnds`) and two `@Ignore` coast-boxed-end reproductions; the suite stands at 1 704 tests, 27 skipped, green. The task list is down to 7 (any-angle P3/P5, the parked boxed end and swap-ends, the channel after its device pass, the multi-route save, the provisional mark, the slow-water card row); 31 plans archived, one dropped. The slow-water card row is planned in Tracks (v2, reviewed once, every decision taken).

## Target Files

- `xTrack/Route/261029_FEAT_PLN_Route_any-angle-search.md` — P3 (one pricing for steps and chords, the `bound`/`full`/`reopen` knobs) and P5 (the approach pass's shape cases on a counter at zero)
- `xTrack/Tracks/261101_FEAT_PLN_Tracks_stops-and-slow-water.md` — the G2 row, the classifier seam, the route attribution aligned
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassSearch.kt` (`AnyAngleRule.shipped`), `RoutePassRunner.kt`, `RouteApproachPass.kt`
- `app/src/test/java/ykws/android/maro/spatial/multipass/RouteBendHarnessTest.kt` — the corners mode (`-Dmaro.harness.corners=true`) and the pace override

## Next Step

The any-angle plan's P3, now that Evolutive + is the default — or the Tracks card plan's P0, the user's pick.
