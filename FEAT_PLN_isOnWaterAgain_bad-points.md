# isOnWaterAgain — captured bad points

Collection of real demo-mode points where `isOnWater` is wrong, for later analysis.
Captured via `isWaterDbg2` (crossing breakdown) on branch `feature/isonwater-again`.

`span = mainlandLonMin..mainlandLonMax = [6.70011, 7.30994]` (constant for this dataset).
Decision: `(main + cap) % 2 == 1` ⇒ land; any ring with odd count ⇒ land; else water.

| # | lat | lon | got | expected | main | cap | rings | note |
|---|-----|-----|-----|----------|------|-----|-------|------|
| 1 | 43.542669 | 7.132332 | land | water | 10 | 1 | {} | Golfe-Juan marina; neighbors (odd main) read water |
| 2 | 43.425762 | 6.883299 | land | water | 2 | 1 | {} | Sainte-Maxime/St-Tropez gulf; just north (main=5) reads water |
| 3 | 43.420952 | 6.892723 | land | water | 6 | 1 | {} | one px west (lon 6.892695) = main=5 → water; +1 crossing flips it |
| 4 | 43.427546 | 6.893056 | land | water | 9 | 1 | {6:3} | RING-driven: mainland even (water), but ring #6 odd → land. Ring-6 count swings 6/3/5 nearby (complex port polygon) |
| 5 | 43.427697 | 6.892871 | land | water | 16 | 1 | {} | mainland even (16) + cap → land |
| 6 | 43.423955 | 6.893667 | land | water | 10 | 1 | {6:6} | mainland even (10) + cap → land; ring 6 even (not the cause) |
| 7 | 43.422070 | 6.892739 | land | water | 6 | 1 | {} | mainland even (6) + cap → land |
| 8 | 43.425924 | 6.883555 | land | water | 2 | 1 | {} | mainland even (2) + cap → land; same area as #2 |
| 9 | 43.448836 | 6.921708 | land | water | 14 | 1 | {} | mainland even (14) + cap → land; new area NE |
| 10 | 43.451877 | 6.921650 | water | land | 15 | 1 | {} | INVERSE: mainland odd (15) + cap → water, should be land |
| 11 | 43.542328 | 7.132366 | land | water | 10 | 1 | {} | Golfe-Juan (near #1); lines just east (lon 7.13242) = main=9 → water — narrow vertical band |

## Confirmed mechanism (segment dump at #1/#11, 2026-06-07)

At lat=43.542654, sweeping lon across the band edge:
- lon 7.132434 / 7.132425 → main=9 (water), 9 segments crossed.
- lon 7.132380 → main=10 (land), same 9 **+ seg4323**.

The spurious extra crossing: `seg4323 (7.132415,43.543995)->(7.132329,43.543896) dLon=-0.0000858 yc=43.543955`
— a ~7 m-wide, ~11 m-tall **near-vertical sliver** ~150 m north. Counted only while the query
longitude is inside its tiny span [7.132329, 7.132415]; east of 7.132415 it drops → water.

**`adjColExtra = 0`** in all dumps ⇒ NOT a grid-column miss; the grid + division-free crossing
test are correct. Root cause = **structural degeneracy of the vertical counting ray against
near-vertical coastline**: a vertical ray samples the coast at one longitude, so a near-vertical
feature whose two sides sit at slightly different longitudes is crossed an odd number of times in
the gap between them → off-by-one parity in a thin vertical band. Tweaking the ray cannot fix this.

**Decision → Option 2 (nearest-segment side test):** no ray, so no ray/coast alignment degeneracy.

## Option 2 residual failures (nearest-segment, 2026-06-07)

On-load self-check of the 11 points: **8 fixed** (incl. the original vertical-band #1), **3 still wrong** —
all where the nearest feature is a **RING** (`mainland=false`, `poly>0`), i.e. a marina/port outline.
Plus a wider band captured live at the Cap d'Antibes "hole" (judged by the live land/sea icon, not the
cached band). Side test is unreliable on complex/concave rings: open water near a ring lands on the
ring's "inland" side.

| # | lat | lon | got | exp | nearest poly | dist (m) | closest | note |
|---|-----|-----|-----|-----|--------------|----------|---------|------|
| 4  | 43.427546 | 6.893056 | land | water | ring 6  | 2.4   | (43.427525,6.893062) | ~on a pier (near-boundary, ambiguous) |
| 6  | 43.423955 | 6.893667 | land | water | ring 53 | 86.6  | (43.424454,6.894490) | open water mislabelled by small ring |
| 11 | 43.542328 | 7.132366 | land | water | ring 40 | 160.1 | (43.543728,7.131903) | ring 40 (Golfe-Juan/Antibes W) |
| 12 | 43.542198 | 7.131013 | land | water | ring 40 | 184.6 | (43.543728,7.131903) | Cap d'Antibes "hole"; same ring 40, wider band |
| 13 | 43.542419 | 7.131997 | land | water | ring 40 | 145.8 | (43.543728,7.131903) | same ring-40 vertex (vIdx=0) as #11/#12 — corner rule lands a wide SW wedge |

**Key:** #11/#12/#13 all have the **identical** closest point `(43.543728,7.131903)` = ring 40 `vIdx=0`.
A wide open-water wedge SW of that single ring vertex is nearest to it, and `cornerWater` decides
land there. The band extends east to ~lon 7.133 (≈180 m wide). So the band's width = the angular
extent of that vertex's nearest-region.

## Root cause & fix (analysis 2026-06-07)

The nearest-segment **side test is fundamentally wrong for rings**: "near a ring edge, on its inland
side" is NOT the same as "inside the ring". Open water beside/around a small ring is nearest to that
ring, and the side/corner test puts it on the ring's land side → wide spurious land band. (In the OLD
ray method these same points had `rings={}` = 0 ring crossings = correctly OUTSIDE ring 40.)

