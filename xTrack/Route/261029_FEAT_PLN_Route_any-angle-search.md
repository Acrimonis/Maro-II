# Route — the any-angle coarse search

**Status:** designed and reviewed (§10), decisions taken 2026-10-29 (§7) — ready for `#implement` from P0.
Nothing implemented. Branch `feature/roouttinggg`.

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

- **No line dearer beyond the crown's own tolerance.** For every fixture and survey pair, the settled line's
  `lineCostSec` (the pull's `totalSec` clock, `RouteFinePass.kt:42-50`) is ≤ the baseline's +
  `route.avoid.ladder.tieToleranceSec` (30 s): the field re-prices at 25 m, Theta\* is not optimal through priced
  water and the greedy pull walks a different chain, so a few seconds either way is legitimate; a line over the
  tolerance is a failure. Every line's delta is reported, and the crown the user sees is R98's weighted slow
  seconds plus trip, read beside it.
- **No wall crossed, no gate breached.** Every `LINE` keeps `minDepth ≥ gate`, `gateEdge ≥ hardM`, `bodyEdge`
  as today; `AvoidDepthGateTest`, `GateGuardsTest`, `GateCornerTest`, `GateEndStepTest`, `RouteMarkerCostTest`,
  `AvoidMarkerCostTest`, `AvoidBandCostTest`, `BerthCostTest` green under both settings.
- **Symmetry.** `RouteDirectionSymmetryTest` (`-Dmaro.prebake=true`): zero plan-pair runs over the bar, the
  line gap ≤ today's 0.4 m.
- **Performance budget.** The rung's wall time on the harness pairs ≤ the baseline's; the target is lower, the
  search's own increase paid back by the pull, the fine pulls and the approach pass. `expansions` and
  `chordCells` are readings beside it, not bounds.
- **Switch off = today.** The whole suite green with the key `false`; the engine fixtures' recorded lines
  unmoved there.

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
  deleted with the retired cases.
  Objection: the harness does not reproduce the device's coarse path, so P4 is the only proof that counts.
- **D4 — the gate on a chord: stored bound per cell plus a measured read over the unproved sub-segments
  (recommended), or a measured read on every chord.** The step guard already does the former.
  Objection: near the coast many cells cannot prove, and a measured read is the pull's dear read; P1's gate
  says whether it binds.
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
  number per line (cost, within 30 s) and three bounds (depth, edge, symmetry).
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
| R8 | **Should fix.** "No line dearer, a failure" is unachievable: the field re-prices, Theta\* is not optimal, the pull walks another chain. | §6: `lineCostSec` within `tieToleranceSec`; every delta reported; R98's crown named beside it. |
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
