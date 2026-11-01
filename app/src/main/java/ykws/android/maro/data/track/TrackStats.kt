package ykws.android.maro.data.track

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units

/** Implied-speed ceiling (m/s, ~1 kn) for the reconciled-idle classifier. */
const val IDLE_MAX_SPEED_MPS = 0.5

/** Net-displacement ceiling (m) for the reconciled-idle classifier. */
const val IDLE_MAX_DRIFT_M = 500.0

/**
 * **The stored track's own idle test, for one interval** — the boat moved under [IDLE_MAX_SPEED_MPS] on
 * average and drifted less than [IDLE_MAX_DRIFT_M] between [prev] and [point]. A recorder stores no
 * point while stopped, so a native stop is one long interval and this is the net-distance rule in
 * effect. An interval with no positive duration is never idle. One home for the two readers that walk
 * the points: [timelineIdleSec] and `TrackSlowWater`.
 */
fun isIdleStretch(prev: TrackPoint, point: TrackPoint): Boolean {
    val dtSec = (point.timeOffsetMs - prev.timeOffsetMs) / 1000.0
    if (dtSec <= 0) return false
    val dM = SpatialOperations.haversine(LatLng(prev.lat, prev.lon), LatLng(point.lat, point.lon))
    return dM / dtSec < IDLE_MAX_SPEED_MPS && dM < IDLE_MAX_DRIFT_M
}

/**
 * Derive idle duration from the point timeline using the compound idle predicate ([isIdleStretch]).
 * GAP markers are filtered out first, so a long stop that produced no points is classified
 * by the displacement vs duration across the gap (idle if the boat stayed put).
 */
fun timelineIdleSec(points: List<TrackPoint>): Long {
    // A GAP seam ends the run: the two real points on either side of it are not a stretch the boat
    // sat through, they are a resume after the recorder was away, so no idle is read across one.
    var idle = 0L
    var previous: TrackPoint? = null
    for (point in points) {
        if (point.type == PointType.GAP) { previous = null; continue }
        val prev = previous
        previous = point
        if (prev == null) continue
        if (isIdleStretch(prev, point)) {
            idle += ((point.timeOffsetMs - prev.timeOffsetMs) / 1000.0).toLong()
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
        distanceNm = Units.metresToNauticalMiles(distanceM).toFloat(),
        averageSpeedMps = if (speedCount > 0) (speedSum / speedCount).toFloat() else 0f,
        idleDurationSec = idle,
        navigatingDurationSec = nav,
        lastPointTimeMs = lastPointTimeMs
    )
}
