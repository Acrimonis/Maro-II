package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

/**
 * **What the drawn line gives the depth gate, read once per rung for the device's `LINE` line.** The
 * figures are in metres and `NaN` where they cannot be read: the shallowest **known** depth on the line;
 * the closest the line comes to the gate's own edge — the water the wall would block — measured by an
 * eight-bearing ring scan from the line's own samples, an auditor that knows nothing of the traced line;
 * and, where a [GateRule] is handed, the same closest approach read off the traced line itself every
 * metre, beside the metres run inside the gate's preferred standoff.
 *
 * It is a **reading and nothing else**: no price, no clock and no state move with it, and the ray's own
 * budget is the trace's — the sink only builds the string while the `MaroRoute` tag is on, so the scan
 * runs on a device pass and never on a plain solve. An unsurveyed sample is skipped rather than read as
 * shallow, exactly as the gate itself treats a NoData sounding, and a sample holding no real sounding — the
 * router's assumed depth ([`DepthGrid.depthAtFilled`]) — is skipped too. A sample blending a real sounding
 * with assumed ones reads the blend under the real node's source.
 */
internal object RouteDepthReading {

    /** The step the line is sampled at, metres — the clock's own floor and the gate's own scale. */
    private const val SAMPLE_STEP_M = 10.0

    /**
     * The step the traced line's own reading samples at, metres — fine enough that a breach of the wall
     * cannot hide between two samples by more than half of it.
     */
    private const val INDEX_SAMPLE_STEP_M = 1.0

    /** How far one edge scan reaches, metres — past this a sample is treated as not near an edge. */
    private const val EDGE_SCAN_MAX_M = 200.0

    /** The edge scan's own ring spacing, metres. */
    private const val EDGE_SCAN_STEP_M = 5.0

    /** The eight bearings, in radians. */
    private val BEARINGS = DoubleArray(8) { it * PI / 4.0 }

    /** The shallowest depth the line's samples answer, or `NaN` where none of them is surveyed. */
    fun minDepthM(world: MultipassWorld, points: List<LatLng>): Double = minDepthAt(world, points).first

    /** [minDepthM] with the sample it was read at — `NaN` and `null` where no sample holds a depth. */
    fun minDepthAt(world: MultipassWorld, points: List<LatLng>): Pair<Double, LatLng?> {
        var shallowest = Double.POSITIVE_INFINITY
        var at: LatLng? = null
        forEachSample(points) { p ->
            val sample = world.depthAt(p.latitude, p.longitude)
            val depth = if (sample.source == DepthSource.NONE) Double.NaN else depthMOf(sample)
            if (!depth.isNaN() && depth < shallowest) {
                shallowest = depth
                at = p
            }
        }
        return if (shallowest == Double.POSITIVE_INFINITY) Double.NaN to null else shallowest to at
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
     * over the **body** — the samples outside the gate's own waiver: more than `excludeEndsM` from either
     * end. Both are `NaN`, with a `NaN` coordinate, where the gate is off or
     * nothing reaches an edge inside [EDGE_SCAN_MAX_M].
     *
     * These two are the **independent auditor** — an eight-bearing scan of the depth itself, which knows
     * nothing of the traced line. Beside them stand the traced line's own readings where a [GateRule] is
     * handed: [indexDistanceM] and [indexBodyDistanceM], the same closest approaches read off the edge index
     * (the pad taken off), and [innerM], the metres of body closer than the gate's preferred standoff. The
     * auditor reading well under the index is the index wrong; the index's body reading under the wall is a
     * breach — both bug reports, neither a repair.
     */
    internal data class EdgeReading(
        val distanceM: Double,
        val at: LatLng,
        val bodyDistanceM: Double,
        val bodyAt: LatLng,
        val indexDistanceM: Double = Double.NaN,
        val indexBodyDistanceM: Double = Double.NaN,
        val innerM: Double = Double.NaN
    )

    /** The closest approach to the gate's edge, whole line and body, each with its own point. */
    fun edgeReading(
        world: MultipassWorld,
        points: List<LatLng>,
        gateActive: Boolean,
        minDepthM: Double,
        excludeEndsM: Double,
        gate: GateRule? = null
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
        var indexAll = Double.POSITIVE_INFINITY
        var indexBody = Double.POSITIVE_INFINITY
        var innerM = 0.0
        forEachSample(points) { p ->
            val edge = edgeDistanceAt(world, p, minDepthM)
            if (edge < all) {
                all = edge
                allAt = p
            }
            val inBody = if (gate != null) !gate.waives(p, start, aim)
                else !MultipassPull.marginWaived(p, excludeEndsM, start, aim)
            if (inBody && edge < body) {
                body = edge
                bodyAt = p
            }
        }
        if (gate != null) {
            // The traced line's own reading: each leg's closest approach exactly, and its points walked every
            // metre only where the leg comes inside the preferred standoff or near an end's waiver — the rest
            // of the line is open water whose leg reading is its body reading.
            for (i in 1 until points.size) {
                val a = points[i - 1]
                val b = points[i]
                val leg = gate.segmentEdgeM(a, b, EDGE_SCAN_MAX_M)
                if (leg < indexAll) indexAll = leg
                val nearEnds = SpatialOperations.pointToSegmentDistance(start, a, b) <= excludeEndsM ||
                    SpatialOperations.pointToSegmentDistance(aim, a, b) <= excludeEndsM
                if (leg >= gate.preferredM && !nearEnds) {
                    if (leg < indexBody) indexBody = leg
                    continue
                }
                forEachSample(listOf(a, b), INDEX_SAMPLE_STEP_M) { p ->
                    if (gate.waives(p, start, aim)) return@forEachSample
                    val indexed = gate.edgeM(p, EDGE_SCAN_MAX_M)
                    if (indexed < indexBody) indexBody = indexed
                    if (indexed < gate.preferredM) innerM += INDEX_SAMPLE_STEP_M
                }
            }
        }
        fun orNaN(v: Double) = if (v == Double.POSITIVE_INFINITY || v == Double.MAX_VALUE) Double.NaN else v
        return EdgeReading(
            orNaN(all), allAt, orNaN(body), bodyAt,
            indexDistanceM = if (gate == null) Double.NaN else orNaN(indexAll),
            indexBodyDistanceM = if (gate == null) Double.NaN else orNaN(indexBody),
            innerM = if (gate == null) Double.NaN else innerM
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

    /** Every sampled point on the line: its first vertex, then each leg walked at [stepM]. */
    private fun forEachSample(points: List<LatLng>, stepM: Double = SAMPLE_STEP_M, visit: (LatLng) -> Unit) {
        if (points.isEmpty()) return
        visit(points[0])
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val legM = SpatialOperations.haversine(a, b)
            val steps = max(1, (legM / stepM).toInt())
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
