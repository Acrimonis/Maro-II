# Route — the verdict as an exchange rate

> From the 2026-10-15 discussion: the crown was the last piece of the pipeline that a reader could not
> predict from the settings. This plan replaces the two dials and their hidden rule with one rate and one
> sum, so a route's crown is arithmetic the user can do on the card's own figures.

## What is there today

- Two dials feed the crown: the **Driving preference** (0–5, cut into three seats at 1.25 / 3.75 by
  [`RoutePreference.of`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePreference.kt:63))
  says which measure leads, and `route.avoid.speedZone.timeBudgetPct` (25) hands the **Balanced** seat a gate.
- The gate is read in exactly one place — [`leadCompare`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRanking.kt:96)'s
  BEST arm, reached from [`runningBest()`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:471) —
  and its number is a **share**: ring interiors' seconds over the line's whole clock.
- The 300 m band's own seconds and the ramps' are read apart ([`slowShares`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt:205)),
  so the gate is a ring share; the band still reaches it through the denominator, since it lengthens the trip.
- Three rungs are drawn at their own λ over one shared grid, and the crown is a **rule over the three
  finished lines**, never a consequence of their rates — which is why the middle rung wins only when the
  rule happens to name it.
- λ is an exchange rate in disguise: the drawn cost is pace-time plus λ × excess, which read on a finished
  line is **trip time + (λ − 1) × excess** — so λ − 1 is exactly the extra trip seconds the search will pay
  to remove one second of slow water.
- The cost the ranking reads carries zone share, zone metres and the clock
  ([`PassCost`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRanking.kt:42)); a
  per-limit slow-water table exists for the panel, and no slow-water total reaches the ranking.

## What is decided

- **D1 — one dial, and it is a verdict knob.** The preference and the budget collapse into a single weight the
  crown's sum reads; the dial carries its own stops (1x, 2x, 3x, 4x, ignore time) and the ladder's λ stay the
  drawings' rates alone. The first draft's claim that the dial's stops would be the ladder's own rates is
  **withdrawn**: the landed rule counts plain seconds, so no line has to be drawn at a new rate for the crown
  to move.
- **D2 — the comfort term is absolute seconds.** Never a share: a share rewards the longer line for the same
  slow seconds, and its denominator moves with the band.
- **D3 — every kind of low-limit water counts.** Ring interiors, the 300 m band, and the decelerations and
  accelerations that serve a limit. The boat's own cornering floor is the boat's water and stays out.
- **D4 — the limit's severity is a lever, boxed.** Each second is weighted by a per-limit severity, shipped
  at 1.0 so the table is inert until filled; the physics weighting already lives in the drawing's excess.
- **D5 — the two ends are asymmetric.** A weight of **0** reproduces the fastest line exactly, while the
  cleanest is the limit as the weight grows without bound — so the top stop is a **special case in the
  comparator**: the slow seconds decide alone, the clock breaking a tie, and it is named **ignore time**, never
  numbered. The landed bridge's floor is 1x, so the **0** stop is P3's to offer.
- **D6 — a tolerance, or the crown flaps.** Nearly every line carries some slow seconds, so a difference
  under a stated number of **score seconds** must not flip the crown, a strictly lower score being the win;
  the tolerance carries a key of its own, in the manner of `route.avoid.ladder.collapse.toleranceM`.
- **D7 — presentation apart from arithmetic.** The card keeps plain minutes in slow water and the per-cent
  share; the score is the weighted sum, and the record says so.

## What the change would be

- The crown's rule is one line: **score = trip time + weight × seconds spent under a limit**, lowest wins —
  weight **0** giving the genuinely fastest line, the top stop the fewest slow seconds. D4's severity factor is
  absent from the landed formula because it ships at 1.0; it returns with its table.
- The share-based gate, `timeBudgetPct` and its Settings row retire as ranking inputs; the share survives as
  a published reading on the line.
- The weight's home is the **dial's own key**, which P3 introduces; the ladder's λ are the drawings' rates
  alone and no longer feed the dial — the first draft's plan to read the dial's stops off the ladder is
  withdrawn.
- The arithmetic is a **deliberate weighting, not a cost**: those slow seconds already sit inside the trip
  time, so the weight states that a second under a limit is worth **1 + weight** ordinary seconds — which is
  what lets both ends fall out of one formula.
- The cost the ranking reads gained the slow-water seconds beside the clock; the zone metres it used to carry
  went with the tail the sum replaced.

## Feasibility and risks

- **Cheap on the engine.** The per-rung readings exist, the comparator is one function, and no search changes.
- **The band dominates a coastal line** — a recorded 6.9 km coastal leg reads 0.25 of its time slow with 0.23
  of it band — so counting the band moves every coastal verdict toward the detour, and the dial's stops need a
  tuning of their own rather than an inheritance from the ladder.
- **A severity table is a new tuning surface with no data behind it.** Shipped at 1.0 it biases nothing; every
  value above it is a guess until a device reading exists.
