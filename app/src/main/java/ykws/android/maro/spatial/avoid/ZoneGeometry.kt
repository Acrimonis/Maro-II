package ykws.android.maro.spatial.avoid

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * The speed-zone geometry the avoid engine reads — pure Kotlin, so the rasterizer's fill, the ETA's
 * limit read and their tests share one spelling of "inside a zone" rather than three. The inside test
 * walks every ring with the same half-open latitude rule the rasterizer's scanline uses, so a boundary
 * vertex agrees between the grid fill and the point read.
 */

/** The zone's axis-aligned bounding box, read from its outer ring alone. */
fun SpeedZone.bbox(): BBox {
    var latSouth = Double.MAX_VALUE
    var latNorth = -Double.MAX_VALUE
    var lonWest = Double.MAX_VALUE
    var lonEast = -Double.MAX_VALUE
    for (p in outerRing) {
        latSouth = min(latSouth, p.latitude)
        latNorth = max(latNorth, p.latitude)
        lonWest = min(lonWest, p.longitude)
        lonEast = max(lonEast, p.longitude)
    }
    return BBox(latSouth, latNorth, lonWest, lonEast)
}

/**
 * Even-odd point-in-ring with the rasterizer's half-open latitude rule: a vertex lying exactly on the
 * scanline counts once, so the fill and this read draw the same boundary.
 */
private fun ringContains(ring: List<LatLng>, latitude: Double, longitude: Double): Boolean {
    if (ring.size < 3) return false
    var inside = false
    var prev = ring[ring.size - 1]
    for (v in ring) {
        val spans = (prev.latitude <= latitude && latitude < v.latitude) ||
            (v.latitude <= latitude && latitude < prev.latitude)
        if (spans) {
            val t = (latitude - prev.latitude) / (v.latitude - prev.latitude)
            if (longitude < prev.longitude + t * (v.longitude - prev.longitude)) inside = !inside
        }
        prev = v
    }
    return inside
}

/** Inside the outer ring and not inside any hole — holes stay water. */
fun SpeedZone.contains(latitude: Double, longitude: Double): Boolean {
    if (!ringContains(outerRing, latitude, longitude)) return false
    return holes.none { ringContains(it, latitude, longitude) }
}

/** Zones whose bounding box overlaps [box], with the ids in [excludedIds] dropped. */
fun speedZonesInBox(zones: List<SpeedZone>, box: BBox, excludedIds: Set<String>): List<SpeedZone> =
    zones.filter { it.id !in excludedIds && it.bbox().overlaps(box) }

/** The strictest limit among the non-excluded zones containing the point, or `null` outside all. */
fun strictestLimitKnAt(
    zones: List<SpeedZone>,
    excludedIds: Set<String>,
    latitude: Double,
    longitude: Double
): Double? = zones
    .filter { it.id !in excludedIds }
    .filter { it.contains(latitude, longitude) }
    .minOfOrNull { it.speedLimitKn }

/** Whether the emitted line enters [zone], sampled at [stepM] along every leg. */
fun lineEntersZone(waypoints: List<LatLng>, zone: SpeedZone, stepM: Double = 25.0): Boolean {
    if (waypoints.any { zone.contains(it.latitude, it.longitude) }) return true
    for (i in 0 until waypoints.size - 1) {
        val a = waypoints[i]
        val b = waypoints[i + 1]
        val dist = SpatialOperations.haversine(a, b)
        val steps = ceil(dist / stepM).toInt().coerceAtLeast(2)
        for (s in 1 until steps) {
            val t = s.toDouble() / steps
            val p = LatLng(
                a.latitude + (b.latitude - a.latitude) * t,
                a.longitude + (b.longitude - a.longitude) * t
            )
            if (zone.contains(p.latitude, p.longitude)) return true
        }
    }
    return false
}

/**
 * The names of the priced zones the line enters, reported only when no avoiding path exists — an
 * ordinary priced crossing with a way around reports nothing.
 */
fun forcedCrossingZoneNames(
    waypoints: List<LatLng>,
    zones: List<SpeedZone>,
    avoidingPathExists: Boolean
): List<String> =
    if (avoidingPathExists) emptyList()
    else zones.filter { lineEntersZone(waypoints, it) }.map { it.name }
