# Regulated Zones Feature — Practical Testing Guide

## Overview

The Regulated Zones feature fetches maritime regulation zones from **SHOM WFS API**, aggregates them with hardcoded seed zones, and serializes the result as a compact JSON `.bin` asset bundled into the APK.

It follows the same **prebake pipeline** as Coastline and DepthMapping: all data gathering runs at build time on the computer, never on-device.

```
SHOM WFS (GeoJSON) ──┐
                      ├──▶ ShomRegulationClient ──┐
Seed zones (SEED) ───┘                           │
                                                  ├──▶ RegulationAggregator ──▶ RegulatedZoneSerializer ──▶ .bin
                                                       (merge + dedup)            (kotlinx JSON)
```

## File Layout

```
app/src/main/java/ykws/android/maro/data/regulation/
├── RegulatedZone.kt              # Data model
├── ShomRegulationClient.kt       # WFS GetFeature client
├── RegulationSeeds.kt            # 4 hardcoded fallback zones
├── RegulationAggregator.kt       # Merge + dedup + validate
└── RegulatedZoneSerializer.kt    # JSON ↔ bytes round-trip

app/src/test/java/ykws/android/maro/data/regulation/
├── RegulatedZonePrebakeTest.kt           # Prebake (network, gated)
├── RegulatedZoneSerializerTest.kt        # Serialization round-trip
├── RegulatedZoneVesselFilterTest.kt      # Vessel size filtering
├── RegulationAggregatorTest.kt           # Aggregation/dedup logic
├── ShomRegulationClientParsingTest.kt    # GeoJSON parsing (offline)
└── ShomRegulationConnectivityTest.kt     # Live WFS probe (network)

tools/bake-regulated-zones.bat            # Bake script
```

---

## Step-by-Step Testing

### 1. Data Model & Serialization

**What it validates:** Creating a `RegulatedZoneSet`, serializing to JSON bytes, deserializing back, and asserting all fields survive.

**Test class:** [`RegulatedZoneSerializerTest.kt`](app/src/test/java/ykws/android/maro/data/regulation/RegulatedZoneSerializerTest.kt)

**Run:**
```cmd
gradlew testDebugUnitTest --tests "*RegulatedZoneSerializerTest*"
```

**Expected:** All tests pass (round-trip preserves zone types, speed limits, metadata, polygon rings).

**Manual inspection:**
```kotlin
// Quick sanity in any test or main:
val set = RegulatedZoneSet(
    zones = listOf(
        RegulatedZone(
            outerRing = listOf(LatLng(43.5, 7.1), LatLng(43.6, 7.2)),
            zoneType = RegulatedZoneType.SPEED_LIMIT,
            speedLimitKn = 10.0,
            name = "Test Zone"
        )
    ),
    metadata = RegulationMetadata(fetchTimestampMs = System.currentTimeMillis(), sourceCount = 1, totalZones = 1)
)
val bytes = RegulatedZoneSerializer.serialize(set)
val restored = RegulatedZoneSerializer.deserialize(bytes)
println("Bytes: ${bytes.size}, Restored: ${restored.zones.size} zones")
```

---

### 2. SHOM WFS Client — Offline GeoJSON Parsing

**What it validates:** The client correctly parses GeoJSON FeatureCollection responses — polygon geometry (outer ring + holes), `type_reglementation` → `RegulatedZoneType`, `vitesse_max` → `speedLimitKn`, `nom` → `name`.

**Test class:** [`ShomRegulationClientParsingTest.kt`](app/src/test/java/ykws/android/maro/data/regulation/ShomRegulationClientParsingTest.kt)

**Run:**
```cmd
gradlew testDebugUnitTest --tests "*ShomRegulationClientParsingTest*"
```

**Expected:** Tests pass with hardcoded sample GeoJSON snippets covering:
- `"Polygon"` geometry with outer ring
- `"MultiPolygon"` geometry with holes
- French property names: `type_reglementation: "vitesse"` → `SPEED_LIMIT`, `type_reglementation: "mouillage"` → `ANCHORING_PROHIBITED`, etc.
- `vitesse_max: 10.0` → `speedLimitKn = 10.0`
- Missing/null properties → defaults used

