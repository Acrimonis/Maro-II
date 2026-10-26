# Stage 1 — recording

Status: retained for review on `codex/performance-01-recording`; desktop acceptance only. Device/storage performance remains unverified.

## Baseline and scope

Parent: upstream develop `b1f40aa211c02c2882bb676514bee753cecacebe`. Before editing, ancestry and source inspection confirmed PR #382's shared forced-crossing probe/grown grid, primitive search heap and point maps, canonical route drawing and boundary splitting. No routing source changed. The fork baseline is published for review in [PR #8](https://github.com/noahb/Maro-II/pull/8); this stage targets that unprotected baseline branch.

The requested follow-up prompt file was absent. The user's explicit stage order governs this implementation; the older implementation prompt was not used to reorder it.

## Change and preservation

`RecordingPoints` owns mutable chunks on the recorder lane. Each publication shares sealed chunks and copies only the bounded tail; `snapshotPoints`, asynchronous checkpoint serialization, metadata copies, resumed recording, GAP insertion and finalization retain stable ordered lists. The tail bound comes from `tracking.pointChunkSize` in `maro.properties`. No flattening getter runs per point. Failure to finalize rebuilds the writer from the retained finalized snapshot before accepting later samples.

`currentTrack` publishes a track and a monotonically increasing state revision together. Equal assignments do not advance it. `RecordingCheckpoint` acknowledges a revision only after saving succeeds, serializes overlapping requests, and ignores already-persisted or older revisions. Resume-time checkpoint deletion invalidates that acknowledgement under the same lock, including failed/cancelled deletions, so the file can be recreated. Metadata, markers and demo-state changes participate through the track setter.

The original point-list append is retained in the differential tests and benchmark. Point values/order, serialized format, checkpoint interval, atomic repository writes, recovery rules, GPS sampling and simplification rules remain unchanged. There is no journal or new dependency.

## Measurements

Linux x86-64, Intel Core i5-1245U, Temurin 17.0.21. Each scenario precreates identical points, alternates reference/candidate order for ten rounds, and includes track copies, snapshot publication, protobuf encoding and revision checks. Unless idle attempts are specified, it serializes every 30 points. Idle cases append the whole recording then attempt 120 unchanged checkpoints. Each checkpoint output is consumed; moving cases assert identical byte totals and write counts.

Medians below use rounds 3–9. Allocations are current-thread allocated bytes, not retained heap. CPU is current-thread CPU time; elapsed includes GC pauses. Run 1 overlapped initial APK packaging in its early rounds; run 2 ran with all build tasks up-to-date. First-use/class-loading and true cold filesystem timings are not established by these runs.

| Run | Points / idle attempts | Elapsed ms, before → after | CPU ms, before → after | Allocated MB, before → after | Checkpoints, before → after |
|---|---|---|---|---|---|
| 1 | 120 / 0 | 1.161 → 1.114 | 1.172 → 1.127 | 0.186 → 0.156 | 4 → 4 |
| 1 | 3,600 / 0 | 106.951 → 100.897 | 106.965 → 99.602 | 130.991 → 81.046 | 120 → 120 |
| 1 | 21,600 / 0 | 2795.680 → 2612.179 | 2779.863 → 2603.205 | 4725.898 → 2871.109 | 720 → 720 |
| 1 | 21,600 / 120 | 1062.189 → 9.573 | 1056.818 → 9.585 | 2842.726 → 23.236 | 120 → 1 |
| 2 | 120 / 0 | 0.947 → 0.936 | 0.952 → 0.895 | 0.186 → 0.156 | 4 → 4 |
| 2 | 3,600 / 0 | 75.279 → 71.770 | 74.309 → 71.790 | 130.991 → 81.046 | 120 → 120 |
| 2 | 21,600 / 0 | 2783.710 → 2614.509 | 2770.613 → 2600.610 | 4725.898 → 2871.108 | 720 → 720 |
| 2 | 21,600 / 120 | 1068.057 → 9.439 | 1060.751 → 9.447 | 2842.732 → 23.236 | 120 → 1 |

Moving workloads serialize the same bytes (120 points: 8,676; 3,600 points: 6,353,832; 21,600 points: 232,161,432). The unchanged-checkpoint workload serializes 77,507,520 → 645,896 bytes. These are serialization measurements, not flash-write timings.

Acceptance: the allocation reduction is repeatable and well above noise; long-recording and idle elapsed/CPU gains repeat across JVM runs. Short-recording timing ranges overlap, so no short-recording speedup is claimed. Individual paired timing reversals occur within the observed spread; no scenario has a repeated median regression. The strongest objection is that desktop serialization does not establish device storage latency or battery savings.

## Tests and reproduction

Debug APK build passed. Recorder/repository selection: 28 tests passed, no failures/errors/skips. The opt-in performance test passed in both runs.

- `RecordingPointsTest`: original-list differential comparison, bounds, resumed-input isolation, retained snapshots across chunk boundaries, exact protobuf bytes including GAP points.
- `RecordingCheckpointTest`: unchanged/older revisions, metadata changes, overlapping persistence, failures, cancellation, deletion invalidation.
- `TrackRecorderCheckpointTest`: actual recorder revision integration, unchanged periodic ticks, metadata retries, resume snapshots and GAP preservation.
- Existing lifecycle tests: overlapping stop/discard, failed finalization and retry, stale checkpoint replacement, monotonic clocks; extended to pin appending after failure without mutating retained snapshots.
- Existing arrival-prompt, repository durability, route-index and safe-ID tests passed.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest \
  --tests '*RecordingPointsTest' --tests '*RecordingCheckpointTest' \
  --tests '*TrackRecorder*Test' --tests '*TrackRepository*Test' --no-daemon
