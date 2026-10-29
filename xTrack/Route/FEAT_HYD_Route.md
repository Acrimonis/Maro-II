# Context Hydration — Route — 2026-10-29

**Last Bake:** 2026-10-29 11:53 UTC — written by `#bake` on `feature/roouttinggg`.

**Directive trace:** no dependency was added and no binary file opened; the device was read only on the user's word that each run had happened (three logcat dumps), never written or deployed to; the code moved under `#implement` (P0, P1) and the user's "go the prototype way", each change behind a key whose default is today's; the `ask` hop reviewed the code twice and the plan three times, every blocking finding folded; none of the covered action classes stopped.

## State

`feature/roouttinggg` carries P0 (a `SEARCH ms` line on the device and the harness's clocks, readings, survey and `-Dmaro.harness.props`), P1 (`ChordReader`, the straight priced on the walk's own cells) and a prototype of P2 (`walkAnyAngle`, a Lazy Theta\* with a 32-cell reach) behind `route.search.anyAngle=false`; the suite (1 685, 24 skipped) and `apk-build.bat` green. Read on the device: the 43° bend is gone, the lines within §6's 5 %, the attempt +20–43 % on one warm run after a cold one, the lone turns not idled — P5 has no ground. P6 is designed and reviewed, nothing of it built. The slack probe stays.

## Target Files

- `xTrack/Route/261029_FEAT_PLN_Route_any-angle-search.md` — §15.3 is P6, D6 the re-solve's key, §14–§15 the readings
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt` — `refineOriented`'s per-zone crossing loop, P6b's seat
- `app/src/main/java/ykws/android/maro/spatial/multipass/ChordReader.kt`, `MultipassSearch.kt` (`walkAnyAngle`) — the walk behind the switch
- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt` — the switch read and the `SEARCH` line
- `app/src/main/assets/maro.properties`, `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the `route.search.anyAngle.*` keys; `route.fine.crossingResolve` to add (ONE HOME)
- `app/src/test/java/ykws/android/maro/spatial/multipass/RouteBendHarnessTest.kt`, `AnyAngleWalkTest.kt`, `ChordCostTest.kt`, `RouteSurveyPairs.kt` — the harness and the walk's tests

## Next Step

`#implement` P6b and P6c: `route.fine.crossingResolve` with its `FINE crossings=off` line and tests, the harness read off against on, and the 16-cell reach re-checked on the desktop; then the user's interleaved device read (P6a).
