<!-- scope: feature -->
# 261004_FEAT_PLN_Route_evolutive-engine

Topic: **`evolutive`** — a second real route engine beside `avoid`, deriving from the same primitives,
instrumented at every step, and holding itself to a per-point speed contract. Nothing is built.

Status: in design.

Vocabulary: the engine is **`evolutive`**; the algorithm inside it is the **adaptive grid**, designed in
[`261004_FEAT_PLN_Route_hybrid-grid.md`](261004_FEAT_PLN_Route_hybrid-grid.md).

## Why a separate engine

- **`avoid` is settled and shipped**: the three-rung ladder, the dials, the corner pass and the clock all
  ride it, and none of them is what changes here.
- **What changes is the walk itself** — its resolution two-layer, its second pass a corridor, its clock's
  boundary sampling tied to the fine cell — so the honest shape is a second algorithm, not a rework of a
  shipped one.
- **The seam was built for exactly this.** [`RouteEngineChoice.all`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngineChoice.kt:36)
  is a list, and its own KDoc says a new algorithm is "one row here plus one class that implements
  `RouteEngine`; nothing else in the feature changes".
- **So `avoid` is not touched**: not its keys, not its grid, not its 50 m design. Its only edit anywhere in
  this work is one stale comment, named under Risks.

## The three requirements

1. **Every step reports.** As much per-step data as can be shown to the user, on the seam, in one neutral
   shape.
2. **A point's speed is the zone it stands in.** The defect below is stated as a contract, not a hope.
3. **`avoid` stays as it is**, and the new engine derives from shared code rather than copying it.

## The code map — what is shared, what is derived

Shared as they stand, with no edit:

- [`AvoidGrid`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidGrid.kt:84) — one instance per
  rectangle already, since `cellM` and the cell-size pair are per instance.
- [`AvoidSearch`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidSearch.kt:30) — the D8 A\*, its
  g-cost in seconds and its `haversine / pace` bound.
- [`AvoidPull`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidPull.kt:33) — the taut pull, the
  clearance guarantee sampled at `marginM / 2`, the price guard and the concave retry.
- [`RouteCostField`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteCostField.kt:89) and its
  `baseCostSec` / `bandReachM` law.
- `BerthCarve`, `openEndDisc`, `carveReachCells`, `depthClearsGate` — the ends, the discs and the carve.
- `TangentCorners`, `snapToCorners`, `RouteCornerPass` — the corners and the racing-line curve.
- `RouteEta` — both clocks; the speed contract below changes its *sampling*, and that change is shared.
- `ZoneGeometry`, `AvoidWorld`, `EndApproaches`, `PullRefusals`.

Extracted from [`RouteAvoidEngine`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:397)
and then shared — **the one real refactor**:

- The **pass pipeline**: the context build, `runPass` (search → pull → snap → pull → clock), `finePass`,
  `fineReSearch`, the shares, `limitAtFor`, `pricedLineCost`, the keep rule.
- **Its two injected differences are the whole of the split**: a **grid provider** (a uniform rectangle for
  `avoid`, a two-layer lattice for `evolutive`) and a **second-pass region provider** (the line's bounding
  box for `avoid`, the corridor chain for `evolutive`).
- **`avoid` then becomes a configuration of the shared pipeline**, and its engine class keeps only its own
  declarations, its rung ladder and its readings — which is what makes the extraction provable rather than
  hopeful: `avoid`'s suite must stay green and its drawn line unchanged.

New, and shared by both:

- **The multi-rectangle neighbour walk**: the seam-aware expansion, the edge priced from the two cell
  centres at the pace, the diagonal and the bound re-derived per layer.
- **The one-lattice builder**: one origin and one cell-size pair, each rectangle a window on it.
- **The step-reading shape** and its emission points.
- **The boundary splitter's step**, derived from the fine cell instead of a constant.

Accepted debt, named rather than fixed:

- The primitives live under `spatial/avoid/`, which becomes a misnomer once two engines derive from them.
  A package move is churn with no behaviour behind it, so it is **not** taken here; the tests are the proof
  if it is ever wanted.

## The per-point speed contract

**Owned by another plan since 2026-10-04**: the fix lands in `avoid` first, as
[`261004_FEAT_PLN_Route_speed-attribution.md`](261004_FEAT_PLN_Route_speed-attribution.md), because the
defect is `avoid`'s too. What follows is the contract `evolutive` relies on, not a second design of it.

The defect, verified in the clock:

- [`TimedLine`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:34) already carries one
  made-good speed per leg, and [`splitAtLimitChanges`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:73)
  already inserts a vertex where the limit changes — the intent is right.
