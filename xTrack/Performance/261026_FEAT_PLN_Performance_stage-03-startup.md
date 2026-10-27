<!-- scope: feature -->
# Stage 3 — startup and refresh ownership

## Disposition
Retained on `codex/performance-03-startup`, independently from upstream develop `b1f40aa211c02c2882bb676514bee753cecacebe` (PR #382). No routing algorithms changed. Device startup/frame performance is **unverified**.

## Change
Coastline, speed-zone and non-speed-zone indexes are constructed on Dispatchers.Default and published after construction. Initializers join active work and skip successfully loaded data; failure/cancellation still permits retry. Cache-directory setup is inside the guarded initialization. Navigation owns the one regulated-zone repository used by navigation and drawing; speed indexes no longer rebuild on an unused coastline emission.

Marker writes publish a successful-write revision. Local writers and the observer join one refresh per revision, including initial loading. Reconciliation stays in the original writer, after its existing drawer transition. Repeated settings observation reuses active subscriptions. The repository's existing global change flow remains for other consumers.

## Proportionate evidence
Linux x86_64 i5-1245U, temporary JDK 17 and existing Gradle toolchain. `MARO_STARTUP_PERF=1`, `StartupPerformanceTest`: 12 alternating reference/candidate rounds in one JVM, first four discarded. Synthetic data only: ten requests for an index over 4,000 vertices, ten loads of 200 zones, and 25 updates of a 20-marker file followed by local and observer refresh. Marker updates include the unchanged persistence work. Index queries and decoded zones are compared with the reference. Index reference constructs each time; zone reference retains the old decode/apply pipeline; marker reference reads/sorts twice.

| Workload | Candidate | Median ms | Range ms |
|---|---|---:|---:|
| index | false | 34.424 | 16.084–82.527 |
| index | true | 6.291 | 2.815–33.692 |
| zones | false | 30.442 | 26.295–58.616 |
| zones | true | 3.814 | 3.154–9.990 |
| markers | false | 52.932 | 24.004–105.706 |
| markers | true | 38.049 | 18.594–67.659 |

Counters: index builds 10→1, zone reads 10→1, marker refresh reads 50→25 (excludes each update's own read). All eight retained paired rounds improved in the final run. An earlier paired marker run had seven wins and one small loss; median 12.581→8.863 ms. The repeat measured 52.932→38.049 ms with all pairs improving. Absolute host times vary substantially, so separate-process timings are not acceptance evidence; counters and paired differences are used. No repeatable regression was observed in these workloads. A single cold index build is unchanged construction plus a dispatcher hop: no cold wall-time speedup is claimed.

## Tests and limits
Debug APK and selected coordination, zone repository, marker repository, coastline spatial-index and speed-index tests passed: {'tests': 37, 'failures': 0, 'errors': 0, 'skipped': 0}. Tests pin active-load joining, successful idempotence, missing/cancelled load retry, shared marker publication, revision changes during a read, read failure retry, and revision advancement only after successful persistence. Existing spatial query tests remain unchanged.

No real-region assets were opened. Android recreation, main-thread frame tails, cold startup latency, device storage, peak memory, baked-data and combined-stage behavior remain unverified. These desktop results do not approve the full app performance requirement.

## Retained paired samples
Milliseconds, warm rounds in execution order; raw opt-in output below permits independent recomputation.

```text
STARTUP-PERF workload=index candidate=false round=0 ns=70499411 builds=10
STARTUP-PERF workload=index candidate=true round=0 ns=8334673 builds=1
STARTUP-PERF workload=index candidate=true round=1 ns=2046994 builds=1
STARTUP-PERF workload=index candidate=false round=1 ns=20090133 builds=10
STARTUP-PERF workload=index candidate=false round=2 ns=19041320 builds=10
STARTUP-PERF workload=index candidate=true round=2 ns=2889335 builds=1
STARTUP-PERF workload=index candidate=true round=3 ns=2766448 builds=1
STARTUP-PERF workload=index candidate=false round=3 ns=20812696 builds=10
STARTUP-PERF workload=index candidate=false round=4 ns=23933120 builds=10
STARTUP-PERF workload=index candidate=true round=4 ns=5912630 builds=1
STARTUP-PERF workload=index candidate=true round=5 ns=6669244 builds=1
STARTUP-PERF workload=index candidate=false round=5 ns=24898592 builds=10
STARTUP-PERF workload=index candidate=false round=6 ns=25344905 builds=10
STARTUP-PERF workload=index candidate=true round=6 ns=5473817 builds=1
STARTUP-PERF workload=index candidate=true round=7 ns=33692170 builds=1
STARTUP-PERF workload=index candidate=false round=7 ns=82527380 builds=10
STARTUP-PERF workload=index candidate=false round=8 ns=47081997 builds=10
STARTUP-PERF workload=index candidate=true round=8 ns=4106301 builds=1
STARTUP-PERF workload=index candidate=true round=9 ns=8927605 builds=1
STARTUP-PERF workload=index candidate=false round=9 ns=48508007 builds=10
STARTUP-PERF workload=index candidate=false round=10 ns=43502665 builds=10
STARTUP-PERF workload=index candidate=true round=10 ns=8762553 builds=1
STARTUP-PERF workload=index candidate=true round=11 ns=2815125 builds=1
STARTUP-PERF workload=index candidate=false round=11 ns=16083612 builds=10
STARTUP-PERF workload=zones candidate=false round=0 ns=137550699 reads=10
STARTUP-PERF workload=zones candidate=true round=0 ns=18083757 reads=1
STARTUP-PERF workload=zones candidate=true round=1 ns=8357926 reads=1
STARTUP-PERF workload=zones candidate=false round=1 ns=58338471 reads=10
STARTUP-PERF workload=zones candidate=false round=2 ns=50871436 reads=10
STARTUP-PERF workload=zones candidate=true round=2 ns=7209505 reads=1
STARTUP-PERF workload=zones candidate=true round=3 ns=7156786 reads=1
STARTUP-PERF workload=zones candidate=false round=3 ns=42995925 reads=10
STARTUP-PERF workload=zones candidate=false round=4 ns=58615761 reads=10
STARTUP-PERF workload=zones candidate=true round=4 ns=9989725 reads=1
STARTUP-PERF workload=zones candidate=true round=5 ns=8615916 reads=1
STARTUP-PERF workload=zones candidate=false round=5 ns=55460792 reads=10
STARTUP-PERF workload=zones candidate=false round=6 ns=50655949 reads=10
STARTUP-PERF workload=zones candidate=true round=6 ns=3408815 reads=1
STARTUP-PERF workload=zones candidate=true round=7 ns=3182234 reads=1
STARTUP-PERF workload=zones candidate=false round=7 ns=28659123 reads=10
STARTUP-PERF workload=zones candidate=false round=8 ns=27233698 reads=10
STARTUP-PERF workload=zones candidate=true round=8 ns=3305260 reads=1
STARTUP-PERF workload=zones candidate=true round=9 ns=3153802 reads=1
STARTUP-PERF workload=zones candidate=false round=9 ns=26294685 reads=10
STARTUP-PERF workload=zones candidate=false round=10 ns=28152374 reads=10
STARTUP-PERF workload=zones candidate=true round=10 ns=6653875 reads=1
STARTUP-PERF workload=zones candidate=true round=11 ns=4219452 reads=1
STARTUP-PERF workload=zones candidate=false round=11 ns=32224076 reads=10
STARTUP-PERF workload=markers candidate=false round=0 ns=239569416 reads=50
STARTUP-PERF workload=markers candidate=true round=0 ns=78447239 reads=25
STARTUP-PERF workload=markers candidate=true round=1 ns=74530445 reads=25
STARTUP-PERF workload=markers candidate=false round=1 ns=87278603 reads=50
STARTUP-PERF workload=markers candidate=false round=2 ns=74983769 reads=50
STARTUP-PERF workload=markers candidate=true round=2 ns=71027988 reads=25
STARTUP-PERF workload=markers candidate=true round=3 ns=67822092 reads=25
STARTUP-PERF workload=markers candidate=false round=3 ns=71308974 reads=50
STARTUP-PERF workload=markers candidate=false round=4 ns=105705940 reads=50
STARTUP-PERF workload=markers candidate=true round=4 ns=65807002 reads=25
STARTUP-PERF workload=markers candidate=true round=5 ns=67659081 reads=25
STARTUP-PERF workload=markers candidate=false round=5 ns=79940815 reads=50
STARTUP-PERF workload=markers candidate=false round=6 ns=63670522 reads=50
STARTUP-PERF workload=markers candidate=true round=6 ns=40278150 reads=25
STARTUP-PERF workload=markers candidate=true round=7 ns=44084374 reads=25
STARTUP-PERF workload=markers candidate=false round=7 ns=80841617 reads=50
STARTUP-PERF workload=markers candidate=false round=8 ns=42193442 reads=50
STARTUP-PERF workload=markers candidate=true round=8 ns=35819691 reads=25
STARTUP-PERF workload=markers candidate=true round=9 ns=18594044 reads=25
STARTUP-PERF workload=markers candidate=false round=9 ns=33141624 reads=50
STARTUP-PERF workload=markers candidate=false round=10 ns=33496741 reads=50
STARTUP-PERF workload=markers candidate=true round=10 ns=18938287 reads=25
STARTUP-PERF workload=markers candidate=true round=11 ns=20522645 reads=25
STARTUP-PERF workload=markers candidate=false round=11 ns=24003563 reads=50
```
