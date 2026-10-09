<!-- scope: feature -->
# Route — the walk context at the seam: one value for the water

**Date:** 2026-10-05 · **Status:** **all three phases landed 2026-10-05** — `apk-build.bat` green after each, the suite triple identical to the baseline 923 / 1 / 11 with the parked `route.avoid.fine.cellRatio` test the only red, and the counting fixture still 270 against the memo-less 410. Two should-fixes open, both in §Landed.
**Order:** the user's word of 2026-10-05 — *"plan it as a following step"*, then *"scale it back or modelize it
differently"*, then *"cleanup the plan of history, keep findings, then review again"*. The shape below is the
answer to all three, with every finding of its three reviews folded in as a constraint.
**Placement:** Phase 2 of [`261005_FEAT_PLN_Route_walk-context.md`](261005_FEAT_PLN_Route_walk-context.md), split
out and planned on its own. That plan's Phase 1 landed a context **inside**
[`MultipassPull`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt), carrying the
water, the sampling **and the walk's three tallies**.

## Why

- **Seven call sites re-list the same values.** [`RoutePassRunner`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt:114)
  starts two walks per pass, [`RouteFinePass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:87)
  four, and [`pricedLineCost()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:30)
  prices a line through the same walk — each spelling out the field, the margin, the two steps and the two ends.
