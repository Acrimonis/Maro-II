package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.abs
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

/**
 * The strictest limit among the non-excluded zones whose **outer ring** lies within [marginM] of the
 * point — the outside margin's limit read, the strictest-wins rule the grid's collar fill and the
 * pull's guard share. Interior containment is answered separately by [strictestLimitKnAt], so the two
 * never double-price.
 */
fun speedZoneCollarLimitKnAt(
    zones: List<SpeedZone>,
    excludedIds: Set<String>,
    latitude: Double,
    longitude: Double,
    marginM: Double
): Double? {
    val p = LatLng(latitude, longitude)
    var strictest: Double? = null
    for (zone in zones) {
        if (zone.id in excludedIds) continue
        val outer = zone.outerRing
        if (outer.size < 2) continue
        var nearest = Double.MAX_VALUE
        for (i in 0 until outer.size - 1) {
            nearest = min(nearest, SpatialOperations.pointToSegmentDistance(p, outer[i], outer[i + 1]))
        }
        if (nearest <= marginM && (strictest == null || zone.speedLimitKn < strictest)) {
            strictest = zone.speedLimitKn
        }
    }
    return strictest
}

/**
 * **The zone price's own boundary distance** — the metres from the point to the nearest place a speed
 * zone's price arm changes: every ring's own edge, where containment toggles, and each ring's collar
 * edge [marginM] out, where the collar arm turns on and off. The minimum over every non-excluded zone,
 * because the arm is the strictest limit over the zones and so changes where **any** one of them does.
 *
 * **Every ring is named, the holes included.** The shipped collar read walks [SpeedZone.outerRing]
 * alone ([speedZoneCollarLimitKnAt]), while the interior arm toggles on every hole too
 * ([SpeedZone.contains]), so a declaration built from the collar read would be blind to a hole and could
 * prove a group straddling one — the defect this function exists to close. `Double.MAX_VALUE` where
 * nothing is near a boundary.
 */
fun speedZonePriceClearanceM(
    zones: List<SpeedZone>,
    excludedIds: Set<String>,
    latitude: Double,
    longitude: Double,
    marginM: Double
): Double {
    val p = LatLng(latitude, longitude)
    var nearest = Double.MAX_VALUE
    for (zone in zones) {
        if (zone.id in excludedIds) continue
        nearest = min(nearest, ringArmClearanceM(zone.outerRing, marginM, p))
        for (hole in zone.holes) {
            nearest = min(nearest, ringArmClearanceM(hole, marginM, p))
        }
    }
    return nearest
}

/**
 * The distance (m) from [p] to a ring's own two arm changes: the ring's edge itself and its collar
 * edge [marginM] out. A ring of fewer than two vertices bounds nothing, and holes are rings like any
 * other. `Double.MAX_VALUE` where the ring is degenerate.
 */
private fun ringArmClearanceM(ring: List<LatLng>, marginM: Double, p: LatLng): Double {
    if (ring.size < 2) return Double.MAX_VALUE
    var nearest = Double.MAX_VALUE
    for (i in 0 until ring.size - 1) {
        nearest = min(nearest, SpatialOperations.pointToSegmentDistance(p, ring[i], ring[i + 1]))
    }
    if (nearest == Double.MAX_VALUE) return nearest
    return min(nearest, abs(nearest - marginM))
}

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
