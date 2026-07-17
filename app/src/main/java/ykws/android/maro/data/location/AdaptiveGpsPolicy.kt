package ykws.android.maro.data.location

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations

/** GPS acquisition cadence the [AdaptiveGpsPolicy] selects from observed movement. */
enum class AcquisitionMode { ACTIVE, IDLE }

/**
 * Stateful, framework-free policy that decides whether the GPS should run at the **active**
 * cadence or drop to a slower **idle** cadence, purely from the stream of fixes.
 *
 * Rule: once the boat has stayed within [thresholdM] of an anchor position for a full [windowMs],
 * emit [AcquisitionMode.IDLE]. The next fix that is ≥ [thresholdM] from the anchor immediately
 * wakes back to [AcquisitionMode.ACTIVE] and re-anchors.
 *
 * Pure Kotlin (no Android deps) so it is unit-testable on the JVM; the caller passes the
 * monotonic clock and the live thresholds from settings.
 */
class AdaptiveGpsPolicy {

    companion object {
        /** Accuracy (metres) above which displacement thresholds are widened to match. */
        const val ACCURACY_FLOOR_THRESHOLD_M = 20.0
    }

    /** Position where the current "quiet" period began, and when. Null until the first fix. */
    private var anchorPos: LatLng? = null
    private var anchorMs: Long = 0L

    /** Last mode returned by [onFix]. Updated on every call. */
    private var lastMode: AcquisitionMode = AcquisitionMode.ACTIVE

    /**
     * Fold one fix into the policy and read back the cadence it implies.
     *
     * @param nowMs       monotonic clock (e.g. `SystemClock.elapsedRealtime()`).
     * @param pos         fix position.
     * @param windowMs    how long movement must stay sub-threshold before going idle.
     * @param thresholdM  "stationary" displacement radius (m).
     * @param speedMps    GPS speed (m/s), or null. When available and < MIN_MOVEMENT_SPEED_MPS,
     *                    suppresses position-only ACTIVE wake-ups (poor-reception tiebreaker).
     * @param accuracyM   GPS accuracy (m), or null. When > ACCURACY_FLOOR_THRESHOLD_M,
     *                    widens [thresholdM] to match — displacement within error is noise.
     */
    fun onFix(
        nowMs: Long,
        pos: LatLng,
        windowMs: Long,
        thresholdM: Double,
        speedMps: Float? = null,
        accuracyM: Float? = null
    ): AcquisitionMode {
        // Widen threshold when accuracy is poor — displacement within error margin is noise.
        val effectiveThresholdM = if (accuracyM != null && accuracyM > ACCURACY_FLOOR_THRESHOLD_M) {
            maxOf(thresholdM, accuracyM.toDouble())
        } else {
            thresholdM
        }

        // First fix? Anchor here, assume active.
        if (anchorPos == null) {
            anchorPos = pos
            anchorMs = nowMs
            lastMode = AcquisitionMode.ACTIVE
            return AcquisitionMode.ACTIVE
        }

        // Displacement from anchor ≥ threshold → moving.
        if (SpatialOperations.haversine(anchorPos!!, pos) >= effectiveThresholdM) {
            // Speed tiebreaker: GPS reports stationary → don't trust position jump.
            // Re-anchor to prevent slow genuine drift from locking into permanent IDLE.
            if (speedMps != null && speedMps < GpsLocationSource.MIN_SPEED_MPS) {
                anchorPos = pos
                anchorMs = nowMs
                val result = if (nowMs - anchorMs >= windowMs) AcquisitionMode.IDLE else AcquisitionMode.ACTIVE
                lastMode = result
                return result
            }
            anchorPos = pos
            anchorMs = nowMs
            lastMode = AcquisitionMode.ACTIVE
            return AcquisitionMode.ACTIVE
        }

        // Within threshold: idle only once we've been quiet for the whole window.
        val result = if (nowMs - anchorMs >= windowMs) AcquisitionMode.IDLE else AcquisitionMode.ACTIVE
        lastMode = result
        return result
    }

    /**
     * Read-only accessor for the last known stillness state.
     * `true` when the policy has determined the device is stationary
     * (stayed within threshold for the full window).
     */
    fun isStill(): Boolean = lastMode == AcquisitionMode.IDLE

    /** Clear all state (call when GPS mode is disabled or manual start). */
    fun reset() {
        anchorPos = null
        anchorMs = 0L
        lastMode = AcquisitionMode.ACTIVE
    }
}
