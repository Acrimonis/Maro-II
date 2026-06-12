package ykws.android.maro.data.regulation

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import ykws.android.maro.data.model.LatLng

/**
 * Classification of maritime regulation zone types.
 */
@Serializable
enum class RegulatedZoneType {
    SPEED_LIMIT,
    ANCHORING_PROHIBITED,
    ACCESS_PROHIBITED,
    ENVIRONMENTAL,
    MOORING,
    FISHING_PROHIBITED,
    NAVIGATION_RESTRICTION,
    OTHER
}

/**
 * Provenance-aware classification for a regulated zone.
 *
 * Captures how the zone was classified, preserving the original source
 * codes for traceability.
 */
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

/**
 * Indicates how the speed limit value was extracted for a zone.
 */
enum class SpeedSource {
    STRUCTURED_FIELD,     // vitesse_max GeoJSON property
    INFORM_TEXT,          // Parsed from INFORM/inform_fr string
    TXTDSC_MAP,           // Cross-referenced via TXTDSC decree name
    DEFAULT_RULE,         // 5 kn coastal baseline
    NONE                  // No speed limit
}

/**
 * Vessel size applicability constraint for a regulation zone.
 *
 * Many SHOM regulations target specific vessel size ranges (e.g. speed limits
 * for vessels > 25 m, anchoring restrictions for vessels > 20 m). A boat
 * < 6 m (the user's case) may be exempt from many of these.
 *
 * Both fields null = applies to all vessels.
 *
 * @property minLengthM Minimum vessel length in metres (inclusive), null = no minimum.
 * @property maxLengthM Maximum vessel length in metres (inclusive), null = no maximum.
 */
@Serializable
data class VesselSizeRestriction(
    @ProtoNumber(1) val minLengthM: Double? = null,
    @ProtoNumber(2) val maxLengthM: Double? = null
)

/**
 * A maritime regulatory zone with a polygon geometry and typed attributes.
 *
 * @property outerRing           Polygon outer boundary as closed LatLng list
 * @property holes               Zero or more interior holes (islands exempt from the regulation)
 * @property zoneType            Classification of the regulation (speed, anchoring, access, …)
 * @property speedLimitKn        Speed limit in knots, null if not a speed zone
 * @property name                Human-readable name (e.g. "Cap d'Antibes — 10 nœuds")
 * @property source              Data source identifier ("SHOM", "SEED", "INPN")
 * @property sourceRef           Official reference ID or arrêté number
 * @property description         Free-text description of the regulation
 * @property vesselSizeRestriction Vessel size applicability constraint, null = applies to all
 * @property restrictionCode     Raw SHOM S-101 restriction code integer
 * @property classification      Provenance-aware classification (sealed class)
 * @property speedSource         How the speed limit was extracted (null = not applicable)
 * @property legalDecreeRef      Official legal decree reference (e.g. "FR_PREMAR_MED_134_2021")
 */
