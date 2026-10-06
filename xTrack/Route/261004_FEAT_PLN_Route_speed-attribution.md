<!-- scope: feature -->
# 261004_FEAT_PLN_Route_speed-attribution

Topic: **the speed a route point carries** — make every emitted leg lie inside one limit regime, so the
speed the user reads is the limit in force where the point stands. One pass, `avoid` included.

Status: in design. This plan **does** change `avoid`, at the user's order of 2026-10-04.

## The defect, as verified

The clock's own design is right: [`TimedLine`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:34)
carries one made-good speed per leg, and [`splitAtLimitChanges`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:73)
inserts a vertex wherever the limit changes. Three things defeat it, and a fourth path may carry the
symptom the user saw.

- **D1 — the sampling step is a constant tied to a design that moved.** [`BOUNDARY_SAMPLE_M = 25.0`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:31)
  is documented as "half the shipped grid cell", true at the 50 m design and false for every other cell
  `avoid` now runs. A regime narrower than the step, lying between two samples, is **invisible**: the
  sample walk compares [`limitKnAt(sample)` with `prevLimit`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:98)
  and only bisects what it sees.
- **D2 — a leg's speed and its time both come from one midpoint read** ([`limitKnAt(midpoint(points[i], points[i + 1]))`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:63)),
  so a straddling leg is reported and *timed* at whichever regime its midpoint happens to sit in. That is
  the disagreement the user saw between a point and the zone it stands in.
- **D3 — the same midpoint decides the slow-time split** ([`inZone(mid)` / `inBand(mid)`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:182)),
  so a straddling leg mis-attributes zone versus band versus ramp seconds. That figure is not cosmetic: it
  is what [`zoneSlowShare`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:142),
  [`budgetMet`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:222),
  [`betterPass`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:908) and
  [`fineSpliceBetter`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:930) read.
- **D4 — three paths rebuild a line's legs outside the clock, and must be checked rather than assumed**:
  [`routePlanOf`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:280) takes a stored
  route's leg times from the points' own `timeOffsetMs` deltas; [`partialPlanOf`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:333)
  times **every** leg at the pace by design, with the KDoc saying so; and
  `TrackFromCourse.build` is the write that decides what a saved line's offsets actually encode.

## The limit function, and why the fix is sound

[`limitAtFor`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:884) answers the
strictest of the zone interior's limit and the band's own width limit, or `null` in open water — a
**piecewise constant** function whose regimes are: each zone's interior, the band's width strip, and the
open water between them. Detection by equality is therefore correct wherever it looks, and the whole
problem is *where* it looks.

## The contract, as landed

- **Every emitted leg stands inside one limit regime**, which is exactly what makes a leg's **midpoint
  read** the limit in force along it — the read the clock already made, and the reason no rule had to
  change.
- **The sampling step is derived, never a constant**: `clockSampleM(cellM, fineRatio) = min(cellM, cellM ×
  fineRatio) / 2`, floored at 1 m, so the clock samples at least twice as finely as the finest cell the
  engine walks and can see every regime the grid can produce.
- **Two rules an earlier draft carried are withdrawn, having failed on contact with the code.** A "slower
  of the two ends" rule would cap an approach leg at the *next* regime's limit and destroy the profile's
  anticipation; and at a boundary vertex the two readings straddle the edge by a fraction of a millimetre,
  so a "mixed legs" count would fire on every legitimate boundary. Neither was needed: purity comes from
  the step, not from the read.
- **The blind spot is stated, not hidden**: a regime narrower than the step that contains no sample stays
  invisible, and no finite sampling can promise otherwise.

## The changes, site by site

`RouteEta.kt`:

- **`sampleM` is a required parameter, never a default.** [`BOUNDARY_SAMPLE_M`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:31)
  is deleted outright: a fallback would re-arm the very defect this plan removes the first time a caller
  forgets it — the trap [`AvoidCell.sourceCostSec`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidGrid.kt:56)
  already records for a defaulted cost. Every call site names its own resolution, the engine's helper and
  the two clock suites alike.
- **`splitAtLimitChanges(waypoints, limitKnAt, sampleM)`** and **`splitLeg(a, b, limitKnAt, sampleM)`** —
  the hard-coded `ceil(dist / BOUNDARY_SAMPLE_M)` becomes `ceil(dist / sampleM)` with `sampleM` floored at
  1.0 m.
