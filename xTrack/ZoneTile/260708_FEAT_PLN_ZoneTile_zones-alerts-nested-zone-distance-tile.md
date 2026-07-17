# Zones Alerts — Nested Zone Distance Tile Fix

**Branch:** `feature/zones-alerts`
**Subfeature:** `UI_Map → zones alertes`
**Date:** 2026-07-07

## Issue

When navigating from inside a SHOM speed zone (e.g., 10kn) towards the 300m coastline band (5kn, more restrictive), the distance tile does NOT show an amber/red alert with distance to the 300m zone. It continues showing either the SHOM zone exit info (green, "open water") or falls back to shore distance.

Expected: the distance tile should detect the more-restrictive 300m band ahead and show an amber alert with distance and "→ BANDE 300M".

## Root Cause Analysis

Three interacting gaps in the zone situation pipeline:

### Gap 1: `determineBeyondType` is blind to the 300m band

**File:** [`NavigationViewModel.kt:1526`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt:1526)

When building `currentZone` for a SHOM zone, `determineBeyondType(exitPos, headingDeg, szIdx)` probes from the SHOM zone's EXIT boundary outward. It only queries `SpeedZoneIndex.firstSpeedZoneAhead()` for SHOM zones. The 300m band is coastline-based — it's not in the SpeedZoneIndex — so `determineBeyondType` never detects it. Result: `currentZone.beyondType` is incorrectly `OPEN_SEA` when the 300m band lies between the boat and the SHOM exit (or immediately beyond it).

### Gap 2: `distanceTo300mAlongHeading` returns 0.0 when inside the band

**File:** [`NavigationViewModel.kt:1215`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt:1215)

```kotlin
if (currentDistToCoast <= CoastlineRepository.ZONE_DISTANCE_M) return 0.0
```

When the boat is inside both the 300m band AND a SHOM zone, this returns 0.0 immediately. The check `bandDist > 0.0` in `zonesAroundBoat` step 1 then filters it out. The 300m band never appears in `zonesAround`, so `nearestAhead` cannot catch it.

### Gap 3: `DistanceCard` boundary selection defers to `currentZone` when `beyondType != OPEN_SEA`

**File:** [`DashboardPanel.kt:387-394`](app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt:387-394)

```kotlin
val boundary = when {
    currentZone != null && nearestAhead != null && exitNextLimit != null -> {
        if (currentZone.beyondType == BeyondType.OPEN_SEA) nearestAhead else currentZone
    }
    ...
}
```

If Gap 1 causes `beyondType` to be `ZONE` (another SHOM zone found beyond), the boundary selection picks `currentZone` (the SHOM zone exit) and ignores `nearestAhead` (the 300m band). Even when Gap 1 returns `OPEN_SEA` (correctly allowing `nearestAhead` through), Gap 2 can prevent `nearestAhead` from containing the 300m band at all.

### Combined Effect

```
Boat inside 10kn SHOM zone, heading shoreward, 300m band ahead:

  Shore ←──[300m band]──←──[10kn SHOM zone]──←── Boat
          0m        300m                    600m

1. currentZone = 10kn SHOM zone
   - determineBeyondType(exitPos@300m) → OPEN_SEA  [Gap 1: doesn't check coast distance]
   
2. zonesAroundBoat:
   - distanceTo300mAlongHeading from 600m → finds band at ~300m → ADDED ✓
   - firstSpeedZoneAhead → hits 10kn exit at ~300m → EXCLUDED (same zone name)
   Result: [BANDE 300M @ 300m ↑]

3. DistanceCard boundary selection:
   - beyondType=OPEN_SEA → picks nearestAhead = BANDE 300M ✓
   - currentLimit=10kn, nextLimit=5kn
   - 5kn < 10kn → AMBER alert ✓

This path WORKS when boat is OUTSIDE the 300m band.

But when the boat is INSIDE the 300m band (boat at 200m from shore, still inside 10kn zone):

  Shore ←──[Boat @ 200m]──←──[10kn SHOM zone continues]──→
          0m              300m boundary

1. currentZone = 10kn SHOM zone (priority over 300m band)
2. zonesAroundBoat:
   - distanceTo300mAlongHeading: currentDistToCoast=200 ≤ 300 → returns 0.0 → FILTERED [Gap 2]
   Result: [] (empty)

3. DistanceCard:
   - nearestAhead = null, currentZone != null → boundary = currentZone (10kn zone)
   - beyondType=OPEN_SEA, exitNextLimit=Double.MAX_VALUE
   - isMoreRestrictive = false (MAX_VALUE > 10kn) → GREEN "open water" exit info
   - 300m band ALERT MISSING ✗
```

## Fix Plan

### Fix 1: Make `determineBeyondType` coastline-aware

In [`NavigationViewModel.kt:1526`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt:1526), after the existing land probes, add a coastline distance check at the boundary position. If the boundary position is within or near the 300m band, and the next SHOM zone (if any) is further away, return `BeyondType.ZONE` with `"BANDE 300M"` as the `beyondName`.

Tolerance buffer from `maro.properties` → `BuildConfig.ZONE_BAND_BOUNDARY_TOLERANCE_M` (default 20.0m):
```
zone.bandBoundaryToleranceM=20.0
```

```kotlin
// After existing land probe loop, before the final return:
val coastDistAtBoundary = repository.distanceToCoastMeters(blt, blo)
if (coastDistAtBoundary <= CoastlineRepository.ZONE_DISTANCE_M + BuildConfig.ZONE_BAND_BOUNDARY_TOLERANCE_M &&
    zoneDist > coastDistAtBoundary) {
    return BeyondType.ZONE to "BANDE 300M"
}
return if (nextZone != null) BeyondType.ZONE to nextZone.first.name
       else BeyondType.OPEN_SEA to null
```

