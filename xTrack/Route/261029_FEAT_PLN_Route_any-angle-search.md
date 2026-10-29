# Route — the any-angle coarse search

**Status:** partly implemented, the switch default off. P0 and P1 implemented 2026-10-29 (§12), P1's cost gate
failed (5–28× against 2×); the re-design (§13) was built as a **prototype of P2** behind `route.search.anyAngle` and
measured (§14); its **first device reading is P4's** (§15): the 43° bend is gone, the lines within §6's 5 %, the
attempt +20–43 % on one warm run after a cold one, P5 without ground. **Next: P6 (§15.3)** — an interleaved device
re-read, the fine crossing re-solve behind its own key (D6), and the 16-cell reach re-checked on the desktop first.
P3 and P5 wait on P6's bar. Sections §4–§11 are the original design; where a later section supersedes one, a
pointer at its head says so. Branch `feature/roouttinggg`.

**One line:** the coarse A\* steps in eight directions, so every line it hands on carries the grid's own
headings; every stage after it moves or deletes points to hide that, and each new shape needs a new patch.
This plan makes the search draw taut lines itself (Lazy Theta\*) over the walk, cells and prices it already
has, behind a switch, measured on the desktop and then on the device, so the patches can be retired.

## 1. The defect — today's device instance (2026-10-29 08:38, "Closest", λ 1, 24 kn, `evolutive`)

- The line rounds the 10 kn zone's east corner at 43.54447,7.15856, runs **due north for 2 067 m along one grid
  column** (lon 7.15856), turns 43° at 43.56306,7.15856 — the user's bend — and heads for Pointe du Bacon's
  band edge.
- The sweep kept the turn for `PRICE`: the chord that skips it crosses the zone's price collar (42 marks,
  75 s) and Bacon's band (21 marks, 143 s), `chordSec=521 pathSec=325`.
- The lone-turn slide (P1 of `261028_FEAT_PLN_Route_lone-turns.md`) found it (`turn=42.9 slackM=48`) and could
  not move it: sliding one point south puts the next leg into Bacon's band. The right line moves **two** points
  together — leave the collar earlier, touch the band on its east side — which no window searches.
- This rung spent 2.4 s of its 4.7 s in the approach pass (9 windows, 617 candidates) and still left five lone
  turns. The same shape stood on "Around" (2026-10-28, 1 483 m of slack) and "Balanced" (the S of 2026-10-28).

## 2. The root, from the code

- `MultipassSearch.forEachStep` enumerates eight neighbours (`STEP_DR/STEP_DC`); a path is a chain of 45°
  steps, and the aim is reached along a staircase whose vertices are cell centres.
- `MultipassPull.walk` is a forward greedy on that chain: it commits `path[probe − 1]` at the first refused
  chord. A committed vertex is therefore **a cell of the staircase**, which hugs an obstacle only where the
  staircase happened to run beside it; past the obstacle the staircase's heading is the grid's, not the
  tangent's, and the vertex lands kilometres from where a taut line turns (lone-turns §2, bare-bends §8).
- Everything after the pull repairs by special case — the slow gate, the land turn, the S merge, the crossing
  slide, the split, the lone slide, the sweep, the straightener, the nudge, the corner pass — each curing one
  shape, each paid on every rung (`APPROACH ms=1231–2421` here).

## 3. Options

| Option | Fixes the shape | Cost | Symmetry | Verdict |
|---|---|---|---|---|
| **A. Two-point slide in the approach pass** | this instance only | one more window kind, 48 × 48 evaluations | as today | rejected — the fifth special case |
| **B. 16 headings (knight steps)** | halves the error (26.6°), does not remove it | 2× edges per expansion; a knight step crosses two cells that must be read | as today | rejected alone — retires nothing; it is what Theta\* degrades to where every chord is refused |
| **C. Lazy Theta\* on the walk** | the lone turn, the S, the two-feature bend — the vertex is chosen among all frontier cells, not on one staircase | one chord read per expansion, on the grid's own arrays | the canonical-end walk and the canonical chord price, unchanged | **recommended** |
| **D. Field D\* / interpolated A\*** | exact through weighted regions | a different search; the two-layer seam and the step guard rewritten | new | out of reach for this plan |
| **E. Visibility graph over the obstacles' corners** | exact tangents on hard obstacles | the band, the zones, the marker wells and the depth ramp are prices, not walls; the gate's edge is a contour of 5 516 chunks | by construction | out of reach; noted for a later era |

**Why C, stated once.** Theta\* (Nash & Koenig) keeps a parent pointer that may be any earlier cell with a clear,
no-dearer straight line, so the path it returns is already taut; where the straight line is refused (land, the
wall, a chord dearer than the staircase it skips) the node takes its grid parent, and that fallback is the pull's
commit — but made at **every** frontier cell with the aim's cost in hand, so the vertex is the cheapest tangent
cell the corridor holds, not the one the staircase passed. The Lazy variant checks the line once per expansion
rather than once per neighbour, which is the 8× that makes it affordable.

## 4. Design

### 4.1 The chord read — `WalkWindows.chordCostSec(fromIdx, toIdx, guard, ends)`

> **Superseded in part (§12, §14).** The read is `ChordReader` now, walked on the fine unit lattice with a coarse
> block read in one piece where no fine window touches it. **"One pricing under the switch" is not what the
> prototype does:** a one-cell step is priced at the destination cell's rate and a chord at each cell's own — the
> two pricings R4 ruled out — recorded as a deviation for P3 to close.

- A supercover walk of the lattice cells the straight segment between two slot centres crosses, in lattice
  metres (the unit the steps already pay, so `heuristicScale` keeps its proof untouched — review R11), priced
  per cell as a step prices a cell today: `(sourceCostSec + zonePriceSec(limits) + berthK × berth) / cellM ×
  metres inside the cell`. Markers, the depth ramp, the band and ring collars, the berth: **the same per-slot
  values the step reads**, so no weight moves.
- **One pricing under the switch.** Every link of a path — a one-cell step included — is priced by the cells it
  crosses, so a step costs half at each cell's rate instead of the destination's rate over the whole edge
  (`MultipassSearch.kt:475`). The destination-rate convention recorded in `RoutePassRunner.kt:219-222` and the
  direction-symmetry plan is rewritten with the switch; every step-priced fixture moves (§6).
- **Refusals**, each `NaN`:
  - a cell no window holds (the corridor is the bound, as for a step);
  - a cell that is not `passable` (land, a Blocked marker's body);
  - the gate, per cell crossed: the stored bound first — a chord's piece inside a cell lies up to `cellM/√2`
    from the centre, so the proof is `gateDistanceM ≥ hardM + cellM/√2`, trusted only within
    `seedReachM(cellM)` as `StepGuard.clear` trusts it — and a measured `GateRule.keepsWall` **over the
    unproved sub-segments only**, never over the whole chord (`segmentDistanceM` sweeps the segment's box,
    O(L²) on a diagonal — review R9).
  - **The ends are never whole-waived.** `StepGuard.allows` returns `true` for any step leaving a non-strict
    start or entering a non-strict aim — a one-cell rule that would waive kilometres on a chord, against the
    gate rule (`FEAT_DSC_Route.md`, Rules: every drawn straight past the 20 m waiver keeps the wall). A chord
    touching an end is read through `clear`'s rim-cut path (`keepsWall` cut at the waiver's rim), strict or not.
