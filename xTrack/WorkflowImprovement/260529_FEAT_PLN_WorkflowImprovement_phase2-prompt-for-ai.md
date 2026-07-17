<!-- scope: feature -->
# Phase 2 — Protobuf Binary Cache for Coastline Data

## Context (Phase 1 Complete)

The `CoastlineGenerator` now produces a `CoastlineData` object. The full enriched data model is:

```kotlin
data class CoastlineData(
    val mainland: CoastlineSegment,
    val islands: List<CoastlineSegment>,
    val metadata: CoastlineMetadata,    // source, pointCount, meanSpacingM, totalLengthKm,
                                        // epsilonM, fetchTimestampMs, projectionRefLat
    val regionId: String,               // e.g. "nice-frejus"
    val boundingBox: BoundingBox        // latSouth/North, lonWest/East
)

data class CoastlineSegment(
    val osmWayId: Long,                 // OSM way ID (not "coast-0")
    val points: List<CoastlinePoint>,   // enriched points
    val isMainland: Boolean,
    val isClosed: Boolean
)

data class CoastlinePoint(
    val lat: Float, val lon: Float,     // WGS84 degrees
    val xM: Float, val yM: Float,       // projected meters (local Cartesian, ref in metadata)
    val edgeDxM: Float, val edgeDyM: Float  // offset to next point in meters
)

data class CoastlineMetadata(
    val source: String,
    val pointCount: Int,
    val meanSpacingM: Double,
    val totalLengthKm: Double,
    val epsilonM: Double?,
    val fetchTimestampMs: Long,
    val projectionRefLat: Double        // ref latitude used for xM/yM projection
)

data class BoundingBox(
    val latSouth: Double, val latNorth: Double,
    val lonWest: Double, val lonEast: Double
)
```

**Current problem**: `CoastlineRepository` holds `CoastlineData` in memory only. Every app launch re-fetches from the Overpass API. No offline capability.

## Goal

Implement a **file-based binary cache** using **Protocol Buffers**:

1. Serialize `CoastlineData` → Protobuf bytes → write to file in app's internal storage
2. Read Protobuf bytes → deserialize → `CoastlineData`
3. Cache-aside pattern: check cache first, generate only on cache miss
4. "Régénérer" button still triggers a fresh OSM fetch and overwrites cache

## Storage Strategy

- **Format**: Protocol Buffers (`protobuf-javalite` or `wire` library for Android)
- **Location**: `context.filesDir / "coastlines" / "{regionId}.bin"`
- **One file per region**: e.g., `coastlines/nice-frejus.bin`
- **No Room/SQLite**: Overkill when we always load the entire coastline into memory

## Architecture

```kotlin
// In CoastlineRepository:

fun loadCoastline(regionId: String) {
    val cached = readFromCache(regionId)
    if (cached != null) {
        coastlineData = cached
        _state.value = CoastlineState.Ready(cached)
        return
    }
    // Cache miss → generate from OSM
    val result = generator.generate(regionId) { ... }
    writeToCache(regionId, result)
    coastlineData = result
    _state.value = CoastlineState.Ready(result)
}

fun refreshCoastline(regionId: String) {
    deleteCacheFile(regionId)
    loadCoastline(regionId)  // forces re-fetch
}
```

## Protobuf Schema (Proposal)

```protobuf
syntax = "proto3";

message CoastlineCache {
    string region_id = 1;
    double lon_west = 2;
    double lon_east = 3;
    double lat_south = 4;
    double lat_north = 5;
    int64 fetch_timestamp_ms = 6;
    double projection_ref_lat = 7;
    double epsilon_m = 8;
    string source = 9;

    Polyline mainland = 10;
    repeated Polyline islands = 11;
}

message Polyline {
    int64 osm_way_id = 1;
    bool is_closed = 2;
    // Packed: [lat0, lon0, xM0, yM0, dx0, dy0, lat1, lon1, xM1, yM1, dx1, dy1, ...]
    // 6 floats per point = 24 bytes per point
    repeated float data = 3 [packed = true];
}
```

## Important Notes

- `CoastlinePoint` uses **Float** (32-bit) — use Protobuf `float` type, not `double`
- Edge vectors (`edgeDxM`, `edgeDyM`) are **critical** — they enable zero-trig spatial queries
- `projectionRefLat` must be preserved — it's used by `isOnWater()` and `distanceToCoastMeters()` to project GPS query points
- Island vs mainland distinction must be preserved (`isMainland` is implicit from separate mainland/islands fields in the proto, or from `mainland` vs `islands` list position)

## Files to Create/Modify

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/proto/coastline.proto` | **Create** | Protobuf schema |
| `app/src/main/java/ykws/android/maro/data/coastline/CoastlineSerializer.kt` | **Create** | serialize/deserialize `CoastlineData` ↔ Protobuf bytes |
| `app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt` | **Modify** | add `readFromCache()`, `writeToCache()`, cache-aside logic in `generate()` |
| `gradle/libs.versions.toml` | **Modify** | add Protobuf library version |
| `app/build.gradle.kts` | **Modify** | add Protobuf plugin and dependency |

## Build Verification

After changes:
- `./gradlew assembleDebug` → BUILD SUCCESSFUL
- `./gradlew testDebugUnitTest` → all 31 tests pass
- APK installs and runs on device
- On first launch: coastline loads from OSM (wait ~2-5s)
- On second launch: coastline loads from cache (instant)
- "Régénérer" button: re-fetches OSM, overwrites cache
- Airplane mode: coastline should load from cache if previously fetched

## Reference

See [`plans/coastline-persistence-and-optimization-analysis.md`](plans/coastline-persistence-and-optimization-analysis.md) for the full storage format analysis.

Git branch: `feature/coastline-generator-bis` — Phase 1 is already merged here.

