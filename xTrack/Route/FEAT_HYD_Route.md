# Context Hydration — Route — 2026-10-04

**Last Bake:** 2026-10-04 19:26 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (17:40 UTC) every change ran on an order — the pull's plumbing and
trace, then the coarse skip, each handed to Code on the user's word; the fine layer's reshape approved from a
plan gate; the cell budget as *do phase 3*; and the two device readings fetched only after the user said the
test had run. No dependency was added and no machine-shaped data file was opened; every claim about the code
here follows a read.

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

**What the readings name as the next work, in order** — the feature file's own order section carries it, and
its first two steps are the measured ones: **the window count's own cost** (361 windows at ~12 ms each,
6 725 ms on the grown corridor and paid again on its retry) and **the price walk's reads** — `priceMs=13828.2`
of `ms=17303.1` at λ = 2.5, so **80 % of the pull is the priced water's own read**, the parked item whose
trigger the reading fired. After them: the mark count, then the rest of the feature's items, each on its own
trigger. The Walk's level is closed by decision with items 14 and 15 parked — the feature file's own section
carries them.

## Target Files
- `xTrack/Route/261004_FEAT_PLN_Route_pull-clearance-sampling.md` — Phases 1 to 3 landed and the reading taken; its Parked price-walk item has had its trigger fired
- `xTrack/Route/261004_FEAT_PLN_Route_fine-window-shape.md` — Phases 1 to 4 landed; the window count's cost is its parked lever
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt` — the fine layer's windows, the estimator, the ceiling and the refusal
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt`, `RouteCostField.kt` — the coarse marks, the skip and the field's two hard reads
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` + `app/src/main/assets/maro.properties` — `route.walk.maxCells`, the walk's own ceiling
- `app/src/test/java/ykws/android/maro/spatial/multipass/AvoidPullSamplingTest.kt`, `RouteFineWindowShapeTest.kt` — the two suites this session added
- `app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt` — the parked red asserting `avoid`'s fine ratio

## Next Step
The order's step 3, **the window count's own cost**: merge adjacent tiles along the coast in
[`fineWindowBoxes`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:331) so the
fine layer stops paying ~12 ms a window, with the coverage test as the correctness guard and `cellsOf` as the
memory guard. Then step 4, the price walk's plan: each soft source declares its distance thresholds, and the
plan states the price error the coarsening accepts — because the price guard's verdict is a number, not only a
verdict.
