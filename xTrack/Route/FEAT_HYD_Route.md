# Context Hydration — Route — 2026-10-30

**Last Bake:** 2026-10-30 20:33 UTC — written by `#bake` on `feature/rooouting`.

**Directive trace:** no dependency was added and no binary file opened (the baked coastline read only through its own deserializer in tests); the device was not touched; the code moved on the user's *"go and do as much as you can"* and *"whatever is next that would improve the perf"*, the branch fast-forwarded on their *"do it"*; the `ask` hop reviewed each of the two speed-ups once, nothing blocking, its should-fix folded; none of the covered action classes stopped.

## State

`feature/rooouting` (on `develop` at PR #398) carries two exact speed-ups, every harness line identical on `evolutive` and `evolutive_plus`: `CoastlineSpatialIndex.distanceM` reads the coast distance off a per-cell candidate list (the 24-pair survey −67–73 %, the default pair −13–23 %), and `MultipassPull.pullBothWays` runs its two walks side by side on `MarkMemo` forks (the survey −11–16 % more, the pair inside noise). The suite (1 692, 24 skipped), the real-coast `RouteDirectionSymmetryTest` and `apk-build.bat` green. The remaining levers each measure under ~5 % of a coastal rung.

## Target Files

- `xTrack/Route/261030_FEAT_PLN_Route_exact-speedups.md` — §5 the readings, both reviews, what is left and why
- `app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt` — `distanceM`, `candidatesOf`
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt` — `pullBothWays`, `MarkMemo.fork`/`join`
- `app/src/test/java/ykws/android/maro/spatial/CoastlineSpatialIndexTest.kt`, `multipass/PullBothWaysForkTest.kt`, `multipass/RouteBendHarnessTest.kt` (`-Dmaro.harness.survey=true`)

## Next Step

The phone's reading of the two speed-ups is the user's (feature file, Owed device passes); P2's owed tests on the Evolutive + engine stay open (any-angle plan §17).
