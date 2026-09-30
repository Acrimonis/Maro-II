<!-- scope: feature -->
# Route — the avoidance algorithm: design of record

**Scope:** the avoid engine's algorithm, folded 2026-09-30 from the eight phase and zone plans whose
work shipped. Facts and rationale only — the values live in `maro.properties`, this file names the
roles and the decisions.

## The pipeline

One solve in [`RouteAvoidEngine.searchOnce`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt) runs:

1. **Corridor** — the start-aim box inflated by `route.avoid.corridor.reachM`, clamped to the region's
   bounds; a truncated box is accepted (it is the searchable water, not a refusal).
2. **Harvest** — `segmentsIn` (ring/basin edges, each with its orientation) and `openCoastIn` (ordered
   mainland polylines). A CCW ring is land, a CW basin stays water.
3. **Rasterize** — one tagged, costed cell state (`FREE`/`LAND`/`BAND`/`ZONE` plus a source cost),
   margin band over every edge, even-odd fill of CCW interiors, open coast closed on its land side.
4. **Ends** — both end cells force-freed, their discs exempt in the pull; an off-water end is refused
   before the search (now the ring-sweep repair moves it instead).
5. **A\*** — eight neighbours, `√2` diagonal, a metres-equivalent g-cost so the haversine heuristic
   stays admissible, a deterministic tie-break, a cancellation check every few hundred expansions.
6. **Taut pull** — the two-pointer string-pull, sampled at `≤ margin / 2`, source-parameterized so the
   band and the zones ride the same predicate.
7. **Corner snap** — each pulled bend moves onto its nearest tangent corner only while both neighbouring
   legs stay clear, then the line is pulled again.

The pipeline runs on `Dispatchers.Default`. Post-search stages — the λ loop, the fine pass, the
fairing and the forced-crossing probe — spend none of the A\*'s budget.

## The one architecture move — a unified cost field

The rasterizer, the A\* and the pull once read three different models; they were forced onto one
[`RouteCostField`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteCostField.kt): a list of
`RouteCostSource` — `HARD` (a wall) or `SOFT` (priced, passable) — evaluated per point by the four
consumers. Every source **adds** to a base; none may lower it, so no passable cell is ever below base
(the trap is a zero default, the base being explicit and replacement forbidden).

- **The unit is seconds**, not metres-equivalent: the base is `cellM / v(pace)`, a zone term is the
  cell's time excess `cellM × (1/v(limit) − 1/v(pace)) × λ`, and the A\* heuristic is
  `haversine / v(pace)` so it stays admissible. The ETA stays λ-free — the clock reads the limit in
  force, never the price.

## The sources

- **Depth gate** — a bilinear read per cell centre; a known depth below `route.avoid.depthGate.minM`
  paints the cell land, ANDed with the coastline's water. NoData, coarse and deeper water are ignored —
  it is a coarse guard, not a fine sounding. The fairing keeps `route.avoid.depthGate.marginM` off it.
- **300 m band** — a price, never a wall: a start already inside is priced, not refused. It carries its
  own absolute limit (`route.avoid.zone300.limitKn`) and is priced by the **same law as a speed zone** —
  that limit's time excess under the price cursor, so a 5 kn band cell and a 5 kn ring cell cost the
  same — the width in full and its outside margin at the configured fraction. Its **limit lives on the
  grid**, priced per expansion by the A\* exactly as a ring's, so the band's price follows the corrected
  λ and the strictest limit wins where the band and a ring overlap; `route.avoid.zone300.enabled`
  switches that price. The limit is read by the clock **whatever that switch says**, a limit being law
  and the switch only pricing water.
- **Speed zones** — a soft source priced per read; the grid stores the **limit** per cell, never a
  finished price, so a λ change costs one multiply per cell. Excluded ids are dropped before fill,
  ETA and report. Switched by `route.avoid.speedZone.enabled`.
- **Marker weights (phase 5)** — parked; avoid-only `w ∈ [0, +10]`, a marker can only make the sea
  dearer, never cheaper (a negative price breaks the closed-set A\*).

## The λ loop and the budget

One solve builds its grid **once** and stores the band's and the rings' **limits** on it, priced at read
time. Pass one seeds λ from `route.avoid.speedZone.softCostAversion`; the line's own clock gives its slow
time split three ways — the **zone share** inside a ring, the band share inside the band's width, and the
ramp share on the approach ramps standing outside both — and the zone share alone is corrected: outside
the ±20 % band of `route.avoid.speedZone.timeBudgetPct` one correction `λ₁ = λ₀ × (zone share / budget)`
runs, then a second solve, then stop (**two passes cap**). The **better of the two passes is kept** — lower
zone share first, then fewer metres inside a zone, then the shorter clock — so a correction that answers
worse cannot replace the line pass one found, and only the kept pass's λ reaches what follows it. A share
still out is **reported** (`budgetUnmetZoneShare`, the zone share), never chased and never refused.

