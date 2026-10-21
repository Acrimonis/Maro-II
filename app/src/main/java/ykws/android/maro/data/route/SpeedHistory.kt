package ykws.android.maro.data.route

/**
 * The learned history: a sliding window of the last N trip speeds, reduced by a recency-weighted median.
 *
 * One completed trip = one cruise speed. The window holds at most [maxN] of them, the oldest falling off
 * past the cap; the value the route plans with is the recency-weighted median, so one abnormal trip cannot
 * move it. There is no wall-clock expiry: the window stands until a newer trip appends or the user
 * regenerates it from the last N water tracks.
 *
 * Pure Kotlin, no Android dependency.
 */
object SpeedHistory {

    /**
     * The median of [speeds], recency-weighted by the same ramp the live reference reads: index 0 is the
     * oldest stored speed and index n-1 the newest, so [recentWeightPct] leans the answer toward the newest
     * trips. Answers `null` for an empty window.
     */
    fun weightedMedian(speeds: List<Double>, recentWeightPct: Double): Double? {
        if (speeds.isEmpty()) return null
        val n = speeds.size
        val denom = (n - 1).coerceAtLeast(1)
        val weighted = speeds.mapIndexed { i, s ->
            s to (1.0 + (recentWeightPct / 100.0) * (i.toDouble() / denom))
        }.sortedBy { it.first }
        val total = weighted.sumOf { it.second }
        val target = total / 2.0
        var acc = 0.0
        for ((speed, weight) in weighted) {
            acc += weight
            if (acc >= target) return speed
        }
        return weighted.last().first
    }

    /** Append [newSpeed] (a completed trip's cruise speed) and drop the oldest past [maxN]; newest last. */
    fun fold(window: List<Double>, newSpeed: Double, maxN: Int): List<Double> {
        val grown = window + newSpeed
        return if (grown.size > maxN) grown.drop(grown.size - maxN) else grown
    }
}
