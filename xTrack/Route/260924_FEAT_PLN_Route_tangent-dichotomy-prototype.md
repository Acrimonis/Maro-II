<!-- scope: feature -->
# Route — tangent dichotomy, test-only prototype

**Created:** 2026-09-24 · **Branch:** `feature/route-avoid` · **Status:** adopted 2026-09-24

## What this is for

The corner taut pull ([`AvoidTangent`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidTangent.kt)) measured **7.3 s on the device for 191 corners** — a quadratic pair scan whose per-chord clearance walk is the cost, the same shape that killed the removed taut tracer (56 079 ms over 92 665 pairs). The user proposed a dichotomic outside-in tangent search, and this plan prototypes it **test-only** against the concave-bay and island cases before any decision to adopt it.

## The algorithm to prototype

- Cast the direct segment start → aim. If it clears at the margin, answer it and stop.
- On a hit, read the coast corner nearest the first blocked sample.
- Compute its tangent point in closed form — the interior-bisector direction at `marginM / sin(half-angle)`, the arithmetic [`AvoidTangent.addTangent`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidTangent.kt:119) already does in 1 ms for every corner.
- Choose the side whose tangent keeps progress toward the aim, jump there, and recurse.

The tangent is closed-form, so no angle sweep and no outward walk is needed; the only search left is *which* corner of the blocker to round, which is what the concave-bay and two-island cases must answer.

## The acceptance cases

1. **Concave bay** — the U-shaped open coast of [`AvoidStage1Test.aUshapedOpenCoastFillsItsWideInteriorAndTheRouteRoundsTheTipBothWays`](../../app/src/test/java/ykws/android/maro/spatial/avoid/AvoidStage1Test.kt:96): must terminate — the known risk, since "hit land" is not monotonic in angle — and round the tip, never cross it.
2. **Two islands either side of the line** — must pick a side, stay clear, and not loop.
3. **Clear open water** — must answer the straight line without searching.

## What the prototype must answer

- Termination on the concave bay within a stated iteration cap.
- Every leg stands ≥ `marginM` off land (end-discs exempt), sampled at `marginM / 2`.
- Length within a stated tolerance of the grid A* + pull line on the same fixtures.
- Timing: O(obstacles × log angle) ray-casts, each a `marginM / 2` clearance walk — projected against the 7.3 s corner pull.

## Placement

- Test-only, in `app/src/test/java/ykws/android/maro/spatial/avoid/` — never in `main/`. The inputs are a `(LatLng) -> Double` clearance function and a clear-leg predicate, so nothing here touches the shipped engine.

## Out of scope

Any change to `main/`, the shipped corner pull, any Settings row, bake or dependency.

## Prototype outcome (2026-09-24)

The test-only walk ([`TangentWalkPrototype`](../../app/src/test/java/ykws/android/maro/spatial/avoid/TangentWalkPrototype.kt)) passed all four acceptance tests:

- Concave bay: **6 leg checks**, 4 waypoints, rounds the tip, length **4422.9 m**.
- Island on the line: **6 leg checks**, 4 waypoints, picks a side, length **4871.5 m**.
- Clear water: **1 check**, the straight line.
- Length against the grid A* + pull reference on the concave bay: **walk/ref = 0.995** — the walk hugs the true corners and is 0.5 % shorter.

Against the corner pull's 7.3 s over ~37 000 checks, the walk does the same job in **6 clearance checks** on these fixtures. The one decision left: promote the walk to `main/`, or keep the corner pull capped or budgeted.

## Promotion plan — the walk into `main/`

- New `spatial/avoid/TangentWalk.kt`: the greedy walk as the shipped taut pull — inputs `start`, `aim`, `marginM`, `edges`, `openCoast`, `clearanceM`, `box`.
- Reuse [`AvoidTangent`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidTangent.kt)'s corner collection and its `SegmentIndex` bbox short-circuit, so far candidates are skipped without a full legClear.
- [`RouteAvoidEngine.searchOnce`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:122) calls the walk instead of `AvoidTangent.pull`, with [`AvoidPull.pull`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidPull.kt:28) as the fallback on `null`.
- Delete `AvoidTangent` once the walk lands; move the prototype tests into the shipped test set and add a dense-fixture, a determinism and a fallback-on-stuck test.
- Measure on the acceptance pair — wall time against the ≤ 500 ms budget, the fallback rate, and length against the grid reference.

