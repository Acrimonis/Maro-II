<!-- scope: feature -->
# Route — the taunt re-read: a windowed chord with an allowance

**Date:** 2026-10-16 · **Status:** in design, for discussion — **Order:** the user's word of 2026-10-16, three
sentences: *"any way to improve this curve? The jagged has to be fixable. Some sort of average taunt?"*, then
*"this should be a generic taunt fix (that might fix some other weird looking corners in the middle of open
water)"*, and *"performance consideration must be paramount"*.

## What the device pass showed

The evidence is one capture, [`route_depth_pass.log`](../../route_depth_pass.log:1) — 1,289 lines, two
acquisitions of the same pair on `evolutive` at λ 1 · 3 · 5, pace 5.0 kn, corridor reach 3 704 m — read
beside the pull's own code.

- **The depth band was the only price acting on that route.** Every arm reports `DEPTH`, and all three
  `LINE` lines carry `slowShare=0.00 zoneShare=0.00 bandShare=0.00 rampShare=0.00`, because the pace equals
  the 300 m band's own 5 kn limit: no limit prices anything, and the only seconds in play are the band's.
- **The three rungs drew 1867.4 m / 726.0 s · 1925.4 m / 748.5 s · 1947.8 m / 757.2 s** — so λ's whole
  visible effect on that pair was 80 m of line, and the drawn edge's jaggedness is the band's fingerprint.
- **The jagged run is a cluster of ten bends in an 80 m × 750 m strip** with legs of **20–40 m**, turns of
  **0–44°** and room of **11–47 m**, answered `SHALLOW`; the sharpest is
  `CORNER idx=12 turn=87.1deg in=72.1m out=25.6m room=25.6m why=BOW_COAST`. A 0° bend has no angle for the
  corner pass to round and takes no room, so the pass is not the defect and cannot be the cure.
- **The pull refused every one of those vertices for price, and by margins a sampler cannot justify** — the
  sweep's own `SWEEP keep` lines: `chordSec=2.485 pathSec=1.233` (twice dearer), `chordSec=7.479
  pathSec=7.463` (**+16 ms**), `chordSec=2.455 pathSec=2.455` (**below the printed precision**), and, on the
  same stretch at λ 3, `chordSec=31.619 pathSec=0.000` / `9.308 / 0.000` / `11.722 / 2.681`.
- **The keep rule is the strict one**: a vertex drops only where the skipping chord is **not dearer** than
  the two legs it replaces ([`MultipassPull.kt:434`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:434),
  `chordSec <= pathSec`), so the price may pin a vertex on a difference smaller than the field's own
  sampling. With the band inert every one of those chords costs `0.000` against `0.000` and drops.
- **Half the refusals are clearance, not price** — `why=LAND` ten times at λ 1 — so the wall's own standoff
  is bound into the same run and no price-side change reaches it.

## The defect, named generically

- **The pull is a forward-only greedy** that commits a waypoint on the **first** refused chord and re-anchors
  there, so a vertex standing between two real corners survives a chord that would skip it and the walk
  never re-tests it; the sweep exists because of exactly that, and says so in its own KDoc.
- **The sweep tests triples alone** — one interior vertex at a time — so **a run of three or more flat
  vertices is invisible to it**, and an open-water wiggle survives for the same reason a pocket-boundary
  oscillation does. That is why this fix is generic: the two symptoms are one defect.
- **It also carries no allowance**, so it pins on sampling noise: a difference of a thousandth of a second
  is a drawn bend the reader sees and no arithmetic the clock, the crown or the price report can read.

## The fix

A **windowed sweep with an allowance**, the user's *average taunt*, specified so the cost stays bounded:

1. **One anchor, chords to k = 2…K** — test `A → C` over a run rather than a triple, take the longest
   acceptable chord, and never re-open a window that has closed.
2. **The allowance** — accept where `chordSec <= pathSec + allowance`, the allowance a **share of the legs'
   own price with a floor of one price sample's seconds**, so no new dial is invented and a chord inside one
   sample of the field can never pin a vertex. A relative term so it scales with the water, a floor so it
   stays meaningful where the legs are free.
3. **The clearance proof is untouched and read first** — the wall stays absolute, and a chord refused by
   land pays no price read at all, exactly as it short-circuits today.
4. **A geometry gate before any field read** — only a run whose intermediate turns are under a small angle
   is a candidate for a longer chord; the test is a bearing comparison, and it is precisely the jagged-run
   case. Everything else keeps today's triple.
5. **K is bounded and the existing gates stand** — `SWEEP skipped=short` and `skipped=no-refusals` still
   make a line the greedy pulled in one chord cost nothing, which is the common case on open water.

## Performance, which is paramount

- **The allowance is free**: the two `softPriceSec` reads it compares are already paid on every candidate,
  and it adds no field read of its own.
- **The window costs one read per extra k, answered from the memo the pull already filled** — on the
  capture, the settled line carries **12–15 points** a rung, the sweep's price reads run **139–175** against
  the pull's **338–452** per pass, and **65–80** of its reads are memo hits.
- **The geometry gate is what keeps the growth linear rather than quadratic** on a long route: a run is
  entered only where the line is already flat, and the number to watch is the vertex count of a **long**
  line's settled answer, not this one's — the sweep's own `priceReads` and `memoPriceHits` per pull are the
  instrument that says so.
- **The phase order is the cost order**: the allowance alone first, then the gate, then the window, so the
  cheapest phase that fixes the visible defect is the one that ships.

## Risks

- **A longer chord can bow a line through water the price was paid to avoid** — the allowance is a drawing
  licence, and its size is what decides how much water it may cut; the drawn line changes on **every** route,
  so the device scope is wide and the acceptance is the user's own pass.
- **The clearance keeps survive any price-side allowance**, so the wall-bound half of that staircase stays
  unless the wall's own standoff is revisited separately.
- **The clock and the crown cannot see the change** — the band adds no limit and the ranking reads limits
  alone — so an expectation of unmoved times and an unmoved winner is the correct one, and only the drawing
  moves.
- **The acceptance of a longer chord must stay deterministic** — the sweep's own tie-break is `<=` and it
  runs left to right to a fixed point, so one input keeps one line; a window must not break that.

## Phases

1. **The allowance** — shipped as a **pure share** rather than the floor this plan ordered; the deviation is
   recorded below, and the Ask hop's replay shows the dropped floor was the half that mattered.
2. **The geometry gate** — shipped at 45°, a code constant, read before any field value.
3. **The window** — shipped over k = 3…4, bounded, with the triple keeping its own shape.
4. **The record** — the engines doc's pipeline and its pull bullet, and the feature's state line.

## Open questions

- **The allowance's shape** — a share of the legs, a fixed fraction of one sample, or a dial a reader can
  see: only the last changes what the user sees, and that one is the user's.
- **K**, and whether the window belongs in the sweep (the cheaper home, already gated and already reporting)
  or in the pull's forward walk (upstream, but it would pay on every pass rather than on the fine settled
  line alone).
- **Whether the pocket structure itself should be smoothed** — the deeper route the user's fourth sentence
  circles: pricing the water by **distance from the shallow edge** rather than by the depth under the cell
  would turn pockets into the gradient the 2026-10-07 order described, and would leave this plan's window as
  the drawing-side complement rather than the cure.

## Left standing, not this plan's

- The pre-warm's ring lines are absent from the capture — no `PREWARM ring`, no `PREWARM walk done` with its
  own `stop=` — while the pass's 192 per-tile lines and ten terminals are present, so the growth's stop
  reason cannot be read from a device pass; and the capture carries **no `HARVEST` and no `WALK` line** at
  all, though the engines reference names both as traced. Each belongs to its own record.

## Implemented — 2026-10-16

- **The window and the allowance landed in the sweep, and what they cover is thinner than the order assumed.**
  The key `route.avoid.pull.allowanceShare` (0.02, clamped 0.0–0.5) read through
  [`AppConfig.routeAvoidPullAllowanceShare`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:620); the
  verdict `chordSec <= pathSec × (1 + share)`; the clearance proof still read first and absolute; a flat-run gate at
  `SWEEP_WINDOW_MAX_TURN_DEG` 45° and a window of `SWEEP_WINDOW_MAX_SPAN` 4 segments, both code constants, the window
  ahead of the triple in [`sweep()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:424);
  the full unit suite green and the APK packaged. It is generic as ordered — the sweep is shared by `avoid` and
  `evolutive`, so both drawn lines move.
