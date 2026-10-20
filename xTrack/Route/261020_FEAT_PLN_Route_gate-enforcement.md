<!-- scope: feature -->
# Plan — the gate's two promises, enforced end to end

**Status:** implemented 2026-10-20 (device pass owed) · **Feature:** Route · **Created:** 2026-10-20

## The order

> Ensure routes enforce the min distance + extra weight/cost when there is room.

Two promises, read off the shipped gate ([`261017_FEAT_PLN_Route_gate-standoff-sampling.md`](261017_FEAT_PLN_Route_gate-standoff-sampling.md)):

- **The minimum distance** — no drawn line comes within the wall of water under 3 m (10 m on `evolutive`, 20 m on `avoid`), except within 20 m of its own ends or on their carved approach.
- **The extra weight when there is room** — on `evolutive` the water between the wall and 20 m is dear (the inner ramp) and the water from 20 m to 50 m is lightly priced (the outer ramp), so wherever a line can stand further off at little cost, it does.

## Audit — every stage that moves or adds a point

| Stage | Wall (min distance) | Weight (when there is room) |
|---|---|---|
| Raster | paints cells under the wall from the measured edge | writes each cell's distance; the search prices it |
| Search (A\*) | `StepGuard` on every improving step — **except a step out of the start's cell or into the aim's, and wholesale for a step with either centre waived** (G5) | `BerthLaw.priceSecAt` on the destination cell |
| Pull | `gateRefuses` on its 20 m / 2 m lattice — but **a refused immediate step is kept**, so the raw legs are only as legal as the search made them (G5) | total-time price guard, strict |
| Corner snap | `legClear` on both legs | price only, strict |
| Fine pass: re-tension | as the pull | as the pull |
| Fine pass: crossing re-solve | step guard in the local search (no end waiver) | the splice is accepted on soft price alone over unequal spans; speed zones ship disabled, so it never runs by default (adjacent, not acted on) |
| **Sweep** | `legClearCause` | **the allowance multiplies the legs' total time** (G6) |
| Straightener | `chordLegal` | total time, strict |
| Medial nudge | `legClearCause` on every new chord | total time, strict |
| **Corner pass** | **bow points and midpoints only** (G1); **the merged corner's crossing never measured** (G2); **the chord left by a passed flat kink never measured** (G3); **a waived corner's closeness handed to bow points outside the waiver** (G4) | none — geometry |
| Clock | its split points lie on the existing legs | — |

## Gaps

- **G1 — the bow is sampled.** `firstBlocker` reads the bow's points and their midpoints. Against a contour corner the miss is centimetres (a 0.75 m half-gap against a 10–20 m circle), but it breaks the recorded rule *the depth gate's standoff is measured, never sampled*.
- **G2 — the merged corner is never measured (blocker).** `compoundLine` replaces two close same-way bends by the crossing of their outer legs. Refused, the crossing is drawn with two legs nothing checked; drawn, its bow's floor is read at the crossing, so a crossing 3 m off the edge licenses a 3 m bow.
- **G3 — the chord a passed flat kink leaves.** A bow's room is measured past kinks under 10°, and the kinks it passes are taken out before it goes in, leaving a chord from the last kept point to the bow's first point that follows no checked leg — up to about `tangent × sin 10°` (≈ 9 m on a 50 m overrun) off the old line.
- **G4 — a waived corner's floor leaks.** The bow's floor is `min(preferred, the corner's own distance)`; a corner inside the 20 m end waiver standing 4 m off hands 4 m to bow points outside it, under the 10 m wall.
- **G5 — the ends' first step is unguarded past the 20 m waiver.** The search skips the guard on a step out of the start's cell or into the aim's, and waives any step with a waived centre wholesale; the pull then keeps a refused immediate step. On `evolutive`'s 20 m cells that is up to ~22 m of one step beyond the waiver; on `avoid`'s 100 m cells the raw end → cell centre leg (≤ 70 m) and the centre → neighbour step (≤ 141 m) together.
- **G6 — the sweep's allowance trades the weight for travel.** It drops a vertex while `chordSec ≤ pathSec × (1 + share)`; since the total-time guard, `pathSec` is travel plus price, so a chord up to 10 % slower overall — one cutting into the inner ramp — drops the vertex holding the line off it. Basing it on the legs' price alone is not enough: at λ 5, 15 m off, the berth's 1.5 s/m dwarfs travel (0.39 s/m at 5 kn), so 10 % of that price is still most of today's allowance.
- **G7 — nothing pins the promises on a whole route.** Each guard has its own suite; no test runs the engine and reads both promises off the drawn line.

