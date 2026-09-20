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
     * needs: where the line goes, what each leg costs and what the whole of it came to. The readings
     * an engine only has because of the machine it uses ride in [details] — a dossier the engine
     * owns, which the trip figure, the panel and the save never read.
     *
     * @property points            the polyline, start first and the resolved destination last.
     * @property distanceM         total length in metres.
     * @property durationSec       **the drawn line's own seconds** — the plan's real time, read off the
     *                             polyline beside it at the limits in force over each of its legs.
     * @property inBand            true when any leg lies inside the 300 m coastal band.
     * @property destinationMoved  true when the aimed destination resolved elsewhere — land, or
     *                             another stretch of water — and the route ends at the closest
     *                             point of the boat's own stretch instead.
     * @property forcedCrossingZoneNames  the priced speed zones' own names, when the route had to
     *                             enter one: a crossing is forbidden while a way around exists, so a
     *                             name here says no way around was found and the crossing was priced
     *                             and taken. Empty on an ordinary route. Deliberately **not** a field
     *                             of any drawn or saved type: `Track.plannedCourse` is serialized and
     *                             this is not, so nothing here reaches the proto.
     * @property details           the answering engine's own readings, or null when it answered none —
     *                             see [RouteEngineDetails].
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
        val distanceM: Double,
        val durationSec: Double,
        val inBand: Boolean,
        val destinationMoved: Boolean,
        val forcedCrossingZoneNames: List<String> = emptyList(),
        val details: RouteEngineDetails? = null
    ) : RouteResult

    /**
     * An end of the route is not on the covered water: outside the mesh's box, or — for the start
     * alone — further than the snap radius from any node. The destination is never reported this
     * way inside the box: it resolves into the boat's own stretch instead, however far that point
     * is, because a route's two ends must share one stretch.
     */
    data object OutsideMesh : RouteResult

    /** Both ends resolved into the same stretch, but no path connects them. */
    data object NoPath : RouteResult
}
