package ykws.android.maro.spatial.multipass

/**
 * **The keep rules, pure and total** — the λ loop's comparator with its input [PassCost], and the splice
 * rule a fine line is kept by, lifted out of the pass pipeline so the ranking and the splice decision are
 * a named seat of their own: a pair of passes ranks, and a fine line is kept, with no grid, world or
 * trace in hand.
 */
internal object RoutePassRules {

    /**
     * **The λ loop's keep rule** — one pass against another, in a **stated order**: the **smaller zone
     * share** first, because that is the quantity the loop's λ is there to buy down; then the **fewer
     * metres inside a zone**, the same objective read in metres rather than in seconds; and only then
     * the **shorter clock**, so a corrective pass keeps a longer line wherever it is no worse on the
     * zone. Pure and total, so a pair of passes ranks with no grid, world or trace in hand.
     */
    internal fun betterPass(candidate: PassCost, incumbent: PassCost): Boolean {
        if (candidate.zoneShare != incumbent.zoneShare) return candidate.zoneShare < incumbent.zoneShare
        if (candidate.zoneMetresM != incumbent.zoneMetresM) return candidate.zoneMetresM < incumbent.zoneMetresM
        return candidate.durationSec < incumbent.durationSec
    }

    /** One pass's own three figures, in [betterPass]'s own order — the comparator's whole input. */
    internal data class PassCost(
        /** The pass line's **zone share** — [slowShares]'s ring-interior reading, the quantity λ buys down. */
        val zoneShare: Double,
        /** The pass line's own metres standing inside a priced zone's interior. */
        val zoneMetresM: Double,
        /** The pass line's own clock, in seconds. */
        val durationSec: Double
    )

    /**
     * **The fine line's keep rule** — the fine line replaces the incumbent only where it is
     * strictly faster **and** no worse in its **slow share**, both read off the same two timed lines
     * with [zoneSlowShare]. The clock alone is λ-blind: a fine line quicker on the clock but spending
     * more of its own time slowed would undo the λ loop the moment it is spliced.
     */
    internal fun fineSpliceBetter(fine: TimedLine, incumbent: TimedLine, paceKn: Double): Boolean =
        fine.durationSec < incumbent.durationSec &&
            zoneSlowShare(fine, paceKn) <= zoneSlowShare(incumbent, paceKn)
}