- **Two readers of one table is the trap.** If both the search's price and the verdict's score read severity,
  the drawn lines move with it and every λ scale shifts with them.
- **The dial's stops and the ladder's rates are now independent.** A hand-set ladder no longer moves the dial;
  what it still decides is which line each rung name drew, so the two scales can drift apart and the record has
  to say which one a reader is looking at.
- **The per-rung reading was not the whole sum.** Each rung's result carries its per-limit slow seconds, but
  that table holds the rings and the band only — no ramp and no bend's floor — so the ramps that serve a limit
  needed a reading of their own, which P0 added.

## Phases

- **P0 — the reading — shipped.** The ranking's cost carries the seconds spent under a limit, read from the
  per-limit table plus the ramps that serve a limit. Still owed: a fixture pinning the engine's own wiring of
  the two together, since the sum is tested only where it is computed.
- **P0's fixture — shipped at function level.** The band, the ring and the serving ramps stand inside the sum
  and a bend's floor outside it.
- **P1 — the score — shipped.** The comparator reads the one-line sum, and the top stop compares the slow
  seconds alone. Still owed: the **0** weight's own test, which the landed bridge cannot reach because its
  floor is 1x.
- **P2 — the edges — shipped.** The tie tolerance and the **ignore time** stop, with the asymmetry written
  into the comparator's own KDoc.
- **P3 — the dial — shipped.** The weight's own key and row, the six stops with **0** offered, the bridge
  replaced, and the seat mapping, the budget key, its row and its messages retired from both locales. Still
  owed: the budget's own dead predicates and its dormant result field, and the row's description sentence,
  which states the wrong stop.
- **P3's persistence.** A stored aversion value is read as a stop on the new dial, so an upgrade moves no
  behaviour by itself and no migration is needed.
- **P4 — the presentation.** The card gains the plain minutes and the per-cent share where it does not already
  carry them; no figure and no drawn line moves except the crown.
- **P5 — the record — shipped.** The avoid-algorithm doc's ranking section, the engines doc's retired provider
  rows and its ladder clause, and the decisions doc's entry reversing the 2026-09-30 band exclusion — which had
  itself superseded the 2026-09-26 ruling that the approach ramps count.

## Open questions

- **Q1 — settled: plain seconds.** The verdict reads the seconds spent under a limit, never the physics excess,
  so the crown and the drawing differ on purpose and the shipped KDoc says so.
- **Q2 and Q4 — dissolved by the landing.** The dial weighs lines already drawn and asks for no new drawing, so
  there is no free-rate search to pay for and no stop for a requested rate to fall between.
- **Q3 — what severity is keyed by:** the limit alone, or the limit and the kind of water (a ring against the
  band), since the two are felt differently.
- **Q4 — the verdict between stops:** with the dial on a rate no line was drawn at, does the crown fall to the
  line nearest that rate, or to the arithmetic winner among all three as it does today?

## Implemented

- The crown is one sum — trip time plus the weight times the seconds spent under a limit, lowest wins — over
  the three drawn lines, with an **ignore time** stop comparing slow seconds alone and a 30 s tolerance so
  near-equal lines do not flip it: **P0, P1 and P2 shipped 2026-10-15** (the slow-water seconds read from the
  per-limit table plus the new `rampSlowSeconds`, a bend's own floor out of both; the ring-share gate, its tail
  and the nearest-preference tie-break removed; `route.avoid.ladder.tieToleranceSec` default 30, bounded and
  commented).
- **P3 shipped 2026-10-15**, replacing that bridge: the weight has its own key
  (`route.avoid.slowWater.weight`, default 4), its own six stops — **Fastest**, 1x, 2x, 3x, 4x, **Ignore time** —
  with the sentinel carried as a `null` weight, one stored stop index clamped on read, the legacy cursor mapped
  onto it once so an upgrade moves no behaviour, and the seat mapping, the cursor, the budget key, its row and
  its messages gone from both locales. The row now reads `Slow water` / `Eau lente` where the preference row
  stood, and the acquisition's own rung words are untouched.
- **The consistency sweep of 2026-10-15 cleared every residue the two reviews named**: the row's description now
  matches its own stops, the budget's last code and its dormant result field are gone, the ladder's code
  defaults follow the file (5 / 3 / 1) and the collapse tolerance's fallback follows it too (200 m, long a
  silent 25), the three toothless checks are re-pointed or deleted with the reason named, and the round-trip,
  the legacy mapping and a stored **0** reaching the engine's weight are pinned by a new settings test.
- The sweep carried the record with it: the avoid-algorithm doc's ranking section, the engines doc's retired
  provider rows and the decisions doc's reversal of the 2026-09-30 band exclusion.
- One reading moved with the λ defaults: the selective perf harness anchors 4074 expansions where the shipped
  λ 0 gave 2820, its KDoc following, because the fast rung now prices at λ 1.
- P4 remains in design: the card's plain minutes and the per-cent share are to be checked against what the
  panel already prints rather than assumed missing.
