package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.LandRingOrientation
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * **The tangent corners** — the convex vertices of the harvested coastline, offset by the margin on
 * the water side, which are the only points a taut string bends at. The grid A* owns the route's
 * order; these corners own the exact points, which the engine snaps its pulled bends onto.
 *
 * - **Convex only.** The sign convention is the coastline's own: land on the left of the direction of
 *   travel, water on the right, so a convex land corner is a negative cross product of the two
 *   vertex-to-neighbour vectors. Concave vertices produce nothing — the string never bends at them.
 * - **Offset by the margin.** Each corner is pushed along its interior-angle bisector to the distance
 *   that stands [offsetM] off both incident edges.
 * - **Rings and open coast alike.** Ring vertices are rebuilt from the flat CCW edge set — one
 *   incoming and one outgoing edge per vertex — while a CW basin keeps its interior water, so its
 *   wall corners carry no tangent.
 */
object TangentCorners {

    /** Hair beyond the margin (relative) so an edge hugging the offset line never trips the strict
     *  `< marginM` sampling comparison through floating-point noise. */
    private const val OFFSET_EPSILON = 1e-6

    /** Collinearity and half-angle guards, in metres and radians. A corner flatter than
     *  [MIN_SIN_HALF] is not a corner, so notch noise never reaches the snap. */
    private const val MIN_EDGE_M = 1e-9
    private const val MIN_SIN_HALF = 0.05

    /** The furthest an offset corner may stand from its vertex before the corner is dropped — a
     *  shallow cape must not push a corner kilometres out. */
    private const val MAX_OFFSET_M = 2000.0

    /** A marker body's outline is sampled at least this many times and at most this many, whatever its size. */
    private const val BODY_MIN_CORNERS = 8
    private const val BODY_MAX_CORNERS = 64

    private data class Key(val lat: Long, val lon: Long)

    /** Every convex corner of [openCoast] and of the CCW rings in [edges], offset by [offsetM]. */
    fun corners(
        edges: List<MultipassEdge>,
        openCoast: List<List<LatLng>>,
        offsetM: Double
    ): List<LatLng> {
        val out = ArrayList<LatLng>()
        for (polyline in openCoast) {
            for (i in 1 until polyline.size - 1) {
                addTangent(polyline[i - 1], polyline[i], polyline[i + 1], offsetM, out)
            }
        }

        val ringEdges = ArrayList<MultipassEdge>(edges.size)
        for (edge in edges) if (edge.orientation == LandRingOrientation.CCW_RING) ringEdges.add(edge)
        if (ringEdges.isEmpty()) return out

        val incoming = HashMap<Key, MultipassEdge>()
        val outgoing = HashMap<Key, MultipassEdge>()
        val ambiguous = HashSet<Key>()
        for (edge in ringEdges) {
            val ka = keyOf(edge.a)
            val kb = keyOf(edge.b)
            if (outgoing.put(ka, edge) != null) ambiguous.add(ka)
            if (incoming.put(kb, edge) != null) ambiguous.add(kb)
        }
        for ((key, inEdge) in incoming) {
            if (key in ambiguous) continue
            val outEdge = outgoing[key] ?: continue
            addTangent(inEdge.a, inEdge.b, outEdge.b, offsetM, out)
        }
        return out
    }

    /**
     * The hug set: every convex corner of each speed-zone outer ring, offset outward by [offsetM],
     * read from the ring's own signed area so a CW ring offsets to the same water side as a CCW one.
     */
    fun ringCorners(zones: List<SpeedZone>, offsetM: Double): List<LatLng> {
        val out = ArrayList<LatLng>()
        for (zone in zones) {
            val closed = normalizeCcw(zone.outerRing)
            // Drop the closing duplicate, then walk every vertex with wrap-around so the closing
            // corner is a candidate exactly once.
            val ring = if (closed.size > 1 && closed.first() == closed.last()) closed.dropLast(1) else closed
            val n = ring.size
            if (n < 3) continue
            for (i in 0 until n) {
                addTangent(ring[(i + n - 1) % n], ring[i], ring[(i + 1) % n], offsetM, out)
            }
        }
        return out
    }

