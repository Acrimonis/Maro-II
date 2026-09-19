package ykws.android.maro.data.track

import ykws.android.maro.data.model.RegionBounds

/**
 * A track's sampled position counts: how many of the sampled points were on water and how many on land.
 * Both are [UNCLASSIFIED] when nothing could be classified — the sentinel the summary carries, which is
 * deliberately distinct from a genuine zero.
 */
data class TrackPositionCounts(
    val water: Int = UNCLASSIFIED,
    val land: Int = UNCLASSIFIED
) {
    val isClassified: Boolean get() = water >= 0 && land >= 0

    companion object {
        /** Never classified — the value an unsampled or unanswerable track carries. */
        const val UNCLASSIFIED = -1
    }
}

/** How many points one track is sampled at — bounded, so a long track costs no more than a short one. */
const val POSITION_SAMPLE_COUNT = 32

/**
 * Classify a track by sampling [points] on an even stride and asking [isWater] about each point
 * [bounds] contains. GAP markers carry no position of their own and are skipped, and neither counts nor
 * breaks a run.
 *
 * A point outside the region, or one the test cannot answer for (`null`), is unclassifiable and is
 * simply not counted; a track with no classified sample at all comes back unclassified, which the filter
 * reads as water.
 *
 * Pure by construction — the test and the bounds arrive as parameters — so the rule is unit-tested with
 * no coastline, no index and no device.
 */
fun classifyTrackPosition(
    points: List<TrackPoint>,
    bounds: RegionBounds,
    isWater: (Double, Double) -> Boolean?,
    sampleCount: Int = POSITION_SAMPLE_COUNT
): TrackPositionCounts {
    val located = points.filter { it.type != PointType.GAP }
    if (located.isEmpty()) return TrackPositionCounts()

    val size = located.size
    val wanted = sampleCount.coerceAtLeast(1)
    // Exactly `wanted` points, spread evenly with both ends included, so the sample is as
    // representative as the budget allows and its cost is the same whatever the track's length.
    val indices = when {
        size <= wanted -> located.indices.toList()
        wanted == 1 -> listOf(0)
        else -> List(wanted) { i -> ((size - 1).toLong() * i / (wanted - 1)).toInt() }
    }

    var water = 0
    var land = 0
    for (index in indices) {
        val point = located[index]
        if (bounds.contains(point.lat, point.lon)) {
            when (isWater(point.lat, point.lon)) {
                true -> water++
                false -> land++
                null -> Unit  // cannot answer here — not counted either way
            }
        }
    }
    return if (water == 0 && land == 0) TrackPositionCounts() else TrackPositionCounts(water, land)
}
