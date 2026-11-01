# Route — exact speed-ups: the named levers, by benefit over cost

**Date:** 2026-10-30 · **Status:** P0 read, S0 and S1 built (uncommitted) · **Branch:** `feature/rooouting`, on `develop` at PR #398 (Evolutive + included)
**Order:** the user's word of 2026-10-30: *"improve the route search algo without changing the routes … go thru
them in order of benefit/cost ratio, let's plan it"*.

**Sources:** every lever here is already designed in an earlier plan and is answer-preserving by its own
design — this file orders them and states the bar, it does not redesign them.
[`261027_FEAT_PLN_Route_search-perf-remediation.md`](261027_FEAT_PLN_Route_search-perf-remediation.md) (wave E §6,
wave F §11), [`261021_FEAT_PLN_Route_pull-cost.md`](261021_FEAT_PLN_Route_pull-cost.md) (F1, F4).

## 1. The bar — "routes unchanged"

- **Exact, not close:** on every step, the harness's `LINE`, `APPROACH move` and `SWEEP` lines are identical
  before and after, the suites are green, and the step's own exactness test (named per step) passes.
- **A gain is kept only above noise:** harness best of five per rung, two baseline runs first; a step inside
  ±10 % of the baseline is reverted, not shipped on faith.
- **Device readings are the user's:** each step names what a phone run would read; the plan never waits on it.

## 2. P0 — profile the code as it stands (cheapest, decides the rest)

- `RouteBendHarnessTest` with `-Dmaro.jfr=…` on Lérins → La Salis, 30 kn, `evolutive` and `evolutive_plus`, read with `jfr print --events jdk.ExecutionSample`, samples bucketed by stage:
  coarse search (`MultipassSearch`), pull (`MultipassPull`), approach pass, fine pass, grid build.
- The last profile (wave G, 2026-10-28) put a third of a rung on `CoastlineSpatialIndex.nearestRef`, since cut;
  nothing has been profiled after that fix, so every share below is a prior, not a reading.
- The harness pays a grid build per rung that the device loads from its tile store — that stage is reported
  apart and not counted towards a device gain.
- Output: a share per stage, written into §5, and the order of §3 re-ranked on it.

## 3. The levers, prior order (benefit ÷ cost)

| # | Lever | Prior benefit | Cost | Exactness rests on | Condition to build |
|---|---|---|---|---|---|
| S1 | Pull's forward and backward walks in parallel (pull-cost F1) | 0.83–0.87 s an attempt, 9–13 % of a search — measured 2026-10-21, before the remediation | Small: `pullBothWays` → `suspend` + `async`, one memo / timing / refusals per walk merged after; two prod callers already `suspend`, two test callers → `runBlocking` | The choice after both walks is untouched; the memo is a pure cache | The pull ≥ ~8 % of a rung in P0 |
| S2 | Primitive-keyed mark memo (pull-cost F4) | Allocation in `priceMs`, unmeasured | Small: a table keyed on the doubles' bits in place of `HashMap<LatLng, Double>` | Same value per key; `LatLng` equality is already bit-exact | P0 shows boxing / hashing in the memo |
| S3 | The walk's values read once per walk (wave E1) | Seven decodes per relaxation in `relax` | Medium: per-walk primitive arrays in `WalkWindows`, `relax` and the step guard read them | Same values, same enumeration order, same heap tie-break | `MultipassSearch` ≥ ~10 % of a rung in P0 |
| S4 | The shallow-edge read nearest-first (wave F4) | ≤ ~2 % of a rung: gate-edge reads were 5–7 % of the approach pass's pricing on the device | Small–medium: ring-ordered quad visit with a latitude-correct bound and margin (review W9) | Same double answered; today's `distanceM` kept in the test as the oracle | P0 shows `ShallowEdgeIndex.distanceM` above ~3 % |
| S5 | One coast sweep with three visit sets, bucketed fills (wave E2) | Cold grid builds only — the phone loads its tiles from the store (63 of 63) | High: the sliver case, seven arrays byte-compared, the old frame kept in the test | Same distance call over the same visit sets | A cold build is part of what the user waits on |

**Excluded — not exact:** B1c (the proof step; never shown line-preserving), wave F3 (a Lipschitz bound the
review found unsound as drawn), pull-cost F3 (moves the marks), galloping the pull's probe.