- **Two layers.** The supercover runs on the coarse lattice. Wherever a fine window holds the cell the segment
  stands in, the segment is sub-walked on the fine lattice and the coarse cell is not read; the coarse cell is
  read only where no fine window holds the point. A block whose fine copy is held but whose other fine cells a
  segment crosses are not held (fine tiles are `sideCells` on the fine lattice, not a multiple of the ratio —
  `RouteGridBuilder.kt:913-916`) prices those cells at the coarse rate rather than refusing — the step graph
  passes coarse → copy → coarse there (`MultipassSearch.kt:334-355`). A chord ending on a fine non-copy cell
  inside a block whose copy is land is read on the fine lattice, as the face crossing reaches it
  (`WalkLattice.kt:745-758`). Both are P1 tests.
- The chord's cost is direction-symmetric (the cells crossed and their lengths are the same from either end,
  summed in canonical order) — a small gain over today's destination-rate step.
- **Cost, to be measured before the walk exists (P1).** A step today is ~4 window reads, a `ZonePrice` call,
  two `gateDistanceM` reads and a push; a chord cell adds a `LongIntMap` lookup (`WalkLattice.kt:607-614`). In
  open water the parent stays far back, so a chord grows to the corridor's length — 10 km ≈ 100 coarse cells —
  and 11–17 k expansions read 0.5–1.7 M cells a walk, 3–6× today's relaxations. The search alone may cost 2–5×
  today's; the plan stands only if the pull, the fine pulls and the approach pass pay that back (§6).

### 4.2 The walk — `walkOnce` under `route.search.anyAngle`

> **Superseded (§13.1, §14).** The built walk pushes at the grid's own step price, reads the one chord at pop and
> falls back to the grid parent's value — no chord priced at push, no `forEachStep` re-enumeration at pop.

- `parent[]` beside `cameFrom[]`; `g`, `h`, the heap, the closed set, the cadence and the tie comparator (`f`,
  `g`, slot) unchanged — the ties themselves move with the prices.
- **Push (relax):** the candidate for `s'` is `g[parent[s]] + chordCostSec(parent[s], s')`, assumed clear
  (lazy). A better `g` sets `parent[s'] = parent[s]`. The one-cell fallback stays on `relax` + `StepGuard.allows`
  — the only writer of `heldRefused`, which the held/waived re-walk reads (`MultipassSearch.kt:253-265`,
  `GateEndStepTest`); a chord refusal records nothing, since a refused chord is never the only edge into a cell.
- **Pop, in this order:** pop → `closed[idx]` → stale (`poppedG != g[idx]`) → **re-parent** → `closed[idx] =
  true` → break on the aim. The re-parent reads `chordCostSec(parent[s], s)`; where it is refused or dearer, it
  iterates `forEachStep` from `s` filtered on `closed[]` and takes the best `g[n] + link(n, s)` — the one-way
  seam edges it cannot see are the accepted miss (review R6). **The aim is re-parented too** — today's loop breaks
  on the aim before any expansion (`:401`), so the last chord would otherwise stay lazy and `costSec` unchecked
  (review R1). A re-parent may raise `g[s]` after its heap entry is gone: safe because `closed` is read before the
  stale test, and it pushes nothing; nodes whose `f` lies between the lazy and the fixed value expand late — D2's
  accepted sub-optimality, not a corruption.
- The path is the parent chain from the aim: 8–20 vertices instead of 140 cells. `SearchOutcome` gains
  `chordReads`, `chordCells` and `chordRefusals` beside `expansions`, summed on the fallback walk as
  `expansions` is (`RoutePassRunner.kt:250`, `MultipassSearch.kt:270`).
- The key `route.search.anyAngle` is added to `maro.properties` first (ONE HOME), its accessor on `AppConfig`.
- Switch off: the code path is today's, bit for bit.

### 4.3 Preserved by construction

- **Zones, band, markers, depth ramp, berth:** priced from the walk's own arrays, per cell crossed, at the
  rung's λ; a chord cannot bypass a price any step pays.
- **Walls and the gate:** a chord through land, a Blocked body or under the wall is refused; an end's waiver
  cuts the read at its rim and never waives a chord.
- **Symmetry:** `searchFromCanonicalEnd` is untouched; the chord read is canonical; the comparator is today's.
  The two-sided pull stays (it now has little to do).
- **Corridor and memory:** a chord never leaves the windows; one `IntArray` more per walk.

### 4.4 Downstream

- The runner hands the parent chain to the pull as the "cell path" (`RoutePassRunner.kt:121-122, 151`); the
  pull, the snap and the final pull run unchanged and mostly agree with it (a chain of 10 points pulls in
  milliseconds — `PULL ms=570–871` today). The pull re-prices on the field at 25 m marks, so a chord the grid
  accepted a cell early may be refused there and its vertex kept — a cell's error, not a kilometre's.
- The fine pass, the sweep, the straightener, the nudge and the corner pass run unchanged.
- The approach pass runs unchanged **in this plan**; its counters (`loneTurns`, `sWindows`, `splitWindows`,
  `crossings`, `RouteApproachPass.kt:360-363`) are the reading that says which of its cases the search now
  makes redundant (P5).
- Stale text to rewrite with the switch: R96 and `RoutePassRunner.kt:125-128` call the cell path "an
  overestimate by nature (jagged)" — false for a taut chain; `SearchCanonicalEndTest`'s red-first precondition
  (`:68`) may not stay red under the switch and is re-read.

## 5. Phases

> **Where they stand (2026-10-29).** P0 done; P1 done, its gate failed; P2 built as a prototype (§14) — still owed of
> it: the five named suites run with the switch **on**, and the lone-turn fixture test; P3's fine re-solve step moved
> to P6b (§15.3); P4 read once (§15); P5 without ground until a counter reads zero. P6 (§15.3) comes before P3.

- **P0 — baseline (half a day).** Harness on the three device pairs (Lérins → La Salis, La Salis → Le Village
  Englouti, the 08:38 pair 43.57013,7.09875 → 43.57061,7.13144) and the 24-pair survey: every rung's `LINE`
  (distance, duration, slow seconds, `minDepth`, `gateEdge`), `loneTurns`, and **`SEARCH ms` beside** `PULL`,
  `FINE` and `APPROACH` — the search has no clock line today and the budget cannot be judged without one. Two
  runs each (±10 % is noise).
- **P1 — the chord read (one day), with a numbered gate.** `chordCostSec` with its tests: a chord along a row
  equals the step sum; through land, a wall, a Blocked body and under the gate is `NaN`; across the seam prices
  its fine cells at 20 m; the two seam cases of §4.1; `a→b == b→a`; an end's rim cut. **The gate:** the mean cost
  of a chord read over the baseline walks' expansions × corridor-length chords, measured on the harness, must
  leave the projected search under **2× today's `SEARCH ms`**; above it, D4's measured read is the first suspect
  and the plan stops for a re-design before P2.
