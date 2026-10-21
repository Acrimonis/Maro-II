package ykws.android.maro.data.route

import ykws.android.maro.data.track.PointType
import ykws.android.maro.data.track.TrackPoint
import ykws.android.maro.spatial.Units

/**
 * Builds the learned speed-history window from recorded tracks.
 *
 * One water track reduces to one cruise speed — the recency-weighted 75th percentile of the track's own
 * speed readings, read off the stored `speedMps` where present and derived from the neighbouring point
 * otherwise. Route-saved tracks are the caller's to exclude, because their points are plan arithmetic, not
 * navigated speed.
 *
 * Pure Kotlin: the haversine is injected so the reduction stays a unit test.
 */
object SpeedHistorySeeder {

    /** One seed pass's result. */
    data class SeedResult(
        val window: List<Double>,
        val medianKn: Double?,
        val usedTracks: Int
    )

    /** Reduce the newest [speeds] (oldest first) to a window of at most [trackCount] and its median. */
    fun seed(speeds: List<Double>, trackCount: Int, recentWeightPct: Double): SeedResult {
        val window = speeds.takeLast(trackCount.coerceAtLeast(1))
        return SeedResult(
            window = window,
            medianKn = SpeedHistory.weightedMedian(window, recentWeightPct),
            usedTracks = window.size
        )
    }

    /**
     * One track's cruise speed (kn), or null when it has fewer than five readable speed points. GAP points
     * are skipped; a point with a stored `speedMps` uses it, one without derives from the previous point
     * over [haversineM].
     */
    fun trackCruiseSpeedKn(
        points: List<TrackPoint>,
        recentWeightPct: Double,
        haversineM: (lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Double
    ): Double? {
        var previous: TrackPoint? = null
        val samples = ArrayList<RoutePace.Sample>(points.size)
        for (p in points) {
            if (p.type == PointType.GAP) {
                previous = p
                continue
            }
            val speedKn = p.speedMps?.let { it.toDouble() * Units.KNOTS_PER_MPS }
                ?: derive(previous, p, haversineM)
            if (speedKn != null && speedKn.isFinite() && speedKn > 0.0) {
                samples.add(RoutePace.Sample(speedKn, p.timeOffsetMs))
            }
            previous = p
        }
        if (samples.size < RoutePace.MIN_SAMPLES) return null
        val maxAt = samples.maxOf { it.atMs }
        return RoutePace.paceKn(samples, maxAt, 25.0, recentWeightPct)
    }

    private fun derive(
        previous: TrackPoint?,
        p: TrackPoint,
        haversineM: (Double, Double, Double, Double) -> Double
    ): Double? {
        if (previous == null || previous.type == PointType.GAP) return null
        val dtSec = (p.timeOffsetMs - previous.timeOffsetMs) / 1000.0
        if (dtSec <= 0.0) return null
        val distM = haversineM(previous.lat, previous.lon, p.lat, p.lon)
        return (distM / dtSec) * Units.KNOTS_PER_MPS
    }
}
