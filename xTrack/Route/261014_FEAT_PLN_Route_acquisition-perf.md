<!-- scope: feature -->
# Route — the acquisition's cost: what is measured, and the levers left

**Date:** 2026-10-14 · **Status:** in design, for discussion · **Order:** the user's word of 2026-10-14 —
*"Looking at the way it is implemented. Is there a way to improve the performance?"*, then *"plan this"*.

**Scope:** the acquisition's own machine cost, not its answer. The coastal leg's optimistically low
reading is a different question with its own plan
([`261007_FEAT_PLN_Route_coastal-fine-grid-engine.md`](261007_FEAT_PLN_Route_coastal-fine-grid-engine.md)):
that one asks whether the figure is wrong, this one whether the rung is dear.

## What is there today

- **The three rungs are already concurrent.** Each `startLookup` launches its own job on
  `computeScope` — `SupervisorJob() + Dispatchers.Default`
  ([`RouteAvoidEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:201),
  [`RouteAvoidEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:326)).
  Only the **grid build** is shared: the first rung creates it as a `LAZY` deferred under the holder's
  lock and the other two await the same one, so an arm costs one rasterise and three A\* passes
  ([`RouteAvoidEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:443)).
  The fold is mutex-guarded because the rungs land concurrently, and a fresh arming rebuilds — the
  holder is fresh per `routesToCompute` and disposed when the arm's last lookup ends.
- **The spine inside a rung is a chain**: corridor → harvest → rasterize → ends → A\* → pull → snap →
  fine → corner pass → clock. There is no parallel piece inside it; the only candidate is the fine
  pass's per-zone re-solve, which is a set of independent local searches run sequentially.
- **The static half is already amortised, and deliberately.** The fine layer's marking — the depth
  wall, the four collars, the marker price — is cached as pace-free, λ-free tiles keyed on every value
  the rasterisation reads *except* the pace, and the λ scaling is a read-time multiply
  ([`FEAT_DOC_Route_engines.md`](FEAT_DOC_Route_engines.md)). `SelectiveMaskCache` holds the
  geographic union on the same stamps. The **coarse interior is not tiled at all**.
- **The instrument does not cover the whole rung.** `instrumentCoarseWalk` — the one `DEVICE PASS`
  line per rung, carrying `coarseMs` and `fineMs` — is emitted at
  [`RouteAvoidEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:604),
  *before* the corner pass, the corner verdicts, the profile clock, the attribution passes and
  `forcedCrossingNames` all run. Those are inside no figure at all.
- **The records already on disk** are the budget's first draft: `route_perf_pass.log`,
  `route_corner_pass.log` and the `test-hop*.log` family. Every `STORE load` they hold reads
  `hit=false`, and every arm logs `TILE built` — no warm arm has ever been measured on the current
  pipeline, which is also why `FineTileMap.hitRate` still has no production reader.

## The budget as the records stand

Taken from the device traces, to be extended in P0 — figures quoted from their own lines, never
restated as truth:

- **the pull's price walk** — `PULL zoneM=851.3 ms=2092.1 clearMs=403.0 priceMs=1687.3 priceReads=5198
  marks=5510` ([`route_perf_pass.log`](../../route_perf_pass.log:500)); `priceReads` tracks `marks`
  within a few per cent.
- **a crossing re-solve that is refused after running** — `FINE zone=Zone 10 kn … spliced=no
  reason=worse local=38021.0 coarse=25011.6 ms=3745.3`
  ([`route_perf_pass.log`](../../route_perf_pass.log:942)); a second at `ms=650.2`, and a third at
  `ms=604.3`.
- **the forced-crossing probe** — `PROBE restrictive=[Zone 10 kn, Zone 5 kn] avoiding=false
  expansions=18047 passable=18066 aimClosed=false forced=[Zone 10 kn, Zone 5 kn]`
  ([`route_perf_pass.log`](../../route_perf_pass.log:255)): a second full search, outside the budget.
- **the rung pair** — `DEVICE PASS … plan=selective … expansions=17409 … coarseMs=2106.0
  fineMs=5867.1` against `plan=evolutive … expansions=9262 … coarseMs=1309.9 fineMs=3990.6` on the
  same corridor ([`route_perf_pass.log`](../../route_perf_pass.log:79),
  [`route_perf_pass.log`](../../route_perf_pass.log:647)): the fine half is the larger, and the
  collared plan expands about twice the cells the ribbon one does.

## The levers, ranked by what is measured

1. **Refuse the crossing re-solve before its search completes, with an incumbent that bounds the
   *pulled* cost.** The fine pass runs a local A\* inside a zone's box and then discards it when
   `local >= coarse`; the discarded cost is the whole solve — two fields, a fine rasterize, the
   search, two pulls and a snap — paid at
   [`RouteFinePass.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:226)
   only after the comparison that could have been made earlier. The fix is an **incumbent-bounded
   local A\***: the coarse stretch's cost, converted to the search's own metres-equivalent unit, is
   handed in as the incumbent, any node with `f ≥ incumbent` is pruned, and the coarse line stands
   when the aim is never expanded. The one soundness condition: the post-hoc comparison measures the
   **pulled** line, whose cost is strictly below the raw path's `g` (the pull shortens the staircase,
   R96), so the incumbent must bound the pulled cost — derived from the staircase's worst-case
   overestimate, or the lever is validated byte-for-byte on the recorded lines and refused if any
   recorded `spliced=yes` case now refuses. Its falsifier is exactly that: a splice the current code
   keeps and the incumbent refuses.
