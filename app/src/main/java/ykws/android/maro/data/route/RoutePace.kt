package ykws.android.maro.data.route

import ykws.android.maro.config.AppConfig

/**
 * The pace a route's trip figure plans at.
 *
 * A route's ETA starts on the set free-water pace, because a boat that has not moved yet has no
 * evidence to offer. Once it has been under way, the boat's own speed over ground is the better
 * answer — but only where that speed means something: taken inside a regulated zone or inside the
 * 300 m band, the reading is the speed the *limit* allowed, not the speed the boat will hold, so
 * those samples are dropped by the caller's [Sample.restricted] flag rather than averaged in.
 *
 * The window is reduced by a **high quantile, not a mean**: a boat's average is dragged down by
 * every swell, turn and idle minute, and a mean-paced ETA that promises more than the boat delivers
 * is the one error a navigator notices. A quantile near the top of the window is the pace the boat
 * actually holds when it is going well, which is the honest number to promise.
 *
 * Thin windows fall back to the set pace: below [MIN_SAMPLES] there is no pace to speak of, and a
 * guess dressed as a measurement is worse than the setting the user chose.
 *
 * Pure Kotlin, no Android dependency, so the reduction is a unit test rather than a device run.
 */
object RoutePace {

    /** How far back the window reaches (ms): the last ten minutes of sailing. */
    const val WINDOW_MS: Long = 10 * 60 * 1_000L

    /**
     * The quantile the window is reduced by — 0.75, the pace held for three quarters of the window
     * or better. High enough to be the boat's real pace, low enough not to be its best moment.
     */
    const val QUANTILE: Double = 0.75

    /** Below this many eligible samples the window is too thin to trust and the set pace stands. */
    const val MIN_SAMPLES: Int = 5

    /**
     * One speed reading.
     *
     * @property speedKn    speed over ground (kn).
     * @property atMs       when it was taken (epoch millis).
     * @property restricted true when the reading was taken inside a regulated zone or the 300 m
     *                      band, where it measures the limit rather than the boat.
     */
    data class Sample(
        val speedKn: Double,
        val atMs: Long,
        val restricted: Boolean = false
    )

    /**
     * @param samples   the recent readings, oldest first or in any order.
     * @param nowMs     the moment the pace is asked for.
     * @param setPaceKn the pace the user set, used whenever the window has nothing to say.
     * @return the pace to plan with (kn), always inside the setting's own span.
     */
    fun paceKn(samples: List<Sample>, nowMs: Long, setPaceKn: Double): Double {
        val eligible = samples
            .filter { !it.restricted && it.speedKn.isFinite() && it.speedKn > 0.0 }
            .filter { it.atMs <= nowMs && it.atMs >= nowMs - WINDOW_MS }
            .map { it.speedKn }
            .sorted()
        if (eligible.size < MIN_SAMPLES) return clamp(setPaceKn)
        return clamp(quantile(eligible))
    }

    /**
     * The nearest-rank quantile: the smallest sample at or above which [QUANTILE] of the window
     * sits. Sorting has already happened, so this is one index, not a second pass.
     */
    private fun quantile(sorted: List<Double>): Double {
        val rank = Math.ceil(sorted.size * QUANTILE).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    /** A pace outside the setting's span is a bad sample, not a new range. */
    private fun clamp(paceKn: Double): Double = paceKn.coerceIn(
        AppConfig.ROUTE_FREE_WATER_PACE_MIN_KN.toDouble(),
        AppConfig.ROUTE_FREE_WATER_PACE_MAX_KN.toDouble()
    )
}
