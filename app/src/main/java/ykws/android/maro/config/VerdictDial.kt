package ykws.android.maro.config

import kotlin.math.roundToInt

/**
 * **The verdict's dial — the slow-water weight the crown is weighed by, as its six stops.**
 *
 * The dial is the crown's one preference, and this table is its own home: the ranking weighs every drawn
 * rung's `score = trip time + weight × seconds spent under a limit`, so a stop states that one second in
 * slow water counts as `1 + weight` ordinary seconds. The six stops are **Fastest** (weight 0, the
 * genuinely fastest line), **1x, 2x, 3x, 4x** and **Ignore time**.
 *
 * **The top stop is a sentinel, never a number.** It is modelled here as a stop whose [weight] is `null`,
 * which is this layer's own documented way of saying *no weight at all*: the ranking then weighs the
 * slow-water seconds alone and lets the clock break a dead heat. No magic figure for it is buried
 * anywhere else, and the stop is named *Ignore time* in both locales and is never numbered.
 *
 * [weight] is what the ranking reads; [index] is the stop's position on the dial's own track, which is
 * what a stored setting holds. The weight's **default** is `route.avoid.slowWater.weight`, read through
 * [AppConfig.routeAvoidSlowWaterWeight] — the one home for that value — so a shipped default keeps the
 * crown it ships with.
 */
internal enum class VerdictDial(val index: Int, val weight: Double?) {

    /** The weight 0 stop — the genuinely fastest line, the slow seconds weighing nothing at all. */
    FASTEST(0, 0.0),

    /** One extra second paid for every second spent in slow water. */
    ONE(1, 1.0),

    /** Two extra seconds paid for every second spent in slow water. */
    TWO(2, 2.0),

    /** Three extra seconds paid for every second spent in slow water. */
    THREE(3, 3.0),

    /** The shipped default — four extra seconds paid for every second spent in slow water. */
    FOUR(4, 4.0),

    /** The sentinel: **no weight at all**, the slow-water seconds deciding alone and the clock breaking a dead heat. */
    IGNORE_TIME(5, null);

    companion object {

        /** The stops in dial order — the track a stored value runs along. */
        val stops: List<VerdictDial> = values().toList()

        /** The stop at a stored [index], clamped to the track's own bounds like every other bounded slider. */
        fun ofIndex(index: Int): VerdictDial = values()[index.coerceIn(0, values().lastIndex)]

        /**
         * The stop a **default weight** stands at — the nearest numbered stop, the sentinel's own stop
         * apart, since a weight is exactly what the sentinel does not carry.
         */
        fun ofWeight(weight: Double): VerdictDial =
            values()[weight.roundToInt().coerceIn(0, FOUR.index)]
    }
}
