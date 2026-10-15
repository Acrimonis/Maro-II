package ykws.android.maro.spatial.multipass

/**
 * **The ladder's ranking, one rate and one sum** — the rule that tells which of the rungs that have
 * landed delivers the intent the user asked for, lifted out of the pass pipeline so the comparison is a
 * named seat of its own: a set of settled rung costs ranks, with no grid, world or trace in hand.
 *
 * **The crown is one line of arithmetic a reader can do on the card's own figures**:
 * `score = trip time + weight × slow-water seconds`, the lowest score winning — so the weight states
 * that one second spent under a regulated limit is worth `1 + weight` ordinary seconds. Both ends fall
 * out of that one formula: a weight of 0 crowns the genuinely fastest line, and the dial's top stop
 * carries **no weight at all** — a `null` weight, the dial's own sentinel ([VerdictDial.IGNORE_TIME]) —
 * and weighs the slow seconds alone.
 *
 * **What this seat no longer reads (2026-10-15).** The rule it replaces led on three measures — the total
 * time, the total time *inside* the slow-water budget, and the absolute zone seconds — and settled a lead
 * tie on a shared tail, so the ranking read a **ring share**, a **ring metre** count and the **clock**,
 * and a total tie fell to the rung nearest the preference. The budget is no longer a gate, the share and
 * the metre figures are no longer ranking inputs, and the nearest-preference tie-break is gone with them:
 * the share survives as a reading published on the line, and a ring's own slow seconds reach the crown
 * inside [PassCost.slowWaterSec] alone, weighed exactly like every other limit's.
 *
 * **A tolerance, or the crown flaps.** Nearly every line carries some slow seconds, so a rung takes the
 * crown only where it leads the one holding it by **more** than [bestRungIndex]'s tolerance: a smaller
 * difference is noise between two lines the boat is driven much the same way, and leaves the crown where
 * it stands. Both figures arrive as parameters, because both are preferences — where they come from is
 * the engine's business and never this seat's.
 */
internal object RoutePassRanking {

    /**
     * **One pass's own two figures** — the comparator's whole input, as the engine settles them: the
     * seconds the line spends under a regulated limit, and its clock.
     */
    internal data class PassCost(
        /**
         * The seconds the pass line spends under a regulated limit — a ring's interior, the 300 m band,
         * and the ramps that serve a limit; the boat's own cornering floor is the boat's own water and
         * stands **outside** the figure. These are **plain seconds read off the timed legs**, never the
         * physics excess the drawing prices, so the verdict and the drawn line differ on purpose: the
         * drawing pays what the slow water costs over the pace, the crown counts how long the boat is
         * under a limit.
         *
         * Every limit's **severity stands at 1.0** — this hop reads no severity table — so the weighted
         * total the score weighs **is** this figure.
         */
        val slowWaterSec: Double,
        /** The pass line's own clock, in seconds. */
        val durationSec: Double
    )

    /**
     * **The winner among the rungs that have landed**, as its position in [costs] — or `null` where
     * nothing has landed. The rungs arrive in the order they settled and the fold is one walk: a rung
     * takes the lead from the one holding it only where it leads by more than [toleranceSec], so the
     * **first** of two lines the boat is driven the same way keeps the crown. [weight] is the dial's own
     * stop's weight, its top stop's `null` included.
     */
    internal fun bestRungIndex(weight: Double?, toleranceSec: Double, costs: List<PassCost>): Int? {
        if (costs.isEmpty()) return null
        var best = 0
        for (i in 1 until costs.size) {
            if (beats(weight, toleranceSec, costs[i], costs[best])) best = i
        }
        return best
    }

    /**
     * Whether [candidate] takes the crown from [incumbent] under [weight] **and** the [toleranceSec]
     * that guards the flip: a candidate leading by less than the tolerance leaves the incumbent standing,
     * whatever measure the stop leads on.
     *
     * A `null` [weight] is the dial's **ignore-time** stop, and here it is the comparator's own special
     * case: the slow-water seconds decide alone, so a candidate ahead of the incumbent by more than the
     * tolerance takes the crown, one behind it by more than the tolerance does not, and only an **exact**
     * tie in the slow seconds falls through to the clock — which the other stops' sum never needs, since
     * its own figure carries the clock already.
     */
    private fun beats(weight: Double?, toleranceSec: Double, candidate: PassCost, incumbent: PassCost): Boolean {
        if (weight == null) {
            val margin = incumbent.slowWaterSec - candidate.slowWaterSec
            if (margin > toleranceSec) return true
            if (margin < -toleranceSec) return false
            return margin == 0.0 && candidate.durationSec < incumbent.durationSec
        }
        return incumbent.score(weight) - candidate.score(weight) > toleranceSec
    }

    /** **The one figure the crown is decided on** — the trip time plus the weight times the slow seconds. */
    private fun PassCost.score(weight: Double): Double = durationSec + weight * slowWaterSec
}
