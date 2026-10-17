package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

/**
 * **What the drawn line gives the depth gate, read once per rung for the device's `LINE` line.** Two
 * figures, both in metres and both `NaN` where they cannot be read: the shallowest **known** depth on
 * the line, and the closest the line comes to the gate's own edge — the water the wall would block —
 * measured by an eight-bearing ring scan from the line's own samples.
 *
 * It is a **reading and nothing else**: no price, no clock and no state move with it, and the ray's own
 * budget is the trace's — the sink only builds the string while the `MaroRoute` tag is on, so the scan
 * runs on a device pass and never on a plain solve. An unsurveyed sample is skipped rather than read as
 * shallow, exactly as the gate itself treats a NoData sounding.
 */
internal object RouteDepthReading {

    /** The step the line is sampled at, metres — the clock's own floor and the gate's own scale. */
    private const val SAMPLE_STEP_M = 10.0

    /** How far one edge scan reaches, metres — past this a sample is treated as not near an edge. */
    private const val EDGE_SCAN_MAX_M = 200.0

    /** The edge scan's own ring spacing, metres. */
    private const val EDGE_SCAN_STEP_M = 5.0

    /** The eight bearings, in radians. */
    private val BEARINGS = DoubleArray(8) { it * PI / 4.0 }

    /** The shallowest depth the line's samples answer, or `NaN` where none of them is surveyed. */
    fun minDepthM(world: MultipassWorld, points: List<LatLng>): Double {
        var shallowest = Double.POSITIVE_INFINITY
        forEachSample(points) { p ->
            val depth = depthMAt(world, p)
            if (!depth.isNaN() && depth < shallowest) shallowest = depth
        }
        return if (shallowest == Double.POSITIVE_INFINITY) Double.NaN else shallowest
    }

    /**
     * The closest the line comes to the gate's edge, metres — the smallest, over its samples, of the
     * distance to the nearest point the gate would block. `NaN` where the gate is off or no sample
     * reaches an edge inside [EDGE_SCAN_MAX_M].
     */
    fun gateEdgeM(
        world: MultipassWorld,
        points: List<LatLng>,
        gateActive: Boolean,
        minDepthM: Double
    ): Double = edgeReading(world, points, gateActive, minDepthM, 0.0).distanceM

    /**
     * **The line's closest approach to the gate's edge, with the point named and the body separated.**
     * [distanceM]/[at] are the closest over the whole line; [bodyDistanceM]/[bodyAt] are the closest
     * over the **body** — the samples more than [excludeEndsM] from either end, the same end disc the
     * pull waives. Both are `NaN`, with a `NaN` coordinate, where the gate is off or nothing reaches an
     * edge inside [EDGE_SCAN_MAX_M].
     */
    internal data class EdgeReading(
        val distanceM: Double,
        val at: LatLng,
        val bodyDistanceM: Double,
        val bodyAt: LatLng
    )

    /** The closest approach to the gate's edge, whole line and body, each with its own point. */
    fun edgeReading(
        world: MultipassWorld,
        points: List<LatLng>,
        gateActive: Boolean,
        minDepthM: Double,
        excludeEndsM: Double
    ): EdgeReading {
        val nowhere = LatLng(Double.NaN, Double.NaN)
        if (!gateActive || points.isEmpty()) {
            return EdgeReading(Double.NaN, nowhere, Double.NaN, nowhere)
        }
        val start = points.first()
        val aim = points.last()
        var all = Double.POSITIVE_INFINITY
        var allAt = nowhere
        var body = Double.POSITIVE_INFINITY
        var bodyAt = nowhere
        forEachSample(points) { p ->
            val edge = edgeDistanceAt(world, p, minDepthM)
            if (edge < all) {
                all = edge
                allAt = p
            }
            if (SpatialOperations.haversine(start, p) > excludeEndsM &&
                SpatialOperations.haversine(p, aim) > excludeEndsM &&
                edge < body
            ) {
                body = edge
                bodyAt = p
            }
        }
        return EdgeReading(
            if (all == Double.POSITIVE_INFINITY) Double.NaN else all,
            allAt,
            if (body == Double.POSITIVE_INFINITY) Double.NaN else body,
            bodyAt
        )
    }

    /** The nearest blocked point from [p] over the eight bearings, or `POSITIVE_INFINITY`. */
    private fun edgeDistanceAt(world: MultipassWorld, p: LatLng, minDepthM: Double): Double {
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(p.latitude))
        var nearest = Double.POSITIVE_INFINITY
        for (bearing in BEARINGS) {
            val dLatPerM = cos(bearing) / mPerDegLat
            val dLonPerM = kotlin.math.sin(bearing) / mPerDegLon
            var stepM = EDGE_SCAN_STEP_M
            while (stepM <= EDGE_SCAN_MAX_M) {
                val q = LatLng(
                    p.latitude + stepM * dLatPerM,
                    p.longitude + stepM * dLonPerM
                )
                if (!depthClearsGate(depthMAt(world, q), minDepthM)) {
                    if (stepM < nearest) nearest = stepM
                    break
                }
                stepM += EDGE_SCAN_STEP_M
            }
        }
        return nearest
    }

    /** Every sampled point on the line: its first vertex, then each leg walked at [SAMPLE_STEP_M]. */
    private fun forEachSample(points: List<LatLng>, visit: (LatLng) -> Unit) {
        if (points.isEmpty()) return
        visit(points[0])
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val legM = SpatialOperations.haversine(a, b)
            val steps = max(1, (legM / SAMPLE_STEP_M).toInt())
            for (s in 1..steps) {
                val t = s.toDouble() / steps
                visit(
                    LatLng(
                        a.latitude + (b.latitude - a.latitude) * t,
                        a.longitude + (b.longitude - a.longitude) * t
                    )
                )
            }
        }
    }
}
