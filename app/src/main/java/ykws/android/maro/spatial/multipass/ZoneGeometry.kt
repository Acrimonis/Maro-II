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

/**
 * **The strictest price among the non-excluded zones containing the point, or `null` outside all** —
 * the routing-only `speedRoute` override where one is set, else the displayed limit. It is the **price
 * law**: the guard's live interior read, the same value the grid stores in a `ZoneRing`. It is **not**
 * the clock's read — a reported figure obeys the limit the user is shown, which is
 * [strictestDisplayLimitKnAt]'s own answer.
 */
fun strictestLimitKnAt(
    zones: List<SpeedZone>,
    excludedIds: Set<String>,
    latitude: Double,
    longitude: Double
): Double? = zones
    .filter { it.id !in excludedIds }
    .filter { it.contains(latitude, longitude) }
    .minOfOrNull { it.routeLimitKn }

/**
 * **The strictest displayed limit among the non-excluded zones containing the point, or `null` outside
 * all** — the **clock's own read**, beside the price law above and deliberately apart from it.
 *
 * A zone may carry a routing-only `speedRoute` override, which buys the search an advantage by lowering
 * its price while the water is still signed at the displayed limit. Every figure a navigator reads —
 * the leg speed, the trip's clock, the line's colour and the slow-limit attribution — must answer the
 * limit in force **on the water**, never the price the search negotiated, or the app would report a
 * speed the chart does not permit. One home for that law, so the clock and the map's own surfaces
 * ([`SpeedZoneIndex`](../../../spatial/SpeedZoneIndex.kt)) agree about what the limit is.
 */