    /**
     * **The body corners of the costed markers** — points on each marker's **body** outline, stood
     * [offsetM] off it, which are the bends a line hugging the body would hold. A circle's body is its
     * ring, a corridor's the stadium of its two sides and two cap half-circles, and a pin — a circle of
     * radius `0` — has no body and yields nothing: it leans only. A wall (grade 10) is a body like any
     * other, and the one with the most need of a hug, since a rastered wall is not a coast edge and the
     * coast set never sees it.
     *
     * The outline is sampled every [cellM] along its length, floored at [BODY_MIN_CORNERS] and capped at
     * [BODY_MAX_CORNERS]; where the cap bites the spacing grows to the perimeter over the cap. The first
     * sample stands due north of a circle's centre and at `p1`'s cap apex for a corridor, so the set is the
     * same for the same marker whatever asked for it. Each corner carries its marker's own collar as the
     * reach a bend is offered it within ([BodyCorner.reachM]).
     */
    internal fun bodyCorners(markers: List<RouteMarker>, offsetM: Double, cellM: Double): List<BodyCorner> {
        val out = ArrayList<BodyCorner>()
        for (m in markers) {
            when (val g = m.geometry) {
                is RouteMarkerGeometry.Circle -> {
                    if (g.radiusM <= 0.0) continue
                    val r = g.radiusM + offsetM
                    val n = sampleCount(2.0 * PI * r, cellM)
                    for (i in 0 until n) {
                        val theta = 2.0 * PI * i / n
                        out.add(BodyCorner(offsetFrom(g.center, r * sin(theta), r * cos(theta)), g.proximityM))
                    }
                }
                is RouteMarkerGeometry.Corridor -> stadiumCorners(g, offsetM, cellM, out)
            }
        }
        return out
    }

