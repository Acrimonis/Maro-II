<!-- scope: feature -->
# Route — the two-sided pull's cost on the device

## Outcome

The symmetry (`261020_FEAT_PLN_Route_direction-symmetry.md`) made the coarse pull run twice per pass —
`MultipassPull.pullBothWays` walks the cell chain forward and the reversed chain backward, then keeps the
cheaper line — and the desktop probe put the rise at ×2.05 of the pull. **Measured on the phone on
2026-10-21 with an instrumented build**, the pull is the smaller cost: overlapping its two walks (F1) saves
**0.83–0.87 s per attempt, 9–13 % of a plain search**, while a corridor retry that cannot change its answer
(F2) spends **~10 s, 48–56 % of every search whose end stands inside a speed zone**. F2 is the larger fix
and a behaviour change, so it is the user's call; F1 changes no answer and gains little.

**Date:** 2026-10-21 · **Status:** in design, device-measured 2026-10-21 14:15–14:17, reviewed twice ·
**Order:** the user's word — "improve the perf/speed of the pull", "plan fixes", "review plan update
report", "instrumentize for device test", "review results update plan".

## The pairs

| | Ends | Length | Zones | Runs |
|---|---|---|---|---|
| **A** shore ↔ open sea | 43.5705, 7.1311 (32 m off the coast, 3.2 m deep) ↔ 43.5413, 7.1016 (1.4 km off, 11 m) | ~10.4 km | none forced | 09:20 · 13:13 · 14:15–14:16 |
| **B** zone ↔ shore | 43.5454, 7.1285 (inside the 5 kn zone, 58 m off, 7 m) ↔ the A shore end | 8.1–9.2 km | `[Zone 10 kn, Zone 5 kn]` forced | 09:28 · 14:16–14:17 |
| **C** open sea ↔ zone | the A sea end ↔ the B zone end | 3.9–4.0 km | same, forced | 13:12, uninstrumented build only |

The morning's A sea end was 43.5415, 7.1020, about 40 m from the afternoon's.

## Device results (instrumented build, installed 14:11)

### The answers did not move

The instrumented build lands the same lines as the uninstrumented one: A at 10 362.3 / 10 370.5 /
10 433.1 m shore→sea and 10 383.2 / 10 383.2 / 10 421.2 m sea→shore (13:13 and 14:15–14:16 alike), and B at
8052.9 / 8210.4 / 9244.4 m and 8097.6 / 8210.5 / 9243.0 m (09:28 and 14:16–14:17 alike).

### Wall time — the phone moved, not the build

| Search | 09:18 build, morning | 09:18 build, 13:13 | Instrumented, 14:15+ |
|---|---|---|---|
| A shore→sea | 9.2 s (cold tiles) | 6.9 s | 6.4 s |
| A sea→shore | 5.4 s | 7.7 s | 9.5 s (fine store emptied by the install) |
| B zone→shore | 14.5 s | — | 21.5 s |
| B shore→zone | 14.0 s | — | 19.0 s |

On the same pair the same afternoon the two builds match (6.9 against 6.4 s), so the instrument costs
nothing visible. The phone itself ran slower in the afternoon than in the morning, including in stages the
instrument does not touch: B's fine pass took 1.6–2.1 s in the morning and 2.6–3.7 s in the afternoon. Why
(thermal state, load) is not read. Compare a fix's gains within one session, never across the day.

### F1 — the two walks, from `PULL2` / `FINAL2`

| Search | `PULL` gain (mean) | `FINAL` gain (mean) | Per attempt | Share of wall |
|---|---|---|---|---|
| A sea→shore (cold) | 0.59 s | 0.24 s | 0.83 s | 9 % |
| A shore→sea | 0.56 s | 0.26 s | 0.83 s | 13 % |
| B zone→shore | 0.60 s | 0.27 s | 0.87 s | ×2 attempts while F2 stands |
| B shore→zone | 0.59 s | 0.26 s | 0.85 s | ×2 attempts while F2 stands |

