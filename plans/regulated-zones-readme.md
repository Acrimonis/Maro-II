# Regulated Zones Feature — README

## Overview

The Regulated Zones feature fetches maritime regulation zones (speed limits, anchoring
prohibitions, access restrictions, environmental zones) from the **SHOM WFS API**,
aggregates them with hardcoded seed zones, deduplicates, and serializes the result
as a compact Protobuf `.bin` asset bundled into the APK.

At runtime, the map overlay filters zones by the user's vessel length (< 6 m by default)
so only applicable regulations are displayed.

---

## Architecture (Step-by-Step)

```
SHOM WFS (GeoJSON) ──┐
                      ├──▶ ShomRegulationClient ──┐
Seed zones (hardcoded)┘                           │
                                                  ├──▶ RegulationAggregator ──▶ RegulatedZoneSerializer ──▶ .bin
                                                  │         (dedup)                (Protobuf)
                                                  └──▶ VesselSizeRestriction ◀── (from GeoJSON properties)
```

### Components

| Component | File | Role |
|-----------|------|------|
| Data model | [`RegulatedZone.kt`](../app/src/main/java/ykws/android/maro/data/regulation/RegulatedZone.kt) | Core types: `RegulatedZone`, `RegulatedZoneSet`, `RegulationMetadata`, `RegulatedZoneType`, `VesselSizeRestriction` |
| WFS client | [`ShomRegulationClient.kt`](../app/src/main/java/ykws/android/maro/data/regulation/ShomRegulationClient.kt) | Fetches GeoJSON from SHOM WFS, parses polygons + properties |
| Seed fallback | [`RegulationSeeds.kt`](../app/src/main/java/ykws/android/maro/data/regulation/RegulationSeeds.kt) | Hardcoded known zones (Cap d'Antibes, Îles de Lérins, Baie des Anges) |
| Aggregator | [`RegulationAggregator.kt`](../app/src/main/java/ykws/android/maro/data/regulation/RegulationAggregator.kt) | Merges SHOM + seeds, deduplicates by centroid proximity (<100 m) + zone type |
| Serializer | [`RegulatedZoneSerializer.kt`](../app/src/main/java/ykws/android/maro/data/regulation/RegulatedZoneSerializer.kt) | Protobuf (`ProtoBuf { encodeDefaults = false }`) encode/decode |
| Prebake test | [`RegulatedZonePrebakeTest.kt`](../app/src/test/java/ykws/android/maro/data/regulation/RegulatedZonePrebakeTest.kt) | Build-time test — fetches, aggregates, serializes |

---

## Tests per Step

### 1. Data Model + Protobuf Roundtrip

**What it validates:** Creating a `RegulatedZoneSet`, serializing to bytes, deserializing
back, and asserting all fields survive the roundtrip — including `VesselSizeRestriction`.

**Test class:** *(to be created)* `RegulatedZoneSerializerTest.kt`

```kotlin
@Test
fun `serialize-deserialize roundtrip preserves all fields`() {
    val original = RegulatedZoneSet(
        zones = listOf(
            RegulatedZone(
                outerRing = listOf(LatLng(43.5, 7.1), LatLng(43.6, 7.2)),
                zoneType = RegulatedZoneType.SPEED_LIMIT,
                speedLimitKn = 10.0,
                name = "Test Zone",
                source = "SHOM",
                sourceRef = "REF-001",
                vesselSizeRestriction = VesselSizeRestriction(minLengthM = 25.0)
            )
        ),
        metadata = RegulationMetadata(
            fetchTimestampMs = 1000L,
            sourceCount = 1,
            totalZones = 1
        )
    )
    val bytes = RegulatedZoneSerializer.serialize(original)
    val restored = RegulatedZoneSerializer.deserialize(bytes)
    assertEquals(original, restored)
}
```

**How to demonstrate:**
```cmd
gradlew testDebugUnitTest --tests "*RegulatedZoneSerializerTest*"
```

### 2. Vessel Size Filtering Logic

**What it validates:** The `appliesTo(boatLengthM)` filter correctly includes/excludes
zones based on `VesselSizeRestriction`.

**Test class:** *(to be created)* `RegulatedZoneVesselFilterTest.kt`

```kotlin
@Test
fun `zone with minLengthM 25 excludes 6m boat`() {
    val zone = RegulatedZone(
        outerRing = listOf(LatLng(43.5, 7.1)),
        zoneType = RegulatedZoneType.SPEED_LIMIT,
        vesselSizeRestriction = VesselSizeRestriction(minLengthM = 25.0)
    )
    assertFalse(zone.appliesTo(6.0))  // 6m boat excluded
    assertTrue(zone.appliesTo(30.0))  // 30m boat included
}
@Test
fun `zone with no restriction applies to all`() {
    val zone = RegulatedZone(
        outerRing = listOf(LatLng(43.5, 7.1)),
        zoneType = RegulatedZoneType.SPEED_LIMIT,
        vesselSizeRestriction = null
    )
    assertTrue(zone.appliesTo(6.0))
    assertTrue(zone.appliesTo(50.0))
}
```

**How to demonstrate:**
```cmd
gradlew testDebugUnitTest --tests "*RegulatedZoneVesselFilterTest*"
```

### 3. SHOM WFS Client — Parsing Vessel Size Properties

**What it validates:** The client correctly reads `longueur_hors_tout_mini` and
`longueur_hors_tout_maxi` from simulated GeoJSON and populates `VesselSizeRestriction`.

**Test class:** *(to be created)* `ShomRegulationClientParsingTest.kt`

```kotlin
@Test
fun `parseFeature reads vessel size from properties`() {
    val geoJson = """
    {
      "type": "Feature",
      "geometry": {"type": "Polygon", "coordinates": [[[7.1,43.5],[7.2,43.6],[7.3,43.5],[7.1,43.5]]]},
      "properties": {
        "type_reglementation": "vitesse",
        "longueur_hors_tout_mini": 20.0,
        "longueur_hors_tout_maxi": 50.0
      }
    }
    """.trimIndent()
    // ... parse, assert vesselSizeRestriction.minLengthM == 20.0, maxLengthM == 50.0
}
```

**How to demonstrate:**
```cmd
gradlew testDebugUnitTest --tests "*ShomRegulationClientParsingTest*"
```

### 4. Aggregation Dedup

**What it validates:** `RegulationAggregator` correctly deduplicates zones by
centroid proximity (<100 m) + same `zoneType`, and SHOM zones take priority over SEED.

**Test class:** *(to be created)* `RegulationAggregatorTest.kt`

```kotlin
@Test
fun `dedup removes seed zone within 100m of SHOM zone with same type`() {
    val shom = RegulatedZone(
        outerRing = listOf(LatLng(43.56, 7.13), LatLng(43.57, 7.14), LatLng(43.56, 7.15), LatLng(43.56, 7.13)),
        zoneType = RegulatedZoneType.SPEED_LIMIT, source = "SHOM", name = "SHOM zone"
    )
    val seed = RegulatedZone(
        outerRing = listOf(LatLng(43.561, 7.131), LatLng(43.571, 7.141), LatLng(43.561, 7.151), LatLng(43.561, 7.131)),
        zoneType = RegulatedZoneType.SPEED_LIMIT, source = "SEED", name = "Seed zone"
    )
    val result = RegulationAggregator.aggregate(listOf(shom, seed))
    assertEquals(1, result.metadata.totalZones)  // only SHOM survives
    assertEquals("SHOM zone", result.zones.single().name)
}
```

**How to demonstrate:**
```cmd
gradlew testDebugUnitTest --tests "*RegulationAggregatorTest*"
```

### 5. End-to-End Prebake

**What it validates:** Full pipeline — SHOM WFS fetch + seeds + aggregation +
serialization to `.bin` file.

**Test class:** [`RegulatedZonePrebakeTest`](../app/src/test/java/ykws/android/maro/data/regulation/RegulatedZonePrebakeTest.kt)
(gated by `-Dmaro.prebake=true`).

**How to demonstrate:**
```cmd
gradlew testDebugUnitTest --tests "*RegulatedZonePrebakeTest*" -Dmaro.prebake=true
```

Expected console output:
```
[prebake] Fetching SHOM regulation zones for nice-frejus...
[prebake] SHOM returned 42 zones
[prebake] 4 seed zones
[prebake] 38 zones after dedup (2 sources)
[prebake] Wrote 18432 bytes -> data\app-assets\regulated-zones\nice-frejus.bin
[prebake] Breakdown by zone type:
         SPEED_LIMIT             15
         ANCHORING_PROHIBITED    12
         ACCESS_PROHIBITED       8
         ENVIRONMENTAL           3
[prebake] Done.
```

---

## How to Demonstrate Each Step

| Step | Command | Expected Outcome |
|------|---------|-----------------|
| **1. Data model roundtrip** | `gradlew testDebugUnitTest --tests "*RegulatedZoneSerializerTest*"` | Test passes (all fields survive Protobuf) |
| **2. Vessel filter** | `gradlew testDebugUnitTest --tests "*RegulatedZoneVesselFilterTest*"` | Test passes (6m boat correctly excluded/included) |
| **3. SHOM parsing** | `gradlew testDebugUnitTest --tests "*ShomRegulationClientParsingTest*"` | Test passes (vessel size parsed from GeoJSON) |
| **4. Aggregation dedup** | `gradlew testDebugUnitTest --tests "*RegulationAggregatorTest*"` | Test passes (seed deduplicated, SHOM kept) |
| **5. SHOM connectivity** | `gradlew testDebugUnitTest --tests "*ShomRegulationConnectivityTest*"` | Console shows `[connectivity] SHOM WFS GetCapabilities: OK` + zone counts per typeName |
| **6. End-to-end prebake** | `gradlew testDebugUnitTest --tests "*RegulatedZonePrebakeTest*" -Dmaro.prebake=true` | `.bin` file written to `data/app-assets/regulated-zones/nice-frejus.bin`, summary printed |
| **Full bake** | `tools\bake-regulated-zones.bat` | Same as #6, via batch script |
| **Bake + build APK** | Run `apk-bake.bat` and select [R]egulated zones, then build | `.bin` bundled into APK under `assets/regulated-zones/` |
| **Verify in APK** | `jar tf app/build/outputs/apk/debug/app-debug.apx \| findstr regulated` | `regulated-zones/nice-frejus.bin` present in APK |

---

## Test Coverage Summary

| Test Class | Network? | What It Validates |
|-----------|----------|-------------------|
| `RegulatedZoneSerializerTest` | No | Protobuf roundtrip, null handling, enum serialization |
| `RegulatedZoneVesselFilterTest` | No | `appliesTo()` logic, edge cases |
| `ShomRegulationClientParsingTest` | No (mock) | GeoJSON parsing, vessel size extraction, error resilience |
| `RegulationAggregatorTest` | No | Dedup by proximity, source priority, empty input, metadata |
| **`ShomRegulationConnectivityTest`** | **Yes** | **Hits real SHOM WFS — validates endpoint is alive + returns valid GeoJSON** |
| `RegulatedZonePrebakeTest` | Yes | End-to-end prebake pipeline (gated by `-Dmaro.prebake=true`) |

## Run Instructions (Quick Reference)

```cmd
REM All tests (1-4 no network; 5 needs network; 6 needs network + gate)
gradlew testDebugUnitTest --tests "*Regulation*"

REM Connectivity test only (hits real SHOM WFS)
gradlew testDebugUnitTest --tests "*ShomRegulationConnectivityTest*"

REM Prebake only (needs network, writes .bin)
gradlew testDebugUnitTest --tests "*RegulatedZonePrebakeTest*" -Dmaro.prebake=true

REM Full bake via script
tools\bake-regulated-zones.bat

REM Interactive bake menu
apk-bake.bat
```
