package ykws.android.maro.data.track

import ykws.android.maro.data.location.GpsLocationSource
import ykws.android.maro.data.model.LatLng

/**
 * The home-port exit rule: decides, fix by fix, that a boat leaving the home circle with tracking off is on
 * a trip, and starts it.
 *
 * **Armed** by any accepted fix inside the circle — while recording or not, so an arrival that ends a
 * recording inside leaves the next departure armed. **Fires** on an accepted fix while recording is off
 * that is farther outside the circle than its own accuracy, when the rule is armed and allowed for the
 * mode, the fix is no spike and the position answers water (`false` and `null` both refuse). A miss keeps
 * the rule armed and is retried on the next fix, so an exit fix read as land still starts on the first one
 * at sea. A pending recovery does not block it: the recorder saves the checkpoint before it starts.
 *
 * **Spike guard** (GPS fixes only — demo samples bypass it, as they bypass the recorder's gates): the exit
 * fix must report a Doppler speed of at least [GpsLocationSource.MIN_SPEED_MPS] — a multipath jump moves
 * the position, not the Doppler, so a boat still at the berth reads near zero — and the speed implied from
 * the last fix inside must not exceed [maxSpeedMps].
 *
 * **Disarmed** by a start, and by a recording's fix far outside: that exit belonged to the recording, so a
 * trip stopped at sea never restarts on its own. A boat never seen inside — the app opened at sea — starts
 * nothing.
 *
 * Hysteresis: a fix just past the edge, within its own accuracy, neither fires nor disarms.
 *
 * Pure Kotlin on one monotonic clock the caller supplies; a fix's wall timestamp is never read.
 *
 * @param maxSpeedMps the boat's top plausible speed, the implied-speed cap of the spike guard.
 */
class AutoStartDetector(private val maxSpeedMps: Double) {

    /** One fix as the rule reads it. A no-lock fix that is not a demo sample is a re-emit, ignored everywhere. */
    data class Fix(
        val position: LatLng,
        val monoMs: Long,
        val accuracyM: Float?,
        val speedMps: Float?,
        val hasLock: Boolean,
        val isDemo: Boolean
    ) {
        val accepted: Boolean get() = hasLock || isDemo
    }

    /** The home circle. */
    data class Home(val origin: LatLng, val radiusM: Double)

    private enum class Side { INSIDE, EDGE, FAR_OUT }

    private var armed = false

    /** The latest accepted fix inside the circle — the spike guard's reference; non-null whenever armed. */
    private var lastInside: Fix? = null

    /** Fold a recording's fix: inside arms the next departure, far outside spends the current one. */
    fun observe(fix: Fix, home: Home?) {
        if (side(fix, home) == Side.FAR_OUT) armed = false
    }

    /**
     * Fold one fix while recording is off and answer whether the rule starts a recording on it.
     *
     * @param allowed the user's toggle for the current mode; off, the fix still arms and the rule waits.
     * @param waterAt the water answer for a position: `true` water, `false` land, `null` unknown.
     */
    fun onSample(fix: Fix, home: Home?, allowed: Boolean, waterAt: (LatLng) -> Boolean?): Boolean {
        if (side(fix, home) != Side.FAR_OUT || !armed || !allowed) return false
        if (!fix.isDemo && !isPlausibleExit(fix)) return false
        if (waterAt(fix.position) != true) return false
        armed = false
        return true
    }

    /** The spike guard: moving by Doppler, and reachable from the last fix inside at the boat's top speed. */
    private fun isPlausibleExit(fix: Fix): Boolean {
        val speed = fix.speedMps ?: return false
        if (speed < GpsLocationSource.MIN_SPEED_MPS) return false
        val reference = lastInside ?: return false
        return FixGuards.isReachable(reference, fix, maxSpeedMps)
    }

    /** Where [fix] lies against the circle, arming on inside; null for a fix the rule ignores. */
    private fun side(fix: Fix, home: Home?): Side? {
        if (home == null) {
            armed = false
            lastInside = null
            return null
        }
        if (!fix.accepted) return null
        val inside = TrackGeofenceChecker.isInsideGeofence(
            fix.position.latitude, fix.position.longitude,
            home.origin.latitude, home.origin.longitude, home.radiusM
        )
        if (inside) {
            armed = true
            lastInside = fix
            return Side.INSIDE
        }
        return if (FixGuards.isFarOutside(fix, home)) Side.FAR_OUT else Side.EDGE
    }
}
