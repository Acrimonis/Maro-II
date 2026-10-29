<!-- scope: feature -->
# Lone turns — a price turn on clean legs, never searched

Status: **in design** (2026-10-28, reviewed — §9). This plan follows from
[`261027_FEAT_PLN_Route_bare-bends.md`](261027_FEAT_PLN_Route_bare-bends.md): its §5, and P5's argument against itself,
which says "a point the pull itself leaves at sea between a band and a corner, on clean legs and with no S around it, is
still out of reach". It is a new plan rather than another bare-bends phase, because that plan has shipped and its pointer
is already in `## Implemented`.

## 1. The defect

- **The shape.** Take one inner point `v` of the swept line, inside the window `a → v → e`:
  - neither leg `a→v` nor `v→e` carries band or zone water;
  - the chord `a→e` that would skip `v` crosses slow water, so the sweep keeps `v` for `PRICE`;
  - `v` has slack: a point some way along one of its own legs gives a clear line that costs less.
- **Why no stage moves it.** Each stage after the pull either deletes points, moves them under some other condition, or
  leaves `v` alone. Reading the code (§9):
  - **Snap** moves a point onto a corner only when the point lies within a set's radius of that corner
    (`RoutePassPrimitives.kt:465-487`).
  - **The sweep** only deletes points (`MultipassPull.kt:1131,1159`).
  - **The approach pass's slow gate** opens a two-span window only where exactly one of its two legs touches slow water
    (`offerFor`: `slowOpen = firstSlow != lastSlow`). Here both legs are clean, so the pass adds `slowSkipped++` and
    searches nothing.
  - **The S read and P5b's leg slide** need an S, which takes a three-span window with opposite turns. A two-span window
    is never an S.
  - **A three- or four-span window** around `v` whose outer legs differ in slowness can merge `v` away.
  - **Straighten** refuses any chord dearer than the legs it replaces (`chordLegal`), so it keeps a price turn.
  - **The nudge** acts only in narrow passages.
  - **The corner pass** replaces only close pairs of turns the same way.
- **So the defect is precisely:** a lone price turn left standing after the three- and four-span windows have run. This is
  what P0 counts.
- **There is no device evidence yet.** The case is derived from the gate's code and P5's argument. Every device instance
  read so far was an S, which P4a and P5b now cure.

## 2. The root, restated from the code

- **The pull commits a point of the path it walks.** When the chord `anchor → path[probe]` is refused, `MultipassPull.walk`
  commits `path[probe − 1]` (`MultipassPull.kt:800-806`). This holds for `walk` only. Snap puts set corners in place of
  path points, and the zone-crossing re-solve splices in fine-grid centres (`RouteFinePass.kt:320`).
- **Two different pulls walk two different paths:**
  - **The runner's pull** walks the coarse cell centres (`RoutePassRunner.kt:121-122`). Here a commit at the refusal's
    tangent would move a vertex by less than one cell.
  - **The fine pull** walks the runner's finished line (`RouteAvoidEngine.kt:766-772` → `RouteFinePass.kt:146`). So its
    `path[probe − 1]` is a coarse vertex, and one step is a whole coarse leg. A tangent commit here would slide `v` along
    its outgoing leg. That is P1's move, but on one leg only, in the forward direction only, and before the sweep (D4).
- **The kilometre-scale misplacements come from where the coarse path bends.** On the device, "Balanced"'s turn stood
  1.1 km along a 1.3 km leg, and λ 3's coarse path bent where the other rungs' did not (bare-bends §9). The greedy pull then
  chains each anchor onto the last, so a wrong vertex fixes the direction of the next leg.
- **The cure at the root is an any-angle coarse search** (Theta*-style parent pointers). That rewrites the search and its
  performance budget, so it stays out of this plan (§6).

## 3. P0 — count the shape (instrument only, no line moves)

> **As built (2026-10-28), against the draft below.** The implementation review (§10) changed three things:
> - **Where it counts.** P0 counts on the line the pass hands on, each point once, rather than per window in `offerFor`.
>   The window form counted a point again in pass 2, and counted points pass 2 had merged away (R2).
> - **What it measures beyond the count.** The four checks alone give an upper bound, since a turn rounding a band with
>   both legs tangent passes all four (R1). So each `APPROACH lone` line also states:
>   - `slackM`: how far the turn could slide along either leg while the rotated leg stays clear and free of slow water,
>     halved down to the step;
>   - `toward=back|on`: which leg that is;
>   - `travelSec`: the travel such a slide would save.
>
>   The slack is read inside the trace, on the untallied context, so a route that is not traced pays nothing for it.
> - **What the summary carries.** It now has `loneTurns=`. No same-pass split exclusion is needed on the final line.

