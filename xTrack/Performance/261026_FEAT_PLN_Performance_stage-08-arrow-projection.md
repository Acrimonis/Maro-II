<!-- scope: feature -->
# Stage 8 — remaining repeated arrow projections

## Disposition
Retained the measured two-pass projection reuse candidate on an independent branch from upstream develop `b1f40aa211c02c2882bb676514bee753cecacebe`. This is app drawing work, not a routing algorithm change. Release-build evaluation has its own branch/record.

The existing selected-arrow resolver projected every anchor's two endpoints once for casing and again for colour. Its comment incorrectly said projection was shared. The candidate prepares visible positions once per draw in a reusable buffer, then keeps the original complete casing pass followed by the complete colour pass. Every draw recomputes projection, so pan/zoom/rotation cannot reuse stale screen positions. Sampling, anchor phase, float interpolation, viewport cull, bearing, colour resolution, casing geometry and drawing order are unchanged. Single-pass paths keep their original walk and allocate no projection buffer.

The buffer is per overlay, grows only to its sampled anchor count, and is bounded by that overlay's existing maxArrows limit. Three primitive arrays cost 12 bytes per retained anchor (24,000 bytes at the existing default limit), plus object/array headers and one scratch point. No dependency or global cache was added.

## Proportionate baseline and measurements
Linux x86_64 i5-1245U, JDK 17, `MARO_ARROW_PERF=1`, synthetic points with Mercator arithmetic. Twenty-four alternating reference/candidate rounds; first twelve warm up. Each sample includes 100 draws and the candidate buffer's first allocation. The retained reference repeats the original projection/interpolation/cull loop for both passes. This is JVM projection arithmetic, **not Android Projection or Canvas timing**; the initial draw's allocation latency is included but not isolated.

| Anchors | Off-screen | Candidate | Median ms / 100 draws | Range ms |
|---:|---|---|---:|---:|
| 8 | false | false | 1.749 | 0.375–5.937 |
| 8 | false | true | 0.561 | 0.245–3.222 |
| 8 | true | false | 0.419 | 0.416–0.554 |
| 8 | true | true | 0.208 | 0.206–0.225 |
| 600 | false | false | 24.759 | 24.565–25.065 |
| 600 | false | true | 13.109 | 13.003–13.225 |
| 600 | true | false | 23.785 | 23.662–23.958 |
| 600 | true | true | 11.821 | 11.735–12.013 |
| 2000 | false | false | 82.424 | 81.427–85.852 |
| 2000 | false | true | 43.463 | 43.049–45.354 |
| 2000 | true | false | 64.879 | 64.627–67.710 |
| 2000 | true | true | 31.831 | 31.720–32.195 |

Endpoint projection calls halve in every case: 4×anchors×draws → 2×anchors×draws. At 600 anchors, 240,000→120,000 calls; at the existing default maximum, 800,000→400,000. Both dense visible/off-screen ranges are disjoint in the candidate's favor. Sparse visible timing is noisy, while sparse off-screen is disjoint; no repeatable regression was observed in these synthetic cases. No gain is claimed for the untouched single-pass path.

## Tests and unmeasured work
Exact differential draw sequences cover both passes, pan, zoom, crossing segments, strict screen boundaries and empty/smaller later anchor sets. Tests also pin projection counts and bounded capacity. Existing direction and chevron geometry tests are run alongside the debug build; 14 tests reported zero failures/errors, with 1 opt-in benchmark skipped in the final correctness run (it passed in the measured run). Debug APK passed.

Android projection cost, Canvas draw time, frame tails, cold native behavior and real-device visual acceptance remain **unverified**. Stage 6's whole-overlay rejection can eliminate this work entirely off-screen; the gains are not additive there, and the combined branches have not been performance-certified. Other remaining routing reuse candidates have no new device bottleneck evidence and are not enabled. Full changed-revision checkpoint serialization also remains in Stage 1; a journal is not inferred from these arrow counters or implemented without its own durability and workload evidence.

