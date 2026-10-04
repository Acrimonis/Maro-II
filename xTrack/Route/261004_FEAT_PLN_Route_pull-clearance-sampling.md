<!-- scope: feature -->
# 261004_FEAT_PLN_Route_pull-clearance-sampling

Topic: **the pull's sampled clearance** — one read coarsened, every other guard left exactly as it is.

Status: **final** — validated against the code and reviewed on 2026-10-04, the review's eight findings folded
in; nothing built.

Placement: the work lands in the shared `multipass` layer, so **both engines** take it and only the step
travels per engine. `avoid` and `evolutive` differ by their plan — the cell they walk and the region they
re-walk — never by their pull.

## Why

The capture of 2026-10-04 puts the pull where the route's seconds now live. On the 12.6 km rung of
[`route-phase7.txt`](../../route-phase7.txt:7) the panel's `PULL` stands 2.75 s against the snap's 0.42 s
and the second pull's 0.31 s, and the pass it belongs to reads 3.62 s — so with the re-walk retired the
same day, the pull is the cost centre of the whole solve.

**The cost model, from the loop itself.** [`legClearCause`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:174)
samples a chord at `clearanceStep(marginM)` = `marginM / 2` = **12.5 m**, and the two-pointer advances its
probe **one path point at a time**, so every intermediate chord is walked in full. Over a 165-point path
that is `Σ dist(anchor → probe)` ≈ `n² / 2 × cell` ≈ 1.03 M m of chord walked, ≈ **82 000 marks**, which at
the measured 2.75 s is about **33 µs a mark** — the price of a live coastline index query. **That sum is a
ceiling, not a count**: it assumes one anchor and no refused chord, and a refusal both shortens the walk that
follows and splits the path, so the real mark count is lower — the arithmetic is what makes the diagnosis
plausible, and the pull's own trace line is what will settle it.

## The rule

**The mark list does not change. Only which marks pay the expensive read changes.**

- **The fine marks stay exactly what they are**: every `marginM / 2` along the chord, the two ends excluded,
  as [`legClearCause`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:186) places
  them today. No phase, no spacing and no count moves, which is what makes the equivalence below exact rather
  than approximate.
- **The coarse marks are new, and there are `ceil(dist / coarseStepM)` of them, placed at the midpoints of the
  equal divisions** — so every point of the chord, **its two ends included**, stands within `coarseStepM / 2`
  of one. Midpoints rather than the division boundaries because a boundary lattice tests the chord's own ends
  and would refuse chords today's walk accepts.
- **Every coarse mark pays the distance read.** A **fine mark pays it only where its nearest coarse mark read
  under `marginM + coarseStepM / 2`** — every other fine read is skipped, and nothing else about the mark is.
- **Why the skip is sound**: the distance to a set is **1-Lipschitz**, so a mark reading `d` proves every point
  within `coarseStepM / 2` of it stands at least `d − coarseStepM / 2` off the wall. A coarse mark at or above
  `marginM + coarseStepM / 2` therefore proves every point of its half-step clear, and the fine mark it covers
  would have read at least the margin — so **no verdict can move**, a chord that is pulled taut today is
  pulled taut, and a chord refused today is refused.
- **Where it is not proved, today's read happens**: near land the trigger sends exactly those fine marks back
  to the read they make today, at today's positions, in today's order.
- **In open water the walk is stronger than today, not merely equal**: the interval is *proved* clear from two
  reads where today's sampling could have stepped over a thin spike of coast between samples.
- The floor is unchanged: [`clearanceStep`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:118)
  keeps its `MIN_SAMPLE_STEP_M` clamp, and a chord shorter than the coarse step takes one coarse mark at its
  midpoint, so it behaves exactly as today with one extra read.
- The coarse step is **required, never defaulted**: this feature has already ruled on that
  ([`sampleM`](261004_FEAT_PLN_Route_speed-attribution.md:64) was made a required parameter and its constant
  deleted, because a fallback re-arms the defect the first time a caller forgets), and a defaulted coarse step
  would skip a read the caller never chose to skip.

## What must not be coarsened, and why

- **The depth gate stays tested at every mark.** Its wall answers **0 or infinity**, not a distance
  ([`depthGateSource`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:222)): a
  step, so the Lipschitz argument says nothing about it, and a coarse-only walk would sail a chord across a
  shallow patch the fine marks refuse. Validating this plan is what found it, and it is the reason the first
  cut is not "coarsen the walk".
- **The price walk keeps its density.** Every shipped price is a **step**: a zone's interior against its
  collar, the band's width against its reach, a marker's disc. A price that changes between two coarse marks
  is a refusal the guard would never see, and the guard's own verdict is a *number* — a Riemann sum — so
  coarsening it changes values and not only verdicts.
- **The two end waivers keep the fine step** as their tolerance
  ([`marginWaived`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:125)), so a
  sample near an end is exempt exactly as today.
- **The coarse step is never finer than the fine one**, so the coarse pass can only ever save reads.

## The cut — the first change, and only this

**The live coastline distance is the one read that becomes coarse.**

- The distance read is `world.distanceToCoastM` through the live index — a minimum distance to the coastline
  polylines ([`distanceToCoast`](../../app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt:391)),
  which is the fact the 1-Lipschitz bound rests on, and the reason a non-metric wall must be excluded.
