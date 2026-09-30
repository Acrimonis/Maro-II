<!-- scope: feature -->
# Route — the speed-zone fix, whole: the price in the field, the hug, and the standoff as a cost

**Created:** 2026-09-25 · **Branch:** `feature/zones-avoid-fix` · **Status:** implemented — shipped 2026-09-25, pointer in `FEAT_DSC_Route.md` `## Implemented`.

**How to read this plan.** Part A and Part B are the build spec; everything below `## Parked` is context from the 2026-09-25 session that the implementation does not need, kept for the record.

---

## Part A — the field unification and the hug set

### A1 · The zone price enters the field, as the band's does

- **What is wrong.** The zone price is handed to `rasterize` as `PricedZone`, so the A\* pays it through the grid cell while the pull and the snap read only the field — which carries the coast, the depth gate and the **band**. The pull's own chord guarantee ("a chord is accepted only while its own summed price stays within the A\* cell path's over the span it would replace") is therefore inert for zones: the A\* bends around a zone and the taut pull straightens the line back across it. That is the cut the user reported.
- **What to build.** The price becomes a `RouteCostSource.Soft(tag = AvoidCellState.ZONE)` built in [`costField()`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:235), read per point from the world's zone reads and priced by [`zonePriceM(cellM, pace, limit, k)`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteCostField.kt:104), armed by `route.avoid.speedZone.enabled`.
- **What it removes.** `PricedZone`, `AvoidGrid.zoneCostM`, `applyZoneCost`, the zone add inside `AvoidGrid.cell()` and `rasterize()`'s `zones` / `blockZones` arguments. `fillZonesEvenOdd` stays as the grid's scanline paint — extended in B1 to paint the collar — so "inside a priced zone" keeps one price function with two materializations, the grid's fill and the field's source.
- **Semantics preserved and asserted.** The strictest limit wins where zones overlap (`zoneLimitKnAt` is a `minOfOrNull` over the containing, non-excluded zones), and the band plus a zone still sum on a cell holding both.

### A2 · The hug set

- **What to build.** A third `CornerSet` in [`searchOnce()`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:169) from a [`TangentCorners`](../../app/src/main/java/ykws/android/maro/spatial/avoid/TangentCorners.kt:1) ring-corner harvest whose outward side is read from the ring's own signed area, behind `route.avoid.speedZone.marginM` (50) with its `AppConfig` accessor, clamp and pin.
- **The stated deviation.** The set's snap radius is the standoff **plus one cell**, not the standoff alone, so a bend lying a cell out is still captured; the visible standoff is the key's number, not the radius.

---

## Part B — the standoff as a cost

### B1 · The collar price (the fix)

