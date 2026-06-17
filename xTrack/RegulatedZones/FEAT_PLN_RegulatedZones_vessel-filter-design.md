<!-- scope: feature -->
# Regulated Zones — Vessel Applicability Filter Design

## Motivation

The user's boat is **< 6 meters**. Many maritime regulations published by SHOM have
**vessel size thresholds** — e.g. "speed limit applies to vessels > 25m" or
"anchoring prohibited for vessels > 20m". Zones that don't apply to small boats
should be filtered out at runtime so the map shows only relevant regulations.

## Design

### 1. Data Model Addition

Add a new `VesselSizeRestriction` data class to [`RegulatedZone.kt`](app/src/main/java/ykws/android/maro/data/regulation/RegulatedZone.kt):

```kotlin
@Serializable
data class VesselSizeRestriction(
    /** Minimum vessel length in metres that this regulation applies to (null = no minimum). */
    val minLengthM: Double? = null,
    /** Maximum vessel length in metres that this regulation applies to (null = no maximum). */
    val maxLengthM: Double? = null
)
```

**Semantics:**
- `null` on both sides = applies to **all vessels** (default)
- `minLengthM = 25.0`, `maxLengthM = null` = applies only to vessels **≥ 25 m**
- `minLengthM = null`, `maxLengthM = 20.0` = applies only to vessels **≤ 20 m**
- `minLengthM = 10.0`, `maxLengthM = 25.0` = applies only to vessels **between 10 m and 25 m**

Add to `RegulatedZone` as field `@ProtoNumber(9)`:

```kotlin
@ProtoNumber(9) val vesselSizeRestriction: VesselSizeRestriction? = null
```

`null` means "no restriction data available" (default) — treated as "applies to all".

### 2. SHOM WFS Client Changes

Update [`ShomRegulationClient.kt`](app/src/main/java/ykws/android/maro/data/regulation/ShomRegulationClient.kt) to parse additional GeoJSON properties
from the SHOM response. Candidate property names (to be confirmed against actual
SHOM schema):

| GeoJSON Property | Mapped Field |
|---|---|
| `longueur_hors_tout_mini` or `longueur_mini` | `VesselSizeRestriction.minLengthM` |
| `longueur_hors_tout_maxi` or `longueur_maxi` | `VesselSizeRestriction.maxLengthM` |

**Parsing logic** in `parseFeature`:
```kotlin
val minLength = properties["longueur_hors_tout_mini"]?.jsonPrimitive?.doubleOrNull
    ?: properties["longueur_mini"]?.jsonPrimitive?.doubleOrNull
val maxLength = properties["longueur_hors_tout_maxi"]?.jsonPrimitive?.doubleOrNull
    ?: properties["longueur_maxi"]?.jsonPrimitive?.doubleOrNull
val vesselRestriction = if (minLength != null || maxLength != null) {
    VesselSizeRestriction(minLengthM = minLength, maxLengthM = maxLength)
} else null
```

### 3. Seed Zone Defaults

Seed zones (in [`RegulationSeeds.kt`](app/src/main/java/ykws/android/maro/data/regulation/RegulationSeeds.kt))
don't have vessel size data from a WFS — they default to `vesselSizeRestriction = null`
(apply to all), which is the safe assumption for hardcoded fallbacks.

### 4. Runtime Filtering

At app runtime (map display layer), filter zones using the user's boat length:

```kotlin
fun RegulatedZone.appliesTo(vesselLengthM: Double): Boolean {
    val r = vesselSizeRestriction ?: return true // no restriction = applies
    if (r.minLengthM != null && vesselLengthM < r.minLengthM) return false
    if (r.maxLengthM != null && vesselLengthM > r.maxLengthM) return false
    return true
}
```

The user's boat length should be stored in SharedPreferences (Settings UI), defaulting
to 6.0 m.

### 5. ProtoNumber Field Numbers (Updated)

Updated `RegulatedZone` ProtoNumber assignment:

| # | Field | Type |
|---|---|---|
| 1 | outerRing | `List<LatLng>` |
| 2 | holes | `List<List<LatLng>>` |
| 3 | zoneType | `RegulatedZoneType` (enum → int) |
| 4 | speedLimitKn | `Double?` |
| 5 | name | `String` |
| 6 | source | `String` |
| 7 | sourceRef | `String` |
| 8 | description | `String` |
| **9** | **vesselSizeRestriction** | **`VesselSizeRestriction?`** |

New `VesselSizeRestriction` ProtoNumber assignment:

| # | Field | Type |
|---|---|---|
| 1 | minLengthM | `Double?` |
| 2 | maxLengthM | `Double?` |

## Updated Todo List

1. [ ] **Add `VesselSizeRestriction` data class** to `RegulatedZone.kt` with `@ProtoNumber` annotations
2. [ ] **Add `vesselSizeRestriction` field** to `RegulatedZone` as `@ProtoNumber(9)`
3. [ ] **Update SHOM client** to parse vessel size properties from GeoJSON
4. [ ] **Update seed zones** (default to null — applies to all)
5. [ ] **Add runtime filter method** `RegulatedZone.appliesTo(vesselLengthM)` (in display-layer subfeature)
6. [ ] **Add boat length setting** in Settings UI (in display-layer subfeature)

## How to Run

### Prebake (fetch + serialize)
```cmd
gradlew :app:testDebugUnitTest --tests "*RegulatedZonePrebakeTest*" -Dmaro.prebake=true
```

### Bake (full pipeline via batch)
```cmd
tools\bake-regulated-zones.bat
```

### Build APK
```cmd
gradlew assembleDebug
```
Or use `apk-bake.bat` with "regulated-zones" target selected.