---

### 3. SHOM WFS Client — Live Connectivity Probe

**What it validates:** The WFS client can reach the SHOM server, probe `GetCapabilities`, and attempt `GetFeature` on candidate layers — without crashing on failure.

**Test class:** [`ShomRegulationConnectivityTest.kt`](app/src/test/java/ykws/android/maro/data/regulation/ShomRegulationConnectivityTest.kt)

**Run:**
```cmd
gradlew testDebugUnitTest --tests "*ShomRegulationConnectivityTest*"
```

**Expected:**
- If network is available and SHOM responds: tests pass, console shows discovered layer names and zone counts.
- If SHOM is unreachable (no network, URL changed): tests **still pass** (best-effort — empty list), no crash.
- This test is idempotent and safe — it never writes files.

**To see debug output**, add `--info`:
```cmd
gradlew testDebugUnitTest --tests "*ShomRegulationConnectivityTest*" --info
```

---

### 4. Aggregation & Dedup

**What it validates:** `RegulationAggregator` correctly:
- Merges SHOM zones + seed zones
- Deduplicates by centroid proximity (< 25 m) + same `zoneType`
- SHOM zones take priority over SEED zones (SHOM keeps its `source`, seed is dropped)
- Validates against Nice–Fréjus bbox (6.73°E–7.31°E, 43.35°N–43.73°N) — rejects out-of-bbox zones
- Sorts by zoneType ordinal then area descending
- Builds correct `RegulationMetadata`

**Test class:** [`RegulationAggregatorTest.kt`](app/src/test/java/ykws/android/maro/data/regulation/RegulationAggregatorTest.kt)

**Run:**
```cmd
gradlew testDebugUnitTest --tests "*RegulationAggregatorTest*"
```

**Expected scenarios tested:**
| Scenario | SHOM zones | SEED zones | Expected result |
|----------|-----------|-----------|----------------|
| No overlap | 2 distinct zones | 2 distinct zones | 4 zones (all kept) |
| Close proximity + same type | 1 zone at (43.560, 7.130) | 1 zone 10 m away | 1 zone (seed deduped) |
| Close proximity + different type | 1 `SPEED_LIMIT` | 1 `ANCHORING_PROHIBITED` | 2 zones (no dedup) |
| Out-of-bbox | 1 inside bbox, 1 outside | — | 1 zone (1 rejected) |

---

### 5. Seed Zones

**No test needed** — [`RegulationSeeds.kt`](app/src/main/java/ykws/android/maro/data/regulation/RegulationSeeds.kt) is a simple data object. But you can verify manually:

```kotlin
val seeds = RegulationSeeds.getSeeds()
seeds.forEach { println("${it.name}: ${it.zoneType}, speedLimit=${it.speedLimitKn}") }
// Expected output:
//   Cap d'Antibes — 10 nœuds: SPEED_LIMIT, speedLimit=10.0
//   Îles de Lérins — Circulation réglementée: NAVIGATION_RESTRICTION, speedLimit=null
//   Baie des Anges — 10 nœuds: SPEED_LIMIT, speedLimit=10.0
//   Parc National de Port-Cros (référence): ENVIRONMENTAL, speedLimit=null
```

The polygon ring generation uses the same local planar projection as [`HazardRings.buildRing()`](app/src/main/java/ykws/android/maro/data/coastline/HazardRings.kt:48).

---

### 6. Vessel Size Filtering

**What it validates:** The `appliesTo(boatLengthM)` logic on `RegulatedZone` correctly includes/excludes zones based on vessel size restrictions.

**Test class:** [`RegulatedZoneVesselFilterTest.kt`](app/src/test/java/ykws/android/maro/data/regulation/RegulatedZoneVesselFilterTest.kt)

**Run:**
```cmd
gradlew testDebugUnitTest --tests "*RegulatedZoneVesselFilterTest*"
```

