<!-- scope: feature -->

# isOnWater — Nearest-Segment Side Test (Option 2)

Design + implementation plan for replacing the vertical-ray containment test in
`CoastlineSpatialIndex.isWater` with a **nearest-segment side test**.

Feature: [[isOnWaterAgain]]. Captured failing points: `plans/isonwater-bad-points.md`.

---

## 1. Context — why we are replacing the ray

The current `isWater` casts a **vertical ray north** and counts coastline crossings
(even-odd parity, closed by a virtual inland "cap"). After fixing the numerical blow-up
(division-free crossing test, already merged on this branch), points were **still** mis-classified
in **narrow, vertical bands** at marinas (Golfe-Juan, Saint-Tropez / Sainte-Maxime ports).

A segment dump at one failing band (point #1/#11) **proved the mechanism**:

```
land band has one extra crossing:
seg4323 (7.132415,43.543995)->(7.132329,43.543896)  dLon=-0.0000858  yc=43.543955
adjColExtra = 0   (NOT a grid miss)
```

`seg4323` is a ~7 m-wide, ~11 m-tall **near-vertical sliver**. A vertical ray samples the coast at a
single longitude, so a near-vertical feature whose two sides sit at slightly different longitudes is
crossed an **odd** number of times in the thin gap between them → off-by-one parity. This is a
**structural degeneracy of casting a vertical ray through near-vertical coastline**, not a numerical
or grid bug. No tweak to the ray can remove it; this coast is full of north–south harbour walls.

## 2. Approach

Classify by the **nearest coastline segment** and the coastline **winding convention** already
documented in `CoastlineSegment`: *water is on the RIGHT of the direction of travel (A→B)*. No ray,
so no ray/coast alignment degeneracy.

```
isWaterByNearest(P):
  if !hasData: return WATER                      # safe default
  ref, Q = nearest segment to P and the closest point on it   # reuse nearestRef/query
  if P beyond 6 NM of coast: return WATER         # existing short-circuit (caller-level)
  if Q is interior to the segment:
      return side(P, ref.a, ref.b) == RIGHT ? WATER : LAND
  else:                                           # Q is a shared vertex → corner case (§4)
      return cornerClassify(P, ref.polylineIdx, ref.vertexIndex)
```

**Side test (planar, division-free):** in local metres (x = east, y = north, the same mid-latitude
projection `pointToSegmentDistance` already uses),

```
cross = (Bx - Ax)*(Py - Ay) - (By - Ay)*(Px - Ax)
cross < 0  ⇒ P is to the RIGHT of A→B ⇒ WATER
cross > 0  ⇒ LEFT ⇒ LAND
```

> The sign mapping (right = water) **must be validated** against a known point — e.g. for the
> west→east mainland, a point just **south** of the coast is water and must yield `cross < 0`. If the
> data's winding is the opposite, flip the comparison (single constant).

## 3. Reuse — near-zero added cost

- `CoastlineSpatialIndex.nearestRef()` / `query()` already do the ring-expanding nearest-segment
  search and return `closestPoint`, `polylineIdx`, and `vertexIndex` (start vertex `i`; segment is
  `points[i]→points[i+1]`).
- The **live map pipeline already calls `query()` every frame** (for `distanceMeters`). So the water
  flag can be computed **inside `query()`** from data it already has → one search, not two. Proposed:
  add `isWater: Boolean` to `CoastlineDistanceResult`, set it in `query()`, and have
  `CoastlineRepository.isOnWater` read it (keeping the `> 6 NM ⇒ water` short-circuit).
- `SpatialOperations.projectPointOntoSegment` already gives `Q`; the clamp parameter `t∈{0,1}`
  identifies the vertex case. Add a small `SpatialOperations.signedSide(p, a, b): Double`.

## 4. Edge cases & risks

- **Corner / shared vertex** (`Q` at `points[i]` or `points[i+1]`): the single-segment cross test is
  ambiguous. Resolve with the two incident edges `e_in = prev→V`, `e_out = V→next` (fetched via
  `segmentsById[polylineIdx].points` and `vertexIndex`):
  - `waterIn = side(P, e_in) == RIGHT`, `waterOut = side(P, e_out) == RIGHT`.
  - `turn = cross(e_in, e_out)` ⇒ convex vs reflex land corner.
  - Convex land corner: `LAND ⇔ !waterIn && !waterOut`; reflex: `LAND ⇔ !waterIn || !waterOut`.
- **Orientation correctness (primary risk):** the whole method depends on consistent winding. One
  backwards-digitised stretch flips that spot. Mitigation: the oracle sweep + the 11 captured points
  as regression; add a one-time winding self-check log on index build.
- **Islands (rings):** must follow the same convention (water outside, land inside). If ring winding
  is opposite to the mainland, the sign needs per-ring handling. Verify on real data.
- **Narrow channels / marina basins:** the nearest segment can be the opposite bank; a basin interior
  may resolve as water or land depending on whether it is traced. Possible residual edge case —
  documented, and far better than today's vertical bands.
- **Zone300 band dependency:** `Zone300Builder` derives the band from a column/south-ray
  classification (`queryColumn` / `rayCrossesSegmentSouth`). For the band edges and the at-sea
  indicator to agree, the band generation should use the **same** nearest-segment classification (or
  be verified consistent). Flag for the implementation step; **regenerate the band** afterwards.

## 5. Implementation steps

1. `SpatialOperations`: add `signedSide(p, a, b): Double` (planar cross product); unit-test
   left/right/on-line. (Keep `rayCrossesSegment*` for now — used by Zone300 prebake / oracle.)
2. `CoastlineSpatialIndex`: add `isWaterByNearest(lat, lon)` using `nearestRef` + the corner rule
   (§4). Replace `isWater`'s body with it (keep the old ray method privately behind a flag during
   A/B dev, then remove).
3. `CoastlineDistanceResult`: add `isWater: Boolean`; compute in `query()`. `CoastlineRepository`:
   `isOnWater` reads it; keep `> 6 NM ⇒ water` and `no-data ⇒ water`.
4. Consistency: make Zone300 band classification use the same side test (or prove equivalence).
5. Remove the temporary diagnostics `isWaterDbg2` / `isWaterDmp`; keep `isWaterDbg` for on-device
   re-verification, then remove once green.

## 6. Verification

- **Unit:** `signedSide` cases; corner cases (convex + reflex); a synthetic **near-vertical sliver**
  fixture reproducing the seg4323 geometry — old ray fails, new side test passes.
- **Oracle sweep** (`CoastlineSpatialIndexWaterTest`): the side test must agree with the independent
  closed-polygon point-in-polygon oracle on synthetic polygons (ensure their winding matches the
  convention). Include an **island/ring-present** case (guards the recurring Zone300 land-mirror).
- **On-device:** re-check the **11 captured points** via `isWaterDbg` — all must read correctly.
  Regenerate the 300 m band and visually confirm the vertical bands are gone.

## 7. Rollback

The change is contained to `isWater` / `query` / `CoastlineDistanceResult` + one `SpatialOperations`
helper. The ray method stays in source until the side test is validated, so reverting is a one-line
switch back.
