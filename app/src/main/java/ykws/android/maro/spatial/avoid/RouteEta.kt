package ykws.android.maro.spatial.avoid

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The zone-aware ETA: the drawn line re-vertexed where the limit in force changes, then timed leg by
 * leg. Inside a zone the boat obeys the strictest limit (never above the configured pace), outside it
 * accelerates back to the configured pace on the simple ramp; entering a slower zone decelerates
 * instantly, the ramp being the plan's "gradual acceleration outside" and nothing more.
 */

/** The simple-ramp acceleration (m/s²) the ETA climbs back to pace with after leaving a zone. */
const val ZONE_EXIT_RAMP_MPS2 = 0.5

/** Sampling step (m) the boundary splitter walks each leg at — half the shipped grid cell. */
private const val BOUNDARY_SAMPLE_M = 25.0

/** A polyline split at limit changes, with one planned time per split leg. */
data class TimedLine(
    val points: List<LatLng>,
    val legTimesSec: List<Double>
) {
    val durationSec: Double get() = legTimesSec.sum()
}

/**
 * Splits [waypoints] where [limitKnAt] changes and times each split leg: inside a zone the strictest
 * limit binds, outside the pace binds, and a limit rise is climbed on the simple ramp.
 */
fun timeLineWithLimits(
    waypoints: List<LatLng>,
    paceKn: Double,
    limitKnAt: (LatLng) -> Double?
): TimedLine {
    if (waypoints.size < 2) return TimedLine(waypoints, emptyList())
    val paceMps = Units.knotsToMps(paceKn)
    val points = splitAtLimitChanges(waypoints, limitKnAt)
    val times = ArrayList<Double>(points.size - 1)
    var carriedMps = paceMps
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        val dist = SpatialOperations.haversine(a, b)
        val limitKn = limitKnAt(midpoint(a, b))
        val targetMps = if (limitKn != null) min(Units.knotsToMps(limitKn), paceMps) else paceMps
        val timed = segmentTimeM(dist, carriedMps, targetMps)
        times.add(timed.first)
        carriedMps = timed.second
    }
    return TimedLine(points, times)
}

/** Walks every leg and inserts the boundary vertices where [limitKnAt] changes. */
private fun splitAtLimitChanges(
    waypoints: List<LatLng>,
    limitKnAt: (LatLng) -> Double?
): List<LatLng> {
    val out = ArrayList<LatLng>(waypoints.size + 8)
    out.add(waypoints.first())
    for (i in 0 until waypoints.size - 1) {
        val sub = splitLeg(waypoints[i], waypoints[i + 1], limitKnAt)
        for (j in 1 until sub.size) out.add(sub[j])
    }
    return out
}

/** Splits one leg into `[a, ...crossings..., b]` by sampling then bisecting at each limit change. */
private fun splitLeg(a: LatLng, b: LatLng, limitKnAt: (LatLng) -> Double?): List<LatLng> {
    val dist = SpatialOperations.haversine(a, b)
    if (dist <= 1e-9) return listOf(a, b)
    val out = ArrayList<LatLng>(4)
    out.add(a)
    var prevT = 0.0
    var prevLimit = limitKnAt(a)
    val steps = ceil(dist / BOUNDARY_SAMPLE_M).toInt().coerceAtLeast(2)
    for (s in 1..steps) {
        val t = s.toDouble() / steps
        val p = interpolate(a, b, t)
        val limit = limitKnAt(p)
        if (limit != prevLimit) {
            val crossing = bisectLimitChange(a, b, prevT, t, prevLimit, limit, limitKnAt)
            if (out.last() != crossing) out.add(crossing)
            prevLimit = limit
        }
        prevT = t
    }
    if (out.last() != b) out.add(b)
    return out
}

/** Bisects the limit change between two samples to a vertex standing on the boundary. */
private fun bisectLimitChange(
    a: LatLng,
    b: LatLng,
    lo: Double,
    hi: Double,
    loLimit: Double?,
    hiLimit: Double?,
    limitKnAt: (LatLng) -> Double?
): LatLng {
    var l = lo
    var h = hi
    repeat(24) {
        val mid = (l + h) / 2.0
        val limit = limitKnAt(interpolate(a, b, mid))
        if (limit == loLimit) l = mid else h = mid
    }
    return interpolate(a, b, (l + h) / 2.0)
}

/**
 * Time one segment at constant target speed [v1] (m/s), starting at [v0] (m/s). A rise climbs the
 * simple ramp; a fall decelerates instantly to the target; returns (seconds, end speed m/s).
 */
private fun segmentTimeM(distanceM: Double, v0: Double, v1: Double): Pair<Double, Double> {
    if (distanceM <= 0.0) return 0.0 to v1
    if (v1 >= v0) {
        val rampDist = (v1 * v1 - v0 * v0) / (2.0 * ZONE_EXIT_RAMP_MPS2)
        if (distanceM <= rampDist) {
            val endMps = sqrt(v0 * v0 + 2.0 * ZONE_EXIT_RAMP_MPS2 * distanceM)
            return ((endMps - v0) / ZONE_EXIT_RAMP_MPS2) to endMps
        }
        return ((v1 - v0) / ZONE_EXIT_RAMP_MPS2 + (distanceM - rampDist) / v1) to v1
    }
    return (distanceM / v1) to v1
}

private fun midpoint(a: LatLng, b: LatLng): LatLng =
    LatLng((a.latitude + b.latitude) / 2.0, (a.longitude + b.longitude) / 2.0)

private fun interpolate(a: LatLng, b: LatLng, t: Double): LatLng =
    LatLng(
        a.latitude + (b.latitude - a.latitude) * t,
        a.longitude + (b.longitude - a.longitude) * t
    )
