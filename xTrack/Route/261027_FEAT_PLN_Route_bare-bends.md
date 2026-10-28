<!-- scope: feature -->
# Route — no bend in the middle of nowhere: a merge never deletes a turn the land forces

**Date:** 2026-10-27 · **Status:** P1 implemented (`## Implemented`) and read on the device (§8): "Closest" fixed,
"Balanced" still turning at sea; P3 implemented and accepted on the device (D2); P2 deferred; **P0 and P4a implemented (`## Implemented`) and read on the device (§12), P4b folded into P5; P5a and P5b implemented (`## Implemented`), D7 settled "both" by the user** — the 2026-10-28
La Salis → Le Village Englouti run (§9), *"a bad/invalid bend around 43.5393, 7.1020 — assess, explain, plan remediation"* · **Order:** the user's word of 2026-10-27, *"plan,
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
| P0 | the harness takes its pair from the command line (§9), and replays a device line | `RouteBendHarnessTest`, `app/build.gradle.kts` (the `-D` forwarding), `RouteApproachPass` (`slowSkipped=`) | instrument |
| P4 | an S standing for one turn is merged at its crossing (§9) | `RouteApproachPass`, `RouteCornerPass` (`MIN_TURN_DEG`, `linesCrossing`) | moves lines |
| P5a | one evaluation and one pattern search for every window (§13 R3) | `RouteApproachPass` | refactor, no line moves |
| P5b | an S's crossing slides along its own legs (§12) | `RouteApproachPass` | moves lines |

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

**P0 — the harness routes any pair.**
- `RouteBendHarnessTest` reads `-Dmaro.harness.start=lat,lon` and `-Dmaro.harness.aim=lat,lon` (the Lérins → La Salis
  pair where neither is set); each key is forwarded by name to the test JVM in `app/build.gradle.kts` as every `maro.*`
  key is. The `APPROACH` summary line it already prints gains `slowSkipped=` (windows the slow-water gate closed), a
  count only, so the device reads it too; the pass's decisions are unchanged. Every rung runs, as today.
- Its first run is §9's pair, to confirm the gate reading (§9); its second is the **baseline** (§10): both pairs, every
  rung, the report kept beside the build so P4's run is read against it. The best-of-REPEATS pick keeps the run with
  the lowest `rungMs`, so D5 reads `rungMs`, not `finePassMs`, or the pick is made on `fineMs`.

**P4 — an S standing for one turn is merged at its crossing.**
- **The case:** two inner points of a three-span window turning opposite ways, both kept for `PRICE` — each chord that
  skips one of them charged by a feature the *other* leg clears — with no slow water on the window's legs. One point
  where the two outer legs cross stands for both (§9: 2 630 m against 2 737 m), and no move reaches it today: the slow
  gate opens no window on open-water legs, the sweep's window closes on a turn over 45°, the merge's pattern search
  starts at the slow-side inner point and its seed route runs through the feature the other point avoids.
- **Where:** `RouteApproachPass.refine`, as the window-eligibility step P1's and P3's reviews left named — the slow gate,
  the land-turn rule and this read in one place, before any candidate. A window qualifies where the slow gate opens it
  (today), **or** where its span is 3, its two inner turns have opposite signs and each is at least **10°** — the
  corner pass's `MIN_TURN_DEG`, made `internal` for this one read (the corner pass's own answer unchanged) — and
  neither inner point is a land turn (`landTurn`, the proof P1 already pays): an **S window**. The turn is
  `RouteCornerPass.turnDegreesAt`, **positive left, negative right**; §9's prose writes turns clockwise-positive, so
  a fixture reads the code's sign, not the prose's. The land-turn rule stands unchanged: an S holding a land turn is
  skipped as every merge is.
- **The candidate:** the crossing of the window's outer legs — `a→b` and `d→c`, each extended — in the frame
  `mergeCrossing` uses, accepted wherever the two lines cross. `mergeCrossing` itself never reaches an S: its sign
  test (`turnAtI * turnAtJ < 0`) refuses opposite turns before the frame, which is the corner pass's intent and stays.
  The crossing is evaluated as any merge candidate — `anyOutside`, then `candidateSec` (price first, the travel bound
  first of all, proof only where it wins) or `replacementSec` where a fixture asks the proof-first order — so the
  six-water equivalence covers it. A crossing standing behind `a` or beyond `d` enters the same evaluation and loses
  or wins on the one cost; nothing refuses it by geometry.
- **The search — after today's, never ahead of it (§11 R1).** The crossing is evaluated **after** the slide's and the
  split's searches, against `min(oldSec, bestSec)` — the lower of the window's own cost and the best those searches
  found — and replaces the window's answer only where it is strictly cheaper. `searchSlide` re-centres on every
  winner, so a crossing evaluated first would move its centre and its bounds and the window could answer a different
  line; evaluated last, today's loop runs bit for bit as today and the crossing is a pure add-on. The cost: an S
  window the slow gate closes pays the two land proofs and one evaluation, nothing else (no slide, no split — those
  are the slow gate's); an S window the slow gate opens pays one more evaluation after its search. The crossing never
  seeds the pattern search: that is P4b's, held until P4a's reading (D4), for a crossing that lost by metres of priced
  water — on §9's pair the leg `X→i4` passes 15–74 m from the zone marks (§9), so that margin is the one to expect.
- **The move's line** reads `kind=merge` as today, each replaced point `PRICE`; the summary gains `sWindows=` (S windows
  opened) and `crossings=` (crossing candidates evaluated), inside `candidates` and `evals`.
- **It ends:** the move is a strict drop of the one cost, like every move; the second pass searches the window the
  crossing made only where the slow gate or the S read opens it; F1's repeat key holds, the window's points its own.
- **Tests:**
  - A fixture with a band disc grazed at a leg's departure and a slow disc at the far corner, the handed line an S of two
    price turns between them on open water: today's pass leaves it (`sWindows` absent); P4 merges it at the crossing,
    the line shorter and clear, the move line `kind=merge replaced=[…:PRICE …:PRICE]`.
  - The same S with the slow disc widened so the crossing's second leg enters it: the crossing loses on price and the
    line keeps its S (`crossings=1`, no move).
  - An S whose first inner point is a land turn: the window is skipped (`landTurnWindows`), the S kept.
  - The six-water equivalence (`pricingFirstAnswersTheProofFirstLine`, both orders, the crossing among the asserted
    paths), `aMergeNeverDeletesALandTurn`,
    `aTurnStandingForTwoFeaturesIsSplitInTwo` and `movesTheSlowCrossingAndRemovesTheOpenWaterBendTogether` hold; the
    engine fixtures' pinned points re-recorded only where they move, each with its reason.
