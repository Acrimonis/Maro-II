<!-- scope: feature -->
# Route — a limit's figure is its own water: the band's minutes stop absorbing the rest of the line

**Date:** 2026-10-05 · **Status:** implemented, **rev 2** — the first attempt's bounded fold was insufficient on the water and has been **struck**; the shipped rule is this plan's contract taken literally, stated in §Rev 2, which wins wherever the text above disagrees.
**Order:** the user's word of 2026-10-05 — *"the acquired routes that avoid the 300M band have the 300m band: x min grossly over inflated … they seem to pick up the 300m zone when they are close to it even if they are out of it"*, after *"is it stale maybe?"* and *"any previous fix worth keeping?"*.
**Placement:** its own plan, deliberately not folded into
[`261005_FEAT_PLN_Route_plan-time-single-source.md`](261005_FEAT_PLN_Route_plan-time-single-source.md), whose
subject is where a time is **used**; this one is how a time is **split**.

## The defect, as verified

**The acquisition panel's Speed limits grid reports a grossly inflated figure for the 300 m band on a route that
avoids the band.** The figure is a **report** on the route's own legs, computed separately from the per-point speeds
that colour a saved line — which is why the colours are right and this number is not.

- **Where it comes from.** [`slowTimeByLimit`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt:248),
  called with the clock's own reads at [`RouteAvoidEngine.kt:378`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteAvoidEngine.kt:378)
  (`ctx.limitAt`, `inZone(ctx.zones)`, `inBand(ctx.world)`), its entries printed as whole minutes under a minute
  dropped ([`routeSlowLimitEntries`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:112)) and
  wrapped by [`routeSlowLimitRows`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:125).
- **What counts as slow there.** Any leg whose time exceeds its own distance at the cruise pace
  ([`RouteEta.kt:265`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt:265)) — which is **not
  the band or a zone alone**: a bend's 5 kn floor and every acceleration ramp qualify.
- **The bug is the fold, and its reach is unbounded.** A slow leg whose midpoint is in neither a zone nor the band is
  charged to the **nearest keyed entry**, searched outward `for (d in 1..legs)`
  ([`RouteEta.kt:293`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt:293)) — the whole line,
  forward winning a tie. So one genuine band leg (a berth end, say) soaks up every slow leg nearer to it than to any
  priced zone, and the band's entry counts seconds spent on water its limit never governed.
- **Membership is not the culprit.** [`inBand`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:248)
  is `distanceToCoastM <= bandWidthM`, the very test the clock and the price make, so nothing outside 300 m is
  labelled band by membership; it is the fold that reaches outside.
- **The sibling split does it honestly.** [`slowShares`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt:206)
  charges each slow leg by its midpoint alone and gives everything else a **ramp** bucket, so the band's *share* is
  band water's own seconds. The per-limit figure has no such bucket, and folds instead.

## The contract

**A limit's figure counts the time spent on water where that limit is in force** — nothing else. A slow leg on
other water is either attributed to a limit whose water it **neighbours and leads into** (its ramp up or down) or
counted nowhere in the per-limit grid; it is never attributed to whichever limit happens to be nearest.

## Phases

1. **The attribution — as first written, and superseded.** This phase prescribed a **bounded fold**: a slow leg
   joining a keyed leg of its own contiguous slow run. The device proved it insufficient, because on a coastal line
   *every* leg is slower than the cruise pace and so the "run" is the whole line. **The shipped rule is §Rev 2's** —
   charge each slow leg to the water at its own midpoint, and a slow leg on open water to **no** entry.
2. **The per-point speed invariant.** One fixture over the writers and readers of per-point speed asserting that a
   vertex carries the speed of the leg **leaving** it, `legDistance ÷ legDuration` for that leg; the destination — the
   one vertex no leg leaves — keeps the last leg's; a recorded track keeps its own GPS speeds and is exempt; and a
   plan-less draft carries zero, read as *no speed* rather than a wrong one. Exit: the invariant holds on the built
   track, on the plan read back through [`routePlanOf`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:376)
   and on the mirrored plan, with no expectation retargeted.
3. **The record.** The plan's own reading and the panel's one-liner (`routeSlowLimitRows`'s doc, which still says a
   ramp is folded to the nearest entry) are corrected together, so the next reader is not taught the defect.

## Verification

- **The falsifying fixture, first**: a line with **one short in-band leg at its end** and a bend mid-line asserting
  the band's entry equals that leg's own excess alone — today it also counts the bend's seconds, so it fails before
  the fix.
- **A route that never enters the band has no band entry**, asserted on a line whose whole length stands outside
  300 m of the coast.
- **No double count, and no orphan**: the sum of the entries never exceeds the route's total slow seconds, and a
  limit whose water the line never stands on never appears.
- **The invariant of Phase 2** holds on the three readers, and **nothing visible moves elsewhere**: the suite's
  completed/failed/skipped triple identical, the parked `route.avoid.fine.cellRatio` test the only red,
  `apk-build.bat` green.

## Risks

- **The strongest objection to the bounded fold** is that a ramp *between* two limits — the deceleration from one
  zone straight into another — then belongs to neither, and its seconds vanish from the grid; the honest answer is
  that a figure saying *nothing* is worth more than one saying *the wrong limit*, and the route's total still holds
  it.
- **A ramp's neighbour test is a positional rule**, and positional rules broke this once already; the fixture must
  place a ramp between two different limits, not only beside one.
