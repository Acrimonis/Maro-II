# Context Hydration — Route — 2026-10-04

**Last Bake:** 2026-10-04 20:32 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (17:40 UTC) every change ran on an order — the pull's plumbing and
trace, then the coarse skip, each handed to Code on the user's word; the fine layer's reshape approved from a
plan gate; the cell budget as *do phase 3*; the two device readings fetched only after the user said the test
had run; and, on 2026-10-04, the window merge taken as *take the window merge first* and the price walk's plan
written on the same word, then the record refreshed on *update docs*. No dependency was added and no
machine-shaped data file was opened; every claim about the code here follows a read.

## State

**The pull pays the coastline read only where it must.** [`MultipassPull.legClearCause`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:174)
places its coarse marks first — one per coarse step, at the midpoints of the equal divisions — makes each pay
the materialized distance read, and lets a fine mark pay it only where the covering coarse mark read under
`marginM + coarseStep / 2`, the trigger the distance's own 1-Lipschitz bound gives. The field's two hard shapes
are read apart ([`hardBlocked`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:281)
for the rastered step, `hardDistanceM` for the coastline's distance), the coarse step travels per engine
(`RoutePassRunner`'s local cell, the fine-pass seats' fine cell) and is required, never defaulted. The device
reading of 2026-10-04 is the proof of parity and of the saving: **the clearance half stands at 0.8–3.5 s**
(`clearMs=772.6` of `ms=3948.6` at λ = 5) with the line, distance and clock unchanged.

**The fine layer is the coastal ribbon, and a walk is refused before it can exhaust the heap.** The grown
corridor's fine layer used to be the corridor's whole box at 20 m — 1 178 555 cells and an `OutOfMemoryError`
in the ring fill. [`RouteGridBuilder.buildLayeredGrid`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:166)
now cuts the fine layer into windows over the water the mask keeps (361 tiles, **205 824 cells**), and both
build paths ask [`cellsOf`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:294)
against `route.walk.maxCells` **before** rastering anything, so an over-sized walk is refused with a
`WALK refused` line instead of dying. With no band there is no mask and no fine layer at all. The reading: no
crash, no refusal, the grown walk at 253 110 cells of the 600 000 ceiling, the answers unchanged.

**The windows are merged now, and the price half has its plan.** The marked tiles no longer cost one sweep
each: [`fineWindowBoxes`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:382)
merges them into **exact-union rectangles** on the lattice's own lines — row runs, then equal-span rows stacked
— so the walk holds the same cells and only the window count falls: the fixture's **129 tiles became 3
windows**, and the grown corridor's own count is what a device pass reads. Beside it the price walk's own cut
has **landed (Phases 1 to 3, 2026-10-04)**: each soft source declares the distance to its own price boundary, a
proved group is priced by one reading, and the price error the coarsening accepts is **zero** because the
group's product is identically the fine sum. **The device pass of 2026-10-04 has now read it**
([`route-phase8.txt`](../../route-phase8.txt:1)) and it says the cut lands exact and **saves nothing yet**: the
fine layer reads 11 windows / 69 576 cells / 885–1 004 ms on a 3.5 km corridor and 26 / 145 396 / 1 828–1 838 ms
on a 13.3 km one, while the pull's own split is unchanged — `priceMs` 14738.3 of 18340.0 with 87176 reads, its
twin 15987.3 of 20051.7 with 91767, so still 80 % of the pull and still the mark count this plan's model
predicts — because the step a priced rung hands the walk is the band's **fine 20 m cell**, exactly the water a
price is paid in, so the grouping's quotient is 1 and no group ever forms. Two tests the landing's review left
are still owed, and proving a group would cost a read of its own, so a formed group's saving is `k − 2` reads.

**What the readings name as the next work, in order** — the feature file's own order section carries it, and
its first two steps are the measured ones: **the window count's own cost**, now landed as the merged windows
and waiting only on a device reading against 361 windows at ~12 ms each, and **the price walk's reads** —
`priceMs=13828.2` of `ms=17303.1` at λ = 2.5, so **80 % of the pull is the priced water's own read**, whose
plan is written and in design. After them: the mark count, then the rest of the feature's items, each on its
own trigger. The Walk's level is closed by decision with items 14 and 15 parked — the feature file's own
section carries them.

## Target Files
- `xTrack/Route/261004_FEAT_PLN_Route_pull-clearance-sampling.md` — Phases 1 to 3 landed and the reading taken; its Parked price-walk item has had its trigger fired
- `xTrack/Route/261004_FEAT_PLN_Route_fine-window-shape.md` — Phases 1 to 4 landed and its parked window lever taken; the merge holds every lattice cell and cuts the window count
- `xTrack/Route/261004_FEAT_PLN_Route_price-walk-reads.md` — in design; each soft source declares its own price boundary distance, and the accepted price error is zero
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt` — the fine layer's windows, the estimator, the ceiling and the refusal
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt`, `RouteCostField.kt` — the coarse marks, the skip and the field's two hard reads
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` + `app/src/main/assets/maro.properties` — `route.walk.maxCells`, the walk's own ceiling
- `app/src/test/java/ykws/android/maro/spatial/multipass/AvoidPullSamplingTest.kt`, `RouteFineWindowShapeTest.kt`, `AvoidPriceWalkTest.kt` — the suites these sessions added: the sampled clearance, the fine layer's windows, and the price walk's grouping with its seven tests
- `app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt` — the parked red asserting `avoid`'s fine ratio

## Next Step
The next cut is the **price walk's own step**, and it is the one the reading named: the step in force on a
priced rung is the band's fine cell — the water a price is paid in — so the grouping's quotient is 1 and no
group ever forms; the walk's **interior** cell (100 m → `k = 8`, a 50 m proof radius) is the lever, and it moves
no answer, because exactness rests on the arm being constant inside a group and never on the step's size
([`261004_FEAT_PLN_Route_price-walk-reads.md`](261004_FEAT_PLN_Route_price-walk-reads.md), whose Phase 4 now
carries the figures). Beside it, the two tests the landing's review left stay owed — a walk-level chord for the
ring collar, and one for the **shipped** declaration closure in `costField` — and the mark count (the order's
step 5) waits on that cut's own reading.