- **Detection.** In `offerFor`, a closed two-span window is a **lone candidate** when four checks pass, read cheapest
  first:
  1. Neither leg is slow (`!firstSlow && !lastSlow`). Both reads are already paid.
  2. `|turnDegreesAt(a, v, e)| ≥ RouteCornerPass.MIN_TURN_DEG`. This uses bearings only.
  3. The skipping chord holds slow water: `slow(a, e)`, the gate's own memoised read. This is the one new read per window.
     It scans the chord's whole lattice where the chord is clean, and stops at the first slow mark on a candidate.
  4. `v` is not a land turn (`landTurn(a, e)`). This proof is paid only where the first three hold. The shared `MarkMemo`
     usually answers it, and so does `landChords`, from the three- and four-span checks.
- **Output.**
  - `loneCandidates=` in the `APPROACH` summary.
  - One `APPROACH lone at=… turn=…` line per candidate, so a logcat or a harness run names the points.
  - `refine` has no live-trace switch, so the detection reads run on every route, not only on traced ones.
- **Where it is read.** The harness on both device pairs, the replayed "Balanced" line, and the user's next device run.
  The reads it adds are checked against `slowMs`/`slowLegs` and `landProofs`, as a share of the pass's `ms`. The worst
  case is about double `slowMs`.
- **Go / no-go (as built).** P1 goes ahead only for a lone turn whose slack runs well past a cell and whose slide saves
  real time. A turn with tens of metres of slack and under a second saved is the grid's quantisation, not a misplacement
  (§11).

## 4. P1 — slide a lone turn along its own legs