- **Device acceptance, the user's:** on the La Salis → Le Village Englouti pair, "Balanced" turns once between La
  Fourmigue's band and the 10 kn zone's corner — no 64° / 33° pair at 43.540, 7.103 in its `CORNER` list, its `LINE`
  about 100 m shorter and no longer the ladder's longest and slowest (today 11 997 m / 1 031 s against "Around"'s
  11 905 m / 1 021 s) — and the three rungs' `APPROACH ms` read beside `sWindows=` (D4).

**P5 — an S's corner slides along its own legs.** The figures are §12's; this block points at them.
- **The case (§12):** an S merged at its crossing X leaves one corner where the two old legs crossed, not where the turn
  belongs: on the device pair the best single turn stands 204 m from the rock's turn `a`, at La Fourmigue's band edge,
  and X is a 31° bend at sea 1.1 km further out. Nothing reaches it: the slow gate (`hasSlowWater` — a band or zone price
  at a leg's ends or on its 20 m lattice) finds none on X's two legs, the sweep ran before the merge and would keep X
  (the chord `a→e` priced for the band at `a`), and the straightener only drops points within 10 m.
- **The premise — the right corner stands on one of the crossing's own legs:** the best turn lies 203.8 m along `a→X`
  and **2.7 m** off it, on the side away from the band (§12). The outer leg a feature pins at its far end (here the band
  at `a`) is right; the corner is wrong only by where along it the turn is made — and the best point *on* the leg costs
  within a fraction of a second of that 2.7 m-off optimum (about 0.4 m of travel). Where neither outer leg is pinned so,
  the search finds the best of two lines, not the best point (§6).

**P5a — one evaluation and one pattern search for every window (first, a refactor).** The window loop's searches are
the slide (eight directions round a point), the split (eight directions in the plane of two cuts) and now a slide along a
leg; each repeats the halving loop and the `incumbentFirst ? candidateSec : replacementSec` switch, which P4a's review
found written three times and which P5b would make four (AGENTS §1).
- One `evaluate(route, incumbent, proofs)` holds the switch; one pattern search takes its centre, its per-axis steps,
  its direction set, its floor and a `wins` read, and runs the halving loop — the slide calls it with its eight compass
  offsets and its radius bound, the split with its eight `(dP, dQ)` directions, P5b with the two directions of a leg.
- **No line moves:** every decision and every count is the same, pinned by the whole suite, the six-water equivalence
  and the harness on both device pairs with every `LINE`, `APPROACH move`, `SWEEP` and summary line identical to P4a's
  (the summary's clocks aside). A diff there is a defect in P5a, not a re-record.
- `refine`'s eligibility block (the slow gate, the S read, the land-turn rule, the repeat key) moves into one function
  answering skip / search / crossing-only, and a window's answer — slide, split, crossing, slid crossing — is one value
  rather than a mutable `pivot`/`newSec`: the extraction P1, P3 and P4a each named.

**P5b — the crossing slides along its legs.**
- **Where and when:** in every S window whose crossing was offered — **won or lost** (D7) — after the crossing's own
  evaluation, against the window's best so far: `min(oldSec, the merge search's best)`, then the crossing's cost where
  it won. The window's own search and the crossing's evaluation are untouched (R1 by construction); the slide adds
  candidates after them, so a window's answer moves only where a slid point is strictly cheaper. A span-3 window never
  splits, so the chain is: the window's cost, the merge search, the crossing, the slid crossing.
- **Won or lost (D7, recommended "both"):** sliding only a crossing that won leaves the near miss — a crossing whose
  second leg grazes a zone by metres (§9: 15 m on the device pair) loses, P4a does not merge, and a corner on its leg,
  both legs clear, still wins by the same 5.7 s. Sliding every offered crossing reaches it, and **retires P4b**, whose
  trigger was exactly that case. Its price: the slide runs in 1–2 S windows a rung (§12) instead of the 0–1 whose
  crossing won.
- **The search:** candidates `P(t) = a + t·(X − a)` on the incoming leg, then `Q(u) = e + u·(X − e)` on the outgoing
  one, `t, u` in `(0, 1)`, the route `a→P→e` or `a→Q→e`; P5a's pattern search in one dimension from `t = 1` (X), steps
  `|leg|/2` halving down to the pass's step (20 m), **not capped by `MAX_SCALES`**, so a leg of any length in the corridor
  reaches the step (6 scales on §12's 1.3 km legs, 9 on a 10 km one). A candidate off its leg is refused before any read,
  as the split's is. **At most 12 candidates a leg, 24 a window** (11 a leg in practice, the first scale's far side being
  off the leg); the incoming leg is searched first, so the outgoing leg's candidates mostly fall to the travel bound.
- **Evaluation:** each candidate a two-leg route through P5a's `evaluate` — the travel bound, the leg bound, the full
  price, the proof only where it would win — so both orders decide alike. `a→P` lies on a proved leg yet is proved on its
  own lattice; no candidate earns a waiver.
- **The move's line** stays `kind=merge`, its `at=` the slid point; the summary gains `crossingSlides=` (candidates
  evaluated, inside `candidates` and `evals`) and `slidCrossings=` (moves whose point a slide placed).
- **It ends:** one move per window, a strict drop of the one cost, no window of its own; the second pass reads the slid
  point like any other — on §12's pair `[a, P, e, i5]` is again an S, its crossing far ahead (§12), cut on travel.
- **What the corner pass then draws:** the slid corner turns the same way as `a`, 204 m before it, for about 120° in all —
  inside the 619 m a pace-speed curve needs — so `compoundLine` replaces the pair with their outer legs' crossing, about
  72 m north of `a`, **where its walls allow**. If they refuse it, the 204 m leg is shared about 181 m to `a` and 23 m to
  P: a tighter bow at the rock (about 19 kn against today's 24) and a micro-curve at P — the shape `compoundLine` exists
  to avoid, read on the device before acceptance.
- **Tests:**
  - A fixture where the crossing wins and a corner on the **incoming** leg is cheaper: the move's point on that leg —
    read in the pass's own parametrisation (`turnDegreesAt(a, P, X)` within 1e-6°, P between `a` and X) — cheaper than
    X, `slidCrossings=1`, both orders, both skip modes, the pair the same armed from the other end.
  - The same on the **outgoing** leg: `refine` runs the line in its canonical direction, so the fixture places its
    pinning feature at the canonical end's side, not by reversing the first.
  - A near miss: the crossing's second leg priced, a corner on its incoming leg clear and cheaper — the window merges at
    that corner although the crossing lost (D7's case).
  - `anSStandingForOneTurnIsMergedAtItsCrossing` re-read: it asserts the merge, its point on one of X's legs (X itself
    where no slide wins), the line no dearer than the crossing's, and says whether the slide moved it.
  - `aSlowOpenedSKeepsItsSearchsAnswerWhereItsCrossingLoses` re-read under D7: its line stays the search's own unless
    `slidCrossings` names a move, which it then asserts strictly cheaper.
  - The six-water equivalence gains lines whose crossing wins (the S fixtures' line in their own water) and asserts
    `crossingSlides` runs in both orders; the whole suite; the harness on both device pairs, every line identical to
    P5a's except where `slidCrossings` names a move; the device line replayed.
- **Device acceptance, the user's:** on La Salis → Le Village Englouti, "Balanced" draws no bend at 43.5407, 7.0986: its
  turn off La Fourmigue is drawn into the rock's corner (or stands at the band's edge if the corner pass refuses the
  compound), the `APPROACH move` naming the slid point; the drawn `LINE`'s time is read against 10:28's 1 025.0 s, the
  approach pass's own saving being about 5.7 s (§12); the rungs' `APPROACH ms` beside `crossingSlides=` (D6).

## 4. Decision on P1's reading

- **D1 — P2's go, and which points slide.** Go if the restored turns stand far enough off what they round to
  matter on the drawn line; the points are the extremities of a pulled stretch — the two bounding a long
  open-water leg (5.6 km on this pair) — every point only if few qualify.
  - **Read 2026-10-27 (§8):** "Closest"'s restored tip turn was slid twice by today's two-span window (3.407 s, then
    0.235 s) and the user's run named no bend on it; the log gives no distance to the margin. P2 stays deferred.
- **D2 — P3's cost, the user's.** The split adds up to 49 evaluations of three legs to each two-span window; on
  "Balanced" today the pass searched 11 windows of every span at about 12 ms an evaluation (5 761.7 ms, 491
  evaluations). Its device reading (`ms`, `splitEvals`) decides whether the bend's fix is worth that time.
- **D5 — the baseline's verdict, the user's.** P4 ships only where the Lérins → La Salis lines are identical to the
  baseline or every move is a strictly cheaper line named by its move line, and the harness's `finePassMs` on both
  pairs stays within its run-to-run noise (REPEATS = 5, best of).
- **D6 — P5b's cost, the user's.** Each leg reads about `2·⌊log2(leg / step)⌋ − 1` points — 11 on §12's 1.3 km legs, 15 on
  a 6.5 km one, those outside the corridor unread — about 3 ms each on the device (§12). A crossing that won reads
  few in full, its leg's best already low; one that lost reads more, each leg's search following its own best from
  nothing (`## Implemented`). The device's `APPROACH ms` beside `crossingSlides=` decides it.
- **D7 — which crossings slide, settled "both" by the user on 2026-10-28.** "Both" (recommended): every offered crossing slides, the near miss is
  reached and P4b is retired. "Won only": the slide runs only where the crossing won — fewer reads, the near miss left,
  P4b kept held.
- **D4 — P4's cost and P4b's go, the user's** (P4b folded into P5b under D7's "both"). P4a adds one evaluation per S window; the device's `APPROACH ms`
  beside `sWindows=` on §9's three rungs decides it, and P4b goes only where a crossing lost by metres of priced water.
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
  P3 does not reach it. **Since 2026-10-28 P4's (§9)**, for the case of two price turns standing for one.
