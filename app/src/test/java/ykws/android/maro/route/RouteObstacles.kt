package ykws.android.maro.route

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min

/** Which boundary of a speed zone a ring came from — a hole is navigable water, not an obstacle. */
enum class RingKind { ZONE_OUTER, ZONE_HOLE }

/** A closed boundary cut into straight pieces. */
data class RouteRing(
    val name: String,
    val kind: RingKind,
    /** Distinct vertices, the closing point not repeated. */
    val points: List<LatLng>,
    val segments: List<RouteSegment>,
)

/**
 * The obstacle set of a corridor — plan §10.2 and §10.3 steps 2–3.
 *
 * Two kinds of geometry, and the distinction is the whole of §10.1: the **exclusion** set (land and
 * the < 2 m contour, dilated by the standoff) can never be crossed, while a **zone ring** is only a
 * boundary the taut string is allowed to bend on — a zone stays crossable, at a price (§11.2).
 * Both block *visibility*, which is what makes the path bend at a boundary vertex instead of cutting
 * an arbitrary chord across it; the cost then prices the crossing the geometry allowed.
 *
 * The primitive is the existing one: [`segmentsIntersect()`](app/src/main/java/ykws/android/maro/spatial/SpatialOperations.kt:139).
 * It treats collinear segments as non-intersecting, so a leg may run *along* a boundary — which is
 * exactly what a taut path hugging the standoff margin does. [RouteBarrierIndex] adds one rule the
 * raw primitive cannot express: an intersection at a shared endpoint — the vertex the leg starts
 * from, where the two adjacent boundary segments meet — is a touch, not a block.
 */
class RouteObstacles(
    val exclusion: RouteExclusion,
    val rings: List<RouteRing>,
    /** Every straight piece a leg may not cross. */
    val barriers: List<RouteSegment>,
    /** Graph nodes: boundary vertices inside the corridor. */
    val vertices: List<LatLng>,
) {

    /** Segments a line of sight is tested against, bucketed so a query touches only nearby ones. */
    val index: RouteBarrierIndex = RouteBarrierIndex(exclusion.bbox, barriers)

    companion object {

        /**
         * Harvests the corridor's vertices and barriers from the exclusion boundary and the zone
         * rings (plan §10.3 step 2). Ring vertices are simplified with the same tolerance as the
         * contours, and only rings that reach the corridor are kept.
         */
        fun harvest(
            exclusion: RouteExclusion,
            zones: List<SpeedZone>,
            config: RouteConfig,
        ): RouteObstacles {
            val corridor = exclusion.bbox
            val marginDeg = RouteConfig.latDegForM(config.standoffM * 2.0)
            val rings = ArrayList<RouteRing>()

            for (zone in zones) {
                val outer = openRing(zone.outerRing)
                if (outer.size >= 3 && reachesCorridor(outer, corridor, marginDeg)) {
                    rings.add(buildRing("${zone.name} · ${zone.speedLimitKn} kn", RingKind.ZONE_OUTER, outer, config))
                }
                for ((i, hole) in zone.holes.withIndex()) {
                    val pts = openRing(hole)
                    if (pts.size >= 3 && reachesCorridor(pts, corridor, marginDeg)) {
                        rings.add(buildRing("${zone.name} hole ${i + 1}", RingKind.ZONE_HOLE, pts, config))
                    }
                }
            }

            val barriers = ArrayList<RouteSegment>(exclusion.segments.size + rings.size * 4)
            barriers.addAll(exclusion.segments)
            rings.forEach { barriers.addAll(it.segments) }

            // Only the corridor's own vertices become nodes: a ring that runs past the corridor
            // still blocks, but its far side is not a graph node the search should consider.
            val vertices = LinkedHashSet<LatLng>()
            exclusion.vertices.forEach { if (RouteCorridor.contains(corridor, it)) vertices.add(it) }
            rings.forEach { ring -> ring.points.forEach { if (RouteCorridor.contains(corridor, it)) vertices.add(it) } }

            return RouteObstacles(
                exclusion = exclusion,
                rings = rings,
                barriers = barriers,
                vertices = vertices.toList(),
            )
        }

        /** A ring as given: the closing point, if the source repeated it, is dropped. */
        private fun openRing(points: List<LatLng>): List<LatLng> =
            if (points.size >= 2 && points.first() == points.last()) points.dropLast(1) else points

        private fun reachesCorridor(points: List<LatLng>, corridor: BBox, marginDeg: Double): Boolean =
            points.any { RouteCorridor.contains(corridor, it, marginDeg) }

        /** Simplifies a ring as a closed curve and cuts it into segments. */
        private fun buildRing(
            name: String,
            kind: RingKind,
            points: List<LatLng>,
            config: RouteConfig,
        ): RouteRing {
            // Douglas-Peucker keeps the first and last point, so the closing point survives it;
            // dropping it leaves a ring the wrap-around below closes without a duplicate vertex.
            val open = SpatialOperations
                .douglasPeucker(points + points.first(), config.harvestEpsilonM)
                .dropLast(1)
            val segments = ArrayList<RouteSegment>(open.size)
            for (i in open.indices) segments.add(RouteSegment(open[i], open[(i + 1) % open.size]))
            return RouteRing(name, kind, open, segments)
        }
    }
}

