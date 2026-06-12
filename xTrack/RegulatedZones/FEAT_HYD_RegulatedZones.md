# Hydration: RegulatedZones

**Last Bake:** 2026-06-12 22:37 UTC+2
**State:** multi-source-normalization subfeature complete. Full implementation deployed.

## Session Summary

Implemented the multi-source-normalization plan for RegulatedZones. Added sealed classification system (RegulationClassification), speed source tracking (SpeedSource), IGN API Carto Nature client for Natura 2000 data, enhanced SHOM client with CATREA/RESTRN/INFORM/TXTDSC parsing, 3-way aggregator with Haversine dedup, 2 new display categories (ENVIRONMENTAL, INFORMATION), and updated icon provider.

## Current State

- **SHOM INSPIRE:** 110 zones fetched (3 layers)
- **IGN Natura 2000:** 16 zones fetched (13 habitat + 3 birds), 12 survived dedup
- **SEED:** 3 zones
- **Total after aggregate:** 124 zones
- **New fields:** classification (RegulationClassification), speedSource (SpeedSource), legalDecreeRef — all nullable
- **New display categories:** ENVIRONMENTAL (🌿, blue, 50%), INFORMATION (ℹ️, blue, 50%)
- **APK:** Built and deployed to device (35111FDH2002V9)

## Target Files Modified

- `app/src/main/java/ykws/android/maro/data/regulation/RegulatedZone.kt`
- `app/src/main/java/ykws/android/maro/data/regulation/ShomRegulationClient.kt`
- `app/src/main/java/ykws/android/maro/data/regulation/RegulationAggregator.kt`
- `app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneIconProvider.kt`
- `app/src/test/java/ykws/android/maro/data/regulation/RegulatedZonePrebakeTest.kt`

## Files Created

- `app/src/main/java/ykws/android/maro/data/regulation/IgnCartoNatureClient.kt`
- `app/src/main/java/ykws/android/maro/data/regulation/InpnRegulationClient.kt`

## Next Steps

- `trouble-shoot-reg-layers` subfeature — hexagon rendering fix verification
- `reg-zones-filtering` subfeature — vessel size filtering and zone type gates
- `add-zone-text` subfeature — zone name labels on map polygons