2. **Read the same water once across the pull pair.** The pull and the final pull run over the same
   setup, but each `pull` call mints a fresh `MarkMemo`
   ([`MultipassPull.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:288)),
   so the final walk re-reads every hard distance and price the first already paid for the unchanged
   vertices. Thread one memo through the pull → snap → final pull, and through the fine pass's two
   pulls. It is exact: the memo is keyed on the point, bound to the field's identity, and the
   `hardBlocked` step test is not memoized, so no verdict moves.
3. **Complete the instrument.** The A\*, the corner pass, the profile clock and the probe run after
   `instrumentCoarseWalk` and are untimed, so the budget cannot say where the rung's money is; extend
   the rung's timing to them on the same nullable-tally path `PullTiming` already uses.

## Rejected as low benefit or unsound

- **A span proof over the depth-priced walk** — unsound: the depth source's honest floor is zero, so a
  per-metre gradient over a raster distance field can never be proven past.
- **Sharing the fine field across the rungs** — the cost field carries the pass's λ, so the field is
  not shareable; only λ-free constants would remain, and they are cheap.
- **Tiling the coarse interior, or widening the pre-warm** — the coarse tile's key would be the two
  ends, so it hits only on a re-arm of the same pair; a wider pre-warm is gated by idle and
  battery-saver. Kept below only as the constraint it is.
- **Reusing the probe's raster** — the probe's cost is its expansions, not its rasterize, so a raster
  donation saves little while aliasing the grid the three rungs share.
- **Gating `marginWaived`'s end checks** — exact but micro; the loops are short and the win is noise.

## What cannot move

- The crossing re-solve, the probe and the pull's price walk are keyed on the **line** and the
  **pair**, not on static geometry — a cache would need a combinatorial key (a zone plus an entry and
  an exit) and would rarely hit.
- The coarse interior's shape is the corridor box, so tiling it helps only a repeat arming over the
  same water.
- Physical facts stand as they are: A\* is a metres-equivalent search, the price field is shared, and
  no lever above may change a drawn line, a clock or a ranking.

## Feasibility and risks

- **High** for P0 and P3: P0 reads records already on disk, and P3 adds timings on a path the code
  already gates behind a nullable tally.
- **Medium** for P1 and P2: P1 must bound the **pulled** cost rather than the raw `g`, or it refuses a
  splice the current code keeps; P2's memo only pays where the snap left vertices unmoved — both are
  bounded by the recorded cases, not guaranteed.
- **The incumbent is the one place the drawn line can change.** Every other lever returns the same
  double; P1 is the only one whose threshold can move the line, so its acceptance is byte-for-byte on
  the recorded lines, never a fixture.
- **The real brake is evidentiary.** Every step whose subject is a *reading* waits on a device pass,
  and R97 says that pass is **the user's own** — the agent never touches the device. A lever may land
  on fixtures and the unit suite, but its saving stays unproven until the user runs one.
- **Coupling**: the walk's `priceReads`/`marks` are also the perf harness's pinned evidence
  ([`RouteSelectivePerfEvalTest`](../../app/src/test/java/ykws/android/maro/spatial/RouteSelectivePerfEvalTest.kt:45)),
  so a change to the walk's reads must be re-baselined, not quietly adjusted.
- **The honest null case** must stay on the table: the budget may show that no lever is worth its
  risk, and P0 is designed to be able to say so.

## Phases

1. **P0 — the budget.** Tabulate from the records already on disk: per rung `coarseMs`/`fineMs`, the
   pull's `priceMs` share, every `spliced=no reason=worse` re-solve with its `ms`, the probe's
   expansions, and the `TILE built` / `STORE load` counts. Record what the instrument does not cover.
   *Acceptance:* a table with each step's measured share; no code change.
2. **P1 — the incumbent-bounded re-solve.** Count the refusals and their `ms`, then derive the
   incumbent from a bound on the pull's staircase saving, or state that the lever is validated
   byte-for-byte on the recorded lines. *Acceptance:* every recorded `spliced=no reason=worse` case
   refuses before its search completes, every recorded `spliced=yes` case still splices, and the drawn
   line is unchanged on the recorded pairs.
3. **P2 — the memo across the pull pair.** Thread one `MarkMemo` through the pull → final pair and the
   fine pass's pair. *Acceptance:* the second walk's unchanged marks answer from the memo, the
   `priceReads`/`marks` tally moves only on the hit side, and the pulled line is byte-identical.
4. **P3 — the instrument.** Extend the rung's timing to the A\*, the corner pass, the profile clock
   and the probe. *Acceptance:* the parts of a `DEVICE PASS` sum to the rung's own wall, the device's
   line format is otherwise unmoved, and the answer is unchanged.

**Acceptance for the plan as a whole:** the drawn line, the clock and the ranking are unmoved on a
device pass; no dependency is added; the seam's shapes are untouched; and each landed lever cites the
figure that prices it.

## Implemented

—