> **As built (2026-10-28), against the draft below:**
> - **A saving floor, `route.approach.lone.minSavedSec` = 1.0 s.** The lone slide's incumbent is `oldSec − floor`, while
>   each leg is still seeded at `oldSec`. Without the floor, 0.03–0.97 s slides set off second-pass re-searches of their
>   neighbours (the second pass's evaluations 98 → 426 on one rung), and two rungs ran +16 % against the 15 % budget.
> - **A travel pre-check.** No search runs where the chord `a→e`'s travel alone cannot beat `oldSec − floor`. That bound
>   is the same in both evaluation orders.
> - **A fixture switch, `slideLoneTurns`.** The P4a/P5b tests pass it as false, so they pin the S search's own answer;
>   the line as shipped is checked in `anSStandingForOneTurnIsMergedAtItsCrossing`.
> - **`isLone` is one local function**, shared by the offer and P0's count.

- **The offer.** A lone candidate becomes an `Offer` with a new `loneTurn` flag.
  - `Search.answerFor` runs `searchAlongLegs(a, v, e, oldSec, oldSec)`. This is P5b's one-dimensional search, with `v` in
    the crossing's place. The window's own cost is both the seed of each leg and the incumbent.
  - `oldSec` equals, bit for bit, the left-to-right sum `candidateSec` makes for the unmoved `v`, and the seed is the
    same constant in both evaluation orders. So both orders accept the same candidates and pay the same `evals`, and the
    six-water equivalence holds without P5b's "won" seeding.
  - Each leg's search starts at `v` and halves back toward it. The backward direction is refused without a read while
    the centre stands at `v`.
- **Why a move is an improvement.** A point on `a→v` or `v→e` shortens the travel (the triangle inequality, near enough).
  It is only approximate: `along` interpolates latitude and longitude linearly while travel is haversine, and a clean leg
  can still carry marker, depth or berth price. The guarantee is `Incumbent`'s strict test: a move is kept only when it is
  cheaper than the window, and it is proved clear like every candidate.
- **What it finds.** It finds the point where the rotated leg just clears the band or zone, on whichever leg a feature
  pins at its far end. That is P5b's premise, and it carries P5b's limit: where neither leg is pinned, the search returns
  the better of two lines, not the best point (D2).
- **Excluded seeds.** A point a split made earlier in the same pass (`seed in splitThisPass`) is not offered. Sliding a
  split's `q` off its leg could drop the pair under the corner pass's span, the floor (`pairIsOneTurn`) the split was
  held to. This matches `splitHeld`.
- **Bookkeeping**, which needs a small refactor:
  - `searchAlongLegs` takes the `AnswerKind` it places and the tally counter it increments. Today it hard-codes
    `SLID_CROSSING` and `crossingSlides`.
  - `AnswerKind.SLID_LONE`, and `loneWindows=`, `loneSlides=` and `slidLones=` in the summary.
  - `offerFor`'s window count gains a lone branch. Today every unsearched offer counts as an `sWindow`.
  - The move's trace kind gains `lone`. Today any two-span move reads `slide`.
  - A window searched without a move joins `unmoved`, so pass 2 never repeats it (F1).
- **Order in the loop.** Spans run from 4 down to 2. A window that returns no answer falls through to the next span, and
  the first move ends the loop. So every three- and four-span window containing `v` is tried before its lone two-span
  window.

## 5. Guards — regression and performance

- **Lines that must not move:** the two device pairs' lines wherever P0 counts no candidate, and land turns, which are never
  offered.
- **Lines expected to move, and with them more than the lone point:**
  - A lone move splices `v′` into the line and ends the loop. Every later window anchored on `v` then reads a new point,
    and pass 2 runs where it never ran before.
  - A slide that stops at the band's edge can leave a leg grazing slow water. Pass 2 can then open the slow gate on
    `(a, v′, e)`, with its 2-D slide and split, or merge it in a three- or four-span window.
  - These follow-on moves are expected, each strictly cheaper. They are counted in the harness diff and in `passEvals`.
- **Tests (`RouteApproachPassTest`):**
  - a lone price turn with slack slides to the band's edge: a golden point, and `slidLones > 0`;
  - a lone turn whose leg already grazes the band stays: `loneWindows > 0`, `moved == 0`;
  - a turn under 10° is not offered;
  - a land turn is not offered;
  - a window with both legs slow is not offered;
  - a split's `q` is not offered in the same pass;
  - the six-water equivalence (`pricingFirstAnswersTheProofFirstLine`) gains a lone line;
  - the `skipRepeats` pin draws one line;
  - `RouteDirectionSymmetryTest` passes under `-Dmaro.prebake=true`.
- **Cost per window.**
  - Each leg's search halves from half the leg down to the step: at a 20 m step, a 1.3 km leg runs 6 scales, 650 m down
    to 20.3 m. That is 6–12 candidates a leg and at most 24 a window. At a 33.3 m step it is at most 10 a leg.
  - Unlike P5b, the travel bound never cuts a candidate here: the new travel is at most the old, which is at most `oldSec`.
    So every candidate pays its new chord's price, about 24 × 3.1 ms ≈ 75 ms a window (3.1 ms an evaluation on the device,
    bare-bends R8).
  - On top of that come pass 2's follow-on searches.
- **Performance budget: ≤ 15 % on a rung**, the bound the harness runs are checked against. On a 5.8 s rung that is about
  11 lone windows, follow-on searches included. P0's count gives the bill before P1 spends it. Above the budget, cap the
  lone windows per pass (D3) before shipping.

## 6. Out of this plan

- **An any-angle coarse search.** It is the root (§2), but it rewrites the search and moves every line.
- **A tangent commit in the runner's pull.** It moves a vertex by less than one coarse cell (§2), so it cannot reach this
  shape.
- **A 2-D slide for lone turns** (the `searchSlide` disc). It tries 8 directions a scale against the one-dimensional
  search's 2. D2 keeps it as the fallback if P1's premise fails on a device line.

## 7. Decisions (the user's)

- **D1 — what counts as the skipping chord's price.** The choice is band and zone water only (`hasSlowWater`, the gate's
  own definition), or any price, markers and depth included (`keepCause == PRICE`, two `totalSec` reads).
  - **Recommended:** band and zone water, matching the slow gate.
  - **Strongest objection:** a turn held by a marker's or a depth's price stays out of reach.
- **D2 — one-dimensional along the legs, or the 2-D slide.**
  - **Recommended:** one-dimensional, which is P5b's code with 2 directions a scale.
  - **Strongest objection:** where neither leg is pinned, it finds the better of two lines, not the best point.
- **D3 — ship P1 on any P0 candidate, or only where the harness also shows it inside the budget.**
  - **Recommended:** require both.
  - **Strongest objection:** the harness does not reproduce the device's coarse path, so a budget read there may undercount
    the device's lone windows.
