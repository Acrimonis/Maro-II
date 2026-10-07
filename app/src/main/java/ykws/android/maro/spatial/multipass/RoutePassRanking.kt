package ykws.android.maro.spatial.multipass

import kotlin.math.abs

/**
 * **The ladder's ranking, pure and total** — the preference-aware rule that tells which of the rungs
 * that have landed delivers the intent the user asked for, lifted out of the pass pipeline so the
 * comparison is a named seat of its own: a set of settled rung costs ranks, with no grid, world or
 * trace in hand.
 *
 * **The preference states an intent, the ranking answers which rung serves it** ([RoutePreference]):
 * - **Fast** leads on the **trip's total time** — the shortest trip wins outright.
 * - **Best** leads on the **total time provided the zone share stays within the budget**; a rung over
 *   the budget is beaten by the smaller share, so the budget is a gate and the clock decides under it.
 * - **Fun** leads on the **absolute zone seconds** — the time slowed by zones, however long the trip
 *   takes — and weighs the clock only as the last resort.
 *
 * Whatever the lead, a tie walks **one shared tail**: the remaining criteria in [betterPass]'s own
 * order, so the three stops differ only in what leads and never in how a lead tie is settled; and a
 * total tie falls to the rung **nearest the preference**.
 */
internal object RoutePassRanking {

    /**
     * **The λ loop's shared tail** — one pass against another, in a **stated order**: the **smaller
     * zone share** first, because that is the quantity the loop's λ is there to buy down; then the
     * **fewer metres inside a zone**, the same objective read in metres rather than in seconds; and only
     * then the **shorter clock**, so a corrective pass keeps a longer line wherever it is no worse on
     * the zone. Pure and total, so a pair of passes ranks with no grid, world or trace in hand.
     */
    internal fun betterPass(candidate: PassCost, incumbent: PassCost): Boolean {
        if (candidate.zoneShare != incumbent.zoneShare) return candidate.zoneShare < incumbent.zoneShare
        if (candidate.zoneMetresM != incumbent.zoneMetresM) return candidate.zoneMetresM < incumbent.zoneMetresM
        return candidate.durationSec < incumbent.durationSec
    }

    /**
     * **One pass's own three figures, in [betterPass]'s own order** — the comparator's whole input. The
     * absolute seconds Fun leads on are derivable from them ([zoneSeconds]) rather than a fourth field,
     * so the engine needs no extra plumbing to rank by the time a line spends slowed.
     */
    internal data class PassCost(
        /** The pass line's **zone share** — the share of its own time spent inside a priced zone. */
        val zoneShare: Double,
        /** The pass line's own metres standing inside a priced zone's interior. */
        val zoneMetresM: Double,
        /** The pass line's own clock, in seconds. */
        val durationSec: Double
    ) {
        /** The absolute seconds this line spends slowed by zones — [zoneShare] read in seconds. */
        val zoneSeconds: Double get() = zoneShare * durationSec
    }

    /** **One landed rung as the ranking reads it** — its ladder index and its own cost. */
    internal data class RungCost(val ladderIndex: Int, val cost: PassCost)

    /**
     * **The winner among the rungs that have landed**, as its position in [rungs] — or `null` where
     * nothing has landed. The rungs arrive in the order they settled; each carries its own ladder index
     * so a total tie can fall to the rung nearest [preference] whatever order they landed in.
     */
    internal fun bestRungIndex(
        preference: RoutePreference,
        budgetPct: Int,
        rungs: List<RungCost>
    ): Int? {
        if (rungs.isEmpty()) return null
        var best = 0
        for (i in 1 until rungs.size) {
            if (beats(preference, budgetPct, rungs[i], rungs[best])) best = i
        }
        return best
    }

    /** Whether [cand] beats [inc] under the preference's lead, then the shared tail, then the tie. */
    private fun beats(preference: RoutePreference, budgetPct: Int, cand: RungCost, inc: RungCost): Boolean {
        val lead = leadCompare(preference, budgetPct, cand.cost, inc.cost)
        if (lead != 0) return lead < 0
        if (betterPass(cand.cost, inc.cost)) return true
        if (betterPass(inc.cost, cand.cost)) return false
        return abs(cand.ladderIndex - preference.index) < abs(inc.ladderIndex - preference.index)
    }

    /** The preference's own lead criterion, or 0 where the two are level on it. */
    private fun leadCompare(
        preference: RoutePreference,
        budgetPct: Int,
        cand: PassCost,
        inc: PassCost
    ): Int = when (preference) {
        RoutePreference.FAST -> cmp(cand.durationSec, inc.durationSec)
        RoutePreference.AROUND -> cmp(cand.zoneSeconds, inc.zoneSeconds)
        RoutePreference.BEST -> {
            // The budget is a gate: a rung inside it beats one over it whatever the clock; among rungs
            // on the same side of the gate the clock decides, and outside it the smaller share does.
            val gate = budgetPct / 100.0
            val candIn = cand.zoneShare <= gate
            val incIn = inc.zoneShare <= gate
            when {
                candIn && !incIn -> -1
                !candIn && incIn -> 1
                candIn -> cmp(cand.durationSec, inc.durationSec)
                else -> cmp(cand.zoneShare, inc.zoneShare)
            }
        }
    }

    /** Three-way compare of two figures: -1 where [a] is smaller, 1 where larger, 0 where level. */
    private fun cmp(a: Double, b: Double): Int = when {
        a < b -> -1
        a > b -> 1
        else -> 0
    }
}