- **The gain is the shorter walk** (`min(fwdMs, bwdMs)`), about 37 % of the pair's sum rather than half,
  because the walks are unequal. The rungs run concurrently, so a search saves roughly its slowest rung's gain.
- **In `PULL` the slower walk is the one ending at the shore end in 12 of 12 calls**, whichever end was armed
  and whichever ran first: ~1.0–1.1 s against 0.4–0.6 s on A, ~0.7–1.0 s against 0.5–0.65 s on B. `FINAL`
  shows no such pattern. The cause is not read.
- **The shared memo is worth nothing measurable, so F1's measuring step is answered.** The `PULL` chain is
  the same chain whichever end is armed (each walk's `Marks` match across the two directions, 12 of 12), so
  every walk ran once first, on an empty memo, and once second, on the other walk's. Running second saves
  **2.0–6.6 % of its price reads** and 24–30 hard reads, and its clock moves by −170 to +170 ms with no sign:
  noise. One memo per walk, which F1 needs, gives that up.
- **The choice costs 40–105 ms per call**, run after both walks, so F1 cannot hide it: about 0.1 s per rung.
- **`FINAL`'s input differs with the arming direction:** the snapped lines' `Marks` match in only 2 of 12
  walks. The two directions' landed lines differ by 12–21 m and up to 1.4 s, inside the survey's bar (same
  crown, 100 m, 30 s). Recorded here, not worked on.

### F2 — the retry, from `RUNG`

- **Six of six zone-pair rungs** logged `reason=FORCED grew=true kept=FIRST`, the grown answer equal to the
  first in seconds, metres and forced zones, with the zone end read as `startZoneKn=5.0` forward and
  `aimZoneKn=5.0` reversed. **Six of six A rungs** logged `reason=NONE grew=false`.
- **Over the day, 18 grown rungs** (B in the morning, C at 13:12, B in the afternoon) gave the same line and
  none was kept. **All 18 share one end**, the B zone end in the 5 kn zone, so this is one case seen three
  times, not 18 cases.
- **The grown attempt repeats the same cell chain:** its `PULL2` counts equal the first attempt's to the read.
- **Cost on the slowest rung:** `growBuildMs` 0.6–0.75 s plus `grownMs` 7.8–9.8 s, so ~10.4 s, 48 % of B's
  wall forward and 55 % reversed; on C (13:12) it is ~6.5 s of 11.5–11.7 s, ~55 %. The grow-build is 0.6–0.75 s,
  not the 29–42 ms the `GRID layer=fine` line reads: that line times only the fine windows.
- **Why it can never help here:** `forcedCrossingNames` blocks every restrictive zone's interior and frees
  only the two ends' own cells (`MultipassGrid.forceFree`). An end standing more than one cell inside a
  restrictive zone is therefore walled in by its own zone, the probe fails at any corridor width, every rung
  reports a forced crossing, and every rung grows. Forward the probe expands **1** cell; reversed it walks
  the whole corridor without reaching that end.

## Instrument (in the working tree, uncommitted)

Reading only; no answer moves (shown above). Beside the unchanged `PULL` / `FINAL` / `DEVICE PASS`:

- **`PULL2` / `FINAL2`** (`RoutePassRunner.splitText`, filled by `MultipassPull.pullBothWays`' `split`
  parameter, a `PullSplit`): each walk's wall `fwdMs` / `bwdMs`, `ClearMs` / `PriceMs`, paid `PriceReads`,
  `Marks`, and the memo's `PriceHits` / `HardHits` / `EdgeHits` over that walk alone, then `choiceMs`,
  `kept=SAME|FORWARD|BACKWARD` and both lines' canonical seconds. **Read:** F1's gain per call is
  `min(fwdMs, bwdMs)`. Memo sharing is **not** `bwd` hits minus `fwd` hits, because each walk also hits its
  own retraced points. Read it from one walk run first and run second across the two arming directions,
  matched by `Marks`.
- **`RUNG`** (`RouteAvoidEngine.searchRung`, one per rung and exit): `reason=NO_PATH|FORCED|NONE`, `grew`,
  `kept=FIRST|GROWN|NONE`, `firstMs` / `growBuildMs` / `grownMs`, the ends' `startZoneKn` / `aimZoneKn`
  (`-` outside a zone), and each attempt's forced zones, seconds and metres. **Read:** a kept grown answer is
  `grew=true kept=GROWN`; the waste is `growBuildMs + grownMs` where `kept=FIRST`.

## Fixes, by measured gain

### F2 — The retry that cannot change its answer (a behaviour change, the user's call)

**Gain:** ~10 s, about half of every search with an end inside a restrictive zone; nothing on the others.

- **(a) Skip the growth when an end is boxed** — when an end stands inside a restrictive zone, which is
  what walls the probe in. It keeps today's answer every time the grown attempt names as many zones. It loses
  the case where a wider route names **fewer**, which the `<` test allows today; that case was never seen,
  because no forced crossing away from an end occurred in any run.
- **(b) Probe with the zones that hold an end left open,** so "forced" means a crossing forced
  **elsewhere**. The growth then fires only on a real forced crossing, and the report stops naming a zone the
  boat merely starts or ends in. It changes `forcedCrossingZoneNames`, a record no composable prints yet, and
  which rungs grow. Of the two cuts, it is the one that fixes the cause.
- **Not proposed:** growing once for the three rungs, which saves ~1.4 s of CPU but little wall time (the
  builds already run in parallel), and growing only the Detour rung, which changes which routes can be found.

### F1 — Run the forward and backward walks in parallel (answer-preserving)

**Gain:** 0.83–0.87 s per attempt, 9–13 % of a plain search, paid for with CPU; less if the phone has no
idle core beside its three rung threads (not read).

- `pullBothWays` becomes `suspend` and walks the two directions in a `coroutineScope` with `async`, on the
  engine's own `Dispatchers.Default` scope; the choice between the two lines (`canonicalTotalSec`,
  `compareLines`) is untouched. The two production calls sit inside the already-`suspend`
  `RoutePassRunner.runPass`; the two test callers (`DirectionSymmetryRepairsTest`,
  `RouteDirectionSymmetryTest`) move to `runBlocking`.