- **D4 — where the slide lives: the approach pass (P1), or a tangent commit in the fine pull (§2).**
  - **Recommended:** the approach pass. Its window is read in the line's canonical direction, on both legs. The fine
    pull's `pull` is forward-only, slides on the outgoing leg alone, and would move every refused commit, not just lone
    ones.
  - **Strongest objection:** the pull's version runs before the sweep, so the sweep and the approach pass would see the
    corrected point and could build on it. P1 fixes the point only after both have run.

## 8. The plan argues against itself

- ~~The defect has no device instance.~~ It has one since 2026-10-28 18:16 (§11): "Around"'s 43° turn, with 1.5 km of
  slack.
- **P1 is a fourth special case in the window loop** — the slow gate, the land turn, the S, and now the lone turn — each
  curing one shape the coarse path leaves. §2 puts the cure for all of them in the search, and every patch here defers
  that rewrite once more.
- **A lone move reaches past its own window.** Pass 2 can re-search the neighbours it changes (§5), so the cost is not just
  the lone windows'.

## 9. Review — 2026-10-28

One reviewer read the plan against `RouteApproachPass`, `MultipassPull`, `RouteFinePass`, `RoutePassRunner`,
`RoutePassPrimitives` (snap), `RouteCornerPass`, `RouteAvoidEngine` and the approach suite, briefed to break each claim.
Nothing blocked.

| # | Finding | Disposition |
|---|---|---|
| R1 | **Should fix.** "At most one step" holds only for the runner's pull, over coarse cells. The fine pull walks the runner's finished line, so its step is a coarse leg, and a tangent commit there is P1's move on one leg, forward. | §2 names both pulls; §6 narrowed to the runner's pull; D4 added. Read and confirmed (`RouteFinePass.kt:146`, `RouteAvoidEngine.kt:766-772`). |
| R2 | **Should fix.** "Every gate- or S-opened window takes the same answer" is false: a lone move splices and ends the loop, so later windows see a new point, and pass 2 runs where it did not. | §5's "must not move" narrowed to lines with no candidate. |
| R3 | **Should fix.** A slide to the band's edge can leave a leg grazing slow water, so pass 2 opens the slow gate or a merge on it. | §5 expects follow-on moves and counts their cost; §8 states it. |
| R4 | **Should fix.** §1 left out snap, the three- and four-span merge, and the corner pass's compound step. None slides `v` to the edge, so the defect stands. | §1 lists every stage, and the shape is restated as what is left after spans 4 and 3. |
| R5 | **Should fix.** The cost is ≤ 24 candidates a window, not "12 a leg, most priced", and the travel bound never cuts here, so every candidate is priced: about 75 ms a window. | §5 states it per window, with the window count the budget allows. |
| R6 | **Note.** `searchAlongLegs` hard-codes its kind and counter; `offerFor` counts every unsearched offer as an S; the trace calls any two-span move `slide`. | §4's bookkeeping names the refactor. |
| R7 | **Note.** A split's `q` is reachable in the same pass, and a lone slide could drop the pair under the floor the split enforced. | Excluded, like `splitHeld` (§4). |
| R8 | **Note.** The triangle inequality is approximate (linear interpolation of latitude and longitude, priced clean legs); the guarantee is `Incumbent`'s. | §4 reworded. |
| R9 | **Note.** "A vertex is always a path point" is true of `walk` only: snap and the crossing re-solve place others. | §2 qualified. |
| R10 | **Note.** P0's reads run on every route, untraced; they are cheap, at worst about double `slowMs`. | §3 states it. |

**What the review confirmed:**
- **The gate:** `slowOpen = firstSlow != lastSlow`, and a two-span window is never an S.
- **The pipeline facts:** the sweep only deletes points; straighten keeps a price turn (`chordLegal`, no allowance); the
  nudge acts only in narrow passages; `walk` commits `path[probe − 1]`.
- **P0's reads:** `slow()` and `landTurn()` are memoised for each `refine` call; `MIN_TURN_DEG` is 10, and `turnDegreesAt`
  is signed, so its absolute value is the one to compare.
- **P1's soundness:** seeding with `oldSec` keeps the two evaluation orders equal; the loop order, `unmoved` and
  `skipRepeats` work as stated; the line is read in its canonical direction; candidates stay inside the corridor; the
  named tests exist.

## 10. Implementation review — P0 and the leftovers (2026-10-28)

One reviewer read the uncommitted tree: P0, the three searches moved into `Search`, the two defaults now following the
properties file, `TurnConfigFixture.withTurn`, and the properties file's comments. Nothing blocked.

