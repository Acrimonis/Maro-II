package ykws.android.maro.data.location

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations

/** GPS acquisition cadence the [AdaptiveGpsPolicy] selects from observed movement. */
enum class AcquisitionMode { ACTIVE, IDLE }

/**
 * Stateful, framework-free policy that decides whether the GPS should run at the **active**
 * cadence or drop to a slower **idle** cadence, purely from the stream of fixes.
 *
 * Rule (the user's spec): once the device has stayed within [thresholdM] of an anchor position for
 * a full [windowMs], emit [AcquisitionMode.IDLE]. Wake back to [AcquisitionMode.ACTIVE] immediately
 * on the first fix that is either faster than [wakeSpeedMps], jumps ≥ [thresholdM] from the previous
 * fix, or drifts ≥ [thresholdM] from the anchor (which also re-anchors, so a slow continuous drift
 * stays ACTIVE). Biased to responsiveness — a single moving fix leaves idle at once.
 *
 * Pure Kotlin (no Android deps) so it is unit-testable on the JVM; the caller passes the wall-clock
 * (`SystemClock.elapsedRealtime()`) and the live thresholds from settings, so changing the Advanced
 * sliders takes effect on the next fix without rebuilding the policy. The Spring analogy: a tiny
 * stateful strategy bean — feed it events, read back the decision.
 */
class AdaptiveGpsPolicy {

    /** Position where the current "quiet" period began, and when. Null until the first fix. */
    private var anchorPos: LatLng? = null
    private var anchorMs: Long = 0L

    /** Previous fix position, for per-fix jump detection. */
    private var lastPos: LatLng? = null

    /**
     * Fold one fix into the policy and read back the cadence it implies.
     *
     * @param nowMs        monotonic clock (e.g. `SystemClock.elapsedRealtime()`).
     * @param pos          fix position.
     * @param speedMps     speed over ground (m/s) or null when the provider gives none.
     * @param windowMs     how long movement must stay sub-threshold before going idle.
     * @param thresholdM   "stationary" displacement radius (m).
     * @param wakeSpeedMps any speed above this immediately forces ACTIVE.
     */
    fun onFix(
        nowMs: Long,
        pos: LatLng,
        speedMps: Float?,
        windowMs: Long,
        thresholdM: Double,
        wakeSpeedMps: Float = 0.8f
    ): AcquisitionMode {
        val fast = speedMps != null && speedMps > wakeSpeedMps
        val jumped = lastPos?.let { SpatialOperations.haversine(it, pos) >= thresholdM } ?: false
        lastPos = pos

        // First fix, or any wake condition → (re)anchor here and stay ACTIVE.
        if (fast || jumped || anchorPos == null) {
            anchorPos = pos
            anchorMs = nowMs
            return AcquisitionMode.ACTIVE
        }

        // Drifted beyond the stationary radius → still moving; re-anchor, stay ACTIVE.
        if (SpatialOperations.haversine(anchorPos!!, pos) >= thresholdM) {
            anchorPos = pos
            anchorMs = nowMs
            return AcquisitionMode.ACTIVE
        }

        // Within the radius: idle only once we've been quiet for the whole window.
        return if (nowMs - anchorMs >= windowMs) AcquisitionMode.IDLE else AcquisitionMode.ACTIVE
    }

    /** Clear all state (call when GPS mode is disabled). */
    fun reset() {
        anchorPos = null
        anchorMs = 0L
        lastPos = null
    }
}
