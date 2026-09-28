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
    ZONE300
}

/**
 * **One alternative the engine offers beside the drawn line**: what the same pipeline draws with one
 * source's price dropped, its own clock, and the seconds that would be saved against the settled route.
 *
 * The saving is the whole point — a candidate that saves nothing is not built into an offer at all —
 * and the line is carried because every candidate is **drawn at once**, the selected one at full
 * strength and the others dimmed, so stepping the set moves the emphasis rather than the drawing.
 *
 * The candidate a user confirms is not read from here afterwards: the answer itself becomes the
 * followed plan, so this type is never a plan's own home — it is the set of *what ifs* behind one.
 *
 * @property source      the price that was dropped to draw [points].
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