| # | Finding | Disposition |
|---|---|---|
| R1 | **Should fix.** P0 counts a necessary condition, not the defect: a turn rounding a band with both legs tangent passes all four checks, so "any candidate → P1" always fires. | Slack is measured on each line (`slackM`, `toward`, `travelSec`); the go/no-go now reads it (§3). |
| R2 | **Should fix.** Counting per window counts a point again in pass 2, and counts points that pass 2 merged away. | P0 counts on the line the pass hands on, each point once. |
| R3 | **Should fix.** `aWaivedCornersBowKeepsTheWallPastTheWaiver` was built at a 2 s roll-in but pinned only the acceleration. | It pins `BUILT_AT_TRANSITION_SEC` too. |
| R4 | **Should fix.** The Ui_Settings feature file still listed the three contradictions now resolved. | Trimmed to the three that remain. |
| R5 | **Note.** The `slowNs`, `clearNs` and `landProofs` KDocs, and the summary comment, predated P0. | Reworded. |
| R6 | **Note.** The test never asserted why each case counts as it does. | It now asserts clean legs (travel alone), the turn floor, and `LAND`; slack and grazing cases were added. |
| R7 | **Note.** Leftover text in the bare-bends plan, the hydration file, and the turn-impact plan. | Left as is: the plans are history, and `#bake` rewrites the hydration file. |
| R8 | **Note.** "For a 6 m boat" has no bearing on r = v²/a. | Dropped. |

**What the review confirmed:**
- the properties comments' arithmetic: 311 m of radius, and roll-ins of 48 m at 28 kn and 17 m at 10 kn;
- P0 reads only through memoised reads, which return the same answer whenever they are called, so it changes no decision;
- the moved searches use only the `Search` holder's own fields;
- no code still references `withLateralAccel`.

## 11. P0 evidence (2026-10-28)

- **Desktop harness:**
  - **Lines unchanged.** Both pairs gave identical lines and counts before and after; only read counts moved.
  - **Lone turns found.** Every rung on both pairs has 2–6 lone turns, and the replayed device line has 5.
  - **Most are the grid's quantisation:** 0–131 m of slack, at most 0.6 s saved; the "Around" 45° turn at La Fourmigue's
    band edge has 19 m.
  - **Two stand out:**
    - Lérins → La Salis, 43.54328,7.15732: 30.9°, 2 185 m of slack, 1.3 s saved.
    - "Closest", 43.57860,7.14631: 89.1°, 1 104 m of slack, 25.2 s saved.
- **Device, 18:16 run, La Salis → Le Village Englouti:**
  - **The first device instance.** On "Around" (λ 5), the turn at 43.53999,7.10292 (−43.1°) has 1 483 m of slack along
    its incoming leg ("back"), and a slide there would save 9.4 s.
  - **Lines unchanged.** All three rungs' lines match the 18:02 run, as P0 intends.
- **Device cost, attributed by the 18:36 run (the build as committed).** Comparing `fineMs` across runs does not isolate
  P0: the 18:02 run came from an app session eight routes in, while the 18:16 and 18:36 runs were each the first route
  after a launch. The pass's own clocks do isolate it:
  - **The slack probe costs 150–206 ms a rung.** That is the approach pass's time outside its tallied clocks (`ms` less
    `clearMs`, `priceMs` and `slowMs`), against 6–24 ms at 18:02: the probe reads land and slow water with no memo, and
    the trace is live on the device.
  - **The count itself costs next to nothing:** `slowMs` went from 15–21 ms to 22–28 ms, and `landProofs` from 6–10 to
    9–12.

## 12. P1 — review and evidence (2026-10-28)

One reviewer read P1 in the working tree against §1–§5, §7, §10 and §11. Nothing blocked.

