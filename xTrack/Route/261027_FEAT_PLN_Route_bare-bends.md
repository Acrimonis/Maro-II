<!-- scope: feature -->
# Route — no bend in the middle of nowhere: a merge never deletes a turn the land forces

**Date:** 2026-10-27 · **Status:** P1 implemented (`## Implemented`) and read on the device (§8): "Closest" fixed,
"Balanced" still turning at sea; P3 implemented, its device reading owed (D2); P2 deferred · **Order:** the user's word of 2026-10-27, *"plan,
review plan, update, report"*, on the "Closest" complaint (*"again a bend in the middle of nowhere doesn't make
sense"*) and the discussion that followed: tweak the extremities of the pulled stretch — east of
Sainte-Marguerite and at the 10 kn zone's corner — rather than replace them. P3: the user's *"plan recommandation"*
of the same day, on *"there is still a bend on balanced"* and the split recommended from its log.

**Related, not overlapped:** [`261026_FEAT_PLN_Route_movable-slow-approach.md`](261026_FEAT_PLN_Route_movable-slow-approach.md)
shipped the approach pass this plan corrects; [`261020_FEAT_PLN_Route_smooth-corners.md`](261020_FEAT_PLN_Route_smooth-corners.md)
owns what the corner pass does after it, the La Salis hook included (§1).

## 1. Evidence — the 2026-10-27 device run (Lérins → La Salis, 30 kn, `evolutive`)

- **"Closest" (λ 1) is longer and slower than "Balanced" (λ 3):** 15 768.6 m and 1 815.4 s against 15 372.2 m and
  1 793.5 s (`LINE`).
- **The open-water bend is the approach pass's.** The swept line (13 points, `SWEEP in=26 out=13`) turned at
  Sainte-Marguerite's east tip, 43.51000, 7.07913 — kept for `PRICE` (53.0°) on the sweep's first pass and for
  `LAND` (36.8°) on its second, the chord skipping it crossing the island — and at the 10 kn zone's corner,
  43.53428, 7.13994, kept for `PRICE` (`zone=200 s` on the chord). The approach pass replaced both with one
  point, 43.50589, 7.08781 (`CORNER idx=4 turn=51.2° band=false inZone=false`); the straightener only drops
  points and the nudge acts at the start. Over the same ends the stretch grew from 10 034 m to 10 469 m (+435 m),
  to leave the Lérins slow zone sooner. `APPROACH moved=2 savedSec=16.893` is the pass's total over its two moves.
- **The mechanism:** `refine` tries a `from`'s spans from `MAX_SPAN` down to 2 and keeps the first that
  improves, so a three-span merge — two inner points replaced by one — won before the two-span window, which
  slides one point with its neighbours held, was tried.
- **Why the point stands in open water:** one point standing for two turns, its ends held, sits where the two
  outer legs cross — the legs graze the island and the zone, the point grazes nothing. **The defect is the
  merge of a turn the land forces, not where the point stands:** a single legitimate turn round a smooth
  margin also stands off it, by `R(sec(θ/2) − 1)` (about 54 m for a 51° turn round a 500 m radius).
- **The La Salis hook is not this pass's.** Every `CORNER at=` is read off the corner pass's `compoundLine`,
  which replaces a close pair of same-way bends with the crossing of their outer legs: the swept line's three
  band-hugging bends near the port (about 107° together) became one 103° turn at that crossing. It is the
  corner pass's — recorded under §5, out of this plan.

## 2. The rule — a merge never deletes a land turn

- **A land turn** is an inner point of the current line whose skipping chord — its two current neighbours
  joined — the pull's own clearance proof refuses for land: `legClearCause(u, w) == ChordRefusal.LAND`, which
  answers for the coast, the rastered walls and the measured shallow-water wall alike. It is the sweep's own
  `keep … why=LAND`, read on the line the pass is handed.
- **A window of span 3 or more** — a merge, replacing its inner points with one — **is eligible only if none of
  its inner points is a land turn.** A turn kept for price, a bend pinned by an old crossing, may still be
  merged away: that is the pass's reason to exist (the 2026-10-26 Village Englouti case, a `BAND` refusal at an
  offshore bend). A two-span window — a slide — deletes nothing and is unaffected.
- **It is read at window level, before any candidate:** one clearance proof per inner point, the pair-keyed
  like the pass's `slow()` and answered by the memo where the chord was walked, so an ineligible window costs a
  proof and skips its whole search — fewer evaluations than today, never more. Both evaluation orders read it
  (`incumbentFirst` either way), and F1's repeat key holds: the test reads only the window's own points.