- Raising the sweep's window turn limit so its chord spans the S — the chord `a→d` is charged by the band the first
  inner point avoids, so the sweep's price proof refuses it whatever the limit.
- **Stopping the pull early** — a lookahead at each commit (try anchors short of the last passing point and keep the
  one whose next chord reaches further): it adds chord proofs inside the pull's own loop, the fine pass's hottest
  read (`PULL clearMs=186 priceMs=305` on this rung, both directions' total, plus the fine pass's two further pulls
  whose time is not logged), and it re-decides every
  waypoint of every line, so each engine fixture and both device pairs would move. The approach pass's window is
  the bounded place for the same decision, after the line is settled.
- **Sliding every merge's point, or every price turn's** — a chord-based slow gate (open a window where the chord
  skipping a point is priced, not only where a leg is) would cure this and the S at once, but opens a window at every
  price turn of every line, the pass's whole cost again (§12's rung closed 32 windows to search 6); P5 slides only the
  crossings P4a offers.
- **Seeding the 2-D slide (`searchSlide`) at X** — it could take a radius of `2·|aX|`; its finest step there is 41 m (about
  0.12 s here), but it reads 48 candidates for P5b's 24, and its coarse steps west and south of X land in the band. P5a's
  one pattern search with a leg's two directions is that alternative, at a leg's cost.
- **Marking X so the second pass searches `[a, X, e]` as a slide** — the 2-D slide and the split, up to 48 + 49 three-leg
  evaluations, about four times P5b's.
- Merging opposite turns in the corner pass's `compoundLine` — an S is "a turn the line means" there, and the
  crossing is new water that pass prices nowhere; the approach pass is the layer that prices a move.

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

**P4's:** a third special case in the window loop — the slow gate, the land-turn rule, now the S read — each treating
one shape the pull leaves by committing the grid path's own point at a refused chord; a pull that placed the vertex at
the band's tangent would cure every shape at once, and §5 keeps calling that a rewrite. The crossing is the right
answer only where the outer legs' bearings are the features' own (§9's first leg is pinned by the band at its
departure, so there it is); elsewhere the candidate lands in priced water and loses — harmless, but a read paid for
nothing, on every zigzag the pull leaves. And eligibility is read from the current line, which the move before it
can change, as P1's is.

**P5's:** P5a moves every search through new code before P5b moves a line, so a slip there moves lines the plan says
it never touches — the harness's identical-lines check is the guard, and it reads only two device pairs. And it is
the third patch on the same root — the pass decides where to look from the legs the line uses, while
what pins a point lies on the chord that skips it — and each patch cures only the shape that was seen: P5 slides the
point P4a made and no other, so a point the pull itself leaves at sea between a band and a corner, on clean legs and
with no S around it, is still out of reach. And its premise — the right corner stands on one of the merged corner's
own legs — holds where one outer leg is pinned at its far end; where neither is, the 1-D search finds the best of two
lines, not the best point.

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

## 9. Evidence — the 2026-10-28 device run (La Salis → Le Village Englouti, 30 kn, `evolutive`)

The log read off the Pixel at 08:43 (`MaroRoute`, the three rungs on three threads; "Balanced" λ 3 on tid 1484); the
user's complaint names a bad bend at 43.5393, 7.1020.

- **The pair:** start 43.57061, 7.13144 (La Salis, coast 41 m) to the aim 43.54038, 7.08260 — the user's "Le Village
  Englouti" marker (13 m off), beside "La Fourmigue" (101 m, `proximityOverrideM=425`), the rock the `REPAIR` line's
  `coast=78.9m` reads. No marker charges anything near the bend (the "What is there?" pin at 43.54133, 7.10159 stands
  125 m off the leg and prices nothing in the log); `LINE` 11 997.3 m, 1 031.1 s, `zoneShare=0.00`, `bandMetres=724 m`.