/**
 * A uniform-grid index over barrier segments, and the visibility primitive the graph is built on.
 *
 * A crossing **blocks**; a touch at an endpoint does not, so a leg may leave a boundary vertex
 * without being blocked by the two segments that meet there. The tolerance is stated in metres and
 * applied on both segments, so a genuine crossing cannot hide inside it.
 */
class RouteBarrierIndex(
    val bbox: BBox,
    private val segments: List<RouteSegment>,
    cellMetres: Double = 250.0,
) {

    private val cellLatDeg = RouteConfig.latDegForM(cellMetres)
    private val cellLonDeg = RouteConfig.lonDegForM(cellMetres, (bbox.latSouth + bbox.latNorth) / 2.0)

    private val rowCount: Int = ceil((bbox.latNorth - bbox.latSouth) / cellLatDeg).toInt().coerceAtLeast(1)
    private val colCount: Int = ceil((bbox.lonEast - bbox.lonWest) / cellLonDeg).toInt().coerceAtLeast(1)

    private val buckets: Array<MutableList<Int>?> = arrayOfNulls(rowCount * colCount)

    init {
        for ((idx, seg) in segments.withIndex()) {
            val rLo = rowOf(min(seg.a.latitude, seg.b.latitude))
            val rHi = rowOf(maxOf(seg.a.latitude, seg.b.latitude))
            val cLo = colOf(min(seg.a.longitude, seg.b.longitude))
            val cHi = colOf(maxOf(seg.a.longitude, seg.b.longitude))
            for (r in rLo..rHi) for (c in cLo..cHi) {
                val k = r * colCount + c
                val list = buckets[k] ?: ArrayList<Int>(4).also { buckets[k] = it }
                list.add(idx)
            }
        }
    }

    private fun rowOf(lat: Double): Int =
        floor((lat - bbox.latSouth) / cellLatDeg).toInt().coerceIn(0, rowCount - 1)

    private fun colOf(lon: Double): Int =
        floor((lon - bbox.lonWest) / cellLonDeg).toInt().coerceIn(0, colCount - 1)

    val size: Int get() = segments.size

    /**
     * True when the straight line [a]→[b] crosses a barrier.
     *
     * @param ignore touches within [touchToleranceM] of either segment's end — the shared-endpoint
     *        rule above, and the reason a tangent path can bend exactly on the boundary.
     */
    fun blocked(a: LatLng, b: LatLng, touchToleranceM: Double = 0.5): Boolean {
        val rLo = rowOf(min(a.latitude, b.latitude))
        val rHi = rowOf(maxOf(a.latitude, b.latitude))
        val cLo = colOf(min(a.longitude, b.longitude))
        val cHi = colOf(maxOf(a.longitude, b.longitude))
        val seen = HashSet<Int>()
        for (r in rLo..rHi) for (c in cLo..cHi) {
            val list = buckets[r * colCount + c] ?: continue
            for (idx in list) {
                if (!seen.add(idx)) continue
                val seg = segments[idx]
                if (crossesProperly(a, b, seg.a, seg.b, touchToleranceM)) return true
            }
        }
        return false
    }

    private fun crossesProperly(
        a: LatLng,
        b: LatLng,
        c: LatLng,
        d: LatLng,
        toleranceM: Double,
    ): Boolean {
        val midLat = (a.latitude + b.latitude + c.latitude + d.latitude) / 4.0
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * Math.PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))

        val ax = a.longitude * mPerDegLon; val ay = a.latitude * mPerDegLat
        val bx = b.longitude * mPerDegLon; val by = b.latitude * mPerDegLat
        val cx = c.longitude * mPerDegLon; val cy = c.latitude * mPerDegLat
        val dx = d.longitude * mPerDegLon; val dy = d.latitude * mPerDegLat

        val rx = bx - ax; val ry = by - ay
        val sx = dx - cx; val sy = dy - cy
        val cross = rx * sy - ry * sx
        if (abs(cross) < 1e-12) return false // parallel or collinear: a leg may run along a boundary

        val qpx = cx - ax; val qpy = cy - ay
        val t = (qpx * sy - qpy * sx) / cross
        val u = (qpx * ry - qpy * rx) / cross

        val abLen = SpatialOperations.haversine(a, b)
        val cdLen = SpatialOperations.haversine(c, d)
        if (abLen <= 0.0 || cdLen <= 0.0) return false
        val tTol = toleranceM / abLen
        val uTol = toleranceM / cdLen
        return t in tTol..(1.0 - tTol) && u in uTol..(1.0 - uTol)
    }
}
