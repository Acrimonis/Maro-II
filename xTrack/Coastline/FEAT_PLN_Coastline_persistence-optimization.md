# CoastlineGenerator — Data Model & Storage Analysis

## Summary of Findings (for AI context injection)

### #1 — Edge Vectors (Pre-compute at Generation Time)

**What**: For each consecutive pair of coastline points `(A, B)`, pre-compute the Cartesian offset in meters `(dx_m, dy_m)` and store it alongside point B.

**Why**: Every spatial operation (`pointToSegmentDistance`, `crossProductZ`) converts lat/lon to local Cartesian meters on every call. This conversion is repeated thousands of times. Pre-computing edge vectors eliminates this repeated work — the edge is already in meters.

**Where**: In `CoastlineGenerator.generate()`, after simplification and orientation, before returning the result. Store in a new field on the point data structure or alongside it.

**Impact**: All distance/zone queries become faster by avoiding redundant projection math.

### #2 — Data Model Enrichments

**Data set gaps identified in the current output:**

| Gap | Fix |
|-----|-----|
| No region identifier | Add `regionId` (e.g., "nice-frejus"), `boundingBox` to result |
| No fetch timestamp | Add `fetchTimestampMs` for cache staleness checks |
| Mainland/island not explicit | Add `isMainland: Boolean` and `isClosed: Boolean` to segment |
| Segment IDs meaningless | Store OSM way ID instead of "coast-0" |
| Double (64-bit) for lat/lon | Use Float (32-bit) — sufficient for 1m precision |
| Total length not in metadata | Add `totalLengthKm` |
| OSM tags discarded | Change `out geom;` → `out body geom;` to fetch tags; extract `coastline=island` for explicit island detection |
| OSM node IDs discarded | Use node IDs in assembly algorithm for reliable endpoint matching |

**Recommended data structure:**

```kotlin
data class CoastlineData(
    val mainland: CoastlinePolyline,       // single continuous open polyline
    val islands: List<CoastlinePolyline>,  // zero or more closed polylines
    val metadata: CoastlineMetadata,
    val regionId: String,
    val boundingBox: BoundingBox
)

data class CoastlinePolyline(
    val osmWayId: Long,                    // OSM way ID (e.g., 12345678)
    val points: List<CoastlinePoint>       // ordered, oriented
)

data class CoastlinePoint(
    val lat: Float,                        // degrees × 1e7 stored as Float
    val lon: Float,
    val edgeDxM: Float,                    // pre-computed: dx to next point in meters
    val edgeDyM: Float                     // pre-computed: dy to next point in meters
)
```

---

## Data Storage / Cache Format Analysis

The output is currently in-memory only (Kotlin objects). Once we persist it for caching, **JSON is the worst option** — verbose, slow to parse, wasteful for numeric arrays.

### Format Comparison

| Format | Size (3,000 pts) | Read Time | Parse Step? | Schema? | Android Setup |
|--------|-----------------|-----------|-------------|---------|---------------|
| **JSON** | ~200 KB | ~50 ms | Yes (full parse) | No | Built-in (kotlinx.serialization) |
| **Protocol Buffers** | ~30 KB | ~5 ms | Yes (binary parse) | Yes (.proto) | Add protobuf-javalite or Wire |
| **FlatBuffers** | ~35 KB | **~0.1 ms** | **No** (zero-copy) | Yes (.fbs) | Add flatbuffers-gradle |
| **Custom binary** | ~24 KB | ~1 ms | Yes (manual read) | Implicit | None (raw bytes) |

### Recommendation: Protocol Buffers

**Best balance** of size, speed, and maintainability for this use case.

#### Why not FlatBuffers?
Zero-copy is amazing for read performance — but the build setup for FlatBuffers on Android (`.fbs` schema compiler, Gradle plugin) adds complexity. For a write-once-read-many cache loaded once at app startup, Protobuf's parse time (~5ms for 30KB) is more than sufficient.

#### Why not JSON?
- 6-7× larger on disk
- Slower to parse (string → numbers → objects)
- No type safety for the binary payload
- Wasteful for arrays of floats (repeated `"lat": 43.55, "lon": 7.00`)

#### Protobuf Schema Proposal

```protobuf
syntax = "proto3";

package ykws.android.maro.data.coastline;

message CoastlineCache {
    string region_id = 1;
    double lon_west = 2;
    double lon_east = 3;
    double lat_south = 4;
    double lat_north = 5;
    int64 fetch_timestamp_ms = 6;

    Polyline mainland = 7;
    repeated Polyline islands = 8;

    optional double epsilon_m = 9;
    optional string source = 10;
}

message Polyline {
    int64 osm_way_id = 1;
    // Repeated pairs: [lat0, lon0, dx0, dy0, lat1, lon1, dx1, dy1, ...]
    // packed encoding = ~8 bytes per point
    repeated float data = 2 [packed = true];
}
```

#### Storage Strategy (File-based)

```
Internal storage:
└── coastlines/
    ├── nice-frejus.bin      ← Protobuf binary cache
    ├── marseille.bin        ← another region
    └── corsica.bin
```

- Each region is one `.bin` file
- Filename = `regionId.bin`
- Read: `File.readBytes()` → `Protobuf.parseFrom(bytes)`
- Write: `Protobuf.toByteArray()` → `File.writeBytes()`

#### Cache-Aside Flow

```
loadCoastline(regionId):
  ├── File "coastlines/{regionId}.bin" exists?
  │   ├── Yes → read & parse Protobuf → return CoastlineData
  │   └── No  → fetch OSM → pipeline → serialize Protobuf → write file → return CoastlineData
  │
refreshCoastline(regionId):
  ├── Delete file
  └── Same as "No" path above
```

#### Room (SQLite) Alternative Considered

Room adds value when you need to **query subsets** of data (e.g., "find all points within this bounding box"). But for coastline caching, we always **load the entire region's coastline** into memory — there's no benefit to SQL queries on individual points.

**Verdict**: Room is overkill. A simple file-based cache with Protobuf is the right tool.

### Storage Summary

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Format | **Protocol Buffers** | 6× smaller than JSON, fast parse, schema-safe |
| Backend | **File system** (internal storage) | One file per region, no SQL overhead |
| Read path | Load whole file, parse once, keep in memory | Coastline data is small (~30 KB per region) |
| Cache key | `regionId` (filename) | Simple, unique per region |
| Refresh | Delete file → re-fetch OSM | On user demand |

---

## Next Topics (Pending)

- **Granularity**: How many coastline points are actually needed for each use case (map display, water/land detection, distance check, 300m zone)?
- **Persistence workflow**: When to save, when to refresh, how to handle multiple regions.