- **The panel's rows must not change shape** for this fix, or a data correction reads as a redesign; if a *ramps*
  entry is ever wanted, that is a new decision with a new label, not a side effect.
- **This plan does not touch where a time is used** — the plan-time fix is kept and its decision on a plan-less
  draft stands; nothing here rolls anything back.

## Open questions

- **Whether ramps deserve their own entry in the grid** — the plan's recommendation is no for now: a limit's figure
  is its own water plus its own ramps, and a ramps row would be a new label and a new decision.
- **Whether the same bound belongs in `slowShares`** — it is already midpoint-honest, so the plan leaves it alone
  and says so, rather than changing a figure nobody complained about.

## Parked / out of scope

- The band's membership rule, the clock, the sampling step and the search's price — all verified right for this
  symptom.
- Today's two kept fixes: the plan's time as the only time, and the one-regime-per-leg speed contract.
- The stopgap of hiding the band's row when the line holds no band leg — unnecessary once the fold is bounded, and
  it would hide honest figures for a route that does enter the band.

## Implemented

**Shipped 2026-10-05, the `#implement` pipeline's Code hop** — the three phases together:

- **The bounded fold** in [`slowTimeByLimit`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt:1):
  a non-keyed slow leg's outward walk stops at the first pace leg, so a ramp joins only a keyed leg of its own
  **contiguous slow run** (the forward leg winning a tie) and a leg walled off from every keyed leg by pace water
  is charged to no entry. The panel's rows, labels and shape are untouched.
- **The falsifying fixture** in
  [`SlowTimeByLimitTest`](../../app/src/test/java/ykws/android/maro/spatial/multipass/SlowTimeByLimitTest.kt:1):
  a mid-line bend and a short in-band leg at the line's end — the band's figure read `2 ×` the in-band leg before
  the fix (the bend folded in) and reads the in-band leg alone after it.
- **The per-point speed invariant** in
  [`PerPointSpeedInvariantTest`](../../app/src/test/java/ykws/android/maro/ui/map/PerPointSpeedInvariantTest.kt:1):
  the built track, `routePlanOf`'s read-back and `mirroredPlanOf`'s reversed line, with a recorded track's GPS
  speeds exempt and a plan-less draft at zero.
- **The record**: the `slowTimeByLimit` and `RouteSlowLimit` docs, and the portable spec §5, no longer teach the
  nearest-entry fold.

## Rev 2, 2026-10-05 — the fold struck, the contract taken literally

**Why.** The bounded fold shipped, the user deployed it, and the figure did not move. The device trace indicted the
fold itself:

```
LINE distance=6906.4m duration=711.9s legs=245 step=10.0m bandMetres=524.4m bandPricedMetres=622.2m slowMetres=6906.4m slowShare=0.25 zoneShare=0.00 bandShare=0.23 rampShare=0.02 forced=[]
```

`slowMetres` **equals** `distance`: every one of the 245 legs is slower than the 25 kn pace, so the contiguous slow
run is the whole line — the band's entry read the route's own 711.9 s (≈ 12 min) where the time actually spent
inside 300 m is `bandMetres` at 5 kn ≈ **204 s ≈ 3 min**.

**The shipped rule, which is §The contract above taken literally.** Charge each slow leg to the water at its **own
midpoint** — a priced ring's limit, or the band's entry — and charge a slow leg on **open water to no entry at all**;
the fold and its outward walk are **deleted**. An entry is the **full seconds** its own legs take, not their excess
over the pace, because the label reads *spent in* that limit; the "time lost" reading remains available on the user's
word. Entries stay band-first then ascending limit, and the panel's rows, labels and shape are untouched.

**What this makes of the first attempt.** §Implemented's first bullet — the bounded fold — is **superseded**; its
falsifying fixture stands, but its two corrected siblings were **redefinitions of behaviour rather than
corrections**, and that is said here so no reader takes them for tidying. The portable spec §5's fold bullet is
replaced by the rule above, so no file still teaches the fold.

**The evidence, and the expected reading.** `everyLegSlowStillLeavesTheBandItsOwnWaterAlone` — thirty legs, all slow,
legs 4 and 5 on band water — read the line's whole duration before the change and those two legs' seconds alone
after. On the recorded rung the band's cell should now read **3 min**, not twelve.

**Still open from the review that followed** — a part-band leg is charged **whole** (≈ 11 s of overhang on the
recorded rung, consistent with `bandMetres`); the zone key's `?: bandLimitKn` fallback is **dead but is the last
non-own-water keying**, so it is dropped or its unreachability stated; the deleted fold left one unused
`kotlin.math.abs` import; and the panel's sub-minute drop still hides a band figure under ≈ 154 m at 5 kn.

**Closed 2026-10-07** — the first three are cleared: `slowTimeByLimit` now charges a leg only the part that lies
inside, so the band no longer absorbs a straddling leg's whole crossing; the dead `?: bandLimitKn` fallback is
dropped; and the unused `kotlin.math.abs` import is deleted. The panel's sub-minute drop is left standing, and
**intended**: it is the display rule that never prints a zero-minute row.

**One observation left standing, and it is not this plan's** — `slowMetres == distance` says the smoothed profile
rides a hair under the cruise pace across a whole coastal line. That is the profile's shape, and it is also why
*any* contiguity fold is unsafe here.