## The fix

- **F0 — one waiver-aware segment read.** `GateRule.keepsWall(a, b, start, aim, approaches)`: the segment keeps `hardM` everywhere outside the waiver. A segment with neither end waived is one `segmentEdgeM`; otherwise it is cut at `GATE_FINE_STEP_M` (2 m), the waived pieces dropped and each run of unwaived pieces read as one segment — the pull's own per-mark granularity, so lenient by at most one 2 m piece at the waiver's rim.
- **F1 (G1)** — the bow's gate test reads **segments** between consecutive bow points (`segmentEdgeM` against the segment's floor) in place of the midpoint point reads; the coast and zone tests keep their midpoints.
- **F2 (G2)** — `compoundLine` merges a pair only when the two extensions it draws (the first bend → crossing, crossing → the second bend) pass the bow's own tests at the **pair's** floor: the gate at `min(preferred, edge at either bend)` (F4's waiver rule applied), the coast at `min(margin, coast at either bend)`, and no zone the bends stand outside. Refused, the pair is rounded as two bends exactly as before the merge existed.
- **F3 (G3)** — `firstBlocker` also tests the **lead chord** where the bow would pass kinks: the drawn line so far is handed to the attempt, the point the removal would keep is found exactly as the removal finds it, and the chord from it to the bow's first point takes the bow's own tests. The bisection then shrinks the bow until the chord clears or the kinks are no longer passed.
- **F4 (G4)** — the bow's floor at a point is the corner's own (`min(preferred, edge at the corner)`) where the point is waived and **at least the wall** where it is not; a segment takes the lesser of its two ends' floors. `round` gains the approaches (the line's own ends are its start and aim).
- **F5 (G5)** — the search guards the ends' own steps with F0:
  - a step out of the start's cell checks the drawn legs it implies — raw start → its cell centre → the neighbour's centre — and a step into the aim's cell the mirror; a step with a waived centre is read through F0 rather than waived wholesale;
  - **a boxed end keeps today's waiver**: before the search, each end's exits are tested; an end with no exit that keeps the wall walks as today, so an end the shallows enclose still finds its way out;
  - **a strict search that finds nothing is re-run with both ends waived**, so the change can never turn a route into no route; the re-run is paid only on a failure, and the outcome says which ends walked strict.
- **F6 (G6)** — the sweep's triple and window drop a vertex while `chordSec ≤ pathSec + share × pathFieldSec`, where `pathFieldSec` is the legs' **field** price — the soft price without the berth's term and without travel. The allowance read is lazy: a chord no dearer in total drops without it. A fixture without pace or berth reads as today; the berth is then compared strictly, traded only for real total-time savings.
- **F7 (G7)** — `GateEnforcementEngineTest`, the engine over a raster world with a straight shelf, a band above 0 so `evolutive`'s depth collar builds its fine windows, and a pair 15 m off the shelf 1.8 km apart:
  - `evolutive`, every rung: every segment beyond 60 m of either end keeps the wall (`segmentEdgeM ≥ hardM`), the line's middle third stands at least 30 m off (the outer ramp less one fine cell), and the engine's own `LINE` trace reads `bodyEdgeIdx ≥ hardM`;
  - `avoid`: every segment beyond 150 m of either end keeps its 20 m wall.

## Non-regression and performance

- **F0 / F5** — only steps touching an end's cell or a waived centre read anything new: an end has 8–16 exits, a 2 m-cut read is a few segment reads, so a search pays tens of reads more. The re-run is paid only where the strict search fails.
- **F1** — a segment read replaces a midpoint read one for one.
- **F2 / F3** — read only on merged pairs and on bows that pass a kink; F3's chord is read once per bisection step.
- **F6** — open water reads as today (a chord is never longer than its legs); a line where the berth binds may keep a vertex the old allowance dropped, which item 3's smoothing plan takes up.
- **Risk:** F5 is the one change that can alter where a route leaves its end; the boxed check and the re-run keep it from ever failing a route that finds one today.

## Tests

