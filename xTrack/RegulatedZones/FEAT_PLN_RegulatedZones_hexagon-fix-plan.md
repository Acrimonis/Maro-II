<!-- scope: feature -->
# Regulated Zones Hexagon Rendering Bug — Fix Plan

## Problem Summary

Two regulated zone map overlays (Cap d'Antibes speed zone, Îles de Lérins navigation restriction) render as crude **geometric octagons** instead of their true SHOM WFS GeoJSON polygon shapes. Root cause: seed zones (created as 8-vertex regular polygons in [`RegulationSeeds.kt`](../app/src/main/java/ykws/android/maro/data/regulation/RegulationSeeds.kt)) survive the aggregation pipeline because the deduplication logic in [`RegulationAggregator.kt`](../app/src/main/java/ykws/android/maro/data/regulation/RegulationAggregator.kt:90) uses a **25 m centroid-distance check** plus a **type-equality gate** — neither of which correctly detects that a seed zone overlaps with a SHOM zone.

---

## Data Flow (After Fix)

```
SHOM WFS (GeoJSON)
    │
    ▼
ShomRegulationClient.fetchZones()
    │  parseRing() → apply RDP simplification (ε=30m)
    │  parsePolygon() → parseFeature() → RegulatedZone
    ▼
List<RegulatedZone> (SHOM)
    │
    ▼
RegulationSeeds.getSeeds()
    │  (Port-Cros stub removed)
    ▼
List<RegulatedZone> (SEED)
    │
    ▼
RegulationAggregator.aggregate()
    │
    ├─ Step A: Collect — all SHOM + all seeds
    ├─ Step B: Deduplicate — overlap test replaces centroid-distance + type gate
    │    • For each seed: if centroid falls within any SHOM zone's bounding box,
    │      discard the seed (SHOM zone is authoritative)
    │    • Remaining seeds (no overlapping SHOM zone) kept as-is
    ├─ Step C: Validate — reject zones outside bbox
    ├─ Step D: Sort — by zoneType ordinal, then area descending
    └─ Step E: Build metadata
    │
    ▼
RegulatedZoneSet
    │
    ▼
RegulatedZoneSerializer.serialize()
    │  Protobuf binary (was JSON)
    │
    ▼
data/app-assets/regulated-zones/nice-frejus.bin
```

---

## Step-by-Step Implementation Plan

### Step 1: Remove Port-Cros stub seed zone

**File:** [`RegulationSeeds.kt`](../app/src/main/java/ykws/android/maro/data/regulation/RegulationSeeds.kt)

**Change:** Remove the Port-Cros `RegulatedZone` entry (lines 66–74). This zone has an empty `outerRing` list, which is invalid geometry. The other three seeds (Cap d'Antibes, Îles de Lérins, Baie des Anges) remain — they will now be properly merged with their SHOM counterparts.

```diff
-        // ── d. Parc National de Port-Cros (référence) ──────────────────────
-        // Stub: empty polygon, will be refined when detailed geometry is available.
-        RegulatedZone(
-            outerRing = emptyList(),
-            zoneType = RegulatedZoneType.ENVIRONMENTAL,
-            name = "Parc National de Port-Cros (référence)",
-            source = "SEED",
-            description = "Zone environnementale du Parc National de Port-Cros (en attente de géométrie précise)"
-        )
```

---

### Step 2: Replace deduplication logic in RegulationAggregator

**File:** [`RegulationAggregator.kt`](../app/src/main/java/ykws/android/maro/data/regulation/RegulationAggregator.kt)

#### 2a. Remove `DUP_RADIUS_M` constant (line 23)

```diff
-    /** Maximum centroid distance (metres) to consider two zones a duplicate. */
-    private const val DUP_RADIUS_M = 25.0
```

#### 2b. Simplify `collect()` step (lines 46–53)

Replace the centroid-distance loop with a simple append-all. The deduplication step handles seeds that overlap SHOM zones.

```diff
-        for (seed in seedZones) {
-            val isNearShom = shomZones.any { shom ->
-                zoneDistanceM(shom, seed) < DUP_RADIUS_M
-            }
-            if (!isNearShom) {
-                collected.add(seed)
-            }
-        }
+        collected.addAll(seedZones)
```

Update the comment on lines 41–42 to reflect the new approach.

#### 2c. Rewrite `deduplicate()` method (lines 90–113)

Replace the centroid-distance + type-equality logic with **overlap-based deduplication**:

1. Separate SHOM zones from non-SHOM zones.
2. For each non-SHOM zone, check if its centroid falls within *any* SHOM zone's axis-aligned bounding box.
3. If yes, the zone is a duplicate — discard it (SHOM attributes are authoritative).
4. If no, keep the zone as standalone.

```kotlin
private fun deduplicate(zones: List<RegulatedZone>): List<RegulatedZone> {
    val shomZones = zones.filter { it.source == "SHOM" }
    val nonShom = zones.filter { it.source != "SHOM" }
    val result = shomZones.toMutableList()

    for (candidate in nonShom) {
        val isOverlappingShom = shomZones.any { shom ->
            centroidInBbox(centroid(candidate), shom.outerRing)
        }
        if (!isOverlappingShom) {
            result.add(candidate)
        }
    }

    return result
}
```

#### 2d. Add `centroidInBbox()` helper method

```kotlin
/**
 * Check whether [point] falls within the axis-aligned bounding box of [ring].
 * A simple bounds check — sufficient for overlap detection in the
 * Nice–Fréjus corridor which does not cross the antimeridian.
 */
private fun centroidInBbox(point: LatLng, ring: List<LatLng>): Boolean {
    if (ring.isEmpty()) return false
    val minLat = ring.minOf { it.latitude }
    val maxLat = ring.maxOf { it.latitude }
    val minLon = ring.minOf { it.longitude }
    val maxLon = ring.maxOf { it.longitude }
    return point.latitude in minLat..maxLat &&
            point.longitude in minLon..maxLon
}
```

#### 2e. Remove `mergeZones()` (lines 123–134)

Since deduplication now either discards the seed or keeps it standalone, `mergeZones()` is no longer called. Remove it to avoid dead code.

```diff
-    private fun mergeZones(duplicates: List<RegulatedZone>): RegulatedZone {
-        val shomZone = duplicates.find { it.source == "SHOM" }
-        val primary = shomZone ?: duplicates.first()
-        val hasSeed = duplicates.any { it.source == "SEED" }
-        val mergedSource = when {
-            hasSeed && shomZone != null -> "SHOM+SEED"
-            else -> primary.source
-        }
-        return primary.copy(source = mergedSource)
-    }
```

#### 2f. Remove unused `zoneDistanceM()` helper (lines 175–182)

```diff
-    private fun zoneDistanceM(a: RegulatedZone, b: RegulatedZone): Double {
-        val aCentroid = centroid(a)
-        val bCentroid = centroid(b)
-        val refLat = Math.toRadians((aCentroid.latitude + bCentroid.latitude) / 2.0)
-        val dLat = (aCentroid.latitude - bCentroid.latitude) * 111_320.0
-        val dLon = (aCentroid.longitude - bCentroid.longitude) * 111_320.0 * cos(refLat)
-        return sqrt(dLat * dLat + dLon * dLon)
-    }
```

Keep `approximateArea()` — it is still used by the sort step at line 71.

#### 2g. Remove unused imports

Remove imports that are no longer needed after removing `zoneDistanceM()`:
- `import kotlin.math.abs` — still used by `approximateArea()`, keep it
- `import kotlin.math.cos` — still used by `approximateArea()`, keep it
- `import kotlin.math.sqrt` — only used by `zoneDistanceM()`, **remove this**

```diff
- import kotlin.math.sqrt
```

---

### Step 3: Add RDP simplification to ShomRegulationClient

**File:** [`ShomRegulationClient.kt`](../app/src/main/java/ykws/android/maro/data/regulation/ShomRegulationClient.kt)

**Change:** Apply Ramer-Douglas-Peucker simplification in `parseRing()` after building the raw ring, using [`SpatialOperations.douglasPeucker()`](../app/src/main/java/ykws/android/maro/spatial/SpatialOperations.kt:400) with ε ≈ 30.0 m.

**Add import:**
```kotlin
import ykws.android.maro.spatial.SpatialOperations
```

**Modify `parseRing()` (line 280):**

```diff
     private fun parseRing(ring: JsonElement): List<LatLng>? {
         val points = ring.jsonArray ?: return null
-        return points.mapNotNull { point ->
+        val raw = points.mapNotNull { point ->
             val arr = point.jsonArray ?: return@mapNotNull null
             if (arr.size < 2) return@mapNotNull null
             val x = arr[0].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
             val y = arr[1].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
             if (kotlin.math.abs(x) > 180.0 || kotlin.math.abs(y) > 90.0) {
                 webMercatorToWgs84(x, y)
             } else {
                 LatLng(latitude = y, longitude = x)
             }
-        }.takeIf { it.size >= 3 }
+        }.takeIf { it.size >= 3 } ?: return null
+
+        // Ramer-Douglas-Peucker simplification: ε ≈ 30m, visually indicative
+        // not survey-grade. Match pattern from CoastlineGenerator.
+        val simplified = SpatialOperations.douglasPeucker(raw, epsilonM = 30.0)
+
+        // Ensure ring has ≥3 points and is closed after simplification
+        if (simplified.size < 3) return null
+        return if (simplified.first() != simplified.last()) {
+            simplified + simplified.first()
+        } else {
+            simplified
+        }
     }
```

**Key consideration:** The `douglasPeucker()` function works on open polylines. The `parseRing()` input is a closed ring (first == last). The function preserves first and last points, so the ring stays closed. However, if simplification collapses the ring to fewer than 3 unique vertices, return null (invalid polygon).

---

### Step 4: Switch serializer from JSON to Protobuf

**File:** [`RegulatedZoneSerializer.kt`](../app/src/main/java/ykws/android/maro/data/regulation/RegulatedZoneSerializer.kt)

**Change:** Replace `Json` with `kotlinx.serialization.protobuf.Protobuf`.

```diff
- import kotlinx.serialization.encodeToString
- import kotlinx.serialization.decodeFromString
- import kotlinx.serialization.json.Json
+ import kotlinx.serialization.ExperimentalSerializationApi
+ import kotlinx.serialization.protobuf.Protobuf
```

```diff
-    private val json = Json {
-        ignoreUnknownKeys = true
-        encodeDefaults = false
-    }
+    private val protobuf = Protobuf
```

```diff
     fun serialize(data: RegulatedZoneSet): ByteArray =
-        json.encodeToString(data).encodeToByteArray()
+        protobuf.encodeToByteArray(data)

     fun deserialize(bytes: ByteArray): RegulatedZoneSet =
-        json.decodeFromString(bytes.decodeToString())
+        protobuf.decodeFromByteArray(bytes)
```

Also consider adding `@OptIn(ExperimentalSerializationApi::class)` at the file level if the project's `kotlinx-serialization-protobuf` version still marks `Protobuf` as experimental.

---

### Step 5: Verify RegulatedZone.kt annotations

**File:** [`RegulatedZone.kt`](../app/src/main/java/ykws/android/maro/data/regulation/RegulatedZone.kt)

**Status:** No changes needed. All data classes already have:
- `@Serializable` on `RegulatedZone`, `RegulationMetadata`, `RegulatedZoneSet`, `RegulatedZoneType`, `VesselSizeRestriction`
- `@ProtoNumber(1..n)` on all fields

Confirmed: [`LatLng`](../app/src/main/java/ykws/android/maro/data/model/LatLng.kt) already has `@Serializable` + `@ProtoNumber(1)` / `@ProtoNumber(2)`.

---

### Step 6: Update tests

#### 6a. Update [`RegulationAggregatorTest.kt`](../app/src/test/java/ykws/android/maro/data/regulation/RegulationAggregatorTest.kt)

**Changes needed:**

1. **Update test `dedup removes seed within 25m of SHOM zone with same type`** (line 45):
   - Rename to reflect new overlap logic
   - The test should verify that a seed whose centroid falls within a SHOM zone's bounding box is removed
   - Example: create a SHOM zone with a bounding box that clearly contains the seed's centroid

2. **Update test `keeps both zones when centroids are far apart`** (line 68):
   - This test still works — seed centroid far from SHOM bounding box → both kept

3. **Update test `keeps zones of different types even when close`** (line 89):
   - With the type gate removed, different-type zones at the same location will now be merged
   - The test should be updated to expect **1 zone** instead of 2, since the overlap test will detect that one's centroid falls within the other's bounding box
   - OR: adjust the geometries so they don't overlap (different positions)

4. **Update test `SHOM zone wins over SEED for same location same type`** (line 187):
   - This test creates SHOM + OSM + SEED at the exact same location
   - After the fix: the SHOM zone is kept, OSM and SEED are discarded because their centroids fall within SHOM's bounding box
   - Expected result: 1 zone, named "Authoritative" — this test should still pass

5. **Add new test: `seed removed when centroid overlaps SHOM bounding box`**
   - Create a SHOM zone with specific ring, create a seed whose centroid falls within that ring's bbox → expect 1 zone

6. **Add new test: `seed kept when centroid outside SHOM bounding box`**
   - Create a SHOM zone, create a seed with centroid clearly outside its bbox → expect 2 zones

7. **Update `metadata contains correct source counts`** (line 146):
   - Since seeds at different locations are expected, this test should still show 2 sources

#### 6b. Update [`RegulatedZoneSerializerTest.kt`](../app/src/test/java/ykws/android/maro/data/regulation/RegulatedZoneSerializerTest.kt)

**Changes needed:**

1. The existing tests should **work as-is** since they test roundtrip (serialize → deserialize → assert equality). The format change from JSON to Protobuf should be transparent for roundtrip tests.

2. However, if `encodeDefaults = false` was masking default values in JSON, and Protobuf's default behavior differs, some fields might behave differently. Add explicit checks:
   - An empty `holes` list should roundtrip as empty
   - A `null vesselSizeRestriction` should roundtrip as null
   - Default `""` strings should roundtrip correctly

3. **Potential issue:** Protobuf does not encode default values (`0`, `0.0`, `""`, `false`, `null`) by default. The tests at lines 63–84 (roundtrip with null `vesselSizeRestriction`) and lines 46–61 (empty zones) should verify these still work.

#### 6c. Update [`RegulatedZonePrebakeTest.kt`](../app/src/test/java/ykws/android/maro/data/regulation/RegulatedZonePrebakeTest.kt)

**Changes needed:**

1. No code changes expected — the prebake test just calls the aggregator and serializer, both of which are being updated.
2. However, the **output `.bin` file** will now be Protobuf instead of JSON. The file size should be significantly smaller.
3. Run the prebake with `-Dmaro.prebake=true` to regenerate the asset.

---

### Step 7: Verify bake tool

**File:** [`tools/bake-regulated-zones.bat`](../tools/bake-regulated-zones.bat)

No changes needed. The batch file invokes `gradlew testDebugUnitTest --tests "*RegulatedZonePrebakeTest*" -Dmaro.prebake=true --rerun-tasks`, which will automatically pick up the new code changes.

---

## Edge Cases and Caveats

### 1. Seed Centroid Near SHOM Zone Boundary

A seed centroid might fall *just outside* the SHOM zone's bounding box but still partially overlap with it. In this case, the seed would survive deduplication and render as an octagon alongside the real SHOM polygon. This is a **false negative** for the overlap test but acceptable because:
- The seed's octagon will be visually distinguishable from the real SHOM polygon
- The SHOM zone's rendering will still be correct
- A full polygon-intersection test would be more accurate but adds complexity

**Mitigation:** Expand the bounding box by a small margin (e.g., 0.001° ≈ 100 m) in `centroidInBbox()` to catch near-miss cases. Alternatively, use a point-in-polygon test from `SpatialOperations` if available.

### 2. RDP Simplification Collapse

A very small or narrow SHOM polygon might collapse to < 3 points after simplification. The code guards against this with `if (simplified.size < 3) return null`, which causes the zone to be dropped. This is acceptable for tiny zones that would be visually insignificant.

### 3. Protobuf Backward Compatibility

The existing `.bin` asset was serialized as JSON. After switching to Protobuf, the old file is unreadable. The prebake must regenerate the asset. The `PrebakeTest` runs during build and replaces the file, so this is handled automatically.

### 4. Protobuf Default Value Handling

Protobuf does not encode default values (`0`, `0.0`, `false`, `""`, `emptyList`, `null`). The `RegulatedZoneSet` has default values for some fields (e.g., `holes = emptyList()`, `name = ""`). On deserialization, missing fields will take their default values from the Kotlin constructor. This is correct behavior and should not break anything.

**Exception:** `fetchTimestampMs` has no default and will always be encoded. `totalZones` and `sourceCount` are `Int` with no defaults — also always encoded. Good.

### 5. Test Data Need

The existing aggregator tests use tiny triangle rings (`ringAround`) with 0.001° offsets. For the bounding box check to work correctly, the test SHOM zones must have bounding boxes large enough to contain the test seed zones. The current `ringAround(43.56, 7.13, offset=0.001)` creates a ~100m triangle, and the seed at `ringAround(43.5601, 7.1301)` has its centroid at (43.5601, 7.1301), which IS within the SHOM zone's bbox of [43.559, 43.561] x [7.129, 7.131]. So the existing test data should still work with the new overlap logic.

### 6. OSM Source Handling

The test at line 187 (`SHOM zone wins over SEED for same location same type`) uses source "OSM". The new dedup logic checks `it.source == "SHOM"` to identify authoritative zones. OSM zones without an overlapping SHOM zone will be kept. This is correct.

---

## Testing Strategy

| Test | File | What it verifies |
|------|------|-----------------|
| `single zone returns same zone` | `AggregatorTest` | Basic pipeline works |
| `dedup removes seed within SHOM bbox` | `AggregatorTest` | **Updated** — seed centroid inside SHOM bbox → removed |
| `keeps both zones when far apart` | `AggregatorTest` | Unchanged — non-overlapping seeds survive |
| `keeps zones of different types` | `AggregatorTest` | **Updated** — different-type zones at same location now merge |
| `empty input returns empty set` | `AggregatorTest` | Unchanged |
| `rejects zone outside bbox` | `AggregatorTest` | Unchanged |
| `metadata contains correct source counts` | `AggregatorTest` | Unchanged |
| `metadata timestamp is set` | `AggregatorTest` | Unchanged |
| `SHOM zone wins over SEED for same location` | `AggregatorTest` | Unchanged — SHOM authoritative |
| `roundtrip preserves all fields` | `SerializerTest` | **Transparent** — Protobuf roundtrip |
| `roundtrip with empty zones` | `SerializerTest` | **Transparent** — Protobuf roundtrip |
| `roundtrip with null vesselSizeRestriction` | `SerializerTest` | **Transparent** — Protobuf roundtrip |
| `roundtrip with full vesselSizeRestriction` | `SerializerTest` | **Transparent** — Protobuf roundtrip |
| `enum values survive roundtrip` | `SerializerTest` | **Transparent** — Protobuf roundtrip |
| `prebakeRegulatedZones` | `PrebakeTest` | Full end-to-end: fetch → aggregate → serialize → write .bin |

### Manual verification after implementation:

1. Run `RegulatedZoneSerializerTest` → all roundtrip tests should pass
2. Run `RegulationAggregatorTest` → all dedup tests should pass with updated expectations
3. Run `tools/bake-regulated-zones.bat` → generates new `nice-frejus.bin` (Protobuf format, smaller size)
4. Verify the output `.bin` size is smaller than before (Protobuf vs JSON)
5. Run the app and visually verify Cap d'Antibes and Îles de Lérins zones render as proper SHOM polygons, not octagons

---

## Validation Checklist

These are the **explicit pass/fail checks** to run before deployment, in order:

### Phase A — Unit Tests (dev machine, no network required)

```bash
tools\test-regulated-zones.bat
```

This runs all `RegulatedZone*`, `Regulation*`, and `ShomRegulation*` test classes. After the fix:

| Test | Expected result | What it validates |
|------|----------------|-------------------|
| `RegulationAggregatorTest` — all tests | ✅ All pass | New overlap dedup logic works, old centroid-distance + type gate are gone |
| `RegulatedZoneSerializerTest` — all tests | ✅ All pass | Protobuf roundtrip works for all fields, enums, nulls, edge cases |
| `ShomRegulationClientTest` (if it exists) | ✅ Pass | RDP simplification doesn't break SHOM WFS parsing |

### Phase B — Prebake Integration Test (dev machine, requires network)

```bash
tools\bake-regulated-zones.bat
```

| Check | How | What it validates |
|-------|-----|-------------------|
| Prebake exits with 0 | Check terminal exit code | Full pipeline: fetch SHOM WFS → apply RDP → collect seeds → overlap-dedup → Protobuf serialize → write `.bin` |
| `.bin` file size is **smaller** than before | Compare old vs new `data/app-assets/regulated-zones/nice-frejus.bin` | Protobuf is more compact than JSON (expected: ~40-60% smaller) |
| `.bin` file is parsable | `RegulatedZoneSerializer.deserialize()` on the output | The Protobuf bytes are valid and deserializable |
| No seed-origin zones in the output | Check prebake console output for zone count by source | The overlap dedup correctly discarded Cap d'Antibes, Îles de Lérins, and Baie des Anges seeds because their centroids fall inside SHOM zone bboxes |

### Phase C — Rendering Validation (APK on device)

1. **Build & install APK** via `apk-build.bat` + `apk-deploy.bat`
2. **Navigate to Cap d'Antibes** (43.56, 7.13) or **Îles de Lérins** (43.523, 7.045)
3. **Zoom in** past the regulated zone zoom gate
4. **Verify:** The Cap d'Antibes speed zone renders as its true SHOM WFS GeoJSON shape (irregular coastal polygon), **not** as a regular octagon
5. **Verify:** The Îles de Lérins zone renders as its true SHOM polygon, not an octagon
6. **Verify:** The Baie des Anges zone (Nice harbor) renders as a proper SHOM polygon, not an octagon
7. **Verify:** Speed limit labels and zone type colors are correct (SHOM attributes preserved)

### Phase D — Regression Checks

| Check | How |
|-------|-----|
| Regulated zones toggle off/on | Toggle the zone layer button — zones should disappear/reappear cleanly |
| Smooth zoom across zoom gate | Zoom past `REGULATED_ZONE_MIN_ZOOM` — zones should fade in without flicker |
| No ANRs or crashes | Pan around the map for 30+ seconds with regulated zones visible |
| GPS vessel filter still works | With a vessel length set, zones tagged `vesselSizeRestriction` should still filter correctly |

---

## Execution Order

1. [`RegulationSeeds.kt`](../app/src/main/java/ykws/android/maro/data/regulation/RegulationSeeds.kt) — Remove Port-Cros stub
2. [`RegulationAggregator.kt`](../app/src/main/java/ykws/android/maro/data/regulation/RegulationAggregator.kt) — Rewrite dedup (constant, collect, deduplicate, helpers, cleanup)
3. [`ShomRegulationClient.kt`](../app/src/main/java/ykws/android/maro/data/regulation/ShomRegulationClient.kt) — Add RDP in parseRing
4. [`RegulatedZoneSerializer.kt`](../app/src/main/java/ykws/android/maro/data/regulation/RegulatedZoneSerializer.kt) — Switch to Protobuf
5. [`RegulationAggregatorTest.kt`](../app/src/test/java/ykws/android/maro/data/regulation/RegulationAggregatorTest.kt) — Update tests
6. Run all unit tests (Phase A)
7. Run prebake to regenerate `.bin` asset (Phase B)
8. Visual verification on device (Phase C)
9. Regression checks (Phase D)