- **What it does on the device pair:** the window spanning the island tip is skipped, so "Closest" keeps the
  swept line's island-tip and zone-corner turns (10 034 m) unless a slide or a merge that deletes no land turn
  finds better.

## 3. Phases

| Phase | What | Touches | Kind |
|---|---|---|---|
| P1 | the rule, with the move's own line | `RouteApproachPass` | moves lines |
| P2 | slides at their own resolution — deferred to P1's reading | `RouteApproachPass` | moves lines |
| P3 | a turn standing for two is split in two (§8) | `RouteApproachPass`, `RouteCornerPass` | moves lines |

**P1 — a merge never deletes a land turn.**
- `refine` holds a pair-keyed `landTurn(u, v, w)` beside `slow()`, and a window of span ≥ 3 with an inner land
  turn is skipped (`landTurnWindows=` on the `APPROACH` line).
- **The move's own line:** `APPROACH move pass= from= span= replaced=[at:why …] at= savedSec=`, one per accepted
  move, each replaced point named with the reason it stood — the attribution the device run had to infer.
  Reporting only; where the trace is live it re-reads each replaced point's cause on an untallied context.
- **No instrument phase first:** the questions it was to answer are gone — the tolerance went with the
  point-support rule (§7), the hook with its reattribution, the pull's "bare bends" with the point test. The
  rule has no tunable; this pair's current log is its before-picture.
- **Tests:**
  - A fixture with an island tip and a slow zone, where today's pass merges the tip's land turn and a price turn
    into one point: P1 keeps the tip's turn, `landTurnWindows` present.
  - `movesTheSlowCrossingAndRemovesTheOpenWaterBendTogether` unmoved — its deleted bend is a price turn.
  - The approach suite and the six-water equivalence (both orders read the rule) hold.
  - The engine fixtures' pinned points re-recorded only where they move, each with its reason.
- **Device acceptance, the user's:** on the same pair, "Closest" keeps a turn at Sainte-Marguerite's tip, the
  `APPROACH move` lines name what each move deleted, and the rung's `LINE` is no longer longer than "Balanced"'s
  by the same stretch.

**P2 — slides at their own resolution (deferred).**
- After P1, the restored turns are the swept line's own points, set on the search grid — up to a coarse cell
  (100 m) off the island's margin or the zone's corner they round. Sliding each, its neighbours held, tightens
  the stretch: the user's "tweak the extremities of the pulled stretch".
- Today's two-span window cannot do it finely: its radius is twice the near leg, and six halvings from half that
  leave a finest step of `radius / 64` — 81 m on this window. A slide needs its own radius (a few fine cells)
  scaled down to `stepM`.
- Its best point is where its two legs, ends held, cross: off a smooth margin by the bend's own geometry, which
  is right, not a defect.
- **Held until P1's reading** shows how far the restored turns stand from what they round (D1).

**P3 — a turn standing for two is split in two.**
- **Where:** `RouteApproachPass.refine`, on the two-span windows it already searches (`u`, `V`, `w`), after the
  slide's own search. It needs no gate of its own and inherits the corridor bounds, the proofs, the memo and the
  canonical orientation; the 2026-10-27 Balanced bend's window is one of them (its slide moved, §8).
- **The candidate:** two points in place of `V`, cut from the slide's answer `V'` (`V` itself where the slide found
  nothing; amended 2026-10-27 on P3's device reading) — `P` on the leg `u→V'`, `dP` back from `V'`, and `Q` on `V'→w`,
  `dQ` forward — the line `u→P→Q→w`. Seeded at the half cut (`dP = |uV|/2`, `dQ = |Vw|/2`) and searched by the slide's
  own loop in the `(dP, dQ)` plane: eight directions, each axis's step a quarter of its own leg, halving,
  `MAX_SCALES`. A candidate off either leg (`dP` or `dQ` outside `(0, leg)`) is refused before any read, as
  `outside` is.
