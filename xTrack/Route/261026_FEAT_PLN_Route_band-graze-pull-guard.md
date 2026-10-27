<!-- scope: feature -->
# Route — the destination-band graze: weighting the band out of the pull guard

**Date:** 2026-10-26 · **Status:** in design, for discussion — **Order:** the user's word of
2026-10-26: *"The grazing of the band around the 300M zone should be accepted to allow a correct pull of
the bend. Is there a way to weight that out and fix it?"*, after the two GPX of the same pair were compared.

## What the device pass showed

The evidence is two captures of the same pair (Village Englouti → Feu Artifice, 30 kn, evolutive, λ 1 · 3 · 5):
the bad GPX `2026 🤿_Le_Village_Englouti--with-bad-pull.gpx` (rung #2, λ 3) and the good
`2026_10_26_21_42-🤿_Le_Village_Englouti-gud.gpx` (rung #1, λ 1), read beside
[`route_full.log`](../../route_full.log:1) and
[`logcat_Le_Village_Englouti_bad-pull.log`](../../logcat_Le_Village_Englouti_bad-pull.log:1).

- **The bad route carries a Z at 43.5401,7.1021; the good route is one straight chord** — λ 1 runs
  43.53261,7.11034 → 43.54343,7.08335 with no intermediate corner, λ 3 keeps 43.53698,7.10395 and
  43.53999,7.10292. λ 3 is dominated: 11975.3 m / 1035.8 s against λ 1's 11824.3 m / 1028.4 s, both
  `zoneShare=0.00`.
- **The bend vertex is kept by the band at the destination, not by any slow water at the bend** — the
  sweep's own record: `SWEEP keep idx=2 at=43.53999,7.10292 why=PRICE chordSec=215.385 pathSec=129.826`
  and `SWEEP arms idx=2 band=80.184 worst=BAND at=43.54281,7.08443`, while the corner itself reads
  `band=false inZone=false`. The charged water sits at the aim's approach, ~1 km from the vertex.
- **λ 1 straightens through the same aim band**, so the charge is an entry-angle artifact: the chord enters
  the destination's band at a shallower angle than the detour, prices more band seconds, and is refused —
  although the aim sits inside the band and the crossing is unavoidable either way.
- **The fine pass's local re-solve never ran** (no `FINE zone … spliced=yes`), and `snapToCorners` has no
  corner within reach of the vertex, so the point is a cell centre of the λ 3 A* path that the pull carries
  and the sweep refuses to remove.

## The defect, named generically

- **The sweep's price guard prices the band in full** through the guard field, with no notion that a
  destination's own band is unavoidable: a chord and its legs both end in the same band, and the chord is
  refused for entering that band at a worse angle. The guard's purpose — never shortcut a priced band the
  search went around — is right for mid-route bands and wrong for the destination's own band.
- **The comparison is λ-scaled**, so the same geometry flips by rung: at λ 1 the chord is cheap enough to
  drop, at λ 3 it is not — the middle rung alone keeps the bend, and comes out dominated.

## The fix

A **band weight on the pull/sweep guard field alone**, leaving the search's band pricing untouched:

1. **A new key** — `route.avoid.pull.bandWeight`, default 0.2, clamped 0..1, loaded beside
   `route.avoid.pull.allowanceShare`. 1.0 is today; 0.0 removes the band from the guard entirely.
2. **The guard field scales the band** — [`costField`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:62)
   gains a `bandWeight` that multiplies its `bandSec`, threaded from the three guard-field sites:
   [`RoutePassRunner`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt:81),
   [`RouteFinePass.fineGuard`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:118) and
   [`RouteFinePass.solveCrossing`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:236).
3. **The search is untouched** — it prices the band through the grid's stored limits, so the route the A*
   chooses does not move; only the pull's and sweep's "not dearer" comparison is dialed.
4. **The recommended value is 0.2** — the bend's band arm is 80.184 s of an 85.6 s gap, so 0.0 closes it and
   the existing 0.1 allowance absorbs the residual; 0.2 keeps 20 % of the band's steering for mid-route bands.

## Risks

- **A weight of 0 would also let the pull shortcut mid-route bands the A* deliberately went around** — the
  one thing the guard exists to prevent. 0.2 is the compromise; it may still straighten some band detours.
- **The zone arm is untouched** — the two southern vertices (43.53698,7.10395, 43.53261,7.11034) are kept by
  the zone and stay as they are; this plan only removes the destination-band artifact.
- **The drawn line changes, the clock and crown barely move** — the ranking reads limits and time, and this
  removes a band-graze, not a limit, so the winner is unlikely to move; the acceptance is the user's pass.

## Review — self-check, 2026-10-26

- **Strongest objection to this plan:** the global weight is blunt — it discounts every band the guard reads,
  not only the destination's, so a mid-route band detour can also be straightened. The precise alternative is
  a **destination-disc waiver**: waive the band's price inside a disc of the aim (and start) whose radius is the
  band's own width, so only the unavoidable crossing is discounted. That is more correct and more code; the
  weight is the cheap first step and the disc the fallback if 0.2 over-straightens.
- **Updated by the review:** the plan ships the weight as a tunable, with 0.0 as the verification baseline and
  the destination-disc waiver recorded as the fallback, not as a second shipping option. The staircase-collapse
  and dominance-filter alternatives are recorded as not-chosen, below.

## Open questions

- **Weight vs disc** — whether 0.2 on all bands, or a full band-discount only near the ends, is the right shape.
- **Default** — 0.2 changes every route's drawing; the value that fixes this pair is what the user's pass confirms.

## Left standing, not this plan's

- **The zone-kept southern corners** — real slow water near the Cap, and a separate question.
- **The staircase itself** — collapsing grid stair-steps before the pull would fix the artifact at the source
  for every λ, and a **dominance filter** in the ranking would drop a dominated rung like λ 3's; both were
  named in the discussion and are alternatives to this plan, not part of it.