- **The fine line round the complaint** (14 points swept to 13, aim end first; the approach pass then slid `i1` about
  30 m): `i1` 43.54328, 7.08285 kept for `LAND` (99°, the chord from the aim crossing the rock) · `i2` 43.53999,
  7.10292 kept for `PRICE`, 64° right — the chord `i1→i3` charged 80 s by the **band** at 43.54281, 7.08443, La
  Fourmigue's, its marks 23–41 m off the leg `i1→i2` · `i3` 43.53698, 7.10395 kept for `PRICE`, 33° left — the
  chord `i2→i4` charged 45 s by the 10 kn **zone**, the chord inside it over its last 600–940 m, its marks
  converging on `i4` · `i4` 43.53261, 7.11034 kept for `PRICE`, 41° left (zone, 640 s). The legs: `i1→i2` 1 681 m
  at 102°, `i2→i3` 345 m at 166°, `i3→i4` 709 m at 133° — all three at pure travel (`pathSec` 129.8 and 68.2 s are
  the metres at 30 kn), so unpriced: a right turn then a left one 345 m later, an S in open water. Turns here are
  written clockwise-positive; `turnDegreesAt` signs them the other way (§3 P4).
- **The drawn bend is that S, drawn as the corner pass means to.** `CORNER idx=2` 64.1° `ROUNDED`, `room=234.5 m`
  (its share of the 345 m leg), 29.05 kn — a radius of about 335 m whose apex stands 60 m inside the vertex at
  bearing 224°, about 43.5395, 7.1023; `idx=3` 32.8°, `room=110.4 m`, 27.28 kn. The two bows take the whole leg
  (234.5 + 110.4 = 344.9 m) and meet as one continuous S; neither is in a zone or the band. The user's point stands
  107 m from `i2` at bearing 224°: it is `i2`'s bow. Nothing the corner pass did is wrong; it drew the line it was
  handed, and `compoundLine` merges only same-way pairs by design.
- **Two turns standing for one.** The outer legs cross at X = 43.54067, 7.09856 — on the leg `i1→i2` itself, 360 m
  before `i2` — and `i1→X→i4` is 2 630 m against 2 737 m through the S (−107 m, about 7 s at 30 kn), one turn of 31°.
  `i1→X` is a prefix of a leg the pull accepted, so it stands to the band exactly as that leg does; `X→i4` is the
  leg `i3→i4` extended (133.3° both), so it passes the zone's marks as that leg does — 15 m at the nearest
  (43.53318, 7.10977), 74 m at the corner mark — unpriced by metres, by the log's marks, not read against the
  polygon (P0 reads it).
- **Why it stands — the slow gate, settled by the log (§11 R2).** The pull commits the grid path's own point at the
  first refused chord — from `i1` the band at the departure refused the chord, so `i2`; from `i2` the zone, so `i3`.
  The sweep drops one point at a time (`i1→i3` band, `i2→i4` zone, each refused) and opens a longer window only over
  turns under 45° (`i2` turns 64°). The approach pass opens a window only where exactly one of its end legs holds
  slow water (`firstSlow != lastSlow`), and here none of `i1→i2`, `i2→i3`, `i3→i4` does: the three legs price at
  pure travel; `landTurnWindows=4` can only be `from=0`'s span 3 and 4 in both passes (the line's one `LAND` chord
  is `aim→i2`), which needs `slow(i2,i3)` and `slow(i3,i4)` false; and the `from=0` slide moved `i1` 29.6 m, a step
  reachable only under the aim-side radius (646 m: 40.4 then 20.2 m), so `slow(aim,i1)` is true and `slow(i1,i2)`
  false. The window `[i1,i2,i3,i4]` was never opened; nothing was searched and lost. Had it opened, no candidate
  would have been the crossing either: a merge seeds at the slow-side inner point and its seed route `i1→i2→i4`
  runs through the zone, the pattern search halving from 1.7 km around `i2` with X 360 m west-north-west of it. §5
  named this case out of P3's reach on 2026-10-27; this run is its first device instance.
- **The other two rungs draw the line "Balanced" should.** "Around" (λ 5) turns at the band's edge, 43.54328, 7.08409
  (45°), and runs one swept leg of about 2 425 m straight to `i4` (26.8°); "Closest" (λ 1) the same from 43.54272,
  7.08512 (16.4°), about 2 322 m — no S, and both legs price at pure travel, so the water between the band's edge and
  the zone's corner is clear and unpriced (the 2 777 m and 2 520 m `CORNER` legs start at the corner pass's
  `compoundLine` crossings, not at the swept points). Their `LINE`s: 11 905.1 m / 1 021.3 s and 11 803.2 m /
  1 021.9 s against "Balanced"'s 11 997.3 m / 1 031.1 s. Against "Closest" the S carries most of the gap (`aim→i4`
  3 042 m against 2 878 m on the swept points; `LINE` +194 m, +9.2 s); against "Around" it does not — "Around"'s
  swept stretch `aim→i5` is 263 m *longer* than "Balanced"'s and its drawn line wins on a 140° compound corner bowed
  off over 528 m and on its La Salis end. λ 3's coarse path (A\*) bent where the others' did not; a crossing merge
  takes about 107 m off "Balanced".

## 10. P4 — regression and performance guards

**Regression — what may not move, and how it is read.**
- **No decision today's pass makes changes.** The S read only *adds* windows and one candidate; the slow gate, the
  land-turn rule, the repeat key, the slide, the split, the pattern search and both evaluation orders keep their code
  paths. A window the slow gate opens today is searched exactly as today **because the crossing is read after that
  search** (§3, §11 R1) against the lower of the window's cost and the search's best; a line moves only where the
  pass's own measure says the crossing is strictly cheaper. An S window searched without a move joins `unmoved` as
  every window does, so the second pass does not re-read it; a crossing move can leave a new S (on §9's pair
  `[i1', X, i4, i5]`, its crossing far ahead, §12), which `cutTravel` ends before any read,
  and the pass count (`MAX_PASSES = 2`), one move per `from` and the strict drop bound it in any case.
- **The pull, the sweep, the straightener and the corner pass are untouched** — their fixtures stand as the proof.
- **Orientation:** the crossing of two lines is the same read from either end, and the pass runs in the pair's
  canonical direction, so `DirectionSymmetryRepairsTest` holds unchanged and `RouteDirectionSymmetryTest` too — the
  latter runs only under `-Dmaro.prebake=true`, so the acceptance run passes that flag.