- **P2 — the walk (one day).** Lazy Theta\* behind the switch; `SearchCanonicalEndTest`,
  `DirectionSymmetryRepairsTest`, `GateEndStepTest`, `SeamCrossingTest`, `RouteFineReachabilityTest` run under
  both settings. A new test: on the lone-turn fixture the search's own path has no vertex with slack.
- **P3 — the pipeline (one day).** The runner on the chain; the fixtures re-baselined with the switch on, each
  line's before/after cost reported (§6); the convention text and R96 rewritten; the harness and the survey
  re-run; the budget read.
- **P4 — the device run (the user's).** The three pairs; `loneTurns`, the rung times by stage, the lines read on
  the map.
- **P5 — retirement (after P4, a plan of its own).** Each approach-pass case whose counter reads zero on the
  device and the harness is removed; the slack probe and the switch with them.

## 6. Guards

- **No line dearer beyond 5 %.** For every fixture and survey pair, the settled line's `lineCostSec` (the pull's
  `totalSec` clock, `RouteFinePass.kt:42-50`) is ≤ the baseline's × 1.05: the field re-prices at 25 m, Theta\* is
  not optimal through priced water and the greedy pull walks a different chain, so a few percent either way is
  legitimate; a line over the bound is a failure. **Relaxed 2026-10-29 on the user's word** from the baseline +
  `route.avoid.ladder.tieToleranceSec` (30 s) — a number borrowed from the crown's tie-break, not a functional
  limit: the prototype's first batch read +3 to +5.5 % on the price scale for +19 to +36 s of slow water on trips of
  17 to 33 minutes, which the user judged fine. Every line's delta is reported, and the crown the user sees is
  R98's weighted slow seconds plus trip, read beside it.
- **No wall crossed, no gate breached.** Every `LINE` keeps `minDepth ≥ gate`, `gateEdge ≥ hardM`, `bodyEdge`
  as today; `AvoidDepthGateTest`, `GateGuardsTest`, `GateCornerTest`, `GateEndStepTest`, `RouteMarkerCostTest`,
  `AvoidMarkerCostTest`, `AvoidBandCostTest`, `BerthCostTest` green under both settings.
- **Symmetry.** `RouteDirectionSymmetryTest` (`-Dmaro.prebake=true`): zero plan-pair runs over the bar, the
  line gap ≤ today's 0.4 m.
- **Performance budget.** The rung's wall time on the harness pairs ≤ the baseline's; the target is lower, the
  search's own increase paid back by the pull, the fine pulls and the approach pass. `expansions` and
  `chordCells` are readings beside it, not bounds. **On the device the budget is §15.3's bar**, which states its
  tolerance and its lines.
- **Switch off = today.** The whole suite green with the key `false` **and `route.fine.crossingResolve` at today's
  `true` (D6)**; the engine fixtures' recorded lines unmoved there.

## 7. Decisions (the user's)

**Taken 2026-10-29, on the recommendations:** D1 the grid's arrays · D2 lazy · D4 stored bound plus unproved
sub-segments · D5 no 16-heading step. D3 (one code path after P5) waits on P4's device reading.

- **D1 — the chord's price: the grid's arrays (recommended) or the pull's field price.** The field price is the
  exact 25 m-mark sum the pull reads, 50–100× dearer per chord; the grid price is what the search already
  charges a step, and the pull re-prices the chain on the field afterwards.
  Objection: a collar's edge sits between two cell centres, so a chord may be accepted a cell early and the pull
  then keeps the vertex — a cell's error, not a kilometre's.
- **D2 — Lazy (recommended) or full Theta\*.** One chord read per expansion against eight.
  Objection: the lazy re-parent can settle on a worse neighbour than the full check would; Nash measures the
  difference under 1 %.
- **D3 — ship with the switch on after P4, or keep both.** Recommended: one code path after P5, the switch
  deleted with the retired cases. **Now waits on §15.3's bar**, not on P4 alone.
  Objection: the harness does not reproduce the device's coarse path, so P4 is the only proof that counts.
- **D4 — the gate on a chord: stored bound per cell plus a measured read over the unproved sub-segments
  (recommended), or a measured read on every chord.** The step guard already does the former.
  Objection: near the coast many cells cannot prove, and a measured read is the pull's dear read; P1's gate
  says whether it binds.
