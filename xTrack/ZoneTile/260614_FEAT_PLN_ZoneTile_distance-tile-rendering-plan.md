<!-- scope: feature -->
# Final Rendering Plan — Zone Tile + Distance Tile

## Config
- `speedZone.maxSearchM=750` — how far to search for zones
- `speedZone.distanceOutOfZoneInfoM=200` — threshold for exit preview mode

---

## Complete scenario table

### Open sea — no zone within 750m

| Tile | Value | Subtitle | Card |
|---|---|---|---|
| **Distance** | `1.2 km` | `de la côte` | blue |
| **Zone** | `—` | `Aucune limite` | blue |

### Outside — zone ahead on heading, >200m away

| Tile | Value | Subtitle | Card |
|---|---|---|---|
| **Distance** | `1.2 km` | `de la côte` | blue |
| **Zone** | `↑750m` | `6 kn · 45 s` | blue |

### Outside — zone ahead on heading, zone closer than shore

| Tile | Value | Subtitle | Card |
|---|---|---|---|
| **Distance** | `800 m` | `→ Cap d'Antibes` | blue |
| **Zone** | `↑750m` | `6 kn · 45 s` | blue |

### Outside — zone to the side, no zone ahead

| Tile | Value | Subtitle | Card |
|---|---|---|---|
| **Distance** | `300 m` | `→ Cap d'Antibes` | blue |
| **Zone** | `→300m` | `Cap d'Antibes` | blue |

### Inside zone — far from exit (>200m)

| Tile | Value | Subtitle | Card |
|---|---|---|---|
| **Distance** | `1.2 km` | `de la côte` | blue |
| **Zone** | `5 kn` | *(empty)* | green/red |

### Inside zone — close to exit (≤200m), beyond = OPEN SEA

| Tile | Value | Subtitle | Card |
|---|---|---|---|
| **Distance** | `120 m` | `sortie zone` | blue |
| **Zone** | `5 kn` | `30 s · OPEN SEA` | green/red |

### Inside zone — close to exit, beyond = LAND

| Tile | Value | Subtitle | Card |
|---|---|---|---|
| **Distance** | `120 m` | `sortie zone` | blue |
| **Zone** | `5 kn` | `30 s · CÔTE` | green/red |

### Inside zone — close to exit, beyond = next ZONE

| Tile | Value | Subtitle | Card |
|---|---|---|---|
| **Distance** | `120 m` | `sortie zone` | blue |
| **Zone** | `5 kn` | `30 s → Cap 6 kn` | green/red |

### Inside zone — on land (edge case)

| Tile | Value | Subtitle | Card |
|---|---|---|---|
| **Distance** | `0 m` | `à terre` | grey |
| **Zone** | `TERRE` | `À TERRE` | grey dimmed |

---

## Data source summary

| Tile field | Data source |
|---|---|
| Distance: value | `zoneSituation.currentZone?.distanceM` (exit, if inside+close) OR `zonesAround.first()?.distanceM` (if closer than shore) OR `distanceToShore` (default) |
| Distance: label | "sortie zone" / "→ {zoneName}" / "de la côte" |
| Zone: value | `currentZone.speedLimitKn kn` (inside) OR `arrow + zonesAround.first().distanceText` (outside) OR `—` (libre) |
| Zone: subtitle | `eta · beyondType` (inside+close) OR `limit · eta` (approaching) OR `zoneName` (nearby) OR empty (inside+far) |
| Zone: card | green/red (inside, compliance) OR blue (outside) |

