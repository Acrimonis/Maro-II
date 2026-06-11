# RegulatedZones — Hydration Snapshot

**Baked:** 2026-06-11 18:47 UTC

## State

- **Status:** active
- **Active subfeature:** data-lookup (complete)
- **Remaining:** aggregation/normalization, prebake test, bake script, apk-bake integration

## What was built this session

1. **Data model** — `RegulatedZone`, `RegulatedZoneSet`, `RegulationMetadata`, `RegulatedZoneType`, `VesselSizeRestriction`, `RegulationSeed` — all with `@Serializable` and `@ProtoNumber` annotations.
2. **`RegulatedZoneSerializer`** — `object` using `ProtoBuf { encodeDefaults = false }` for compact binary serialization.
3. **`ShomRegulationClient`** — WFS GetFeature client for SHOM regulation layers, parses GeoJSON including `longueur_hors_tout_mini`/`maxi` for vessel size restrictions.
4. **`LatLng` updated** — added `@ProtoNumber` annotations for Protobuf compatibility.
5. **`kotlinx-serialization-protobuf` dependency** — added to `libs.versions.toml` and `app/build.gradle.kts`.

## Key Files

- `app/src/main/java/ykws/android/maro/data/model/LatLng.kt`
- `app/src/main/java/ykws/android/maro/data/regulation/RegulatedZone.kt`
- `app/src/main/java/ykws/android/maro/data/regulation/ShomRegulationClient.kt`
- `app/src/main/java/ykws/android/maro/data/regulation/RegulatedZoneSerializer.kt`
- `app/src/main/java/ykws/android/maro/data/regulation/RegulationSeeds.kt`
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`

## Next Step

Step 7 — Build `RegulatedZonePrebakeTest.kt` (Gradle test gated by `-Dmaro.prebake=true`) and `tools/bake-regulated-zones.bat`.