### Update — winding dump + degenerate-ring filter (2026-06-07)

Ring-winding dump: rings 40 & 53 are **n=3, area=0 degenerate slivers** (ring 40 = the seg4323 sliver)
mis-flagged as rings; their "side" is meaningless → #1/#11/#6 bands. Filtering degenerate rings
(`<4 pts or |area| < 1e-9 deg²`) from the index **fixed #6**, but #1/#11 hopped to the next tiny
marina ring (**ring 41**, non-degenerate) and still read land. Whack-a-mole confirms the structural
issue: **small marina sub-structures (breakwaters/pontoons/basins) must not decide open-sea land/water
— the mainland must.**

### Update 2 — mainland-primary hybrid implemented; root cause = data spike (2026-06-07)

Mainland-primary hybrid (mainland side test OR inside real CCW island; degenerate rings filtered) is
implemented. Self-check: **8/11** again — #6 fixed, but #1/#11 still land and now the breakdown shows
`mainLand=true island=false mainPoly=41`. Polyline 41 is OPEN mainland (not a ring) and **contains
`seg4323`** — the ~7 m near-vertical **spike** at (43.5439,7.1319), the same artifact from the first
diagnosis. It has now appeared as: a ray crossing → degenerate ring 40 → a segment of open polyline 41.

**Conclusion:** the stubborn residuals (#1,#11 = the seg4323 spike; #4 = on a pier at 2.4 m) are
**coastline DATA artifacts** (tiny near-vertical spikes/needles at marinas), not a classifier bug.
Any nearest-feature test that picks a 7 m spike gets a meaningless side. Chasing it between
representations is whack-a-mole.

**Right fix = data cleaning:** a spike/needle-removal pass on the coastline polylines (drop vertices
forming tiny out-and-back excursions; or upstream in the generator). That removes the artifact for
every test at once. The broad problem (mainland vertical bands) is already solved by the hybrid.

### Final direction — MAINLAND-PRIMARY hybrid

`land = (nearest MAINLAND segment side test == land) OR (inside a real island)`
- Mainland side test = base answer (fixes the original vertical bands).
- Real islands only: non-degenerate, CCW (interior land), via even-odd containment → land.
- Marina rings / basins / degenerate slivers no longer flip open water to land (a boat *inside* a
  tiny basin may be mislabelled = at-dock, negligible). Keep the degenerate-ring filter too.

---
(earlier note, superseded by the mainland-primary direction above:)

**Fix = HYBRID:**
- **Mainland (open polyline):** nearest-segment side test — keep; it fixed the vertical-band degeneracy.
- **Rings (closed):** even-odd **containment** (point-in-ring), which is winding-INDEPENDENT and robust,
  so it doesn't matter how ring 40 is wound. A point outside a ring → water; inside → land.
- `land = mainlandSideTest==land  OR  insideAnyRing(even-odd)`.
- Cast the ring-containment ray **off-axis** (not due N/S) to avoid the near-vertical-tangent thin-band
  on marina pier walls (the issue that made the old ring parity unstable for on-structure points like #4).

8/11 originally-captured points now correct; mainland classification fixed. **Residual = rings.**
Candidate fix: hybrid — nearest-segment side test for the mainland; **robust closed-polygon
containment for rings** (rings are closed, so even-odd point-in-ring is winding-independent; use a
non-axis-aligned ray to avoid the near-vertical tangent thin-band).
