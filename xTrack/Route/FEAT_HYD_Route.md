# Context Hydration — Route — 2026-10-21

**Last Bake:** 2026-10-21 13:56 UTC — written by `#bake` on `feature/route-sym-pull` (cut from `origin/develop` by the user's `#new route-sym-pull`); nothing of this branch is committed before this bake.

**Directive trace:** two covered action classes were met and neither stopped. The device was read twice on the user's word (`adb logcat -d`, `dumpsys package`, after "grab the last searches" and "done fetch") and never written to, deployed or launched. A `#impl` order stood behind the code change, so no work started without one. No dependency was added; no baked spatial file was opened (the saved logs are text); and every claim about the code came from a file read. The user's `#bake` and `#commit` were their own invocations.

## State

**The phone's cost of the two-sided pull is measured, and the larger cost was a different one (2026-10-21).** An instrumented build (`PULL2` / `FINAL2` per-walk lines, `RUNG` per-rung line) put the pull at 24–36 % of a search; overlapping its two walks (F1, **unbuilt**) would save 0.83–0.87 s an attempt, 9–13 % of a plain search, and the memo shared between the walks is worth only 2–7 % of reads. The retry beside it was the real cost: with a route end inside a speed zone the forced-crossing probe walled the end in by its own zone, every rung read a forced crossing and grew a corridor that gave the same line, ~10 s of a 19–21 s search. **Fixed** (fix (b) of [`261021_FEAT_PLN_Route_pull-cost.md`](../../xTrack/Route/261021_FEAT_PLN_Route_pull-cost.md)): `RouteAvoidEngine.forcedCrossingNames` leaves open and never names the zones containing an end, through `MultipassGrid.blockedCopy`'s new `stayOpen` predicate. The leftovers are taken too: the probe names no `priced` list and reports `probeMs=`, four more tests pin the end-zone rule (two ends in two zones, a crossing inside an end zone, a routing override), and two mutations confirm the exemption and its crossing clause are each pinned. The full unit suite (1421) and `assembleDebug` are green. The leftovers are uncommitted at this bake; the commit `c662726c` predates them.

**Open:** the device pass on the zone pair (`RUNG reason=NONE grew=false`, `endZones=[…]`); F1 and its CPU-for-wall trade; the unmeasured `stayOpen` cost (now read as `probeMs=` on the device); `FINAL`'s input differing with the arming direction; one `evolutive` pair crowning a different rung each way.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — `forcedCrossingNames` (end zones), `searchRung` + `RungReading` (the `RUNG` line)
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt` — `blockedCopy(paceKn, stayOpen)`
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt`, `RoutePassRunner.kt` — `PullSplit`, `pullBothWays(split=)`, `splitText`: the `PULL2` / `FINAL2` instrument
- `app/src/test/java/ykws/android/maro/spatial/multipass/RouteZonePhase4Test.kt` — five tests pinning the end-zone rule
- `xTrack/Route/261021_FEAT_PLN_Route_pull-cost.md` — the plan, with the device results, the review rounds and `## Implemented`

## Next Step

Run the zone pair both ways on the device and read `RUNG` / `PROBE endZones=`; then decide F1 (parallel pull walks) on its 0.83–0.87 s.
