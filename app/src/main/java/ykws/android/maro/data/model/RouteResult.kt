package ykws.android.maro.data.model

/**
 * **One slow-water entry of a route's report** — the seconds spent under a single speed limit, or in
 * the 300 m band when [isBand] is set. [limitKn] is the enforced limit the entry stands for (the band's
 * own limit when [isBand]); the seconds are the route's own time on that limit's water **alone** — the
 * legs whose midpoint stands on it — with nothing folded in from neighbouring water, so a limit's
 * figure is never inflated by slow legs on open water.
 */
data class RouteSlowLimit(
    val limitKn: Double,
    val seconds: Double,
    val isBand: Boolean = false
)

/**
 * What a route engine answers with.
 *
 * A failure is a named outcome rather than an empty success, so the UI can tell "no water route
 * connects these two points" from "this position is not on the covered water at all" — the two
 * need different words and neither may be silently rendered as a straight line over land.
 */
sealed interface RouteResult {

    /**
     * A route was found.
     *
     * Everything here is what **any** engine must answer, because it is what a reader of the plan
     * needs: where the line goes, what each leg costs and what the whole of it came to. What an engine
     * counts while it answers is **not** here: the two engines that carried dossiers were removed on
     * 2026-09-22, so there is no instrumentation field and no engine-specific vocabulary left for a
     * reader to depend on.
     *
     * @property points            the polyline, start first and the resolved destination last.
     * @property distanceM         total length in metres.
     * @property durationSec       the route's planned seconds: the drawn line's own clock at the limits
     *                             in force, each leg timed at the limit in force at its midpoint and
     *                             capped by the pace. [legTimesSec] sums to it exactly, with no fairing
     *                             residual.
     * @property destinationMoved  true when the aimed destination resolved elsewhere — land, shallow
     *                             water, or too near either — and the route ends at the nearest
     *                             clear point instead.
     * @property forcedCrossingZoneNames  the priced speed zones' own names, when the route had to
     *                             enter one: a crossing is forbidden while a way around exists, so a
     *                             name here says no way around was found and the crossing was priced
     *                             and taken. A zone a route end stands in is never named — it is no
     *                             crossing, the end being already inside it. Empty on an ordinary route. Deliberately **not** a field
     *                             of any drawn or saved type: `Track.route` is serialized and
     *                             this is not, so nothing here reaches the proto. **Kept** although
     *                             the dummy never fills it: the ladder's doubled-reach retry alone
     *                             reads it, so it is the shape a real engine fills.
     * @property crossedSlowZoneNames  the slow water the drawn line enters, by name — the presence
     *                             fact beside the crossing above, read from the water and never from
     *                             the price. A record, not a drawn fact: it reaches no proto.
     */
    data class Success(
        val points: List<RoutePoint>,
        /**
         * The planned time of each leg, in the same order as [points] — `legTimesSec[i]` is the time
         * the plan allots the leg from `points[i]` to `points[i + 1]`, so the list is one shorter
         * than the polyline and the cumulative sum is exactly [durationSec]: the drawn line is its own
         * clock, with no fairing residual to fold anywhere.
         *
         * It is what makes the plan's own pace recoverable per leg — the enforced limit in force at the
         * leg's midpoint, capped by the pace — and it is exposed here rather than recomputed by a caller
         * because the limit resolution behind it is the engine's own work: a second spelling outside
         * would be a second answer waiting to disagree.
         */
        val legTimesSec: List<Double> = emptyList(),
        /**
         * **The speed made good** over each leg, in m/s, beside [legTimesSec] and in the same order —
         * one set of numbers the drawn line, the panel's figures and the saved track all read.
         *
         * It is the enforced limit in force at the leg's midpoint, capped by the pace — the clock is
         * what derives it, `distance / time`, so it always equals the clock beside it. It is the
         * engine's own reading and never a second computation by a caller, for the same reason the leg
         * times are.
         */
        val legSpeedsMps: List<Double> = emptyList(),
        val distanceM: Double,
        val durationSec: Double,
        val destinationMoved: Boolean,
        val forcedCrossingZoneNames: List<String> = emptyList(),
        /**
         * **The slow water the drawn line entered, beside [forcedCrossingZoneNames] and answering a
         * different question** — the zones whose own limit in force stands below the cruise pace,
         * whether or not the search was charged for them. It is a **record and not a drawn fact**:
         * nothing on the map, in the clock or in a saved track moves because of it, and it reaches no
         * proto.
         *
         * The two facts are read in two different places by design. [forcedCrossingZoneNames] is the
         * **price**'s answer — the crossings the search could not avoid; this one is the **water**'s —
         * what the clock and the line's colour obey. A zone the price law calls free (its `speedRoute`
         * override at or above the pace, or `route.avoid.speedZone.enabled` off) is named here and
         * nowhere in [forcedCrossingZoneNames]; a zone at or above the pace is named by neither, since
         * it costs no second and shows in no colour. Empty on an ordinary route, on a partial line and
         * on a saved route read back.
         */
        val crossedSlowZoneNames: List<String> = emptyList(),
        /**
         * **The time the route spends in slow water, one entry per speed limit** — the seconds its legs
         * take on each regulated limit's own water, a leg charged by its midpoint alone and the 300 m
         * band standing as its own entry ([RouteSlowLimit.isBand]). A slow leg on open water is no
         * limit's and stands in no entry. Empty on an ordinary route with no slow water, on a partial
         * line and on a saved route read back, none of which carries the attribution.
         */
        val slowLimitSeconds: List<RouteSlowLimit> = emptyList()
    ) : RouteResult

    /**
     * An end of the route is not on water the engine can see at all, so no line was drawn from it.
     *
     * It is the engine-neutral refusal, and it is the only one of the two that survives the removal of
     * 2026-09-22: the mesh-worded `OutsideMesh` went with the engine that could produce it, so the
     * outcome a caller branches on — "an end is outside covered water" — now has exactly one value.
     */
    data object OutsideWater : RouteResult

    /** No path connects the two ends through the water the engine covers. */
    data object NoPath : RouteResult
}