- **The corner snap is the same seam**, taking the same bundle from **three** callers
  ([`snapToCorners()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:176)).
- **Two adapters exist only to bridge the bundle** — `legClear` and `softPriceSec`'s own entry point — the second
  feeding a caller that has no ends of its own. Deleting them is what pays for this step.
- **The water is per call and the walk is per walk.** The field is built afresh in each call that walks; the tallies
  belong to the walk. A value holding both would have to be re-made per walk, which no type can enforce.
- **Nothing visible is at stake** — the line, the readings and the counts must be identical.

## The design

- **`PullSetup` — the water, as the callers' value.** It holds `marginM`, `coarseStepM`, `priceStepM`, `field`,
  `start`, `aim` and `approaches`, and it **is the landed `PullContext` cut back to those seven**: the walk's three
  tallies leave it, and `refusals`, `timing` and `memo` return to being the walk's own arguments. **Two constructs
  coexist, one lifetime each**: `PullSetup` per call, and the walk's own context — Phase 1's, unchanged — built
  **inside** `pull` from the setup and the three tallies, so the internal threading the landed phase put in place
  is not opened again. The tally that carries the risk is the memo's: made inside the walk, per walk, and **no call
  at the seam passes one**.
- **Built in the four calls that hand a field to a walk-side consumer** — `runPass`,
  [`finePass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:48), `solveCrossing`
  and `fineReSearch` (which builds a field for `pricedLineCost` alone) — each **from the context that call was
  handed**, immediately after its own field, never from a cached one.
- **`pull(setup, path, refusals, timing, memo)`** — the seven constants collapse to one argument, `memo = null`
  still walks as before for the fixtures' memo-less baseline, and nothing at the seam ever passes a `memo`.
- **The corner snap and the price walk take it too**: `snapToCorners(setup, sets, …)` with `sets` staying explicit
  — it is geometry, not water — and `pricedLineCost(setup, points)`.
- **The two adapters are deleted, not renamed.**

## Phases

Each phase changes one signature **and every caller of it**, so each ends on a green tree — the order the plan
failed on before is the reason they are cut this way.

1. **`pull` and its three walking callers.** `PullSetup` declared and built in `runPass`, `finePass` and
   `solveCrossing`; `pull` taking it; the six walking call sites passing one value; the tests that call `pull`
   moved with it. Exit: the suite compiles, runs and its completed/failed/skipped triple is **identical** to the
   baseline, with the same `priceReads` on the fixtures that assert it and the parked
   `route.avoid.fine.cellRatio` test the only red; `apk-build.bat` green.
2. **The corner snap.** `snapToCorners` takes the value and its **three** callers follow, tests included. Exit: the
   same snapped line point for point on the engine-level suites — no fixture calls `snapToCorners` directly, so the
   drawn line is its evidence — and the same `priceReads`.
3. **The price walk's own entry.** `pricedLineCost` takes the value, `fineReSearch` builds its setup, and the two
   adapters (`legClear`, `softPriceSec`'s entry) are deleted. Exit: the suite's triple still identical, the same
   `priceReads`, and a search of the package showing neither adapter exists.

## Verification

- **`priceReads` is the measure that can be asserted** — the same count on the same fixtures after each phase. `ms`,
  `clearMs` and `priceMs` are wall-clock and are **read, not asserted**: no test drives `runPass` yet, so no exit
  may rest on them.
- **The drawn line is unmoved** on every fixture that reaches the corner snap or the price walk.
- **The setup is built in exactly four places**, each immediately after its own field, and stored in no field of an
  engine, runner or seat.
- **The memo stays inside the walk, per walk** — made inside `pull`, never in the setup, with **no call at the seam
  passing one**; the shape keeps `pull`'s optional parameter, so this is a convention held at the seam rather than
  a type. **Its blind spot is named**: the only fixture that could catch a memo shared across a pass's two pulls is
  the still-owed test that drives `runPass`, since the counting fixture asserts a single `pull` against
  `memo = null` and would pass either way.
- **The suite's triple is identical after every phase**, and `apk-build.bat` green.

## Findings this plan carries

Each was found by a review of this plan or of its validation, and each is a constraint rather than a step.

- **"Four sites", walk-side, and the count is not "every field build".** `costField(` runs **ten** times across the
  package — the grid's rasterize field and the probe's copies among them — and the tests build **23**
  [`RouteCostField`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:260) instances
  directly. The four are the ones that hand a field to a **walk-side consumer**, and each phase names the one it
  takes.
- **The middle site is `finePass`, not `fineReSearch`** — the latter sets no steps of its own, which is why it is
  Phase 3's site and not Phase 1's.
- **The context is rebuilt when the corridor grows**
  ([`RouteAvoidEngine.kt:302`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:302),
  [`:308`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:308)), so a setup is built from the
  context the call was handed and never from a cached one.
- **A field's answers are not proven stable for its life, and that is this plan's condition.**
  [`costField`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:29) closes over
  [`LiveMultipassWorld`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt:86), whose
  reads reach a plain `var` [`DepthRepository.grid`](../../app/src/main/java/ykws/android/maro/data/depth/DepthRepository.kt:39)
  and a rebuildable coastline index, while the memo wipes on **identity alone**
  ([`MarkMemo.bind()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:165)).
  **Pre-existing**, not created here; the two exits are small — state the world's mutation window (loaded, then
  frozen for a solve) or snapshot the world reads the field closes over — and they are carried in the feature's
  todos rather than taken here.
- **The tallies stay out of the type, and that is a rule, not a preference.** `refusals` is the arm's tally, shared
  by two pulls of one pass; `timing` and `memo` are one per walk. They keep their own lifetimes and nullability
  because **a counter shared between walks cannot change an answer, while a cache can** — the memo being the cache
  in question.
- **The approach lists are immutable in fact, not by type** — built with `listOf` and never appended
  ([`EndApproaches`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:649)) — so a setup
  takes a copy if the lists could ever be touched by another owner.
- **`pricedLineCost` reads a subset** (`marginM`, `priceStepM`, `field`), so the setup arrives with four members
  unused: a fact of the pass's own value, **not** a placeholder invented for a member the callee does not read. Of
  its two callers one is a **twin** and the other is not:
  [`RouteFinePass.kt:258`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:258) builds
  a field of its own for the comparison, while
  [`:191`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:191) hands it the very field
  its neighbouring pulls at
  [`:183`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:183) and
  [`:187`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:187) used. The twin is
  harmless exactly because that path walks with **no memo**: a memo would wipe on the twin's identity and lose its
  hits rather than answer wrongly.
- **The corner snap's parameter list, before and after this step** — before, it took nine, seven of them the bundle:
  `path` and `sets` beside the margin, the two steps, the field, the two ends and the approaches, with `approaches`
  inside the bundle and `sets` not. **After the step it takes three** — the setup, `sets` and `path` — which is what
  the landed code shows.
- **Citations in this plan are functions first, lines second.** Line numbers were copied from reviews taken at
  different moments of the same day and drift with every edit; where a line and a function disagree, the function
  is what to open.

## Alternatives considered and rejected

- **The stateful walk object** (`walk(setup).pull(path)`, the walk's state in fields): the shortest call sites, and
  rejected because it *enables* the one mistake worth preventing — a walk object kept and reused, pricing a second
  walk from the first walk's water — and because construction cost then hides behind an object.
- **The full caller-built context** (the tallies inside, built per walk by the caller): it carried the memo and the
  field across a rebuild unless a rule was kept by hand, so the guarantee would rest on a fixture rather than on a
  type.
- **Doing nothing** — legitimate, since Phase 1 already removed the internal threading; the cost is that seven call
  sites keep re-listing seven values, and the type is cheap because the water is already built per call.

## Risks

- **The shape is a rename of a bundle rather than a new model** — a real but modest win, and five test files churn
  for it. The case for taking it is that the seam is where a third engine or another pass will be added, and there
  the type prevents a wrong argument.
- **`PullSetup` is a magnet** — its membership stays exactly the water and its sampling; `sets`, the tallies and
  anything per-chord stay out.
- **It lands on top of an unread pass** — the mark count's Phase 3 reading is owed, and the reading would report one
  shape while the recorded capture came from the other; `priceReads` and the drawn line keep that honest.

## Open questions

- **The name** — `PullSetup` (what a call sets up once) against `PullWater`, which names the content better and the
  field worse; the landed type's name is `PullContext` today, so a rename happens here either way.
- **Whether `timing` should be returned rather than passed**, now that the argument list is short enough to see;
  returning it would make "one per walk" explicit. It is a smaller change than this one and can follow it.
- **Whether the corner snap ships inside Phase 1 or stays Phase 2** — it is the smaller half and can ship after the
  walking callers.

## Parked / out of scope

- The Phase-1 review's first should-fix — the adapters' placeholder fields — is **closed by deletion here**; the
  second, `MarkMemo.price` being test-only, is not this step's and stays with the walk-context plan.
- The two exits for the field-stability condition, carried in the feature's todos.
- Any behaviour change, any new key, any new dependency, any device pass.
- The mark count's Phase 3 reading, which stays the one device pass the feature keeps.
- The parent plan's own preference for keeping `pull`'s parameters, which it has amended to defer to this one.

## Landed 2026-10-05 — all three phases

**Phases 1, 2 and 3 landed** on the user's `#implement` order: [`PullSetup`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:193)
is the landed context cut back to the water, built in the **four** calls that hand a field to a walk-side consumer,
with `pull(setup, path, refusals, timing, memo)`, [`snapToCorners(setup, sets, path)`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:179)
and [`pricedLineCost(setup, points)`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:33);
**both bridging adapters are deleted** — the only `legClear` and `softPriceSec` forms take a context — and 21 `pull`
call sites across five fixtures moved onto the value.

**The review hop's verdict — no blocker, two should-fixes and one doc correction.** All five design constraints were
verified on the tree and all four declared deviations judged right: the snap's argument order is this plan's own
literal wording and `sets`/`path` cannot be confused; the plain class has no `.copy` or structural use anywhere; the
crossing's moved step values are both `fineCellM` with nothing evaluated depending on the order; and the inline
tally-free context is a local `val` of real values, so no placeholder and no second lifetime. Open:

- **The tally-free context fold is duplicated verbatim** at
  [`RoutePassPrimitives.kt:185`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:185)
  and [`RouteFinePass.kt:33`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:33) — a
  one-line factory over a setup would collapse it, and it is **not** the deleted adapter, which bridged loose
  parameters rather than a setup.
- **The per-walk memo is a convention no fixture guards** — the plan's own blind spot, closed only by the still-owed
  test that drives `runPass`; a memo shared across a pass's two pulls would pass every existing test.
- **Doc:** the §Findings bullet on the snap's nine parameters described the pre-step signature and now says both.

**Closed 2026-10-07** — the two should-fixes are cleared: the tally-free fold is now one factory,
`PullSetup.context()`, called by `pricedLineCost`, `snapToCorners` and `pull` alike; and the memo's one-per-walk
rule is **pinned by a fixture**, `eachPullGetsItsOwnMemoSoTwoWalksReadAlike` in `AvoidPriceWalkTest`. The
field-stability condition that bullet 93 left as this plan's precondition is held by `LiveMultipassWorld`'s own
**stated mutation window**, the first of the two exits.

**Unverified by that hop, and resting on the build's own report:** the suite's triple, `apk-build.bat` and the
drawn-line identity — the review had no shell.

## Outcome

**Landed 2026-10-05, all three phases.** `PullSetup` holds the walk's water and is built in the four calls that hand a field to a walk-side consumer; `pull`, `snapToCorners` and `pricedLineCost` take it, and both bridging adapters (`legClear`, `softPriceSec`'s entry) are deleted. Its two should-fixes — a verbatim-duplicated tally-free fold and the memo's per-walk convention — were cleared 2026-10-07 with `PullSetup.context()` and a fixture. A pure refactor: no behaviour, no key and no reading moved.