## 4. Steps

- One lever a step, in §3's order as re-ranked by P0; a lever whose condition P0 fails is skipped and named.
- Each step: baseline harness (twice) → change → exactness test → harness diff of the lines → timing →
  suites → keep or revert. Two consecutive build failures halt the step (§4 of `AGENTS.md`).
- The next step starts on the user's word; a step closes with its harness figures written into §5. **Changed
  2026-10-30:** the user's *"go and do as much as you can unless you need some functional or perf cost decision"*
  runs the steps on, stopping only at such a decision.

## 5. Readings

**P0 — the profile (2026-10-30, harness, `evolutive`, 30 kn).** The default pair alone gave too few samples, so
the 24-pair survey (`-Dmaro.harness.survey=true`, its report in the test XML's `system-out`, not the report
file) was read with Flight Recorder: **73 % of all samples sat in `CoastlineSpatialIndex.nearestRef`**, reached
through `distanceToCoastM` from the pull's hard reads and the band's price and clearance. The open-water pairs
showed it plainest — a 1–9 ms search followed by a 0.3–1.8 s pull — because a point kilometres off the coast
walks rings until the ring passes its own distance, crossing every segment in a square that wide. No lever of
§3 touches that read, so a new one went first.

**S0 — the coast distance read off the point's own cell (built, exact).** `CoastlineSpatialIndex.distanceM` keeps,
per 500 m grid cell and built on first read, the segments that can be the nearest for some point of the cell (a
ceiling from the centre's nearest segment at the longest degree of longitude, a floor from the box gap at the
shortest), and measures those alone; `CoastlineRepository.distanceToCoastMeters` — the live world's read — and
the harness world call it. Exact because the value is the minimum of the same `pointToSegmentDistance` over a
set holding the exhaustive nearest segment.
- **Identity:** `distanceM` equals `query`'s distance bit for bit on a synthetic coast with an island at 100 m and
  500 m cells (near, far, outside the grid, a built cell read twice) and on 5 000+ points of the baked coast
  (two new tests). The harness's every printed line, clocks and clock-derived ratios removed, is identical
  before and after on the survey (72 lines) and the pair (3 lines), on `evolutive` and `evolutive_plus`.
- **Timing (best of five on the pair, one run on the survey; ms):**

  | Run | Before | After | Change |
  |---|---|---|---|
  | Pair, `evolutive`, rung Around / Balanced / Closest | 480 / 377 / 388 | 369 / 319 / 332 | −14 to −23 % |
  | Pair, `evolutive_plus`, rung | 472 / 391 / 403 | 403 / 325 / 349 | −13 to −17 % |
  | Survey, `evolutive`, rungs summed | 14 106 / 13 663 / 13 467 | 4 467 / 3 720 / 3 633 | −68 to −73 % |
  | Survey, `evolutive_plus`, rungs summed | 14 256 / 13 466 / 13 279 | 4 676 / 3 722 / 3 803 | −67 to −72 % |
  | Survey, pull summed | 11 263 / 11 310 / 11 227 | 1 939 / 1 832 / 1 843 | −83 % |

- **Memory:** the lists fill only the cells a read reaches; every cell of the region filled (13 662 of 13 900,
  mean 110 segments, max 543) is ~6 MB, built in 0.85 s, kept for the index's life.
- **Tried and dropped:** the segments' boxes in one flat array for the skip — 0–2 % against a same-session
  control, inside noise, reverted.
- 1 690 tests green (1 688 + the two identity tests) on the second full run — the first lost
  `FineTileWarmTest`'s grace-window case, which reads no coast and passed three of three alone; `apk-build.bat`
  green. The device reading is the user's.

- **Review hop (`ask`, 2026-10-30):** it could not break the exactness claim or the thread safety. Folded: the
  KDoc names the one precondition the identity with `query` rests on (`query` is exhaustive only while the grid's
  extreme degree of longitude stays within `SAFE_STOP_FACTOR` of its middle one — about ±3° of latitude at 44° N,
  every shipped region inside it; past it `distanceM` is the exhaustive one); a segment a concurrent build met
  twice is dropped from the stored list; the class table and the `seen` KDoc name the cell lists; the stamp no
  longer shadows `query`; a test runs three readers on one fresh index. **Left, named:** the box-to-box gap
  repeats `boxCannotBeat`'s point-to-box formula; no probe sits exactly on a cell edge; the repository's
  "80–150× faster" KDoc is stale (pre-existing). Re-run after the fold: survey and Evolutive + pair identical,
  1 691 tests green.