fun strictestDisplayLimitKnAt(
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
        val nearest = ringDistanceM(outer, p)
        if (nearest <= marginM && (strictest == null || zone.routeLimitKn < strictest)) {
            strictest = zone.routeLimitKn
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
    val nearest = ringDistanceM(ring, p)
    if (nearest == Double.MAX_VALUE) return nearest
    return min(nearest, abs(nearest - marginM))
}

/**
 * **The metres from [p] to [ring]'s nearest edge** — the least [SpatialOperations.pointToSegmentDistance] over
 * its segments, `Double.MAX_VALUE` for a ring of fewer than two vertices: the collar reads' and the arm
 * clearance's one walk. A zone ring holds tens of vertices, so no segment is skipped — a box bound measured
 * no cheaper than the read it would spare on the shipped zones (2026-10-28 profile).
 */
internal fun ringDistanceM(ring: List<LatLng>, p: LatLng): Double {
    var nearest = Double.MAX_VALUE
    for (i in 0 until ring.size - 1) {
        nearest = min(nearest, SpatialOperations.pointToSegmentDistance(p, ring[i], ring[i + 1]))
    }
    return nearest
}

/**
 * **A set of zones with their boxes settled once** — the per-point reads above, each asked thousands of
 * times along one line by the clock, the price field and the share split, with a bounding-box reject
 * ahead of every ring walk. [SpeedZone.bbox] rescans the ring, so a per-call box would cost what it
 * saves; here each box is computed once per set, and the set lives as long as the arm that built it.
 *
 * **Every answer is the plain read's own, exactly.** A point outside a ring's box is outside the ring, so
 * the containment reads skip it with the same answer; a distance read skips a zone only where a true
 * lower bound on its distance ([bboxDistanceLowerBoundM]) already proves it cannot lower the result.
 * The excluded ids are the caller's business — a set is built over zones already filtered.
 */
class ZoneSet(val zones: List<SpeedZone>) {

    private val boxes: List<BBox> = zones.map { it.bbox() }

    /** Whether the set holds nothing — every read then answers at once. */
    val isEmpty: Boolean get() = zones.isEmpty()

    /** [strictestLimitKnAt] over this set — the price law's read, boxes rejected first. */
    fun strictestLimitKnAt(latitude: Double, longitude: Double): Double? {
        var strictest: Double? = null
        for (i in zones.indices) {
            if (!inBox(boxes[i], latitude, longitude)) continue
            val zone = zones[i]
            if (zone.contains(latitude, longitude) && (strictest == null || zone.routeLimitKn < strictest)) {
                strictest = zone.routeLimitKn
            }
        }
        return strictest
    }

    /** [strictestDisplayLimitKnAt] over this set — the clock's read, boxes rejected first. */
    fun strictestDisplayLimitKnAt(latitude: Double, longitude: Double): Double? {
        var strictest: Double? = null
        for (i in zones.indices) {
            if (!inBox(boxes[i], latitude, longitude)) continue
            val zone = zones[i]
            if (zone.contains(latitude, longitude) && (strictest == null || zone.speedLimitKn < strictest)) {
                strictest = zone.speedLimitKn
            }
        }
        return strictest
    }

    /** Whether any zone of the set contains the point — the share split's own interior read. */
    fun containsAny(latitude: Double, longitude: Double): Boolean {
        for (i in zones.indices) {
            if (inBox(boxes[i], latitude, longitude) && zones[i].contains(latitude, longitude)) return true
        }
        return false
    }

    /** [speedZoneCollarLimitKnAt] over this set — a zone further than [marginM] from the point is never walked. */
    fun collarLimitKnAt(latitude: Double, longitude: Double, marginM: Double): Double? {
        val p = LatLng(latitude, longitude)
        var strictest: Double? = null
        for (i in zones.indices) {
            val zone = zones[i]
            val outer = zone.outerRing
            if (outer.size < 2) continue
            if (strictest != null && zone.routeLimitKn >= strictest) continue
            if (bboxDistanceLowerBoundM(boxes[i], latitude, longitude) > marginM) continue
            if (ringDistanceM(outer, p) <= marginM) strictest = zone.routeLimitKn
        }
        return strictest
    }

    /**
     * [speedZonePriceClearanceM] over this set — a zone whose box already stands further than the running
     * answer plus [marginM] cannot lower it (its edge is at least that far, and so is its collar's), so its
     * rings are never walked.
     */
    fun priceClearanceM(latitude: Double, longitude: Double, marginM: Double): Double {
        val p = LatLng(latitude, longitude)
        var nearest = Double.MAX_VALUE
        for (i in zones.indices) {
            if (nearest < Double.MAX_VALUE && bboxDistanceLowerBoundM(boxes[i], latitude, longitude) - marginM >= nearest) continue
            val zone = zones[i]
            nearest = min(nearest, ringArmClearanceM(zone.outerRing, marginM, p))
            for (hole in zone.holes) {
                nearest = min(nearest, ringArmClearanceM(hole, marginM, p))
            }
        }
        return nearest
    }

    private fun inBox(box: BBox, latitude: Double, longitude: Double): Boolean =
        latitude >= box.latSouth && latitude <= box.latNorth && longitude >= box.lonWest && longitude <= box.lonEast

    companion object {
        /** The empty set — every read answers nothing at once. */
        val EMPTY = ZoneSet(emptyList())
    }
}

/**
 * **A true lower bound (m) on the distance from a point to anything inside [box]**, under the same local
 * projection [SpatialOperations.pointToSegmentDistance] measures with. That read scales longitude by the
 * cosine of the mid-latitude of the three points it is handed, a latitude that lies between the lowest and
 * highest of them; this bound scales by the cosine of the **highest** absolute latitude the point and the
 * box reach, which is never larger, and trims a thousandth off for the arithmetic — so a segment inside the
 * box can never measure nearer than this bound says.
 */
internal fun bboxDistanceLowerBoundM(box: BBox, latitude: Double, longitude: Double): Double {
    val dLatDeg = max(0.0, max(box.latSouth - latitude, latitude - box.latNorth))
    val dLonDeg = max(0.0, max(box.lonWest - longitude, longitude - box.lonEast))
    if (dLatDeg == 0.0 && dLonDeg == 0.0) return 0.0
    val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * Math.PI / 180.0
    val farthestAbsLat = max(abs(latitude), max(abs(box.latSouth), abs(box.latNorth)))
    val cosMin = Math.cos(Math.toRadians(farthestAbsLat)).coerceAtLeast(0.0)
    val dy = dLatDeg * mPerDegLat
    val dx = dLonDeg * mPerDegLat * cosMin
    return Math.sqrt(dx * dx + dy * dy) * 0.999
}

/** Whether the emitted line enters [zone], sampled at [stepM] along every leg. */
fun lineEntersZone(waypoints: List<LatLng>, zone: SpeedZone, stepM: Double = 25.0): Boolean {
    if (waypoints.any { zone.contains(it.latitude, it.longitude) }) return true
    val box = zone.bbox()
    for (i in 0 until waypoints.size - 1) {
        val a = waypoints[i]
        val b = waypoints[i + 1]
        // A sample is lat/lon-linear on the chord, so a leg whose own box misses the ring's holds none inside.
        if (max(a.latitude, b.latitude) < box.latSouth || min(a.latitude, b.latitude) > box.latNorth ||
            max(a.longitude, b.longitude) < box.lonWest || min(a.longitude, b.longitude) > box.lonEast
        ) continue
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

/** The zones [waypoints] enters — the one entry test the report's two facts both stand on. */
private fun zonesEnteredBy(waypoints: List<LatLng>, zones: List<SpeedZone>): List<SpeedZone> =
    zones.filter { lineEntersZone(waypoints, it) }

/**
 * The names of the **priced** zones the line enters, reported only when no avoiding path exists — an
 * ordinary priced crossing with a way around reports nothing. A zone the price law charges nothing for
 * — its `speedRoute` override at or above the pace — is no restrictive zone at the caller and is never
 * named here: [crossedSlowZoneNames] is the fact that reads the water instead of the price.
 */
fun forcedCrossingZoneNames(
    waypoints: List<LatLng>,
    zones: List<SpeedZone>,
    avoidingPathExists: Boolean
): List<String> =
    if (avoidingPathExists) emptyList()
    else zonesEnteredBy(waypoints, zones).map { it.name }

/**
 * **The zones the drawn line enters whose own limit in force is below the cruise pace** — what the
 * water was signed at where the line crossed it, and deliberately **not** what the search was charged
 * for that water.
 *
 * The limit read is the **displayed** one, [`SpeedZone.speedLimitKn`] — the field
 * [strictestDisplayLimitKnAt] reads and therefore the value the clock, the line's colour and the
 * slow-limit attribution obey. It is never [`SpeedZone.routeLimitKn`], the routing-only `speedRoute`
 * override the price law stands on, and never the price itself: a zone the search was allowed to treat
 * as free keeps slowing the boat, so it is named here while [forcedCrossingZoneNames] names nothing. A
 * zone whose own limit is **at or above the pace** costs no second and shows in no colour, so it is
 * named by neither fact.
 *
 * The entry test is [forcedCrossingZoneNames]'s own ([zonesEnteredBy]), so the two facts can never
 * disagree about which water the line entered. Empty where the line enters no water slower than the pace.
 */
fun crossedSlowZoneNames(
    waypoints: List<LatLng>,
    zones: List<SpeedZone>,
    paceKn: Double
): List<String> = zonesEnteredBy(waypoints, zones)
    .filter { it.speedLimitKn < paceKn }
    .map { it.name }
