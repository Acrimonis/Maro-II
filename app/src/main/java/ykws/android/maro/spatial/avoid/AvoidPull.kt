package ykws.android.maro.spatial.avoid

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.ceil

/**
 * The clearance taut pull: collapses the coarse cell path into straight waypoints that stay clear of
 * the field's walls. The classic two-pointer string-pull — the anchor stands while the probe advances,
 * and on a failed extension the probe's predecessor becomes the new anchor — walked over the
 * **unified cost field** [RouteCostField] rather than a bare distance, so the same walk serves the
 * coastline of stage 1 and every source the later phases add.
 *
 * A chord is clear when every sample along it stands at least [marginM] from the field's nearest hard
 * wall, sampled at `≤ marginM / 2` so the margin is a guarantee rather than an approximation. The
 * clearance is **exempt within a margin-radius disc around each forced-free end** (the raw start and
 * aim), which is how the first and last legs reconcile with the margin predicate. A rejected chord is
 * re-walked once from its predecessor's predecessor — the concave-coast retry, bounded rather than
 * looped.
 *
 * **The priced half of the guarantee.** Where the field carries a price, a chord is accepted only
 * while its own summed price stays within the A* cell path's over the span it would replace — so the
 * pull can never shortcut a corner through a priced band the search just went around. The path's price
 * is a prefix sum, so each candidate costs one walk of the chord alone, and a field with no price
 * skips the whole reading.
 */
object AvoidPull {

    /**
     * Pulls [path] (raw start first, raw aim last) taut into the ordered waypoint list, as direct as
     * the field's margin allows and independent of grid orientation.
     *
     * @param field the unified cost field — its nearest hard wall is the clearance read here.
     */
    fun pull(
        path: List<LatLng>,
        start: LatLng,
        aim: LatLng,
        marginM: Double,
        field: RouteCostField
    ): List<LatLng> {
        if (path.size <= 2) return path
        val result = ArrayList<LatLng>(path.size)
        result.add(path.first())
        val pathPriceM = if (field.hasSoft) softPricePrefix(path, marginM, field) else null
        var anchor = 0
        var probe = 1
        while (probe < path.size) {
            val replacedPriceM = pathPriceM?.let { it[probe] - it[anchor] }
            when {
                legClear(path[anchor], path[probe], marginM, field, start, aim) &&
                    (replacedPriceM == null ||
                        softPriceM(path[anchor], path[probe], marginM, field) <= replacedPriceM) -> probe++
                // The immediate step grazes land in a corner: it cannot be pulled, so it is accepted
                // once and the walk moves on — the bounded form of the concave re-walk.
                probe == anchor + 1 -> {
                    result.add(path[probe])
                    anchor = probe
                    probe++
                }
                else -> {
                    result.add(path[probe - 1])
                    anchor = probe - 1
                    // probe stands; the loop re-walks the chord from the new anchor exactly once.
                }
            }
        }
        if (result.last() != path.last()) result.add(path.last())
        return result
    }

    internal fun legClear(
        a: LatLng,
        b: LatLng,
        marginM: Double,
        field: RouteCostField,
        start: LatLng,
        aim: LatLng
    ): Boolean {
        val dist = SpatialOperations.haversine(a, b)
        val sampleStep = marginM / 2.0
        val steps = ceil(dist / sampleStep).toInt().coerceAtLeast(2)
        for (i in 1 until steps) {
            val t = i.toDouble() / steps
            val p = LatLng(
                a.latitude + (b.latitude - a.latitude) * t,
                a.longitude + (b.longitude - a.longitude) * t
            )
            // End-disc exemption: the margin binds the path, not the forced-free ends themselves.
            if (SpatialOperations.haversine(p, start) < marginM) continue
            if (SpatialOperations.haversine(p, aim) < marginM) continue
            if (field.hardDistanceM(p) < marginM) return false
        }
        return true
    }

    /**
     * The field's prices summed along one straight segment, in metres-equivalent — price per metre
     * times metres — read at the **midpoint of each interval** so the whole segment is covered, at
     * `≤ marginM / 2` intervals so a price narrower than the step cannot slip between two readings.
     *
     * The midpoint is what makes two segments comparable: an end-excluding walk discounts a short
     * segment by half a sample and a long one by almost nothing, so a chord would have looked dearer
     * than the cell path it replaces and no line would ever be pulled taut. A field with no price
     * reads 0 and the guard is inert.
     */
    internal fun softPriceM(a: LatLng, b: LatLng, marginM: Double, field: RouteCostField): Double {
        val dist = SpatialOperations.haversine(a, b)
        val sampleStep = marginM / 2.0
        val steps = ceil(dist / sampleStep).toInt().coerceAtLeast(1)
        val stepM = dist / steps
        var sum = 0.0
        for (i in 0 until steps) {
            val t = (i + 0.5) / steps
            val p = LatLng(
                a.latitude + (b.latitude - a.latitude) * t,
                a.longitude + (b.longitude - a.longitude) * t
            )
            sum += field.evaluate(p).softCostM * stepM
        }
        return sum
    }

    /** [softPriceM] accumulated along [path], so one span's price is a single subtraction. */
    private fun softPricePrefix(
        path: List<LatLng>,
        marginM: Double,
        field: RouteCostField
    ): DoubleArray {
        val prefix = DoubleArray(path.size)
        for (i in 1 until path.size) {
            prefix[i] = prefix[i - 1] + softPriceM(path[i - 1], path[i], marginM, field)
        }
        return prefix
    }
}