- **D6 — the fine crossing re-solve: its own key, today's default (taken 2026-10-29 on the recommendation, the
  default left to the user).** It splices nothing in either build on the device's three pairs (`spliced=no
  reason=worse` on every rung) and costs 1–2 s a rung on the 08:38 pair in both, so its waste is not the any-angle
  walk's. Turning it off under the switch alone would let B pass §15.3's bar on a removal A never had; turning it
  off for every build moves the shipped lines (§6, "switch off = today"). So it gets `route.fine.crossingResolve`
  (`maro.properties`, `true`, accessor in `AppConfig`), and P6's builds A and B carry the same value. Whether the
  default moves to `false` is the user's, on P6's reading and on the harness survey showing no `FINE splice` worth
  keeping.
  Objection: three pairs is a small base for a stage the engine has run since the fine pass exists.
- **D5 — 16 headings first.** Recommended: no. It is a half measure that retires nothing and would be re-done.

## 8. Out of this plan

- Retiring the approach-pass cases, the slack probe and the switch (P5, its own plan on the device reading).
- A coarser coarse cell (150–200 m) — once the line no longer inherits the grid, fewer expansions become a
  lever; measured after P4.
- The fine pass's crossing re-solve on an any-angle fine walk — the same change on the fine window, later.
- Field D\* and the visibility graph (§3 D, E).
- Overlapping the two pull walks (`261021_FEAT_PLN_Route_pull-cost.md` F1) — the pull shrinks here instead.

## 9. The plan argues against itself

- **Theta\* is not optimal through priced water.** A chord refused on price falls back to a cell; the vertex is
  the best cell on the frontier, still a 100 m quantum from the true tangent. The pull and the corner pass take
  it from there, as today.
- **Every fixture line moves.** That is the point, but each move is a review: §6 turns the review into one
  number per line (cost, within 5 % — first 30 s, relaxed 2026-10-29) and three bounds (depth, edge, symmetry).
- **The search gets dearer before the rest gets cheaper — by 2–5× on the reviewer's count.** If P1's gate fails
  the plan stops there; if it passes, the budget is still the whole rung's, and only P3 reads it.
- **The device's coarse path is not the harness's.** The harness proves the mechanism and the budget; only P4
  proves the device's lines.

## 10. Review — 2026-10-29

One reviewer read the plan against `MultipassSearch`, `WalkLattice`, `RoutePassRunner`, `MultipassPull`,
`ShallowEdgeIndex`, `RouteFinePass`, the named tests and the feature file. Three blocked; all folded in.

| # | Finding | Disposition |
|---|---|---|
| R1 | **Blocks.** The aim's chord is never verified: the loop breaks on the aim before any expansion, so the last chord stays lazy and `costSec` unchecked. | §4.2 fixes the pop order and re-parents the aim. |
| R2 | **Blocks.** "The ends' waiver as `allows`" whole-waives a chord — kilometres, against the gate rule. | §4.1: a chord touching an end is read through the rim cut, never waived. |
| R3 | **Blocks.** Routing one-cell links through the chord read bypasses `allows`, the only writer of `heldRefused`; the held/waived re-walk never fires and a boxed end answers no path. | §4.2: the one-cell fallback stays on `relax` + `allows`; a chord refusal records nothing. |
| R4 | **Should fix.** Two pricings in one walk (destination rate for start links, per-cell for chords). | §4.1: one pricing under the switch; the convention text rewritten; fixtures expected to move. |
| R5 | **Should fix.** The seam rule misses a fine non-copy cell under a land copy, and a block whose fine tile does not hold every cell a segment crosses. | §4.1: sub-walk wherever a fine window holds the cell; unheld cells of a held block at the coarse rate; both P1 tests. |
| R6 | **Should fix.** The re-parent must enumerate the full edge set, and `forEachStep` is not reversible. | §4.2: it iterates `forEachStep` filtered on `closed[]`, the one-way miss accepted; `expansions` is a reading. |
| R7 | **Should fix.** Raising `g` after the pop needs the `closed` test before the stale one, and no push. | §4.2 states the order. |
| R8 | **Should fix.** "No line dearer, a failure" is unachievable: the field re-prices, Theta\* is not optimal, the pull walks another chain. | §6: `lineCostSec` within 5 % of the baseline (first set at `tieToleranceSec`, relaxed 2026-10-29); every delta reported; R98's crown named beside it. |
| R9 | **Should fix.** The budget cannot be judged: no `SEARCH ms` baseline; chords reach the corridor's length (~100 cells); `keepsWall` over a whole chord is O(L²). | §4.1 costs it honestly; P0 adds the clock; P1 is a numbered gate; the read covers unproved sub-segments only. |
| R10 | **Should fix.** "reach/2" names nothing; a chord's piece lies up to `cellM/√2` from the centre. | §4.1 states the bound. |
| R11 | **Note.** Lattice-metre chords never undercut the scaled heuristic. | Confirmed; §4.1 cites it. |
| R12 | **Note.** R96 and the runner comment call the cell path jagged; `SearchCanonicalEndTest`'s red-first precondition may not hold. | §4.4 lists them for P3. |
| R13 | **Note.** No `route.search.*` key exists; the fallback walk must sum the new counters. | §4.2. |

**Confirmed:** the thirteen named tests exist and pin what §5–§6 say; `forEachStep`'s edge set and the seam
rules are as §4.1 describes; the runner hands `start + centres + aim` to `pullBothWays`, so a short chain needs
no runner change; the approach-pass counters exist; `lineCostSec` is the pull's own clock; no R-rule is
contradicted.

## 11. The stages after the search — what each is for, and what is left of it once the search is taut

> **Disproved for now (§15.2).** On the device the approach pass did not idle — it still moves 1–5 points a rung and
> the lone turns stand — so the "retire" verdicts below wait on a counter reading zero. The re-solve row's reason
> (a staircase losing to a taut line) does not fit either: it splices nothing in build A too (§15.2, D6).

The pipeline per rung (`RoutePassRunner.runPass`, then `RouteFinePass.refineOriented`, then
`RouteAvoidEngine` 772–782): search → pull (two-sided) → snap → final pull → fine crossing re-solve and splice →
fine pull → snap → sweep → approach pass → straighten → nudge → corner pass. Today's cost is the 08:38 "Closest"
rung (grown walk, 4.7 s).

| Stage | Cures | Today (ms) | After the fix | Verdict |
|---|---|---|---|---|
| Pull, two-sided | the staircase; the pair's one line | 570–871 | a 10-vertex chain, re-priced on the field at 25 m: drops a vertex the grid accepted a cell early | keep; cost collapses |
| Snap to corners | a vertex within a set's radius moved onto the exact corner | ~100 | a Theta\* vertex is the tangent **cell**; the snap takes it to the tangent **point** | keep — more useful than today |
| Final pull | re-tension after the snap | 80–140 | as the pull | keep |
| Fine crossing re-solve + splice | a zone crossing re-solved at 20 m | ~900 (`spliced=no reason=worse`) | its local search is the same 8-direction A\*, so its answer is a staircase that loses to a taut coarse line | **disable until the fine walk is any-angle too** (§8); P3 reads `spliced=` |
| Fine pull / settled | field-first re-tension at the fine cell | part of `fineMs` 2 300–2 700 | as the pull | keep; shrinks |
| Sweep (lookback window, allowance) | a point whose skipping chord is no dearer | small (`dropped=0` today) | a taut chain holds no such point except where the field disagrees with the grid | **retire candidate**; P4's `dropped` decides |
| Approach — slow-gate slide (`SLIDE`, the movable slow approach) | the approach point to slow water, on the field | 1 231–2 421 for the whole pass | the search places the vertex on the best cell; the slide refines it within the 100 m quantum | keep as the one polish, if its counter shows moves |
| Approach — land-turn rule | a merge that would delete a land turn | — | lives inside the merges | goes with them |
| Approach — split (P3) | one turn standing for two features | — | each frontier cell picks its own tangent, so two features get two vertices | **retire** |
| Approach — S merge at the crossing (P4a) + crossing slide (P5b) | two price turns standing for one | — | the S is the staircase's; a taut chain has none | **retire** |
| Approach — lone slide (P1) + slack probe (P0) | a lone price turn with slack | — | the vertex is chosen among all frontier cells with the aim's cost in hand | **retire** |
| Straighten (Douglas–Peucker 10 m) | near-collinear runs | ~0 (`11→11`) | a taut chain has none; a splice could leave one | retire candidate; harmless |
| Nudge (medial) | a passage narrower than the preferred standoff | ~0 (never fired) | independent of the search's headings | keep, unrelated |
| Corner pass (clothoid, compound merge) | driveable bends; two same-way bends read as one turn | 20–50 | independent of where a vertex stands; fewer same-way pairs to merge | keep, unchanged |
| Canonical end, two-sided pull, canonical snap and fine orientation | one line from either end | — | the chord read is symmetric by construction, so these become belt and braces | keep |
| End repair, probe, corridor growth, berth, gate | not line shape | — | untouched | keep |

- **What the search fix buys, by this table:** the approach pass's four shape cases (1.2–2.4 s a rung), the fine
  crossing re-solve (~0.9 s, losing today) and most of the pull (0.6–0.9 s) — against a search 2–5× dearer than
  today's, whose clock P0 adds. The polish that remains — snap, the slow-gate slide, the corner pass — is the
  part that works on exact geometry rather than on the grid's shape.
- **The order of retirement is P5's, on P4's counters:** a case is removed only when its counter reads zero on
  the device and the harness; the fine re-solve is switched off in P3 on its own `spliced=` reading, since it
  loses already.

## 12. P0 and P1 — 2026-10-29, `#implement`

All harness runs at the harness's 30 kn on `evolutive`, best of five per rung (one per survey pair), two runs each;
the reports are kept outside the repo. Every line, reading and survey line is identical between P0 and P1.

**P0 — the baseline.**
- `RoutePassRunner` traces `SEARCH ms= expansions= passable= found= reversed= rewalked= costSec=` after the search,
  so the device's logcat carries it too. `RouteBendHarnessTest` prints a `CLOCK` line per rung (search, pull, snap,
  final pull, fine pass, approach pass, rung) and a `READING` line (length, limit-read duration, `slowSec` — the
  seconds over the pace, `lineCostSec` on the fine pass's water — §6's guard number, `minDepth`, `gateEdge`,
  `bodyEdge`, `loneTurns`); `-Dmaro.harness.survey=true` routes the direction-symmetry survey's 24 seeded pairs, the
  draw now shared as `RouteSurveyPairs`.

