# Markers — Sort Scoring v2 (Percentage-Based)

> **Feature:** Markers | **Subfeature:** sort-scoring
> **Created:** 2026-06-30 | **Status:** Plan — final
> **Supersedes:** `260630_FEAT_PLN_Markers_sort-scoring.md` (v1 with absolute distance + W)

## Ordering Rules (3 layers)

```
Layer 1: Containment tree — children always before parents (depth-first, leaves-first)
Layer 2: Category — ZoneMatch always before ProximityMatch (within same-level siblings)
Layer 3: sortScore() + zoneSize tie-breaker — within same category siblings
```

## Formula

```
sortScore = categoryBase + typeWeight × percentage

categoryBase:   ZoneMatch = 0.0      ProximityMatch = 1.0
typeWeight:     Pin = 0.5            Circle = 1.0           Corridor = 2.0

percentage (ZoneMatch):      distanceToCenterM / zoneSizeM
percentage (ProximityMatch): seaDistanceM / proximityRange
```

Tie-breaker: when sortScore is equal, smaller zoneSize wins.

## Parameters (maro.properties)

```properties
# ── Marker match ordering ─────────────────────────────────────────────
# Order: children-before-parents → ZoneMatch before ProximityMatch → sortScore.
#
# sortScore = categoryBase + typeWeight × percentage
#   ZoneMatch categoryBase=0.0, ProximityMatch categoryBase=1.0.
#   percentage (ZoneMatch) = distanceToCenterM / zoneSizeM  (0=center, 1=boundary).
#   percentage (ProximityMatch) = seaDistanceM / proximityRange  (0=boundary, 1=maxRange).
#   Tie-breaker: smaller zoneSize wins.
#
# typeWeight.Pin (default 0.5)
#   Pin = most specific. At equal %, Pin always beats Circle/Corridor.
#   Range 0.0–1.0.
marker.sort.typeWeight.Pin=0.5

# typeWeight.Circle (default 1.0) — baseline.
#   Range 0.0–5.0.
marker.sort.typeWeight.Circle=1.0

# typeWeight.Corridor (default 2.0)
#   Corridor = least specific. Needs ~4× closer % to beat a Circle.
#   Range 0.0–10.0.
marker.sort.typeWeight.Corridor=2.0
```

## Implementation

### MarkerMatcher.kt — replace sortScore()

```kotlin
private fun sortScore(match: WhereAmIMatch): Double {
    val typeWeight = when (markerOf(match).geometry) {
        is MarkerGeometry.Pin -> AppConfig.markerSortTypeWeightPin
        is MarkerGeometry.Circle -> AppConfig.markerSortTypeWeightCircle
        is MarkerGeometry.Corridor -> AppConfig.markerSortTypeWeightCorridor
    }
    val (categoryBase, percentage) = when (match) {
        is WhereAmIMatch.ZoneMatch ->
            0.0 to (match.distanceToCenterM / match.zoneSizeM.coerceAtLeast(1.0))
        is WhereAmIMatch.ProximityMatch -> {
            val range = markerOf(match).proximityOverrideM
                ?: 200.0  // fallback, should never be null after save
            1.0 to (match.seaDistanceM / range.coerceAtLeast(1.0))
        }
    }
    return categoryBase + typeWeight * percentage
}
```

### depthFirstLeavesFirst() — add zoneSize tie-breaker

```kotlin
private fun depthFirstLeavesFirst(nodes: List<WhereAmIMatch>): List<WhereAmIMatch> {
    val result = mutableListOf<WhereAmIMatch>()
    for (node in nodes.sortedWith(compareBy<WhereAmIMatch>(
        { sortScore(it) },
        { when (it) {
            is WhereAmIMatch.ZoneMatch -> it.zoneSizeM
            is WhereAmIMatch.ProximityMatch -> zoneSizeOf(markerOf(it).geometry)
        }}
    ))) {
        if (node is WhereAmIMatch.ZoneMatch && node.children.isNotEmpty()) {
            result.addAll(depthFirstLeavesFirst(node.children))
        }
        result.add(node)
    }
    return result
}

private fun zoneSizeOf(geometry: MarkerGeometry): Double = when (geometry) {
    is MarkerGeometry.Pin -> 0.0
    is MarkerGeometry.Circle -> geometry.radiusM
    is MarkerGeometry.Corridor -> geometry.widthM
}
```

## Files Changed

| File | Change |
|------|--------|
| `maro.properties` | Replace 4 sort keys → 3 (remove distanceWeight) |
| `AppConfig.kt` | Remove `markerSortDistanceWeight`, keep 3 typeWeights |
| `MarkerMatcher.kt` | Replace `sortScore()` + add zoneSize tie-breaker |

## Examples

| Match | Score | Why |
|-------|-------|-----|
| Circle 200m, at center (0%) | 0 + 1.0×0.00 = **0.00** | Dead center |
| Corridor 400m, at centerline (0%) | 0 + 2.0×0.00 = **0.00** | Tie → Circle 200m < Corridor 400m → Circle wins |
| Circle 200m, at 100m (50%) | 0 + 1.0×0.50 = **0.50** | Halfway out |
| Corridor 400m, at 200m (50%) | 0 + 2.0×0.50 = **1.00** | Corridor penalty + halfway |
| Pin at 40m / 200m (20%) | 1 + 0.5×0.20 = **1.10** | First proximity |
| Circle at 60m / 600m (10%) | 1 + 1.0×0.10 = **1.10** | Tie with pin → Pin 0 < Circle 200 → Pin wins |
| Pin at 150m / 200m (75%) | 1 + 0.5×0.75 = **1.375** | Far pin |
| Circle at 30m / 600m (5%) | 1 + 1.0×0.05 = **1.05** | Very close circle → beats far pin |