MARO_RECORDING_PERF=1 ./gradlew :app:testDebugUnitTest \
  --tests '*RecordingPerformanceTest' -Pmaro.testStdout=true --no-daemon
```

The first test compilation exposed Android's missing compile-time JDK management classes; the benchmark accesses those JVM-only counters reflectively. Subsequent builds passed. No device was accessed or deployed. Device CPU/battery, flash latency, full application frame timing, peak retained heap, power-loss fault injection and bake-dependent evidence remain unmeasured. No whole-app performance claim follows from this stage.

## Warm paired elapsed readings

| Run | Points / idle attempts | Round | Reference ms | Candidate ms |
|---|---|---|---|---|
| 1 | 120 / 0 | 3 | 1.635 | 1.861 |
| 1 | 120 / 0 | 4 | 1.925 | 1.658 |
| 1 | 120 / 0 | 5 | 1.311 | 1.570 |
| 1 | 120 / 0 | 6 | 1.161 | 1.082 |
| 1 | 120 / 0 | 7 | 0.999 | 1.114 |
| 1 | 120 / 0 | 8 | 1.012 | 0.980 |
| 1 | 120 / 0 | 9 | 0.892 | 0.927 |
| 1 | 3600 / 0 | 3 | 105.496 | 99.580 |
| 1 | 3600 / 0 | 4 | 104.860 | 100.897 |
| 1 | 3600 / 0 | 5 | 190.757 | 131.401 |
| 1 | 3600 / 0 | 6 | 135.450 | 132.656 |
| 1 | 3600 / 0 | 7 | 106.951 | 106.184 |
| 1 | 3600 / 0 | 8 | 109.507 | 99.175 |
| 1 | 3600 / 0 | 9 | 103.832 | 95.956 |
| 1 | 21600 / 0 | 3 | 2837.409 | 2685.914 |
| 1 | 21600 / 0 | 4 | 2738.488 | 2599.869 |
| 1 | 21600 / 0 | 5 | 2828.636 | 2612.179 |
| 1 | 21600 / 0 | 6 | 2756.236 | 2609.952 |
| 1 | 21600 / 0 | 7 | 2767.307 | 2531.701 |
| 1 | 21600 / 0 | 8 | 2795.680 | 2814.642 |
| 1 | 21600 / 0 | 9 | 2853.926 | 2677.279 |
| 1 | 21600 / 120 | 3 | 1140.871 | 10.166 |
| 1 | 21600 / 120 | 4 | 1044.419 | 8.854 |
| 1 | 21600 / 120 | 5 | 1046.234 | 11.335 |
| 1 | 21600 / 120 | 6 | 1062.189 | 9.869 |
| 1 | 21600 / 120 | 7 | 1092.983 | 9.573 |
| 1 | 21600 / 120 | 8 | 1067.965 | 8.975 |
| 1 | 21600 / 120 | 9 | 1044.987 | 9.543 |
| 2 | 120 / 0 | 3 | 1.072 | 1.102 |
| 2 | 120 / 0 | 4 | 0.959 | 0.950 |
| 2 | 120 / 0 | 5 | 0.991 | 0.936 |
| 2 | 120 / 0 | 6 | 0.947 | 1.096 |
| 2 | 120 / 0 | 7 | 0.762 | 0.829 |
| 2 | 120 / 0 | 8 | 0.594 | 0.550 |
| 2 | 120 / 0 | 9 | 0.499 | 0.686 |
| 2 | 3600 / 0 | 3 | 72.860 | 70.941 |
| 2 | 3600 / 0 | 4 | 75.279 | 73.475 |
| 2 | 3600 / 0 | 5 | 76.602 | 71.770 |
| 2 | 3600 / 0 | 6 | 79.587 | 80.931 |
| 2 | 3600 / 0 | 7 | 79.477 | 78.945 |
| 2 | 3600 / 0 | 8 | 75.184 | 70.214 |
| 2 | 3600 / 0 | 9 | 73.238 | 69.843 |
| 2 | 21600 / 0 | 3 | 2854.551 | 2614.509 |
| 2 | 21600 / 0 | 4 | 2783.710 | 2623.359 |
| 2 | 21600 / 0 | 5 | 2741.836 | 2536.651 |
| 2 | 21600 / 0 | 6 | 2753.466 | 2793.180 |
| 2 | 21600 / 0 | 7 | 2895.645 | 2747.459 |
| 2 | 21600 / 0 | 8 | 2802.216 | 2539.306 |
| 2 | 21600 / 0 | 9 | 2769.362 | 2602.060 |
| 2 | 21600 / 120 | 3 | 1086.875 | 9.672 |
| 2 | 21600 / 120 | 4 | 1052.130 | 9.033 |
| 2 | 21600 / 120 | 5 | 1093.096 | 9.439 |
| 2 | 21600 / 120 | 6 | 1054.772 | 10.789 |
| 2 | 21600 / 120 | 7 | 1101.770 | 9.245 |
| 2 | 21600 / 120 | 8 | 1068.057 | 9.751 |
| 2 | 21600 / 120 | 9 | 1061.287 | 8.870 |