| Pair | Rung | `SEARCH ms` (run 1 / 2) | `rungMs` | `lineCostSec` | `loneTurns` |
|---|---|---|---|---|---|
| Lérins → La Salis | Around / Best / Fast | 30.7/34.6 · 29.9/30.0 · 27.9/29.4 | 428–468 · 359–361 · 377–382 | 6 240.3 · 4 164.1 · 2 137.1 | 2 · 2 · 2 |
| La Salis → Le Village Englouti | Around / Best / Fast | 17.2/19.9 · 18.4/17.2 · 16.7/16.0 | 325–340 · 269–294 · 280–298 | 2 059.3 · 1 570.4 · 1 064.3 | 5 · 5 · 6 |
| 43.57013,7.09875 → 43.57061,7.13144 | Around / Best / Fast | 12.0/14.9 · 12.6/11.9 · 10.8/10.3 | 273–278 · 267–270 · 263–274 | 4 227.5 · 2 898.6 · 1 546.5 | 2 · 2 · 3 |
| survey, 24 pairs (sums) | Around / Best / Fast | 96.5/87.5 · 71.0/71.1 · 70.9/65.2 | 13 526–13 962 · 12 837–13 239 · 12 808–13 095 | — | — |

- **Reading:** on the desktop the search is 3–10 % of a rung (10–35 ms over 11–30 k expansions, about 1 µs each);
  the survey's pairs are mostly open water drawn in two points, where the pull is ~80 % of the rung. Twice today's
  search therefore leaves about one microsecond per expansion for the chord.

**P1 — the chord read.**
- `CellPrice` (MultipassSearch.kt) is the one per-cell price `relax` and the chord read — `relax`'s arithmetic
  unchanged; `WalkWindows.chordCostSec` walks the segment per §4.1; `StepGuard` gains `provesPiece`, `keepsWall(a, b)`
  and `endLegKeepsWall`; `ExpansionProbe` is the harness's door onto a walk's expansions. `ChordCostTest`, 12 tests:
  a row and a diagonal equal their steps' sum and the half-cell formula, open water equals today's step sum, land, a
  Blocked body and a cell no window holds refuse, a vertex touch reads nothing, a rock between two water cells
  refuses under the wall, the rim cut both ways, a route end's walled cell left but no other, the seam's fine cells
  at 20 m, the partly held block at the coarse rate, the land-copy block on the fine lattice and its coarse node on
  its own cell, and `a→b == b→a` bit for bit.
- **Deviations:** the segment is walked on the fine unit lattice everywhere (not the coarse lattice with fine
  sub-walks); an end standing on the coarse layer reads its own whole cell; a route end's own cell is exempt from
  the passability test (the raster may wall it); a chord touching an end slot reads that end's raw leg.

**The gate — failed.** Projected search = today's `SEARCH ms` + expansions × the corridor's length × the chord's
time per metre (the dearer of the clear chords' and every chord's rate, on chords between the search's own path
cells 8–64 cells apart), against 2× `SEARCH ms`:

| Pair | Around (run 1 / 2) | Best | Fast |
|---|---|---|---|
| Lérins → La Salis (9.6 km) | 27.7× / 18.3× | 13.0× / 14.0× | 12.2× / 14.5× |
| La Salis → Le Village Englouti (5.2 km) | 9.8× / 9.4× | 10.1× / 7.7× | 7.0× / 8.7× |
| the 08:38 pair (2.6 km) | 9.5× / 7.0× | 6.1× / 5.0× | 5.3× / 5.0× |
| survey, 24 pairs (sums) | 13.9× / 14.0× | 14.9× / 15.8× | 16.0× / 16.0× |

- In milliseconds: Lérins 28–32 → 359–841, Englouti 16–24 → 120–228, the 08:38 pair 10–14 → 54–108, the survey's
  sums 67–80 → 1 069–1 119.
