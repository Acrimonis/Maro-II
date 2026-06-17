<!-- scope: feature -->
# Regulated Zones — Data Format & Extraction Redesign

> No backward compatibility required. Full renumbering of ProtoNumber fields.
> All SHOM layers available on the public INSPIRE endpoint — no auth needed.

> No backward compatibility required. Full renumbering of ProtoNumber fields.
> Old `.bin` will be regenerated via re-run of prebake.

---

## Proposed Format

```kotlin
@Serializable
data class RegulatedZoneSet(
    @ProtoNumber(1) val zones: List<RegulatedZone>,
    @ProtoNumber(2) val metadata: RegulationMetadata
)

@Serializable
data class RegulatedZone(
    // ── Geometry ────────────────────────────────────────
    @ProtoNumber(1) val outerRing: List<LatLng>,
    @ProtoNumber(2) val holes: List<List<LatLng>> = emptyList(),

    // ── Classification (always populated) ───────────────
    @ProtoNumber(3) val zoneType: RegulatedZoneType,          // derived type
    @ProtoNumber(4) val classification: RegulationClassification, // source system

    // ── Speed (always populated, NONE if not speed zone) ─
    @ProtoNumber(5) val speedLimitKn: Double? = null,
    @ProtoNumber(6) val speedSource: SpeedSource,             // how speed was derived

    // ── Identity & Provenance ────────────────────────────
    @ProtoNumber(7) val name: String = "",
    @ProtoNumber(8) val source: String,                       // "SHOM", "INPN", "SEED"
    @ProtoNumber(9) val sourceRef: String = "",               // official ID
    @ProtoNumber(10) val sourceLayer: String? = null,          // WFS layer name

    // ── Descriptions ─────────────────────────────────────
    @ProtoNumber(11) val description: String = "",             // combined description
    @ProtoNumber(12) val informFr: String? = null,             // raw French INFORM text

    // ── Legal & Restriction ──────────────────────────────
    @ProtoNumber(13) val legalDecreeRef: String? = null,       // TXTDSC arrêté ref
    @ProtoNumber(14) val vesselSizeRestriction: VesselSizeRestriction? = null,
)

@Serializable
sealed class RegulationClassification {
    @Serializable data class S101(val code: Int) : RegulationClassification()
    @Serializable data class Catrea(val code: Int) : RegulationClassification()
    @Serializable data class Restrn(val code: Int) : RegulationClassification()
    @Serializable data class InpnMpa(val type: String, val mnhnId: String?) : RegulationClassification()
    @Serializable data object Seed : RegulationClassification()
}

enum class SpeedSource {
    STRUCTURED_FIELD,     // vitesse_max property
    INFORM_TEXT,          // parsed from INFORM string
    TXTDSC_MAP,           // cross-referenced TXTDSC decree
    DEFAULT_RULE,         // 5 kn coastal baseline
    NONE                  // not a speed zone
}
```

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| `classification` is **non-nullable** | Every zone must record how it was classified |
| `speedSource` is **non-nullable** (default `NONE`) | Every zone knows whether speed extraction was attempted |
| Removed raw code fields (`catrea`, `restrn`, `mpaType`, `mpaMnhnId`) | Raw codes are captured inside `RegulationClassification` sealed variants — no need for separate nullable fields |
| Removed old `restrictionCode` field | Replaced by `classification: S101(code)` |
| Renumbered ProtoNumber fields | Old `.bin` will be regenerated, no compat needed |
| `sourceLayer` is nullable | Not all sources have a WFS layer name (seeds don't) |
| `informFr` kept as separate field | Raw French INFORM text useful for debugging and fallback keyword scanning |

---

## Changed From Current Model

| Current Field | Proto# | New Field | Proto# | Change |
|--------------|--------|-----------|--------|--------|
| outerRing | 1 | outerRing | 1 | Same |
| holes | 2 | holes | 2 | Same |
| zoneType | 3 | zoneType | 3 | Same |
| speedLimitKn | 4 | speedLimitKn | 5 | **Renumbered** |
| name | 5 | name | 7 | **Renumbered** |
| source | 6 | source | 8 | **Renumbered** |
| sourceRef | 7 | sourceRef | 9 | **Renumbered** |
| description | 8 | description | 11 | **Renumbered** |
| vesselSizeRestriction | 9 | vesselSizeRestriction | 14 | **Renumbered** |
| restrictionCode | 10 | *(removed)* | — | Replaced by `classification: S101` |
| *(new)* | — | classification | 4 | **NEW** — sealed class |
| *(new)* | — | speedSource | 6 | **NEW** — required enum |
| *(new)* | — | sourceLayer | 10 | **NEW** — WFS layer name |
| *(new)* | — | informFr | 12 | **NEW** — raw French INFORM |
| *(new)* | — | legalDecreeRef | 13 | **NEW** — TXTDSC reference |

---

## Data Extraction (Same Scope)

### SHOM — Single Public Endpoint

All layers available on the same public endpoint:

| Property | Value |
|----------|-------|
| WFS Endpoint | `https://services.data.shom.fr/INSPIRE/wfs` |
| Auth | **None** — fully anonymous access |
| Existing layers | `REGLEMENTATION_NAVIGATION_BDD_WFS:resare_polygon`, `splare_polygon`, `achare_polygon`, `achare_point`, `ctsare_polygon`, `admare_polygon` |
| **New layer** | `REGNAV_BDD_WFS:resare` — richer S-57 properties (CATREA, RESTRN, INFORM, TXTDSC) |
| Output format | `application/json` (GeoJSON) |
| SRS | EPSG:3857 (Web Mercator) — existing conversion code reused |

Add `REGNAV_BDD_WFS:resare` to `CANDIDATE_TYPENAMES` in `ShomRegulationClient`. Parse its additional GeoJSON properties: `CATREA`, `RESTRN`, `INFORM`/`inform_fr`, `TXTDSC`, `objnam`.

### INPN — New Client

| Property | Value |
|----------|-------|
| WFS Endpoint | `https://inpn-inspire.mnhn.fr/geoservices/ows` |
| Auth | None |
| Primary layer | `wfs_inpn:amp_polygones` (Marine Protected Areas) |
| Secondary layer | `wfs_inpn:natura2000_sic` (Sites d'Intérêt Communautaire) |
| Output format | `application/json` (GeoJSON) |
| SRS | EPSG:4326 (WGS84) likely — auto-detect in coordinate parser |

### Bounding Box

Expand from current `6.73,43.35,7.31,43.73` to `6.7,43.4,7.6,43.8` (Menton to Fréjus).

---

## Impact

| Artifact | Action |
|----------|--------|
| `RegulatedZone.kt` | Rewrite data classes, sealed class, enums, `ZoneDisplayCategory` + `displayCategories()` |
| `RegulatedZoneSerializer.kt` | No change (kotlinx.serialization handles sealed classes) |
| `ShomRegulationClient.kt` | Add `REGNAV_BDD_WFS:resare` to typenames, parse CATREA/RESTRN/INFORM/TXTDSC, populate `classification` |
| `InpnRegulationClient.kt` | **NEW** — WFS client for INPN layers |
| `RegulationAggregator.kt` | Accept INPN zones, 3-way dedup (SHOM > INPN > SEED) |
| `RegulatedZonePrebakeTest.kt` | Wire INPN client, expanded bbox, source summary |
| `RegulatedZoneIconProvider.kt` | Add ENVIRONMENTAL + INFORMATION mappings, fix colours to blue |
| `MapScreen.kt` | Verify new categories handled |
| `data/app-assets/regulated-zones/nice-frejus.bin` | **Delete** — regenerate with `bake-regulated-zones` |

