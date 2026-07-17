# Track Simplification — Douglas-Peucker + Speed-Aware Reinsertion

> **Update 2026-06-22:** Redesigned as single-pass compound-importance Douglas-Peucker. Replaced two-pass approach (spatial → speed-reinsert) with unified recursion: `importance = max(spatial/ε, |speed-avg|/δ)`. Simplifier reduced from 197→130 lines. No more speed-reinsertion artifacts, no boundary-time hacks.
>
> **Update 2026-06-22:** Added `timeOffsetMs: Long` (ProtoNumber 15) to `TrackPoint` — monotonically unique millisecond timestamps.

**Feature:** GPS
**Subfeature:** track-simplification
**Date:** 2026-06-22

## Motivation

GPS fixes arrive at 1 Hz — a 3-hour sail produces ~10,800 points. Most are redundant: a boat on a straight course at constant speed for 5 minutes generates 300 collinear points fully described by the start and end. Simplification at save time reduces storage, speeds up map rendering, and produces cleaner GPX exports — without losing track shape or speed profile.

## Design

### Two-Pass Algorithm

Executed in `finalizeTrack()`, before `repository.save()`. The live `recordingPoints` StateFlow is unaffected — simplification only applies to the persisted track.

```
simplify(rawPoints):
    spatial   = douglasPeucker(rawPoints, epsilonM=3.0)
    withSpeed = speedReinsert(spatial, rawPoints, deltaKn=3.0)
    return withSpeed
```

### Pass 1: Douglas-Peucker Spatial Simplification

Classic recursive polyline simplification:

1. Given points P₀…Pₙ, draw the chord P₀→Pₙ
2. Find point Pₖ with maximum perpendicular distance from that chord
3. If distance > ε (3 m): recursively simplify P₀…Pₖ and Pₖ…Pₙ, concatenate results
4. If distance ≤ ε: keep only P₀ and Pₙ, discard intermediate points

Perpendicular distance of point P to line segment AB:

```
distance(P, AB) = |(B-A) × (P-A)| / |B-A|
                = |(lonB-lonA)*(latP-latA) - (latB-latA)*(lonP-lonA)| / Haversine(A,B)
```

For small distances (< 100 m segments after simplification), planar approximation with lat/lon deltas is sufficient. Use actual metres via `SpatialOperations.haversine` for the segment length denominator.

**Complexity:** O(n²) worst-case, O(n log n) average with the optimal split-point optimization.

### Pass 2: Speed-Aware Reinsertion

Scan each consecutive pair (A, B) in the spatial result:

1. Find all original raw points that fall between A and B (by timeOffsetSec)
2. Compute the average speed of those raw points
3. Find the point with maximum absolute speed deviation from the average
4. If max deviation > δ (3 kn): insert that point, then recursively check sub-segments (A→inserted) and (inserted→B)
5. If max deviation ≤ δ: keep only A and B

This preserves acceleration/deceleration events within otherwise straight legs.

### Tunables

| Property | Default | Range | Purpose |
|---|---|---|---|
| `tracking.simplifyEnabled` | `true` | bool | Master toggle |
| `tracking.simplifyEpsilonM` | `3.0` | 1.0–20.0 | Max deviation from simplified chord (metres) |
| `tracking.simplifySpeedDeltaKn` | `3.0` | 1.0–10.0 | Speed deviation to re-insert a point (knots) |

All in `maro.properties`.

## Implementation Steps

### 1. Add properties to `maro.properties`

```
# Track simplification: Douglas-Peucker spatial tolerance (metres).
# Lower = more points kept. Range: 1.0-20.0, recommended: 3.0
tracking.simplifyEpsilonM=3.0

# Track simplification: speed deviation threshold to re-insert a point (knots).
# Lower = more speed detail preserved. Range: 1.0-10.0, recommended: 3.0
tracking.simplifySpeedDeltaKn=3.0

# Track simplification: master enable/disable.
tracking.simplifyEnabled=true
```

### 2. Create `TrackSimplifier.kt`

New file in `app/src/main/java/ykws/android/maro/data/track/`:

```kotlin
object TrackSimplifier {

    fun simplify(
        points: List<TrackPoint>,
        epsilonM: Double = 3.0,
        speedDeltaKn: Double = 3.0
    ): List<TrackPoint> {
        if (points.size < 3) return points
        
        // Pass 1: spatial
        val spatial = douglasPeucker(points, epsilonM)
        
        // Pass 2: speed
        return speedReinsert(spatial, points, speedDeltaKn)
    }
    
    private fun douglasPeucker(
        points: List<TrackPoint>, 
        epsilonM: Double
    ): List<TrackPoint>
    
    private fun speedReinsert(
        simplified: List<TrackPoint>,
        raw: List<TrackPoint>,
        deltaKn: Double
    ): List<TrackPoint>
    
    private fun perpendicularDistanceM(
        point: TrackPoint,
        lineStart: TrackPoint,
        lineEnd: TrackPoint
    ): Double
}
```

Key implementation notes:
- Use `SpatialOperations.haversine` for segment length in perpendicular distance
- For the cross-product numerator, use planar approximation (lat/lon deltas scaled by `111_320 * cos(lat)` for lon→metres)
- `speedReinsert` uses binary search on `timeOffsetSec` to find the raw-point range for each segment
- Keep first and last points always (they anchor the track)

### 3. Integrate into `TrackRecorder.finalizeTrack()`

After `finalized = track.copy(...)`, before `repository.save(finalized)`:

```kotlin
val simplifiedPoints = if (simplifyEnabled) {
    TrackSimplifier.simplify(finalized.trackPoints, epsilonM, speedDeltaKn)
} else {
    finalized.trackPoints
}
val finalized = track.copy(
    ...,
    trackPoints = simplifiedPoints
)
```

Read `simplifyEnabled`, `epsilonM`, `speedDeltaKn` from constructor parameters (passed from ViewModel which reads `maro.properties`).

### 4. Plumb through constructor chain

- `TrackViewModel` reads properties → passes to `TrackRecorder` constructor
- `TrackRecorder` stores them as constructor params

### 5. Build + verify

- Build with `apk-build.bat`
- Test: record a track with straight sections and turns, export GPX, verify in QGIS/Google Earth that shape is preserved and point count is reduced
- Verify speed profile: check that acceleration zones retain intermediate points

## Expected Reduction

| Sailing pattern | Raw points | Simplified | Reduction |
|---|---|---|---|
| Coastal, many turns | 10,800 | ~600–900 | ~92% |
| Straight + speed changes | 3,600 | ~80–150 | ~96% |
| Straight, constant speed | 3,600 | ~4–10 | ~99.7% |
| Harbour maneuvers | 500 | ~200–350 | ~30% (turns preserved) |
