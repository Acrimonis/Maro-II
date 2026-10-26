<!-- scope: feature -->
# Stage 6 — retained overlays, preview ownership and arrow bounds

## Disposition
Independent branch from upstream develop `b1f40aa211c02c2882bb676514bee753cecacebe`. Retained the measured geometry-work reduction and conservative warm-draw bounds candidate. Preview ownership is implemented; its Android runtime performance remains **unverified**, as do frame tails and complete visual acceptance. No overall device performance pass is claimed.

History/routes/pinned entries retain complete point-list geometry and the appearance inputs actually read by their render plan. Geometry comparison includes interior points, speeds, breaks and timestamps through TrackPoint equality, not just IDs/endpoints/counts. A completed library reload publishes a render revision even when summary fields stay equal; file replacements invalidate their detail entry. Prepared RenderPoints survive style changes. Entries are pruned to the current drawn set and cleared with their MapView owner. Unchanged overlay objects remain; departed objects alone are removed, and order changes move the retained objects. Publication is checked for coroutine cancellation. Live and selected-track owners remain separate.

The preview owns its own entry and bitmap pool. Confirmed structural keys no longer include the moving preview, and cleanup removes owned objects instead of sweeping title prefixes. Preview shapes/icons retain their original positions below confirmed shapes/icons; the route destination pin remains foreign-owned.

Arrow bounds are calculated once per geometry. The original sampler still runs on every zoom change before any bounds rejection, preserving its global anchor phase and rounding. Bounds skip only later drawing projections. The viewport is padded beyond the existing screen cull; rotation, wrapped/inverted/nonfinite bounds and polar clamp cases fall back to the original path. Empty anchor sets return before viewport conversion.

## Proportionate measurements
Linux x86_64 i5-1245U, JDK 17, synthetic geometry, `MARO_OVERLAY_PERF=1`. Twenty alternating rounds; first ten warm up. Geometry cases use 20 tracks × 2,000 points with GAPs, over one cold pass or twenty refreshes. The reference rebuilds the same pure render points and segment plan every pass; the candidate uses the production retained cache. Per-point and segment equality are checked outside timing. This excludes repository I/O and Android overlay allocation/drawing.

| Workload | Candidate | Median ms | Range ms |
|---|---|---:|---:|
| OVERLAY: cold | false | 0.778903 | 0.691162–0.916230 |
| OVERLAY: cold | true | 0.810566 | 0.695503–0.998020 |
| OVERLAY: unchanged | false | 12.283931 | 10.897589–14.561545 |
| OVERLAY: unchanged | true | 0.953063 | 0.698427–1.818453 |
| OVERLAY: edit | false | 13.991726 | 12.012604–21.561861 |
| OVERLAY: edit | true | 0.913625 | 0.684770–0.997933 |
| OVERLAY: fade | false | 12.562965 | 11.581496–13.610909 |
| OVERLAY: fade | true | 1.216329 | 0.974060–2.293433 |
| BOUNDS: 4 points; offscreen=false | false | 0.003930 | 0.003388–0.004519 |
| BOUNDS: 4 points; offscreen=false | true | 0.003459 | 0.003348–0.005519 |
| BOUNDS: 4 points; offscreen=true | false | 0.003418 | 0.003399–0.004334 |
| BOUNDS: 4 points; offscreen=true | true | 0.003439 | 0.003377–0.003905 |
| BOUNDS: 4000 points; offscreen=false | false | 0.105675 | 0.098983–0.144050 |
| BOUNDS: 4000 points; offscreen=false | true | 0.109198 | 0.102672–0.198343 |
| BOUNDS: 4000 points; offscreen=true | false | 0.098807 | 0.097803–0.106104 |
| BOUNDS: 4000 points; offscreen=true | true | 0.004336 | 0.001921–0.011660 |

Geometry counters, reference→candidate: cold prepare/build 20→20; unchanged 400→20; one interior edit 400→21; fade change prepares 400→20 and builds 400→39 (the newest appearance is unchanged). The repeated workloads have disjoint timing ranges; cold ranges overlap and no cold speedup is claimed. The preceding run's cold medians were 1.257→1.202 ms, versus 0.779→0.811 ms in the final run: no repeatable cold regression observed in the measured pure-plan workload.

Bounds cases measure 100 warm drawing passes after the unchanged sampling pass. For 4,000 off-screen points, forward projections 7,800→0, median 0.098807→0.004336 ms. All-visible projections remain 7,800 and timing ranges overlap; sparse cases have no anchors and no bounds work. **The two Android inverse viewport projections per nonempty draw are not included in the JVM timing**, so the full draw/frame gate remains open. No projection or anchor-sampling rate/detail was lowered.