- **One read helper inside `splitLeg`**: `read(p) = limitKnAt(p)?.takeIf { it.isFinite() }`, used by the
  sample walk *and* by `bisectLimitChange`, so a non-finite limit can never make every sample look like a
  change.
- **`timeLineWithLimits(waypoints, paceKn, limitKnAt, sampleM)`** and the same parameter on
  `timeLineWithProfile`, added before `accelM2`, which keeps its default.
- **The leg's speed keeps its midpoint read** — `readLimit(limitKnAt, midpoint(a, b))`, capped by the pace —
  which is correct precisely because the legs come out pure. **`TimedLine` is unchanged**: no count and no
  new field.

`RouteAvoidEngine.kt` — three clock sites, one expression:

- **One helper**: `private fun clockSampleM(cellM: Double): Double = min(cellM, cellM * AppConfig.routeAvoidFineCellRatio) / 2.0`.
- **Pass it at** [`timeLineWithLimits(final, pace, limitAt)`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:608),
  [`timeLineWithLimits(line, pace, limitAt)`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:1068)
  and [`timeLineWithProfile(rounded.points, ctx.pace, ctx.limitAt, rounded.ceilingKnAt)`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:438).
- **The count never landed** (correction, 2026-10-05): this line promised the `LINE` trace beside
  [`timedLine.points`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:448) a
  `mixedLegs=${timedLine.mixedLegs}`, so a device pass could see whether the blind spot is entered — but
  the contract's *no count and no new field* won over it, `TimedLine` carries no `mixedLegs`, and the
  trace never gained one, so the blind spot stays uncounted.

Nothing else moves: [`Success`](../../app/src/main/java/ykws/android/maro/spatial/RouteResult.kt:39) already
carries `legSpeedsMps` per leg, the drawing reads it, and no proto field changes.

## The tests, as the one pass's proof

- **T1 — the narrow strip, which is the defect itself, and placed between two samples.** A north-south leg
  of 400 m over open water carrying a 10 m strip of a 4 kn zone from **181 m to 191 m**: at `sampleM = 25.0`
  the sixteen sub-steps sample on 25 m marks and **none stands in the strip**, so assert **one** leg at the
  pace — the bug reproduced; at `sampleM = 5.0` a sample lands inside it and assert **three** legs, the
  middle at 4 kn and the others at the pace. The placement is the fixture's whole point: a strip centred on
  200 m is caught even by the 25 m step, so a centred fixture would pass either way.
- **T2 and T4 are withdrawn** with the two rules above: there is no mixed-leg count to assert and no
  slower-end rule to prove.
- **T3 — the derived step, as a pure read**: `clockSampleM(50.0, 0.40)` is 10.0, `clockSampleM(50.0, 0.3333)`
  is 8.3325, `clockSampleM(100.0, 0.3333)` is 16.665 and `clockSampleM(50.0, 1.0)` is 25.0 — each one a
  number a stale constant gets wrong.
- **The regression harness is the three existing suites, run before and after**:
  [`RouteSpeedProfileTest`](../../app/src/test/java/ykws/android/maro/spatial/avoid/RouteSpeedProfileTest.kt:17),
  [`RouteZonePhase4Test`](../../app/src/test/java/ykws/android/maro/spatial/avoid/RouteZonePhase4Test.kt:205)
  — whose single-regime and two-regime cases must still yield one leg and two legs — and
  [`RouteAvoidEngineTest`](../../app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt:566).
  A finer step that inserts a spurious boundary fails this harness, which is what makes it worth running.

## What changes beyond the speed

- **The λ loop's own readings move where a leg used to straddle**: `zoneSlowShare`, `slowShares`,
  `budgetMet`, `betterPass` and `fineSpliceBetter` all read the timed legs, so a route crossing a zone
  boundary may now be priced, ranked or spliced differently. This is a behaviour change in `avoid`, it is
  what the order authorises, and it is the reason the harness above is the gate.
- **The clock costs more reads, and the shipped file is the cheaper case**: against the flat 25 m the
  derived step is 10 m at the design pair — **2.5×** the samples — and 16.7 m at the file's own `cellM=100`
  with `0.3333`, which is **1.5×**. Each sample asks `zoneLimitKnAt` and, for the band, `distanceToCoastM`;
  the clock runs per pass, on the drawn line only.
