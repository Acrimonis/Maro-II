<!-- scope: feature -->

# 300 m Line — Design

Design notes for the **Zone300 / drawZone** subfeature: deriving, drawing, and
querying the regulatory 300 m band (French *bande des 300 mètres*, 5-knot speed
limit) along all coastlines, islands included.

## Summary (ELI16)

**The functional case (the "why")**
- Within **300 m of any shore** (mainland *and* islands), boats must stay at
  **5 knots**.
- The app already knows, for wherever you are, **how far the nearest land is**.
- Skippers need to *see* that 300 m limit on the map and get a **number** for how
  close they are to it.

**Drawing the line (the "how")**
- Imagine **spray-painting the coast outward in a 300 m-thick strip**: the strip
  is the slow zone, its outer edge is the 300 m line.
- We build it from the distance the app already knows: **mark every sea point
  exactly 300 m from its nearest land, connect them**.
- It **can't cross itself** (each point has only one "nearest land"), it's a
  clean fence between inside/outside 300 m.
- Where two shores are close (narrow channel, clustered islands), the strips
  **merge into one**.
- Corners come out **rounded automatically** — natural for navigation.

**Making it pretty & fast**
- The raw line is **stairstep-jagged** (sampled on a grid).
- **Douglas–Peucker** drops redundant points → fewer points = faster map.
- **Chaikin** rounds the leftover corners → a clean, navigable curve.
- Computed **once and cached**, so panning/zooming stays smooth.

**The two numbers exposed**
- **`isIn300mZone()`** → "Am I in the 5-knot zone?" = on water **and** nearer
  than 300 m.
- **`distanceTo300mZone()`** → one signed number, shown two ways: *"Distance to
  the zone"* when outside, *"Distance before end of zone"* when inside. Shown in
  metres, switching to km past 1000 m.
- It's a **visual aid**, so within **~5 %** (≈ ±15 m) is fine — the number and the
  drawn line always agree.

**Parked for later:** real-world exceptions like marked harbor channels — v1
draws the clean geometric band only.

## Problem

The app already computes distance-to-coast (any coast, islands included). This
feature adds a regulatory band 300 m seaward of every coastline, drawn so it is
identifiable on the map, plus two query APIs: `isIn300mZone()` and
`distanceTo300mZone()`.

## Core principle — the line *is* the metric

The 300 m boundary is, by definition, `{ points where distanceToCoast == 300 }`
— the same spatial index the boat marker already uses
(`CoastlineSpatialIndex.kt`). Treating the line as a visualization of that
threshold (rather than an independent geometry) guarantees the map can never
disagree with the logic.

We build the line directly from `distanceToCoast` (distance to the *nearest*
point of any coast), **not** by perpendicular offset of the coastline. Because
the distance field is single-valued (every sea point has exactly one
nearest-land distance), its 300 m contour is the fence between "< 300 m" and
"> 300 m" and **cannot self-intersect**; offset lines from opposite shores would
cross in narrow bays/straits and collapse small islands. The contour also
**rounds corners for free** — convex capes bow out as arcs, concave bay corners
turn as arcs of points 300 m from the corner — so the geometry is already partly
smooth; added smoothing only de-stairsteps the grid sampling.

**Accuracy:** this is a *visual* control — an approximation within ~5 % (≈ ±15 m)
of true 300 m is acceptable; the grid resolution is sized to hold that (below).

Both query APIs therefore fall out of the existing metric, O(1), with **no
dependency on the drawn polygon**:

```kotlin
fun isIn300mZone(lat, lon): Boolean =
    isOnWater(lat, lon) && distanceToCoast(lat, lon) <= 300.0

fun distanceTo300mZone(lat, lon): Double =
    distanceToCoast(lat, lon) - 300.0   // signed: + outside (approaching), − inside
```

## Geometry method — distance-field isoline (chosen)

Two approaches were considered:

- **A — Parallel offset** of the coastline polylines (push each vertex 300 m
  along the seaward normal). Light, but collapses in bays/straits narrower than
  ~600 m and around small islands, self-intersects at concave corners, and can
  wander onto land. Rejected.
- **B — Distance-field isoline (chosen).** Contour the predicate
  `isOnWater && distanceToCoast <= 300` via marching squares. Topology is
  automatically correct: opposite shores and clustered islands **union/merge**
  on their own (satisfies the "merge intersecting zones" rule), no
  self-intersection cleanup, and the result is by construction the same
  threshold the APIs use.

### Precompute pipeline (one-time per region, cached)

Runs at coastline-load (background, alongside `CoastlineGenerator`); nothing
recomputes on pan/zoom. Caching/rebuild follows the **same logic as coastline
generation** (same key, same region-change trigger).

1. **Restrict to a near-coast ribbon.** Only the **0–500 m** strip hugging the
   coast can contain the band, so we lay the grid on that ribbon only — never the
   open sea. Candidate cells are found cheaply from the coastline geometry
   *before* the distance math. (500 m = 300 m band + a clean "outside" margin so
   marching squares can find the contour; beyond 500 m is ignored for drawing.)
