package ykws.android.maro.data.route

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.track.PointType
import ykws.android.maro.data.track.TrackPoint
import ykws.android.maro.spatial.Units

/**
 * Reduces one recorded track to a history [SpeedHistory.Trip].
 *
 * A trip's number is its **cruising speed**: the 90th percentile of its eligible readings — outside the speed
 * zones and the 300 m band and at or above the stop speed — the typical open-water speed, with the burst
 * sitting in the top tenth. The history is the boat's driving profile across trips, not the conditions of one
 * outing, so this is not the live pace's average. The stored `speedMps` is read where present and derived from
 * the neighbouring point otherwise; GAP seams are skipped. Each trip also carries its **open-water minutes**,
 * the time its eligible readings cover, which is the weight it votes with.
 *
 * Route-saved tracks are the caller's to exclude, because their points are plan arithmetic, not navigated
 * speed.
 *
 * Pure Kotlin: the haversine is injected so the reduction stays a unit test.
 */
object SpeedHistorySeeder {

    /** Below this many eligible readings a track has no cruising speed to speak of. */
    const val MIN_READINGS = 5

    /** The percentile a trip's readings are reduced by. */
    private const val CRUISE_QUANTILE = 0.90

    /**
     * One track's trip, or null when it has fewer than [MIN_READINGS] eligible readings.
     *
     * @param restricted required: whether a point sits in a speed zone or the band. There is no default — a
     *                   caller that forgot it would silently turn the exclusion off.
     * @param maxCoverSec the longest a reading speaks for: a stop leaves no points, so a longer interval to the
     *                   next point is silence, not open water.
     */
    fun tripOf(
        points: List<TrackPoint>,
        startTimeMs: Long,
        demo: Boolean,
        haversineM: (lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Double,
        restricted: (lat: Double, lon: Double) -> Boolean,
        stopKn: Double = AppConfig.routePaceStopKn,
        maxCoverSec: Double = AppConfig.trackingGapTimeThresholdSec.toDouble()
    ): SpeedHistory.Trip? {
        val speeds = ArrayList<Double>(points.size)
        var coveredSec = 0.0
        var previous: TrackPoint? = null
        for ((i, p) in points.withIndex()) {
            if (p.type == PointType.GAP) {
                previous = p
                continue
            }
            val eligibleSpeed = if (restricted(p.lat, p.lon)) {
                null
            } else {
                (p.speedMps?.let { it.toDouble() * Units.KNOTS_PER_MPS } ?: derive(previous, p, haversineM))
                    ?.takeIf { RoutePace.isCruising(it, stopKn) }
            }
            if (eligibleSpeed != null) {
                speeds.add(eligibleSpeed)
                coveredSec += coverSec(p, points.getOrNull(i + 1), maxCoverSec)
            }
            previous = p
        }
        if (speeds.size < MIN_READINGS) return null
        val sorted = speeds.sorted()
        val rank = Math.ceil(sorted.size * CRUISE_QUANTILE).toInt().coerceIn(1, sorted.size)
        return SpeedHistory.Trip(
            speedKn = sorted[rank - 1].coerceIn(
                AppConfig.ROUTE_FREE_WATER_PACE_MIN_KN.toDouble(),
                AppConfig.ROUTE_FREE_WATER_PACE_MAX_KN.toDouble()
            ),
            openWaterMin = coveredSec / 60.0,
            startTimeMs = startTimeMs,
            demo = demo
        )
    }

    /** The seconds a point speaks for: up to the next real point, none across a GAP seam, never past [maxSec]. */
    private fun coverSec(p: TrackPoint, next: TrackPoint?, maxSec: Double): Double {
        if (next == null || next.type == PointType.GAP) return 0.0
        return ((next.timeOffsetMs - p.timeOffsetMs) / 1000.0).coerceIn(0.0, maxSec)
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
