# Context Hydration — Route — 2026-10-21

**Last Bake:** 2026-10-21 08:16 UTC — written by `#bake` on `feature/route-sym-2` (cut from `origin/develop` by the user's `#new route-sym-2`); the first bake's commit is `b64a060e`, the canonical-end search and this bake come after it.

**Directive trace:** one covered action class was met and it stopped: the user's first `#impl` met an unsettled design choice, so the pipeline asked which repairs it authorised before it started. No dependency was added; the baked coastline, depth and zone files were read only by the app's own deserialisers inside gated tests, never opened by the agent; the device was not touched; and every claim about the code came from a file read. The user's `#bake`, `#commit` and `#push` were their own invocations.

## State

**A pair arms as one line from either end, within the bar (2026-10-21).** The pass's search leaves the end that sorts first ([`RoutePassRunner.searchFromCanonicalEnd`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt), the other way once if it lands no path), the two pulls run both ways and keep the cheaper ([`MultipassPull.pullBothWays`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt)), and a coarse block's fine copy has its step back out. On the gated 24-pair survey the plan-pair runs over the bar went 5 → 0 and the worst line gap is 0.4 m; the whole unit suite is green and `assembleDebug` packages. **The cost:** the two-sided pull doubles the pull, so a ladder is 64 % (`avoid`) to 79 % (`evolutive`) slower on a desktop JVM — the device's `PULL ms` / `FINAL ms` are owed.

**Open:** one `evolutive` pair still crowns a different rung each way on equal lines (the rungs' scores are unprinted); the guard swap and the fallback walk have no unit test. From the berth-carve session: an end the 50 m coast margin hems in beyond its disc fails as no path, and the Bacon start's device run is owed.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt` — `searchFromCanonicalEnd`, the two-sided pulls
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt` — `pullBothWays`, where a cheaper two-sided pull would go
- `app/src/test/java/ykws/android/maro/spatial/multipass/RouteDirectionSymmetryTest.kt` — the gated instrument (`-Dmaro.prebake=true`)
- `xTrack/Route/261020_FEAT_PLN_Route_direction-symmetry.md` — the plan, with measurements, cost and `## Implemented`

## Next Step

Read `PULL ms` and `FINAL ms` on a device pair; if the phone feels the doubled pull, make the two-sided pull cheaper (the backward walk is the half to economise).
