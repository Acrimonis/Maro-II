<!-- scope: feature -->
# Route — the speed-zone standoff: the parked fixes, the evidence, and the settled design

**Created:** 2026-09-26 · **Branch:** `feature/zones-avoid-fix` · **Status:** in design — settled by the walk of 2026-09-26 and the second review's specifics folded in; the build is owed to implementation.

**Why this file exists.** The speed-zone fix shipped twice and the device pass disproved it twice: the route still crosses a zone in the Golfe-Juan / Cap d'Antibes water. This file parks the two fixes, records the evidence that stopped them, and carries the design the walk settled.

---

## The goal

- A drawn route that neither cuts through a speed zone a way around exists for, nor passes nearer a ring than the configured standoff — and whose crossing decisions are judged against the whole trip rather than against a fixed per-cell weight.
- **The line also carries its speeds:** the enforced limit while inside a zone, the configured pace outside it, and a comfortable ramp between the two at every transition.

---

## The parked fixes

### Fix 1 — the strictest-covering read (2026-09-26)

- **What it changed.** [`zoneArmPriceM()`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:284) prices the dearest of every covering source: [`zoneLimitKnAt`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidWorld.kt:83) for the interior and [`collarLimitKnAt`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidWorld.kt:90) for the strictest within-margin zone; `SpeedZoneRingProbe` / `speedZoneRingProbeAt` were retired for [`speedZoneCollarLimitKnAt`](../../app/src/main/java/ykws/android/maro/spatial/avoid/ZoneGeometry.kt:74).
- **Why.** The first cut avoided only the nearest zone and ran through the rest.
- **Outcome.** The route avoids more zones than before, but still crosses one.

### Fix 2 — the two-tier collar price (2026-09-26)

- **What it changed.** `route.avoid.speedZone.collarFraction=0.5` prices the collar at a fraction of the interior — [`zoneCollarPriceM`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteCostField.kt:109), `PricedZone.collarCostM`, `max(interior, collar)`.
- **Why.** The collar priced like the interior left the field flat, so the pull could straighten across a border it was pressed against.
- **Outcome.** The offending leg moved from lon 7.1546 to 7.1542 — about thirty metres — and the crossing persists.

---

## The evidence that stopped them

- **The crossed zone is identified.** From the live zone fetch ([`CapAntibesZoneTest`](../../app/src/test/java/ykws/android/maro/data/regulation/CapAntibesZoneTest.kt)): a navigation-restriction zone carrying a **10 kn limit** (SHOM Réf `FR000027927500003`), ~4.3 km², 27 vertices, centre 43.5447 N / 7.1301 E, with a 5 kn neighbour (~1.4 km², 65 vertices) at 43.5460 / 7.1294. The route's south-west leg ends inside the 10 kn zone.
- **This water was already studied here.** [`260920_FEAT_PLN_Route_trajectory-quality.md`](xxArchive/260920_FEAT_PLN_Route_trajectory-quality.md): in the Antibes / Golfe-Juan / Cap d'Antibes water "the passages that matter are between a zone's edge and the shore and are a few tens of metres wide. Cutting 25 m out of them severs them; **pricing them moves nothing, because there is no other water to move to**".
- **The prime suspect was the collar's width.** `route.avoid.speedZone.marginM=50` prices a 50 m band around every ring, and the only ways around this zone are the tens-of-metres passages.
- **Two structural limits compound it.** The search is bounded by the corridor box, grown only on `NoPath`; and the fine cell never runs ([`route.avoid.fine.cellRatio`](../../app/src/main/assets/maro.properties:95) ships parsed and unused).
- **The device record.** Three passes on this stretch: `2026_09_25_22_22`, `2026_09_26_09_22` (after Fix 1), `2026_09_26_09_36` (after Fix 2).

---

## The settled design

### 1 · What the map stores — the zone limit per cell

- [`rasterize()`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidGrid.kt:220) writes the zone's **limit** into each cell rather than the finished price, and the A* prices it at read time. A change of λ then costs one multiply per cell instead of the **139–182 ms** zone read — which is what makes the loop in §3 affordable.
- **The sub-choice settled:** one limit per cell, the strictest in force, with no separate collar flag — the standoff leaves the grid under §4.
- **How the A\* is handed the price:** the grid exposes the limit per cell and the engine passes the search a plain price function — `λ × cellSeconds(limit)` — read once per expansion, so [`AvoidSearch`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidSearch.kt:40) keeps its shape and holds no λ of its own.

### 2 · The unit — seconds