- At a mark where the read is skipped, the **cheap reads happen exactly as today**: the rastered walls' block
  test (the depth gate), the end waiver, and whatever the price walk already asks. When the band is armed, the
  band's price asks the same live coastline query per mark
  ([`RoutePassPrimitives.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:69)),
  so the price half of the pull keeps a live read per mark and is untouched here.
- Everything else is unchanged by construction, so the gate, the band, the zones, the markers and the price
  walk keep today's marks, today's numbers and today's verdicts.

What this does not buy, named rather than implied: the price walk's own live reads — the price half of every
priced rung — and the mark count itself. Both are the parked work below.

## Per engine

The step is the only thing that differs, and it comes from the water the walk was resolved on — the same local
rule the tail already reads
([`RoutePassRunner.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt:64)):

- **`avoid`** — the coarse pass reads at its own cell (`route.avoid.grid.cellM`, 100 m in the asset) and its
  fine stretch at `route.avoid.fine.cellRatio` × that cell (33.3 m), so the fine water keeps a finer step.
- **`evolutive`** — the interior at `route.evolutive.grid.cellM` (100 m) and the band at
  `route.evolutive.grid.fineCellM` (20 m), read locally through the two-layer walk it already carries.
- **A single-grid walk** answers its own cell, so `avoid` stays one number per pass and no engine gets a branch
  of its own.

## The code changes, site by site

- [`RouteCostField`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:240) splits
  its **two hard reads along the discriminator that already exists** — [`Hard.rastered`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:48)
  tells the materialized coastline from the rastered gate. The cheap per-mark wall test asks the rastered
  sources' `blocked`; the coarse read is
  [`hardDistanceM`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:279) over the
  materialized ones alone, whose only caller is the pull. Without that split the gate would be skipped with
  the coastline, which is the defect the validation found.
- [`legClearCause`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:174) and
  [`legClear`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:152) take the
  coarse step as a **required** parameter, place the coarse marks, and skip the distance read at the fine
  marks their proof covers.
- [`clearanceStep`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:118) stays
  exactly what it is: the fine step and the refinement's own density, one home.
- **The corner snap is a site in its own right**: [`snapToCorners`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:168)
  asks two clearances and four priced segments per corner candidate, so it takes the same step and inherits
  the same proof — its verdicts decide where a bend lands, so it is named here rather than left implied by
  "the field it is handed".
- The step is threaded from the callers: [`runPass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt:36)
  hands the walk's own cell and its local rule, [`finePass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:40)
  and [`solveCrossing`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:90) hand
  the fine cell.
- No plan member, no property key and no new vocabulary: the step is a parameter, never a setting.
- **The pull gets its own trace line**, so its share and the price walk's are told apart — today's three
  traces (`PULL`, `SNAP`, `FINAL`) only bound it between two neighbours.

## Verification

- **The suites are green with no assertion changed** — that is the equivalence proof, not a hope: the same
  marks are visited in the same order and every read that decides a verdict still happens.
- **A counting field proves the skip**: a test-only field whose `distanceAt` counts its calls, pulled with a
  coarse step, asserts the read count falls while the returned waypoints stay identical — the one test that
  says the change is a saving and not a reroute.
- **A near-wall chord is still refused**: a chord whose coarse marks read under the trigger is walked at
  today's density and refused exactly as today.
- **A shallow patch inside one coarse interval is still refused** — the depth-gate regression the validation
  caught, and the test that must exist before any of this ships.
- **A chord whose fine marks would read under the margin is never skipped**: the bound's own case, asserted on
  a fixture where the wall lies between two fine marks.
- The device reading repeats the same pair and paces as [`route-phase7.txt`](../../route-phase7.txt:1), and the
  pull's new line answers the question the capture could only infer.

## Phases

1. **The plumbing, provably inert** — the field's two reads split, the step a required parameter, and every
   caller threading it as **today's fine step**. The exit is the suite green with no assertion changed: with
   the coarse step equal to the fine one, not a single read is skippable.
2. **The trace line, landed before the change** — so the pull's own cost and the price walk's are separable,
   and the baseline is **measured on the device** with the same arms as the capture rather than inferred from
   two trace gaps. A phase in this order because a win nobody measured before the change cannot be told from
   a reroute.
3. **The coarse marks and the skip**, with the step taken from the walk's own cell per engine, plus the four
   tests above. The exit is the suite green and the counting field's read count down.
4. **The device measurement again, and the record** — both engines on the capture's arms, and this plan's
   figures folded with the retirement's at the next bake.

## Risks

- **The trigger is the safety surface.** A wrong threshold ships a line that grazes the margin, so the trigger
  is derived from the step and the margin and never tuned, and the case where a wall lies between fine marks is
  its regression.
- **A non-metric hard source would break the bound silently.** Today's field carries two: the coastline (a
  true distance) and the depth gate (a step, handled by staying at fine density). A third must state which of
  the two shapes it is, or the walk cannot be sound — that contract belongs in the field's own KDoc.
- **The gain is smaller than the mark count suggests** where a route hugs the shore or crosses shallow water,
  since those are exactly the marks that keep today's read — and on a priced rung the price walk's own reads
  are untouched, so the win is the clearance half alone. The measurement, not the arithmetic, decides.
- **The step must never be finer than the fine step**, or the coarse pass would cost more than it saves.

## Parked

- **The price walk's reads** — coarsening them needs each soft source to declare its **distance thresholds**
  first (the band's width and reach, a zone's collar and ring offsets), so an interval can be proved to hold
  one price. Until then the price half keeps a live read per mark. Resume condition: this cut measured, and the
  price walk's share known from the new trace line.
- **The mark count itself** — the two-pointer's `n² / 2` sampled volume is untouched here. Fixed sample marks
  along a chord with a memo of the reads they already paid is the next lever, measurable once the per-read cost
  is down.

## Open questions

- The coarse step's value is **decided**: the walk's own cell, because it needs no key and already describes
  the water's own resolution. The alternative — a fixed multiple of the margin — would make both engines read
  alike at the cost of a number nobody else uses.
