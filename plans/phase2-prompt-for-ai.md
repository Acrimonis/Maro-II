# Phase 2 Prompt — Protobuf Binary Cache for Coastline Data

## Context

Phase 1 is complete. The CoastlineGenerator now produces a `CoastlineData` object with enriched data:

```kotlin
data class CoastlineData(
    val mainland: CoastlineSegment,
    val islands: List<CoastlineSegment>,
    val metadata: CoastlineMetadata,    // includes fetchTimestampMs, projectionRefLat, etc.
    val regionId: String,
    val boundingBox: BoundingBox
)

data class CoastlineSegment(
    val osmWayId: Long,
    val points: List<CoastlinePoint>,   // Float lat/lon + edge vectors + projected XY
    val isMainland: Boolean,
    val isClosed: Boolean
)

data class CoastlinePoint(
    val lat: Float, val lon: Float,
    val xM: Float, val yM: Float,       // projected meters
    val edgeDxM: Float, val edgeDyM: Float
)
```

**Current state**: `CoastlineRepository` holds `CoastlineData` in memory only. Data is lost on app restart. Every launch re-fetches from Overpass API.

## Goal

Implement a **file-based binary cache** for coastline data using Protocol Buffers:

1. Serialize `CoastlineData` to a compact Protobuf binary file
2. Cache one file per `regionId` in app's internal storage
3. Cache-aside pattern: load from cache first, fetch OSM only on cache miss
4. User can still "Refresh from OSM" to overwrite cache

## Key Constraints

- **Format**: Protocol Buffers (`protobuf-javalite` or `wire` library for Android)
- **Storage**: File system (`context.filesDir / "coastlines" / "{regionId}.bin"`)
- **No Room**: SQLite is overkill when we always load the entire coastline into memory
- **Backward compatible**: The existing `CoastlineRepository.generate()` and `CoastlineState.Ready` flow must continue working

## Architecture

```
readCoastline(regionId):
  ├── cache file exists?
  │   ├── Yes → deserialize Protobuf → return CoastlineData
  │   └── No  → generator.generate() → serialize Protobuf → write file → return

refreshCoastline(regionId):
  ├── Delete cache file
  └── Same as "No" path above
```

## Important Notes

- `CoastlinePoint` uses `Float` (not Double) — Protobuf `float` type
- Edge vectors (`edgeDxM`, `edgeDyM`) must be preserved — they're critical for zero-trig spatial queries
- The `projectionRefLat` in metadata must be preserved for query-time GPS point projection
- Island vs mainland distinction must be preserved (`isMainland`, `isClosed` flags)
- OSM way IDs must be preserved (`osmWayId`)

## Files to Modify

- `CoastlineRepository.kt` — add cache-aside logic to `generate()`, add `readFromCache()` / `writeToCache()`
- `build.gradle.kts` or `libs.versions.toml` — add Protobuf dependency
- New: `.proto` schema file for the Protobuf message definition
- New: `CoastlineSerializer.kt` — serialize/deserialize `CoastlineData` to/from Protobuf bytes

## Reference Design

See [`plans/coastline-persistence-and-optimization-analysis.md`](plans/coastline-persistence-and-optimization-analysis.md) for the full analysis and Protobuf schema proposal.

## Build Verification

After implementation:
- `./gradlew assembleDebug` must succeed
- `./gradlew testDebugUnitTest` must pass (all existing 31 tests)
- APK must install and run on device (coastline should load from cache on second launch)