| # | Finding | Disposition |
|---|---|---|
| R1 | **Should fix.** No reversed-direction check for a lone move: the equivalence test's mirror is a reflection, not a reversal. | Each case in `aLoneTurnSlidesAlongItsLegsOrStays` now asserts the reversed line. |
| R2 | **Should fix.** The S line as shipped (the S, then a lone slide) was checked by nothing for clearance, cost or symmetry. | `anSStandingForOneTurnIsMergedAtItsCrossing` now refines with the defaults and asserts clear, no dearer than the merge, reversed, and identical with every window searched again. |
| R3 | **Should fix.** The `loneTurns` KDoc said "none is searched", and P0's comment was stale. | Both say what the slide leaves: under the floor, a split's point, or made after the last pass. |
| R4 | **Should fix.** Four lines ran past 133 characters. | Rewrapped. |
| R5 | **Note.** Every lone window paid all its leg evaluations, even where nothing could save the floor. | The travel pre-check (§4). |
| R6 | **Note.** The traced slack probe (150–206 ms a rung on the device) dwarfs P1's own cost. | Kept for one device reading of P1, then to be dropped (§13). |
| R7 | **Note.** The equivalence test never covers a slide the floor refuses. | Not added: by the review's own reading it is order-equivalent. |
| R8 | **Note.** The key sits in the pacing section of `maro.properties`, and §4's draft call was stale. | The section was accepted; §4 records the floor. |

**What the review confirmed:**
- order-equivalence with the lowered incumbent, since a lone offer is never `searched` and never an S, so `best.sec` equals `oldSec`;
- the floor applies to lone slides only, and `+∞` is handled;
- the S tests' switch hides no regression;
- the window counts are disjoint;
- the config follows ONE HOME.

**Evidence:**
- **Tests:** 1 666 pass (24 skipped), and `RouteDirectionSymmetryTest` under `-Dmaro.prebake=true` passes 2/2 over the 24 pairs.
- **The device's "Around" line, replayed:**
  - The 43° turn at 43.53999,7.10292 moves to 43.54277,7.08493, La Fourmigue's band edge, saving 10.9 s.
  - The same happens on all three rungs' replays.
  - No other lone slide clears the floor.
- **Harness pairs:**
  - 1–2 lone slides a rung. On "Closest" (Lérins → La Salis), the 89° turn takes 7.1 s and then 4.3 s.
  - The approach pass costs +23–34 ms a rung on one pair and −8 to +24 ms on the other: at most about 13 % of a rung, inside
    the budget. Whole-rung times vary ±10 % between identical runs.

## 13. Left open

- **The phone run of P1 is owed.** It will show whether "Around"'s turn now sits at the band edge.
- **Drop the slack probe after that run.** It is diagnostic only and reads with no memo when the trace is live.

## Implemented

- **P1 (2026-10-28, `3f54b95c`, merged through PR #397).** `RouteApproachPass` slides a lone turn along its own legs
  where that saves `route.approach.lone.minSavedSec`. Read on the device 2026-10-29 08:38 ("Closest", 24 kn): two
  lone slides took (2.0 s, 1.1 s), five lone turns left, and a 43° bend at 43.56306,7.15856 that needs two points
  moved together — the case `261029_FEAT_PLN_Route_any-angle-search.md` takes to the search. The slack probe (§13)
  is kept on the user's word while the device passes continue.
- **P0 (2026-10-28, `41db360d`).** `RouteApproachPass.refine` counts the lone turns left on its line (`loneTurns=`) and
  names each one with its slack and the travel a slide would save (`APPROACH lone … slackM= toward= travelSec=`). It moves
  no line. Tested by `aLoneTurnIsCountedAndTheLineKept` (five cases), alongside the review's code leftovers:
  - the two defaults follow `maro.properties`;
  - `TurnConfigFixture.withTurn` pins three fixtures;
  - the three searches are members of `Search`;
  - the properties file's comments match the shipped values.

  Checks: 1 666 tests pass (24 skipped), and the harness gives identical lines. The plan's go/no-go now reads
  slack, not the bare count. **P1 is not built.**
- **P1 (2026-10-28, uncommitted).** A two-span window whose legs are both clean, whose point turns ≥ `MIN_TURN_DEG`,
  and whose skipping chord crosses slow water without being refused for land (`isLone`) is offered P5b's slide along
  its own legs (`searchAlongLegs`, `kind = SLID_LONE`). A slide is kept only where it saves
  `route.approach.lone.minSavedSec` (1 s) and is proved clear. No search runs where the chord's travel cannot save
  that much.
  - **The decisions as taken:** D1 band and zone water only, D2 one-dimensional along the legs, D3 evidence and budget
    (both met, §12), D4 the approach pass.
  - **Tests:** `aLoneTurnSlidesAlongItsLegsOrStays` covers five cases, the floor, repeat skipping and reversal. The
    equivalence test's lone lines, the S line as shipped, and the direction-symmetry survey also cover it.
  - **Deviations from the plan:** the saving floor and the travel pre-check (§4), and the `slideLoneTurns` fixture switch.