- **The land-turn rule holds:** an S window holding a land turn is skipped, pinned by its own fixture (P4's third test).
- **The engine fixtures** (`AvoidPriceWalkTest`, `RouteZonePhase4Test`, the approach suite) are run whole; a pinned
  point is re-recorded only where a line moves, each with its move line as the reason — a moved line with no
  `kind=merge … PRICE … PRICE` behind it is a defect, not a re-record.
- **The two device pairs on the harness (P0), before and after:** Lérins → La Salis identical on every rung (no
  price-pinned S there since P3), La Salis → Le Village Englouti with "Balanced"'s S merged and the other two rungs
  identical — D5. Then the device, D4.
- **Where it can still bite:** a zigzag of price turns along a coast whose crossing stands in the band — the
  candidate loses on price, the line stands, a read is spent; and the single turn at the crossing is always flatter
  than the sharper of the pair (`|θ1| − |θ2|`, 31° here), so the corner pass is handed less to round, never more.
  The bowed single corner stands inside its turn, on the band's side of `i1→X`; the corner pass's walls read it as
  they read every bow.

**Performance — the budget and where the reads go.**
- **Reads ordered cheapest first:** the two turns (pure geometry), the crossing (pure geometry), the travel bound
  (`candidateSec`'s first cut: a crossing route no shorter than the window's legs is `cutTravel` before any read),
  then the land proofs (memoised per chord, the ones a slow-opened merge pays today), then two priced legs, then the
  proof only where the candidate wins.
- **The count is small by construction:** a line of 13 points holds about ten span-3 windows, of which only those whose
  inner turns have opposite signs and both stand at or above the corner pass's `MIN_TURN_DEG` are S windows — on
  §9's rung, one (`i2` right, `i3` left; every other adjacent pair turns the same way). At the device's ~2.8 ms an
  evaluation (`priceMs=951 + clearMs=90 / evals=375`), a winning crossing's two-leg proof about 3 ms more, an S
  window costs under 10 ms on a pass of 1 082 ms; no cap is needed, and `sWindows=` / `crossings=` on the summary
  make the count visible.
- **No new reads in the hot loops:** nothing changes in the pull (`PULL ms=494` here), the sweep or the corner pass;
  the second pass re-opens an S window only where its points changed (the repeat key).
- **Measured, not assumed:** the harness's `finePassMs` on both pairs against the baseline (D5), then the device's
  `APPROACH ms` per rung beside `sWindows=` (D4); P4b's pattern search is held until that reading says a crossing lost
  by metres of priced water.

## 11. Review — one independent read, 2026-10-28, and what it changed

The hop read the 2026-10-28 additions (P0, P4, D4, D5, §5, §6, §9, §10) against `RouteApproachPass`, `MultipassPull`,
`RouteCornerPass`, `RouteFinePass`, the harness, the approach suite, `app/build.gradle.kts` and the three rungs' logs,
briefed to break each claim. Its findings and the plan's disposition:

| # | Finding | Disposition |
|---|---|---|
| R1 | **Blocks.** "Searched exactly as today" is false with the crossing *ahead* of the pattern search: `searchSlide` re-centres on every winner, so a crossing beating the seed but not the final best moves the centre and the window can answer another line; the incumbent was unstated (`oldSec` vs `bestSec`). | The crossing is evaluated **after** the slide and split, against `min(oldSec, bestSec)`; today's loop bit-identical (§3, §10). |
| R2 | **Should fix.** The log does settle the gate: the three legs price at pure travel, `landTurnWindows=4` needs both `from=0` gates open, and the `from=0` slide's 29.6 m step is reachable only under the aim-side radius — so `slow(i1,i2)` is false and the window never opened. | §9 states the gate as the one reason; the seed-route sentence kept as a counterfactual. |
| R3 | **Should fix.** `mergeCrossing`'s `|s|, |t| ≤ 1` does not refuse an S (s ≈ −0.22, t ≈ −0.86 in its own frame); its sign test does, before the frame. | The sentence dropped; the sign test named (§3). |
| R4 | **Should fix.** §3 had no magnitude floor while §10 had `MIN_TURN_DEG`, which is `private`; `turnDegreesAt` is positive-left while §9 wrote turns clockwise-positive. | One floor (10°, `MIN_TURN_DEG` made `internal`, named in Touches); the sign convention stated once (§3, §9). |
| R5 | **Should fix.** The band marks are 23–41 m off `i1→i2`, not 5 m; the zone marks converge on `i4`, the nearest 15 m off `i3→i4`; `X→i4` is that leg extended, so 15–74 m, not 74–222 m. | Figures corrected (§9); P4b's trigger expects that margin (§3). |
| R6 | **Should fix.** `PULL clearMs/priceMs` already sum both directions; the fine pass runs two further pulls unlogged. | §5's bullet reworded. |
| R7 | **Should fix.** The harness already prints the `APPROACH` summary; the `-D` keys need forwarding in `build.gradle.kts`; `slowSkipped=` touches the pass; best-of picks on `rungMs`, so `finePassMs` is not a best-of. | P0 rewritten; Touches extended; D5 reads `rungMs` or the pick moves to `fineMs`. |
| R8 | **Note.** "The S makes Balanced the longest and slowest" overclaims against "Around" (whose swept stretch is 263 m longer and wins on a bowed 140° corner and its La Salis end); against "Closest" the S carries most of the gap. | §9 reworded to the measured claims. |
| R9 | **Note.** The 2 777 m / 2 520 m legs are the corner pass's, from `compoundLine` crossings; the swept legs are ~2 425 m / ~2 322 m. | §9 corrected. |
| R10 | **Note.** `candidateSec` prices "up to two" legs (`cutLeg` between them); "a crossing behind `a` loses on travel" was asserted, not shown. | §3: the crossing enters the normal evaluation, nothing refuses it by geometry. |
| R11 | **Note.** `|θ1| − |θ2|`, ten windows and exactly one S window on this line confirmed; the merged line is itself an S whose outer legs cross 3.7 km beyond `i5` (`cutTravel`); an S window searched without a move must join `unmoved`. | §10 carries the termination reading. |
| R12 | **Note.** Per-evaluation cost is ~2.8 ms with the clearance clock; a winning proof ~3 ms; under 10 ms holds. Pass 2 is four times pass 1 on this rung (`passMs=204.9/876.9`). | §10 figures corrected; the pass-2 cost noted for D4. |
| R13 | **Note.** Every test name exists; the six-water equivalence is `pricingFirstAnswersTheProofFirstLine`; `RouteDirectionSymmetryTest` runs only under `-Dmaro.prebake=true`. | Named (§3, §10). |
| R14 | **Note.** `i1` slid ~30 m, not 13; its sweep turn is 99.1° (103° is the slid corner); `i1→i2` is 1 658 m before the slide. | §9 corrected. |

**Confirmed by the hop:** the slow gate, the seed and radius, the scale loop, span 4 before 3 and the first-move
`break`, a move only below `oldSec`; the sweep's 45° window gate and the straightener's 10 m; the pull's commit at
`path[probe − 1]`; `mergeCrossing`'s sign test; the stage order; the canonical orientation; X at 43.54067, 7.09856 on
`i1→i2` 360 m before `i2`, 107 m saved, 31° net; the corner rooms 234.5 + 110.4 m; the `LINE` figures of all three
rungs; `sWindows=` / `crossings=` fitting the tally; the P4 lines consistent with P1/P3's. **Code health, named:**
`refine` carries three test switches, two chord caches and the slow, land and repeat gates inline — P4's read is the
third eligibility rule, and the window-eligibility step P1 and P3 left named is where all three go before a fourth.

## 12. Evidence — P4a's device run (2026-10-28 10:28, La Salis → Le Village Englouti, 30 kn, `evolutive`)

- **P4a's acceptance: met.** "Balanced" (λ 3) merges the S in the first pass, `kind=merge replaced=[43.53999,7.10292:PRICE
  43.53698,7.10395:PRICE] at=43.54067,7.09855 savedSec=6.963` — §9's crossing, both points named `PRICE` as on the
  device's own water (the desktop read the second `NONE`). `LINE` 11 934.1 m / 1 025.0 s against 08:43's 11 997.3 m /
  1 031.1 s (−63 m, −6.1 s). The summary: `sWindows=2 crossings=2 evals=375`.
- **The bend left: the crossing itself.** `CORNER idx=2 at=43.54067,7.09855 turn=31.3deg`, in open water 1.3 km from the
  rock's turn `a` (43.54315, 7.08253, 103.1°) and 1.3 km from the zone corner `e` (43.53261, 7.11034, 40.8°).
- **Where the turn belongs (desktop probe, the shipped bake and markers, λ 3 and λ 5 alike; the figures are the
  probe's, not the device's):** every single turn between `a` and `e` on a 20 m lattice, priced and proved — the best at
  43.54279, 7.08501, 203.8 m along `a→X` and **2.7 m** off it (bearing 101.3° against the leg's 102.05°), 1 117 m from
  X; `a→P` and `P→e` both clear and at pure travel; `a→P→e` 164.48 s against `a→X→e` 170.19 s (−5.7 s, 88 m). The
  chord `a→e` is clear but priced 404.45 s against 163.90 s of travel — the band at `a` — so a turn there is needed, at
  the band's edge.
- **Why nothing moved X:** the slow gate (`hasSlowWater`) finds no band or zone price on either leg of `[a, X, e]` — the
  probe prices both at pure travel, and the device's `sWindows=2` with §11 R2's reading agrees — so it closes the
  window; the sweep ran before the merge and would keep X (the chord `a→e` priced); the second crossing the summary
  counts is the S `[a, X, e, i5]` (X turns right, `e` left; every later bend left), whose outer legs `a→X` (102.05°)
  and `e→i5` (92.5°) cross about 6.5 km along `a→X`, some 2 km beyond `i5` — a route of about 8.6 km, 555 s of travel,
  against a window of about 299 s, cut on travel (`cutTravel=1`). With P in place of X the window is again an S, its
  crossing as far ahead.
- **Cost:** the rungs' `firstMs` 4.3–4.5 s against 08:43's 3.2–3.4 s, but the stages P4a never touches slowed as much
  (the coarse search +67–86 %, the first pull +46–60 %) while the approach pass grew 11–13 % on the same evaluations
  (375 against 375 on "Balanced", +1 and +2 on the others): the first search after the install, on a cold tile store —
  the shared fine grid built in 3.7 s (`TILE built` and `STORE save` lines) against 19 ms at 08:43, outside the rungs'
  `firstMs`. P4a's own share is its 1–2 crossing evaluations a rung and the S windows' land proofs (`landProofs` 7 → 12
  on "Balanced"), each about 3 ms (`(priceMs + clearMs) / evals` = 3.09 ms) — under 30 ms; a second search on the same
  build would show the baseline.

## 13. Review — one independent read of P5, 2026-10-28, and what it changed

The hop read P5's first draft (§3, D6, §5, §6, §12) against `RouteApproachPass`, `RouteCornerPass`, `MultipassPull`, the
approach suite, `maro.properties` and the 08:43 and 10:28 logs, briefed to break each claim. Nothing blocked.

| # | Finding | Disposition |
|---|---|---|
| R1 | **Should fix.** "Only where the crossing has won" is a choice, not R1's need, and leaves the near miss (a crossing losing by metres while a corner on its leg wins); its relation to P4b unstated. | D7, the user's, recommended "both": every offered crossing slides, P4b folded in (§3, §4). |
| R2 | **Should fix.** The six-water lines are slow-opened S windows whose crossing always loses, so `crossingSlides > 0` could not hold there. | Lines whose crossing wins added; the slide asserted in the P5b fixtures too (§3). |
| R3 | **Should fix.** P5 would write the evaluation switch a fourth time, the halving loop a third and a fourth answer shape on mutable locals — the extraction P1, P3 and P4a named. | P5a, a refactor that moves no line, first (§3). |
| R4 | **Should fix.** P stands 2.7 m off `a→X`, not 40 m (that was its distance south of `a`) — the premise holds more strongly. | Corrected (§3, §12). |
| R5 | **Should fix.** The S after the merge has its crossing about 2 km beyond `i5`, not behind `a`; §10 said 3.7 km beyond `i5`. | One statement, in §12; §3 and §10 point there. |
| R6 | **Should fix.** 6 scales on a 1.3 km leg: 12 candidates a leg, 24 a window, not 28; `MAX_SCALES` would stop legs over 2.56 km above the step. | 24; the 1-D loop runs to the step, uncapped (§3, D6). |
| R7 | **Should fix.** The coarse slowdown is 67–86 %; the 3.7 s grid is one shared build on a cold store, outside `firstMs`; P4a's share omits the land proofs (7 → 12). | §12 corrected. |
| R8 | **Note.** 3.09 ms an evaluation; most candidates fall to the travel bound — 6–10 priced, 20–30 ms. | D6 and §3 state it; the incoming leg first. |
| R9 | **Note.** The cost along a leg has one dip; the halving reaches the whole leg and stops within one 20.6 m step of the band's edge. | Confirmed; no change. |
| R10 | **Note.** The slow gate is `hasSlowWater`, not a cost-equals-travel test; "sec = travel" is the probe's. | Named (§3, §12). |
| R11 | **Note.** The compound crossing likely passes its walls (72 m north of `a`); if not, a 19 kn bow at the rock and a micro-curve at P; "LINE 5 s faster" was the approach pass's saving. | The dependency and the fallback in the acceptance (§3). |
| R12 | **Note.** "On the line" needs the pass's own parametrisation; `refine` canonicalises, so the outgoing fixture cannot be the incoming one reversed; the existing test should say whether the slide ran. | Tests rewritten (§3). |
| R13 | **Note.** The probe's figures were repeated in §3, D6 and §12. | §12 holds them; §3 and D6 point there. |
| R14 | **Note.** A span-3 window never splits; only λ 3 merged on §12's pair. | Wording corrected. |
| R15 | **Note.** The 2-D slide's real costs are 48 candidates and band-bound coarse steps, not its radius; marking X costs about four times P5b. | §5 rewritten; P5a's leg-direction search adopted as that alternative. |

**Confirmed by the hop:** the merge line, the `LINE` and summary figures, `CORNER idx=2` 31.3°, the probe's arithmetic
(every leg at pure travel, 170.19 against 164.48 s, |PX| 1 116.6 m), `cutTravel=1` from `[a, X, e, i5]` as pass 2's only
S, R1 and termination, the sweep's and the straightener's reach, the timing ratios, `pairIsOneTurn` true for the slid
pair (pace radius 357.3 m, 619 m needed against 204 m), and the off-leg refusal matching the split's.

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
- **2026-10-28 — P3's device reading (08:39, Lérins → La Salis, 30 kn, `evolutive`): accepted.** "Balanced" splits
  its bend at sea in the first pass (`kind=split replaced=[43.51450,7.09898:PRICE] at=[43.51077,7.07885
  43.53230,7.13609] savedSec=17.756`) and turns by Sainte-Marguerite's east end and the zone's corner off Cap
  d'Antibes, nothing in open water; its `LINE` 15 047.1 m / 1 656.8 s (−94 m, −7.4 s on 23:08), so the ladder
  reads in order — "Closest" 1 650.7 s, "Balanced" 1 656.8 s, "Around" 1 658.4 s. "Closest" keeps its tip and
  corner turns (14 992.6 m). D2: the rungs' `firstMs` 5.8 / 5.9 / 6.9 s against 9.4 / 8.6 / 12.1 s at 23:08 and
  the approach pass 2.2 / 2.4 / 3.3 s against 4.3 / 3.1 / 6.6 s, wave G's coast read in the same build — the
  split's cost is inside the budget (the store served 64 of 64 tiles; the phone's state is not controlled).

- **2026-10-28 — P0 and P4a, on `feature/roooouuutttiiinnngggg`, `#implement`.** The whole unit suite green (1 661
  tests, five new), both direction-symmetry suites green under `-Dmaro.prebake=true`, and `apk-build.bat` green. The
  device reading on the La Salis → Le Village Englouti pair (D4) is the user's.
  - **P0** — `RouteBendHarnessTest` routes any pair (`-Dmaro.harness.start=`, `-Dmaro.harness.aim=`, forwarded by
    `app/build.gradle.kts`, read after the skip gate, a malformed point refused); the `APPROACH` summary carries
    `slowSkipped=`. **Deviation — the replay.** The desktop's own search does not bend where the device's did: on
    the Fourmigue pair its "Balanced" draws the device's "Around" line, no S. So `-Dmaro.harness.replay="<lat>,<lon> …"`
    hands a device's swept line to the approach pass alone, on each rung's grid and at its λ, the setup built as the
    fine pass builds it, printing `REPLAY` lines and a warning where the line's ends are not the harness pair's.
  - **P4a** — `RouteApproachPass.refine` reads each window's eligibility in one block: the slow gate as before, and
    an **S window** (span 3, the two inner turns of opposite sign, each at least `RouteCornerPass.MIN_TURN_DEG`, now
    `internal`), the land-turn rule over both. An S is offered one candidate, the crossing of its outer legs
    (`searchCrossing`), read **after** the window's own slide and split against the lower of the window's cost and
    their best (§11 R1), in both evaluation orders. The line intersection moved out of `mergeCrossing` into
    `RouteCornerPass.crossingOf`/`linesCrossing`, `mergeCrossing`'s arithmetic and frame unchanged. The summary
    carries `sWindows=` (S windows the slow gate or the radius left unsearched) and `crossings=` (every S window's
    crossing — **the count D4 reads**, `sWindows` leaving out an S the slow gate opened).
  - **Measured on the harness** (§10, D5) — on both device pairs, every rung, every `LINE`, `APPROACH move`, `SWEEP`
    and `STRAIGHTEN` line identical to the pre-change baseline; only the summary counts moved (`landTurnWindows` up
    where an S holding a land turn is now read and skipped, `repeats` up where an S window searched without a move is
    skipped by the second pass, `crossings` 0–2 a rung). `rungMs`, `finePassMs` and `APPROACH ms` stayed inside the
    spread two baseline runs already showed (±10 %). **The replay** of the device's swept "Balanced" line merges the S
    at 43.54067, 7.09856 — §9's crossing — `savedSec=6.929`, the line 107 m shorter, the La Salis end's moves as
    before; the harness reads `i3` as `NONE` where the device kept it for `PRICE`, its water at the zone corner not the
    device's, so the replay proves the mechanism and §9's crossing, not the device's prices.
  - **Pinned** — `anSStandingForOneTurnIsMergedAtItsCrossing` (an S on open water merged at its crossing, both orders,
    both skip modes, both directions); `anSWhoseCrossingIsPricedKeepsItsTwoTurns`; `anSHoldingALandTurnIsNotMerged`;
    `aSlowOpenedSKeepsItsSearchsAnswerWhereItsCrossingLoses` (a crossing cheaper than the seed and dearer than the
    search's best, the line recorded from the pre-change build — R1's guard); `onlyAnSOfTwoRealTurnsIsOfferedACrossing`
    (a second turn under the floor, and two same-way turns); four S lines added to
    `pricingFirstAnswersTheProofFirstLine`, asserting a crossing runs. On 76 slow-opened S lines probed, the pre-change
    and new pass drew the same line, the crossing offered and losing each time.
  - **Review hop (2026-10-28)** — nothing blocking; it confirmed the R1 ordering, the counters' identities, the
    repeat key, termination, the orientation and `crossingOf`'s bit-identity. Fixed on its word: R1 pinned (the test
    above), the stale comments (`hasSlowWater`, the six waters' marker water, `turnDegreesAt`'s caller list),
    `sWindows`'s wording, the floor and same-way tests, both directions, the harness's lazy reads and point check, the
    replay's end warning, `linesCrossing`'s "parallel". **Left, named:** the evaluation switch
    (`candidateSec`/`replacementSec` on `incumbentFirst`) is written three times — slide, split, crossing — and wants
    one `evaluate`; `refine`'s eligibility block is in one place but inline, and a window's three answer shapes
    (slide, split, crossing) ride a placeholder `Slide` and a mutable `pivot`/`newSec` — the extraction P1 and P3
    named is now due before a fourth rule.