- **The replay against your own capture is this landing's real measure, and it is thin.** Of the **48** `why=PRICE`
  refusals in [`route_depth_pass.log`](../../route_depth_pass.log:1) exactly **three** flip — `7.479` against `7.463`,
  and the printed-equality `2.455` against `2.455` — so about **one step of the ten-bend staircase, on λ 1 alone**, is
  straightened, and the `2.455` case moves no drawn point.
- **What a share cannot reach is the lane that binds the strip**: **20 of the 48** refusals have `pathSec=0.000` — free
  legs beside a chord of 1.5 to 31.6 s — and a share of zero is zero, so every window over them sums to zero and no
  chord passes.
- **The floor this plan ordered was dropped, and the replay says it was the load-bearing half.** §2's *floor of one
  price sample's seconds* was not implemented; the Ask hop's figures show it would have flipped four of those free-leg
  lanes. The deviation is recorded here rather than left implied.
- **The second symptom is untouched.** The capture's 87.1° `BOW_COAST` corner is kept by clearance and the gate refuses
  a window over any run holding it, so the flat-wiggle class is answered and the corner you pointed at is not.
- **Cost, measured.** On the existing settled-rung fixture the fine line's `priceReads` moved 503 → 697 and `marks`
  1297 → 1718 with the pull's and the final pass's counters unmoved — the Ask hop prices that at roughly **+7–10 ms on a
  75.9–98.8 ms fine stage**, sound on this water. The plan's own cost model is refuted by it: the window's longer chords
  are new points that each pay a read, not one memoised read per k.
- **Owed, recorded and not fixed** — the allowance is written twice where one loop would carry both; an accepted window
  prints no `chordSec`/`pathSec` and a refused one prints nothing, so its proof cannot be audited; the sweep has no clock
  of its own; four of the window's branches are unowned by fixtures; `AvoidPullLookbackTest` leans on the shipped key
  without naming it; and the sweep's KDoc overstates what the memo saves.
