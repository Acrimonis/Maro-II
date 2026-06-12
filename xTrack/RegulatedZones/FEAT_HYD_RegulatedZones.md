# Hydration: RegulatedZones

**Last Bake:** 2026-06-12 08:55 UTC+2
**State:** Active — display-layer complete, all subfeatures done.

## Summary
- Reverted **HEAD** (`e84c7ba` — overlay reorder, pointInPolygon, RegulationInfoBanner, activeRegulations detection) — info banner and zone-detection removed from the map UI
- Removed **land-clipping** (centroid waterTest filter from `drawRegulatedZones()`) — zones now render unconditionally without centroid-based skip
- Remaining display-layer: `drawRegulatedZones()` renders translucent filled polygons with per-type colours, `RegulatedZonesLayerButton` toggle, `RegulatedZonesRepository` asset loader, `produceState` data flow, `regulatedZonesVisible` settings toggle
- Prebaked .bin contains 77 zones — all render as fill+stroke polygons on the map

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — Modified (cleaned: no info banner, no land-clipping, no pointInPolygon)
- `xTrack/RegulatedZones/FEAT_DSC_RegulatedZones.md` — Updated front-matter dates
- `xTrack/GLOBAL_CONTEXT.md` — Updated active pointers + feature summary

## Key Changes
- Reverted HEAD commit `e84c7ba` — removed `RegulationInfoBanner`, `pointInPolygon()`, `activeRegulations` detection, overlay reorder, `RegulatedZone` import, `TextOverflow` import
- Removed `waterTest` centroid filter from `drawRegulatedZones()` — no more land-clipping skip logic
- Stripped `waterTest` parameter from `MapContent`, `CoastlineMapView`, and `drawRegulatedZones()` signatures

## Next Steps
None pending — feature is stable. Open for future enhancement:
- Vessel-size filter integration in display pipeline
- Per-type visibility toggles (speed, anchoring, access, etc.)
- Zone tap interaction (highlight + full details in info banner)
