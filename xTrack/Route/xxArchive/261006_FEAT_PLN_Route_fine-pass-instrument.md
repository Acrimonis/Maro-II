<!-- scope: feature -->
# Route — the fine pass, measured before it is changed

**Date:** 2026-10-06 · **Status:** in design · **Order:** the user's word of 2026-10-06 — `#impl`, taken on the
recommendation that this walk be measured before its price step is touched.

**Origin:** the review of [`261006_FEAT_PLN_Route_fine-walk-price-step.md`](261006_FEAT_PLN_Route_fine-walk-price-step.md:1),
**parked** the same day: its Phase 1 landed on a walk no shipped engine reaches, and the fine-only walk that
does run was never measured at all.

## Why — three findings, one build

- **The address was wrong.** The parked plan moved the price step on the fine **re-search**'s walk, which both
  shipped plans retire ([`UniformGridPlan.secondPassRegions()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:90) and
  [`EvolutiveGridPlan.secondPassRegions()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:141) answer `emptyList()`), while the walk that runs — [`finePass()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:47) —
  prices at its own fine cell under a comment asserting the opposite rule.
- **The payoff is unsized.** The engine times `finePass` and the retired re-search **together** as one `fineMs`
  ([`RouteAvoidEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:353)), and its only reader is tag-gated, so no shipped trace says what this walk costs or whether
  its price reads pay per interval. The record's 71–80 % share belonged to the half that was cut.
- **Reachability has no pin.** A wrong step is catchable by the owed `runPass` test; an absent walk is not —
  which is exactly how the parked plan's Phase 1 shipped unread.

## What changes

- **The fine stage is timed apart.** It keeps its total and gains its two parts beside it — the refinement and
  the re-search — so a shipped trace names which half spends the time.
- **The crossing is timed per zone.** Each zone's local re-solve prints its own ms and its box, so a route
  entering two zones separates them.
- **The fine pass's pulls report their tallies.** Both of [`finePass()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:84)'s pulls take a `PullTiming` and print the fields
  the runner's PULL and FINAL lines already carry — `priceReads`, `marks`, `memoPriceHits`, `stepM`,
  `priceStepM` — which is what says whether grouping is switched off on this walk.
- **The lines ride the existing trace**, so a pass taken the way yours always are captures them: no new switch,
  no tag to set, no separate instrument to enable.
- **A reachability pin.** One test fails if a shipped engine reaches no fine walk — it asserts the fine stage's
  own parts are produced for each shipped plan over a fixture route, so a walk nobody runs can never be changed
  again without the suite saying so.
- **Nothing about the price step changes here.** The step decision — the fine pass's own cell, or the engine's
  interior one — waits on the reading this build exists to take.

## Phases

1. **The timing.** The fine stage split into its two parts, the crossing's per-zone ms, printing beside the
   existing lines. Exit: `apk-build.bat` green, the suite triple unchanged.
2. **The tallies.** `PullTiming` threaded through `finePass`'s two pulls and the crossing's local pull, printed
   as the runner prints them. Exit: the same, with the counting fixture's figures unmoved.
3. **The pin.** The engine-reachability test. Exit: green, and shown red against the empty-region plans it
   exists to catch.

## Verification

- `apk-build.bat` green and the suite carrying the same three known reds and no fourth.
- The pin green, and red when a plan's `secondPassRegions` is emptied again.
- **The device pass is the user's own** (R97): it is the measurement, and its acceptance half is that no drawn
  line, distance or ETA moves.

## The device pass — what to run

- **Build:** the agent builds; **you** deploy and take the pass.
- **Pairs, by shape:** one route entering a priced speed zone, so the crossing half is in the figures; one
  entering none, so the pulls are read alone; one long arm (~13 km) and one short one (~3 km) — the last pass
  left its 13.3 km arm unread.
- **Pace:** at least one pair at the low end (5 kn) and one at the cruise you normally run; the last pass's
  35 kn could not serve the acceptance half.
- **Engines:** one pair per engine if the answer must cover both.
- **Report back:** *deployed and run*, with the log — the agent fetches the logcat then, and not before.

## Risks

- **The reading may say stop** — the figure can come back small enough that no change is warranted; that is a
  result, not a failure.
- **A split trace can mislead** if its parts are read as additive while the crossing's raster is paid once per
  zone rather than once per route.

## Parked

- The price-step change itself, with its resume: the reading named above.
- The owed `runPass`-driving seam test, which stays owed for the step and is **not** the reachability pin.

## Outcome

**Landed 2026-10-06 in one `#implement` run.** The fine stage's timing split into the refinement and the
re-search beside `fineMs` on the `DEVICE PASS` line, every
[`solveCrossing()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:115) outcome carrying its own `ms` and box,
and both of [`finePass()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:47)'s pulls plus the crossing's local pull
reporting the runner's five tallies. The pin
([`RouteFineReachabilityTest`](../../app/src/test/java/ykws/android/maro/spatial/multipass/RouteFineReachabilityTest.kt:1)) drives each shipped plan's
fine stage through the engine's own builder with the region restored, and was shown red against the raw
empty-region plan. `apk-build.bat` SUCCESSFUL; suite **939 / 3 / 11** — the baseline 937 / 3 / 11 plus the
pin's two tests, the same three reds. **The device pass of the same day** read `refineMs` equal to `fineMs`
on all 19 rungs (`reSearchMs` 0.0–0.1, `FINE research box=empty`) with the grouped term zero on every fine
pull, which named the fine-only walk's price step as the lever. **Phases 1 and 3 are superseded** by
[`261006_FEAT_PLN_Route_fine-pass-retirement.md`](261006_FEAT_PLN_Route_fine-pass-retirement.md:1) — with the
step dropped, the split's re-search half and the pin's subject are retired.