## The standoff, and its retirement

The device disproved the standoff twice (a 10 kn zone off Cap d'Antibes, passages tens of metres wide
between a ring and the shore), so it became a **price, never a clearance**: the zone's outside margin
(`route.avoid.speedZone.outsideMarginM`) prices the ring around the interior at a fraction
(`…outsideMargin.costFraction`) of the interior's excess, and the band does the same. The staged
full→half→none clearance ladder and the signed ring-distance read were deleted. A crossing is reported
**forced** only where no way around exists — probed once on a copy of the grid with the restrictive
zones blocked.

## The corner mechanism

The corner-graph A\* measured **7.3 s over 191 corners**; the greedy tangent walk was jittery on the
real coast; the shipped pass is **grid A\* + verified corner snap + second pull** — the grid owns the
order, the tangent corners own the exact points.

## The fine pass and re-search (D8)

The coarse cell closes any passage narrower than roughly two cells. The fine pass re-rasterizes a swath
around the settled line at `route.avoid.fine.cellRatio` (0.40 → 20 m at 50 m) and keeps the fine line
**only where it is strictly faster and no worse in slow share** — the clock alone would let the tail undo
the λ loop's own choice; the crossing's local A\* re-solves a zone the line enters, inside the zone's own
box, spliced only where it answers, and its price is the loop's own λ, so that splice is price-driven
rather than clock-driven.

## The corridor growth

`search()` retries once with the reach doubled on **no-path, budget or crossing**, keeps the wider
answer only where it does better, and refuses any growth once the first box already equals the region's
bounds.

## The fairing (phase 6)

A post-search stage that turns the grid path into a navigable trajectory — spiral → arc → spiral, the
radius the largest that clears the walls capped by the pace's own radius, the corner speed
`min(cap, max(decelFloor, minSpeedKn))`, the clearing radius found by **24-step bisection**. The
route's figures are the **pre-fairing base plus the bends' caps' delta** — the geometry is never
re-costed. The forced-crossing probe reads the pre-fairing line.

## The evidence and the budget

- Stage 1 on the Lérins-to-Salis corridor: **~120–250 ms realistic, ~350–450 ms pessimistic** against
  the 500 ms wall; the swing term is the rasterizer's island-adjacent water confirmation.
- The armed corridor read **170–225 ms**, the zone read 139–182 ms of it over 23 rings · 387 vertices.
- The corner-graph A\*: 7.3 s over 191 corners.
- The device factor for this engine is unmeasured; the JVM figure is a ceiling, the phone is the gate.

## The next-chunk frame — a regional coarse grid

The parked alternative dissolves the reach, the growth and the box-dependence in one move: one coarse
grid over the whole region (200–300 m), kept rather than rebuilt per ask, with a per-ask A\* over it
and the fine pass promoted to the substantive half. First ask ≈ 1.05 s, later asks ≈ 0.5 s, against
today's grown 3.3 s — and "no path" / "forced" become statements about the region's own water, so the
standoff register's F1 and F2 become true by construction.

## Provenance

Folded from [`260924_FEAT_DOC_Route_avoid-algorithm-phase1.md`](xxArchive/260924_FEAT_DOC_Route_avoid-algorithm-phase1.md),
[`260924_FEAT_PLN_Route_avoid-land-stage.md`](xxArchive/260924_FEAT_PLN_Route_avoid-land-stage.md),
[`260924_FEAT_PLN_Route_avoid-soft-sources-and-curves.md`](xxArchive/260924_FEAT_PLN_Route_avoid-soft-sources-and-curves.md),
[`260924_FEAT_PLN_Route_tangent-dichotomy-prototype.md`](xxArchive/260924_FEAT_PLN_Route_tangent-dichotomy-prototype.md),
[`260925_FEAT_PLN_Route_avoid-switches-and-zone-tangent.md`](xxArchive/260925_FEAT_PLN_Route_avoid-switches-and-zone-tangent.md),
[`260925_FEAT_PLN_Route_speed-zone-full-fix.md`](xxArchive/260925_FEAT_PLN_Route_speed-zone-full-fix.md),
[`260926_FEAT_PLN_Route_speed-zone-standoff-alternatives.md`](xxArchive/260926_FEAT_PLN_Route_speed-zone-standoff-alternatives.md) and
[`260926_FEAT_PLN_Route_regional-coarse-grid.md`](xxArchive/260926_FEAT_PLN_Route_regional-coarse-grid.md) —
all archived 2026-09-30. The removed taut tracer and mesh engine survive in
[`260922_FEAT_DOC_Route_taut-tracer.md`](xxArchive/260922_FEAT_DOC_Route_taut-tracer.md) and
[`260922_FEAT_DOC_Route_mesh-engine.md`](xxArchive/260922_FEAT_DOC_Route_mesh-engine.md).