- The A* costs in **seconds**: the base is `cellM / v(pace)`, the zone term is the cell's time excess `cellM × (1/v(limit) − 1/v(pace)) × λ`, and λ is dimensionless. `timeBudgetPct` then reads with no conversion.
- [`AvoidSearch`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidSearch.kt:59)'s heuristic becomes a time bound, `haversine / v(pace)`, so it stays admissible; the pull's margins stay in metres, being geometry; and [`AvoidCell.sourceCostM`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidGrid.kt:44) changes unit from metres to seconds.
- **The unit's reach, enumerated** — the grid's base (`cellM / v(pace)`), the 300 m band's [`bandPriceM`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteCostField.kt:96) which becomes a time excess like the zone's, the A\* heuristic, the `√2` diagonal step — which now means a diagonal covers `√2` cells of distance and time alike — and every test asserting a `cellM`-based cost. The depth gate's clearance and the pull's margins stay in metres, being geometry.
- **The ETA stays λ-free:** the clock reads the limits in force, never the price, so the unit change reaches the search and never the reported time.

### 3 · The λ loop — a band, one correction, two passes

- Pass one seeds λ from `softCostAversion`, so the first line looks like the shipped one. The zone share is read; inside a **±20 % band** the loop stops there.
- Outside the band, one multiplicative correction — `λ₁ = λ₀ × (zoneShare / budget)` — then a second solve, then stop. **Cap: two passes.**
- The budget is a **target**: a share still out of band is **reported**, not chased and not refused.
- **How the share is read:** off the chosen line's own timed legs — the seconds it spends **slowed by a zone, the approach ramps outside the ring included**, against its total time — the quantity the ETA already walks, so the two can never disagree.
- **The key:** `route.avoid.speedZone.timeBudgetPct`, default **33**, clamped 0–100 — and a **lever the user sets**, with its own Settings row beside the route keys, because how much slow water a trip may use is a preference rather than a tuning constant.
- **The assumption, stated:** the update presumes a bigger λ shrinks the share. True in open water; where the water is lumpy it may not hold, which is exactly why the loop stops after one correction instead of chasing.

### 4 · The standoff — a clearance in the final pull

- The collar leaves the search entirely, so the crossing decision no longer depends on a band wider than the water it runs through. The standoff returns in the **final pull** as a clearance, reusing [`legClear`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidPull.kt:72) with the zone ring added to what a chord may not come closer to than the standoff.
- It holds wherever a chord outside the standoff exists, and degrades to the free path the search found in a tens-of-metres passage. `collarFraction` either dies with the search collar or becomes the pull's clearance margin.
- **Its mechanics:** the guard field that already carries the hard walls gains a ring-distance read, and [`legClear`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidPull.kt:72) takes the standoff as a **second margin** beside the obstacle's — a chord is refused when it comes closer than the obstacle margin to land or the depth gate, or closer than the standoff to a zone ring. `route.avoid.speedZone.marginM` supplies the second; the obstacle margin is untouched.

### 5 · The fine pass — after the route

- Coarse solve first, then a refinement along the coarse answer only: a swath `2 × (standoff + cellM)` wide — about 200 m — re-rasterized at `route.avoid.fine.cellRatio` (20 m), then a **pull and a snap only**.
- **The one exception:** at any restrictive zone the coarse line enters, a **local A\*** re-solve in a box — the crossing zone's bbox inflated by the standoff plus one coarse cell, clamped to the corridor — splicing in a way around if the fine grid finds one.
- The pass never runs inside the λ loop, so it costs nothing there.
- **The swath's construction:** the coarse polyline's own bounding box inflated by half the swath — one box, not a hull — which keeps the pass cheap and still covers every metre the line runs.
- **The box's clamp:** the crossing zone's bbox inflated by the standoff plus one coarse cell, intersected with the corridor, so the local A\* can never wander past the search it refines.
- **Its λ:** the local A\* prices with the λ the coarse pass settled on, so the refined line obeys the same budget.

### 6 · The two verdicts

- **Budget unmet** — a way around exists and the price could not reach it; the corridor growth of §7 is the step that tries to turn it into a real answer.
- **Forced crossing** — no way around exists at all. The two are reported apart, and only the second is the geometric case.
- **The verdict's home:** a nullable share on the result — `RouteResult.Success.budgetUnmetZoneShare`, set only when the band was missed — plus `route_budget_unmet` and its description in both locales; the forced crossing keeps its existing field and names, so the two never share a string.
- **The probe runs once**, after the λ loop's final pass, against the settled route, so neither verdict is reported per pass.

### 7 · The corridor growth

- Extend [`search()`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:153)'s `NoPath` retry: if the answer is still out of band, retry once with the reach doubled. **Cap: one step**, because it multiplies the sweep roughly fourfold.

---

## The speed profile — the route carries its speeds (requirement added 2026-09-26)

