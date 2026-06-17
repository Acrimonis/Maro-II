<!-- scope: feature -->
# Coastline Zone Strategy — Granular Data Loading

## Concept

Instead of loading the entire coastline as one block, split it into **overlapping zones**. Only load the zones relevant to the user's current position and map viewport.

```
Full coastline (100 km):
┌──────────────────────────────────────────────────────────────┐
│ Villefranche ── Nice ── Antibes ── Cannes ── Fréjus          │
└──────────────────────────────────────────────────────────────┘

Split into overlapping zones (each 15 km, 3 km overlap):
┌──────────────┐
│  Zone A      │  Villefranche ── Nice ── Antibes
   └──────────────┐
    │  Zone B     │  Nice ── Antibes ── Cannes
        └──────────────┐
         │  Zone C     │  Antibes ── Cannes ── Fréjus
             └──────────────┐
              │  Zone D     │  Cannes ── Fréjus ── St-Raphaël
```

## Why Overlap?

| Use Case | Overlap Needed | Why |
|----------|---------------|-----|
| Map display | **Yes** — zones meet seamlessly | No visible gap when panning |
| Distance to coast | **Yes** — up to 5 km | A point near a zone boundary must find the nearest point in either zone |
| 300m exclusion zone | **Yes** — up to ~2 km | The 300m buffer extends beyond zone boundaries; overlap ensures no gaps in the buffer |

**Recommended overlap**: 5 km per side. This covers:
- Maximum distance query: 300m zone check
- GPS jitter
- Map viewport edge

## How It Works

### Zone Definition

Each zone is defined by its **longitude range** (for west-east coastlines):

```kotlin
data class CoastlineZone(
    val id: String,                    // "zone-a"
    val lonStart: Double,              // 6.70
    val lonEnd: Double,                // 6.85
    val points: List<CoastlinePoint>,  // only points within this longitude range
    val mainland: CoastlinePolyline,
    val islands: List<CoastlinePolyline>
)
```

### Active Zone Selection

Based on the user's GPS position and map viewport:

```
User at (43.55, 7.00) — near Cannes
  → Viewport shows 6.95°E to 7.05°E
  → Active zones: Zone B (6.82-7.02) + Zone C (6.97-7.17)
  → Zones A and D not loaded → memory saved
```

```kotlin
fun getActiveZones(
    gpsPosition: LatLng,
    viewportBounds: BoundingBox
): List<String> {
    // Return zone IDs that intersect with the viewport + 5 km margin
}
```

### Zone Switching

When the user pans the map or moves:

```
User pans east to (43.55, 7.10) — near Mandelieu
  → New viewport: 7.05°E to 7.15°E
  → Zone B falls out of view → can be unloaded
  → Zone D comes into view → load Zone D
  → Zone C stays active → keep loaded
```

## Zone Size Trade-offs

| Zone Length | Number for 100 km | Memory per Zone | Loading Frequency |
|-------------|-------------------|-----------------|-------------------|
| 5 km | 20 | ~5 KB | High (often swapping) |
| **10 km** | **10** | **~10 KB** | **Medium (good balance)** |
| 15 km | 7 | ~15 KB | Low (fewer swaps) |
| 20 km | 5 | ~20 KB | Very low |

**Recommended**: **10 km zones with 3 km overlap** → effective zone spacing = 7 km → ~14 zones for 100 km.

At any time, the user sees ~3 zones (current + 1 on each side) = **~30 KB in memory**.

Compare to loading all 100 km at once: **~100 KB in memory**.

## Cache Strategy

Each zone is cached as its own Protobuf file:

```
Internal storage:
└── coastlines/
    └── nice-frejus/
        ├── zone_00.bin     ← 6.70°E to 6.80°E
        ├── zone_01.bin     ← 6.77°E to 6.87°E
        ├── zone_02.bin     ← 6.84°E to 6.94°E
        ├── zone_03.bin     ← 6.91°E to 7.01°E  ← user is here
        ├── zone_04.bin     ← 6.98°E to 7.08°E  ← user is here
        └── ...             ← total: ~14 files
```

On first fetch for a region:
1. Fetch full coastline OSM data
2. Process full pipeline
3. Split into zones
4. Save each zone as a separate Protobuf file

On app launch:
1. Determine which zones are needed (GPS + viewport)
2. Check cache for each zone
3. If cached → load from file (instant, ~1ms each)
4. If not cached → fetch OSM for the whole region (one-time cost)

## Impact on Pipeline

The current pipeline processes the **entire coastline** at once and returns one result. With zones:

```
Current:  Fetch all → Process all → One big result
Future:   Fetch all → Process all → Split into zones → Cache per zone → Load active zones
                                          │
                                    (splitting at generation time,
                                     after simplification & orientation)
```

The "split into zones" step is added at the **end** of the pipeline — after assembly, island filter, clip, simplify, and orientation. This means:
- Full pipeline processes the complete OSM data (same as now)
- Only the final step divides the output into zones
- Each zone inherits the correct orientation (water on right)
- Islands are associated with the nearest mainland zone

## Spatial Query Behavior

### Distance to Coast

```kotlin
fun distanceToCoast(userPos: LatLng): Double {
    val activeZones = getActiveZones(userPos, viewportBounds)
    var minDist = Double.MAX_VALUE
    for (zone in activeZones) {
        for (edge in zone.edges) {
            val d = pointToSegmentDistance(userPos, edge)
            if (d < minDist) minDist = d
        }
    }
    return minDist
}
```

Since zones overlap by 5 km, a point near a zone boundary finds the nearest edge in either overlapping zone — no edge artifacts.

### 300m Exclusion Zone

Each active zone produces its own 300m buffer strip. The union covers the user's area seamlessly because zones overlap.

### Map Rendering

Draw each active zone's polyline on the map. The overlap means polylines double-cover the overlap region — but since the points are identical (same OSM data), the lines overlap perfectly with no visible artifact.

## Summary

| Aspect | Current (one block) | Proposed (zones) |
|--------|-------------------|------------------|
| Memory usage | Full coastline (~100 KB for 100 km) | Active zones only (~30 KB) |
| First load time | Fetch + process entire coastline (same) | Same initial cost |
| Pan/move behavior | Always has all data | Load/unload zones (instant from cache) |
| Distance queries | Scan all points | Scan active zone points only |
| Cache files | 1 file per region | ~14 files per region |
| Overlap | N/A | 3-5 km for seamless behavior |