@Serializable
data class RegulatedZone(
    @ProtoNumber(1) val outerRing: List<LatLng>,
    @ProtoNumber(2) val holes: List<List<LatLng>> = emptyList(),
    @ProtoNumber(3) val zoneType: RegulatedZoneType,
    @ProtoNumber(4) val speedLimitKn: Double? = null,
    @ProtoNumber(5) val name: String = "",
    @ProtoNumber(6) val source: String = "SHOM",
    @ProtoNumber(7) val sourceRef: String = "",
    @ProtoNumber(8) val description: String = "",
    @ProtoNumber(9) val vesselSizeRestriction: VesselSizeRestriction? = null,
    /** Raw SHOM S-101 restriction code integer, e.g. 1=speed, 7=no-anchor, 25=diving, 28=environment. */
    @ProtoNumber(10) val restrictionCode: Int? = null,
    /** Provenance-aware classification — see [RegulationClassification]. */
    @ProtoNumber(11) val classification: RegulationClassification? = null,
    /** How the speed limit was extracted — see [SpeedSource]. */
    @ProtoNumber(12) val speedSource: SpeedSource? = null,
    /** Official legal decree reference (e.g. "FR_PREMAR_MED_134_2021"). */
    @ProtoNumber(13) val legalDecreeRef: String? = null
) {
    /**
     * Check whether this zone applies to a vessel of the given length.
     *
     * A `null` [vesselSizeRestriction] means the zone applies to all vessels.
     *
     * @param vesselLengthM Length of the vessel in metres.
     * @return `true` if the zone applies, `false` if the vessel is exempt.
     */
    /**
     * Check whether this zone applies to a vessel of the given length.
     *
     * Checks two sources:
     * 1. [vesselSizeRestriction] field (populated by auth-protected SHOM WFS)
     * 2. [description] text heuristic (e.g. "vessels more than 50m",
     *    "for all boats", "mandatory for vessels more than 50m")
     *
     * Speed limit zones always apply to all vessels. If no restriction is
     * found, the zone applies to all.
     *
     * @param vesselLengthM Length of the vessel in metres.
     * @return `true` if the zone applies, `false` if the vessel is exempt.
     */
    fun appliesTo(vesselLengthM: Double): Boolean {
        if (vesselLengthM < 0.0) return false
        // Speed limits apply to all vessels
        if (speedLimitKn != null) return true

        // 1. Check structured vesselSizeRestriction (from auth WFS)
        val r = vesselSizeRestriction
        if (r != null) {
            if (r.minLengthM != null && vesselLengthM < r.minLengthM) return false
            if (r.maxLengthM != null && vesselLengthM > r.maxLengthM) return false
            return true
        }

        // 2. Check description text heuristic (from public INSPIRE WFS)
        if (description.isNotBlank()) {
            val desc = description.lowercase()

            // Minimum vessel size patterns:
            //   "more than 50m", "greater than 20 m", "over 80m"
            //   "greater than or equal to 24 m", "≥ 24 m"
            //   "20 metres or more", "24 m or more", "80 metres and over"
            val minMatch = Regex("""(more than|over|exceeding|greater than or equal to|greater than|>|≥|minimum|supérieur|supérieure|plus de|>)\s*(\d+)\s*m""")
                .find(desc)
            val orMoreMatch = Regex("""(\d+)\s*m(etres|eters)?\s*(or more|ou plus|and over|et plus)""")
                .find(desc)

            val minM = minMatch?.groupValues?.get(2)?.toDoubleOrNull()
                ?: orMoreMatch?.groupValues?.get(1)?.toDoubleOrNull()

            if (minM != null && vesselLengthM < minM) return false

            // Maximum vessel size: "less than 20m", "< 20m", "moins de 20m"
            val maxMatch = Regex("""(less than|under|below|<|≤|maximum|inférieur|inférieure|moins de|<)\s*(\d+)\s*m""")
                .find(desc)
            if (maxMatch != null) {
                val maxM = maxMatch.groupValues[2].toDoubleOrNull()
                if (maxM != null && vesselLengthM > maxM) return false
            }
        }
        return true
    }
}

/**
 * Metadata describing the regulation data set provenance.
 *
 * @property regionId         Region identifier, default "nice-frejus"
 * @property fetchTimestampMs Epoch millis when the data was fetched
 * @property sourceCount      Number of distinct sources that contributed zones
 * @property totalZones       Total number of zones in the set
 */
@Serializable
data class RegulationMetadata(
    @ProtoNumber(1) val regionId: String = "nice-frejus",
    @ProtoNumber(2) val fetchTimestampMs: Long,
    @ProtoNumber(3) val sourceCount: Int,
    @ProtoNumber(4) val totalZones: Int
)

/**
 * A complete serializable set of regulated zones with metadata.
 */
@Serializable
data class RegulatedZoneSet(
    @ProtoNumber(1) val zones: List<RegulatedZone>,
    @ProtoNumber(2) val metadata: RegulationMetadata
)

/**
 * A hardcoded seed zone definition used as fallback when remote WFS sources are unreachable.
 *
 * @property lat          Latitude of zone centroid (WGS84)
 * @property lon          Longitude of zone centroid (WGS84)
 * @property radiusM      Radius in metres from centroid
 * @property zoneType     Classification of the regulation
 * @property speedLimitKn Speed limit in knots, null if not a speed zone
 * @property name         Human-readable name
 * @property description  Free-text description
 */
data class RegulationSeed(
    val lat: Double,
    val lon: Double,
    val radiusM: Double,
    val zoneType: RegulatedZoneType,
    val speedLimitKn: Double? = null,
    val name: String,
    val description: String
)

/**
 * Display category for the warning strip — derived at render time from
 * [RegulatedZone] properties. No protobuf serialization impact.
 *
 * Each category gets a distinct icon and colour in the warning strip.
 *
 * Categories are ordered by priority — the first matched category in
 * [displayCategories] wins priority in strip rendering.
 */
enum class ZoneDisplayCategory {
    NO_ANCHOR,
    MOORING,
    SPEED_LIMIT,
    NO_DIVING,
    SEAPLANE,
    NO_ACCESS,
    /** Environmental zone (marine park, nature reserve) with no actionable prohibition. */
    ENVIRONMENTAL,
    /** Informational zone with no actionable prohibition or speed limit. */
    INFORMATION,
}

/**
 * Derive all [ZoneDisplayCategory] values that apply to this [RegulatedZone]
 * based on its type, speed limit, and description text.
 *
 * A single zone can match multiple categories (e.g. a Nice airport zone that
 * restricts both "anchorage" and "diving" returns both [NO_ANCHOR] and
 * [NO_DIVING]). The warning strip shows one icon per distinct category.
 *
 * Returns an empty set if no category matches (zone renders on map but gets
 * no strip icon).
 */
