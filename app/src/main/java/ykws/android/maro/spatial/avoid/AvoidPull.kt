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
 * **The priced half of the guarantee waits for its first source.** The field's soft prices are what
 * stop this walk from shortcutting a corner through a priced zone the search just went around — a
 * chord is accepted only while its summed soft cost stays within the A* cell path's over the same
 * span. It lands with phase 3, the 300 m band being the first price to ride the field; until then the
 * field carries no soft source and the walk is byte-for-byte stage 1's.
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
        var anchor = 0
        var probe = 1
        while (probe < path.size) {
            when {
                legClear(path[anchor], path[probe], marginM, field, start, aim) -> probe++
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
}
