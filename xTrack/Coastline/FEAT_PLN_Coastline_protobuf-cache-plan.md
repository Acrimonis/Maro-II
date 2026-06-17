<!-- scope: feature -->
# Phase 2 — Protobuf Binary Cache Implementation Plan

## Overview

Replace the existing JSON-based coastline cache with a **Protocol Buffers** binary cache. One `.bin` file per region, cache-aside pattern, with "Régénérer" button forcing a fresh OSM fetch.

---

## Pre-Requisites: Missing Classes from Phase 1

Two classes are imported but **not defined** anywhere in the codebase:

| Class | Used In | Missing |
|-------|---------|---------|
| [`CoastlineCache`](app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt:11) | Repository: `loadCache()`, `saveCache()` | Entire `.kt` file |
| [`GenerationProgress`](app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt:16) | Repository, ViewModel, MapScreen | Entire `.kt` file |

These need to be created before or during Phase 2 implementation. The new Protobuf cache will **replace** `CoastlineCache` entirely, but `GenerationProgress` is unrelated to caching and must exist for the code to compile.

---

## Files to Create/Modify

| # | File | Action | Purpose |
|---|------|--------|---------|
| 1 | `app/src/main/proto/coastline.proto` | **Create** | Protobuf schema (`.proto` file) |
| 2 | `gradle/libs.versions.toml` | **Modify** | Add protobuf version, library, plugin |
| 3 | `app/build.gradle.kts` | **Modify** | Add protobuf plugin, dependency, proto source set |
| 4 | `app/src/main/java/ykws/android/maro/data/coastline/CoastlineSerializer.kt` | **Create** | Serialize/deserialize `CoastlineData` ↔ Protobuf bytes |
| 5 | `app/src/main/java/ykws/android/maro/data/model/GenerationProgress.kt` | **Create** | Missing Phase 1 class (required for compilation) |
| 6 | `app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt` | **Modify** | Replace JSON cache with Protobuf cache + cache-aside logic |
| 7 | `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` | **Modify** | Update to use new cache API |

---

## Step-by-Step Implementation

### Step 1 — Create Protobuf Schema

**File**: [`app/src/main/proto/coastline.proto`](app/src/main/proto/coastline.proto)

```protobuf
syntax = "proto3";

package coastline;

option java_package = "ykws.android.maro.data.coastline";
option java_outer_classname = "CoastlineProtos";

message CoastlineCache {
    string region_id = 1;

    // Bounding box (WGS84 degrees)
    double lon_west = 2;
    double lon_east = 3;
    double lat_south = 4;
    double lat_north = 5;

    // Metadata
    int64 fetch_timestamp_ms = 6;
    double projection_ref_lat = 7;
    double epsilon_m = 8;          // 0.0 if not simplified
    string source = 9;

    // Polylines
    Polyline mainland = 10;
    repeated Polyline islands = 11;
}

message Polyline {
    int64 osm_way_id = 1;
    bool is_closed = 2;

    // Packed 6 floats per point:
    //   lat, lon, xM, yM, edgeDxM, edgeDyM
    // 24 bytes per point (6 × float32)
    repeated float data = 3 [packed = true];
}
```

**Design notes**:
- `epsilon_m` set to `0.0` when no simplification was applied (non-nullable proto3 `double`)
- On deserialization: `epsilonM = if (proto.epsilonM != 0.0) proto.epsilonM else null`
- `meanSpacingM` and `totalLengthKm` are **computed on deserialization** from edge vectors, not stored in proto
- `pointCount` is computed from data array length at deserialization time

---

### Step 2 — Gradle Configuration

#### [`gradle/libs.versions.toml`](gradle/libs.versions.toml) — Add:

```toml
[versions]
protobuf = "3.25.3"
protobuf-plugin = "0.9.4"

[libraries]
protobuf-javalite = { group = "com.google.protobuf", name = "protobuf-javalite", version.ref = "protobuf" }

[plugins]
protobuf = { id = "com.google.protobuf", version.ref = "protobuf-plugin" }
```

#### [`app/build.gradle.kts`](app/build.gradle.kts) — Add:

```kotlin
plugins {
    // ... existing plugins ...
    alias(libs.plugins.protobuf)
}

android {
    // ... existing config ...

    sourceSets {
        getByName("main") {
            proto {
                srcDir("src/main/proto")
            }
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().configureEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    // ... existing dependencies ...
    implementation(libs.protobuf.javalite)
}
```

---

### Step 3 — Create CoastlineSerializer

**File**: [`app/src/main/java/ykws/android/maro/data/coastline/CoastlineSerializer.kt`](app/src/main/java/ykws/android/maro/data/coastline/CoastlineSerializer.kt)

**Responsibilities**:
- `serialize(data: CoastlineData): ByteArray` — convert `CoastlineData` → Protobuf bytes
- `deserialize(bytes: ByteArray): CoastlineData` — convert Protobuf bytes → `CoastlineData`
- Internal helpers: `polylineToProto(segment)` and `polylineFromProto(proto, isMainland)`

**Serialization logic** (`CoastlineData` → Proto):
```
CoastlineData {
    regionId, boundingBox, metadata, mainland, islands
}
  ↓
CoastlineCache proto {
    region_id, lon/lat bounds,
    fetch_timestamp_ms, projection_ref_lat, epsilon_m, source,
    mainland: Polyline { osm_way_id, is_closed, data: [lat,lon,xM,yM,dx,dy,...] },
    islands: [Polyline { ... }, ...]
}
```

**Deserialization logic** (Proto → `CoastlineData`):
```
CoastlineCache proto {
    region_id, bounds, metadata fields,
    mainland Polyline,
    islands [Polyline]
}
  ↓
Parse Polyline.data in chunks of 6 floats → CoastlinePoint list
Compute meanSpacingM and totalLengthKm from edge vectors
CoastlineData {
    mainland → CoastlineSegment(isMainland=true)
    islands → List<CoastlineSegment>(isMainland=false)
    metadata → computed from proto fields + edge data
}
```

**Code structure** (pseudocode):
```kotlin
object CoastlineSerializer {

    fun serialize(data: CoastlineData): ByteArray {
        val builder = CoastlineProtos.CoastlineCache.newBuilder()
            .setRegionId(data.regionId)
            .setLonWest(data.boundingBox.lonWest)
            .setLonEast(data.boundingBox.lonEast)
            .setLatSouth(data.boundingBox.latSouth)
            .setLatNorth(data.boundingBox.latNorth)
            .setFetchTimestampMs(data.metadata.fetchTimestampMs)
            .setProjectionRefLat(data.metadata.projectionRefLat)
            .setEpsilonM(data.metadata.epsilonM ?: 0.0)
            .setSource(data.metadata.source)
            .setMainland(segmentToProto(data.mainland))
        data.islands.forEach { builder.addIslands(segmentToProto(it)) }
        return builder.build().toByteArray()
    }

    fun deserialize(bytes: ByteArray): CoastlineData {
        val proto = CoastlineProtos.CoastlineCache.parseFrom(bytes)
        val mainland = segmentFromProto(proto.mainland, isMainland = true)
        val islands = proto.islandsList.map { segmentFromProto(it, isMainland = false) }
        val allSegments = listOf(mainland) + islands

        val totalPoints = allSegments.sumOf { it.points.size }
        val totalLength = computeTotalLength(allSegments)
        val meanSpacing = if (totalPoints > allSegments.size)
            totalLength / (totalPoints - allSegments.size) else 0.0

        return CoastlineData(
            mainland = mainland,
            islands = islands,
            metadata = CoastlineMetadata(
                source = proto.source,
                pointCount = totalPoints,
                meanSpacingM = meanSpacing,
                totalLengthKm = totalLength / 1000.0,
                epsilonM = if (proto.epsilonM != 0.0) proto.epsilonM else null,
                fetchTimestampMs = proto.fetchTimestampMs,
                projectionRefLat = proto.projectionRefLat
            ),
            regionId = proto.regionId,
            boundingBox = BoundingBox(
                latSouth = proto.latSouth, latNorth = proto.latNorth,
                lonWest = proto.lonWest, lonEast = proto.lonEast
            )
        )
    }

    private fun segmentToProto(segment: CoastlineSegment): CoastlineProtos.Polyline {
        val floats = mutableListOf<Float>()
        for (pt in segment.points) {
            floats.add(pt.lat)
            floats.add(pt.lon)
            floats.add(pt.xM)
            floats.add(pt.yM)
            floats.add(pt.edgeDxM)
            floats.add(pt.edgeDyM)
        }
        return CoastlineProtos.Polyline.newBuilder()
            .setOsmWayId(segment.osmWayId)
            .setIsClosed(segment.isClosed)
            .addAllData(floats)
            .build()
    }

    private fun segmentFromProto(
        proto: CoastlineProtos.Polyline,
        isMainland: Boolean
    ): CoastlineSegment {
        val data = proto.dataList
        val points = data.chunked(6) { chunk ->
            CoastlinePoint(
                lat = chunk[0], lon = chunk[1],
                xM = chunk[2], yM = chunk[3],
                edgeDxM = chunk[4], edgeDyM = chunk[5]
            )
        }
        return CoastlineSegment(
            osmWayId = proto.osmWayId,
            points = points,
            isMainland = isMainland,
            isClosed = proto.isClosed
        )
    }

    private fun computeTotalLength(segments: List<CoastlineSegment>): Double {
        var total = 0.0
        for (seg in segments) {
            for (pt in seg.points) {
                total += sqrt(
                    pt.edgeDxM.toDouble().pow(2) +
                    pt.edgeDyM.toDouble().pow(2)
                )
            }
        }
        return total
    }
}
```

