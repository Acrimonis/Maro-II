---
name: isOnWaterAgain
status: done
created: 2026-06-07
modified: 2026-06-08
active_subfeature: none
subs_total: 0
subs_done: 0
one_liner: Fixed inverted isOnWater + 300 m band at marinas via a mainland-primary classifier (nearest-mainland side test OR real-island containment) plus coastline data cleaning (drop tiny fragments / degenerate rings) and a capOpenEnds boundary-only fix; validated by tests + a rebaked asset.
---

# Feature: isOnWaterAgain

**Description:**
`isOnWater` occasionally returns the inverted water/land result, most often for points just
south (seaward) of a small near-vertical coastline segment. Root cause: the per-segment
crossing test `rayCrossesSegmentNorth` computes the intersection latitude by dividing by
`dLon` (longitude span of the segment); for a near-vertical segment `dLon → 0`, so the
division amplifies floating-point error and flips the even-odd parity. The fix rewrites that
test to be division-free (cross-product / orientation comparison) — same vertical-north ray,
same cap-closure architecture, same half-open vertex de-dup rule. Verification uses a
temporary `isWaterDbg` Logcat line driven by demo-mode panning to capture concrete failing
`lat/lon`, which become permanent regression cases.

## Subfeatures

## Todos
- [x] Division-free crossing test (necessary baseline; insufficient alone).
- [x] Diagnose: residual errors are tiny coastline DATA artifacts at marinas (seg4323 13 m spike, degenerate & CW rings), not a classifier bug.
- [x] **Final classifier** — `isWater` = mainland-primary hybrid: nearest-MAINLAND side test (`signedSide`/`classifyWater`/`cornerWater`) OR inside a real CCW island (`insideRealIsland`, bbox-filtered PNPOLY).
- [x] Index cleaning: drop degenerate rings (≤3 pts / ~0 area) + tiny open fragments (<30 m); expose `usableSegments`.
- [x] Band: in-app builders + the offline prebake now use `index.isWater` + `index.usableSegments`; `capOpenEnds` seals only region-clipped ends (no interior-junction spikes).
- [x] Rebake the bundled asset `app/src/main/assets/coastline/nice-frejus.bin` with the new logic.
- [x] Tests: signedSide; convex/reflex corner; tiny-fragment / degenerate-ring / CW-basin isWater; oracle sweep; `BandValidationTest` (no-spike guard on the real asset). All green.
- [x] Strip all temporary diagnostics.
- [ ] (Deferred / optional) Clean the spikes upstream in the coastline generator too; on-structure (<30 m) points like #4 remain inherently ambiguous.

## Rules
- Approach pivoted to **Option 2 (nearest-segment side test)** — the vertical-ray + cap containment is structurally degenerate against near-vertical coast (confirmed via segment dump). The division-free ray fix is kept as a baseline but is insufficient alone.
- Preserve the half-open longitude straddle (`<=` lower, strict `<` upper) — it de-dups shared vertices.
- The division-free test must stay algebraically identical to the old `< rayLatStart` / `<= rayLatEnd` rules.
- Anomalies manifest as narrow, typically vertical bands a few metres wide in longitude — nudging slightly east/west gives the correct result. Signature of the vertical counting-ray degenerating against near-vertical coastline (breakwaters, piers, steep capes).

## Key Files
- `app/src/main/java/ykws/android/maro/spatial/SpatialOperations.kt` — `rayCrossesSegmentNorth` (the fix).
- `app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt` — `isWater` containment caller.
- `app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt` — `isOnWater` (debug log spot).
- `app/src/test/java/ykws/android/maro/spatial/SpatialOperationsTest.kt` — ray unit tests.
- `app/src/test/java/ykws/android/maro/spatial/CoastlineSpatialIndexWaterTest.kt` — oracle sweep / regressions.

## Docs
- `docs/isOnWater-nearest-segment-design.md` — Option 2 design + implementation plan (nearest-segment side test).