- **The mechanism, in one sentence.** The zone's existing Soft source prices **two ranges from the same per-point read**: inside the ring at `zonePriceM(cellM, pace, limit, k)` as today, and within `route.avoid.speedZone.marginM` of the ring at the **same** price; beyond the collar, nothing.
- **Why a price and not a rule.** Staying outside the collar is free *and no longer* whenever the route already runs parallel to a ring — which is exactly the pass-by the probe measured. So the cheapest-clock line moves out to the collar's edge by itself, and the behaviour becomes the band's own: outside is cheaper, so the line stays outside wherever it can afford to.
- **The read.** The signed distance from the point to the nearest **outer-ring** segment, negative inside and positive outside, answered by [`PolygonIndexBase.status().nearestBoundaryM`](../../app/src/main/java/ykws/android/maro/spatial/PolygonIndexBase.kt:161) — an indexed cell-binned read, not a linear walk over the region's 387 ring vertices. One signed read is the whole collar test: `d < 0` prices the interior arm, `0 ≤ d ≤ marginM` the collar arm, `d > marginM` nothing, so the interior and collar arms can never both fire.
- **The collar arm's limit.** The interior arm keeps `zoneLimitKnAt` (the strictest **containing** limit); the collar arm lies outside every zone, so it needs the **nearest** non-excluded zone's limit — a read [`AvoidWorld`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidWorld.kt:83) does not yet expose, and the world gains for this.
- **Holes carry no collar** — the signed read measures the **outer ring only**, so a hole's shore is not a priced boundary. The index's raw `nearestBoundaryM` measures hole edges too ([`PolygonIndexBase.kt:66`](../../app/src/main/java/ykws/android/maro/spatial/PolygonIndexBase.kt:66)), so the collar read must be outer-ring-only rather than the index's raw nearest. *Objection, recorded:* in some regulations a hole edge is a real boundary; if it must carry a collar, that is a separate change with its own test.
- **The clock is untouched.** The ETA reads the limit in force, never the price, so a line inside the collar but outside a zone keeps the pace — the collar changes which line is chosen, not how the chosen line is timed.
- **The forced-crossing probe is untouched in meaning:** it blocks the ring's **interior** through the same read it uses today, never the collar. A blocked collar would fire "no way around" on routes that legally cross the margin.
- **Passability is untouched:** no cell becomes impassable, so no new `NoPath` and no corridor change.
- **Performance — not the same read frequency.** The field is evaluated once per passable cell, so if the zone source were read there it would run containment per cell instead of once per scanline — the per-cell explosion the stage-1 rasterizer already refuses. The grid therefore keeps the scanline zone paint (extended to the collar with the same `forEachCellNear` sweep the coastline margin uses) and the field's zone source is read only by the pull and the snap over their few sample points; re-measure under §Measurement.
- **Two shapes considered, one chosen.**
  - **Chosen — the collar priced like the interior.** One price function, two materializations — the grid's scanline and the field's source — the band's own shape; the whole zone source keeps a single price home.
  - On the shelf — the **ramp** rising toward the ring (the removed tracer's berth price, settled 2026-09-21 for the taut tracer). Smoother-aimed, but the price becomes position-dependent, which the pull's chord guard then compares over spans, and it changes the same lines further. Not built; named so it is not rediscovered.
- **No new key.** The collar's width is `route.avoid.speedZone.marginM` and its price is `route.avoid.speedZone.softCostAversion` through `zonePriceM` — one home each, already shipped.
- **Where it lives.** One function beside `zonePriceM` in [`RouteCostField.kt`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteCostField.kt:104) — `zoneCollarPriceM(distanceToRingM, marginM, fullPriceM)` — read by the zone arm in `costField()`, so the A\*, the pull and the snap keep reading one source.

### B2 · The snap order — small, and only if it is still needed

- **Today:** `pull → snap → pull`. The closing pull may erase a snapped bend wherever the straight chord does not enter a priced cell.
- **After B1** a chord into the collar is dearer than the cell path, so the pull's guard protects the snap wherever the standoff matters — which may make B2 unnecessary. **Measure before building it.**
- **If still needed:** snap **after** the closing pull, or carry the snapped points as anchors of the second pull, with the same both-legs-clear and price guards the snap already applies.

---

## The build order for the branch

1. Build Part A (§A1, §A2) and its tests.
2. Build **B1**: the zone Soft source prices the collar through the one new function; nothing else in the field, the grid or the pull moves.
3. **Measure**, §Measurement below: the armed corridor wall clock with the collar, and the pass-by reading.
4. **B2** only if a reading or the device pass shows a bend still lost.
5. `apk-build.bat` green, and `avoid.*` plus `RouteAvoidEngineTest` green.

---

## Tests — each with the revert it must fail on

- **The pass-by regression, new and the point of the whole plan:** a zone whose edge lies 20 m from a straight line; with the collar armed the drawn line keeps at least `route.avoid.speedZone.marginM` from the ring. Shown red without the collar.
- **The standoff key's reach:** two fixtures at two `marginM` values, the kept distance following the key — so the collar's width is the key's and not a constant.
- **The cut regression:** `theLineDoesNotEnterAZoneItCouldHaveGoneAround`.
- **The hug test:** bends on the ring's offset corners, and no leg nearer the ring than the standoff.
- **The forced-crossing probe, kept green with the collar:** a zone that truly blocks the corridor still reports its crossing by name, and a route that legally crosses the collar reports nothing.
- **The clock, untouched:** a line inside the collar but outside the zone is timed at the pace, not at the zone's limit.
- **The band plus a zone sum.**

---

## Measurement and gates

- **The armed corridor budget, re-read with the collar** — the same gate as stage 1, on the same Lérins-to-Salis corridor; the baseline to beat is today's **170–225 ms** (zone read 139–182 ms of it) against the 500 ms wall. A wall-clock assertion, with the machine-speed exposure the stage-1 gate already carries.
- **The phone's factor is unmeasured for this engine** (the removed tracer's readings said ~54× desktop, a different engine's figures) — so the device pass, not the JVM figure, is what settles the budget.

---

## What stays out

- Change 4's fine band, the curve fitter (phase 6) and the markers (phase 5).
- Any Settings row; any new dependency; anything committed or pushed by the agent.
- Named so they are not absorbed: the ramp price, a collar on zone **holes**, and the corridor's own growth policy.

---

## Parked — not for implementation

> Context from the 2026-09-25 session the build does not read; kept for the record.

### A3 · The earlier review and its closures

- The `#implement` Ask hop returned **revise** on three tree findings — the switch shipping `true` against its own comment, its test asserting the code default rather than the shipped file, and an armed cost never measured. All three closed: the file ships `false`, the switch assertions read `maro.properties`, and the armed Lérins-to-Salis corridor measures **170–225 ms** against the 500 ms wall (the zone read being 139–182 ms of it, over the region's 23 rings · 387 vertices).

### A4 · What Part A does not give — measured

- A route that merely **passes beside** a zone keeps no standoff at all. Probe, on a synthetic zone whose north edge lies a chosen gap south of a straight line: **wanted 20 m → kept 20.0 m · 60 → 60 · 150 → 150 · 400 → 400**, each with **zero bends** (`points=2`, one 4 839 m leg). The standoff key of 50 m never applies.
- The companion probe **refuted** the other suspicion — that a digitized ring's corners are dropped by the guards: for every turn from 90° down to 5° the ring yields **one hug point per vertex**, each 50–70 m from its own vertex and **inside** the 100 m snap radius. Only needle-spikes (interior angle below ~5.7°) are dropped, by `MIN_SIN_HALF`.

### A5 · The carry-across list (superseded — the build here starts clean)

- **Modified, to carry as they stand** (10): `app/src/main/assets/maro.properties`, `app/src/main/java/ykws/android/maro/config/AppConfig.kt`, `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt`, `app/src/main/java/ykws/android/maro/spatial/avoid/AvoidGrid.kt`, `avoid/AvoidPull.kt`, `avoid/AvoidWorld.kt`, `avoid/TangentCorners.kt`, `app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt`, `app/src/test/java/ykws/android/maro/spatial/avoid/RouteZonePhase4Test.kt`, `avoid/TangentCornersTest.kt`, plus the record file `xTrack/Route/FEAT_DSC_Route.md`.
- **Untracked, to carry**: this plan and `260925_FEAT_PLN_Route_speed-zone-hug-alignment.md`.
- **To delete before any commit — the probe scaffolding** (it is diagnosis only):
  - `app/src/test/java/ykws/android/maro/spatial/avoid/ZoneHugProbeTest.kt`
  - the `probeTheStandoffOnAPassBy` block appended to `RouteAvoidEngineTest.kt` (keep every shipped assertion around it)
  - `zone-hug-probe.txt`
- **To reset**: `route.avoid.speedZone.enabled` back to `false` — the user armed it locally for the device pass, and while it is armed [`theSpeedZoneSwitchShipsDisarmed`](../../app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt:473) is **red**, which is the file-backed assertion doing its job rather than a regression.

### The device pass (owed, the user's)

- Arm `route.avoid.speedZone.enabled=true` locally, run the same route across the same zone, export it, and compare with `2026_09_25_21_40-Route_zonekc_2026-09-25_21_40-1.gpx` — the file that opened this work. What to look for: the line standing off the ring along the pass-by, not merely avoiding it.

### Provenance

- `260925_FEAT_PLN_Route_speed-zone-hug-alignment.md` — Part A's own plan, its review and its closures.
- [`260924_FEAT_PLN_Route_avoid-soft-sources-and-curves.md`](260924_FEAT_PLN_Route_avoid-soft-sources-and-curves.md:1) and [`260925_FEAT_PLN_Route_avoid-switches-and-zone-tangent.md`](260925_FEAT_PLN_Route_avoid-switches-and-zone-tangent.md:1) — the phases this fix completes.
- The two probes and their readings, §A4 — reproduced from `zone-hug-probe.txt` and the pure corner probe, both of which come out with the scaffolding.