- **The requirement.** Every leg carries the speed the boat should hold: the **enforced limit** while inside a zone, never above the configured pace; [`route.freeWaterPaceKn`](../../app/src/main/assets/maro.properties:46) outside it; and a **comfortable ramp** between the two at every transition.
- **What the clock does today.** [`timeLineWithLimits`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:35) already splits the line where the limit changes — placing the boundary vertex by bisection — and climbs back to pace after a zone on a ramp; but the ramp's rate is a **hardcoded** `ZONE_EXIT_RAMP_MPS2 = 0.5` ([`RouteEta.kt:18`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:18)), and **entering a slower zone decelerates instantly** ([`segmentTimeM`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:120), its fall branch).
- **What changes.**
  - **A key for the rate:** `route.speed.accelMps2`, default 0.5, clamped — one home, and the property file wins over the constant.
  - **The decel is modelled:** a fall ramps down at the same rate, so the boat starts slowing `(v0² − v1²) / 2a` before the boundary. 28 kn to 5 kn at 0.5 m/s² is about **200 m and 24 s**, which dominates a short zone.
  - **The profile reaches the result:** a speed per leg on `RouteResult.Success`, beside `legTimesSec`, so the drawn line, the panel's figures and the saved track (the GPX already writes `<speed>`) all read one set of numbers.
- **Why it bears on the budget and the offers.** A 300 m stretch at 5 kn costs about **117 s** ideally and about **165 s** once both transitions are paid — so any saving offered for crossing a zone is only honest with the ramps in the clock.
- **The approximation accepted (2026-09-26).** The A\* prices a cell by the limit in force and pays no ramp, and the **ramp and the ETA are computed after the route is chosen** — a fair enough approximation at 50 m cells. Giving the search a boundary allowance is **declined**: it would buy seconds of accuracy for a cell cost that is no longer additive.
- **The three points settled (2026-09-26).** The decel **leads the boundary**, so the profile shows the boat easing down before the zone and the slow approach metres sit outside the ring; the ramp stays a **straight constant-acceleration line**, the hull's own curve being a negligible refinement and the approximation accepted; and those approach metres **count** toward the budget — so the figure is named for what it is, the share of the trip **slowed by** zones rather than the share inside rings.

---

## The offers — a dashboard carousel (planned 2026-09-26)

- **What they are.** Once the line is drawn, the engine re-solves the **same grid** with one source's price dropped — the speed zones first, then the 300 m band — and each alternative's own ETA against the current route's is the **saving**.
- **Where they live: the dashboard, not toasts.** The candidates render as the route panel's own row, beside the line's figures, so they never cover the map.
- **The control is next / previous, the Waze shape.** Both `‹ ›` and a swipe step the set, and **neither wraps** — at the first card `‹` is disabled and at the last `›` is, so the edges are visible rather than looping.
- **All the candidates are drawn at once.** The set arrives together and every candidate's line stands on the map, the selected one at full strength and the others **dimmed**, so stepping moves the emphasis rather than the drawing and the choices are read side by side.
- **The lines awaiting confirmation draw above every other layer that displays a track.** The rule means above every track-rendering layer — the recorded tracks' overlays and the standing route alike — so the choice under the cursor is never buried; the dimmed candidates share that band, and the scope is the track layers alone.
- **The colours are the line's own.** The selected candidate draws in the route line's existing appearance and the others in that same colour at a lower alpha, so both alphas live in the appearance family rather than as new literals.
- **The dim's strength is a device judgement**, defaulting near half the selected line's alpha — strong enough to pick the chosen line out of a busy area, weak enough to keep the others legible.
- **Deferred (2026-09-26):** the row's home — inside `RouteConfirmationPanel` or a strip of its own in the dashboard slot — waits on a **layout change landing first**, and is re-assessed against it rather than decided now.
- **The set.** The safe line is the first entry, so a user who never touches the control sees exactly today's behaviour; behind it the offers, one per source, and only those with a **positive** saving.
- **The commitment is unchanged.** The existing `Confirm` takes the displayed candidate: the carousel moves what is **drawn** and never what is **followed**, and the trip figure under it is the candidate's own with the saving stated beside it. The set **stays switchable** after a confirm, so a user who took one candidate can still take another without asking again.
- **No saving, no carousel.** Where the line already had to cross a zone because no way around exists, there is nothing to offer and the row is **absent** rather than showing a lone card.
- **Cleared with the line.** A new ask clears the set and puts the cursor back on the safe line, the offers belonging to the line they were computed for.
- **Why they are cheap.** The grid is built once and the limit is stored per cell, so each candidate is one A\* plus a pull and a snap — tens of milliseconds — after the main line is drawn.
- **The agent's calls, not yours:** the candidates ride the main solve's own cancellable job, they are a plain list on the existing result, and the panel's state is one index into it.

---

## The build order

