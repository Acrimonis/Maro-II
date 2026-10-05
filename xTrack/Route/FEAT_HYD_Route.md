# Context Hydration — Route — 2026-10-05

**Last Bake:** 2026-10-05 09:39 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-04 20:32 UTC) every change ran on an order — the plan's
pin and Phase 4b on `#impl`, the record fold on *update plan and review*, the bake itself on `#bake`. No
dependency was added, no machine-shaped data file was opened, no work started without an order, the device
was not touched, and every claim about the code follows a file read.

## State

**The price walk's step is the walk's interior cell, so a proved group forms where none could.** [`priceStepFor(walk)`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt:163)
answers it — never the fine cell — and [`pull`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:70)
and [`snapToCorners()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:175)
carry it beside the untouched clearance step, so Δ price stays zero and the line, distance, ETA and clock
cannot move; the shipped margin (50 m, so a 25 m fine interval, not the 12.5 m three documents carried) makes
the grouping `k = 4`, a 50 m proof radius. Five tests in
[`AvoidPriceWalkTest`](../../app/src/test/java/ykws/android/maro/spatial/multipass/AvoidPriceWalkTest.kt:236)
pin the equivalence, the seam, a two-layer walk, the ring collar's chord and the shipped declaration closure;
the suite stands at 903 with its one parked red and `apk-build.bat` is green.

**Two gaps the landing's own review named are owed.** The seam test pins `priceStepFor` but drives no
`runPass`, so a reverted call site would collapse the grouping with the suite green; and the collar chord's
deep half passes on the hole's own `|d − 100|` term, a reason its comment does not state and its assertions
do not count.

**What no one has read yet is the win itself.** `avoid`'s price share has never been measured — its single
100 m walk had been grouping all along — and `evolutive`'s grouping has not been read since the step moved,
so the mark count (the order's step 5) still waits on that reading. The walk's level stays closed by
decision with items 14 and 15 parked, and `avoid`'s parked ratio test stays the user's own.

## Target Files
- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt` — `priceStepFor(walk)` and the two steps
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt`, `RoutePassPrimitives.kt`, `RouteFinePass.kt` — the priced sites and the two-arity callers
- `app/src/test/java/ykws/android/maro/spatial/multipass/AvoidPriceWalkTest.kt` — the five tests; `app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt` — the parked red
- `app/src/main/assets/maro.properties` — the shipped margin the corrected model reads
- `xTrack/Route/261004_FEAT_PLN_Route_price-walk-reads.md` — Phase 4b landed, its two gaps, its settled question

## Next Step
The device pass, and it must carry **both engines**: `evolutive` for the saving this cut opened and `avoid`
for a price share never read. Beside it, the one test that drives `runPass` itself, so a reverted call site
cannot re-collapse the grouping unseen.
