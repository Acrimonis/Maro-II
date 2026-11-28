package ykws.android.maro.data.track

import ykws.android.maro.data.track.AutoStartDetector.Fix
import ykws.android.maro.data.track.AutoStartDetector.Home
import ykws.android.maro.spatial.SpatialOperations

/**
 * The auto-save on return, fix by fix while a recording runs: a trip that has been out of the home circle
 * and comes back inside the smaller **arrival circle** (same origin) is *holding*; the recorder saves it once
 * the hold has lasted its time, and a fix clearly out of the arrival circle ends the hold first.
 *
 * - **From open sea:** a fix farther outside the home circle than its own accuracy, since the recording
 *   started or since its last [reset] — a Continue reopens the trip with that requirement fresh, so a boat
 *   still at the berth is not saved again.
 * - **Entry:** a fix inside the arrival circle, allowed for the mode, and — for a GPS fix — reachable from the
 *   previous accepted fix at [maxSpeedMps] at most (the spike guard; no Doppler test, an arriving boat moves).
 * - **Leave:** while holding, a fix farther outside the arrival circle than its own accuracy.
 *
 * Pure Kotlin on the caller's monotonic clock; the hold's timer is the recorder's.
 */
class ArrivalDetector(private val maxSpeedMps: Double) {

    /** What one fix did to the hold. */
    enum class Signal { NONE, ENTERED, LEFT }

    private var leftHome = false
    private var holding = false
    private var previous: Fix? = null

    /** Whether a hold is under way — the arrival prompt stands aside while it is. */
    val isHolding: Boolean get() = holding

    /** A new recording, a Continue, or a hold spent: the trip must leave the home circle again to be saved. */
    fun reset() {
        leftHome = false
        holding = false
    }

    /** The hold ends without a save (the rule was switched off during it): the trip stays back from sea. */
    fun abandonHold() {
        holding = false
    }

    /**
     * Fold one recording fix.
     *
     * @param arrivalRadiusM the arrival circle's radius around [Home.origin], capped at the home circle's.
     * @param allowed        the user's toggle for the current mode; off, no hold begins.
     */
    fun onFix(fix: Fix, home: Home?, arrivalRadiusM: Double, allowed: Boolean): Signal {
        if (home == null) {
            val wasHolding = holding
            reset()
            previous = null
            return if (wasHolding) Signal.LEFT else Signal.NONE
        }
        if (!fix.accepted) return Signal.NONE
        val before = previous
        previous = fix

        // A misconfigured arrival circle wider than the home one would save trips that never came home.
        val arrivalM = minOf(arrivalRadiusM, home.radiusM)
        if (FixGuards.isFarOutside(fix, home)) leftHome = true

        if (holding) {
            if (FixGuards.isFarOutside(fix, home, arrivalM)) {
                holding = false
                return Signal.LEFT
            }
            return Signal.NONE
        }
        if (!allowed || !leftHome) return Signal.NONE
        if (SpatialOperations.haversine(fix.position, home.origin) > arrivalM) return Signal.NONE
        if (!fix.isDemo && before != null && !FixGuards.isReachable(before, fix, maxSpeedMps)) return Signal.NONE
        holding = true
        return Signal.ENTERED
    }
}
