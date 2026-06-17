<!-- scope: feature -->
# `lookupZoneAroundBoat(distInMeters)` — Discussion

## Concept
A radial search function that returns all zones (SHOM speed zones + 300m band) within a given radius of the boat position, regardless of heading.

## What exists today

| Query | Returns | Limitation |
|---|---|---|
| `SpeedZoneIndex.query(lat, lon)` | `SpeedZoneQuery` with `nearestZone`, `allInsideZones`, `distanceToBoundaryM` | SHOM zones only, no radial radius filter |
| `distanceToCoastMeters(lat, lon)` | Distance to shore | 300m band only |
| `inZone300` / `distToZone` | Boolean / signed distance | Binary, no radius |

## Proposed signature

```kotlin
fun lookupZoneAroundBoat(distInMeters: Double): List<ZoneBoundaryInfo>
```

Returns all zones (SHOM + 300m band virtual zone) whose nearest boundary is within `distInMeters` of the boat, sorted by distance.

## Use cases

1. **"Are there any zones near me?"** — single call instead of two separate queries
2. **Dashboard "nearby zones"** — show count or nearest zone names
3. **Auto-show decision** — simplified: "is there a zone within reveal distance?"
4. **Closest zone** — unified ranking across SHOM + 300m band

## Relationship to entry/exit methods

- Entry/exit: **heading-constrained** (along a specific direction)
- `lookupZoneAroundBoat`: **radial** (all directions within a radius)
- Different questions, complementary APIs

## Concerns

| Concern | Mitigation |
|---|---|
| Radial search could be expensive for large radii | `distInMeters` caps the search; dashboard use 500m–1km |
| Overlaps with existing `query()` | Refactor: `query()` becomes internal helper, `lookupZoneAroundBoat` is the public API |
| 300m band isn't a polygon zone | Represent as virtual zone: present if `distToCoast ≤ 300 + radius` |

