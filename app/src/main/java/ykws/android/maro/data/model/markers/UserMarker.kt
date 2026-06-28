package ykws.android.maro.data.model.markers

import kotlinx.serialization.Serializable
import ykws.android.maro.data.model.LatLng
import kotlin.math.*

/**
 * A user-defined marker placed on the map.
 *
 * Markers have three geometry types (Pin, Circle, Corridor) and support on-demand
 * sea-distance-gated matching — see the markers design plan for the full spec.
 *
 * @property id                  Unique identifier (UUID string).
 * @property name                User-assigned label (displayed in drawer/management, NOT on map).
 * @property geometry            Pin, Circle, or Corridor geometry.
 * @property description         Optional free-text note or rule description.
 * @property proximityOverrideM  If non-null, overrides the default proximity range formula.
 *                               If null, proximity is computed from geometry + config.
 * @property confirmed           Whether the marker has been saved (confirmed) or is in placement (unconfirmed).
 *                               Unconfirmed markers render in [semantic.caution] colour;
 *                               confirmed markers render in [semantic.info] colour.
 */
@Serializable
data class UserMarker(
    val id: String,
    val name: String,
    val geometry: MarkerGeometry,
    val description: String = "",
    val proximityOverrideM: Double? = null,
    val confirmed: Boolean = true,
    val colorIndex: Int? = null,      // null = default colour, 0-15 = 16-colour palette
    val pinned: Boolean = false,
    val icon: String? = null,          // POI emoji/unicode icon, null = no icon
    val createdAtEpochMs: Long = 0L    // 0 = legacy marker
) {
    /**
     * Axis-aligned lat/lon bounding box for cheap pre-filter before expensive
     * land-blocking checks. Computed lazily from [geometry] on first access.
     *
     * 4 float comparisons per marker gate the call to [closestUnblockedPoint].
     */
    val bbox: BBox by lazy { computeBbox(geometry) }

    companion object {
        /** Earth radius in metres (WGS84 mean radius). */
        private const val EARTH_RADIUS_M = 6_371_000.0

        /** Metres per degree of latitude at the equator. */
        private val M_PER_DEG_LAT = EARTH_RADIUS_M * PI / 180.0

        private fun computeBbox(geometry: MarkerGeometry): BBox {
            return when (geometry) {
                is MarkerGeometry.Pin -> {
                    val p = geometry.position
                    BBox(p.latitude, p.latitude, p.longitude, p.longitude)
                }
                is MarkerGeometry.Circle -> {
                    val c = geometry.center
                    val degPerMeterLat = 1.0 / M_PER_DEG_LAT
                    val degPerMeterLon = degPerMeterLat / cos(Math.toRadians(c.latitude))
                    val dLat = geometry.radiusM * degPerMeterLat
                    val dLon = geometry.radiusM * degPerMeterLon
                    BBox(c.latitude - dLat, c.latitude + dLat,
                         c.longitude - dLon, c.longitude + dLon)
                }
                is MarkerGeometry.Corridor -> {
                    val degPerMeterLat = 1.0 / M_PER_DEG_LAT
                    val avgLat = (geometry.p1.latitude + geometry.p2.latitude) / 2.0
                    val degPerMeterLon = degPerMeterLat / cos(Math.toRadians(avgLat))
                    val hwLat = (geometry.widthM / 2.0) * degPerMeterLat
                    val hwLon = (geometry.widthM / 2.0) * degPerMeterLon
                    BBox(
                        minOf(geometry.p1.latitude, geometry.p2.latitude) - hwLat,
                        maxOf(geometry.p1.latitude, geometry.p2.latitude) + hwLat,
                        minOf(geometry.p1.longitude, geometry.p2.longitude) - hwLon,
                        maxOf(geometry.p1.longitude, geometry.p2.longitude) + hwLon
                    )
                }
            }
        }
    }
}

/**
 * Axis-aligned lat/lon bounding box for cheap spatial pre-filter.
 *
 * @property latSouth Southernmost latitude in decimal degrees.
 * @property latNorth Northernmost latitude in decimal degrees.
 * @property lonWest  Westernmost longitude in decimal degrees.
 * @property lonEast  Easternmost longitude in decimal degrees.
 */
data class BBox(
    val latSouth: Double,
    val latNorth: Double,
    val lonWest: Double,
    val lonEast: Double
) {
    /**
     * True if this bounding box overlaps [other], optionally expanded by
     * [marginDeg] degrees on all sides.
     */
    fun overlaps(other: BBox, marginDeg: Double = 0.0): Boolean =
        latSouth - marginDeg <= other.latNorth + marginDeg &&
        latNorth + marginDeg >= other.latSouth - marginDeg &&
        lonWest - marginDeg <= other.lonEast + marginDeg &&
        lonEast + marginDeg >= other.lonWest - marginDeg
}

/**
 * Sealed hierarchy of marker geometry types.
 *
 * MarkerGeometry subclasses are pure shape definitions; matching logic
 * (zone containment, proximity range, land-blocking) lives in the
 * match-resolution engine (Phase C).
 */
@Serializable
sealed class MarkerGeometry {

    /** Pin: a single point — no zone, proximity-only matching. */
    @Serializable
    data class Pin(
        val position: LatLng
    ) : MarkerGeometry()

    /** Circle: a circular zone defined by a centre point and radius in metres. */
    @Serializable
    data class Circle(
        val center: LatLng,
        val radiusM: Double            // > 0
    ) : MarkerGeometry()

    /**
     * Corridor: a linear zone between two endpoints with a width.
     *
     * The corridor has rounded end caps: being within `widthM / 2` of p1 or p2
     * counts as inside, even if the perpendicular projection falls past the segment end.
     */
    @Serializable
    data class Corridor(
        val p1: LatLng,
        val p2: LatLng,
        val widthM: Double             // > 0
    ) : MarkerGeometry()
}
