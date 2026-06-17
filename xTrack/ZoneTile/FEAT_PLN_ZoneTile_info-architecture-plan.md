<!-- scope: feature -->
# Zone Info Architecture Plan

## Three normalized methods

All return `ZoneBoundaryInfo` (single) or `List<ZoneBoundaryInfo>` (multiple).

### 1. `infoToZoneEntryAlongHeading(lat, lon, headingDeg, ..., maxSearchM = 500.0)`
- **Direction:** One ray along heading
- **Result:** `ZoneBoundaryInfo?` — single closest zone boundary ahead
- **Cost:** ~50 coastline lookups (500m ray-march cap) + 1 zone index query
- **Current state:** ✅ Implemented, needs `maxSearchM` parameter added

### 2. `infoToZoneExitAlongHeading(lat, lon, headingDeg, ..., maxSearchM = 500.0)`
- **Direction:** One ray along heading (inverted, from inside)
- **Result:** `ZoneBoundaryInfo?` — single closest exit boundary
- **Cost:** ~50 coastline lookups + angular zone projection
- **Current state:** ✅ Implemented, needs `maxSearchM` parameter added

### 3. `infoToZoneAroundBoat(lat, lon, radiusM = 500.0)`
- **Direction:** 360° radial
- **Result:** `List<ZoneBoundaryInfo>` — all zones within radius
- **Cost:** 1 zone index `query()` + 1 coast distance check + distance filter
- **Current state:** ❌ Not implemented yet

## Implementation plan for `infoToZoneAroundBoat`

```kotlin
fun infoToZoneAroundBoat(
    lat: Double, lon: Double,
    radiusM: Double = 500.0
): List<ZoneBoundaryInfo> {
    val results = mutableListOf<ZoneBoundaryInfo>()

    // 1. SHOM speed zones within radius
    val query = speedZoneIndex?.query(lat, lon)
    query?.let { q ->
        // Nearest zone if within radius
        if (q.nearestZone != null && q.distanceToBoundaryM != null 
            && abs(q.distanceToBoundaryM!!) <= radiusM) {
            results.add(ZoneBoundaryInfo(
                zoneName = q.nearestZone!!.name,
                distanceM = abs(q.distanceToBoundaryM!!),
                speedLimitKn = q.nearestZone.speedLimitKn,
                beyondType = if (q.distanceToBoundaryM!! <= 0) BeyondType.ZONE else BeyondType.OPEN_SEA,
                ...
            ))
        }
        // All inside zones
        q.allInsideZones.forEach { zone ->
            if (results.none { it.zoneName == zone.name }) {
                results.add(ZoneBoundaryInfo(zoneName = zone.name, distanceM = 0.0, ...))
            }
        }
    }

    // 2. 300m band virtual zone
    val distToCoast = repository.distanceToCoastMeters(lat, lon)
    if (distToCoast <= CoastlineRepository.ZONE_DISTANCE_M + radiusM) {
        val bandDist = maxOf(0.0, distToCoast - CoastlineRepository.ZONE_DISTANCE_M)
        results.add(ZoneBoundaryInfo(
            zoneName = "BANDE 300M",
            distanceM = bandDist,
            speedLimitKn = 5.0,
            ...
        ))
    }

    return results.sortedBy { it.distanceM }
}
```

## Parameter changes needed

### `infoToZoneEntryAlongHeading` — add `maxSearchM`
- Pass to `querySpeedZoneAhead()` → `distanceTo300mAlongHeading(lat, lon, heading, dist, maxSearchM)`
- Default `500.0` replaces hardcoded `2000.0`

### `infoToZoneExitAlongHeading` — add `maxSearchM`
- Pass to `findBandExitAlongHeading(lat, lon, heading, dist, maxSearchM)`
- Default `500.0` replaces hardcoded `2000.0`

