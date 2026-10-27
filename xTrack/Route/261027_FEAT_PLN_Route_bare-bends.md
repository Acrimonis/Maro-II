<!-- scope: feature -->
# Route — no bend in the middle of nowhere: a merge never deletes a turn the land forces

**Date:** 2026-10-27 · **Status:** P1 implemented (`## Implemented`); P2 deferred to P1's device reading · **Order:** the user's word of 2026-10-27, *"plan,
review plan, update, report"*, on the "Closest" complaint (*"again a bend in the middle of nowhere doesn't make
sense"*) and the discussion that followed: tweak the extremities of the pulled stretch — east of
Sainte-Marguerite and at the 10 kn zone's corner — rather than replace them.

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

## 4. Decision on P1's reading

- **D1 — P2's go, and which points slide.** Go if the restored turns stand far enough off what they round to
  matter on the drawn line; the points are the extremities of a pulled stretch — the two bounding a long
  open-water leg (5.6 km on this pair) — every point only if few qualify.

## 5. Not in this plan

- **The La Salis hook** — the corner pass's `compoundLine` crossing (§1); the corner pass's plan
  ([`smooth-corners`](261020_FEAT_PLN_Route_smooth-corners.md)) is its home.
- A point-support rule ("a turn must rest on something") — withdrawn on review (§7).
- Two points per window — the search squares (48 × 48 candidates a window).
- Re-solving a stretch on the fine grid in place of the pass — a larger rewrite; the fine pass's own local
  re-solve refused this very stretch as dearer (`FINE zone=Zone 5 kn … spliced=no reason=worse`).
- Capping a merge's radius so its finest step reaches `stepM` — it changes every merge's reach, not the defect.
- The zone reads' cost — the search-speed plan's next lever.

## 6. Strongest objection

The rule can refuse a merge that rounds land better than the points it deletes — a staircase of land turns
along a coast, which one point would round more tightly — and it reads a land turn against the point's current
neighbours, which a slide or a merge beside it can change. P1 alone restores the swept line's grid-placed turns,
right in shape but not tight until P2.

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

## Key files

- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteApproachPass.kt` — `refine`, the window loop
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt` — `legClearCause`, `ChordRefusal`
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
