<!-- scope: feature -->
# Zone Tile "Exiting Zone" Caption — Discussion

## Current "Inside Zone" States (SpeedLimitCard)

| Aspect | Compliant | Over-speed |
|---|---|---|
| Title | zone name (e.g. BANDE 300M) | zone name |
| Value | speed limit (e.g. 5 kn) | speed limit |
| Subtitle | `5 kn max — 120 m` (distance to exit) | `5 kn max — 120 m` |
| Card | dark green + green border | dark red + red border |

## All States Summary

| State | Title | Value | Subtitle | Card |
|---|---|---|---|---|
| Loading | ZONE | — | *(empty)* | blue |
| On land | ZONE | TERRE | À TERRE | grey dimmed |
| Inside, compliant | zone name | 5 kn | 5 kn max — 120 m | green |
| Inside, speeding | zone name | 5 kn | 5 kn max — 120 m | red |
| Approaching (heading) | zone name | ↑1.2 km | 5 nœuds · 45 s | blue |
| No zone nearby | LIBRE | — | Aucune limite | blue |
| Zone ahead (no heading) | ZONE | 800 m | BANDE 300M | blue |

## "Exiting Zone" Scenario

The user added a todo `exiting zone` under the `tweak` subfeature. Two sub-cases:

1. **Just exited** — `signedDist` flipped negative→positive. Currently falls into the "outside" states (heading-ahead, nearby, or free-sailing).

2. **At boundary** — distance ~0. Current subtitle `5 kn max — 0 m` or `5 kn max — 120 m` communicates proximity but NOT direction (entering vs exiting).

### Possible approaches:
- Transient "exited" state when signedDist crosses from ≤0 to >0
- Direction indicator showing → exit / ← entry
- Use existing `approachingSpeedZone` logic - or add an `exiting`/`exited` counterpart
- A momentary toast/popup vs persistent state change in the tile subtitle