### Fix 2: `distanceTo300mAlongHeading` — support inside-band queries

When already inside the 300m band (`currentDistToCoast <= ZONE_DISTANCE_M`) and heading towards shore (coast distance decreasing), return the distance to shore (how deep into the band the boat is going). When heading away from shore, return null (no band ahead — you're leaving it).

```kotlin
private fun distanceTo300mAlongHeading(...): Double? {
    if (currentDistToCoast <= CoastlineRepository.ZONE_DISTANCE_M) {
        // Inside band. Return distance to shore when heading landward.
        // Probe a short step forward: if coastDist decreases, we're heading into the band.
        val probePt = SpatialOperations.pointAlongBearing(lat, lon, headingDeg, 5.0)
        val probeDist = repository.distanceToCoastMeters(probePt.latitude, probePt.longitude)
        return if (probeDist < currentDistToCoast) currentDistToCoast else null
    }
    // ... existing ray-march ...
}
```

This ensures `zonesAroundBoat` can include "BANDE 300M" even when already inside the band, so the distance tile can alert about approaching shore (deeper into the restricted zone).

### Fix 3: `DistanceCard` — prefer more-restrictive ahead zone

In [`DashboardPanel.kt:387-394`](app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt:387-394), replace the `beyondType`-gated boundary override with a pure speed-limit comparison:

```kotlin
// BEFORE (line 389):
if (currentZone.beyondType == BeyondType.OPEN_SEA) nearestAhead else currentZone

// AFTER:
if (nearestAhead.speedLimitKn < currentZone.speedLimitKn) nearestAhead else currentZone
```

The full boundary selection becomes:
```kotlin
val boundary: ZoneBoundaryInfo? = when {
    currentZone != null && nearestAhead != null && exitNextLimit != null -> {
        if (nearestAhead.speedLimitKn < currentZone.speedLimitKn) nearestAhead else currentZone
    }
    currentZone != null -> currentZone
    nearestAhead != null -> nearestAhead
    else -> null
}
```

**Rationale:** The old `beyondType == OPEN_SEA` gate unconditionally preferred `nearestAhead`, which would incorrectly override a valid green exit-to-open-water display with a less-restrictive ahead zone. The new condition — "is the ahead zone strictly more restrictive?" — only overrides when it matters. The current zone's exit behavior (green "open water", amber "→ next zone") stays intact for all non-override cases.

**Trace table:**

| Scenario | currentLimit | aheadLimit | Boundary | Card | OK? |
|----------|:--:|:--:|---|---|:--:|
| 10kn zone, exit to open water, no ahead | 10 | — | currentZone | 🟢 green exit | ✓ |
| 10kn zone, exit to open water, 10kn ahead | 10 | 10 | currentZone | 🟢 green exit | ✓ |
| 10kn zone, 5kn 300m band ahead | 10 | 5 | nearestAhead | 🟠 amber entry | ✓ |
| 5kn zone, exit to open water, 10kn ahead | 5 | 10 | currentZone | 🟢 green exit | ✓ |
| 10kn zone, 3kn SHOM zone ahead | 10 | 3 | nearestAhead | 🟠 amber entry | ✓ |

This is defense-in-depth — even if Fix 1/2 miss an edge case, the distance tile still catches a more-restrictive zone ahead.

### Fix 4: `DistanceCard` — label "→ shore" when inside 300m band

When Fix 2 adds "BANDE 300M" to `zonesAround` while the boat is already inside the band, the "entering zone ahead" label says "BANDE 300M" which reads wrong (you're not approaching it, you're in it). Override the subtitle to "→ shore" when the boundary is the 300m band and the boat is inside it.

```kotlin
// DashboardPanel.kt, label computation (line ~468):
val label = if (boundary == currentZone) {
    when (currentZone!!.beyondType) { ... }
} else {
    if (boundary.zoneName == "BANDE 300M" && distanceToShore != null && distanceToShore <= 300.0)
        "→ shore"
    else
        boundary.zoneName
}
```

The distance value is `currentDistToCoast` (from Fix 2) — distance to shore. Label matches: "200 m → shore".

## Files Changed

| File | Change |
|------|--------|
| [`build.gradle.kts`](app/build.gradle.kts) | New `buildConfigField` for `ZONE_BAND_BOUNDARY_TOLERANCE_M` |
| [`maro.properties`](app/src/main/assets/maro.properties) | `zone.bandBoundaryToleranceM=20.0` |
| [`NavigationViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt) | Fix 1: `determineBeyondType` coastline check |
| [`NavigationViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt) | Fix 2: `distanceTo300mAlongHeading` inside-band mode |
| [`DashboardPanel.kt`](app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt) | Fix 3: `DistanceCard` boundary selection |
| [`DashboardPanel.kt`](app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt) | Fix 4: `DistanceCard` label "→ shore" |

## Verification

After the fix, these scenarios should all work:

| Scenario | Expected Distance Tile |
|----------|----------------------|
| Open water → 10kn SHOM zone | Amber: ↑ distance to 10kn entry |
| Inside 10kn zone, outside 300m band, heading shoreward | Amber: ↑ distance to BANDE 300M entry |
| Inside 10kn zone, inside 300m band, heading shoreward | Red/amber: distance to shore (deep in band) |
| Inside 10kn zone, heading seaward (away from shore) | Green: exit distance to open water |
| Inside 300m band only (no SHOM zone), heading seaward | Green: exit distance to open water |
