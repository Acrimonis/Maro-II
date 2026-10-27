# Stage 2 — track library

Status: retained for review on `codex/performance-02-library`, based on freshly fetched upstream develop `b1f40aa211c02c2882bb676514bee753cecacebe`. Independent of Stage 1; targets the same fork baseline branch as [Stage 1 PR #9](https://github.com/noahb/Maro-II/pull/9).

## Changes

- Single saves update the changed summary from the track already in memory. Directory enumeration preserves the full rebuild's ordering without decoding unchanged tracks. The complete summary projection is shared with the retained recovery/full-rebuild path.
- `saveAll` publishes an import batch's index once; `deleteAll` performs one marker cascade and one index publication. Imports and merged-source deletion use these paths. Merge persistence deliberately retains save → marker reassignment → source deletion order, so merging uses two index writes.
- The index is invalidated before changing track files. A cancelled/failed batch leaves individually atomic track files and a missing derived index that is rebuilt on the next read. Missing, corrupt and old-version index recovery remain available. Initial/recovery reads for mutations avoid publishing an intermediate index.
- Metadata read-modify-write holds the repository mutex throughout; imports invalidate affected detail-cache entries. Source loading, filtering, sorting and merge computation run within `Dispatchers.Default`; UI publication remains in the ViewModel coroutine. The merger's numeric/point algorithm is unchanged.

No dependency, format, GPS cadence, routing code or release configuration changed. Multi-file batches are not transactions: a partially completed batch remains recoverable through the existing per-track atomic files. The strongest objection to the optimization is the additional index invalidation/recovery step; failure tests pin that step rather than assuming batch atomicity.

## Baseline and measurements

The baseline benchmark ran on upstream code before implementation, with only optional decode/index-write counters added. Both runs used the same synthetic library, filesystem and JVM/toolchain as Stage 1. Each track contains 1,500 fixed points; libraries contain 10 or 200 tracks. The initial index is built outside timing. Seven fresh directories per scenario: two warmup rounds, then five reported rounds. All file I/O, protobuf work and atomic replacements of the mutation are timed. The original workload used single-save/delete loops; the candidate uses the batch entry points. The optional observer is null in production; its overhead has not been measured independently.

| Tracks | Workload | Median ms, before → after | Range ms, before → after | Track decodes | Index writes |
|---|---|---|---|---|---|
| 10 | rename | 57.603 → 11.040 | 49.099–69.667 → 10.124–14.869 | 11 → 1 | 1 → 1 |
| 10 | import | 269.271 → 9.101 | 265.382–272.900 → 8.098–16.709 | 65 → 0 | 5 → 1 |
| 10 | merge-delete | 318.218 → 10.671 | 314.946–321.551 → 9.690–10.858 | 51 → 0 | 6 → 2 |
| 200 | rename | 814.199 → 7.166 | 803.769–819.246 → 6.734–7.450 | 201 → 1 | 1 → 1 |
| 200 | import | 4098.233 → 5.244 | 3772.202–4129.976 → 5.078–5.561 | 1015 → 0 | 5 → 1 |
| 200 | merge-delete | 4930.586 → 8.160 | 4917.447–5027.821 → 7.578–8.640 | 1191 → 0 | 6 → 2 |

Import adds five tracks. Merge/delete combines five tracks and deletes those five sources; source tracks are preloaded in this micro-workload, and marker persistence is absent from it. Renaming includes loading the affected track. All six elapsed-time ranges are disjoint, with lower candidate times; counts confirm that unchanged file decodes disappeared. This establishes the benefit for these synthetic filesystem workloads, not device responsiveness or cold flash performance.

## Validation

Debug APK built. Final related suite: 36 tests passed, zero failures/errors/skips. The performance test passed on both baseline and candidate.

`TrackRepositoryIncrementalTest` differentially compares complete summaries, order and classification against a forced full rebuild. It pins batch read/write counts; corrupt-index mutation repair; failed index publication and partially failed batch recovery across a new repository instance; concurrent metadata edits; and preservation of user/active-track markers during a batch cascade. Existing durability, safe-ID, route-summary, GPS-source, position-classification and recorder lifecycle tests passed.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest \
  --tests '*TrackRepository*Test' --tests '*TrackRecorderLifecycleTest' \
  --tests '*TrackGpsSourcedTest' --tests '*TrackPositionTest' --no-daemon
MARO_LIBRARY_PERF=1 ./gradlew :app:testDebugUnitTest \
  --tests '*TrackLibraryPerformanceTest' -Pmaro.testStdout=true --no-daemon
```

The full rebuild remains the differential reference in production recovery and tests. The baseline timing record below was captured before changing the mutation path; the parent commit retains that path. Source IDs, timestamps, point lists and the merger's computations are not approximated.

Unmeasured: device flash latency, UI frame tails, CPU/allocation/peak-memory totals, large real imported marker bundles, power-loss durability, cold filesystem caches and bake-dependent scenarios. The worker dispatch is compiled but device main-thread/frame behavior remains unverified.

## Paired workload records

Runs were sequential (baseline, then candidate), not interleaved across revisions. The repeat spread is reported above; the work counters provide independent evidence of the removed work.

| Tracks | Workload | Round | Baseline ms | Candidate ms |
|---|---|---|---|---|
| 10 | rename | 0 | 88.296 | 18.415 |
| 10 | rename | 1 | 80.029 | 14.128 |
| 10 | rename | 2 | 69.667 | 14.869 |
| 10 | rename | 3 | 62.755 | 11.040 |
| 10 | rename | 4 | 57.603 | 11.127 |
| 10 | rename | 5 | 50.888 | 10.859 |
| 10 | rename | 6 | 49.099 | 10.124 |
| 10 | import | 0 | 282.668 | 12.616 |
| 10 | import | 1 | 272.995 | 11.789 |
| 10 | import | 2 | 269.271 | 16.709 |
| 10 | import | 3 | 269.614 | 9.101 |
| 10 | import | 4 | 265.468 | 9.960 |
| 10 | import | 5 | 272.900 | 8.446 |
| 10 | import | 6 | 265.382 | 8.098 |
| 10 | merge-delete | 0 | 348.913 | 29.736 |
| 10 | merge-delete | 1 | 332.906 | 12.331 |
| 10 | merge-delete | 2 | 321.551 | 10.838 |
| 10 | merge-delete | 3 | 318.218 | 10.333 |
| 10 | merge-delete | 4 | 318.955 | 10.671 |
| 10 | merge-delete | 5 | 315.491 | 9.690 |
| 10 | merge-delete | 6 | 314.946 | 10.858 |
| 200 | rename | 0 | 808.738 | 8.059 |
| 200 | rename | 1 | 818.948 | 7.284 |
| 200 | rename | 2 | 814.199 | 6.868 |
| 200 | rename | 3 | 819.246 | 7.450 |
| 200 | rename | 4 | 817.623 | 7.191 |
| 200 | rename | 5 | 805.782 | 7.166 |
| 200 | rename | 6 | 803.769 | 6.734 |
| 200 | import | 0 | 4118.348 | 5.406 |
| 200 | import | 1 | 4236.006 | 5.033 |
| 200 | import | 2 | 4098.233 | 5.199 |
| 200 | import | 3 | 4095.946 | 5.561 |
| 200 | import | 4 | 4126.430 | 5.078 |
| 200 | import | 5 | 4129.976 | 5.244 |
| 200 | import | 6 | 3772.202 | 5.367 |
| 200 | merge-delete | 0 | 4735.768 | 9.966 |
| 200 | merge-delete | 1 | 5356.801 | 8.353 |
| 200 | merge-delete | 2 | 4917.447 | 8.640 |
| 200 | merge-delete | 3 | 4926.007 | 7.796 |
| 200 | merge-delete | 4 | 4930.586 | 8.160 |
| 200 | merge-delete | 5 | 5027.821 | 7.578 |
| 200 | merge-delete | 6 | 5008.498 | 8.366 |
