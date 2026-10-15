<!-- scope: feature -->
# Route — the pull's lookback pass: removing the spurious bend

**Date:** 2026-10-14 · **Status:** in design, finalized · **Order:** the user's word of 2026-10-14 —
*"plan a loopback pass"*, then *"let's finalize plan. Arbitrate points that do not create any
regression or degrade performance. otherwise discuss."*

**Scope:** a precision fix, not a performance lever. The taut pull is a forward-only greedy that commits
a waypoint on the first refused chord and never looks back, so a vertex between two real corners can
survive that a fuller pull would remove. This plan adds the lookback the walk lacks — a
removable-vertex sweep that drops each vertex the walk's own proofs clear. It changes the drawn line,
so it sits beside, not inside,
[`261014_FEAT_PLN_Route_acquisition-perf.md`](261014_FEAT_PLN_Route_acquisition-perf.md).

## What is there today

- [`MultipassPull.pull`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:288)
  is a forward-only greedy: a clear, not-dearer chord advances the probe; a refused chord commits
  `path[probe - 1]` and re-anchors there, with one bounded re-walk. There is no lookback, and a
  committed waypoint is never reconsidered.
- Each verdict is a proof: the clearance walk proves a chord clear through the distance's 1-Lipschitz
  bound, and the price guard refuses only a chord dearer than the span it replaces, both sides read on
  the same step and grouping rule
  ([`MultipassPull.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:416),
  [`MultipassPull.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:470)).
  A kept vertex is kept because the straight chord **from the current anchor** was proven bad — never
  because it must stay.
- The defect is the gap between those two: a vertex the greedy committed on an earlier refusal sits over
  open water between two real corners, and the forward-only anchor never tests the chord that would skip
  it.
- **A clean line needs no sweep.** The artifact requires a refused chord, so a pull that refused nothing
  cannot have it — the gate below rests on exactly that.

## The change

A **fixpoint removable-vertex sweep** over the pulled line, using the walk's own tests:

- For each consecutive triple `A, B, C`, drop `B` when two proofs hold together: the chord `A → C` is
  **clear** (`legClearCause(A, C, ctx) == null`) and it is **not dearer** than the pulled line it
  replaces (`softPriceSec(A, C) ≤ softPriceSec(A, B) + softPriceSec(B, C)`, through the same walk).
- Repeat whole-line sweeps until none drops; every removal shortens the list, so the loop terminates.

Because both conditions are proofs the pull already trusts, the sweep can only drop a vertex whose chord
is proven safe: it can never move the line nearer to land than the margin, and it can never shortcut
through priced water. The fix is precision-**improving** — the line becomes genuinely taut, while every
real corner, where the chord fails, stays.

### Decided — no regression, no cost on the common path

- **One insertion point: the fine pass's settled line, before the corner pass.** The runner's final pull
  is *not* swept: the coarse line is re-refined by the fine pass, so sweeping it would pay twice for one
  answer.
- **The sweep is gated on the line's own refusals.** It runs only where that line's pull recorded at
  least one refusal (`ctx.refusals.land + ctx.refusals.price > 0`). A line the greedy pulled in one
  chord has no artifact, so the common case pays nothing at all.
- **The fixed ends are never candidates.** Only an interior `B` may drop; the raw start and aim are the
  ends the walk must reach, and the sweep never touches them.
- **The tie-break is deterministic.** A triple drops on `≤` — not dearer, not merely cheaper — and sweeps
  run left to right to a fixed point, so the same input always yields the same line.
- **It reuses the pull's own context and memo**, so the end-exemption, the field and the marks already
  paid all apply unchanged.

## Open for discussion

- **Sufficiency.** The sweep removes droppable bends; nothing yet proves the *observed* bend is one of
  them. P0 is the gate that decides it: if the pinned fixture's chord is refused, the plan stops here
  rather than growing a funnel.
- **The funnel fork.** A funnel is the classical one-pass exact algorithm and removes every spurious bend
  by construction — at the cost of rewriting the walk and re-deriving the price guard inside it. The
  sweep is chosen because it touches only the proofs the walk already holds and rewrites nothing.
