package ykws.android.maro.data.track

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations

/** Implied-speed ceiling (m/s, ~1 kn) for the reconciled-idle classifier. */
const val IDLE_MAX_SPEED_MPS = 0.5

/** Net-displacement ceiling (m) for the reconciled-idle classifier. */
const val IDLE_MAX_DRIFT_M = 500.0

/**
 * Derive idle duration from the point timeline using the compound idle predicate.
 * GAP markers are filtered out first, so a long stop that produced no points is classified
 * by the displacement vs duration across the gap (idle if the boat stayed put).
 */
fun timelineIdleSec(points: List<TrackPoint>): Long {
    val real = points.filter { it.type != PointType.GAP }
    var idle = 0L
    for (i in 1 until real.size) {
        val dtSec = (real[i].timeOffsetMs - real[i - 1].timeOffsetMs) / 1000.0
        if (dtSec <= 0) continue
        val dM = SpatialOperations.haversine(
            LatLng(real[i - 1].lat, real[i - 1].lon),
            LatLng(real[i].lat, real[i].lon)
        )
        if (dM / dtSec < IDLE_MAX_SPEED_MPS && dM < IDLE_MAX_DRIFT_M) {
            idle += dtSec.toLong()
        }
    }
    return idle
}

/**
 * Rebuild distance, average speed, idle, navigating duration and lastPointTimeMs from the stored
 * points. Used for checkpoint recovery and the one-time migration — NOT for the normal finalize
 * path, whose accumulators also include the post-point idle flush.
 */
fun Track.withDerivedStats(): Track {
    val real = trackPoints.filter { it.type != PointType.GAP }
    var distanceM = 0.0
    for (i in 1 until real.size) {
        distanceM += SpatialOperations.haversine(
            LatLng(real[i - 1].lat, real[i - 1].lon),
            LatLng(real[i].lat, real[i].lon)
        )
    }
    var speedSum = 0.0
    var speedCount = 0
    for (p in real) {
        p.speedMps?.let { speedSum += it; speedCount++ }
    }
    val lastPointTimeMs = lastRealPointTimeMsOrNull() ?: 0L
    val idle = timelineIdleSec(trackPoints)
    val nav = ((lastPointTimeMs - startTimeMs) / 1000 - idle).coerceAtLeast(0)
    return copy(
        distanceNm = (distanceM / 1852.0).toFloat(),
        averageSpeedMps = if (speedCount > 0) (speedSum / speedCount).toFloat() else 0f,
        idleDurationSec = idle,
        navigatingDurationSec = nav,
        lastPointTimeMs = lastPointTimeMs
    )
}