2. **Sample a binary mask** over the ribbon: cell is in-zone if
   `isOnWater && distanceToCoast <= 300`. **Uniform grid, no variable
   resolution.** Spacing = `min(CoastlineGenerator resolution, 15 m)` → ≤ ±7.5 m
   quantization, comfortably inside the 5 % budget, while reusing the existing
   resolution where it is already fine enough. Every closed coastline ring
   (mainland or islet) gets a band — no minimum-size filter.
3. **Marching squares** on the mask → closed polygon rings that already exclude
   land and already merge. The ring *is* the 300 m line; the enclosed area *is*
   the regulated band.
4. **Simplify → smooth** (seaward edge only — see below).
5. **Render in osmdroid**: `Polygon` overlays for the translucent fill + the same
   rings as `Polyline` for the crisp **red** boundary, both using the **same
   renderer/style path as the coastline overlay**. Built once, added to
   `mapView.overlays`, rebuilt only on region change.

## Rendering — line + shaded fill

Chosen visual: **red boundary line + translucent fill** between the line and the
coast, so the whole regulated area reads as one zone. The line and fill use the
**same rendering path/style as the coastline overlay**. The fill is bounded
landward by the **actual coastline** (flush, never on land) and seaward by the
computed 300 m line. Performance is a hard constraint — the cost is osmdroid
drawing N cached vertices per frame, so vertex count is the lever (see smoothing).

## Smoothing — simplify then smooth (seaward edge only)

A marching-squares ring has two kinds of edge: the part **hugging the coast** and
the part out at **300 m**. We smooth/simplify **only the seaward (300 m) edge**;
the landward edge is **snapped to the actual coastline vertices** (the same ones
the coastline overlay draws). This keeps the fill flush with the coast and makes
it impossible for smoothing to nudge the zone onto land. Where two shores are
< ~600 m apart the band fills the whole gap — there both edges are 300 m lines.

The two operations are **opposite** and both are needed, in order:

1. **Douglas–Peucker (simplification)** — *removes* vertices within ε of the
   original. Performance lever; kills the marching-squares stairstep. Alone it
   stays angular (keeps spiky extremes). ε ≈ grid resolution (~10–25 m). ε is
   the cap on how far the drawn line can drift from true 300 m — keep it well
   under the 300 m band width.
2. **Chaikin ×1–2 (smoothing)** — *rounds* corners into a navigational curve.
   Readability lever. Adds vertices (≈2× per iteration) and biases corners
   slightly inward. Start from the simplified ring so it stays cheap.

Alternative if DP looks jagged: **Visvalingam–Whyatt** (area-based) tends to
look more natural on organic coastlines than DP.

Note: `CoastlineGenerator` already has a clip+simplify step — reuse its
simplification utility rather than writing fresh; the 300 m line inherits the
coastline's resolution.

## Distance metric — single source of truth (decided)

Decision: **one metric, `distanceTo300mZone() = distToCoast − 300`.** Since this
is a visual control with ~5–10 % tolerance, the analytic metric is good enough
and the drawn line is just its visualization — no second source of truth.

A second option — distance to the *drawn* (smoothed) polyline via a second
spatial index over the line's vertices — was considered and **rejected**: it is
cheap to build (reuse `CoastlineSpatialIndex` on the line segments) but would
make the map line, `isIn300mZone()`, and the readout able to disagree. Not worth
it at this tolerance.

The small deviation of `distToCoast − 300` from true distance-to-the-isoline at
sharp concave corners is within tolerance and a non-issue.

## `distanceTo300mZone()` semantics (decided)

Signed value, `distToCoast − 300`, labelled by the UI:
- **Outside the band** (positive): *"Distance to the zone"* — how far until the
  5-knot zone begins.
- **Inside the band** (negative, magnitude = `300 − distToCoast`):
  *"Distance before end of zone"* — how far seaward to exit.

Displayed in **metres, switching to kilometres above 1000 m**. The metric is
analytic and grid-independent, so it is valid at any range (e.g. far offshore),
even where no line is drawn.

## Open questions / out of scope

- **Real-world exceptions** — the actual band has marked channels (*chenaux*)
  and harbor/port carve-outs. Proposed: pure geometric band for v1; channels/
  ports deferred (not modeled in the OSM coastline data anyway).

## Key files (design targets)

| Aspect | File |
|--------|------|
| Spatial index / distance queries | `spatial/CoastlineSpatialIndex.kt`, `data/.../CoastlineRepository.kt` |
| Water/land (ray-casting) | `CoastlineRepository.kt` (`isOnWater`) |
| Coastline gen + existing simplify | `data/coastline/CoastlineGenerator.kt` |
| Map rendering / overlays | `ui/map/MapScreen.kt` (`drawCoastline`, `CoastlineMapView`) |
| Throttled recompute pipeline | `ui/map/CoastlineViewModel.kt` |
