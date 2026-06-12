# Regulated Zones — Final Icon & Geo-Fence Design

## Final Icon Set

| Category | Icon | Background | Alpha | Meaning |
|----------|------|-----------|-------|---------|
| NO_ANCHOR | ⚓ | Amber `#FF8F00` | 75% (active) | Anchoring prohibited |
| MOORING | 🛥️ | Green `#2E7D32` | 75% (active) | Small craft mooring area |
| SPEED_LIMIT | **5**/**10** (white bold) | Red `#E53935` | 75% (active) | Speed limit (kn) |
| NO_DIVING | 🤿 | Amber `#FF8F00` | 75% (active) | Diving / underwater prohibited |
| SEAPLANE | ✈️ | Grey `#78909C` | 50% (inactive) | Seaplane operation area |

Alpha values sourced from `ZoneConfig.iconBackActiveAlpha` (191 = 75%) and `ZoneConfig.iconBackInactiveAlpha` (128 = 50%).

## Geo-Fence

Strip filters zones by boat position: only show icons for zones whose polygon contains the current GPS position. Uses ray-casting point-in-polygon algorithm (same as `CoastlineSpatialIndex`).

## Files to Modify

1. `RegulatedZone.kt` — add `contains(LatLng): Boolean` extension
2. `RegulatedZoneIconProvider.kt` — update emoji/color/alpha mappings
3. `MapScreen.kt` — pass current position to warning strip, add geo-fence filtering