- **The sampling step is a constant**: [`BOUNDARY_SAMPLE_M = 25.0`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:31),
  documented as "half the shipped grid cell" — a tie to the 50 m design that no longer holds anywhere.
- **A change is only seen when a sample lands in another regime**, so a strip narrower than the step is
  invisible and a leg can straddle two limits.
- **The leg's speed is one midpoint read** ([`limitKnAt(midpoint(...))`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:63)),
  so a straddling leg reports whichever limit its midpoint happens to sit in — the figure the user saw
  disagree with the zone.

The contract, testable:

- **Every emitted leg lies inside one limit regime**, and its speed is the limit in force read at **both**
  ends of the leg; a leg whose ends disagree is split again rather than averaged.
- **The sampling step is derived, never a constant**: at most half the fine cell (`fineCellM / 2` = 10 m at
  the design pair), so the finest regime the grid can create is also the finest the clock can see.
- **The regime is asked once per vertex**, so a point's speed is the limit standing on that point and the
  panel, the drawn line and the saved track read the same number.
- **Where a regime is narrower than the step, the engine says so** rather than reporting a speed it cannot
  stand behind: a reading counts the legs whose ends disagreed after the second split.

## The step instrumentation

- **The readings ride the seam, not the result.** [`RouteUpdate`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngine.kt:131)
  already carries the finished and next stage; one optional, engine-neutral reading is added beside them.
- **This reverses a recorded decision, and the plan says so.** [`RouteResult.Success`](../../app/src/main/java/ykws/android/maro/data/model/RouteResult.kt:16)
  states that the two engines carrying dossiers were removed on 2026-09-22 and that no instrumentation
  field and no engine-specific vocabulary are left. The reversal is scoped: the **seam** gains a reading
  so any engine may report, while `RouteResult` and the saved proto stay clean.
- **One neutral shape, no engine vocabulary**: a stage, a label id from the `CustomSortField` pattern, a
  value with its unit, and enough structure for a table. No dossier, no per-engine type.
- **What each stage reports**:
  - **corridor** — the region's area, its box count, the band's share of it, the measured deviation.
  - **grid** — cells by layer, the lattice origin, the raster pass's duration.
  - **search** — expansions, passable cells, aim-closed, the seam crossing count and their cost.
  - **pull** — refused chords and their causes, the clearance sampled.
  - **snap** — how many corners moved, and by how much.
  - **clock** — the leg count, the regimes seen, the legs whose ends disagreed, the slow-time split.
- **One surface shows it**: the acquisition panel's step line, which exists, with a details expandable for
  the table. Data first, surface second — a reading nobody can see is not worth a phase.

## The mechanisms, pinned

The decisions a one-pass implementation needs, and the reason the answer to "are we ready" was *not yet*:

- **One lattice, each rectangle a window on it.** [`AvoidGrid`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidGrid.kt:84)
  gains nothing: every rectangle is built with the lattice's own `latSouth`/`lonWest` and cell-size pair and
  its own `rows`/`cols`, so a rectangle is a **`LatticeWindow(grid, offsetRow, offsetCol)`** — a place in
  the lattice plus a dense array. Both layers and every corridor box are such windows, which is what makes
  the seam arithmetic instead of a search.
- **The A\*'s cell id becomes the lattice id.** [`AvoidSearch.search`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidSearch.kt:65)
  sizes `g`, `h`, `cameFrom` and `closed` by `rows * cols` of one grid and indexes by `row * cols + col`, so
  the multi-rectangle walk needs a **sparse id map**: `id = latticeRow.toLong() shl 32 or latticeCol`, a
  `HashMap<Long, Int>` to the array slot, and a `LongArray` back for path reconstruction. Both are built
  once per search; the per-neighbour cost is one lookup.
- **The seam's step cost is the distance between two cell centres, and it is the only formula**:
  `stepSec(a, b) = haversine(centre(a), centre(b)) / paceMps + soft(b)`, where
  `soft(b) = window(b).grid.cell(b).sourceCostSec - window(b).grid.baseCostSec` — the destination's own
  added sources, the base being the crossing time that distance already pays. On a uniform grid this
  reproduces today's `sourceCostSec × multiplier` exactly, which is what keeps the existing suite green;
  across a seam it is simply correct, because the hop pays the ground it covers.
- **The diagonal therefore needs no special case**: `sqrt 2` was only ever the diagonal in cell units, and
  the centre distance yields it for free at any mix of cell sizes.
- **The fine band's membership is a per-cell distance read, not a dilation pass**: band water is a cell
  whose `distanceToCoastM(centre)` is within `bandWidthM + cellM`, or whose depth stands within the fine
  cell of the gate — the two reads [`rasterize`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:1045)
  already makes per cell for the margin and the gate, so the band adds no query of its own.