- **Priced one chord per step** — §4.2's push, which prices `chordCostSec(parent[s], s')` for every neighbour —
  the projection is about seven times that: 0.3–5.7 s a rung. §4.1, §5 and D2 count one chord per expansion; the
  two texts disagree and P2's design must pick one.
- **The floor:** one chord per expansion from the walk's first cell, as measured, is 1.7–2.3× — but those chords are
  nearly all refused early (Lérins: 1 of 29 921 clear; the start's own leg and the land), so it prices no real chain.
- **Where the time goes:** the same path chords read without the gate cost about what the gated ones do over all
  chords (0.25–0.6 ns/m both), so D4's measured read is not the bulk; the walk is — the fine unit lattice reads two
  slot lookups for each 20 m piece of open water. A coarse step where no fine cell of the block is held would cut
  open water by up to ~5× (an estimate, not measured), still above the bar on the long pair.
- **What the projection assumes:** every expansion pays a corridor-length chord, the plan's own assumption; a
  Theta\* whose parents stay a few cells back would pay less, which only P2 measures.

**Review (the `ask` hop):** twelve findings. Fixed: a chord's passability exemption narrowed to the route's own end
cells (it covered both chord ends), an all-refused sample printed PASS (now `NO-READING`), the per-metre rate counted
clear chords only (now the dearer of clear and all), the rim test refused on land rather than the rim (rebuilt on a
rock), line widths. Open: §4.2 against §4.1/D2 (above); the fine-unit walk's cost (above); an even ratio's coarse
centre on a fine line reads only its +x/+y side (ratio 5 today); a non-45° vertex near-tie is untested; a coarse
cell and its own fine copy share a centre, so their chord is zero-length; the probe's step counter runs unguarded.

**Verification:** the unit suite 1 678 green (24 opt-in instruments skipped), `apk-build.bat` green.
`RouteDirectionSymmetryTest` under `-Dmaro.prebake=true` was not run after its survey draw moved to
`RouteSurveyPairs` (moved verbatim; the harness survey draws the same seed).

## 13. The re-design after the gate — 2026-10-29, options

**What the gate said, read again.** The chord read is not slow: 0.3–0.6 ns per metre over every chord, about 0.5 µs
per kilometre. The search is cheap: ~1 µs per expansion, 10–35 ms a rung on the desktop. The gate failed because the
plan lets every expansion pay a chord as long as the corridor (3–10 km, 3–27 µs) and counts 11–30 k of them. Two
levers therefore exist — how long a chord may be, and how many are read — and a third, how much a chord's cell costs.
The desktop-to-device ratio is unknown for the search (the device's `SEARCH ms` line exists since P0 and has not been
read); the rung ratio of the 08:38 pair is ~10×, so a desktop projection of 0.4–0.8 s is seconds on the phone.

### 13.1 The §4.1 / §4.2 contradiction, resolved

- §4.2 prices a chord at **push** (`g[parent[s]] + chordCostSec(parent[s], s')`, one read per neighbour) and again at
  **pop** (the re-parent). §4.1, §5 and D2 count one read per expansion. The push reads are the 7× between
  `projCorridorMs` and `projPushMs` in §12.
- **Resolution: the push prices nothing.** A neighbour is pushed on a **free lower bound** — `g[parent[s]] +
  haversine(parent[s], s') / paceMps × hScale`, the heuristic's own unit, which every priced cell exceeds since a
  price only adds to the open-water base — and the one chord read happens at pop, where Lazy Theta\* already
  re-parents. A pop whose true chord is dearer than its pushed bound keeps the chord cost or falls back to the grid
  parent, whichever is cheaper; the heap is optimistic rather than wrong, which is D2's accepted sub-optimality
  stated once more. One chord per expansion is then the count, and §5's gate is the right gate.

### 13.2 Options

| # | Option | What it does to the cost | What it does to the line | Verdict |
|---|---|---|---|---|
| **R1** | **A parent reach cap** — `route.search.anyAngle.reachCells` (~20–32 coarse cells): a node may take a parent no farther back than that; beyond it, it takes its grid parent and becomes the next anchor. | Every chord is at most K cells: K × 100 m × ~0.5 ns/m ≈ 1–1.6 µs per expansion at K = 20–32, about today's expansion — **projected 2–2.6× before any chord cheapening, under 2× with R2** (an estimate from §12's rates, not a measurement). | Taut within K cells; in open water the chain bends by under `atan(0.5 / K)` ≈ 1.4° every K cells, which the pull's chord test removes as it removes any collinear vertex today. The Bacon shape's legs are ~2 km, inside K = 20. | **Recommended**, with R2 |
| **R2** | **A cheaper chord**: (a) a per-walk lazy rate cache — one double per slot, `cellSec / cellM`, and one bit for "the stored bound proves it" — so a cell read on a chord is one array lookup after its first; (b) the segment walked at the coarse cell wherever a block holds no fine cell, the fine sub-walk only inside held blocks (§4.1's own wording, which P1 did not build). | (a) removes the four window reads, the zone price and the berth law per cell after the first read — most of the 0.3–0.6 ns/m; (b) cuts open-water pieces 5× at the shipped ratio. Together an estimated 3–8×. | None — the same cells, the same prices. | **Recommended**, built first and re-gated with the harness as it stands (path spans ≤ K) |
| R3 | **Re-base the bar on the rung** — accept the corridor-length chord and gate on §6's budget instead (the rung's wall time ≤ today's, the search's growth paid by the pull and the approach pass). | Projected search 0.36–0.84 s desktop on Lérins; at a ~10× device ratio, seconds — more than the pull and the approach pass together (0.6–0.9 + 1.2–2.4 s). | The plan's full taut line. | Rejected alone: the long pair does not pay back. Its *budget* stays as P3's reading. |
| R4 | **A coarser coarse cell** (150–200 m, §8) | Expansions fall with the square of the cell, chord cells with the cell: a 200 m cell is ~8× cheaper on the product. | Every line moves with the grid; the fine band and the seam unchanged. | A lever for after P4, as §8 says; not the re-design. |
| R5 | **Generalise the approach pass** — one joint two-point slide (§3 A) in place of the search change. | The approach pass's own cost, already 1.2–2.4 s a rung on the device. | Cures the 08:38 shape and nothing the next shape brings. | Rejected, as §3 rejected it. |
| R6 | **16 headings** (D5) | 2× edges. | Halves the error, removes nothing. | Rejected, as D5 decided. |
| R7 | **Chords only near features** — plain A\* where the eight neighbours are open water, Theta\* near a price or a wall. | Saves the open-water chords, which are the long ones. | The open-water vertex is still a staircase cell, so the tangent at the next feature is chosen among staircase cells — the defect of §2 again. | Rejected: R1 bounds the same cost without losing the tangent. |

### 13.3 The re-designed sequence

> **Superseded by §14** ("go the prototype way"): P1b was never run as a separate gate; the prototype carried both.

- **P1b — the chord cheapened and re-gated (half a day).** R2 (a) and (b); `ChordCostTest` unchanged and green;
  the harness's gate re-run on the three pairs and the survey with the path spans capped at K, the figure read
  against the same 2× bar. The key `route.search.anyAngle.reachCells` added to `maro.properties` beside the switch.
- **P2 — the walk, as §4.2 with two changes:** the push on the free bound (§13.1), and the reach cap (R1) — a node
  farther than K cells from `parent[s]` is pushed on its grid parent `s`. The rest of §4.2 — the pop order, the aim
  re-parented, the fallback through `forEachStep` filtered on `closed[]`, the counters — stands.
- **P3–P5** as §5; §6's rung budget read in P3, the device's `SEARCH ms` read in P4.

### 13.4 Argued against

- **A cap is a quantum of its own.** A tangent farther than K cells from the last anchor is found from an anchor that
  is a staircase cell again. K = 32 is 3.2 km on the 100 m grid — longer than any leg of the three device pairs' lines
  but one (Lérins' first leg); where it binds, the pull takes it from there, as today, one vertex instead of ten.
- **The estimate is not a measurement.** R1's 2–2.6× is §12's rates times K; P1b measures it before P2 is built, and
  the gate stands at 2×.
- **The device ratio is a guess.** Only the device's `SEARCH ms` line, now traced, turns the desktop projection into
  the phone's.

## 14. The prototype, built and measured — 2026-10-29, `#implement` on the user's word ("go the prototype way")

**What exists** (all behind `route.search.anyAngle`, default `false`; off is today's search bit for bit — the suite
and the lines say so): `ChordReader` (the chord with a per-slot rate and proof cache and a coarse block read in one
piece where no fine window touches it, counters in `ChordTally`), `MultipassSearch.walkAnyAngle` (the Lazy Theta\*:
the push through the grid's own step and gate check, naming the expander's parent where within
`route.search.anyAngle.reachCells`; the one chord read at pop, the aim's included; the grid parent's step as the
fallback), and three more keys — `.estimate` (`step` | `bound` | `full`: what a push prices a chord at) and `.reopen`
(a lowered cost reopens the closed neighbours it now reaches cheaper). The `SEARCH` line carries the chord counters,
and the harness takes `-Dmaro.harness.props="key=value;…"` and prints a `LEGS` line. The review hop's blocking find
(a dead-end node kept a finite `g`) and its simplification (the grid parent's value in place of a neighbour
re-enumeration) are folded. `AnyAngleWalkTest` (7) green: open water is one straight line, an island is rounded on
tangent cells, the cap bounds every chord, a rock the straight would graze is kept off by the gate, a pair is one
chain from either end, off is today's walk. The unit suite and `apk-build.bat` green.

**Measured**, three device pairs best of two runs, the 24-pair survey one run; reach 32 coarse cells unless noted.
Search = `SEARCH ms` against `off`; cost = the refined line's `lineCostSec` against `off`; rung = the rung's wall time.

| Setting | Search, 3 pairs | Search, survey | Line cost, 3 pairs | Line cost, survey | Rung, 3 pairs | Rung, survey |
|---|---|---|---|---|---|---|
| `step` (pessimistic push, chord at pop) | 1.26–1.88× | 1.5–2.1× | −3.8 to +0.5 % | worst pair +0.3 %, none over 5 % | 0.87–1.24× | 0.41–0.50× |
| `step` + reopen | 1.28–2.12× | 1.28–1.39× | same as `step` | worst +0.3 % | 0.91–1.13× | 0.24–0.27× |
| `bound` (straight-line push, chord at pop) | 1.4–2.3× | 1.08–1.26× | −2.3 to +1.4 % | worst +0.6 % | 0.82–1.29× | 0.24–0.27× |
| `full` (the chord at push) | 1.8–2.8× | 1.8–1.9× | −0.3 to +3.5 % | worst +0.1 % | 0.96–1.15× | 0.25–0.30× |

- **The chord read is one per expansion** (Lérins: 29 699 reads for 29 921 expansions) and its cost is no longer the
  problem: with the reach capped at 32 cells and the rates cached the search costs **1.1–2.8× today's** against
  P1's 5–28×. The cap at 16 / 32 / 64 / none moves the search 1.0–1.3× and the lines almost nothing (unbounded and
  64 give the same lines); the reach the chords actually use is a mean of 5–7 cells at a cap of 32. The first batch
  (`on0`/`on64`/`on32`/`on16`) ran on the build before the review's fixes; the table is the build after.