fun RegulatedZone.displayCategories(): Set<ZoneDisplayCategory> {
    val desc = description.lowercase()
    val cats = mutableSetOf<ZoneDisplayCategory>()

    // ── Anchoring / mouillage ──────────────────────────────────────────────
    // Keyword match includes seagrass keywords which imply no-anchoring rules.
    val hasAnchorDesc = "anchoring is prohibited" in desc ||
            "anchoring and stopping prohibited" in desc ||
            "anchorage is prohibited" in desc ||
            "anchoring" in desc ||
            "mouillage" in desc ||
            "ancrage" in desc ||
            "posidonie" in desc ||
            "herbier" in desc
    if (zoneType == RegulatedZoneType.ANCHORING_PROHIBITED ||
        (zoneType != RegulatedZoneType.SPEED_LIMIT && hasAnchorDesc)
    ) cats += ZoneDisplayCategory.NO_ANCHOR

    // ── Diving / plongée ───────────────────────────────────────────────────
    // Also check restrictionCode == 10 (S-101: prohibited area, often diving)
    val divingDesc = "diving" in desc || "plongée" in desc || "subaquatique" in desc
    if (divingDesc || restrictionCode == 10) cats += ZoneDisplayCategory.NO_DIVING

    // ── Small craft mooring / amarrage ──────────────────────────────────────
    if (zoneType == RegulatedZoneType.MOORING ||
        "small craft mooring" in desc ||
        "moorings" in desc ||
        "mooring" in desc ||
        "amarrage" in desc ||
        "corps mort" in desc
    ) cats += ZoneDisplayCategory.MOORING

    // ── Seaplane / hydravion ────────────────────────────────────────────────
    if ("seaplane" in desc || "hydravion" in desc) cats += ZoneDisplayCategory.SEAPLANE

    // ── Speed limit ─────────────────────────────────────────────────────────
    if (zoneType == RegulatedZoneType.SPEED_LIMIT ||
        speedLimitKn != null ||
        "speed is limited" in desc ||
        "speed limit" in desc ||
        "speed" in desc ||
        "vitesse" in desc ||
        "knot" in desc ||
        "noeud" in desc
    ) cats += ZoneDisplayCategory.SPEED_LIMIT

    // ── Access prohibition ──────────────────────────────────────────────────
    if (zoneType == RegulatedZoneType.ACCESS_PROHIBITED ||
        "access is prohibited" in desc ||
        "entry is prohibited" in desc ||
        "prohibited area" in desc ||
        "accès interdit" in desc ||
        "interdit" in desc ||
        "prohibé" in desc
    ) cats += ZoneDisplayCategory.NO_ACCESS

    // ── Fallback for environmental zones ────────────────────────────────────
    // If the zone is ENVIRONMENTAL type but no actionable keyword matched,
    // show it as informational ENVIRONMENTAL.
    if (cats.isEmpty() && zoneType == RegulatedZoneType.ENVIRONMENTAL) {
        cats += ZoneDisplayCategory.ENVIRONMENTAL
    }

    // ── Fallback for navigation restriction zones ───────────────────────────
    // If the zone is NAVIGATION_RESTRICTION type but no actionable keyword
    // matched, show it as informational INFORMATION.
    if (cats.isEmpty() && zoneType == RegulatedZoneType.NAVIGATION_RESTRICTION) {
        cats += ZoneDisplayCategory.INFORMATION
    }

    return cats
}

/**
 * Check whether a geographic [point] (WGS84) falls inside this [RegulatedZone]'s
 * outer ring (and not inside any hole). Uses the even-odd rule ray casting
 * algorithm, matching the implementation in [CoastlineSpatialIndex].
 *
 * @param point The point to test (WGS84 latitude/longitude).
 * @return `true` if the point is inside the zone (including on the boundary).
 */
fun RegulatedZone.contains(point: LatLng): Boolean {
    val ring = outerRing
    if (ring.size < 3) return false

    // Even-odd ray casting (PNPOLY)
    var inside = false
    var j = ring.size - 1
    for (i in ring.indices) {
        val yi = ring[i].latitude; val xi = ring[i].longitude
        val yj = ring[j].latitude; val xj = ring[j].longitude
        if (((yi > point.latitude) != (yj > point.latitude)) &&
            (point.longitude < (xj - xi) * (point.latitude - yi) / (yj - yi) + xi)
        ) inside = !inside
        j = i
    }
    if (!inside) return false

    // Check holes — if the point is inside any hole, it's NOT in the zone
    for (hole in holes) {
        if (hole.size < 3) continue
        var inHole = false
        var k = hole.size - 1
        for (i in hole.indices) {
            val yi = hole[i].latitude; val xi = hole[i].longitude
            val yj = hole[k].latitude; val xj = hole[k].longitude
            if (((yi > point.latitude) != (yj > point.latitude)) &&
                (point.longitude < (xj - xi) * (point.latitude - yi) / (yj - yi) + xi)
            ) inHole = !inHole
            k = i
        }
        if (inHole) return false
    }

    return true
}