- **D4's three paths are checked, not changed, in this pass**: whether `TrackFromCourse.build` writes the
  plan's own cumulative `legTimesSec` into the points' `timeOffsetMs`; whether `partialPlanOf`'s
  pace-only legs are visible to the user during the draw, since its KDoc says they are paced on purpose;
  and that `routePlanOf` carries no speeds, so a followed route's legs are derived from the stored times
  alone.

## Phases — one pass

1. **`RouteEta`'s changes**, with T1 to T4 landed in the clock's suites.
2. **The three engine sites and the trace**, with `clockSampleM` as its own pure function.
3. **The harness re-run** and the diff read for the cases where a leg used to straddle.
4. **D4's three checks**, each answered in one line in the hydration and, where the answer is "wrong", a
   follow-up plan rather than a change here.
5. **Record** — bake and fold.

## What landed, 2026-10-04

- **`RouteEta.kt`**: `BOUNDARY_SAMPLE_M` is deleted for the caller's required `sampleM`; `MIN_BOUNDARY_SAMPLE_M
  = 1.0` is the splitter's floor; `splitAtLimitChanges` and `splitLeg` take `sampleM`; one `readLimit`
  helper reads every sample and treats a non-finite limit as no limit, in the walk and the bisect alike;
  both clocks take `sampleM`; and `clockSampleM(cellM, fineRatio)` lives here as `internal` — the clock's
  own rule, not the engine's.
- **`RouteAvoidEngine.kt`**: the three clock sites pass `clockSampleM(cellM, AppConfig.routeAvoidFineCellRatio)`,
  and the `LINE` trace carries `step=`, so a device pass can read what the clock actually sampled.
- **The Ask hop's patch, landed after the review**: the `readLimit` guard now has its **third and fourth
  readers** — [`timeLineWithLimits`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:85)'s
  leg loop and the profile's `limitMpsAt`/`ceilingMpsAt` — so a non-finite limit can no longer yield a NaN
  leg time while the splitter reads the same sample as no limit. `aNonFiniteLimitReadsAsNoLimitEverywhere`
  is its regression, and it would fail before the patch.
- **The tests**: T1 and T3 landed in [`RouteZonePhase4Test`](../../app/src/test/java/ykws/android/maro/spatial/avoid/RouteZonePhase4Test.kt:248),
  and the eight call sites in the two clock suites name `sampleM = 25.0`, so every existing assertion runs
  as a true regression rather than a silent change.
- **The harness, run twice**: the build compiles and 187 spatial tests run, the only red being
  [`theFineCellRatioShipsAtFortyPercentOfTheCoarseCell`](../../app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt:632) —
  `avoid`'s own experiment residue, red before this change and left to the user by design. The first run
  also showed one red of my own, T3's delta being tighter than its arithmetic; corrected, and green.

## Risks

- **The finer step multiplies the clock's reads.** 2.5× at the design pair, on a per-sample basis that
  includes a coastline distance query; if the device shows it, the mitigation is to ask the zone first and
  the band only where the zone answer leaves room, never to widen the step back.
- **The behaviour change is real but bounded to lines that straddled a boundary**, and it is provable by
  re-running the harness and reading which tests moved.
- **A spurious boundary would be worse than the bug**: any change that makes `read` return something new
  per sample would split every leg, which is what T2 and the harness exist to catch.
- **The blind spot cannot be closed, only narrowed and counted** — a regime narrower than the step that
  contains no sample stays invisible, and no finite sampling can promise otherwise.
- **`partialPlanOf`'s pace-only legs may be the symptom the user actually saw**, in which case this pass
  fixes the live line and the early-save line stays paced until a second decision is taken on it.

## Open questions

- Whether the early-save line should be re-timed at each iteration's landing, or left paced until the full
  line arrives — **answered 2026-10-05** by [`261005_FEAT_PLN_Route_plan-time-single-source.md`](261005_FEAT_PLN_Route_plan-time-single-source.md): the draft now carries **no time** until a plan lands.
- **Answered** (2026-10-05): `mixedLegs` never landed — the contract's *no count and no new field* won,
  so there is no count to surface and no trace to carry it.
