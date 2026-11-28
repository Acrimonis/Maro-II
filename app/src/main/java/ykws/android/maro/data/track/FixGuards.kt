package ykws.android.maro.data.track

import ykws.android.maro.data.track.AutoStartDetector.Fix
import ykws.android.maro.data.track.AutoStartDetector.Home
import ykws.android.maro.spatial.SpatialOperations

/** The two tests every automatic-tracking rule ([AutoStartDetector], [ArrivalDetector], [WaterStartDetector]) shares. */
internal object FixGuards {

    /**
     * [fix] lies farther outside the circle of [radiusM] around [home]'s origin than its own accuracy —
     * clearly out, never a jitter fix just past the edge.
     */
    fun isFarOutside(fix: Fix, home: Home, radiusM: Double = home.radiusM): Boolean =
        SpatialOperations.haversine(fix.position, home.origin) - radiusM > (fix.accuracyM ?: 0f)

    /**
     * The spike guard's implied speed: [fix] reachable from [before] at [maxSpeedMps] at most on the
     * monotonic clock. A jump at the same instant, or backwards in time, is not.
     */
    fun isReachable(before: Fix, fix: Fix, maxSpeedMps: Double): Boolean {
        val elapsedMs = fix.monoMs - before.monoMs
        if (elapsedMs <= 0L) return false
        val impliedMps = SpatialOperations.haversine(before.position, fix.position) * 1_000.0 / elapsedMs
        return impliedMps <= maxSpeedMps
    }
}
