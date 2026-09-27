package ykws.android.maro.data.model

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
     * @property durationSec       **the drawn line's own seconds** — the plan's real time, read off the
     *                             polyline beside it at the limits in force over each of its legs.
     * @property destinationMoved  true when the aimed destination resolved elsewhere — land, or
     *                             another stretch of water — and the route ends at the closest
     *                             point of the boat's own stretch instead.
     * @property forcedCrossingZoneNames  the priced speed zones' own names, when the route had to
     *                             enter one: a crossing is forbidden while a way around exists, so a
     *                             name here says no way around was found and the crossing was priced
     *                             and taken. Empty on an ordinary route. Deliberately **not** a field
     *                             of any drawn or saved type: `Track.route` is serialized and
     *                             this is not, so nothing here reaches the proto. **Kept** although
     *                             the dummy never fills it: the dashboard card and the confirmation
     *                             panel both read it, so it is the shape a real engine fills.
     */
    data class Success(
        val points: List<RoutePoint>,
        /**
         * The planned time of each leg, in the same order as [points] — `legTimesSec[i]` is the time
         * the plan allots the leg from `points[i]` to `points[i + 1]`, so the list is one shorter
         * than the polyline and the cumulative sum is [durationSec].
         *
         * It is what makes the plan's own pace recoverable per leg — the speed the engine intended,
         * neither the cruise speed nor the limit — and it is exposed here rather than recomputed by
         * a caller because the limit resolution behind it is the engine's own work: a second
         * spelling outside would be a second answer waiting to disagree.
         */
        val legTimesSec: List<Double> = emptyList(),
        /**
         * **The pace made good** over each leg, in m/s, beside [legTimesSec] and in the same order —
         * one set of numbers the drawn line, the panel's figures and the saved track all read.
         *
         * A leg carrying a ramp reports the average of its own profile rather than either end of it,
         * because the clock is what derives it: `distance / time`. It is the engine's own reading and
         * never a second computation by a caller, for the same reason the leg times are.
         */
        val legSpeedsMps: List<Double> = emptyList(),
        val distanceM: Double,
        val durationSec: Double,
        val destinationMoved: Boolean,
        /**
         * The share of the trip's own time it spends slowed by speed zones, set **only** where the
         * slow-water budget was missed: `null` means the line is inside the budget, a value means it is
         * over it and the overrun is reported rather than refused. Deliberately apart from
         * [forcedCrossingZoneNames]: one says a way around existed and the price could not reach it,
         * the other that no way around exists at all.
         */
        val budgetUnmetZoneShare: Double? = null,
        val forcedCrossingZoneNames: List<String> = emptyList(),
        /**
         * **What the engine offers beside this line** — one candidate per priced source that would save
         * time, empty where nothing does. Their UI (the panel's carousel row) waits on the panel's own
         * re-shell, so this ships the numbers and the lines the row will draw.
         */
        val offers: List<RouteOffer> = emptyList()
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