- **Phase 1's delegation is composition, not inheritance**: `RouteEvolutiveEngine` holds a private
  `RouteAvoidEngine` and forwards the three seam calls to it, so the row is selectable and behaves exactly
  like `avoid` from its first commit; Phase 3 replaces that field with the shared pipeline.
- **The reading's shape, minimal and neutral**: `RouteStepReading(stage, labelResId, value, unitResId)` as a
  list on [`RouteUpdate`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngine.kt:131), with one
  `route_unit_*` key per unit it prints (metres, square metres, seconds, knots, count) in both locales, and
  the stage's own label reusing the `RouteStage` ids that already exist.
- **The order between the plans is not optional**: the speed fix lands **first**, because it edits
  [`RouteEta`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:51) and three clock sites
  inside the very class Phase 3 extracts; extracting first would have the refactor and the fix editing the
  same lines.
- **The ladder is `evolutive`'s own declaration, and the proposal is the same three rungs** under the
  existing `route_rung_*` labels, since the acquisition's table is engine-agnostic. A second set of labels
  is the alternative, and it is a decision about what the user reads rather than about the code.

## What landed, 2026-10-04 — Phases 1 and 2

- **Phase 1, the seam row**: [`RouteEvolutiveEngine`](../../app/src/main/java/ykws/android/maro/spatial/RouteEvolutiveEngine.kt:1)
  holds a private `RouteAvoidEngine` and forwards the three seam calls, so the row is selectable and behaves
  exactly like `avoid` from its first commit; the row joined
  [`RouteEngineChoice.all`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngineChoice.kt:36) and
  `route_engine_evolutive` joined both locales, with the registry's own test asserting the new row builds
  the new engine.
- **Phase 2, the reading and its surface**: `RouteStepReading` and `RouteUpdate.readings` are on the seam,
  empty by default so an engine that measures nothing says so; the avoid engine emits the search's two
  counts at the PULL update and the pulled-point count at SNAP; the view model narrates them beside the
  stage word from **one** insertion in `onUpdate`, so no stage site was touched; and the panel prints them
  as one flat line in its header, the strings resolved in a plain loop because a lambda is not a composable
  context.
- **The owed patch the review found** landed in [`RouteEta.kt`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:95): the guard now covers all four readers of the limit.
- **The assertion the review also owed** landed too: `everyStageReportsItsOwnFiguresOnTheUpdate` in
  [`RouteAvoidEngineTest`](../../app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt:632)
  collects every update of an avoid lookup and pins that the update closing the search carries its two
  counts in cells and the one closing the pull carries the pulled-point count — so the panel's line has a
  proof behind it rather than a hope.
- **Phase 4 is satisfied rather than built**: the delegate means `evolutive` inherits the clock's required
  `sampleM` and the derived step with no code of its own, so the phase is a note, not a task.
- **Phase 3, the extraction — landed additively rather than by moving code**, which is a deliberate
  deviation from this plan's letter and states its reason: [`RouteGridPlan`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteGridPlan.kt:1)
  holds the **two decisions that are the whole difference between two algorithms** — the first walk's cell
  and the second pass's region — with [`UniformGridPlan`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteGridPlan.kt:53)
  carrying today's behaviour exactly, `avoid`'s engine taking a plan as a defaulted parameter
  ([`RouteAvoidEngine`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:129)),
  the plan's two helpers moved to its own file, and `evolutive` given a named plan of its own so Phase 5
  has its insertion point. **The proof is the 190-test spatial suite, one red** — every `avoid` answer
  unchanged, plus `aLookupTakesItsCellAndItsSecondPassRegionFromThePlan`, which counts the engine's
  consultations of a plan double. Moving the pipeline out of the class was rejected for a reason to keep:
  it buys the same interface at the price of migrating a shipped engine, and the interface is what the
  phase exists for.
- **The gate**: the full unit suite runs 865 tests with one red, `avoid`'s own
  `theFineCellRatioShipsAtFortyPercentOfTheCoarseCell`, which is its experiment residue and not this plan's
  to move.
- **Phase 3 is not started, and the plan says why it may stop**: the extraction moves the pass pipeline out
  of a shipped 1 500-line class and is gated on `avoid`'s answers being provably unchanged. It is the next
  run's first step, not a half-start here.

## Property and keys

- **`evolutive` owns its own namespace**: `route.evolutive.grid.cellM`, `route.evolutive.grid.fineCellM`
  and `route.evolutive.fine.corridorHalfWidthM`, all defined in the grid plan.
- **`avoid`'s keys are untouched**, `route.avoid.fine.cellRatio=0.3333` included: that file value is the
  user's own experiment in that engine and is not this plan's to settle.