- **2026-10-28 — P5a and P5b, on `feature/roooouuutttiiinnngggg`, `#implement`, D7 "both".** The whole unit suite green
  (1 665 tests, four new), both direction-symmetry suites green under `-Dmaro.prebake=true`, `apk-build.bat` green. The
  device reading (D6) is the user's.
  - **P5a** — `RouteApproachPass.refine`'s window loop is `offerFor` (the slow gate, the S read, the land-turn rule and
    the repeat key → an `Offer` or none) and `answerFor` (slide → split → crossing → slid crossing → one `Answer`);
    `Search` holds the context and the one evaluation switch (`evaluate`, each call one candidate; `seedSec`), and one
    generic `patternSearch` runs the halving loop for the slide, the split and the legs. `searchCrossing`, `Slide` and
    `Split` are gone. **Shown to move nothing:** with the slide switched off, the whole suite green and the harness on
    both device pairs and the device-line replay identical to P4a in every `LINE`, `APPROACH move`, `SWEEP`,
    `STRAIGHTEN`, `REPLAY` and summary count, clocks aside.
  - **P5b** — `searchAlongLegs`: after an S window's crossing, won or lost, a one-dimensional `patternSearch` along each
    of its legs (toward the window's first point, then its last), from half the leg to the pass's step, no scale cap;
    **each leg's search follows its own best** — seeded with the crossing's cost where it won, `+∞` where it lost — and a
    point is kept only where it beats the window's best. `refine` now requires a positive step, and a leg that is not
    finite is skipped, so the uncapped loop always ends. The summary carries `crossingSlides=` and `slidCrossings=`.
  - **Measured** — the replay of the device's swept "Balanced" line merges the S straight to 43.54276, 7.08503 — the
    desktop probe's best single turn, 43.54279, 7.08501 (§12) — `savedSec=12.689` against P4a's 6.929; the Lérins → La
    Salis pair identical to P5a on every rung (no crossing there); the La Salis → Le Village Englouti pair's own lines
    identical, the slide reading 27/27/46 points a rung and placing none (`evals` 296 → 323 on "Balanced"). `rungMs`,
    `finePassMs` and `APPROACH ms` inside the ±10 % the baseline runs spread over.
  - **Pinned** — `aCrossingSlidesAlongItsIncomingLeg` (the S mirrored so the pass runs it from the rock's end; its slid
    point recorded exactly, so another search order fails it); `aCrossingSlidesAlongItsOutgoingLeg` (the corridor cut
    20 m past the crossing so only the outgoing leg can place the corner); `aCrossingThatLosesStillSlides` (a rock
    refuses the crossing; a corner on one of its legs wins); `aCrossingThatLosesOnPriceIsSearchedPastItsMiddle` (a small
    slow disc prices the crossing and the leg's middle; the corner is found past the middle — shown to fail with each
    leg's search seeded from the window's best); `anSStandingForOneTurnIsMergedAtItsCrossing` re-read (its point on one
    of the crossing's legs, the slide named); `aSlowOpenedSKeepsItsSearchsAnswerWhereItsCrossingLoses` (the slide reads
    and places nothing); the six-water equivalence gains the S fixture's line and its mirror and asserts a slid crossing
    places a move in both orders.
  - **Deviations** — the near-miss fixture refuses the crossing with a rock rather than pricing it, and the priced near
    miss is its own test; the outgoing leg is forced by a corridor cut rather than by water. D6's "at most 24" became the
    log bound above.
  - **Review hop (2026-10-28)** — nothing blocking; P5a and P5b confirmed as planned, the counters' identities and the
    slide's accounting held. Fixed on its word: **the slide stopped at a leg's middle when the crossing lost** — its centre
    moved only on points beating the window, so the far half of a leg was never read (the device's turn stood 1.1 km
    along a 1.3 km leg) — now each leg follows its own best; a losing crossing's cost no longer seeds the legs, which had
    made the two evaluation orders search differently; the termination guard; the equivalence lines; the priced near
    miss; the exact pin; D6; the `candidates` KDoc; §12's story told once, in the class KDoc. **Left, named:** the
    accept-if-cheaper step is written three times (slide, split, legs) with its own best each; an unsearched S still
    starts from a placeholder `Answer` at `+∞`; `Offer` and `Answer` are plain classes; `offerFor`/`answerFor` are local
    functions over `refine`'s state, which `Search` could carry.

- **2026-10-28 — the reviews' code leftovers, on `feature/roooouuutttiiinnngggg`, `#implement`.** The whole unit suite
  green (1 665 tests), `apk-build.bat` green, the harness on both device pairs and the device-line replay identical to
  P5b in every line and count, clocks aside.
  - **The approach pass** — one `Incumbent` (a cost and its answer, taken over only by a strictly cheaper one) holds
    every search's best: the slide's from its seed, the split's from its incumbent, the window's from its own cost, each
    leg's from the crossing's cost or `+∞`; the window's answer is `Search.answerFor`, returning none where nothing beats
    the window, so no placeholder answer remains; `Offer` and `Answer` are data classes; `searchSplit` reads the floor's
    radius off `Search`. `offerFor` stays local to `refine`, reading the line, the repeat set and the slow and land caches
    it owns.
  - **The code default follows the properties** — `AppConfig.routeTurnLateralAccelMps2` defaults to 0.666667, as
    `route.turn.lateralAccelMps2` ships, its KDoc pointing at the key (AGENTS, ONE HOME PER FACT). Four fixtures measured
    at the old 0.33 now say so: `TurnConfigFixture.withLateralAccel` sets the value directly (its setter is `internal`)
    and restores it — `aBendTooRoomTightForTheFloorIsStillCurved`, `aBendAtTheHardBottomCurvesAndAHairLessStands`,
    `aWaivedCornersBowKeepsTheWallPastTheWaiver` and `aTurnRoundOneHeadlandIsNotSplit`; five others' KDoc no longer
    names 0.33. `aWaivedCornersBowKeepsTheWallPastTheWaiver` failed at 0.666667 on its outcome (`BOW_DEPTH` for
    `ROUNDED`) — the corner pass refusing the bow there, not drawing it under the wall — so the pin hides no defect.
  - **Review hop (2026-10-28)** — nothing blocking; the `Incumbent` rewrite confirmed result for result. Fixed on its
    word: the fixture first went through `applyProperties`, which also resets the `path.*` set and two settings seeds — now
    a direct set; the stale 0.33 comments; the seed wording; `Incumbent`'s fields `private set`, a candidate stored as it
    is. **Left, named:** the slide, split and leg searches are object functions taking `Search` while `answerFor` is its
    member. **Contradictions found beside the order, not changed:** `routeTurnTransitionSec` defaults to 2.0 against
    `route.turn.transitionSec=3.333333`, `routeSpeedAccelMps2` to 1.0 against `route.speed.accelMps2=0.666667`, and
    `maro.properties`'s own comments above those keys still call 0.33, 2.5 s and 1.0 the shipped values.