**P0 again, after S0 (survey):** the grid build and the pull share the samples (273 each of 822), the search 50.
The phone loads its grids from its tile store, so on the phone the pull is the larger share. Inside the pull the
new read is 101 of 273, the band's and zones' price reads ~100, the gate edges ~35. Each §3 lever measured
against its condition:
- **S1 (parallel pull walks):** its condition holds (the pull ≈ 45 % of the survey's rungs, ≈ 8 % of the
  pair's). Built on the user's *"whatever is next that would improve the perf"* — see S1 below.
- **S2 (primitive memo):** already shipped as `PointDoubleMap`; struck.
- **S3 (walk values once):** the search is 6 % of the survey and ~10 % of the pair; not built.
- **S4 (shallow edge, nearest first):** the gate edges ~5 % of the pull; not built.
- **S5 (one coast sweep):** grid builds only, which the phone serves from its tile store; not built.
- **Seen, not in §3:** the marker circles' `haversine` (`RouteMarkerGeometry.Circle`) is 3–4 % of samples; a
  latitude-only bound could skip `contains` and `wellFactor` exactly, under the noise bar, not built.


**S1 — the pull's two walks side by side (built, exact).** `pullBothWays` is `suspend`; the backward walk runs on
`Dispatchers.Default` beside the forward one, each on a `MarkMemo.fork` of the pass's memo and its own refusals and
timing, joined back forward first (`MarkMemo.join`, `PullRefusals.add`, `PullTiming.add`, `recordWalk`). The far-edge
table's join keeps a found distance over a "none", and of two "none"s the longer reach — each an answer
`farEdgeDistance` already gives.
- **Identity:** the harness's every printed line identical to the pre-S0 baseline on the survey and the pair, on
  `evolutive` and `evolutive_plus`; the suite (1 691) and the real-coast `RouteDirectionSymmetryTest`
  (`-Dmaro.prebake=true`, both cases) green; `apk-build.bat` green.
- **Timing (ms, against S0 alone):** survey pull 1 768–1 793 / 1 607–1 654 / 1 622–1 658 → 1 258–1 278 / 1 079–1 093 /
  1 068–1 136 (−29 to −35 %), rungs 4 300–4 327 / 3 497 / 3 389–3 442 → 3 794–3 825 / 2 975–3 049 / 2 862–3 032
  (−11 to −16 %); `evolutive_plus` survey rungs −15 to −23 %. The pair's pull 27 → 18–23 ms, its rungs inside noise.
- **The cost:** the backward walk no longer hits the forward walk's fresh entries (the pull-cost plan read 2–7 % of
  reads), and each call copies the pass memo twice. The harness routes one rung at a time, so a core is always free
  there; the phone routes three at once on eight cores, four of them small, so its gain is the user's reading.
- **Review hop (`ask`, 2026-10-30):** it could not break the identity or the race-freedom. Folded: the fork reads
  through to its frozen parent and joins back only its own reads (no table copied, no point allocated per entry);
  the backward walk also stops on its own scope's cancellation; the `pullBothWays`, `PullSplit` and `read` KDoc
  corrected (what the walks share is built for concurrent readers, not stateless; a walk's hits never come from the
  other walk; `clearMs`/`priceMs` sum the walks); `PullBothWaysForkTest` pins the forked line to the memo-less one,
  to one of the two walks, and a later pull on the joined memo to one on a fresh memo, under four berth laws.
  Re-run: harness identical on all four runs, timings as above within noise, 1 692 tests, the real-coast symmetry
  test and `apk-build.bat` green. **Left, named:** the dispatcher is fixed at `Dispatchers.Default` rather than
  injected; `PullTiming.add` lists its 14 fields by hand; the trace's `memo*Hits`, `hardReads` and `priceReads`
  read differently from earlier device logs.

## 6. Open

- **S1's real gain** needs an idle core beside the three rung threads; the Pixel 7 has eight cores, four of them
  small, and that was never read on the phone.
- The harness's marker copy predates the device runs; its lines serve as a before/after baseline only, which
  is all this plan needs.