- `AppConfig.routeEngineId` gains `evolutive` as a selectable value; its default does not move.
- `route_engine_evolutive` joins `res/values/strings.xml` and `res/values-fr/strings.xml` — the label is an
  id on the choice row, never a literal.
- **One comment correction in `avoid`**: [`maro.properties`](../../app/src/main/assets/maro.properties:289)
  claims the two-resolution grid ships ("Shipped: 100 m open, 25 m near") while `avoid` has one `cellM`.
  The comment is wrong about the file it sits in, and that is the only line of `avoid` this work touches.

## Verification

- **`avoid` is proved unchanged**: its suite green and its drawn line byte-identical on the fixtures, before
  and after the pipeline extraction.
- **The pipeline extraction is proved by the configuration**: `avoid` running through the shared pipeline
  must reproduce the removed engine's answers on the existing tests.
- **The speed contract is proved per leg**: for every emitted leg, `limitKnAt` at both ends agrees — a
  property test over zone fixtures, including one zone narrower than the sampling step.
- **The readings are proved by shape**: every stage emits a reading, and the set is identical between a
  `dummy`-style silent engine and `evolutive` except for presence.
- **The seam is proved by the clock**: a seam-crossing path's g equals its own timed cost.

## Phases

1. **The seam row, silent** — `evolutive` as a third `RouteEngineChoice` row, a class that answers the
   declarations and delegates to `avoid`'s pipeline unchanged, and the two locale strings. It is selectable,
   it answers, and it changes nothing else.
2. **The reading shape, on the seam** — the neutral step reading on `RouteUpdate`, emitted by the existing
   pipeline's stages, shown on the acquisition panel's step line, with `avoid` left silent.
3. **The pipeline extraction** — the shared pipeline with its grid and region providers, `avoid` moved onto
   it, and its suite and drawn line proved unchanged.
4. **The speed contract — inherited, not built here.** It lands in `avoid` first, in
   [`261004_FEAT_PLN_Route_speed-attribution.md`](261004_FEAT_PLN_Route_speed-attribution.md); this plan
   takes it as given and only supplies its own `sampleM` at the clock sites.
5. **The adaptive grid** — the grid plan's Phases 3–6, in order: the corridor chain alone, then the two
   layers, the seam helper, and the local cell.
6. **The readings grow** — the per-stage content above, filled in as each stage lands, never before.
7. **The device pass** — the user's: the open-water cost, the deviation, and the legs' speeds against the
   zones on a real route.
8. **Record** — bake, fold, and settle the epic's `## Implemented`.

## Risks

- **The extraction is the riskiest step and it touches a shipped engine.** It is sequenced after the seam
  row and gated on `avoid`'s answer being unchanged; if the gate cannot be written, the extraction stops.
- **Instrumentation reverses a recorded decision**, so the reversal has to be scoped or it will creep back
  into `RouteResult` and the proto. The seam is the boundary; the result type stays neutral.
- **A reading is a promise to the user.** Every figure shown has to be one the engine stands behind, which
  is why the straddle count is reported rather than hidden.
- **The speed contract assumes the limit function can be asked at a point cheaply.** Every vertex asks it
  once; if that read is expensive, the clock pays it per vertex rather than per leg.
- **A narrower sampling step costs more reads**: `fineCellM / 2` = 10 m against today's 25 m is 2.5× the
  samples per leg, on the drawn line only.
- **Two engines mean two ladders, two key families and two test suites.** The shared pipeline is what keeps
  that from becoming two codebases, and it is the reason the extraction is not optional.
- **`avoid`'s props still describe a grid it does not have**: that one comment, and the user's own
  experiment values, remain until they are settled.

## The seam's own ceiling, found by the Phase 3 review

- **It carries one cell and one region — the corridor's shape, not the lattice's.**
  [`secondPassRegion`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteGridPlan.kt:43) answers a
  single `BBox` and `firstWalkCellM` a single size, so a chain of boxes, or a fine band beside a coarse
  interior, cannot be expressed through them.
- **So Phase 5's first step is to widen this seam**, from a cell to a grid **provider**: the plan hands back
  the rectangle, or the rectangles, the walk may use, at the sizes it chooses. That is one small edit while
  the interface is new and has a single real implementation — and the corridor still lands through the
  region member as it stands.

## Open questions

- Whether the readings are always emitted or only when a dev switch is on: the phase assumes always, with
  the surface deciding what to show.
- Whether the speed contract's both-ends rule should also split at a *pace* change, since the pace is the
  cap rather than a zone.
- Whether the extraction's home is the existing `spatial/avoid/` package or a neutral one, given the
  misnomer above.
