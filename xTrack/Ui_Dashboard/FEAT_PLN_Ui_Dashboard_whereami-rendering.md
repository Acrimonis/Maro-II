# WhereAmI Dashboard — Rendering Update

## Scope
[`MarkerDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt) — `MatchResultContent` composable only.

## Changes

### 1. Thread `boatPosition` into `MatchResultContent`
- `MarkerDrawer` already receives `boatPosition: LatLng?`
- Pass it to `MatchResultContent(viewModel, onClose, boatPosition)`
- Used to compute geometric boundary distance for `LineOfSightMatch` rows

### 2. Empty state → centered italic "in the middle of nowhere"
- Replace the two-branch empty state (lines 429-455) with a single centered italic Text
- Both "no markers" and "no matches" show the same message

### 3. Match rows → per-line with color accent + distance
- Replace `joinToString` + single `Text` (lines 456-468) with `Column` + `matches.forEach`
- Each row: `Row` with 4dp color accent bar (`MarkerColors.of(marker.colorIndex)`) + `Text`
- `ZoneMatch` → marker name only (no distance)
- `LineOfSightMatch` → `"cardinalDirection of markerName · distance"` 
  - Distance = geometric (flight of bird) to closest zone boundary, NOT `seaDistanceM`
  - Compute via `closestGeometricBoundaryPoint()` + `haversine(boat, thatPoint)`

### 4. No model changes
- `WhereAmIMatch`, `WhereAmIResult`, `MarkerMatcher` — untouched
- `seaDistanceM` semantics preserved for sort scoring

## Files touched
| File | Change |
|------|--------|
| [`MarkerDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt) | `MatchResultContent` signature + body rewrite |
