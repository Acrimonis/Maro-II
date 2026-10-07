package ykws.android.maro.spatial.multipass

import androidx.annotation.StringRes
import ykws.android.maro.R

/**
 * **The acquisition's driving preference — the ladder's three stops, their λ and their word.**
 *
 * One home for the ladder's three λ values and the thresholds between them: the Settings slider, the
 * drawer's quick access and the engine's own ranking all read this, so no surface can name a stop the
 * engine does not seat. The three stops are listed most-averse first, in the ladder's own index order —
 * `around` at λ 5, `best` at 2.5 and `fast` at 0 — which is the order the acquisition's first column
 * lists its rungs in.
 *
 * The word each stop carries is the **preference family's** own string (`route_computation_*`), the one
 * a user reads as an intent, and it is deliberately kept apart from the acquisition row's own rung
 * vocabulary (`route_rung_*`), which names where the line goes rather than what the user wants. Being a
 * `@StringRes` id, no engine and no pure rule holds user-facing text, and both locales carry every key.
 */
internal enum class RoutePreference(
    /** The ladder index this stop's rung sits at — 0 around, 1 best, 2 fast. */
    val index: Int,
    /** The fixed λ this stop's rung is solved at. */
    val lambda: Double,
    /** The id of the word a user reads for this stop — the preference family's own line. */
    @StringRes val labelResId: Int
) {

    /** λ 5 — the least time slowed by zones, around them where it can. */
    AROUND(0, 5.0, R.string.route_computation_around),

    /** λ 2.5 — the shortest clock that still keeps slow water under the budget. */
    BEST(1, 2.5, R.string.route_computation_balanced),

    /** λ 0 — the shortest clock, whatever water it crosses. */
    FAST(2, 0.0, R.string.route_computation_through);

    companion object {

        /** The stop a stored aversion snaps to — the midpoints between the evenly spaced rungs. */
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