- `GateCornerTest` (new): F2 — a merge whose crossing stands in the wall is refused and every drawn segment keeps the floor; F3 — a bow passing a kink whose chord would cut a shoal is shrunk or refused; F4 — a corner 15 m from the start 4 m off the edge draws no bow point beyond 20 m under the wall; F1 — every bow segment near a rock keeps its floor.
- `GateEndStepTest` (new): F5 — a start whose cheapest exit skims a rock past 20 m leaves by another; a start the shallows box in still finds a path; a strict failure re-runs waived.
- `GateSweepAllowanceTest` (new): F6 — a paced, berth-priced sweep keeps the vertex whose chord cuts the inner ramp and is under 10 % slower overall, and drops one whose chord is clear and no dearer; an un-paced fixture without berth drops as before.
- `GateEnforcementEngineTest` (new): F7.

## Docs

- `FEAT_DOC_Route_decisions.md` §8 — *the sweep's own allowance [is] untouched* is amended: the allowance is a share of the legs' field price.
- `maro.properties` — the `route.avoid.pull.allowanceShare` comment and tldr follow F6.
- `FEAT_DOC_Route_engines.md` — the corner pass's merge and the ends' strict exits.

## Adjacent, not acted on

- The crossing re-solve's splice compares soft price over unequal spans; it runs only with speed zones on.
- `evolutive`'s depth collar exists only while the 300 m band is on (`RouteGridPlan.fineWater`): with the band off, no fine layer follows the shallows.

## Key files

- `app/src/main/java/ykws/android/maro/spatial/multipass/ShallowEdgeIndex.kt` — `GateRule.keepsWall`
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteCornerPass.kt` — `compoundLine`, `attemptCorner`
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassSearch.kt` — `searchWalk`, `StepGuard`
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt` — `sweep`, `longestWindowChord`
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — the corner call

## Implemented

**2026-10-20, on `feature/route-low-depth-check`, uncommitted.** F0–F7 shipped as planned. One implementation review and one re-review ran: every *should* finding was fixed, and the nits are fixed or listed below. The full unit suite passes (1392 tests, 0 failures, 12 skipped as before) and `apk-build.bat` packages; the device pass is the user's.

- **The wall, end to end.**
  - `GateRule.keepsWall` reads a straight past the waiver.
  - The search holds each end's own first step to the wall:
    - an end the shallows box in walks under the old waiver, the other end still held;
    - a held walk that finds nothing is re-walked waived only where it refused a waivable step into water it never reached;
    - the `LINE` trace says `strictStart`/`strictAim`.
  - The corner pass measures:
    - every bow straight;
    - the merged pair's crossing and extensions;
    - the chords joining a bow past the kinks it passes on **either** side — a passed kink ahead is no longer drawn behind the bow, which removes the old fold-back;
    - the waiver rim on a joining straight.
  - A bow point outside the waiver never takes a waived corner's floor under the wall, and a joining chord is held to the lesser of its corner's floor and its fixed end's own reading.
- **The weight.** The sweep's allowance is a share of the legs' **field** price (`PullContext.fieldOnly`, read only when the strict test fails and the field prices anything). The berth is now traded only for a truly cheaper total.
- **Code health.** `chordPointAt` is the one interpolation home: the pull's and the clock's private copies are gone.
- **Tests.**
  - Four new suites: `GateCornerTest` 4, `GateEndStepTest` 6, `GateSweepAllowanceTest` 2, `GateEnforcementEngineTest` 2.
  - Each wall test reads the result back on 1 m pieces, independently of the code under test.
  - All four corner cases and the end-exit case fail on the committed corner pass and search, and pass now.
  - End to end, every `evolutive` rung's middle stands ~52 m off the shelf.
- **Docs.** `maro.properties` and `AppConfig` (the allowance), `FEAT_DOC_Route_decisions.md` §8 (amended), `FEAT_DOC_Route_engines.md` (step 10, the gate's waiver).

**Still open:**
- G1's own gap is centimetres against a contour corner, so no test can fail on it; F1 stands for the recorded rule's sake.
- The aim's boxed check reads its exits as entries, approximate across the seam's one-way crossings; the waived re-walk covers it.
- With `route.turn.reachFraction=1.0` a bow passing a kink near the line's end can leave a short, measured hook to the end.
- The device pass: `LINE` `strictStart`/`strictAim`, `bodyEdgeIdx`, and the `SWEEP` keeps where the berth binds.
