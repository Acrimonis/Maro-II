<!-- scope: feature -->
# Zone Methods — Performance Analysis

## Pipeline: 3 Hz (333ms tick) on Dispatchers.Default

## Operation costs

| Operation | Lookups | Time | Scenarios |
|---|---|---|---|
| `SpeedZoneIndex.query()` | ~1–5 zone edges | < 1ms | All |
| `distanceToCoastMeters()` | 1 coastline index | < 0.5ms | All |
| Ray-march entry (500m cap) | 50 steps + 10 binary = 60 coastline | ~2–5ms | Outside, zone within range |
| Ray-march exit (500m cap) | 50 steps + 10 binary = 60 coastline | ~2–5ms | Inside zone |
| `determineBeyondType()` | 1 zone + 2–6 coastline | < 1ms | Inside zone |
| `firstSpeedZoneAhead()` | ~5–10 zone edges | < 1ms | Multi-zone scenarios |

## Per scenario

| Scenario | Heavy | Light | Total time | Budget left |
|---|---|---|---|---|
| Open sea, no zone within 500m | 0 | 2 lookups | **< 1ms** | 332ms |
| Outside, zone within 500m | 1 ray-march entry | 3 lookups | **~3–5ms** | 328ms |
| Inside single zone | 1 ray-march exit + beyond | 2 lookups | **~3–6ms** | 327ms |
| Inside, heading to another zone | 1 ray-march + beyond + zone query | 3 lookups | **~4–7ms** | 326ms |

## Key findings

- **500m cap** reduces ray-march from 210 to 60 coastline lookups — the single most impactful optimization
- **`nearbyZones` reuses existing pipeline results** — `query()` and `distanceToCoastMeters()` are already computed every tick
- **All scenarios fit with >95% idle time** — no performance concern at 3 Hz