- Each walk gets its own `MarkMemo`, `PullTiming` and `PullRefusals` (none is thread-safe), folded into the
  caller's afterwards through an `internal` merge on each, so the `PULL` / `FINAL` lines keep their fields.
  `ms=` becomes the slower walk; `clearMs` / `priceMs` stay the summed work and may read larger than `ms=`.
  `PullSplit` keeps working, each walk filling its own side.
- **What the walks share is already shared safely:** `RouteCostField` is immutable, its sources close over
  the `MultipassWorld`, `GateRule` and `BerthLaw` hold no mutable state, and `ShallowEdgeIndex` is built for
  concurrent readers (`ConcurrentHashMap` chunks). The three rungs already read that world concurrently.
- **Verify:** `pullBothWays` returns the same list as the sequential version on the gated 24-pair survey
  (`RouteDirectionSymmetryTest`, `-Dmaro.prebake=true`) and the existing pull fixtures. **Device:** pairs A
  and B in one session, `PULL ms` / `FINAL ms` against `PULL2` / `FINAL2` in the same session.

### F3 — Chord-verdict cache between the two walks (dropped unless F1 is refused)

Anchoring the mark lattice at the canonical end and caching verdicts on the unordered chord would let a chord
both walks evaluate be judged once. The device shows the two walks share only 2–7 % of their points today,
the shared-chord count is unmeasured, F3 conflicts with F1 (one shares the walks, the other separates them),
and it moves the marks, so lines may move.

### F4 — Primitive-keyed memo (only on a profile)