## Warm samples
```text
ARROW-PERF count=8 offscreen=false candidate=false round=12 ns=1955292 reads=3200 capacity=0
ARROW-PERF count=8 offscreen=false candidate=true round=12 ns=533565 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=false candidate=true round=13 ns=588110 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=false candidate=false round=13 ns=1760963 reads=3200 capacity=0
ARROW-PERF count=8 offscreen=false candidate=false round=14 ns=2926337 reads=3200 capacity=0
ARROW-PERF count=8 offscreen=false candidate=true round=14 ns=618252 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=false candidate=true round=15 ns=650949 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=false candidate=false round=15 ns=5936966 reads=3200 capacity=0
ARROW-PERF count=8 offscreen=false candidate=false round=16 ns=3662170 reads=3200 capacity=0
ARROW-PERF count=8 offscreen=false candidate=true round=16 ns=670543 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=false candidate=true round=17 ns=3221819 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=false candidate=false round=17 ns=1971182 reads=3200 capacity=0
ARROW-PERF count=8 offscreen=false candidate=false round=18 ns=1737739 reads=3200 capacity=0
ARROW-PERF count=8 offscreen=false candidate=true round=18 ns=525686 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=false candidate=true round=19 ns=1619091 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=false candidate=false round=19 ns=1665355 reads=3200 capacity=0
ARROW-PERF count=8 offscreen=false candidate=false round=20 ns=1567892 reads=3200 capacity=0
ARROW-PERF count=8 offscreen=false candidate=true round=20 ns=462085 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=false candidate=true round=21 ns=420057 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=false candidate=false round=21 ns=1639305 reads=3200 capacity=0
ARROW-PERF count=8 offscreen=false candidate=false round=22 ns=478266 reads=3200 capacity=0
ARROW-PERF count=8 offscreen=false candidate=true round=22 ns=255723 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=false candidate=true round=23 ns=244517 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=false candidate=false round=23 ns=375306 reads=3200 capacity=0
ARROW-PERF count=8 offscreen=true candidate=false round=12 ns=417071 reads=3200 capacity=0
ARROW-PERF count=8 offscreen=true candidate=true round=12 ns=206651 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=true candidate=true round=13 ns=224854 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=true candidate=false round=13 ns=416196 reads=3200 capacity=0
ARROW-PERF count=8 offscreen=true candidate=false round=14 ns=429961 reads=3200 capacity=0
ARROW-PERF count=8 offscreen=true candidate=true round=14 ns=206815 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=true candidate=true round=15 ns=206717 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=true candidate=false round=15 ns=440083 reads=3200 capacity=0
ARROW-PERF count=8 offscreen=true candidate=false round=16 ns=554014 reads=3200 capacity=0
ARROW-PERF count=8 offscreen=true candidate=true round=16 ns=206649 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=true candidate=true round=17 ns=221917 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=true candidate=false round=17 ns=415776 reads=3200 capacity=0
ARROW-PERF count=8 offscreen=true candidate=false round=18 ns=419359 reads=3200 capacity=0
ARROW-PERF count=8 offscreen=true candidate=true round=18 ns=208862 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=true candidate=true round=19 ns=216633 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=true candidate=false round=19 ns=446499 reads=3200 capacity=0
ARROW-PERF count=8 offscreen=true candidate=false round=20 ns=419177 reads=3200 capacity=0
ARROW-PERF count=8 offscreen=true candidate=true round=20 ns=222255 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=true candidate=true round=21 ns=208646 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=true candidate=false round=21 ns=417738 reads=3200 capacity=0
ARROW-PERF count=8 offscreen=true candidate=false round=22 ns=445437 reads=3200 capacity=0
ARROW-PERF count=8 offscreen=true candidate=true round=22 ns=207603 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=true candidate=true round=23 ns=205566 reads=1600 capacity=8
ARROW-PERF count=8 offscreen=true candidate=false round=23 ns=417490 reads=3200 capacity=0
ARROW-PERF count=600 offscreen=false candidate=false round=12 ns=24751617 reads=240000 capacity=0
ARROW-PERF count=600 offscreen=false candidate=true round=12 ns=13115486 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=false candidate=true round=13 ns=13127199 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=false candidate=false round=13 ns=24837764 reads=240000 capacity=0
ARROW-PERF count=600 offscreen=false candidate=false round=14 ns=24757768 reads=240000 capacity=0
ARROW-PERF count=600 offscreen=false candidate=true round=14 ns=13133751 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=false candidate=true round=15 ns=13072919 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=false candidate=false round=15 ns=24787724 reads=240000 capacity=0
ARROW-PERF count=600 offscreen=false candidate=false round=16 ns=24693771 reads=240000 capacity=0
ARROW-PERF count=600 offscreen=false candidate=true round=16 ns=13112301 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=false candidate=true round=17 ns=13077901 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=false candidate=false round=17 ns=24853159 reads=240000 capacity=0
ARROW-PERF count=600 offscreen=false candidate=false round=18 ns=24696907 reads=240000 capacity=0
ARROW-PERF count=600 offscreen=false candidate=true round=18 ns=13225282 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=false candidate=true round=19 ns=13105065 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=false candidate=false round=19 ns=25065461 reads=240000 capacity=0
ARROW-PERF count=600 offscreen=false candidate=false round=20 ns=24723664 reads=240000 capacity=0
ARROW-PERF count=600 offscreen=false candidate=true round=20 ns=13133319 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=false candidate=true round=21 ns=13019787 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=false candidate=false round=21 ns=24889185 reads=240000 capacity=0
ARROW-PERF count=600 offscreen=false candidate=false round=22 ns=24759577 reads=240000 capacity=0
ARROW-PERF count=600 offscreen=false candidate=true round=22 ns=13002904 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=false candidate=true round=23 ns=13010122 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=false candidate=false round=23 ns=24564971 reads=240000 capacity=0
ARROW-PERF count=600 offscreen=true candidate=false round=12 ns=23733779 reads=240000 capacity=0
ARROW-PERF count=600 offscreen=true candidate=true round=12 ns=11817528 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=true candidate=true round=13 ns=11806387 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=true candidate=false round=13 ns=23793874 reads=240000 capacity=0
ARROW-PERF count=600 offscreen=true candidate=false round=14 ns=23794648 reads=240000 capacity=0
ARROW-PERF count=600 offscreen=true candidate=true round=14 ns=11891831 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=true candidate=true round=15 ns=11846037 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=true candidate=false round=15 ns=23916234 reads=240000 capacity=0
ARROW-PERF count=600 offscreen=true candidate=false round=16 ns=23958243 reads=240000 capacity=0
ARROW-PERF count=600 offscreen=true candidate=true round=16 ns=11913332 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=true candidate=true round=17 ns=12012870 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=true candidate=false round=17 ns=23776848 reads=240000 capacity=0
ARROW-PERF count=600 offscreen=true candidate=false round=18 ns=23705014 reads=240000 capacity=0
ARROW-PERF count=600 offscreen=true candidate=true round=18 ns=11734613 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=true candidate=true round=19 ns=11764704 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=true candidate=false round=19 ns=23662196 reads=240000 capacity=0
ARROW-PERF count=600 offscreen=true candidate=false round=20 ns=23744591 reads=240000 capacity=0
ARROW-PERF count=600 offscreen=true candidate=true round=20 ns=11745147 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=true candidate=true round=21 ns=11825102 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=true candidate=false round=21 ns=23792932 reads=240000 capacity=0
ARROW-PERF count=600 offscreen=true candidate=false round=22 ns=23844236 reads=240000 capacity=0
ARROW-PERF count=600 offscreen=true candidate=true round=22 ns=11859842 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=true candidate=true round=23 ns=11777814 reads=120000 capacity=600
ARROW-PERF count=600 offscreen=true candidate=false round=23 ns=23686326 reads=240000 capacity=0
ARROW-PERF count=2000 offscreen=false candidate=false round=12 ns=81968123 reads=800000 capacity=0
ARROW-PERF count=2000 offscreen=false candidate=true round=12 ns=43049148 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=false candidate=true round=13 ns=43073994 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=false candidate=false round=13 ns=82483586 reads=800000 capacity=0
ARROW-PERF count=2000 offscreen=false candidate=false round=14 ns=82003080 reads=800000 capacity=0
ARROW-PERF count=2000 offscreen=false candidate=true round=14 ns=43706990 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=false candidate=true round=15 ns=43258362 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=false candidate=false round=15 ns=82195810 reads=800000 capacity=0
ARROW-PERF count=2000 offscreen=false candidate=false round=16 ns=82448069 reads=800000 capacity=0
ARROW-PERF count=2000 offscreen=false candidate=true round=16 ns=43626046 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=false candidate=true round=17 ns=45155972 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=false candidate=false round=17 ns=85851987 reads=800000 capacity=0
ARROW-PERF count=2000 offscreen=false candidate=false round=18 ns=85412868 reads=800000 capacity=0
ARROW-PERF count=2000 offscreen=false candidate=true round=18 ns=45353600 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=false candidate=true round=19 ns=45102588 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=false candidate=false round=19 ns=85546987 reads=800000 capacity=0
ARROW-PERF count=2000 offscreen=false candidate=false round=20 ns=82477170 reads=800000 capacity=0
ARROW-PERF count=2000 offscreen=false candidate=true round=20 ns=43389608 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=false candidate=true round=21 ns=43537206 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=false candidate=false round=21 ns=82400695 reads=800000 capacity=0
ARROW-PERF count=2000 offscreen=false candidate=false round=22 ns=82208382 reads=800000 capacity=0
ARROW-PERF count=2000 offscreen=false candidate=true round=22 ns=43290545 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=false candidate=true round=23 ns=43300992 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=false candidate=false round=23 ns=81426829 reads=800000 capacity=0
ARROW-PERF count=2000 offscreen=true candidate=false round=12 ns=65903921 reads=800000 capacity=0
ARROW-PERF count=2000 offscreen=true candidate=true round=12 ns=31823126 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=true candidate=true round=13 ns=31826187 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=true candidate=false round=13 ns=65290440 reads=800000 capacity=0
ARROW-PERF count=2000 offscreen=true candidate=false round=14 ns=67709883 reads=800000 capacity=0
ARROW-PERF count=2000 offscreen=true candidate=true round=14 ns=31951110 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=true candidate=true round=15 ns=31851655 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=true candidate=false round=15 ns=64699700 reads=800000 capacity=0
ARROW-PERF count=2000 offscreen=true candidate=false round=16 ns=64790219 reads=800000 capacity=0
ARROW-PERF count=2000 offscreen=true candidate=true round=16 ns=31889255 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=true candidate=true round=17 ns=31738102 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=true candidate=false round=17 ns=65115890 reads=800000 capacity=0
ARROW-PERF count=2000 offscreen=true candidate=false round=18 ns=65018059 reads=800000 capacity=0
ARROW-PERF count=2000 offscreen=true candidate=true round=18 ns=31789724 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=true candidate=true round=19 ns=32194538 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=true candidate=false round=19 ns=64943538 reads=800000 capacity=0
ARROW-PERF count=2000 offscreen=true candidate=false round=20 ns=64813789 reads=800000 capacity=0
ARROW-PERF count=2000 offscreen=true candidate=true round=20 ns=31836035 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=true candidate=true round=21 ns=31720378 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=true candidate=false round=21 ns=64750166 reads=800000 capacity=0
ARROW-PERF count=2000 offscreen=true candidate=false round=22 ns=64627095 reads=800000 capacity=0
ARROW-PERF count=2000 offscreen=true candidate=true round=22 ns=31963087 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=true candidate=true round=23 ns=31747688 reads=400000 capacity=2000
ARROW-PERF count=2000 offscreen=true candidate=false round=23 ns=64782919 reads=800000 capacity=0
```
