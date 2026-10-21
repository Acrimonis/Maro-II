package ykws.android.maro.data.route

import ykws.android.maro.config.AppConfig

/**
 * The pace a route's trip figure plans at — the reference read off the smoothed speed curve of the
 * current leg.
 *
 * The model: the boat's own free-water speed-over-ground readings are reduced to a **recency-weighted
 * 75th percentile**. The percentile drops the slowest quarter, so a lull never drags the cruise down; the
 * recency ramp gives the newest sailing more weight ([recentWeightPct]), so a genuine change of pace takes
 * over once it fills the recent end of the curve, while a blip too short to be a real level stays too small
 * a share to move it. There is no fixed window and no dead-band: the weighted percentile is the smoothed
 * curve's plateau, read live.
 *
 * Restricted readings — taken inside a regulated speed zone or the 300 m band, where the limit rather than
 * the boat sets the number — are dropped by the caller's [Sample.restricted] flag before the reduction.
 *
 * Thin buffers fall back to the set pace: below [MIN_SAMPLES] there is no plateau to read, and a guess
 * dressed as a measurement is worse than the value the caller already chose.
 *
 * Pure Kotlin, no Android dependency, so the reduction is a unit test rather than a device run.
 */
object RoutePace {

    /** How far back the buffer reaches (ms): the last ten minutes of sailing. */
    const val WINDOW_MS: Long = 10 * 60 * 1_000L

    /** The quantile the curve is reduced by — 0.75, the pace held for three quarters of the buffer or better. */
    const val QUANTILE: Double = 0.75

    /** Below this many eligible samples the buffer is too thin to trust and the set pace stands. */
    const val MIN_SAMPLES: Int = 5

    /** One speed reading. */
    data class Sample(
        val speedKn: Double,
        val atMs: Long,
        val restricted: Boolean = false
    )

    /**
     * @param samples          the recent readings, oldest first or in any order.
     * @param nowMs            the moment the pace is asked for.
     * @param setPaceKn        the pace the user set, used whenever the buffer has nothing to say.
     * @param recentWeightPct  0–100: the extra share the newest sailing carries against the oldest, linear.
     * @return the pace to plan with (kn), always inside the setting's own span.
     */
    fun paceKn(
        samples: List<Sample>,
        nowMs: Long,
        setPaceKn: Double,
        recentWeightPct: Double = 0.0
    ): Double {
        val eligible = samples
            .filter { !it.restricted && it.speedKn.isFinite() && it.speedKn > 0.0 }
            .filter { it.atMs <= nowMs && it.atMs >= nowMs - WINDOW_MS }
            .sortedBy { it.atMs }
        if (eligible.size < MIN_SAMPLES) return clamp(setPaceKn)
        return clamp(weightedQuantile(eligible, QUANTILE, recentWeightPct))
    }

    /**
     * The recency-weighted quantile: each sample's weight is `1 + (recentWeightPct / 100) × rank`, rank
     * running 0 at the oldest to 1 at the newest, so 0 % is the plain quantile and 100 % doubles the
     * newest sample's weight against the oldest. Sorted by speed, the answer is the first speed whose
     * cumulative weight crosses [quantile] of the total.
     */
    private fun weightedQuantile(byTime: List<Sample>, quantile: Double, recentWeightPct: Double): Double {
        val n = byTime.size
        val denom = (n - 1).coerceAtLeast(1)
        val weighted = byTime.mapIndexed { i, s ->
            s.speedKn to (1.0 + (recentWeightPct / 100.0) * (i.toDouble() / denom))
        }
        val total = weighted.sumOf { it.second }
        val target = total * quantile
        var acc = 0.0
        for ((speed, weight) in weighted.sortedBy { it.first }) {
            acc += weight
            if (acc >= target) return speed
        }
        return byTime.last().speedKn
    }

    /** A pace outside the setting's span is a bad sample, not a new range. */
    private fun clamp(paceKn: Double): Double = paceKn.coerceIn(
        AppConfig.ROUTE_FREE_WATER_PACE_MIN_KN.toDouble(),
        AppConfig.ROUTE_FREE_WATER_PACE_MAX_KN.toDouble()
    )
}
