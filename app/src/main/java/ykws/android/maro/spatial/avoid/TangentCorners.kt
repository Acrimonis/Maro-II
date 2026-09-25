package ykws.android.maro.spatial.avoid

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.LandRingOrientation
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToLong
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

    private data class Key(val lat: Long, val lon: Long)

    /** Every convex corner of [openCoast] and of the CCW rings in [edges], offset by [offsetM]. */
    fun corners(
        edges: List<AvoidEdge>,
        openCoast: List<List<LatLng>>,
        offsetM: Double
    ): List<LatLng> {
        val out = ArrayList<LatLng>()
        for (polyline in openCoast) {
            for (i in 1 until polyline.size - 1) {
                addTangent(polyline[i - 1], polyline[i], polyline[i + 1], offsetM, out)
            }
        }

        val ringEdges = ArrayList<AvoidEdge>(edges.size)
        for (edge in edges) if (edge.orientation == LandRingOrientation.CCW_RING) ringEdges.add(edge)
        if (ringEdges.isEmpty()) return out

        val incoming = HashMap<Key, AvoidEdge>()
        val outgoing = HashMap<Key, AvoidEdge>()
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