    /**
     * The stadium outline of a corridor's body, offset by [offsetM] and sampled by arc length from `p1`'s
     * cap apex: `p1`'s cap to its right side, the right side to `p2`, `p2`'s cap, the left side back, and
     * the rest of `p1`'s cap. A corridor whose two ends coincide is a circle and is sampled as one.
     */
    private fun stadiumCorners(
        g: RouteMarkerGeometry.Corridor,
        offsetM: Double,
        cellM: Double,
        out: MutableList<BodyCorner>
    ) {
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(g.p1.latitude))
        val dx = (g.p2.longitude - g.p1.longitude) * mPerDegLon
        val dy = (g.p2.latitude - g.p1.latitude) * mPerDegLat
        val length = hypot(dx, dy)
        val radius = g.widthM / 2.0 + offsetM
        if (length < MIN_EDGE_M) {
            val n = sampleCount(2.0 * PI * radius, cellM)
            for (i in 0 until n) {
                val theta = 2.0 * PI * i / n
                out.add(BodyCorner(offsetFrom(g.p1, radius * sin(theta), radius * cos(theta)), g.proximityM))
            }
            return
        }
        val ux = dx / length
        val uy = dy / length
        // The left normal of travel `p1 → p2`.
        val nx = -uy
        val ny = ux
        val cap = PI * radius
        val halfCap = cap / 2.0
        val perimeter = 2.0 * length + 2.0 * cap
        val n = sampleCount(perimeter, cellM)
        for (i in 0 until n) {
            var s = perimeter * i / n
            val x: Double
            val y: Double
            if (s < halfCap) {
                // `p1`'s cap, from the apex (behind `p1`) round to the right side.
                val theta = PI + s / radius
                x = radius * (cos(theta) * ux + sin(theta) * nx)
                y = radius * (cos(theta) * uy + sin(theta) * ny)
            } else if ((s - halfCap).also { s = it } < length) {
                // The right side, `p1` to `p2`.
                x = -radius * nx + s * ux
                y = -radius * ny + s * uy
            } else if ((s - length).also { s = it } < cap) {
                // `p2`'s cap, right side round to the left side.
                val theta = -PI / 2.0 + s / radius
                x = dx + radius * (cos(theta) * ux + sin(theta) * nx)
                y = dy + radius * (cos(theta) * uy + sin(theta) * ny)
            } else if ((s - cap).also { s = it } < length) {
                // The left side, `p2` back to `p1`.
                x = dx + radius * nx - s * ux
                y = dy + radius * ny - s * uy
            } else {
                // The rest of `p1`'s cap, left side round to the apex.
                val theta = PI / 2.0 + (s - length) / radius
                x = radius * (cos(theta) * ux + sin(theta) * nx)
                y = radius * (cos(theta) * uy + sin(theta) * ny)
            }
            out.add(BodyCorner(offsetFrom(g.p1, x, y), g.proximityM))
        }
    }

    /** How many samples an outline of [perimeterM] takes at one per [cellM], within the body's floor and cap. */
    private fun sampleCount(perimeterM: Double, cellM: Double): Int =
        ceil(perimeterM / cellM.coerceAtLeast(MIN_EDGE_M)).toInt().coerceIn(BODY_MIN_CORNERS, BODY_MAX_CORNERS)

    /** The point [eastM] east and [northM] north of [origin], in the same planar read [addTangent] makes. */
    private fun offsetFrom(origin: LatLng, eastM: Double, northM: Double): LatLng {
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(origin.latitude))
        return LatLng(origin.latitude + northM / mPerDegLat, origin.longitude + eastM / mPerDegLon)
    }

    /** The ring read counter-clockwise, so the offset faces the water (outside) side either way. */
    private fun normalizeCcw(ring: List<LatLng>): List<LatLng> =
        if (ring.size < 3 || signedArea(ring) >= 0.0) ring else ring.reversed()

    /** Shoelace signed area (deg² × 2) — positive for CCW, negative for CW; closed or open ring alike. */
    private fun signedArea(ring: List<LatLng>): Double {
        var s = 0.0
        var prev = ring.last()
        for (v in ring) {
            s += prev.longitude * v.latitude - v.longitude * prev.latitude
            prev = v
        }
        return s
    }

    private fun keyOf(p: LatLng): Key =
        Key((p.latitude / 1e-9).roundToLong(), (p.longitude / 1e-9).roundToLong())

    private fun addTangent(
        prev: LatLng,
        vertex: LatLng,
        next: LatLng,
        offsetM: Double,
        out: MutableList<LatLng>
    ) {
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(vertex.latitude))
        val px = (prev.longitude - vertex.longitude) * mPerDegLon
        val py = (prev.latitude - vertex.latitude) * mPerDegLat
        val nx = (next.longitude - vertex.longitude) * mPerDegLon
        val ny = (next.latitude - vertex.latitude) * mPerDegLat
        // Land on the left of travel: a negative cross product is the small land wedge — a convex
        // corner the string bends at. Concave (≥ 0) corners yield no tangent.
        val cross = px * ny - py * nx
        if (cross >= 0.0) return
        val pl = hypot(px, py)
        val nl = hypot(nx, ny)
        if (pl < MIN_EDGE_M || nl < MIN_EDGE_M) return
        val ux = px / pl
        val uy = py / pl
        val vx = nx / nl
        val vy = ny / nl
        val cosAngle = (ux * vx + uy * vy).coerceIn(-1.0, 1.0)
        val sinHalf = sqrt((1.0 - cosAngle) / 2.0)
        if (sinHalf < MIN_SIN_HALF) return
        // The land bisector (unit sum of the two vertex-to-neighbour directions), then out to the
        // water side at the distance that stands the margin off both incident edges.
        var bx = ux + vx
        var by = uy + vy
        val bl = hypot(bx, by)
        if (bl < MIN_EDGE_M) return
        bx /= bl
        by /= bl
        val offsetDistance = offsetM / sinHalf
        if (offsetDistance > MAX_OFFSET_M) return
        val d = offsetDistance * (1.0 + OFFSET_EPSILON)
        out.add(
            LatLng(
                vertex.latitude - d * by / mPerDegLat,
                vertex.longitude - d * bx / mPerDegLon
            )
        )
    }
}
