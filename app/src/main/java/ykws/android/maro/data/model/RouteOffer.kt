package ykws.android.maro.data.model

/**
 * **One priced source a candidate computation may drop** — the dimension the computations are declared
 * along.
 *
 * A source is a *price* the search pays, never a physical obstacle, so dropping it can only make the
 * water cheaper to cross; whether that was worth it is read from the candidate's **own clock**, which
 * still obeys every limit in force.
 */
enum class RouteOfferSource {

    /** The regulated speed zones' limits: priced by the search, obeyed by the clock. */
    SPEED_ZONES,

    /** The 300 m coastal band's price — an aversion the clock never reads. */
    ZONE300,

    /**
     * **Both prices at once, the zones' first** — the cumulative rung the passes name: the line
     * with no aversion left, drawn by the pass that drops [SPEED_ZONES] and [ZONE300] together.
     */
    SPEED_ZONES_AND_ZONE300
}

/**
 * **One candidate computation, as the file declares it** (R61) — the prices it leaves out and the
 * [source] its line is declared under, so the configuration names both and neither is inferred at the
 * call site.
 *
 * @property source the id the computation is declared under.
 * @property drops  the prices this pass prices as open water — the speed zones at λ = 0, the band out of
 *                  the grid's base.
 */
data class RouteCandidatePass(
    val source: RouteOfferSource,
    val drops: Set<RouteOfferSource>
)