---

### Step 4 — Create Missing GenerationProgress

**File**: [`app/src/main/java/ykws/android/maro/data/model/GenerationProgress.kt`](app/src/main/java/ykws/android/maro/data/model/GenerationProgress.kt)

```kotlin
package ykws.android.maro.data.model

data class GenerationProgress(
    val phase: String,
    val progress: Int   // 0-100
)
```

---

### Step 5 — Modify CoastlineRepository

**File**: [`app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt`](app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt)

#### Changes overview:

| Current | New |
|---------|-----|
| `cacheDir = filesDir/coastline_cache/` | `cacheDir = filesDir/coastlines/` |
| Single `coastline.json` file | Per-region `{regionId}.bin` file |
| JSON `CoastlineCache` model | Protobuf `CoastlineProtos.CoastlineCache` |
| `loadCache(): CoastlineCache?` | `readFromCache(regionId): CoastlineData?` |
| `saveCache(data: CoastlineData)` | `writeToCache(regionId, data)` |
| `clearCache()` deletes single file | `deleteCacheFile(regionId)` deletes region file |
| `restoreFromCache(segments, metadata)` | **Removed** — cache loading now returns full `CoastlineData` |
| `generate()` always fetches OSM + saves cache | `generate()` always fetches OSM (no cache logic) |
| — | **New** `loadCoastline(regionId)`: cache-aside (check → miss → generate → cache) |
| — | **New** `refreshCoastline(regionId)`: delete cache → loadCoastline |

#### Cache-aside flow for `loadCoastline(regionId)`:

```
loadCoastline(regionId):
  ┌─ Set state = Loading
  ├─ readFromCache(regionId) → cached: CoastlineData?
  │   ├─ Not null → set coastlineData, spatialIndex, state = Ready → RETURN
  │   └─ Null → continue
  ├─ generator.generate(regionId) → result
  ├─ coastlineData = result
  ├─ spatialIndex = CoastlineSpatialIndex(result.allSegments)
  ├─ writeToCache(regionId, result)
  └─ state = Ready(result)
```

#### `refreshCoastline(regionId)` for "Régénérer" button:

```
refreshCoastline(regionId):
  ├─ deleteCacheFile(regionId)
  └─ loadCoastline(regionId)  // forces cache miss → fresh OSM fetch
```

#### Import changes:

```kotlin
// REMOVE these:
import kotlinx.serialization.json.Json
import ykws.android.maro.data.model.CoastlineCache

// ADD these:
import ykws.android.maro.data.coastline.CoastlineSerializer
import java.io.FileNotFoundException
```

#### Method signatures to add/change:

```kotlin
class CoastlineRepository(
    private val generator: CoastlineGenerator = CoastlineGenerator(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private var cacheDir: File? = null

    // Cache directory changed from "coastline_cache" to "coastlines"
    fun setCacheDir(context: Context) {
        cacheDir = File(context.filesDir, "coastlines")
        cacheDir?.mkdirs()
    }

    // Region-specific cache file
    private fun cacheFile(regionId: String): File? =
        cacheDir?.resolve("$regionId.bin")

    // Protobuf read
    private fun readFromCache(regionId: String): CoastlineData? {
        val file = cacheFile(regionId) ?: return null
        if (!file.exists()) return null
        return try {
            CoastlineSerializer.deserialize(file.readBytes())
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    // Protobuf write
    private fun writeToCache(regionId: String, data: CoastlineData) {
        val file = cacheFile(regionId) ?: return
        try {
            file.writeBytes(CoastlineSerializer.serialize(data))
        } catch (_: Exception) {
            // Non-critical
        }
    }

    // Delete region-specific cache
    private fun deleteCacheFile(regionId: String) {
        cacheFile(regionId)?.delete()
    }

    // New: cache-aside load
    suspend fun loadCoastline(regionId: String = CoastlineGenerator.REGION_ID) {
        _state.value = CoastlineState.Loading
        _progress.value = GenerationProgress("", 0)

        // Check cache first
        val cached = withContext(ioDispatcher) { readFromCache(regionId) }
        if (cached != null) {
            coastlineData = cached
            spatialIndex = CoastlineSpatialIndex(cached.allSegments)
            _state.value = CoastlineState.Ready(data = cached)
            _progress.value = GenerationProgress("Terminé (cache)", 100)
            return
        }

        // Cache miss → full OSM generation
        try {
            val result = withContext(ioDispatcher) {
                generator.generate(regionId = regionId) { phase, pct ->
                    _progress.value = GenerationProgress(phase, pct)
                }
            }
            coastlineData = result
            spatialIndex = CoastlineSpatialIndex(result.allSegments)
            withContext(ioDispatcher) { writeToCache(regionId, result) }
            _state.value = CoastlineState.Ready(data = result)
            _progress.value = GenerationProgress("Terminé", 100)
        } catch (e: Exception) {
            _state.value = CoastlineState.Error(
                message = e.message ?: "Erreur lors de la génération."
            )
        }
    }

    // New: force re-fetch
    suspend fun refreshCoastline(regionId: String = CoastlineGenerator.REGION_ID) {
        withContext(ioDispatcher) { deleteCacheFile(regionId) }
        loadCoastline(regionId)
    }

    // Keep the existing generate() method but remove saveCache call from it
    // Or rename it and make it private, called only from loadCoastline
}
```

**Note**: The existing `generate()` method currently calls `saveCache(result)` at line 154. This call must be **removed** when migrating to the new cache system, since `saveCache()` no longer exists. The `generate()` method becomes a "generate-only" method (no caching).

Also remove the old `loadCache()`, `saveCache()`, `clearCache()`, and `restoreFromCache()` methods entirely.

---

### Step 6 — Modify CoastlineViewModel

