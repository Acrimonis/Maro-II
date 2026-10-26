package ykws.android.maro.spatial.multipass

/**
 * **The ladder's ranking, one rate and one sum** — the rule that tells which of the rungs that have
 * landed delivers the intent the user asked for, lifted out of the pass pipeline so the comparison is a
 * named seat of its own: a set of settled rung costs ranks, with no grid, world or trace in hand.
 *
 * **The crown is one line of arithmetic a reader can do on the card's own figures**:
 * `score = trip time + weight × slow-water seconds`, the lowest score winning — so the weight states
 * that one second under a limit, already weighed by that limit's own severity, is worth `1 + weight`
 * ordinary seconds. Both ends fall
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
 *
 * **Every settled rung carries a rank of its own, and the tie the tolerance allows is broken by an
 * explicit chain** (2026-10-15). The tolerance still draws the **groups** — it separates nothing inside
 * itself — but the ranks now read `1, 2, 3` where the rule this seat replaces read the shared `1, 1, 3`:
 * [tieBreakChain] is that order, the exact measure first, then the fewer seconds under a limit, then the
 * shorter trip time, then the arrival order. **The crown heads the group it wins**, whatever the chain
 * prefers inside it, so the tolerance's own winner is always the rank-1 rung and a sub-tolerance
 * difference can never swap the crown a surface marks.
 *
 * **The order and the crown are one seat, and no surface duplicates the rule.** [bestRungIndex] names
 * the crown and [ranks] every landed rung's place under the very same sum and tolerance, so a panel
 * reads both straight off the engine's own fold ([ykws.android.maro.spatial.RouteVerdict]) rather than
 * re-deriving the arithmetic — the tolerance that draws the groups is the tolerance that guards the
 * flip, and rank 1 is always the crown.
 */
internal object RoutePassRanking {

    /**
     * **One pass's own two figures** — the comparator's whole input, as the engine settles them: the
     * seconds the line spends under a regulated limit, and its clock.
     */
    internal data class PassCost(
        /**
         * The seconds the pass line spends under a regulated limit — a ring's interior, the 300 m band and
         * the ramps that serve a limit — **each weighed by its own limit's severity**, the boat's own
         * cornering floor standing **outside** the figure. The measurement is the clock's plain seconds;
         * the weighting makes the figure the line's own **excess under its limits** — `(pace − limit) / pace`
         * floored at zero, so nothing at or above the cruise weighs anything — which is why the crown's
         * currency and the drawing's price now agree in kind rather than differing on purpose. A ramp's
         * second rides the limit of the slow run it serves, and a leg meeting no limit weighs nothing.
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
        val margin = incumbent.measure(weight) - candidate.measure(weight)
        if (margin > toleranceSec) return true
        if (margin < -toleranceSec) return false
        return weight == null && margin == 0.0 && candidate.durationSec < incumbent.durationSec
    }

    /**
     * **Every landed rung's rank, one per entry of [costs] and no two alike** — the crown's own order
     * under the very same sum and tolerance [bestRungIndex] weighs by, every settled rung standing on a
     * rank of its own (`1, 2, 3`, and never the shared `1, 1, 3` of the rule this seat replaces,
     * 2026-10-15).
     *
     * **The tolerance draws the groups; the chain settles what a group holds level.** A rung's group is
     * one more than the number of rungs that **lead it by more than [toleranceSec]**, so two rungs the
     * tolerance keeps together stand in one group however [tieBreakChain] reads them, while a rung the
     * tolerance has clearly left behind never shares the slower group's ranks. Inside a group the ranks
     * follow the chain's own order, stated in full at [tieBreakChain].
     *
     * **The crown heads its own group, and no chain can move it.** The crown is [bestRungIndex]'s winner
     * and always rank 1 — the tolerance holds it there against a sub-tolerance challenger the chain would
     * otherwise prefer — so the checkmark a surface draws on rank 1 is the engine's own winner, always.
     * [weight] is the dial's own stop's weight, its top stop's `null` included — where the slow-water
     * seconds alone decide.
     */
    internal fun ranks(weight: Double?, toleranceSec: Double, costs: List<PassCost>): List<Int> {
        if (costs.isEmpty()) return emptyList()
        val crowned = bestRungIndex(weight, toleranceSec, costs) ?: return emptyList()
        // The tolerance's own grouping: how many rungs clearly lead each one — a group's base.
        val held = costs.indices.map { i ->
            costs.indices.count { j -> leads(weight, toleranceSec, costs[j], costs[i]) }
        }
        val chain = tieBreakChain(weight, costs)
        val ranks = IntArray(costs.size)
        var next = 1
        for (base in held.distinct().sorted()) {
            val group = costs.indices.filter { held[it] == base }.sortedWith(chain)
            // The crown is its group's head whatever the chain prefers inside it: the tolerance names the
            // crown, so a sub-tolerance difference can never move it off rank 1.
            val ordered = if (crowned in group) listOf(crowned) + group.filter { it != crowned } else group
            for (i in ordered) ranks[i] = next++
        }
        return ranks.toList()
    }

    /**
     * **The chain that breaks a tie inside one group** — the explicit order a reader can follow, applied
     * to the rungs [toleranceSec] holds level (2026-10-15):
     *
     * 1. the **exact measure** ([measure]) — the same sum, compared without the tolerance, so the cheaper
     *    line leads on the arithmetic itself; under the `null` weight this is the slow seconds alone, and
     *    the step below decides nothing of its own;
     * 2. **fewer seconds under a limit** ([PassCost.slowWaterSec]) — the cleaner line;
     * 3. **shorter trip time** ([PassCost.durationSec]);
     * 4. **first landed** — the rung's own position in [costs], the engine's arrival order, so a rung the
     *    chain can tell from its twin no other way keeps the place it settled in, the incumbent first.
     */
    private fun tieBreakChain(weight: Double?, costs: List<PassCost>): Comparator<Int> =
        compareBy<Int>(
            { costs[it].measure(weight) },
            { costs[it].slowWaterSec },
            { costs[it].durationSec },
            { it }
        )

    /**
     * Whether [candidate] leads [incumbent] on the ranking's own measure by more than [toleranceSec] —
     * the sum under a numbered stop, the slow-water seconds alone under the `null` weight, always the
     * tolerance that guards the flip. The reading is `beats`'s own margin without its dead-heat clock
     * fallback, so what it draws is a **group boundary** and never a rank: the clock that settles an
     * exact tie under the top stop moves no boundary, and the chain alone reads inside one.
     */
    private fun leads(weight: Double?, toleranceSec: Double, candidate: PassCost, incumbent: PassCost): Boolean =
        incumbent.measure(weight) - candidate.measure(weight) > toleranceSec

    /** **The one figure the crown is decided on** — the trip time plus the weight times the slow seconds. */
    private fun PassCost.score(weight: Double): Double = durationSec + weight * slowWaterSec

    /**
     * **The one figure a stop weighs** — [score] under a numbered stop, the slow-water seconds alone under
     * the dial's own `null` weight ([VerdictDial.IGNORE_TIME]), so the sum and the top stop read through
     * one seat and neither the fold nor the chain carries the branch.
     */
    private fun PassCost.measure(weight: Double?): Double = if (weight == null) slowWaterSec else score(weight)
}
