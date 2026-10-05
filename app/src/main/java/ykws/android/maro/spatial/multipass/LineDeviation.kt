package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations

/**
 * **How far one line sits from another**, in metres: the furthest and the average of the shortest
 * distances its own points keep from the other polyline.
 *
 * A point is measured to the other line's **segments**, never to its vertices, so a coarse vertex
 * standing half-way along a fine segment reads as near rather than as half a segment away. The question
 * it answers is the corridor's own: a line that wanders [maxM] off its reference needs a corridor at
 * least that wide, which is the reading the fine region's half-width rests on.
 */
internal data class LineDeviation(val maxM: Double, val meanM: Double)

/**
 * Every point of [from] measured against the polyline [to] — `null` where either side has no segment to
 * measure between, so a caller reads a missing figure rather than a zero that would look exact.
 */
internal fun deviationTo(from: List<LatLng>, to: List<LatLng>): LineDeviation? {
    if (from.isEmpty() || to.size < 2) return null
    var maxM = 0.0
    var total = 0.0
    for (point in from) {
        var nearestM = Double.MAX_VALUE
        for (i in 0 until to.size - 1) {
            val distanceM = SpatialOperations.pointToSegmentDistance(point, to[i], to[i + 1])
            if (distanceM < nearestM) nearestM = distanceM
        }
        if (nearestM > maxM) maxM = nearestM
        total += nearestM
    }
    return LineDeviation(maxM, total / from.size)
}