**Expected:**
- Zone with no restriction → applies to all vessels
- Zone with `speedLimitKn` → applies to all vessels (speed limits are universal)
- (Note: vessel size filtering is a forward-looking API — current `RegulatedZone` model doesn't have `VesselSizeRestriction`, the test validates the simple `appliesTo` contract)

---

### 7. End-to-End Prebake (Full Pipeline)

**What it validates:** The complete pipeline — SHOM WFS fetch + seeds + aggregation + JSON serialization → `.bin` file.

**Test class:** [`RegulatedZonePrebakeTest.kt`](app/src/test/java/ykws/android/maro/data/regulation/RegulatedZonePrebakeTest.kt)

**Prerequisite:** Network access (SHOM WFS must be reachable).

**Run:**
```cmd
gradlew testDebugUnitTest --tests "*RegulatedZonePrebakeTest*" -Dmaro.prebake=true
```

**Expected console output** (approximate):
```
[prebake] Fetching SHOM regulation zones for nice-frejus...
[prebake] SHOM returned N zones
[prebake] M seed zones
[prebake] K zones after dedup (Z sources)
[prebake] Wrote X bytes -> data\app-assets\regulated-zones\nice-frejus.bin
[prebake] Breakdown by zone type:
         SPEED_LIMIT             ...
         ANCHORING_PROHIBITED    ...
         ACCESS_PROHIBITED       ...
         ENVIRONMENTAL           ...
         ...
[prebake] Done.
```

**If SHOM is unreachable:** The test still completes with just the seed zones:
```
[prebake] SHOM returned 0 zones (WFS unreachable)
[prebake] 4 seed zones
[prebake] Wrote Y bytes -> data\app-assets\regulated-zones\nice-frejus.bin
[prebake] Done (seeds only — SHOM empty).
```

**Output file:** `data/app-assets/regulated-zones/nice-frejus.bin` (gitignored, auto-packaged into APK by Gradle's `assets.srcDir`).

---

### 8. Bake Script

**What it validates:** The `tools/bake-regulated-zones.bat` script runs the prebake test correctly.

**Run (from repo root):**
```cmd
tools\bake-regulated-zones.bat
```

**Expected:**
```
[bake-regulated-zones] Fetching SHOM regulation zones + aggregating...
... Gradle output ...
BUILD SUCCESSFUL in Xs
```

**Shortcut via apk-bake.bat:**
```cmd
apk-bake.bat regulatedzones          # non-interactive
apk-bake.bat --fresh regulatedzones  # force re-fetch
apk-bake.bat all                      # bake everything (incl. regulated-zones)
```

Interactive mode (no arguments):
```cmd
apk-bake.bat
# Shows status: [present]/[MISSING] for regulated-zones .bin
# Prompts: "Bake regulated zones .bin (SHOM WFS, network)? [y/N]"
```

---

### 9. Full Bake → Build → Deploy Cycle

Once the prebake succeeds, build and deploy the APK:

```cmd
apk-build.bat        # packages data/app-assets/regulated-zones/*.bin into APK
apk-deploy.bat       # install on device and launch
```

---

## Run All Regulated-Zones Tests

```cmd
gradlew testDebugUnitTest --tests "*RegulatedZone*" --tests "*Regulation*" --tests "*ShomRegulation*"
```

This runs all 6 test classes without the prebake gate (network-independent tests only). Add `-Dmaro.prebake=true` to include the live prebake test.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| Prebake test skips (no output) | Forgot `-Dmaro.prebake=true` | Add the JVM property |
| SHOM returns 0 zones | WFS endpoint URL changed or unreachable | Run `ShomRegulationConnectivityTest` with `--info` to see HTTP response; check `CANDIDATE_BASE_URLS` in [`ShomRegulationClient.kt`](app/src/main/java/ykws/android/maro/data/regulation/ShomRegulationClient.kt:122) |
| Seeds only (no SHOM zones) | Network down or SHOM changed API | Acceptable — seeds provide baseline; update `CANDIDATE_BASE_URLS` when URL is confirmed |
| Serialization fails | Data model changed without updating serializer | Check `@Serializable` annotations on all classes in [`RegulatedZone.kt`](app/src/main/java/ykws/android/maro/data/regulation/RegulatedZone.kt) |
| `.bin` not in APK | `data/app-assets/` not baked yet | Run `apk-bake.bat regulatedzones` first, then `apk-build.bat` |