- **The rare-line cost.** On the lines it runs on, a sweep costs roughly a pull-equivalent, and a
  fixpoint may be two or three sweeps. That is the one new cost in a precision plan; the instrument (the
  perf plan's P3) must price it, and if it is not noise the funnel becomes the better answer.

## Feasibility and risks

- **High** for the mechanism: the two proofs exist, are stateless, and are reused as they are.
- **The line changes.** This is a behaviour change, so its acceptance is the device pass (R97) — the same
  line, distance and clock where no vertex is removable, and a straight stretch where the bend was.
- **The re-baseline scope is wide.** The sweep moves the line, so it moves the line's distance and
  duration, the `DEVICE PASS` `pulled`/`snapped`/`coarseM` figures, the harness's `priceReads`/`marks`,
  and every fixture that pins a line, a distance or a duration — the engine seam tests and
  [`RouteSelectivePerfEvalTest`](../../app/src/test/java/ykws/android/maro/spatial/RouteSelectivePerfEvalTest.kt:45)
  among them. All are re-pinned on the record, never adjusted quietly.

## Phases

1. **P0 — pin the artifact.** A fixture whose wall or priced-water detour makes the greedy commit a
   vertex between two real corners, pinned as a failing test before the change. **And the gate:** if the
   chord that would skip it is refused, stop and discuss the funnel rather than forcing the sweep.
2. **P1 — the sweep.** Add `removable(prev, mid, next, ctx)` and the fixpoint pass, wired at the one
   insertion point, gated on the line's own refusals, the fixed ends excluded, the tie-break `≤`.
3. **P2 — re-baseline.** Re-pin every line-, distance- and duration-pinning assertion the sweep moves —
   the harness's counts, the `DEVICE PASS` figures and the engine seam fixtures — and run the scoped
   suites plus `assembleDebug`.
4. **P3 — the reading.** The device pass (R97) is the user's own: the bend is gone, and every recorded
   line with no removable vertex is byte-identical.

**Acceptance:** the spurious bend is straight between its two real corners; no chord the proofs refuse is
ever dropped; the line, distance and clock are unmoved on every recorded pair that had nothing to remove;
the common case — a line with no refusal — is untouched and pays nothing; no dependency is added; the
seam's shapes are untouched.

## Implemented

Landed 2026-10-14 by the `#implement` pipeline — Code (implement + `apk-build.bat`) → Ask (feature
coverage and code health) → Architect (this record).

- **The sweep.** [`MultipassPull.sweep`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:374)
  removes, to a fixed point, every interior vertex whose skipping chord the pull's own two proofs
  clear; it is called once, from
  [`RouteFinePass.finePass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:111)
  on the settled line, before the corner pass consumes it. Every one of the five decisions above was
  honoured as written.
- **The gate reads the arm's cumulative refusals**, not the settled pull's own — the Ask hop proved the
  two are behaviourally equivalent, since a pull with no refusal returns two points and the size guard
  fires before the gate.
- **The fixtures.** [`AvoidPullLookbackTest`](../../app/src/test/java/ykws/android/maro/spatial/multipass/AvoidPullLookbackTest.kt:1)
  pins the swept line, `[START, CORNER, AIM]` with `SUSPECT` gone and `CORNER` kept, and pins the price
  half on a chord that is dearer so the vertex is kept.
- **Gates.** `apk-build.bat` BUILD SUCCESSFUL; the whole `testDebugUnitTest` green, and no line-,
  distance- or duration-pinning assertion moved.

### Owed, named and not done

- The `sweep` KDoc and its `@param` still say *the line's own refusals* where the code reads the arm's
  cumulative tally.
- [`FEAT_DOC_Route_engines.md`](FEAT_DOC_Route_engines.md:90)'s fine-pass step names no sweep, and the
  readings section still counts "the fine pass's two pulls" while the settled tally now folds the
  sweep's reads in — as does the `FINE settled` trace, whose memo hits freeze at the pull's end, before
  the sweep runs.
- No fixture drives `finePass`'s returned line, so a reverted call site would not be caught — the
  class [`FEAT_DOC_Route_engines.md`](FEAT_DOC_Route_engines.md:205) already files as a known gap.
- [`FEAT_DOC_Route_decisions.md`](FEAT_DOC_Route_decisions.md:19) §2's *open-water bends accepted as a
  known defect* reads as the opposite of this fix; §2's own findings are the off-axis first leg and the
  shore refusals, so no decision is reversed, but the titles need one line of disambiguation.
- The sweep's rare-line cost is unmeasured, and the device pass is the user's own (R97).
