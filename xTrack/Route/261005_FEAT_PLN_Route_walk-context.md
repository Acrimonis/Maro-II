<!-- scope: feature -->
# Route — the pull's walk context: one value per walk instead of ten threaded parameters

**Date:** 2026-10-05 · **Status:** in design, nothing built, no file outside this one touched.
**Order:** the user's word of 2026-10-05, *"plan this"* — the code-health point the `#implement` review left on
the mark count's Phase 2, deliberately kept out of that order and planned now.

**Asked for:** the Ask hop's own words of 2026-10-05: *"`memo` is threaded through eight signatures beside
`timing` in seven, so a walk-context value is the cleaner home"*.

## Why

- **One walk's constants travel one by one.** [`pull()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:188)
  already takes **eleven** parameters — `path`, `start`, `aim`, `marginM`, `coarseStepM`, `priceStepM`, `field`,
  `approaches`, `refusals`, `timing`, `memo` — and every helper beneath it re-declares the same subset:
  [`softPricePrefix`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:580),
  [`chordDecision`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:242),
  [`legClearCause`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:260),
  `priceRefusal`, `softPriceSec`, `spanPriceSec` and
  [`groupPriceSec`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:540).
- **The repetition is the review's count**: `memo` rides **eight** of those signatures, `timing` **seven**, and
  since Phase 4b both steps ride nearly all of them — the price step is a **required** parameter wherever a price
  is read.
- **The whole set is one walk's own and nothing else's** — the margin, the two steps, the field, the two raw ends,
  the carved approaches and the three tallies (refusals, timing, memo). None of it varies chord to chord, and no
  per-chord value (the two endpoints, the anchor and probe indices, the prefix array, a `MarkLattice`) belongs in
  it.
- **Two callers pay the same repetition at the seam**, and one of them is outside this file: `RoutePassRunner`
  lists the ten values twice and [`RouteFinePass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:87)
  four times.
- **Nothing behavioural is at stake**, which is what makes it safe to plan: the same reads at the same points in
  the same order producing the same doubles, with the memo's **one-walk lifetime** preserved structurally — the
  context is made inside `pull()`, so a memo can no more outlive its walk than it can today.

## The shape

- **One `PullContext` per walk, made at the top of `pull()` and nowhere else.** It carries the walk's constants
  — margin, coarse step, price step, field, start, aim, approaches — and the walk's three tallies — refusals,
  timing, memo — with the three kept **nullable exactly as they are today**, since a solve that wants none of
  them passes none.
- **`pull()` keeps its parameter list as the walk's own seam**, and folds the list into a context on its first
  line; `path` alone stays outside it. That is the smallest change that removes the internal threading, and it
  keeps every existing caller literally unchanged.
- **The internals take the context, not the bundle**: `(a, b, ctx)` in place of six or seven trailing
  parameters, with the per-chord arguments still passed explicitly.
- **The context is `internal`, like [`MarkLattice`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:73)
  and [`MarkMemo`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:116)** — a package
  type, never a public one, and never a field of an engine, a runner or a seat.
- **The two consult helpers stop taking the memo**: [`readHard()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:600)
  and [`readPrice()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:574) take the
  context and reach the memo and the timing through it, so the "one home that decides whether a read was paid"
  stays one home.

## Phases

1. **The context inside `MultipassPull`.** Declare the type, build it at `pull()`'s own start from the existing
   parameters, and thread it through the eight signatures; `pull`'s own signature is untouched. Exit: the whole
   unit suite green with **the same counts** — nothing added, removed or retitled, and the memo's counting fixture
   still reading **270 against the memo-less 410** — plus `apk-build.bat` green.
2. **The seam — planned separately and landed the same day** (2026-10-05): the callers, the corner snap and the
   price walk's own entry have their own plan,
   [`261005_FEAT_PLN_Route_walk-context-seam.md`](261005_FEAT_PLN_Route_walk-context-seam.md), which fixed the one
   decision this phase could not leave open — the **memo stays inside the walk**, so a caller-built value cannot
   price a walk from another walk's water. **All three of its phases landed**, with two should-fixes open: a
   verbatim-duplicated tally-free context fold, and the memo's per-walk convention being guarded by no fixture
   until the `runPass`-driving test exists.
3. **The suite follows the shape.** The fixtures that build a walk by hand move to the context in one mechanical
   pass, with no assertion edited and no expected value changed — the phase's evidence is that the diff of the
   test files holds signature changes alone.
4. **The ordered cleanup, two self-contained tidies.** The review's smaller pair, taken with this build rather
   than parked: [`readPrice()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:574)'s
   double hash probe (a `hasPrice` followed by a `price`, two lookups for one question, and the one that decides
   whether a read was paid) and `groupPriceSec`'s `groupStep`, recomputed on every call where the walk's own
   lattice and step already fix it. Exit: both still behaviour-identical — the counting fixture's `270` and the
   one-mark-twice counter's `1` unmoved — and `apk-build.bat` green.

## Verification

- **The counts are the proof**: the suite's completed/failed/skipped triple is identical before and after, and the
  counting fixture's own numbers (`270` against `410`, the one-mark-twice counter at `1`) are unmoved.
