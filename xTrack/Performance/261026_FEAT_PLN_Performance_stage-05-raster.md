<!-- scope: feature -->
# Stage 5 — cooperative rasters and bounded cache transfers

## Disposition
Retained on an independent branch from upstream develop `b1f40aa211c02c2882bb676514bee753cecacebe`. No routing, raster resolution, colour, corner test or progress cadence changed. Device/baked-region performance is **unverified**.

Depth and warning pixel loops check cancellation before allocation, between rows and before final progress/bitmap creation. The ViewModel checks before cache work and publication, passes its coroutine cancellation to builders, and moves cache writes to Dispatchers.IO. Existing owner/generation and sibling requeue logic remains intact.

RawBuf retains its big-endian dimensions and exact pixel order. Cache reads/writes use bounded buffers from `raster.bufferPixels`; writes publish a temporary file atomically after completion, with the existing-file fallback for filesystems lacking atomic moves. Cancellation removes the temporary file and leaves the previous entry intact. Reads propagate cancellation without evicting a valid entry. Small rasters retain the original single-buffer read when they fit the bound. Bitmap row spans preserve linear chunk order, including partial rows.

## Proportionate measurements
Linux x86_64 i5-1245U, temporary JDK 17, existing Gradle toolchain, synthetic data only. `MARO_RASTER_PERF=1` runs 24 alternating reference/candidate rounds; the first 12 are warmup. Raster references are the upstream pixel kernels with only Android bitmap conversion omitted. The cache reference retains whole-buffer extraction/transfer. This measures JVM kernels and raw file transfer, not Android Bitmap JNI or physical flash.

| Workload | Side | Warning | Candidate | Median ms | Range ms |
|---|---:|---|---|---:|---:|
| RASTER | 256 | false | false | 1.127 | 1.100–1.428 |
| RASTER | 256 | false | true | 1.134 | 1.103–1.520 |
| RASTER | 256 | true | false | 0.559 | 0.511–1.422 |
| RASTER | 256 | true | true | 0.552 | 0.515–0.937 |
| RASTER | 2048 | false | false | 39.972 | 38.100–46.030 |
| RASTER | 2048 | false | true | 39.550 | 37.681–39.742 |
| RASTER | 2048 | true | false | 12.924 | 12.398–14.261 |
| RASTER | 2048 | true | true | 12.093 | 11.630–13.498 |
| BUFFER | 256 | — | false | 0.536 | 0.491–0.794 |
| BUFFER | 256 | — | true | 0.539 | 0.505–0.733 |
| BUFFER | 2048 | — | false | 24.352 | 22.429–26.877 |
| BUFFER | 2048 | — | true | 11.781 | 11.080–13.685 |
| CANCEL | 256 | — | false | 0.908 | 0.571–0.956 |
| CANCEL | 256 | — | true | 0.484 | 0.455–0.705 |
| CANCEL | 2048 | — | false | 12.830 | 12.483–13.487 |
| CANCEL | 2048 | — | true | 0.667 | 0.567–1.758 |

Large warning cancellation after ten completed rows: coast calls 4,348,944→21,248; the old kernel completes all rows. Large cache transfer working buffers: approximately 32 MiB→2 MiB per read/write (excludes the required displayed bitmap and input grid). Small rasters keep the same bounded-size whole-buffer path. Complete runs retain identical water-query counts and exact ARGB arrays. Depth completion timings overlap; no speedup claimed. Warning completion varies with JIT/host load, so cancellation and cache-buffer gains are the retained benefits.

The initial always-chunked small read regressed (0.754→0.898 ms in one paired run); that path was rejected for small rasters. The bounded original-read fallback measured 0.536→0.539 ms after longer warmup, within noise, while large transfers measured 24.352→11.781 ms with disjoint ranges. Cancellation measured 12.830→0.667 ms on the large workload. No repeatable uncancelled regression remained in the tested cases.

## Validation and gaps
Debug APK passed. Pixel/reference, cancellation, raw-byte round trip, partial reads/writes, invalid dimensions/truncation, rectangular transfer coverage and cancellation-safe replacement tests passed. Final focused run: {'tests': 8, 'failures': 0, 'errors': 0, 'skipped': 0}; DepthColorRamp tests also passed in the preceding debug-build run. No new dependency.

Real Bitmap creation/getPixels/setPixels costs, end-to-end cache timing on Android storage, rendering frame tails, peak process memory, supersession UI traces, native bitmap reclamation and real baked-region runs remain unverified. Cancellation is bounded by one raster row or one transfer operation, not a hard real-time guarantee. No bitmap already published/shared is recycled.

