package ykws.android.maro.data.track

import android.util.Log
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.abs

/**
 * Single-pass track simplification using compound importance.
 *
 * Douglas-Peucker recursion with a unified "importance" score that considers both
 * spatial deviation (shape) and speed deviation (velocity profile) simultaneously.
 * A point is kept iff its compound importance exceeds 1.0.
 *
 * No separate speed-reinsertion pass — shape and speed are balanced in one recursion.
 * Raw GPS fix order is preserved in the output.
 */
object TrackSimplifier {

    private const val TAG = "MaroII_Simplify"

    /** Maximum recursion depth — prevents stack overflow on pathological tracks. */
    private const val MAX_RECURSION_DEPTH = 100

    /**
     * Simplify a track point list.
     *
     * @param points        Raw recorded points in time order.
     * @param epsilonM      Spatial tolerance (metres) — points within this distance
     *                      of the chord contribute zero spatial importance.
     * @param speedDeltaKn  Speed tolerance (knots) — speed deviations below this
     *                      contribute zero speed importance.
     * @return Simplified list preserving shape turns and speed changes.
     */
    fun simplify(
        points: List<TrackPoint>,
        epsilonM: Double = 3.0,
        speedDeltaKn: Double = 3.0
    ): List<TrackPoint> {
        Log.d(TAG, "simplify: input=${points.size} ε=$epsilonM δ=$speedDeltaKn")
        if (points.size < 3) return points

        val result = simplifyRecursive(points, epsilonM, speedDeltaKn)
        Log.d(TAG, "simplify: result=${result.size} (${(100.0 * result.size / points.size).toInt()}%)")
        return result
    }

    // ── Core recursion ──────────────────────────────────────────────────────

    private fun simplifyRecursive(
        points: List<TrackPoint>,
        epsilonM: Double,
        speedDeltaKn: Double,
        depth: Int = 0
    ): List<TrackPoint> {
        if (points.size <= 2) return points
        if (depth >= MAX_RECURSION_DEPTH) return listOf(points.first(), points.last())

        val first = points.first()
        val last = points.last()

        // Compute average speed over this segment for the speed-importance denominator
        var sumKn = 0.0
        var count = 0
        for (i in points.indices) {
            val kn = (points[i].speedMps ?: 0f) * 1.94384
            sumKn += kn
            count++
        }
        val avgKn = if (count > 0) sumKn / count else 0.0

        // Find point with maximum compound importance
        var maxImportance = 0.0
        var maxIdx = 0

        for (i in 1 until points.size - 1) {
            val p = points[i]

            // Spatial importance: how far off the chord?
            val spatialDist = perpendicularDistanceM(p, first, last)
            val spatialImportance = spatialDist / epsilonM

            // Speed importance: how much does speed deviate from segment average?
            val kn = (p.speedMps ?: 0f) * 1.94384
            val speedImportance = abs(kn - avgKn) / speedDeltaKn

            val importance = maxOf(spatialImportance, speedImportance)
            if (importance > maxImportance) {
                maxImportance = importance
                maxIdx = i
            }
        }

        return if (maxImportance > 1.0) {
            val left = simplifyRecursive(points.subList(0, maxIdx + 1), epsilonM, speedDeltaKn, depth + 1)
            val right = simplifyRecursive(points.subList(maxIdx, points.size), epsilonM, speedDeltaKn, depth + 1)
            left.dropLast(1) + right
        } else {
            listOf(first, last)
        }
    }

    // ── Geometry ────────────────────────────────────────────────────────────

    /** Perpendicular distance (metres) from point P to the infinite line AB. */
    private fun perpendicularDistanceM(
        p: TrackPoint,
        a: TrackPoint,
        b: TrackPoint
    ): Double {
        val segmentLenM = SpatialOperations.haversine(
            LatLng(a.lat, a.lon),
            LatLng(b.lat, b.lon)
        )
        if (segmentLenM < 1e-6) return 0.0

        val avgLatRad = Math.toRadians((a.lat + b.lat) / 2.0)
        val cosLat = kotlin.math.cos(avgLatRad)

        val dx = (b.lon - a.lon) * 111_320.0 * cosLat
        val dy = (b.lat - a.lat) * 111_320.0
        val px = (p.lon - a.lon) * 111_320.0 * cosLat
        val py = (p.lat - a.lat) * 111_320.0

        return abs(dx * py - dy * px) / segmentLenM
    }
}
