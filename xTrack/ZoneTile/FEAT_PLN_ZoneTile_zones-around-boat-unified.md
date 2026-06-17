<!-- scope: feature -->
# Unified `zonesAroundBoat()` — Single Method for All Zone Lookups

## Proposal

Replace the three-method approach (entry + exit + around) with a single method that returns zones around the boat sorted by heading priority:

```kotlin
private fun zonesAroundBoat(
    lat: Double, lon: Double,
    headingDeg: Double,
    currentDistToCoast: Double,
    speedZoneIndex: SpeedZoneIndex?,
    radiusM: Double = 750.0
): List<ZoneBoundaryInfo>
```

Sorting priority (1 = highest):
1. Directly on heading ray (from ray-march / firstSpeedZoneAhead)
2. Within cone (±CONE_HALF_ANGLE = 15°)
3. To right/left (outside cone)
4. Behind

## Simplified ZoneSituation

```kotlin
data class ZoneSituation(
    val currentZone: ZoneBoundaryInfo?,      // zone we're inside (null if outside)
    val zonesAround: List<ZoneBoundaryInfo>  // all zones sorted by heading priority
)
```

## Functional coverage

| Scenario | zonesAround first item | Tile display |
|---|---|---|
| Outside, zone ahead | `↑zoneA 200m` | Heading-ahead |
| Outside, zone to right | `→zoneB 300m` | Nearby, with direction |
| Outside, nothing | empty | LIBRE |
| Inside, any scenario | currentZone for primary, zonesAround for context | Exit info + nearby |
| Inside, next zone ahead | currentZone excluded, `↑nextZone 400m` | Current zone + next preview |

## Performance

| Step | Cost |
|---|---|
| Existing zone query + coast check | < 1ms (already running) |
| Bearing + heading-diff per zone | O(n), trivial |
| Sort by heading priority | O(n log n), trivial |
| **Total** | **< 2ms peak** |