## Retained warm samples
```text
RASTER-PERF side=256 warning=false candidate=false round=12 ns=1427991 reads=0 checks=0
RASTER-PERF side=256 warning=false candidate=true round=12 ns=1455647 reads=0 checks=258
RASTER-PERF side=256 warning=false candidate=true round=13 ns=1405402 reads=0 checks=258
RASTER-PERF side=256 warning=false candidate=false round=13 ns=1406374 reads=0 checks=0
RASTER-PERF side=256 warning=false candidate=false round=14 ns=1398524 reads=0 checks=0
RASTER-PERF side=256 warning=false candidate=true round=14 ns=1414300 reads=0 checks=258
RASTER-PERF side=256 warning=false candidate=true round=15 ns=1519776 reads=0 checks=258
RASTER-PERF side=256 warning=false candidate=false round=15 ns=1414799 reads=0 checks=0
RASTER-PERF side=256 warning=false candidate=false round=16 ns=1105924 reads=0 checks=0
RASTER-PERF side=256 warning=false candidate=true round=16 ns=1403326 reads=0 checks=258
RASTER-PERF side=256 warning=false candidate=true round=17 ns=1115884 reads=0 checks=258
RASTER-PERF side=256 warning=false candidate=false round=17 ns=1101930 reads=0 checks=0
RASTER-PERF side=256 warning=false candidate=false round=18 ns=1120746 reads=0 checks=0
RASTER-PERF side=256 warning=false candidate=true round=18 ns=1110581 reads=0 checks=258
RASTER-PERF side=256 warning=false candidate=true round=19 ns=1103423 reads=0 checks=258
RASTER-PERF side=256 warning=false candidate=false round=19 ns=1100138 reads=0 checks=0
RASTER-PERF side=256 warning=false candidate=false round=20 ns=1105249 reads=0 checks=0
RASTER-PERF side=256 warning=false candidate=true round=20 ns=1114521 reads=0 checks=258
RASTER-PERF side=256 warning=false candidate=true round=21 ns=1107735 reads=0 checks=258
RASTER-PERF side=256 warning=false candidate=false round=21 ns=1133257 reads=0 checks=0
RASTER-PERF side=256 warning=false candidate=false round=22 ns=1120678 reads=0 checks=0
RASTER-PERF side=256 warning=false candidate=true round=22 ns=1151904 reads=0 checks=258
RASTER-PERF side=256 warning=false candidate=true round=23 ns=1109059 reads=0 checks=258
RASTER-PERF side=256 warning=false candidate=false round=23 ns=1204583 reads=0 checks=0
RASTER-PERF side=256 warning=true candidate=false round=12 ns=934113 reads=67960 checks=0
RASTER-PERF side=256 warning=true candidate=true round=12 ns=932514 reads=67960 checks=258
RASTER-PERF side=256 warning=true candidate=true round=13 ns=937087 reads=67960 checks=258
RASTER-PERF side=256 warning=true candidate=false round=13 ns=969143 reads=67960 checks=0
RASTER-PERF side=256 warning=true candidate=false round=14 ns=878177 reads=67960 checks=0
RASTER-PERF side=256 warning=true candidate=true round=14 ns=892237 reads=67960 checks=258
RASTER-PERF side=256 warning=true candidate=true round=15 ns=515191 reads=67960 checks=258
RASTER-PERF side=256 warning=true candidate=false round=15 ns=1421584 reads=67960 checks=0
RASTER-PERF side=256 warning=true candidate=false round=16 ns=572694 reads=67960 checks=0
RASTER-PERF side=256 warning=true candidate=true round=16 ns=581137 reads=67960 checks=258
RASTER-PERF side=256 warning=true candidate=true round=17 ns=543686 reads=67960 checks=258
RASTER-PERF side=256 warning=true candidate=false round=17 ns=561943 reads=67960 checks=0
RASTER-PERF side=256 warning=true candidate=false round=18 ns=556583 reads=67960 checks=0
RASTER-PERF side=256 warning=true candidate=true round=18 ns=560158 reads=67960 checks=258
RASTER-PERF side=256 warning=true candidate=true round=19 ns=538181 reads=67960 checks=258
RASTER-PERF side=256 warning=true candidate=false round=19 ns=542213 reads=67960 checks=0
RASTER-PERF side=256 warning=true candidate=false round=20 ns=531126 reads=67960 checks=0
RASTER-PERF side=256 warning=true candidate=true round=20 ns=585644 reads=67960 checks=258
RASTER-PERF side=256 warning=true candidate=true round=21 ns=535590 reads=67960 checks=258
RASTER-PERF side=256 warning=true candidate=false round=21 ns=542666 reads=67960 checks=0
RASTER-PERF side=256 warning=true candidate=false round=22 ns=545760 reads=67960 checks=0
RASTER-PERF side=256 warning=true candidate=true round=22 ns=538867 reads=67960 checks=258
RASTER-PERF side=256 warning=true candidate=true round=23 ns=532277 reads=67960 checks=258
RASTER-PERF side=256 warning=true candidate=false round=23 ns=510606 reads=67960 checks=0
RASTER-PERF side=2048 warning=false candidate=false round=12 ns=41159662 reads=0 checks=0
RASTER-PERF side=2048 warning=false candidate=true round=12 ns=39697924 reads=0 checks=2050
RASTER-PERF side=2048 warning=false candidate=true round=13 ns=39742389 reads=0 checks=2050
RASTER-PERF side=2048 warning=false candidate=false round=13 ns=40138420 reads=0 checks=0
RASTER-PERF side=2048 warning=false candidate=false round=14 ns=39805724 reads=0 checks=0
RASTER-PERF side=2048 warning=false candidate=true round=14 ns=39551451 reads=0 checks=2050
RASTER-PERF side=2048 warning=false candidate=true round=15 ns=39681592 reads=0 checks=2050
RASTER-PERF side=2048 warning=false candidate=false round=15 ns=43961946 reads=0 checks=0
RASTER-PERF side=2048 warning=false candidate=false round=16 ns=38570855 reads=0 checks=0
RASTER-PERF side=2048 warning=false candidate=true round=16 ns=39721717 reads=0 checks=2050
RASTER-PERF side=2048 warning=false candidate=true round=17 ns=39424439 reads=0 checks=2050
RASTER-PERF side=2048 warning=false candidate=false round=17 ns=40420509 reads=0 checks=0
RASTER-PERF side=2048 warning=false candidate=false round=18 ns=39328639 reads=0 checks=0
RASTER-PERF side=2048 warning=false candidate=true round=18 ns=39729623 reads=0 checks=2050
RASTER-PERF side=2048 warning=false candidate=true round=19 ns=39373181 reads=0 checks=2050
RASTER-PERF side=2048 warning=false candidate=false round=19 ns=40439457 reads=0 checks=0
RASTER-PERF side=2048 warning=false candidate=false round=20 ns=39373676 reads=0 checks=0
RASTER-PERF side=2048 warning=false candidate=true round=20 ns=39456559 reads=0 checks=2050
RASTER-PERF side=2048 warning=false candidate=true round=21 ns=37681457 reads=0 checks=2050
RASTER-PERF side=2048 warning=false candidate=false round=21 ns=39732206 reads=0 checks=0
RASTER-PERF side=2048 warning=false candidate=false round=22 ns=38099998 reads=0 checks=0
RASTER-PERF side=2048 warning=false candidate=true round=22 ns=39548584 reads=0 checks=2050
RASTER-PERF side=2048 warning=false candidate=true round=23 ns=39498128 reads=0 checks=2050
RASTER-PERF side=2048 warning=false candidate=false round=23 ns=46029693 reads=0 checks=0
RASTER-PERF side=2048 warning=true candidate=false round=12 ns=12397503 reads=4348944 checks=0
RASTER-PERF side=2048 warning=true candidate=true round=12 ns=12070565 reads=4348944 checks=2050
RASTER-PERF side=2048 warning=true candidate=true round=13 ns=11841957 reads=4348944 checks=2050
RASTER-PERF side=2048 warning=true candidate=false round=13 ns=13459236 reads=4348944 checks=0
RASTER-PERF side=2048 warning=true candidate=false round=14 ns=13281253 reads=4348944 checks=0
RASTER-PERF side=2048 warning=true candidate=true round=14 ns=12349443 reads=4348944 checks=2050
RASTER-PERF side=2048 warning=true candidate=true round=15 ns=13498013 reads=4348944 checks=2050
RASTER-PERF side=2048 warning=true candidate=false round=15 ns=13455650 reads=4348944 checks=0
RASTER-PERF side=2048 warning=true candidate=false round=16 ns=12623349 reads=4348944 checks=0
RASTER-PERF side=2048 warning=true candidate=true round=16 ns=12584102 reads=4348944 checks=2050
RASTER-PERF side=2048 warning=true candidate=true round=17 ns=11635693 reads=4348944 checks=2050
RASTER-PERF side=2048 warning=true candidate=false round=17 ns=12749150 reads=4348944 checks=0
RASTER-PERF side=2048 warning=true candidate=false round=18 ns=12592637 reads=4348944 checks=0
RASTER-PERF side=2048 warning=true candidate=true round=18 ns=11731361 reads=4348944 checks=2050
RASTER-PERF side=2048 warning=true candidate=true round=19 ns=12556504 reads=4348944 checks=2050
RASTER-PERF side=2048 warning=true candidate=false round=19 ns=14260888 reads=4348944 checks=0
RASTER-PERF side=2048 warning=true candidate=false round=20 ns=12679819 reads=4348944 checks=0
RASTER-PERF side=2048 warning=true candidate=true round=20 ns=11928070 reads=4348944 checks=2050
RASTER-PERF side=2048 warning=true candidate=true round=21 ns=11629670 reads=4348944 checks=2050
RASTER-PERF side=2048 warning=true candidate=false round=21 ns=12975814 reads=4348944 checks=0
RASTER-PERF side=2048 warning=true candidate=false round=22 ns=13071313 reads=4348944 checks=0
RASTER-PERF side=2048 warning=true candidate=true round=22 ns=12652180 reads=4348944 checks=2050
RASTER-PERF side=2048 warning=true candidate=true round=23 ns=12115708 reads=4348944 checks=2050
RASTER-PERF side=2048 warning=true candidate=false round=23 ns=12873131 reads=4348944 checks=0
BUFFER-PERF side=256 candidate=false round=12 ns=507577
BUFFER-PERF side=256 candidate=true round=12 ns=545547
BUFFER-PERF side=256 candidate=true round=13 ns=506071
BUFFER-PERF side=256 candidate=false round=13 ns=602300
BUFFER-PERF side=256 candidate=false round=14 ns=677446
BUFFER-PERF side=256 candidate=true round=14 ns=728138
BUFFER-PERF side=256 candidate=true round=15 ns=694818
BUFFER-PERF side=256 candidate=false round=15 ns=726905
BUFFER-PERF side=256 candidate=false round=16 ns=543978
BUFFER-PERF side=256 candidate=true round=16 ns=584496
BUFFER-PERF side=256 candidate=true round=17 ns=536650
BUFFER-PERF side=256 candidate=false round=17 ns=531492
BUFFER-PERF side=256 candidate=false round=18 ns=498213
BUFFER-PERF side=256 candidate=true round=18 ns=505084
BUFFER-PERF side=256 candidate=true round=19 ns=517708
BUFFER-PERF side=256 candidate=false round=19 ns=495656
BUFFER-PERF side=256 candidate=false round=20 ns=490908
BUFFER-PERF side=256 candidate=true round=20 ns=527585
BUFFER-PERF side=256 candidate=true round=21 ns=540402
BUFFER-PERF side=256 candidate=false round=21 ns=540027
BUFFER-PERF side=256 candidate=false round=22 ns=499373
BUFFER-PERF side=256 candidate=true round=22 ns=520246
BUFFER-PERF side=256 candidate=true round=23 ns=733430
BUFFER-PERF side=256 candidate=false round=23 ns=794292
BUFFER-PERF side=2048 candidate=false round=12 ns=24811293
BUFFER-PERF side=2048 candidate=true round=12 ns=11750233
BUFFER-PERF side=2048 candidate=true round=13 ns=13166089
BUFFER-PERF side=2048 candidate=false round=13 ns=23649254
BUFFER-PERF side=2048 candidate=false round=14 ns=22429279
BUFFER-PERF side=2048 candidate=true round=14 ns=13318488
BUFFER-PERF side=2048 candidate=true round=15 ns=11395595
BUFFER-PERF side=2048 candidate=false round=15 ns=25390928
BUFFER-PERF side=2048 candidate=false round=16 ns=26877021
BUFFER-PERF side=2048 candidate=true round=16 ns=11207539
BUFFER-PERF side=2048 candidate=true round=17 ns=13684512
BUFFER-PERF side=2048 candidate=false round=17 ns=22643126
BUFFER-PERF side=2048 candidate=false round=18 ns=26288204
BUFFER-PERF side=2048 candidate=true round=18 ns=13231078
BUFFER-PERF side=2048 candidate=true round=19 ns=11080199
BUFFER-PERF side=2048 candidate=false round=19 ns=24297292
BUFFER-PERF side=2048 candidate=false round=20 ns=22985222
BUFFER-PERF side=2048 candidate=true round=20 ns=11409085
BUFFER-PERF side=2048 candidate=true round=21 ns=11618970
BUFFER-PERF side=2048 candidate=false round=21 ns=23791172
BUFFER-PERF side=2048 candidate=false round=22 ns=25195635
BUFFER-PERF side=2048 candidate=true round=22 ns=11812620
BUFFER-PERF side=2048 candidate=true round=23 ns=12943472
BUFFER-PERF side=2048 candidate=false round=23 ns=24405911
CANCEL-PERF side=256 candidate=false round=12 ns=956247 reads=67960
CANCEL-PERF side=256 candidate=true round=12 ns=704999 reads=2668
CANCEL-PERF side=256 candidate=true round=13 ns=488430 reads=2668
CANCEL-PERF side=256 candidate=false round=13 ns=913893 reads=67960
CANCEL-PERF side=256 candidate=false round=14 ns=909032 reads=67960
CANCEL-PERF side=256 candidate=true round=14 ns=489081 reads=2668
CANCEL-PERF side=256 candidate=true round=15 ns=464040 reads=2668
CANCEL-PERF side=256 candidate=false round=15 ns=918455 reads=67960
CANCEL-PERF side=256 candidate=false round=16 ns=909427 reads=67960
CANCEL-PERF side=256 candidate=true round=16 ns=479061 reads=2668
CANCEL-PERF side=256 candidate=true round=17 ns=459252 reads=2668
CANCEL-PERF side=256 candidate=false round=17 ns=904714 reads=67960
CANCEL-PERF side=256 candidate=false round=18 ns=906125 reads=67960
CANCEL-PERF side=256 candidate=true round=18 ns=454666 reads=2668
CANCEL-PERF side=256 candidate=true round=19 ns=462922 reads=2668
CANCEL-PERF side=256 candidate=false round=19 ns=905898 reads=67960
CANCEL-PERF side=256 candidate=false round=20 ns=918344 reads=67960
CANCEL-PERF side=256 candidate=true round=20 ns=503086 reads=2668
CANCEL-PERF side=256 candidate=true round=21 ns=511348 reads=2668
CANCEL-PERF side=256 candidate=false round=21 ns=614761 reads=67960
CANCEL-PERF side=256 candidate=false round=22 ns=570565 reads=67960
CANCEL-PERF side=256 candidate=true round=22 ns=506055 reads=2668
CANCEL-PERF side=256 candidate=true round=23 ns=469170 reads=2668
CANCEL-PERF side=256 candidate=false round=23 ns=576925 reads=67960
CANCEL-PERF side=2048 candidate=false round=12 ns=12548546 reads=4348944
CANCEL-PERF side=2048 candidate=true round=12 ns=637370 reads=21248
CANCEL-PERF side=2048 candidate=true round=13 ns=593524 reads=21248
CANCEL-PERF side=2048 candidate=false round=13 ns=13288655 reads=4348944
CANCEL-PERF side=2048 candidate=false round=14 ns=12758375 reads=4348944
CANCEL-PERF side=2048 candidate=true round=14 ns=689952 reads=21248
CANCEL-PERF side=2048 candidate=true round=15 ns=567454 reads=21248
CANCEL-PERF side=2048 candidate=false round=15 ns=12615009 reads=4348944
CANCEL-PERF side=2048 candidate=false round=16 ns=12676409 reads=4348944
CANCEL-PERF side=2048 candidate=true round=16 ns=1502685 reads=21248
CANCEL-PERF side=2048 candidate=true round=17 ns=644090 reads=21248
CANCEL-PERF side=2048 candidate=false round=17 ns=12775678 reads=4348944
CANCEL-PERF side=2048 candidate=false round=18 ns=12899621 reads=4348944
CANCEL-PERF side=2048 candidate=true round=18 ns=623803 reads=21248
CANCEL-PERF side=2048 candidate=true round=19 ns=585493 reads=21248
CANCEL-PERF side=2048 candidate=false round=19 ns=12482731 reads=4348944
CANCEL-PERF side=2048 candidate=false round=20 ns=13486978 reads=4348944
CANCEL-PERF side=2048 candidate=true round=20 ns=1758255 reads=21248
CANCEL-PERF side=2048 candidate=true round=21 ns=713226 reads=21248
CANCEL-PERF side=2048 candidate=false round=21 ns=12883953 reads=4348944
CANCEL-PERF side=2048 candidate=false round=22 ns=12999722 reads=4348944
CANCEL-PERF side=2048 candidate=true round=22 ns=1025649 reads=21248
CANCEL-PERF side=2048 candidate=true round=23 ns=986537 reads=21248
CANCEL-PERF side=2048 candidate=false round=23 ns=12907498 reads=4348944
```