- **Why on the legs:** `u→P` and `Q→w` lie on the slide's legs and `P→Q` is shorter than `P→V'→Q`, so a
  split loses only where `P→Q` costs more or is refused. One new chord decides each candidate — not the 48 × 48
  square of free positions §5 declined.
- **Price, then proof:** the three legs are priced and proved as a slide's two are — the three legs' travel, then
  the running sum, as the bounds, the proof paid only where the candidate would win. No leg inherits its parent's
  proof (each is walked on its own lattice), and no new point earns a waiver. `incumbentFirst = false` runs the
  proof-first order on the split too, so the six-water equivalence covers it.
- **The window's move:** the cheaper of the slide's best and the split's best, each beating the window's own cost.
  The split's incumbent is the lower of the two, so its bounds cut from the slide's answer.
- **The corner pass's floor:** a split whose `P` and `Q` stand closer than the tangent a pace-speed curve needs for
  their summed angle — `paceRadius · tan(Σθ / 2)`, the span test by which `RouteCornerPass.compoundLine` turns a
  close pair into the crossing of its outer legs — is refused, since that crossing is `V` itself. The span test
  moves out of `mergeCrossing` into one function both passes call. On this pair at 30 kn it is about 72 m
  (`route.turn.lateralAccelMps2`, a 23° turn); `P` and `Q` would stand kilometres apart.
- **It ends:** every move is a strict drop of the one cost, so a later merge undoes a split only where one point is
  cheaper still, and the land-turn rule (§2) reads `P` and `Q` like any point. The second pass searches the windows
  a split made (`u,P,Q`, `P,Q,w`); F1's repeat key holds, a split's points being new.
- **The lines:** the move line gains `kind=slide|merge|split`, a split's `at=` naming `P` then `Q`; the summary
  gains `splitWindows=`, `splitEvals=`, `splits=` and `splitFloor=` (bests refused by the floor), the split's reads
  inside the clocks and counts already there.
- **Tests:**
  - A fixture with one land disc and one slow disc on the inside of one bend, the line's single point where their
    tangent legs cross: the pass splits it, the line is cheaper, each new point stands nearer its own feature than
    `V` did, and the move line reads `kind=split`.
  - A turn hugging one small disc: the best split stands inside the floor, is refused (`splitFloor` present), and
    the line is the slide's.
  - The six-water equivalence (both orders, the split's paths among the asserted ones), the second-pass skip,
    `aMergeNeverDeletesALandTurn` and `movesTheSlowCrossingAndRemovesTheOpenWaterBendTogether` hold; an engine
    fixture's pinned points are re-recorded only where they move, each with its reason.
- **Device acceptance, the user's:** on the same pair, "Balanced" turns near Sainte-Marguerite's east end and near
  the zone's corner off Cap d'Antibes rather than at sea between them, its `LINE` no longer longer and slower than
  "Around"'s; the `APPROACH` line's `ms` on that rung (5 761.7 today, 491 evaluations) read beside `splitEvals` (D2).

## 4. Decision on P1's reading

- **D1 — P2's go, and which points slide.** Go if the restored turns stand far enough off what they round to
  matter on the drawn line; the points are the extremities of a pulled stretch — the two bounding a long
  open-water leg (5.6 km on this pair) — every point only if few qualify.
  - **Read 2026-10-27 (§8):** "Closest"'s restored tip turn was slid twice by today's two-span window (3.407 s, then
    0.235 s) and the user's run named no bend on it; the log gives no distance to the margin. P2 stays deferred.
- **D2 — P3's cost, the user's.** The split adds up to 49 evaluations of three legs to each two-span window; on
  "Balanced" today the pass searched 11 windows of every span at about 12 ms an evaluation (5 761.7 ms, 491
  evaluations). Its device reading (`ms`, `splitEvals`) decides whether the bend's fix is worth that time.
- **D3 — the fix and its reach, settled by the user on 2026-10-27.** P3, the split in the approach pass, over a fix
  at the source (the pull placing turns at the features) or leaving the bend. A split round a wide, smooth margin —
  two gentler bends where one stood, other routes' lines moving — is accepted: no rule restricts the split to the
  two-feature case.

## 5. Not in this plan

- **The La Salis hook** — the corner pass's `compoundLine` crossing (§1); the corner pass's plan
  ([`smooth-corners`](261020_FEAT_PLN_Route_smooth-corners.md)) is its home.
- A point-support rule ("a turn must rest on something") — withdrawn on review (§7).
- Two points per window — the search squares (48 × 48 candidates a window).
- Re-solving a stretch on the fine grid in place of the pass — a larger rewrite; the fine pass's own local
  re-solve refused this very stretch as dearer (`FINE zone=Zone 5 kn … spliced=no reason=worse`).
- Capping a merge's radius so its finest step reaches `stepM` — it changes every merge's reach, not the defect.
- The zone reads' cost — the search-speed plan's next lever.
- An any-angle search, or a pull that places a vertex off the grid path at a feature's corner — the source of §8's
  bend, fixed there for every pass, but a rewrite of the search or the pull.
- A bend standing for two features with no slow crossing beside it — the approach pass searches no such window, so
  P3 does not reach it.

## 6. Strongest objection

The rule can refuse a merge that rounds land better than the points it deletes — a staircase of land turns
along a coast, which one point would round more tightly — and it reads a land turn against the point's current
neighbours, which a slide or a merge beside it can change. P1 alone restores the swept line's grid-placed turns,
right in shape but not tight until P2.

**P3's:** it treats the symptom downstream — the pull commits grid-path points (§8), and a fix there would cure
every pass and the land-only case P3 cannot reach (§5). It costs up to 49 three-leg evaluations per two-span
window, on a pass the search-speed plan has spent a day shrinking, for about 107 m on this pair, the gain being
mostly a line that no longer looks wrong. And it moves lines elsewhere: a single turn round a wide, smooth margin
can split into two bends farther apart than the floor — cheaper and right, but more points for the corner pass.

## 7. Review — one independent read, 2026-10-27, and what it changed

The hop checked §1–§6 of the first draft against the device log's three rungs and the source, and was briefed
to break each claim. Its findings and the plan's disposition:

| # | Finding | Disposition |
|---|---|---|
| R1 | **Blocks the draft.** The La Salis point is the corner pass's `compoundLine` crossing, not the approach pass's; P1 could not remove it and P0 would never have seen it. | Reattributed (§1); out of this plan (§5). |
| R2 | **Blocks the draft.** A legitimate single turn round a smooth obstacle stands off its margin by `R(sec(θ/2) − 1)`; a 20 m point test refuses it. | The point-support rule withdrawn; the defect stated as the merge of a land turn (§2). |
| R3 | **Blocks the draft.** The search's finest step is `radius / 64` — 81 m on this window — so no 20 m tolerance is reachable and "slides onto the corner" was out of reach. | `tolM` gone with the rule; P2 needs its own resolution and is deferred (§3). |
| R4 | Refusing each bare candidate strands the scale search's centre. | Moot: the rule is window-level, before any candidate. |
| R5 | The draft's per-candidate refusal touched one evaluation order only, so the proof-first test would diverge. | Moot: both orders read the window's eligibility. |
| R6 | `hardDistanceM` leaves the rastered walls out, so a turn round a wall marker read as bare. | Moot: `legClearCause` reads every wall. |
| R7 | `priceClearanceM` reports false edges inside zones and edges `max(band, zone)` hides. | Moot: no support read remains. |
| R8 | §1's keep causes were reversed, the growth was 435 m (not 510), and `savedSec` is two moves' total. | Corrected. |
| R9 | The cost counts were proofs per rung, not per pass. | Moot with the per-candidate check. |
| R10 | The zone-corner point's side of the zone is inferred, not read. | §1 names the point, not its side. |
| R11 | A corridor-clipped point would have read as bare. | Moot. |
| R12 | The gate row added nothing where the berth is priced. | Moot. |
| R13 | `movesTheSlowCrossing…`'s point is near the rim by chance, not by construction. | Its deleted bend is a price turn; the rule leaves it. |
| R14 | Simpler fix: refuse a merge that deletes a point kept for `LAND`, or cap a merge's radius. | The first taken as §2's rule; the second declined (§5). |

**Confirmed by the hop, as read in the log and the source:** the `LINE` figures; the approach pass placed
43.50589, 7.08781; the 51.2° corner and the zone's 200 s on the chord; the 5.6 km leg; the span order and the
first-improvement stop; a two-span window slides its middle point with its neighbours held; seeds are never
tested; direction symmetry holds, the pass running in canonical orientation.

## 8. Evidence — P1's device run (2026-10-27 21:53, Lérins → La Salis, 30 kn, `evolutive`)

- **The rungs' `LINE`:** "Closest" (λ 1) 15 000.1 m, 1 651.4 s; "Around" (λ 5) 15 089.4 m, 1 658.7 s; "Balanced"
  (λ 3) 15 137.5 m, 1 662.9 s. "Closest" is now the shortest and fastest; "Balanced" is longer and slower than
  "Around" at the same shares (`zoneShare=0.31`, `bandShare=0.09`).
- **P1's acceptance on "Closest": met.** The sweep keeps Sainte-Marguerite's east tip, 43.51000, 7.07416, for
  `LAND`; the pass slides it twice (`span=2 replaced=[43.51000,7.07416:LAND] … savedSec=3.407`, then `0.235`) to
  43.50888, 7.07203 and skips 10 merge windows (`landTurnWindows=10`). The line turns there (40.1°) and at the
  zone's corner off Cap d'Antibes, 43.53183, 7.13502 (10.2°).
- **"Balanced"'s bend is the swept line's own point, not a merge.** The sweep keeps 43.51450, 7.09898 for `PRICE`
  (`chordSec=1247.890 pathSec=539.768`), the chord skipping it crossing the island's band and a zone
  (`band=363.013 zone=361.361 worst=BAND at=43.51261,7.07626`). The pass slides it 180 m (`span=2 …
  at=43.51342,7.09750 savedSec=12.175`), where it stands at sea as a 22.9° turn — 1.7 km from that band reading and
  3.7 km short of the zone corner "Closest" and "Around" turn at.
- **One point standing for two turns — §1's geometry, reached another way.** The leg into it passes about 245 m
  south of the refused chord's worst point; the leg out of it runs within 5 m of the zone corner. A line turning at
  the tip "Around"'s sweep kept (43.51257, 7.08185) and at that corner is about 107 m shorter over the same ends
  (7 865 against 7 973 m on the logged points, not priced).
- **Where it comes from:** the pull commits the grid path's own point at the first refused chord
  ([`MultipassPull.kt:805`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt#L805)), so a
  vertex stands where the grid path bent. "Balanced"'s path bent once between the island and the zone, "Closest"'s
  and "Around"'s near each; why the A\* bent where it did is not read.
- **What no current move does:** a slide moves one point and a merge replaces several with one (§2), so no move
  turns one point into two; and one point cannot round both features — moving it toward either takes the other leg
  into the band or the zone. P2, a finer slide, does not reach it either.

## Key files

- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteApproachPass.kt` — `refine`, the window loop
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt` — `legClearCause`, `ChordRefusal`, `walk` (where a vertex is committed)
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteCornerPass.kt` — `compoundLine`, `mergeCrossing`'s span test (P3's floor)
- `app/src/test/java/ykws/android/maro/spatial/multipass/RouteApproachPassTest.kt`

