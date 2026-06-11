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
 * @property source              Data source identifier ("SHOM", "SEED", "OSM", "DIRM")
 * @property sourceRef           Official reference ID or arrêté number
 * @property description         Free-text description of the regulation
 * @property vesselSizeRestriction Vessel size applicability constraint, null = applies to all
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
    @ProtoNumber(9) val vesselSizeRestriction: VesselSizeRestriction? = null
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
            // Minimum vessel size: "more than 50m", "vessels more than 50m", "> 50m", "plus de 50m"
            val minMatch = Regex("""(more than|over|exceeding|>|≥|minimum|supérieur|supérieure|plus de|>)\s*(\d+)\s*m""")
                .find(desc)
            if (minMatch != null) {
                val minM = minMatch.groupValues[2].toDoubleOrNull()
                if (minM != null && vesselLengthM < minM) return false
            }
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