**File**: [`app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt)

#### `initCache(context)` changes:

```kotlin
// OLD:
fun initCache(context: Context) {
    repository.setCacheDir(context)
    viewModelScope.launch {
        val cache = repository.loadCache()
        if (cache != null) {
            repository.restoreFromCache(cache.segments, cache.metadata)
            // ... compute center from cached data
        }
    }
}

// NEW:
fun initCache(context: Context) {
    repository.setCacheDir(context)
    viewModelScope.launch {
        repository.loadCoastline()  // cache-aside: tries cache first, falls back to OSM
        val data = repository.getCoastlineData()
        if (data != null) {
            val allPoints = data.allSegments.flatMap { it.points }
            if (allPoints.isNotEmpty()) {
                val avgLat = allPoints.sumOf { it.lat.toDouble() } / allPoints.size
                val avgLon = allPoints.sumOf { it.lon.toDouble() } / allPoints.size
                _mapCenter.value = LatLng(avgLat, avgLon)
                _isWater.value = repository.isOnWater(avgLat, avgLon)
            }
        }
    }
}
```

#### `loadCoastline()` (button handler) changes:

```kotlin
// OLD:
fun loadCoastline() {
    viewModelScope.launch {
        repository.clearCache()
        repository.generate()
        // ... compute center
    }
}

// NEW:
fun loadCoastline() {
    viewModelScope.launch {
        repository.refreshCoastline()  // deletes cache → fresh OSM fetch
        val data = repository.getCoastlineData()
        if (data != null) {
            val allPoints = data.allSegments.flatMap { it.points }
            if (allPoints.isNotEmpty()) {
                val avgLat = allPoints.sumOf { it.lat.toDouble() } / allPoints.size
                val avgLon = allPoints.sumOf { it.lon.toDouble() } / allPoints.size
                _mapCenter.value = LatLng(avgLat, avgLon)
                _isWater.value = repository.isOnWater(avgLat, avgLon)
            }
        }
    }
}
```

---

### Step 7 — Clean Up: Remove Old JSON Cache Model

The `CoastlineCache` data class (if it exists) is no longer needed and should be deleted:
- Delete `app/src/main/java/ykws/android/maro/data/model/CoastlineCache.kt` (if it exists)
- Remove `import ykws.android.maro.data.model.CoastlineCache` from `CoastlineRepository.kt`
- Remove `import kotlinx.serialization.json.Json` from `CoastlineRepository.kt` (no longer needed for caching)

---

## Data Flow Diagram

```
┌─────────────────────────────────────────────┐
│             App Launch (initCache)          │
└──────────────────────┬──────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│        CoastlineViewModel.initCache()        │
│   calls repository.setCacheDir(context)      │
│   calls repository.loadCoastline(regionId)   │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│         CoastlineRepository.loadCoastline()  │
├──────────────────────────────────────────────┤
│ 1. readFromCache(regionId)                   │
│    ├─ File exists? → deserialize → return    │
│    └─ No file → null                         │
│                                              │
│ 2. Cache hit? → Ready state → DONE          │
│                                              │
│ 3. Cache miss → generator.generate(regionId) │
│    → writeToCache(regionId, result)          │
│    → Ready state                             │
└──────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│         "Régénérer" Button Click            │
└──────────────────────┬──────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│     CoastlineViewModel.loadCoastline()       │
│   calls repository.refreshCoastline(regionId)│
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│      CoastlineRepository.refreshCoastline()  │
├──────────────────────────────────────────────┤
│ 1. deleteCacheFile(regionId)                 │
│ 2. loadCoastline(regionId) → cache miss →    │
│    fresh OSM fetch → new cache               │
└──────────────────────────────────────────────┘
```

## File Layout After Implementation

```
Internal storage (filesDir):
└── coastlines/
    ├── nice-frejus.bin        ← Protobuf binary cache (~30 KB)
    └── (future regions).bin

Source code:
app/
├── src/main/proto/
│   └── coastline.proto        ← Protobuf schema (NEW)
├── src/main/java/.../data/coastline/
│   ├── CoastlineGenerator.kt
│   ├── CoastlineRepository.kt (MODIFIED)
│   └── CoastlineSerializer.kt (NEW)
└── src/main/java/.../data/model/
    ├── GenerationProgress.kt  (NEW, if missing)

gradle/
└── libs.versions.toml         (MODIFIED)
app/build.gradle.kts           (MODIFIED)
```

## Build Verification

After implementation, verify with:
```
cd Maro II
./gradlew assembleDebug          → BUILD SUCCESSFUL
./gradlew testDebugUnitTest      → all 31 tests pass
```

## Risks & Considerations

1. **Protobuf generated code**: After adding the proto file and Gradle plugin, a Gradle sync/build will auto-generate `CoastlineProtos.java` in `app/build/generated/source/proto/`. This generated class is what `CoastlineSerializer` imports.

2. **Backward compatibility**: Old JSON cache files in `coastline_cache/coastline.json` will be ignored. Users will get a fresh OSM fetch on first launch after the update. This is acceptable.

3. **Cache staleness**: Currently no TTL/staleness check. The cache is used until explicitly invalidated (by "Régénérer" button). If stale cache detection is needed later, add a `maxAgeMs` parameter to `readFromCache()`.

4. **Thread safety**: `readFromCache` and `writeToCache` are called from `withContext(ioDispatcher)`, consistent with existing IO-dispatched operations.

5. **Error resilience**: Cache read failures (corrupted files, I/O errors) fall back to a fresh OSM fetch. Cache write failures are silently ignored (non-critical).