## Implemented

- **2026-10-27 — P1, on `feature/roooouuutttiiinnngggg`, `#implement`.** The whole unit suite green (1 651 tests,
  one new) and `apk-build.bat` green; the user's device pass on the same pair reads *"looks better"* — its log not
  read, the device being off `adb` when asked.
  - **The rule** — `RouteApproachPass.refine` skips a merge window (span ≥ 3) whose inner points hold a land turn:
    `legClearCause(u, w) == LAND` on each inner point's current neighbours, cached per chord like `slow()`, timed
    into `clearMs` and counted (`landProofs=`); `landTurnWindows=` on the summary line. Read before any
    candidate, in both evaluation orders, ahead of F1's repeat key. `keepLandTurns = false` is the test seam.
  - **The move's own line** — `APPROACH move pass= from= span= replaced=[lat,lon:LAND|PRICE|NONE …] at=
    savedSec=`, one per accepted move, the window's points captured before the splice; `PRICE` is the plain
    chord-against-legs comparison, not the sweep's allowance-weighted verdict. `MultipassPull.atText` is now
    `internal` so both lines print points one way.
  - **Pinned** — `aMergeNeverDeletesALandTurn`: one land turn round an island and one price turn round a second
    slow disc in the one merge window (each asserted), so a rule reading either point alone, or every point
    together, would fail; without the rule the pass merges both and its move line names the land turn, with it
    the window is skipped and a land turn survives, the line no dearer. The approach tests read the summary line
    among the move lines (`summary()`); no engine fixture moved.
  - **Deviations** — `deleted=` shipped as `replaced=` (a slide's point is replaced, and `LAND` there is no
    breach); the move line's causes are re-read on an untallied context where the trace is live — a clearance
    proof and up to three prices per replaced point inside `ms`/`passMs`, never in the read clocks.
  - **Review hop (2026-10-27)** — nothing blocking; it confirmed the rule as planned, the chord cache, the timing
    inside `clearNs`, F1's key, the lazy line's captured values, and that no reader of `APPROACH` lines breaks.
    Fixed on its word: the fixture rebuilt from two land turns into one land and one price turn; the move line's
    reads and the clearance clock's KDoc written as they are; `replaced=`; `landProofs`; a vacuous assertion
    dropped. **Left, named:** the land-turn test is written three times (`landTurn`, `keepCause`, the test's
    count), `keepCause` answering strings beside `ChordRefusal`; `refine` carries three test switches and two
    local caches — the next rule there wants its own window-eligibility step.
- **2026-10-27 — P3, on `feature/roooouuutttiiinnngggg`, `#implement`.** The whole unit suite green (1 653 tests,
  two new) and `apk-build.bat` green; no engine fixture moved. The device reading on the Lérins → La Salis pair
  (D2) is owed.
  - **The split** — `RouteApproachPass.searchSplit`: in a two-span window, after the slide's own search, two
    points on the window's legs (since amended to the slide's legs, below) are searched by the slide's loop in the `(dP, dQ)` plane against the lower of the
    window's cost and the slide's best; the window takes the cheaper. The move line reads `kind=slide|merge|split`,
    a split's `at=[P Q]`; the summary carries `splitWindows= splitEvals= splits= splitFloor=`.
  - **One evaluator** — `candidateSec` (price first) and `replacementSec` (proof first) now take a route of two or
    three legs and a proof order (`SLIDE_PROOFS`, `SPLIT_PROOFS` — the split's new chord first), the slide's sums
    and bounds bit for bit what they were.
  - **The floor** — `RouteCornerPass.pairIsOneTurn`, `mergeCrossing`'s span test moved out, and `turnDegreesAt`
    made internal, so both passes read one test and one turn.
  - **Pinned** — `aTurnStandingForTwoFeaturesIsSplitInTwo` (an island and a slow disc, one turn each, the same
    line proof-first and with every second-pass window searched); `aTurnRoundOneHeadlandIsNotSplit` (a cheaper,
    clear half cut refused by the floor alone, the line keeping its one turn); the six-water equivalence asserts
    split candidates run in both orders.
  - **Deviations** — `splitFloor` counts cuts refused before any read, not bests refused afterwards: the floor is
    read per cut, so the search finds the best admissible split. The floor reads the span test alone, not the
    corner pass's flat-kink and crossing-within-the-legs checks, so it errs toward the one point. A point a split
    made is not split again in the same pass (only `q` can be seeded later), so a pass ends; a window searched
    under that hold is never recorded as searched, so the second pass may still split it. A cut off a leg is
    counted nowhere. No test switch was added to `refine`.
  - **Review hop (2026-10-27)** — one blocking finding, fixed: a held window was recorded as searched, so the second
    pass skipped a split it should have tried and the repeat skip could change the line. Fixed on its word too: the
    slide's and split's evaluators unified (AGENTS §1, no copy-paste), the floor test rebuilt so the floor decides,
    the split test made to assert each feature its own turn and both orders and skip modes, the stale tally and
    class KDoc. **Left, named:** the slide's search is still inline in `refine` while the split's is its own
    function, and `refine` has grown — P1's note (a window-eligibility step) stands; `AppConfig`'s code default for
    `route.turn.lateralAccelMps2` (0.33) contradicts `maro.properties` (0.666667), which the floor now reads too.
- **2026-10-27 — P3 amended: the split cuts the slide's answer, on `#implement`.** P3's device run (23:08, the
  same pair) left "Balanced"'s bend at sea: the split of the seed kept part of a second leg clipping the zone's
  corner, which the slide had moved off for 12.2 s, so it lost; and the slid window's legs were both clean, so the
  slow-water gate never offered it to the second pass (an independent review read this off the slide's 169 m step and
  the 49 + 41 + 33 split evaluations). `searchSplit` now takes the slide's best point — the seed where the slide found
  nothing — so the first pass splits the cleaned legs. The whole unit suite green (1 654 tests, one new:
  `aTurnTheSlideMovesIsSplitOnTheLegsItFound`, its bend's second leg cutting into the disc so the slide moves first,
  shown to fail with the seed restored) and `apk-build.bat` green.
  - **Review hop** — nothing blocking. Fixed on its word: "the one corner it replaced" → "it splits" in both passes'
    KDoc, this plan's P3 text, the two-feature fixture's names, and the new test asserting the split's first point
    stands off the original first leg. **Left, named:** the seed's own split is no longer searched — one beating both
    the slide and the pivot's split is lost (no fixture or device case shows one; trying both doubles `splitEvals`);
    "seed" names both the line's point in `refine` and the half cut in `searchSplit`. "Closest"'s line is expected to
    move (its tip turn is slid first) and wants a re-read; the cost reading (D2) is owed on the device.
