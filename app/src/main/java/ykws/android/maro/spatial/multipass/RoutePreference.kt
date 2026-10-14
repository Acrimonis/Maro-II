package ykws.android.maro.spatial.multipass

import androidx.annotation.StringRes
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig

/**
 * **The acquisition's driving preference — the ladder's three stops, their word and the λ each is solved at.**
 *
 * One home for the ladder's shape: its three stops, their indices, their λ and the thresholds between them,
 * so the Settings slider, the drawer's quick access and the engine's own ranking all read this, and no
 * surface can name a stop the engine does not seat. The three stops are listed most-averse first, in the
 * ladder's own index order — `around`, `best` and `fast` — which is the order the acquisition's first
 * column lists its rungs in.
 *
 * **Each stop's λ is a key of its own** — `route.avoid.ladder.around|best|fast.lambda` in `maro.properties`,
 * read through [AppConfig] — because the three values are the ladder's one lever and the properties file is
 * where every value lives; this enum is what maps an index and a word onto them, and no subclass below
 * holds a number.
 *
 * The word each stop carries is the **preference family's** own string (`route_computation_*`), the one a
 * user reads as an intent, and it is deliberately kept apart from the acquisition row's own rung vocabulary
 * (`route_rung_*`), which names where the line goes rather than what the user wants. Being a `@StringRes`
 * id, no engine and no pure rule holds user-facing text, and both locales carry every key.
 */
internal enum class RoutePreference(
    /** The ladder index this stop's rung sits at — 0 around, 1 best, 2 fast. */
    val index: Int,
    /** The id of the word a user reads for this stop — the preference family's own line. */
    @StringRes val labelResId: Int
) {

    /** λ `route.avoid.ladder.around.lambda` — the least time slowed by zones, around them where it can. */
    AROUND(0, R.string.route_computation_around) {
        override val lambda: Double get() = AppConfig.routeAvoidLadderAroundLambda
    },

    /** λ `route.avoid.ladder.best.lambda` — the shortest clock that keeps its slow water under the budget. */
    BEST(1, R.string.route_computation_balanced) {
        override val lambda: Double get() = AppConfig.routeAvoidLadderBestLambda
    },

    /** λ `route.avoid.ladder.fast.lambda` — the shortest clock, whatever water it crosses. */
    FAST(2, R.string.route_computation_through) {
        override val lambda: Double get() = AppConfig.routeAvoidLadderFastLambda
    };

    /**
     * The fixed λ this stop's rung is solved at, read from its own properties key as it is asked for — so
     * the file stays the value's one home and an edit reaches the next arming. No rung is ever corrected:
     * this value is the whole of what separates one acquisition of an arming from its siblings.
     */
    abstract val lambda: Double

    companion object {

        /**
         * The stop a stored aversion snaps to — the midpoints between the rungs, kept as **code constants**
         * because the dial is a preference rather than a value of the ladder. They assume the shipped
         * 5 / 2.5 / 0 spacing, so a ladder edited to uneven values leaves this mapping measuring the old
         * gaps.
         */
        fun of(aversionKn: Double): RoutePreference = when {
            aversionKn <= 1.25 -> FAST
            aversionKn <= 3.75 -> BEST
            else -> AROUND
        }

        /** The stop at a ladder [index], clamped to the ladder's own bounds. */
        fun ofIndex(index: Int): RoutePreference = when (index) {
            0 -> AROUND
            2 -> FAST
            else -> BEST
        }
    }
}
