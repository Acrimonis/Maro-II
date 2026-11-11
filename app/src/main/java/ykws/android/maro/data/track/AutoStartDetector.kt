package ykws.android.maro.data.track

import ykws.android.maro.data.location.AdaptiveGpsPolicy
import ykws.android.maro.data.location.GpsLocationSource
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations

/**
 * The forgotten-start rule: decides, fix by fix, that a boat leaving the home circle with tracking off
 * is on a trip nobody started.
 *
 * Level, not edge — every accepted fix while recording is off is tested against all of:
 *
 * 1. **Inside** the circle at the completing fix or at any fix of the movement window;
 * 2. **Rested** there — the latch, below;
 * 3. **Off for a while** — at least [Params.offMs] since the last stop (no recorded stop holds);
 * 4. **Moved** — the largest distance from the newest retained fix to any other reaches the idle
 *    detection's effective threshold, and the Doppler speed of the completing and previous fixes agrees;
 * 5. **On water** — the position answers `true` (`false` and `null` both refuse);
 * 6. **No recovery pending**.
 *
 * Conditions 5 and 6 are asked last, only when 1–4 pass. A miss is retried on the next fix.
 *
 * **The latch.** The standby stream sends a fix only after a move, so a boat at rest sends nothing: rest is
 * *no movement seen for [Params.restMs]*, judged when the next fix arrives. It is set by a still first fix
 * inside the circle, or by a fix inside after a rest gap, and cleared only by a fix farther outside than its
 * own accuracy. The state lives for the detector's life — a recording starting or ending never resets it —
 * and [observe] feeds it the fixes of a recording too, so a stop at the berth is a rest as well.
 *
 * Pure Kotlin on one monotonic clock the caller supplies; a fix's wall timestamp is never read.
 */
class AutoStartDetector(startedAtMonoMs: Long) {

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

    /** The live thresholds, passed per fix so a setting changed between two fixes applies on the next. */
    data class Params(
        /** Minimum time since the last stop (ms). */
        val offMs: Long,
        /** Time without movement (ms) that makes the boat rested. */
        val restMs: Long,
        /** The idle detection's time setting (ms): "how long the boat can stay within the distance". */
        val idleTimeMs: Long,
        /** The idle detection's distance setting (m). */
        val idleDistanceM: Double,
        /** The standby fix cadence (ms), which floors the movement window. */
        val standbyIntervalMs: Long
    ) {
        /** Three standby fixes' worth at least: a 10 s idle setting alone holds two fixes about 10 m apart. */
        val moveWindowMs: Long get() = maxOf(idleTimeMs, STANDBY_FIXES_IN_WINDOW * standbyIntervalMs)
    }

    /** The home circle. */
    data class Home(val origin: LatLng, val radiusM: Double)

    /** A start, and where the trip begins from. */
    data class Decision(val fromHome: Boolean)

    private class Seen(val position: LatLng, val monoMs: Long, val speedMps: Float?, val inside: Boolean)

    private class Reading(val moving: Boolean, val insideInWindow: Boolean)

    private val window = ArrayDeque<Seen>()
    private var latch = false
    private var seen = false
    private var prevInside = false
    private var lastMoveMono = startedAtMonoMs

    /**
     * Fold a recording's fix into the latch and the window; the state keeps flowing while a track
     * records so the rest of a stop at the berth is known when it ends.
     */
    fun observe(fix: Fix, params: Params, home: Home?) {
        advance(fix, params, home)
    }

    /**
     * Fold one fix while recording is off and answer whether the rule fires on it.
     *
     * @param lastStopMono   monotonic time of the last stop, or null when none is recorded.
     * @param orphansPending whether a recoverable checkpoint awaits — asked only when 1–5 pass.
     * @param waterAt        the water answer for a position: `true` water, `false` land, `null` unknown.
     */
    suspend fun onSample(
        fix: Fix,
        params: Params,
        home: Home?,
        lastStopMono: Long?,
        orphansPending: suspend () -> Boolean,
        waterAt: (LatLng) -> Boolean?
    ): Decision? {
        val reading = advance(fix, params, home) ?: return null
        val offLongEnough = lastStopMono == null || fix.monoMs - lastStopMono >= params.offMs
        if (!reading.insideInWindow || !latch || !offLongEnough || !reading.moving) return null
        if (waterAt(fix.position) != true) return null
        if (orphansPending()) return null
        return Decision(fromHome = true)
    }

    private fun advance(fix: Fix, params: Params, home: Home?): Reading? {
        if (home == null) {
            forget()
            return null
        }
        if (!fix.accepted) return null

        val distanceToOrigin = SpatialOperations.haversine(fix.position, home.origin)
        val inside = TrackGeofenceChecker.isInsideGeofence(
            fix.position.latitude, fix.position.longitude,
            home.origin.latitude, home.origin.longitude, home.radiusM
        )
        // Hysteresis: one jitter fix just past the edge, within its own accuracy, keeps the latch.
        val farOut = distanceToOrigin - home.radiusM > (fix.accuracyM ?: 0f)
        when {
            farOut -> latch = false
            !seen -> latch = inside && !isMovingBySpeed(fix.speedMps)
            inside && prevInside && fix.monoMs - lastMoveMono >= params.restMs -> latch = true
        }

        window.addLast(Seen(fix.position, fix.monoMs, fix.speedMps, inside))
        while (window.first().monoMs < fix.monoMs - params.moveWindowMs) window.removeFirst()
        val moving = hasMoved(fix, params)
        if (moving || isMovingBySpeed(fix.speedMps)) lastMoveMono = fix.monoMs
        seen = true
        prevInside = inside
        return Reading(moving, window.any { it.inside })
    }

    /** A Doppler speed at or above the floor; an absent speed is not evidence of movement. */
    private fun isMovingBySpeed(speedMps: Float?): Boolean =
        speedMps != null && speedMps >= GpsLocationSource.MIN_SPEED_MPS

    /**
     * Condition 4 over the window: at least two retained fixes, the newest at least the effective
     * threshold away from one of them, and — where speeds are present — the completing and the previous
     * retained fix both at the Doppler floor.
     */
    private fun hasMoved(fix: Fix, params: Params): Boolean {
        if (window.size < 2) return false
        val newest = window.last()
        val threshold = AdaptiveGpsPolicy.effectiveThresholdM(params.idleDistanceM, fix.accuracyM)
        val farthest = window.maxOf { SpatialOperations.haversine(it.position, newest.position) }
        if (farthest < threshold) return false
        val previous = window[window.size - 2]
        return dopplerAgrees(newest.speedMps) && dopplerAgrees(previous.speedMps)
    }

    private fun dopplerAgrees(speedMps: Float?): Boolean = speedMps == null || speedMps >= GpsLocationSource.MIN_SPEED_MPS

    /** The circle is off or gone: nothing seen counts, so a later return starts from a first fix. */
    private fun forget() {
        window.clear()
        latch = false
        seen = false
        prevInside = false
    }

    private companion object {
        const val STANDBY_FIXES_IN_WINDOW = 3L
    }
}