`HashMap<LatLng, Double>` boxes a key and a value per mark; a table keyed on the doubles' bits would cut
allocation without changing a value. Taken only on a profile that shows allocation in `priceMs`.

## Not proposed

- **Pulling the second walk over the first walk's output:** far fewer chords, but the two answers would no
  longer come from one input, which is what the symmetry rests on.
- **Galloping / bisecting the probe** in place of `probe++`: a farther chord can pass after a nearer one is
  refused, so every line could move — a separate decision.
- **Replacing the per-mark price reads with a cell-grid traversal:** the span proof's "identically the fine
  sum" would no longer hold.

## Open

- F2's cut: (a), (b) or neither.
- A forced crossing **away** from an end, to test what (a) would lose; every forced case so far is the one
  B/C zone end.
- Whether the phone has an idle core beside the three rung threads (F1's real gain).
- Why the `PULL` walk ending at the shore end is the slow one, and why the phone ran slower in the afternoon.
- Whether the instrument ships or is removed: it sits in the working tree, uncommitted.

## Review

**Round 1 (2026-10-21, the plan against the code and the 09:18 log):** F0 (audit the shared reads) was
closed by reading and folded into F1; the retry's cause became the boxed end, which gave F2 its two cuts; F3
became an alternative to F1; the critical-path column was named for what it adds.

**Round 2 (2026-10-21, the device results).** The report that came before this round was wrong in four
places, corrected above:

- Memo sharing was read as "the second walk answers 100–300 more price hits" and "~450 hard reads the first
  walk never gets". Both are confounded: a first walk scores up to 432 hard hits on its own, and the same walk
  run first and second shows the real share, 2–7 % of reads.
- "Zone searches ran slower than the old build" — the builds match the same afternoon; the phone changed.
- "The slower walk ends at the shore end in all six pull pairs" holds for `PULL` (12 of 12), not for
  `FINAL`.
- The instrument's own reading rule for memo sharing (`bwd − fwd` hits) was the same confound; rewritten.

Also: the grow-build was put at 29–42 ms from the `GRID` line and is 0.6–0.75 s, and F2's evidence widened
to C at 13:12, still one zone end.

## Implemented

**F2 cut (b), 2026-10-21** — the forced-crossing probe leaves open the water of any restrictive zone a route
end stands in and never names it (`RouteAvoidEngine.forcedCrossingNames`; `MultipassGrid.blockedCopy` gained
a `stayOpen` predicate). The probe returns at once, with no search, when every restrictive zone holds an
end. The retry therefore fires only on a crossing forced elsewhere. Pinned by five tests in
`RouteZonePhase4Test` (the revert pin: with the exemption off, `anEndZoneDoesNotMakeAnAvoidableCrossingForced`
fails). Full unit suite 1418 tests green, `assembleDebug` packaged. The `PROBE` trace line gained
`endZones=[…]`; the zone world of 14:15–14:17 should now log `reason=NONE grew=false` on pair B. The
device pass is the user's.

**Deviations from the plan:** the exemption is by zone containment of the end point, not by flood-filling the
end's slow water, so a zone that merely touches an end stays blocked (it would still wall the end in; the
`endZones=` field will show it). F1, F3 and F4 are not built.

**Ask hop (2026-10-21), no blocking finding.** Taken: the stale `forcedCrossingZoneNames` KDoc and the engines
doc now state the end-zone rule. Left, stated: the restrictive predicate is written twice in
`forcedCrossingNames` and `zones.zip(priced)` could be one `filter` (drops the `priced` parameter); the probe's
`stayOpen` cost (a point-in-polygon per restrictive cell, per rung and attempt) is unmeasured and untimed in
the trace; unpinned cases are a cell in both an end zone and a crossing zone, two ends in different zones, and
an end zone whose `routeLimitKn` sits at the pace; the avoidable-crossing test relies on the 3704 m default
reach and no test asserts that growth does not fire.
