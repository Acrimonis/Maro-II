# Regulated Zones — Multi-Source Data Normalization Plan

## 1. Data Format

### Proposed `RegulationClassification` — Sealed Class for Provenance

```kotlin
@Serializable
sealed class RegulationClassification {
    /** SHOM S-101 restriction code from INSPIRE endpoint. */
    @Serializable data class S101(val code: Int) : RegulationClassification()
    /** SHOM S-57 CATREA from auth endpoint. */
    @Serializable data class Catrea(val code: Int) : RegulationClassification()
    /** SHOM S-57 RESTRN from auth endpoint. */
    @Serializable data class Restrn(val code: Int) : RegulationClassification()
    /** INPN Marine Protected Area designation. */
    @Serializable data class InpnMpa(val type: String, val mnhnId: String?) : RegulationClassification()
    /** Hardcoded seed — no source classification. */
    @Serializable data object Seed : RegulationClassification()
}
```

### Proposed `SpeedSource` — Extraction Method Enum

```kotlin
enum class SpeedSource {
    STRUCTURED_FIELD,     // vitesse_max GeoJSON property
    INFORM_TEXT,          // Parsed from INFORM/inform_fr string
    TXTDSC_MAP,           // Cross-referenced via TXTDSC decree name
    DEFAULT_RULE,         // 5 kn coastal baseline
    NONE                  // No speed limit
}
```

### Updated `RegulatedZone` — 3 New Fields

```kotlin
@Serializable
data class RegulatedZone(
    // Existing fields (1–10, unchanged)
    @ProtoNumber(1) val outerRing: List<LatLng>,
    @ProtoNumber(2) val holes: List<List<LatLng>> = emptyList(),
    @ProtoNumber(3) val zoneType: RegulatedZoneType,
    @ProtoNumber(4) val speedLimitKn: Double? = null,
    @ProtoNumber(5) val name: String = "",
    @ProtoNumber(6) val source: String = "SHOM",
    @ProtoNumber(7) val sourceRef: String = "",
    @ProtoNumber(8) val description: String = "",
    @ProtoNumber(9) val vesselSizeRestriction: VesselSizeRestriction? = null,
    @ProtoNumber(10) val restrictionCode: Int? = null,

    // New fields (11–13)
    @ProtoNumber(11) val classification: RegulationClassification? = null,
    @ProtoNumber(12) val speedSource: SpeedSource? = null,
    @ProtoNumber(13) val legalDecreeRef: String? = null,
)
```

All 3 new fields are nullable with `null` defaults → **backward compatible** with existing `.bin` assets.

---

## 2. Final Icon Mapping — All 8 Categories

| Display Key | Icon | Bg Colour | Alpha | Strike | Maps To | Trigger |
|-------------|------|----------|-------|--------|---------|---------|
| `SPEED_LIMIT` | **5/10** | Red | 75% | No | ⚠️ Action | CATREA=27, restrn=1, vitesse_max, INFORM, TXTDSC, keyword `vitesse`/`noeud`/`nds`/`knot` |
| `NO_ANCHOR` | ⚓ | Blue | 75% | **Yes** | 🛑 Prohibition | RESTRN=1/2, restrn=7, keyword `mouillage`/`ancrage`/`anchor`/`posidonie`/`herbier` |
| `NO_ACCESS` | 🚤 | Blue | 75% | **Yes** | 🛑 Prohibition | RESTRN=7/8, restrn=10/11/12, INPN biotope, keyword `interdit`/`prohibé`/`accès` |
| `MOORING` | 🛥️ | Blue | 75% | No | ✅ Permissive | restrn=18, keyword `mooring`/`amarrage`/`corps mort` |
| `NO_DIVING` | 🤿 | Blue | 75% | **Yes** | 🛑 Prohibition | RESTRN=10 (code check), keyword `plongée`/`diving`/`subaquatique` |
| `SEAPLANE` | ✈️ | Grey | 50% | No | ℹ️ Info | keyword `seaplane`/`hydravion` |
| `ENVIRONMENTAL` | 🌿 | Blue | 50% | No | ℹ️ Info | ENVIRONMENTAL type with no actionable keywords |
| `INFORMATION` | ℹ️ | Blue | 50% | No | ℹ️ Info | NAVIGATION_RESTRICTION type with no actionable keywords |

---

## 3. Implementation Steps

### Step 1: Data Model — `RegulatedZone.kt`

- Add `RegulationClassification` sealed class (5 variants)
- Add `SpeedSource` enum (5 values)
- Add 3 new fields to `RegulatedZone` with ProtoNumber 11–13
- Add `ENVIRONMENTAL` and `INFORMATION` to `ZoneDisplayCategory` enum
- Update `displayCategories()`:
  - Add `if (restrictionCode == 10 || restrn == 10)` code check for `NO_DIVING`
  - Add keyword scanning for `posidonie`/`herbier` → `NO_ANCHOR`
  - Add fallback: if `zoneType == ENVIRONMENTAL` and no keywords matched → `ENVIRONMENTAL`
  - Add fallback: if `zoneType == NAVIGATION_RESTRICTION` and no keywords matched → `INFORMATION`

### Step 2: Icon Provider — `RegulatedZoneIconProvider.kt`

- Add emoji/colour/alpha/strike mappings for `ENVIRONMENTAL` (🌿, blue, 50%, no strike)
- Add emoji/colour/alpha/strike mappings for `INFORMATION` (ℹ️, blue, 50%, no strike)
- Fix existing colours to blue for all except SPEED_LIMIT (red) and SEAPLANE (grey)
- Current blue categories already correct: NO_ANCHOR, MOORING, NO_DIVING, NO_ACCESS

### Step 3: Map Display — `MapScreen.kt`

- Verify `RegulationZoneCategoryIcon` composable handles `ENVIRONMENTAL` and `INFORMATION`
- Verify strike logic: `hasStrike = category in [NO_ANCHOR, NO_DIVING, NO_ACCESS]`
- No other display changes needed

---

## 4. Files Changed

| File | Change |
|------|--------|
| `RegulatedZone.kt` | Classification sealed class, SpeedSource enum, 3 new fields, 2 new ZoneDisplayCategory values, extended displayCategories() |
| `RegulatedZoneIconProvider.kt` | emojiForCategory, colorForCategory, alphaForCategory for 2 new categories; colours to blue |
| `MapScreen.kt` | Verify new categories handled in RegulationZoneCategoryIcon |