Preview moving no longer keys confirmed structural work by source inspection, but Compose execution counts, bitmap allocation and drag frame timing were not measured; those remain unverified, not a passed performance claim.

## Validation and limits
Debug APK and 190 targeted tests passed: retained identity/eviction, complete geometry invalidation, randomized managed ordering with foreign objects, role/axes/fade equivalence, inactive colour axes, GAPs, marker paint/match behavior, arrow phase and conservative boundary cases. An additional 35 route-role/filter/selection and GAP checks passed with zero failures. Existing rendering functions remain the differential reference; Android screenshots and real MapView integration were unavailable.

Unmeasured: device pan/zoom/rotation/selection visuals, frame tails, actual retained native memory, large-library detail-cache I/O, preview drag costs, viewport conversion cost, real-region data and combined-stage behavior. Cache retention is bounded by currently drawn items, not by an arbitrary total-history cache. This PR does not certify the complete visual/performance matrix.

## Warm samples
```text
OVERLAY-PERF workload=cold candidate=false round=10 ns=773940 prepared=20 built=20
OVERLAY-PERF workload=cold candidate=true round=10 ns=786596 prepared=20 built=20
OVERLAY-PERF workload=cold candidate=true round=11 ns=727608 prepared=20 built=20
OVERLAY-PERF workload=cold candidate=false round=11 ns=765210 prepared=20 built=20
OVERLAY-PERF workload=cold candidate=false round=12 ns=873435 prepared=20 built=20
OVERLAY-PERF workload=cold candidate=true round=12 ns=877959 prepared=20 built=20
OVERLAY-PERF workload=cold candidate=true round=13 ns=905192 prepared=20 built=20
OVERLAY-PERF workload=cold candidate=false round=13 ns=783867 prepared=20 built=20
OVERLAY-PERF workload=cold candidate=false round=14 ns=818816 prepared=20 built=20
OVERLAY-PERF workload=cold candidate=true round=14 ns=966687 prepared=20 built=20
OVERLAY-PERF workload=cold candidate=true round=15 ns=998020 prepared=20 built=20
OVERLAY-PERF workload=cold candidate=false round=15 ns=813664 prepared=20 built=20
OVERLAY-PERF workload=cold candidate=false round=16 ns=916230 prepared=20 built=20
OVERLAY-PERF workload=cold candidate=true round=16 ns=834536 prepared=20 built=20
OVERLAY-PERF workload=cold candidate=true round=17 ns=727213 prepared=20 built=20
OVERLAY-PERF workload=cold candidate=false round=17 ns=716639 prepared=20 built=20
OVERLAY-PERF workload=cold candidate=false round=18 ns=691162 prepared=20 built=20
OVERLAY-PERF workload=cold candidate=true round=18 ns=695503 prepared=20 built=20
OVERLAY-PERF workload=cold candidate=true round=19 ns=707338 prepared=20 built=20
OVERLAY-PERF workload=cold candidate=false round=19 ns=701266 prepared=20 built=20
OVERLAY-PERF workload=unchanged candidate=false round=10 ns=11724271 prepared=400 built=400
OVERLAY-PERF workload=unchanged candidate=true round=10 ns=940683 prepared=20 built=20
OVERLAY-PERF workload=unchanged candidate=true round=11 ns=1117898 prepared=20 built=20
OVERLAY-PERF workload=unchanged candidate=false round=11 ns=11635719 prepared=400 built=400
OVERLAY-PERF workload=unchanged candidate=false round=12 ns=11678710 prepared=400 built=400
OVERLAY-PERF workload=unchanged candidate=true round=12 ns=972029 prepared=20 built=20
OVERLAY-PERF workload=unchanged candidate=true round=13 ns=1818453 prepared=20 built=20
OVERLAY-PERF workload=unchanged candidate=false round=13 ns=10897589 prepared=400 built=400
OVERLAY-PERF workload=unchanged candidate=false round=14 ns=14278219 prepared=400 built=400
OVERLAY-PERF workload=unchanged candidate=true round=14 ns=965443 prepared=20 built=20
OVERLAY-PERF workload=unchanged candidate=true round=15 ns=707153 prepared=20 built=20
OVERLAY-PERF workload=unchanged candidate=false round=15 ns=11626444 prepared=400 built=400
OVERLAY-PERF workload=unchanged candidate=false round=16 ns=12843590 prepared=400 built=400
OVERLAY-PERF workload=unchanged candidate=true round=16 ns=698427 prepared=20 built=20
OVERLAY-PERF workload=unchanged candidate=true round=17 ns=728016 prepared=20 built=20
OVERLAY-PERF workload=unchanged candidate=false round=17 ns=13567328 prepared=400 built=400
OVERLAY-PERF workload=unchanged candidate=false round=18 ns=13604079 prepared=400 built=400
OVERLAY-PERF workload=unchanged candidate=true round=18 ns=1019121 prepared=20 built=20
OVERLAY-PERF workload=unchanged candidate=true round=19 ns=896154 prepared=20 built=20
OVERLAY-PERF workload=unchanged candidate=false round=19 ns=14561545 prepared=400 built=400
OVERLAY-PERF workload=edit candidate=false round=10 ns=13903287 prepared=400 built=400
OVERLAY-PERF workload=edit candidate=true round=10 ns=946924 prepared=21 built=21
OVERLAY-PERF workload=edit candidate=true round=11 ns=960306 prepared=21 built=21
OVERLAY-PERF workload=edit candidate=false round=11 ns=14454418 prepared=400 built=400
OVERLAY-PERF workload=edit candidate=false round=12 ns=14715984 prepared=400 built=400
OVERLAY-PERF workload=edit candidate=true round=12 ns=971883 prepared=21 built=21
OVERLAY-PERF workload=edit candidate=true round=13 ns=911003 prepared=21 built=21
OVERLAY-PERF workload=edit candidate=false round=13 ns=12012604 prepared=400 built=400
OVERLAY-PERF workload=edit candidate=false round=14 ns=13280889 prepared=400 built=400
OVERLAY-PERF workload=edit candidate=true round=14 ns=809467 prepared=21 built=21
OVERLAY-PERF workload=edit candidate=true round=15 ns=916248 prepared=21 built=21
OVERLAY-PERF workload=edit candidate=false round=15 ns=21561861 prepared=400 built=400
OVERLAY-PERF workload=edit candidate=false round=16 ns=16333550 prepared=400 built=400
OVERLAY-PERF workload=edit candidate=true round=16 ns=997933 prepared=21 built=21
OVERLAY-PERF workload=edit candidate=true round=17 ns=697581 prepared=21 built=21
OVERLAY-PERF workload=edit candidate=false round=17 ns=13236868 prepared=400 built=400
OVERLAY-PERF workload=edit candidate=false round=18 ns=13987008 prepared=400 built=400
OVERLAY-PERF workload=edit candidate=true round=18 ns=771721 prepared=21 built=21
OVERLAY-PERF workload=edit candidate=true round=19 ns=684770 prepared=21 built=21
OVERLAY-PERF workload=edit candidate=false round=19 ns=13996444 prepared=400 built=400
OVERLAY-PERF workload=fade candidate=false round=10 ns=11908923 prepared=400 built=400
OVERLAY-PERF workload=fade candidate=true round=10 ns=1086632 prepared=20 built=39
OVERLAY-PERF workload=fade candidate=true round=11 ns=1333602 prepared=20 built=39
OVERLAY-PERF workload=fade candidate=false round=11 ns=11581496 prepared=400 built=400
OVERLAY-PERF workload=fade candidate=false round=12 ns=13609347 prepared=400 built=400
OVERLAY-PERF workload=fade candidate=true round=12 ns=1019526 prepared=20 built=39
OVERLAY-PERF workload=fade candidate=true round=13 ns=974060 prepared=20 built=39
OVERLAY-PERF workload=fade candidate=false round=13 ns=12081420 prepared=400 built=400
OVERLAY-PERF workload=fade candidate=false round=14 ns=11776725 prepared=400 built=400
OVERLAY-PERF workload=fade candidate=true round=14 ns=1365927 prepared=20 built=39
OVERLAY-PERF workload=fade candidate=true round=15 ns=2293433 prepared=20 built=39
OVERLAY-PERF workload=fade candidate=false round=15 ns=12565628 prepared=400 built=400
OVERLAY-PERF workload=fade candidate=false round=16 ns=12861589 prepared=400 built=400
OVERLAY-PERF workload=fade candidate=true round=16 ns=1153967 prepared=20 built=39
OVERLAY-PERF workload=fade candidate=true round=17 ns=1575328 prepared=20 built=39
OVERLAY-PERF workload=fade candidate=false round=17 ns=13060809 prepared=400 built=400
OVERLAY-PERF workload=fade candidate=false round=18 ns=13610909 prepared=400 built=400
OVERLAY-PERF workload=fade candidate=true round=18 ns=1278691 prepared=20 built=39
OVERLAY-PERF workload=fade candidate=true round=19 ns=1053218 prepared=20 built=39
OVERLAY-PERF workload=fade candidate=false round=19 ns=12560302 prepared=400 built=400
BOUNDS-PERF count=4 offscreen=false candidate=false round=10 ns=3991 projections=0
BOUNDS-PERF count=4 offscreen=false candidate=true round=10 ns=3482 projections=0
BOUNDS-PERF count=4 offscreen=false candidate=true round=11 ns=3436 projections=0
BOUNDS-PERF count=4 offscreen=false candidate=false round=11 ns=3483 projections=0
BOUNDS-PERF count=4 offscreen=false candidate=false round=12 ns=3869 projections=0
BOUNDS-PERF count=4 offscreen=false candidate=true round=12 ns=4223 projections=0
BOUNDS-PERF count=4 offscreen=false candidate=true round=13 ns=4152 projections=0
BOUNDS-PERF count=4 offscreen=false candidate=false round=13 ns=4519 projections=0
BOUNDS-PERF count=4 offscreen=false candidate=false round=14 ns=4217 projections=0
BOUNDS-PERF count=4 offscreen=false candidate=true round=14 ns=4271 projections=0
BOUNDS-PERF count=4 offscreen=false candidate=true round=15 ns=5519 projections=0
BOUNDS-PERF count=4 offscreen=false candidate=false round=15 ns=4371 projections=0
BOUNDS-PERF count=4 offscreen=false candidate=false round=16 ns=4305 projections=0
BOUNDS-PERF count=4 offscreen=false candidate=true round=16 ns=3417 projections=0
BOUNDS-PERF count=4 offscreen=false candidate=true round=17 ns=3364 projections=0
BOUNDS-PERF count=4 offscreen=false candidate=false round=17 ns=3459 projections=0
BOUNDS-PERF count=4 offscreen=false candidate=false round=18 ns=3388 projections=0
BOUNDS-PERF count=4 offscreen=false candidate=true round=18 ns=3387 projections=0
BOUNDS-PERF count=4 offscreen=false candidate=true round=19 ns=3348 projections=0
BOUNDS-PERF count=4 offscreen=false candidate=false round=19 ns=3393 projections=0
BOUNDS-PERF count=4 offscreen=true candidate=false round=10 ns=4334 projections=0
BOUNDS-PERF count=4 offscreen=true candidate=true round=10 ns=3425 projections=0
BOUNDS-PERF count=4 offscreen=true candidate=true round=11 ns=3444 projections=0
BOUNDS-PERF count=4 offscreen=true candidate=false round=11 ns=3419 projections=0
BOUNDS-PERF count=4 offscreen=true candidate=false round=12 ns=3423 projections=0
BOUNDS-PERF count=4 offscreen=true candidate=true round=12 ns=3388 projections=0
BOUNDS-PERF count=4 offscreen=true candidate=true round=13 ns=3377 projections=0
BOUNDS-PERF count=4 offscreen=true candidate=false round=13 ns=3417 projections=0
BOUNDS-PERF count=4 offscreen=true candidate=false round=14 ns=3417 projections=0
BOUNDS-PERF count=4 offscreen=true candidate=true round=14 ns=3434 projections=0
BOUNDS-PERF count=4 offscreen=true candidate=true round=15 ns=3905 projections=0
BOUNDS-PERF count=4 offscreen=true candidate=false round=15 ns=3417 projections=0
BOUNDS-PERF count=4 offscreen=true candidate=false round=16 ns=3417 projections=0
BOUNDS-PERF count=4 offscreen=true candidate=true round=16 ns=3451 projections=0
BOUNDS-PERF count=4 offscreen=true candidate=true round=17 ns=3552 projections=0
BOUNDS-PERF count=4 offscreen=true candidate=false round=17 ns=3459 projections=0
BOUNDS-PERF count=4 offscreen=true candidate=false round=18 ns=3399 projections=0
BOUNDS-PERF count=4 offscreen=true candidate=true round=18 ns=3498 projections=0
BOUNDS-PERF count=4 offscreen=true candidate=true round=19 ns=3429 projections=0
BOUNDS-PERF count=4 offscreen=true candidate=false round=19 ns=3887 projections=0
BOUNDS-PERF count=4000 offscreen=false candidate=false round=10 ns=106390 projections=7800
BOUNDS-PERF count=4000 offscreen=false candidate=true round=10 ns=120405 projections=7800
BOUNDS-PERF count=4000 offscreen=false candidate=true round=11 ns=106315 projections=7800
BOUNDS-PERF count=4000 offscreen=false candidate=false round=11 ns=112270 projections=7800
BOUNDS-PERF count=4000 offscreen=false candidate=false round=12 ns=118311 projections=7800
BOUNDS-PERF count=4000 offscreen=false candidate=true round=12 ns=104239 projections=7800
BOUNDS-PERF count=4000 offscreen=false candidate=true round=13 ns=108861 projections=7800
BOUNDS-PERF count=4000 offscreen=false candidate=false round=13 ns=98983 projections=7800
BOUNDS-PERF count=4000 offscreen=false candidate=false round=14 ns=100025 projections=7800
BOUNDS-PERF count=4000 offscreen=false candidate=true round=14 ns=111281 projections=7800
BOUNDS-PERF count=4000 offscreen=false candidate=true round=15 ns=109535 projections=7800
BOUNDS-PERF count=4000 offscreen=false candidate=false round=15 ns=99226 projections=7800
BOUNDS-PERF count=4000 offscreen=false candidate=false round=16 ns=144050 projections=7800
BOUNDS-PERF count=4000 offscreen=false candidate=true round=16 ns=187689 projections=7800
BOUNDS-PERF count=4000 offscreen=false candidate=true round=17 ns=198343 projections=7800
BOUNDS-PERF count=4000 offscreen=false candidate=false round=17 ns=106251 projections=7800
BOUNDS-PERF count=4000 offscreen=false candidate=false round=18 ns=100328 projections=7800
BOUNDS-PERF count=4000 offscreen=false candidate=true round=18 ns=102672 projections=7800
BOUNDS-PERF count=4000 offscreen=false candidate=true round=19 ns=103330 projections=7800
BOUNDS-PERF count=4000 offscreen=false candidate=false round=19 ns=105099 projections=7800
BOUNDS-PERF count=4000 offscreen=true candidate=false round=10 ns=106104 projections=7800
BOUNDS-PERF count=4000 offscreen=true candidate=true round=10 ns=4541 projections=0
BOUNDS-PERF count=4000 offscreen=true candidate=true round=11 ns=4286 projections=0
BOUNDS-PERF count=4000 offscreen=true candidate=false round=11 ns=98550 projections=7800
BOUNDS-PERF count=4000 offscreen=true candidate=false round=12 ns=97898 projections=7800
BOUNDS-PERF count=4000 offscreen=true candidate=true round=12 ns=4735 projections=0
BOUNDS-PERF count=4000 offscreen=true candidate=true round=13 ns=4554 projections=0
BOUNDS-PERF count=4000 offscreen=true candidate=false round=13 ns=99065 projections=7800
BOUNDS-PERF count=4000 offscreen=true candidate=false round=14 ns=98474 projections=7800
BOUNDS-PERF count=4000 offscreen=true candidate=true round=14 ns=4360 projections=0
BOUNDS-PERF count=4000 offscreen=true candidate=true round=15 ns=4312 projections=0
BOUNDS-PERF count=4000 offscreen=true candidate=false round=15 ns=100056 projections=7800
BOUNDS-PERF count=4000 offscreen=true candidate=false round=16 ns=98058 projections=7800
BOUNDS-PERF count=4000 offscreen=true candidate=true round=16 ns=11660 projections=0
BOUNDS-PERF count=4000 offscreen=true candidate=true round=17 ns=1974 projections=0
BOUNDS-PERF count=4000 offscreen=true candidate=false round=17 ns=99097 projections=7800
BOUNDS-PERF count=4000 offscreen=true candidate=false round=18 ns=97803 projections=7800
BOUNDS-PERF count=4000 offscreen=true candidate=true round=18 ns=2034 projections=0
BOUNDS-PERF count=4000 offscreen=true candidate=true round=19 ns=1921 projections=0
BOUNDS-PERF count=4000 offscreen=true candidate=false round=19 ns=100078 projections=7800
```

## CI synchronization correction
The first full CI run timed out in the existing outside-region refusal test: its terminal could arrive before `startLookup` returned the ID into a nullable variable, so the collector discarded it. The test now awaits publication of that ID before matching a terminal. No routing source or timeout is changed; this corrects the fixture race rather than relaxing its assertion.
