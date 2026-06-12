# Hydration: RegulatedZones

**Last Bake:** 2026-06-12 11:48 (UTC+2)
**State:** `trouble-shoot-reg-layers` subfeature complete (investigation → plan → implementation → tests pass).
**Pending:** Manual Phase C (on-device visual verification) and Phase D (regression checks).

## Summary

Fixed the hexagon-shaped regulated zone rendering bug. Root cause was that seed zones (8-vertex regular octagons) survived the aggregation pipeline due to a 25 m centroid-distance dedup check that failed to detect overlap with true SHOM zones.

## Changes

- **RegulationSeeds.kt:** Removed Port-Cros stub (empty ring)
- **RegulationAggregator.kt:** Replaced centroid-distance + type-equality dedup with `centroidInBbox()` overlap test. Removed `DUP_RADIUS_M`, `mergeZones()`, `zoneDistanceM()`
- **ShomRegulationClient.kt:** Added `SpatialOperations.douglasPeucker(ε=30m)` in `parseRing()` with ring re-closing guard
- **RegulatedZoneSerializer.kt:** Switched from `Json` to `ProtoBuf` with explicit `RegulatedZoneSet.serializer()`
- **RegulationAggregatorTest.kt:** Updated test names/comments to reflect overlap dedup
- **Prebake:** Regenerated `nice-frejus.bin` (59,379 bytes, Protobuf format)

## Target Files

- `app/src/main/java/ykws/android/maro/data/regulation/RegulationAggregator.kt`
- `app/src/main/java/ykws/android/maro/data/regulation/RegulatedZoneSerializer.kt`
- `app/src/main/java/ykws/android/maro/data/regulation/ShomRegulationClient.kt`
- `app/src/main/java/ykws/android/maro/data/regulation/RegulationSeeds.kt`
- `app/src/test/java/ykws/android/maro/data/regulation/RegulationAggregatorTest.kt`
- `data/app-assets/regulated-zones/nice-frejus.bin`
- `plans/regulated-zones-hexagon-fix-plan.md`

## Next Step

Run `apk-build.bat` + `apk-deploy.bat` for on-device visual validation (Phase C).
