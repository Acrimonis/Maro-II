# Context Hydration — Route — 2026-09-24

**Last Bake:** 2026-09-24 17:52 UTC — written by `#bake`

**Directive trace:** no dependency added, no machine-shaped data file opened, and nothing deployed — the device was only read (`adb logcat`, `adb devices`, `pidof`); every build stayed on this machine and the device pass stayed the user's. The work was ordered throughout: the prototype, the adoption and the refinement each followed the user's word, and the git write is the `#commit` the user invoked.

## State

Stage 1 ships, and its taut pull settled twice on device evidence. The avoid engine routes around land through a corridor-bounded grid A* plus a taut pull; the first taut pass — a corner-graph A* — measured 7.3 s on the phone over 191 corners, its greedy tangent-walk replacement then drew a jittery line the user's route export exposed, and the shipped pull is now grid A* → grid pull → verified corner snap → pull: a bend moves onto its nearest tangent corner only when both neighbouring legs stay clear, so a sharp headland gets its true corners while a smooth island keeps its grid line. The ANR that opened the session — the pipeline ran on the main thread — is closed by `withContext(Dispatchers.Default)` around `RouteAvoidEngine.search()`.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — the off-loaded pipeline, the corner snap
- `app/src/main/java/ykws/android/maro/spatial/avoid/TangentCorners.kt` — the convex offset corners
- `app/src/main/java/ykws/android/maro/spatial/avoid/AvoidPull.kt` — `legClear` now `internal` for the snap's verification
- `app/src/test/java/ykws/android/maro/spatial/avoid/TangentCornersTest.kt` · `app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt` — the corner and pipeline tests
- `xTrack/Route/260924_FEAT_PLN_Route_tangent-dichotomy-prototype.md` — the plan of record

## Next Step

The real-corridor device measurement of the new line — the user's.
