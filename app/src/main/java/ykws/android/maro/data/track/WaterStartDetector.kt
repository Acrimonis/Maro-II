package ykws.android.maro.data.track

import ykws.android.maro.data.location.AdaptiveGpsPolicy
import ykws.android.maro.data.location.GpsLocationSource
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.track.AutoStartDetector.Fix
import ykws.android.maro.data.track.AutoStartDetector.Home
import ykws.android.maro.spatial.SpatialOperations

/**
 * The start on water, fix by fix while recording is off: a boat on water, outside the home circle, that
 * makes a **positive move** — at least [Params.distanceM] from its reference point, at a Doppler speed of at
 * least [GpsLocationSource.MIN_SPEED_MPS] — starts a recording, anywhere.
 *
 * - **Reference:** where the boat last rested, or the first accepted fix the detector sees after it starts
 *   or after a recording begins or ends — so the app opened under way starts once the boat has covered the
 *   distance.
 * - **Rest:** the stop detection's own test — a fix still within its distance (through
 *   [AdaptiveGpsPolicy.effectiveThresholdM]) of the anchor where the quiet period began, the window or more
 *   after it. A swinging boat's fixes and a silent boat's gap both read so. A rest moves the reference to the
 *   anchor.
 * - **Armed:** from the detector's start; a recording stopped outside the home circle disarms it — the user
 *   stopped it there — and the next rest re-arms it, so only a stop-and-go starts a new trip.
 * - **Outside the home circle** by more than the fix's accuracy: inside it the exit rule owns the start.
 * - **Spike guard** for a GPS fix: the Doppler floor above, and the implied speed from the previous accepted
 *   fix at most [maxSpeedMps]. Demo samples bypass it.
 * - **On water:** the position answers `true`; `false` and `null` refuse, retried on the next fix.
 *
 * Pure Kotlin on the caller's monotonic clock.
 */
class WaterStartDetector(private val maxSpeedMps: Double) {

    /** The live thresholds, passed per fix so a setting changed between two fixes applies on the next. */
    data class Params(
        /** Distance from the reference that makes the start (m) — `tracking.waterStart.distanceM`. */
        val distanceM: Double,
        /** The stop detection's window (ms): a fix gap at least this long, within [restDistanceM], is a rest. */
        val restWindowMs: Long,
        /** The stop detection's distance (m). */
        val restDistanceM: Double
    )

    private var armed = true
    private var reference: LatLng? = null
    private var last: Fix? = null
    /** Where the boat's current quiet period began — the stop detection's anchor, re-set by every move. */
    private var restAnchor: Fix? = null

    /** Fold a recording's fix: the last position stays current, so a rest after the stop reads from it. */
    fun observe(fix: Fix, params: Params) {
        fold(fix, params)
    }

    /** A recording began: the reference is taken afresh once it ends. */
    fun onRecordingStarted() {
        reference = null
    }

    /** A recording ended; one stopped outside the home circle disarms the rule until the boat rests. */
    fun onRecordingStopped(outsideHome: Boolean) {
        if (outsideHome) armed = false
        reference = null
    }

    /**
     * Fold one fix while recording is off and answer whether the rule starts a recording on it.
     *
     * @param allowed the user's toggle for the current mode; off, the fix is still folded.
     * @param waterAt the water answer for a position: `true` water, `false` land, `null` unknown.
     */
    fun onSample(
        fix: Fix,
        home: Home?,
        params: Params,
        allowed: Boolean,
        waterAt: (LatLng) -> Boolean?
    ): Boolean {
        if (!fix.accepted) return false
        val before = fold(fix, params)
        val from = reference ?: run {
            reference = fix.position
            return false
        }
        if (!allowed || !armed) return false
        if (home != null && !FixGuards.isFarOutside(fix, home)) return false
        val threshold = AdaptiveGpsPolicy.effectiveThresholdM(params.distanceM, fix.accuracyM)
        if (SpatialOperations.haversine(from, fix.position) < threshold) return false
        if (!fix.isDemo && !isPlausibleMove(before, fix)) return false
        if (waterAt(fix.position) != true) return false
        reference = null
        return true
    }

    /**
     * Takes [fix] as the last one and runs the stop detection's own test on it — re-arming and moving the
     * reference to the rest point once the boat has stayed within the distance of it for the window. Returns
     * the fix before it.
     */
    private fun fold(fix: Fix, params: Params): Fix? {
        if (!fix.accepted) return null
        val before = last
        last = fix
        val anchor = restAnchor
        val restThresholdM = AdaptiveGpsPolicy.effectiveThresholdM(params.restDistanceM, fix.accuracyM)
        when {
            anchor == null || SpatialOperations.haversine(anchor.position, fix.position) >= restThresholdM ->
                restAnchor = fix
            fix.monoMs - anchor.monoMs >= params.restWindowMs -> {
                armed = true
                reference = anchor.position
            }
        }
        return before
    }

    /** The spike guard: moving by Doppler, and reachable from the previous fix at the boat's top speed. */
    private fun isPlausibleMove(before: Fix?, fix: Fix): Boolean {
        val speed = fix.speedMps ?: return false
        if (speed < GpsLocationSource.MIN_SPEED_MPS) return false
        return before == null || FixGuards.isReachable(before, fix, maxSpeedMps)
    }
}
