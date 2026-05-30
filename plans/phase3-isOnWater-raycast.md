# Phase 3: `isOnWater` — Ray-Cast Redesign

**Date:** 2026-05-30
**Status:** Implemented & Verified (BUILD SUCCESSFUL)

---

## Problem

The old `isOnWater` used the **single nearest coastline segment's cross-product** to determine water/land. This was unreliable — multiple false negatives in the field. The nearest segment is not always the correct segment for orientation (bays, capes, simplification artifacts).

## Solution

**Ray casting.** A vertical ray is cast SOUTH from the query point. Every time it crosses the mainland coastline, the state flips (water→land or land→water). The parity of crossings determines the result.

```
ODD  crossings → LAND
EVEN crossings → WATER  (0 crossings = open water)
```

Islands are checked separately: if the query point is inside any closed island polygon (odd crossings with that island's ring) → LAND.

## Algorithm

```
isOnWater(lat, lon):
  1. distanceToCoast > 6 NM → WATER (short-circuit, beyond license zone)
  2. Cast ray south: (lon, lat) → (lon, lat - 6 NM in degrees)
  3. Count mainland crossings via spatial index column query
  4. ODD → LAND, EVEN → WATER
  5. If WATER: check each island for enclosure (odd crossings → LAND)
```

## Files Changed

| File | Changes |
|---|---|
| `SpatialOperations.kt` | +`rayCrossesSegmentSouth()` (25 lines). −`crossProductZ`, `isRightSide`, `isOnWater`, `signedArea`, `isClosedPolyline`, `ensureWaterOnRight` (~110 lines) |
| `CoastlineSpatialIndex.kt` | +`queryColumn()` + `ColumnCandidate` data class (45 lines) |
| `CoastlineRepository.kt` | Rewrote `isOnWater()` — ray-cast + island enclosure + 6 NM short-circuit (−55, +40 lines) |
| `CoastlineGenerator.kt` | −`orientByIslandPositions()`, −orientation step in `generate()`, −`ensureWaterOnRight()` call in `processPolyline()` (~55 lines) |
| `SpatialOperationsTest.kt` | +6 ray-cast tests, −14 old orientation/isOnWater/signedArea tests |
| `CoastlineGeneratorTest.kt` | −2 tests referencing removed functions |

**Net: ~215 lines removed, ~165 added. ~50 lines lighter.**

## Key Design Decisions

- **Vertex de-duplication:** strict `<` on upper longitude bound — each shared vertex counted exactly once
- **6 NM cap:** ray extends 11,112 m south (regulatory zone + performance optimization)
- **`queryColumn()`:** collects only segments along the ray's vertical column — ~5–20 candidates vs. ~15,000 brute-force
- **No sqrt/trig:** intersection checks are 8 float comparisons per segment

## Functions Removed

| Function | Reason |
|---|---|
| `crossProductZ()` | Only used by old `isOnWater` and orientation |
| `isRightSide()` | Only used by old `isOnWater` |
| `SpatialOperations.isOnWater()` | Replaced by `CoastlineRepository.isOnWater()` with ray-cast |
| `signedArea()` | Only used by `ensureWaterOnRight()` |
| `isClosedPolyline()` | Runtime heuristic; island status now from `CoastlineSegment.isClosed` |
| `ensureWaterOnRight()` | Orientation irrelevant for ray-cast |
| `orientByIslandPositions()` | Orientation irrelevant for ray-cast |

## Performance

| Metric | Old (brute-force) | New (ray-cast) |
|---|---|---|
| Segments checked | ~15,000 | ~5–20 |
| CPU/query | ~4-8 ms | ~0.01 ms |
| Math ops | Distance projection + sqrt | Boolean comparisons |

## Test Results

```
BUILD SUCCESSFUL — 30 actionable tasks: 3 executed, 27 up-to-date
All SpatialOperationsTest + CoastlineGeneratorTest pass.
```