- **No assertion is retargeted and no expectation edited**, in either the lattice fixtures or the memo's.
- **The memo cannot outlive its walk, and the type says so**: no constructor call of the context exists outside
  `pull()`, and the context is never stored in a field — asserted by reading the diff, since a test cannot see it.
- **The seam's behaviour is unchanged where Phase 2 runs**: the same pulled line, the same `PULL` and `FINAL`
  traces, and identical `priceReads` between the two shapes on the same fixture.
- **`apk-build.bat` green** and the one parked red (`route.avoid.fine.cellRatio`) the only failure.

## Risks

- **A context is a magnet.** The strongest objection to this plan is that a walk context invites every later
  convenience to be parked in it, and the pull's purity is the reason its numbers can be trusted; the membership
  rule is therefore closed at exactly what travels with the walk today, and a new field is a new decision, not an
  addition.
- **A shared context would break the memo's one-walk bound** — the field is rebuilt per λ pass, and a context that
  outlived a walk would carry a stale memo past its rebuild. Made inside `pull()`, that cannot happen; made by a
  caller, it can, so Phase 2 must pass a **fresh** context or none.
- **The tallies are nullable and shared in different ways** — `refusals` is passed to two different pulls by the
  runner, `timing` is one per pull, and `memo` is one per pull; the context mirrors that exactly and must not
  normalise it into one lifetime.
- **Test churn is the cost**, and it is the reason Phase 3 exists as its own step: hundreds of lines of fixtures
  change signature while proving nothing new.
- **Doing it while Phase 3 of the mark count is unread** means the reading will be taken on the refactored shape;
  the plan's own answer is that the two shapes are the same walk, and the counting fixture is what holds that.

## Open questions

**Resolved before the build, on the order of 2026-10-05 (*"refactor and cleanup code"*):** the type is named
**`PullContext`**; `pull` **keeps its eleven parameters** as the walk's seam; the context is built inside `pull()`
and never stored; and the seam (Phase 2) stays out of this build until the user says otherwise — so the first
question below is answered *inside `MultipassPull`* for now.

- **Does the context cross into `RoutePassPrimitives.snapToCorners` and the two callers, or stay inside
  `MultipassPull`?** Phase 1 alone answers the review's sentence; Phase 2 is the larger, seam-wide version, and
  the user's call belongs at that boundary.
- **The name** — `PullContext` (the pull's own) against a `WalkContext` that would also cover the corner snap and
  the fine pass; the plan's own preference is `PullContext` while the context is one file's business, and a rename
  if Phase 2 makes it the seam's.
- **Whether `pull` should keep its eleven parameters at all** — folding them into a caller-built context is
  tidier at the call site and weaker at the guarantee, which is why it is not the recommendation. **Amended
  2026-10-05:** the seam plan takes the other view, with the memo kept inside the walk and the value rebuilt per
  pass so the guarantee survives structurally, and this plan defers to it — see
  [`261005_FEAT_PLN_Route_walk-context-seam.md`](261005_FEAT_PLN_Route_walk-context-seam.md).

## Parked / out of scope

- ~~The two smaller observations from the same review~~ — **taken, as the build's Phase 4**: the double hash probe
  in `readPrice` and `groupPriceSec`'s recomputed `groupStep`, each behaviour-identical and each judged on the
  counting fixture's own numbers.
- Any behaviour change, any new key, any new dependency, any device pass — nothing here is measurable on a device,
  and the mark count's Phase 3 reading is unaffected by the shape it is taken on.

## Landed 2026-10-05 — Phases 1, 3 and 4, with Phase 2 deferred

**Phases 1 and 3 landed** on the order *"refactor and cleanup code. Mitigate risks / improve code health"*: an
`internal PullContext` is built at [`pull()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:236)'s
first line from the eleven parameters the walk already took, threaded through the helpers in place of the repeated
bundles, with `pull`'s signature and every call site untouched and no context stored in a field; the tests'
hand-built walks moved onto a `walkCtx` helper with signature changes alone. **Phase 4 landed with them**:
[`readPrice()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:626) asks one lookup
where it asked two (`.hasPrice` then `.price`), counting a miss once and answering the memo's own double on a hit,
and `groupPriceSec`'s `groupStep` is computed once in `softPriceSec` and passed. `apk-build.bat` green, the suite
at **923 / 1 / 11** with the parked ratio test the only red, and the counting fixture still reading **270 against
the memo-less 410** with the one-mark-twice counter at **1**.

**The review hop's verdict — no blocker, two should-fixes.** Membership, the one-walk lifetime (no `PullContext`
field anywhere in `app/src`) and the three tallies' separate lifetimes are proven on the tree, and both tidies are
behaviour-identical. Open: the two **bundle adapters** (`legClear`, `softPriceSec`'s entry) fill a call-local
context with **placeholder** `start`/`aim`/`coarseStepM` their callee never reads — inert today, silently wrong if
a later change makes one live, which is why naming them is the declared risk's own mitigation — and
[`MarkMemo.price`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:143) is
production-dead, held by the tests alone, so the price walk's "one home" is convention rather than structure. One
check stayed open with the hop itself: the suite's triple and the fixtures' signature-only change are read from
the tree rather than diffed, no git being available there.

**Phase 2 was not taken**, on purpose: the callers and the corner snap move `pull`'s own signature and hand the
context's lifetime to a caller, so it waits on the user's word per §Open questions.