- **The dearer lines of the first batch (+3 to +5.5 %) were that build's, not the design's.** After the review's
  fixes and the rewrite the same `step` estimate reads about 0 % on the field and −2 % on the grid; `full` reads −4 to
  −8 % on the grid (the exact chord) and no better on the field. Which of the changes cured it was not isolated. The
  §6 bound was relaxed to 5 % on the user's word (above); no setting now needs it — the worst line of 72 is +3.5 %
  (`full`, Bacon Around).
- **Reopen buys nothing**: the grid cost is identical with and without it and the search is 15–30 % dearer. Drop it.
- **`full` is the exact answer and the dearest**: 1.8–2.8× is over the bar on the long pair, for no line gain.
- **`step` and `bound` differ on the heap's order only**: `bound` falls back more often (10 k against 6.9 k on
  Lérins) and is cheaper on the survey; `step` is the simpler and the safer in priced water (a chord is never
  credited before it is read).
- **What the walk buys, and what it does not (desktop).** The pull collapses where it was the bulk — survey 10.9 s →
  0.73–1.4 s (the survey's pairs are open water, a 150-cell staircase each), the three pairs' pull 38 → 8–14 ms
  (Lérins), 18 → 5 ms (Englouti), 25 → 15–21 ms (Bacon) — so the survey's rung is 0.24–0.5× today's. **On the three
  device pairs the rung is within about ±25 % of today's either way**: the pull's saving (13–30 ms) is spent by the
  search's growth (+15–35 ms), and **the approach pass (65–185 ms), the fine pass and the lone turns do not shrink**
  (Lérins 2 → 2–4 lone turns, Englouti 5 → 4–6, Bacon 2 → 2–3). The fine crossing re-solve was not switched off (P3's
  step). The single-run survey clocks carry a few tens of percent of noise.
- **The desktop's coarse path is not the device's** (route-harness-offline): the staircase it draws on these pairs
  is not the one that left the 43° bend at the 08:38 pass, so the harness cannot say whether the bend is gone. Only
  the device can, which is the case for the user's identical runs.

**Kept for now, removed later.** The `bound`/`full` estimates and the `reopen` path stay as measuring knobs behind
their keys (`route.search.anyAngle.estimate`, `.reopen`) until P3, which deletes them with the decision on `step`.
§12's open review notes — an even ratio's centre on a fine line, the non-45° near-tie, the coarse cell and its own
copy's zero-length chord, the probe's unguarded counter — are carried to P3 unchanged; none bites at the shipped
ratio of 5.

**What §13 left open, answered.** The §13.1 free bound (`bound`) works and is the cheapest on open water; the push
at the grid step (`step`) is as good on the line and was kept as the default estimate for its safety. R1 (the cap)
is confirmed as the lever for the search's cost and R2 (the cheap reads) took the rest; R3–R7 stand as rejected.

**The user's decision (§7 D3 and the next phase):** set `route.search.anyAngle=true` in `maro.properties`, build, and
read the three pairs on the phone — `SEARCH ms` and its chord counters, the stage clocks, `loneTurns`, the 43° bend
on the 08:38 pair — against the same build with the key off; two runs each. If the bend stands or the approach pass
does not shrink, P5 (retiring the patches) has no ground and the walk stays an option.

**Review (the `ask` hop, second):** one blocking and three should-fix, all folded (above); notes taken: the block
tally counted a still-axis piece (fixed), the passability exemption covered a chord crossing an end's walled cell
(now only a chord's own ends), the plan's §13.1/§13.3 wording (this section).

## 15. The device reading — 2026-10-29, builds A (switch off) and B (switch on), the user's own routes

Reviewed once by the `ask` hop (two blocking, eight should-fix, all folded): the first draft read the 08:38 pair's
grown walk as a fourth route, proposed a counter the pass already prints, and built P6 on a hypothesis that counter
refutes. This is the corrected reading.

**The runs.** Three pairs armed on the Pixel 7 at 24 kn on `evolutive`, the same three on each build, told apart by
their walk's `passable` count: the 08:38 pair (43.57013,7.09875 → 43.57061,7.13144 — its rung is a first walk of
16 208 cells and, on `reason=FORCED`, a grown walk of 38 691, both lines drawn and the grown one kept), La Salis →
Le Village Englouti (24 650) and Lérins → La Salis (34 839). Build A at 11:43–11:46 (three arms of the 08:38 pair,
one each of the others); build B first at 11:48 (one arm each, cold, straight after the install, discarded) and
again at 11:53–11:55 (two arms of the 08:38 pair, one each of the others, plus two other short pairs the user
armed — the warm run read below). The logs are kept outside the repo.

### 15.1 The lines

- **The 43° bend is gone.** On the grown walk build A draws, on its λ 1 rung, exactly the 08:38 shape — the zone's
  corner at 43.54447,7.15856, 2 067 m due north along the grid column, the 43° turn at 43.56306,7.15856 — in three
  arms out of three. Build B draws it on none of its six grown-walk rungs: that rung's line turns at 43.55317,7.15856
  (28°) as the other two rungs do, 195 m shorter and 12 s faster (13 557 m, 1 338 s against 13 752 m, 1 352 s). The
  plan's §1 instance is cured by the search, with no patch.
- **Every other line is within §6's 5 % — and not identical.** Across the three pairs the distances move by up to
  +0.6 % (Englouti λ 1 +64 m, Lérins λ 5 +79 m) and the durations by −5 to +6 s; the corners stand on the same
  features — the zone corner at 43.54448,7.15805, the band-edge turn at 43.5532–43.5536,7.1585 (52 m apart between
  builds) — with turns within 6° (the zone corner 43–47° → 49°). `minDepth` is unchanged; `gateEdge` reads 25 → 30 m
  and `bodyEdge` 25 → 40 m on four rungs (the three grown-walk lines and the first walk's λ 1), the closest point
  moving from the open coast to the aim's disc; `slowMetres` falls below `distance` on B's grown λ 3 and on every
  Lérins line where A read them equal — a profile change not read further here. The lone turns: Lérins 4–5 → 2–4,
  Englouti 5–7 → 6–8, the 08:38 first walk 2–4 → 3–4, its grown walk 5–6 → 6–7.
- **Why the lines agree.** The later stages are the same and they converge: the snap puts a vertex on the zone's
  corner and the approach pass slides the band-edge turn to the same point whichever chain arrives. What the search
  changes is the chain's *shape* where the staircase misled the pull — the 08:38 grown walk — not the line where the
  pull already found the tangent.

### 15.2 The cost, per rung on the phone — the `RUNG` line's wall time (`firstMs + growBuildMs + grownMs`), the
unit §6 budgets; B's warm run against A's warm arms

| Pair | Build A | Build B | Search, warm | Pull | Fine pass |
|---|---|---|---|---|---|
| 08:38 (first + grown walk) | 9.0–10.5 s | 11.6–12.0 s, then 13.8–14.5 s | 146–260 → 262–459 ms first walk (1.8×); 235–447 → 594–1 253 ms grown (2.4×) | 557 → 490 / 485 → 237 ms | 2.9 → 4.3 / 2.7 → 3.8 s |
| La Salis → Englouti | 3.5–3.8 s | 4.3–5.1 s | 212–368 → 547–588 ms (2.0×) | 504 → 159 ms | 1.9 → 2.9 s |
| Lérins → La Salis | 5.2–6.0 s | 7.4–8.1 s | 372–599 → 980–1 194 ms (2.3×) | 839 → 313 ms | 3.2 → 4.6 s |

- **The search costs 1.8–2.4× warm**, the top of the desktop's 1.3–2.4×; the pull gives back 0.1–0.5 s a rung, as on
  the desktop. The attempt is **+20 to +43 %** — a third slower, on these arms.
- **The fine pass costs 1.0–1.5 s more a rung, and that is the increase**; its approach pass reads 2.3–3.3 s against
  1.4–2.4 s. It prices about the same number of candidates (425–674 against 335–585 per rung) and — the pass's own
  `priceMarks` / `candidates`, already on its line — **the same marks per candidate** (126 against 116, 94 against
  114, 132 against 134, 134 against 136): the taut chain's longer legs do *not* make a candidate price more sea. What
  rose is the **time per read** (`priceMs` / candidate +4 to +67 %).
- **The uneliminated cause is the phone's state, not the chain.** Every clock in build B rose by a similar factor —
  the fine crossing re-solve on the identical zone box reads 1.4–1.9 s against 1.0–1.6 s; B's second arm of the 08:38
  pair is 20 % slower than its first, 40 s earlier, where A's three arms sit within 10 % — and B ran back-to-back
  after its cold run. The fair re-read is interleaved A/B arms with medians over three or more; nothing here needs a
  new counter.
- **The one lever with a reading: the fine crossing re-solve.** On the 08:38 first walk it costs 1.0–1.9 s a rung in
  both builds and splices nothing (`spliced=no reason=worse`, every rung, both builds; 0.3–0.8 s on Lérins, the same).
  §11 ordered it switched off on that reading (P3); it is about the size of the fine pass's whole increase.
- **§14's gate, read:** the bend fell (its first condition); the approach pass did not shrink and still moves 1–5
  points a rung with the lone turns unchanged (its second) — §11's premise that a taut chain idles the pass's cases
  is not borne out on these pairs, so **P5 has no counters at zero and no ground yet**; the walk is justified by the
  bend alone so far.

### 15.3 The next phase — P6, the attempt's cost

Reviewed once before `#implement` (one blocking — the re-solve's scope, now D6 — and seven should-fix, folded).

- **P6b — `route.fine.crossingResolve` (D6), the code.** `RouteFinePass.refineOriented` reads the key per call, as
  `RoutePassRunner` reads `route.search.anyAngle`, and with it `false` skips the per-zone crossing loop and prints one
  `FINE crossings=off` line, so a device log says which build ran; `solveCrossing` itself is untouched. **A coupling
  to keep in view:** the re-solve's local pulls write into the line's own refusal tally, and the sweep skips itself
  where that tally reads zero (`MultipassPull` sweep gate), so with the key off a line whose only refusals were a
  discarded re-solve's can lose its sweep and move although nothing was ever spliced. Tests: (a) key off — no `FINE
  zone` / `FINE RASTER` lines, one `crossings=off` line; (b) key on — today's output, the engine tests unchanged
  (`theCrossingRasterIsBuiltOncePerArmAndTheRungsDrawTheSameLines` pins the key on); (c) the harness's three pairs
  and survey, key off against on, every rung that moves and every desktop `FINE splice` reported.
- **P6c — the 16-cell reach, re-checked on the desktop first.** §14's "lines unmoved at 16" came from the first,
  pre-fix build, and the 08:38 shape's legs run ~2 km, longer than 16 cells: on the current build, with the key at
  16, the harness must keep every line within §6's 5 % — and the device must show **no 43° turn on the 08:38 pair's
  grown λ 1 rung** — before 16 is offered. It is a value change (`maro.properties` and the `AppConfig` default that
  follows it) and does nothing in build A.
- **P6a — the device protocol (the user's; listed under the feature's owed passes).** Two builds, identical but for
  the switch: A `route.search.anyAngle=false`, B `=true`, both with `route.fine.crossingResolve` at the value P6b's
  harness reading recommends and the reach P6c settles. The three pairs armed warm, in interleaved arms (A, B, A, B,
  A, B — the builds swapped by reinstall, one launch before the first arm of each), three arms or more per build and
  pair, in one session.
- **The bar, on lines the phone prints.** Per pair and per rung (λ), the rung's wall time is `RUNG firstMs +
  growBuildMs + grownMs`; **B's median over its arms is at most A's slowest arm**. Every line within §6's 5 % on the
  `LINE` line's `distance=` and `duration=` (the phone prints no `lineCostSec`), and no 43° turn on the 08:38 grown
  λ 1 rung (`CORNER idx= … turn=`). Arms are told apart by `SEARCH … passable=`; the readings beside the bar: `SEARCH
  ms=`, `DEVICE PASS … fineMs=`, the sum of `FINE zone=… ms=` (the re-solve, where on), and `APPROACH … candidates=
  priceMs= priceMarks=`. Met, the switch's default is the user's D3; P5 still waits on a counter reading zero.
- **Dropped from the first draft:** the straightener ahead of the approach pass — `STRAIGHTEN points=N→N` on every
  rung of both builds and the window counts unchanged, so it buys no window; and the short-leg cut — the pass already
  prices at the coarse 100 m step over 20 m marks through the pull's own grouping, and an artificial vertex is a
  window seed and a lone-turn candidate, so it adds work unless the pass is taught to skip it.

### 15.4 Argued against

- **Three pairs, one bend.** The cure is read on the one instance the plan was written for; the other two pairs
  gained nothing visible, and the lone turns moved both ways. The search earns its cost only where the staircase
  misled the pull, which the device finds and the desktop does not.
- **A third slower today, on a run that may be the phone's.** Until P6a's interleaved read the +20–43 % is a
  reading of one warm run after a cold one; the default stays off either way until the re-solve is off.
- **P5's premise failed on these pairs.** The approach pass still moves points and the lone turns stand; if P6a–c
  bring the attempt level, the walk is a bend cure behind a switch, not the retirement of the patches §11 promised.
