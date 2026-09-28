package ykws.android.maro.data.model

/**
 * **One priced source a candidate line may drop** — the dimension the offers are read along.
 *
 * A source is a *price* the search pays, never a physical obstacle, so dropping it can only make the
 * water cheaper to cross; whether that was worth it is decided by the candidate's **own clock**, which
 * still obeys every limit in force.
 */
enum class RouteOfferSource {

    /** The regulated speed zones' limits: priced by the search, obeyed by the clock. */
    SPEED_ZONES,

    /** The 300 m coastal band's price — an aversion the clock never reads. */
    ZONE300,

    /**
     * **Both prices at once, the zones' first** — the cumulative rung of the ladder R53 names: the line
     * with no aversion left, drawn by the pass that drops [SPEED_ZONES] and [ZONE300] together.
     */
    SPEED_ZONES_AND_ZONE300
}

/**
 * **One candidate pass, as the file declares it** (R61) — the prices it leaves out and the [source] its
 * line is offered under, so the configuration names both and neither is inferred at the call site.
 *
 * @property source the id the offer is published under.
 * @property drops  the prices this pass prices as open water — the speed zones at λ = 0, the band out of
 *                  the grid's base.
 */
data class RouteCandidatePass(
    val source: RouteOfferSource,
    val drops: Set<RouteOfferSource>
)

/**
 * **One alternative the engine offers beside the drawn line**: what the same pipeline draws with prices
 * dropped, its own clock, and the seconds that would be saved against the settled route.
 *
 * The saving is the whole point — a candidate that saves less than the configured floor is not built into
 * an offer at all — and the line is carried because every candidate is **drawn at once**, the selected
 * one at full strength and the others dimmed, so stepping the set moves the emphasis rather than the
 * drawing.
 *
 * The candidate a user confirms is not read from here afterwards: the answer itself becomes the
 * followed plan, so this type is never a plan's own home — it is the set of *what ifs* behind one.
 *
 * @property source      the price (or prices) dropped to draw [points].
 * @property points      the candidate's own polyline, start first and resolved destination last.
 * @property legTimesSec the candidate's planned time per leg, in the polyline's own order, so
 *                       `legTimesSec[i]` times the leg from `points[i]` to `points[i + 1]`.
 * @property legSpeedsMps the pace made good over each leg, m/s, beside those times.
 * @property distanceM   the candidate's total length in metres.
 * @property durationSec the candidate's total time, and the figure the saving is read against.
 * @property savingSec   what the settled route would spend **more** than this one — strictly positive,
 *                       an offer being built only where there is a saving to state.
 */
data class RouteOffer(
    val source: RouteOfferSource,
    val points: List<RoutePoint>,
    val legTimesSec: List<Double>,
    val legSpeedsMps: List<Double>,
    val distanceM: Double,
    val durationSec: Double,
    val savingSec: Double
)

/**
 * **The saving a candidate states, or null where it clears no floor** (R63): the seconds the settled
 * line would spend more than [candidateSec], kept only where that is a sale of at least [minSavingPct]
 * of the settled trip's **own clock**.
 *
 * One home for the rule, so the engine that builds the offers and the test that reads the floor agree by
 * construction rather than by two copies of the arithmetic. The floor is a share of the **trip** and
 * deliberately not the λ loop's ±20 % band, that band measuring the slowed share and not the trip.
 *
 * `minSavingPct` of `0` offers every saving, and a trip of zero length can floor nothing out — a line
 * that saves nothing is the one shape refused at every floor.
 */
fun routeCandidateSavingSec(
    settledSec: Double,
    candidateSec: Double,
    minSavingPct: Int
): Double? {
    val savingSec = settledSec - candidateSec
    if (savingSec <= 0.0) return null
    if (minSavingPct > 0 && settledSec > 0.0 && savingSec < settledSec * minSavingPct / 100.0) return null
    return savingSec
}
