# Remove Proximity Range Gates — Line-of-Sight Only

## Motivation

The current `resolveMatch` applies distance-based proximity gates (range pre-gate, proximity gate, 200m pin cap) that exclude valid markers even when a clear sea line-of-sight exists. Per spec, the only requirements for a match are:

1. Boat inside zone → ZoneMatch
2. Boat outside + clear line-of-sight to any boundary point → match

Distance does not exclude. `proximityRange` remains as a display-ordering hint in `sortScore` only.

## Changes

### File 1: `MarkerMatcher.kt`

#### Rename `ProximityMatch` → `LineOfSightMatch`

- Data class definition (line 33)
- All construction sites (lines 119, 127, 157)
- `markerOf()` extraction (line 455)
- `sortScore` (line 525)
- Doc comments referencing it

#### `resolveMatch()` — remove all distance gates

| Remove | Keep |
|--------|------|
| Step 2: `val range = proximityRange(marker)` | Zone check unchanged |
| Step 3 range check: `if (directDist <= range)` → return directly if ≤1m | Boat-at-marker (≤1m) always matches |
| Step 4 pin 200m cap: `if (directDist > range) return null` | Pin branch gets land check via `segmentIntersectsLand` |
| Step 5: range pre-gate (entire block) | Step 6: `closestUnblockedPoint` unchanged |
| Step 7: `if (dist > range) return null` | Return `LineOfSightMatch` directly if unblocked found |

**After:**

```
isInsideGeometry? ──yes──▶ ZoneMatch
  no
directDist ≤ 1m? ──yes──▶ LineOfSightMatch
  no
Pin? ──yes──▶ segmentIntersectsLand(boat, pin)?
                blocked → null
                clear   → LineOfSightMatch
  no (Circle/Corridor)
closestUnblockedPoint()
  null   → null
  found  → LineOfSightMatch
```

#### Keep

- `proximityRange()` — used by `sortScore` only
- `sortScore` — unchanged, still uses `proximityRange` for LineOfSightMatch percentage

#### Remove

- `ProximityConfig` data class (lines 47-50) — unused

### File 2: `MarkerOverlay.kt` (line 130)

- `ProximityMatch` → `LineOfSightMatch`

### File 3: `MarkerDrawer.kt` (line 459-460)

- `ProximityMatch` → `LineOfSightMatch`

## Not changed

- `UserMarker.proximityOverrideM` — stays in data model
- `CreateFormState.proximityOverrideM` — stays in wizard
- ZoneMatch — unchanged
- `closestUnblockedPoint` — unchanged
- `sampleBoundaryPoints` — unchanged (fixed in prior task)
