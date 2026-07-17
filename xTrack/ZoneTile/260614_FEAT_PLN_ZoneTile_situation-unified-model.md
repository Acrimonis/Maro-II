<!-- scope: feature -->
# Zone Situation — Unified Data Model

## Core idea

Replace multiple StateFlows and query methods with a single `ZoneSituation` data structure that describes everything about zones around the boat.

## `ZoneSituation` data class

```kotlin
data class ZoneSituation(
    val currentZone: ZoneBoundaryInfo?,      // zone we're inside (null if outside)
    val headingAhead: ZoneBoundaryInfo?,     // closest zone along heading (entry or next zone)
    val nearbyZones: List<ZoneBoundaryInfo>, // all zones within radius, sorted by distance
)
```

## Two query methods

### Outside zone: `infoToZoneAroundBoat(radiusM)`
| Field | Value |
|---|---|
| `currentZone` | `null` |
| `headingAhead` | Closest entry boundary along heading (cone-priority over ray) |
| `nearbyZones` | All SHOM zones + 300m band within `radiusM`, sorted by distance |

### Inside zone: `infoZoneAndZoneAhead(radiusM)`
| Field | Value |
|---|---|
| `currentZone` | Current zone with exit info (name, limit, exit distance, ETA, beyond type) |
| `headingAhead` | Next zone/land/open-sea past the exit boundary |
| `nearbyZones` | All zones within `radiusM` (includes current zone) |

## Tile rendering

```kotlin
val situation = if (insideAnyZone) infoZoneAndZoneAhead(500.0)
                else infoToZoneAroundBoat(500.0)

val display = situation.headingAhead ?: situation.nearbyZones.firstOrNull()
if (display != null) {
    DashboardCard(
        title = display.zoneName,
        value = if (situation.currentZone != null) "${display.speedLimitKn} kn"
                else "${display.directionArrow}${distanceText(display.distanceM)}",
        subtitle = buildString {
            display.etaSeconds?.let { append(etaText(it)) }
            if (display.beyondType != BeyondType.ZONE) append(" · ${display.beyondType}")
        },
        cardColor = if (display.isCompliant) zoneCompliant else zoneDanger
    )
}
```

## Benefits

- Single StateFlow replaces 7+ StateFlows
- One render path for all zone states
- `nearbyZones` enables future UI features (zone list, count badge)
- Clear API: outside uses radial scan, inside uses current + ahead

