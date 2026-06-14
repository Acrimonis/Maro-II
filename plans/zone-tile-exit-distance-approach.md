# Zone Exit Distance — Implementation Approach

## Chosen approach

**300m band:** Inverted ray-march on coastline distance (reuse existing `distanceTo300mAlongHeading()` structure, flip exit condition)

**SHOM speed zones:** Polygon edge intersection (Option B) — ray from boat position × zone `outerRing` edges via new `SpatialOperations.raySegmentIntersection()` utility

## Unified function

```kotlin
fun distanceToZoneExitAlongHeading(
    lat: Double, lon: Double,
    headingDeg: Double,
    currentDistToCoast: Double,
    speedZoneIndex: SpeedZoneIndex?
): ZoneExitResult? {
    // 1. 300m band exit
    val bandExit = findBandExitAlongHeading(lat, lon, headingDeg, currentDistToCoast)
    
    // 2. SHOM zone exit via polygon edge intersection
    val szExit = findSpeedZoneExitAlongHeading(lat, lon, headingDeg, speedZoneIndex)
    
    // 3. Return closest exit
    return pickClosest(bandExit, szExit)
}
```

## New utility needed

```kotlin
// SpatialOperations.kt
fun raySegmentIntersection(
    origin: LatLng, headingDeg: Double,
    segA: LatLng, segB: LatLng
): Double? // distance (m) or null
```

Iterate `SpeedZone.outerRing` edges, find closest intersection in heading direction.

## Pipeline integration

Call from `querySpeedZoneAhead()` (or parallel function) in the existing `mapLatest` on `Dispatchers.Default`. Store result alongside `HeadingAheadResult` — either extend it with `exitDistanceM` / `exitEtaSeconds` fields, or emit a separate state flow.