1. **The unit and the storage together** (§1, §2): the grid keeps the limit per cell and everything in the field costs seconds — base, band, heuristic, the `√2` step, and every test that asserted a `cellM`-based cost.
2. **The ramp clock** (§8): `route.speed.accelMps2`, the modelled decel leading the boundary, and the approach's slow metres in the clock — **before the loop below, which reads its share off this clock**.
3. **The λ loop** (§3) on that unit and that clock: the share read off the line, the verdict's field and strings written (§6), the budget's Settings row added.
4. **The standoff into the pull** (§4), with the probe moved to a single end-of-loop run (§6).
5. **The corridor growth** (§7).
6. **The fine pass** (§5).
7. **The speeds and the offers on the result** (§8, the offers): the per-leg speeds, `budgetUnmetZoneShare`, and the offers behind them — with the carousel's row documented in [`docs/ui-component-guidelines.md`](../../docs/ui-component-guidelines.md) §5.8 (`RouteConfirmationPanel`) **as it is built**, the doc being the home of the panel's composition.
8. **The technical fixes** — one sweep, one harvester, scoped zone reads — ride this build.

---

## The performance budget and its guards

- **The wall and the reading.** 500 ms wall; the armed corridor reads **170–225 ms** (the zone read 139–182 ms of it) on desktop; this engine's device factor is unmeasured.
- **The hidden multiplier.** [`searchOnce`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:192) rasterizes once and [`forcedCrossingNames`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:330) rasterizes **again** with `blockZones = true` whenever a restrictive zone is armed.
- **The guards.** Map materialized once per search, re-priced per λ pass · the forced-crossing probe run once, at the end · two λ passes at most · one corridor growth step at most · the fine pass local (swath plus crossing boxes), never whole-box.
- **The post-route passes, priced.** The ramp clock and the offers both run **after** the line is chosen — the clock over tens of points, each offer one search on the shared grid — which makes them the cheapest work in the plan and correctly the last in its order.
- **The expected factor.** About **1× on the common path** — the saved sweep pays for the extra search — and up to **~3×** if the corridor grows once.
- **The gate is a device reading**, not the arithmetic.

---

## Technical fixes folded in — behaviour-neutral and cheap

1. **One sweep per solve.** [`forcedCrossingNames`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:330) re-rasterizes the corridor to block the restrictive zones; the same result comes from flagging those interiors on the one grid instead of a second sweep.
2. **One corner harvester.** [`corners`](../../app/src/main/java/ykws/android/maro/spatial/avoid/TangentCorners.kt:45) and [`ringCorners`](../../app/src/main/java/ykws/android/maro/spatial/avoid/TangentCorners.kt:82) are two adapters over one [`addTangent`](../../app/src/main/java/ykws/android/maro/spatial/avoid/TangentCorners.kt:116); normalising the coastline rings to the ordered-ring input at the harvest boundary leaves one entry point.
3. **Scope the zone reads to the box.** [`collarLimitKnAt`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidWorld.kt:90) and [`zoneLimitKnAt`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidWorld.kt:83) walk **every** zone per point while [`searchOnce`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:186) already holds the box's zones; passing those in removes the redundant walks.
4. **Give `route.avoid.fine.cellRatio` its consumer.** §5 is it.

---

## Tests and gate

- **The tests, each with the revert it must fail on:** the λ band's exit (red with the exit removed) · the corridor growth finding a way around (red with the growth disabled) · the fine pass threading a passage the coarse grid cannot see (red with the pass removed) · the pull's clearance holding on open water and degrading in a passage (red with the clearance removed) · the unit change (base, heuristic and every `cellM`-based cost, red on any metres unit left behind).
- **The gate:** per-stage device timing — corridor · sweep · A\* · pull · snap — with the acceptance pair unchanged.

---

## What stays out

- Any new dependency; anything committed or pushed by the agent. The budget's Settings row is **in**, because §3 makes it a lever the user owns, and the offers are **in** as stackable toasts.
- A whole-box fine grid; a passage-aware collar; forbidding the zone interior — that would cut off coastal routing through those slivers by design, per the archived study.

---

## Provenance

- The three device passes on this stretch: `2026_09_25_22_22-Route_2026-09-25_22_22-1.gpx`, `2026_09_26_09_22-Route_2026-09-26_09_22-1.gpx`, `2026_09_26_09_36-Route_2026-09-26_09_36-1.gpx`.
- The zone list: `app/build/test-results/testDebugUnitTest/TEST-ykws.android.maro.data.regulation.CapAntibesZoneTest.xml`.
- The archived study of this water: [`260920_FEAT_PLN_Route_trajectory-quality.md`](xxArchive/260920_FEAT_PLN_Route_trajectory-quality.md).
- The removed tracer's documents: [`260922_FEAT_DOC_Route_taut-tracer.md`](260922_FEAT_DOC_Route_taut-tracer.md) and [`260922_FEAT_DOC_Route_mesh-engine.md`](260922_FEAT_DOC_Route_mesh-engine.md).
