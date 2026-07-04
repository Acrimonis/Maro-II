# Dash Distance — Two Fixes

## Fix 1: Shore-bound 300m Gate

### Bug
Inside 300m band heading **towards shore**: `findBandExitAlongHeading()` ray-march fails (`coastDist` decreases, never crosses `ZONE_DISTANCE_M`). `currentZone` stays null. `boundary` falls to `zonesAround?.firstOrNull()` — which hijacks with any SHOM entry ahead → false AMBER.

### Fix
Insert gate after `currentZone` extraction, before `boundary` lookup:

```kotlin
// Inside 300m band heading towards shore: exit ray-march fails (heading
// landward, coastDist never exceeds ZONE_DISTANCE_M). Treat as LAND exclusion.
if (currentZone == null && isWater && distanceToShore != null && distanceToShore <= 300.0) {
    DashboardCard(..., coastline, ...)
    return
}
```

---

## Fix 2: Zone-Ahead Priority Over Exit

### Bug
Inside SHOM 10kn zone heading towards 300m band (5kn): `boundary = currentZone` (exit always wins over `zonesAround`). `currentZone.beyondType = OPEN_SEA` (hardcoded). `nextLimit = ∞`. `∞ > 10` → GREEN. Should be AMBER (5kn < 10kn).

### Root cause
Boundary selection at line 355: `currentZone ?: zonesAround?.firstOrNull()` — exit always wins. `zonesAround` IS populated (300m band entry is in heading-ray results) but ignored when inside a zone.

### Fix
Replace boundary selection: compute `exitNextLimit` from `currentZone.beyondType`, compare against `nearestAhead.speedLimitKn`. Pick the boundary with the **lower** (more restrictive) next-limit.

#### New boundary selection (replaces lines 352-356)

```kotlin
// ── Find next zone boundary ahead on heading cone ──────────────────
val currentZone = zoneSituation?.currentZone
val nearestAhead = zoneSituation?.zonesAround?.firstOrNull()

// Fix 1: Inside 300m band heading towards shore → coastline
if (currentZone == null && isWater && distanceToShore != null && distanceToShore <= 300.0) {
    DashboardCard(
        title = stringResource(R.string.dash_distance_title),
        value = distanceText(distanceToShore),
        subtitle = stringResource(R.string.dash_distance_from_shore),
        cardColor = DashboardColors.cardBg,
        modifier = modifier
    )
    return
}

// Compute exit's effective next-limit (what's beyond the exit)
val exitNextLimit: Double? = if (currentZone != null) {
    when (currentZone.beyondType) {
        BeyondType.OPEN_SEA -> Double.MAX_VALUE
        BeyondType.ZONE -> {
            zoneSituation?.zonesAround
                ?.firstOrNull { it.zoneName == currentZone.beyondName }
                ?.speedLimitKn ?: currentZone.speedLimitKn
        }
        BeyondType.LAND -> null  // excluded below
    }
} else null

// Pick the more restrictive boundary: lower next-limit wins
val boundary: ZoneBoundaryInfo? = when {
    currentZone != null && nearestAhead != null && exitNextLimit != null -> {
        if (nearestAhead.speedLimitKn < exitNextLimit) nearestAhead else currentZone
    }
    currentZone != null -> currentZone
    nearestAhead != null -> nearestAhead
    else -> null
}
```

#### Updated `nextLimit` computation (replaces lines 397-414)

```kotlin
val currentLimit = currentZone?.speedLimitKn ?: Double.MAX_VALUE
val nextLimit = if (boundary == currentZone) {
    exitNextLimit!!  // exit boundary: next is what's beyond
} else {
    boundary.speedLimitKn  // entry boundary: next is the zone's limit
}
```

#### Updated label (replaces lines 432-442)

```kotlin
val label = if (boundary == currentZone) {
    // Exiting current zone
    when (currentZone!!.beyondType) {
        BeyondType.OPEN_SEA -> "open water"
        BeyondType.ZONE -> currentZone.beyondName ?: currentZone.zoneName
        BeyondType.LAND -> "land"
    }
} else {
    // Entering zone ahead
    boundary.zoneName
}
```

---

## Scenario Trace

| Scenario | Boundary chosen | currentLimit | nextLimit | Result |
|----------|----------------|-------------|-----------|--------|
| 10kn SHOM → 300m band | nearestAhead (5kn < ∞) | 10 | 5 | **AMBER** `↑ BANDE 300M` |
| 300m band → open water | currentZone (no ahead) | 5 | ∞ | **GREEN** `↑ open water` |
| Outside → 300m band | nearestAhead | ∞ | 5 | **AMBER** `↑ BANDE 300M` |
| 300m → shore (no SHOM) | gate: coastline | — | — | **Coastline** |
| 10kn SHOM → 10kn SHOM | nearestAhead (10 < ∞) | 10 | 10 | **Coastline** (same) |
| 10kn SHOM → 3kn SHOM | nearestAhead (3 < ∞) | 10 | 3 | **AMBER** `↑ Zone 3kn` |

## Files Changed
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — DistanceCard, ~25 lines changed