## Review of the promotion plan

- **R1 — a stuck walk falls back to the grid pull**, so a missed route can never regress; but the fallback rate on the acceptance pair is unknown and must be measured, not assumed.
- **R2 — the efficiency reading does not transfer.** The synthetic fixtures hold 4 corners; the real corridor holds 191. The per-hop O(corners) scan stays cheap only because the bbox short-circuit rejects far candidates at one step, and that path is unmeasured on the dense corridor.
- **R3 — the walk is a heuristic.** The corner A* guaranteed the shortest clear line over its corners; the walk's concave-bay reading is 0.995, but the acceptance pair must confirm the line does not materially lengthen.
- **R4 — the side choice is arbitrary.** "Closest clear tangent to the aim" worked on two fixtures; a determinism test and the visited-set + hop cap are the guards, not a proof.
- **Strongest objection:** adopting on the sparse-fixture proof alone would swap a known 7.3 s defect for an unmeasured one — the acceptance-pair measurement is the gate, not a follow-up.

## The decision

- A — adopt the walk now, gated on the acceptance-pair measurement, grid pull as the fallback.
- B — adopt the walk with the corner pull kept only as a fallback for stuck cases (re-introduces its cost there).
- C — keep the corner pull capped or budgeted, the walk stays test-only.

## Promotion outcome (2026-09-24)

Adopted (option A): [`TangentWalk`](../../app/src/main/java/ykws/android/maro/spatial/avoid/TangentWalk.kt) now ships as the taut pull — the greedy walk over closed-form tangents with the bbox short-circuit, the direct leg answered first, and the grid-centre pull kept as the fallback. `AvoidTangent` and the test-only prototype are deleted. [`TangentWalkTest`](../../app/src/test/java/ykws/android/maro/spatial/avoid/TangentWalkTest.kt) pins clear water, the concave bay, the island, determinism, the empty-corner fallback and a 40-tooth sawtooth (4.7 ms); [`RouteAvoidEngineTest`](../../app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt) stays green, its Lérins-to-Salis-shaped corridor answering under the 500 ms wall. `gradlew :app:assembleDebug` green. The real-corridor device measurement remains the user's.

## Refinement — snap to corners (option 1, chosen 2026-09-24)

The greedy walk's output is jittery on the real coastline (the 19:19 route zigzags), so the walk is dropped as the taut pass. The grid A* keeps the order; the tangent corners supply the exact points.

- The pipeline becomes: grid A* → coarse cell path → `AvoidPull.pull` (the taut line) → move each bend onto its nearest tangent corner (within 2 × grid cell) **only when both neighbouring legs stay clear** → `AvoidPull.pull` again.
- `TangentWalk` is replaced by `TangentCorners`, which only collects the convex offset corners; the greedy walk is deleted.
- A bend on open water, or one whose corner would breach the margin (a smooth island's shallow corners), keeps its grid point — so the corner only tightens sharp headlands, never dips into the margin.

Shipped 2026-09-24: `TangentCorners.corners()` exposed; `RouteAvoidEngine.searchOnce` runs grid pull → verified corner snap → pull; the headland test still lands on the two true corners, the circle-island margin test stays green, and the new 40-tooth digitized-coast test collapses to a clean line. `gradlew :app:assembleDebug` green.

## Open points

- Whether the bbox short-circuit is folded into the walk's candidate filter first, or the walk is measured without it.
- How "which side" is chosen when the blocker offers two corners — the closest-clear-tangent heuristic, pinned by a determinism test.
- Whether the walk is promoted to `main/` or the corner pull is capped or budgeted — the decision this prototype feeds.
