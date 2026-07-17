# Fix 3: SHOM Exit Real beyondType + Improved Land Probe

## Part A: Call `determineBeyondType()` from SHOM exit

**File:** [`NavigationViewModel.kt:494-507`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt:494)

Replace hardcoded `BeyondType.OPEN_SEA` with real beyond-type:

```kotlin
val exitPos = SpatialOperations.pointAlongBearing(
    center.latitude, center.longitude, headingDegH, dist
)
val beyond = determineBeyondType(exitPos, headingDegH, szIdx)
ZoneBoundaryInfo(
    ...
    beyondType = beyond.first,
    beyondName = beyond.second,
)
```

## Part B: Improve `determineBeyondType()` itself

**File:** [`NavigationViewModel.kt:1518-1529`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt:1518)

Replace exponential-only probe with distance-aware three-way compare:

```kotlin
val nextZone = speedZoneIndex?.firstSpeedZoneAhead(blt, blo, headingDeg)
val zoneDist = nextZone?.second ?: Double.MAX_VALUE

val probes = doubleArrayOf(5.0, 10.0, 30.0, 60.0, 120.0, 240.0, 480.0)
for (probe in probes) {
    if (probe >= zoneDist) return BeyondType.ZONE to nextZone!!.first.name
    val pt = SpatialOperations.pointAlongBearing(blt, blo, headingDeg, probe)
    if (!repository.isOnWater(pt.latitude, pt.longitude)) return BeyondType.LAND to null
}
return if (nextZone != null) BeyondType.ZONE to nextZone.first.name
       else BeyondType.OPEN_SEA to null
```

**Improvements:**
- Probes: 25→50→100→200→400 → **5→10→30→60→120→240→480** (7 probes, finer close-range)
- Zone-distance gating: if zone closer than next probe → ZONE wins immediately
- Land-before-zone: land probe runs before zone check per distance

**Cost:** +2 `isOnWater` queries/tick, ~10 lines changed, no caller impact.
