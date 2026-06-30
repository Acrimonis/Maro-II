# whereAmI() — Segment-Intersection `closestUnblockedPoint` (Final)

> **Feature:** Markers | **Subfeature:** whereami-zone-los
> **Created:** 2026-06-30 | **Updated:** 2026-06-30 15:52 | **Status:** Implemented

## Requirements

### Eligibility

Boat must satisfy at least one of:
- **Zone (geometric):** boat is inside the marker's geometric zone → `ZoneMatch` (no land test)
- **Proximity zone:** boat's direct distance to geometry ≤ proximity range → eligible for line-of-sight check

### Line of Sight

If eligible via proximity zone (not already inside zone):
- Any clear sea-path to a zone boundary sample → `ProximityMatch`
- No sea-distance limit — the proximity zone gate already ensures the boat is close enough
- If no line of sight to any boundary sample → excluded (null)

### Exclusion

- Boat not in zone AND direct distance > proximity range → excluded (early exit, no LOS check)
- Boat in proximity range but all boundary samples blocked by land → excluded

## Algorithm: Boundary Sampling + Segment Intersection

Sample candidate points on the zone boundary at evenly-spaced bearings. For each, test `segmentIntersectsLand(boat, candidate)` — does the straight line cross any coastline edge? Closest clear candidate wins. All blocked → null.

**Single dependency:** `segmentIntersectsLand` — already correct.

## Sample counts (slightly oversampled — cheap)

| Geometry | Condition | Samples | Spacing |
|----------|-----------|---------|---------|
| Circle | Boat inside | 16 | 22.5° |
| Circle | Boat outside | 16 | adaptive (~1-15° depending on arc) |
| Corridor | End-cap disc (×2) | 8 each | 45° |
| Corridor | Edge line (×2) | 5 each | 25% along length |
| Pin | Always | 1 | N/A |

**Worst case:** corridor = 8+8+5+5 = 26 samples × ~50 edges = ~1300 intersection tests. Sub-millisecond.

## resolveMatch() Flow

1. Zone check (geometric, no land test) → `ZoneMatch`
2. Compute proximity range (override or formula)
3. Boat at marker (≤ 1 m) → skip land check
4. Find closest unblocked boundary point via `closestUnblockedPoint()`
5. Unblocked point found → `ProximityMatch` directly (no sea-distance limit; zone gate already ensured proximity)

## What stays

- `closestGeometricBoundaryPoint()` — direct-line fast path
- `circlePointAtBearing()` — needed for circle/disc sampling
- `segmentIntersectsLand()` — the core primitive
- `isInsideGeometry()`, `distanceToClosestGeometryPoint()`, `zoneCenterOf()`, `proximityRange()`
- `resolveMatch()`, `resolveAllMarkers()`, `hasLineOfSight()`, `pointsVisible()`
- `sampleBoundaryPoints()`, `testDebugSamples()`
- `segmentIntersectsLandStepped()` — stepped water check for joint-gap detection

## Files Changed

| File | Change |
|------|--------|
| [`MarkerMatcher.kt`](../app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt) | Removed ~400 lines angular shadow code. Added `sampleBoundaryPoints()`, `testDebugSamples()`, `segmentIntersectsLandStepped()`. Removed sea-distance limit from resolveMatch step 5. |

## Verification

1. `compileDebugKotlin` → pass
2. `assembleDebug` → pass
3. Baie des Milliardaires → all 26 samples blocked → filtered
4. Pin behind peninsula → single sample blocked → filtered
5. Circle in open water → direct-line fast path → match
6. Corridor in open water → samples clear → match
7. Boat inside zone → 16 samples around full circle → some clear → match
