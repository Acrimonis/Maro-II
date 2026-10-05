package ykws.android.maro.data.track

import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import java.util.UUID

/**
 * One leg of a planned course: the water point it ends on, how long it is and the time the plan
 * allots it at the limit the search resolved for that leg.
 *
 * Its own type rather than a pair, so the three figures a planned vertex needs — where, how far,
 * how long — travel together and cannot drift apart between the router and the save.
 */
data class CourseLeg(
    val point: RoutePoint,
    val distanceM: Double,
    val durationSec: Double
)

/**
 * Builds the ordinary [Track] a confirmed route is saved as, beside [TrackRepository].
 *
 * A saved route is a normal track in every respect — list, stats, GPX export, heatmap, replay — so
 * nothing downstream gains a special case: the vertices are plain [TrackPoint]s, each carrying the
 * speed the plan intended for its outgoing leg and the cumulative planned time as its own time
 * offset, and the six summary figures (distance, both durations, both speeds, the last stamp) come
 * out of the plan's own numbers.
 *
 * That is also why the timestamps are the *plan's* arithmetic rather than a clock read: distance
 * over a leg is the haversine the track machinery already computes, the duration is the last
 * offset minus the first, and the idle classifier sees none of it — every planned speed is well
 * clear of the idle floor — so `withDerivedStats()` reproduces these same numbers instead of
 * becoming a second code path.
 *
 * Saving is explicit and one-way **at this level**: the route keeps living and the track freezes, and
 * this object links nothing — no field on the track points back at the route, deliberately, because a
 * track is a stored journey and the route is the mode's own object. What links them is the **mode's
 * session** (R25): `RouteViewModel` remembers which track each route of a session became, so a route
 * already saved is **renamed into a set rather than written a second time**, and the link dies with
 * the mode.
 *
 * **The two persisted end ids are the sole back-reference, and they point at markers, not at the
 * route** (R82): [Track.routeStartMarkerId] and [Track.routeDestinationMarkerId] record which flagged
 * marker each end stood on, so a later acquisition on the same two markers can pull this very line
 * back. The live route object is still linked by nothing on the track — the session alone holds that—
 * and these ids name the *ends*, which is why they travel even though the route does not.
 */
object TrackFromCourse {

    /**
     * @param start      the position the dashboard read when the route was confirmed.
     * @param legs       the route's legs, first leg leaving [start] and each carrying its own length
     *                   and planned time; empty is answered with an empty track.
     * @param pinned     the pin state the save offers — the track's existing field, whose other
     *                   writer stays the track list's own control.
     * @param id         the track id; a fresh UUID unless a caller is rebuilding a known one.
     * @param createdAtMs the instant the route was **generated and finalised**, which dates and names
     *                   the track (R40) — **not** the instant the save happened. One value feeds the
     *                   header and the name, so a route saved twice keeps one identity.
     * @param name       the track's name; the Tracks feature's own auto-name by default, and a route
     *                   hands in its own when several are written by one action and each carries its
     *                   index ([routeTrackName]).
     * @param routeStartMarkerId the flagged marker id the route's start end stood on, or `""` when
     *                   that end was the boat's position or the standing marker position (R82).
     * @param routeDestinationMarkerId the flagged marker id the route's destination end stood on, or
     *                   `""` when it was not a marker.
     */
    fun build(
        start: RoutePoint,
        legs: List<CourseLeg>,
        pinned: Boolean = false,
        id: String = UUID.randomUUID().toString(),
        createdAtMs: Long = System.currentTimeMillis(),
        name: String? = null,
        routeStartMarkerId: String = "",
        routeDestinationMarkerId: String = ""
    ): Track {
        val distanceM = legs.sumOf { it.distanceM }
        val durationSec = legs.sumOf { it.durationSec }
        val durationMs = (durationSec * 1_000.0).toLong()

        val points = ArrayList<TrackPoint>(legs.size + 1)
        var elapsedMs = 0L

        // A vertex carries the speed of the leg **leaving** it — the water it stands on and is about to
        // cross, which is what [TrackPoint.speedMps] means ("the speed over ground at this point") and what
        // this class's own doc promises ("the speed the plan intended for its outgoing leg"). Labelling a
        // vertex with the leg *arriving* at it put every stored speed one leg out of step with the water it
        // was drawn over: the first vertex inside a zone wore the open-water leg behind it and read at the
        // pace, and every boundary sat one leg away from the water that changed there. The destination is
        // the one vertex no leg leaves, so it keeps the last leg's own speed.
        val legSpeeds = legs.map { if (it.durationSec > 0.0) it.distanceM / it.durationSec else 0.0 }
        if (legs.isNotEmpty()) points += plannedPoint(start, legSpeeds.first(), 0L)
        for ((index, leg) in legs.withIndex()) {
            elapsedMs += (leg.durationSec * 1_000.0).toLong()
            points += plannedPoint(leg.point, legSpeeds.getOrElse(index + 1) { legSpeeds[index] }, elapsedMs)
        }

        // Both figures are read off the vertex set the track actually carries, which is the very set
        // `withDerivedStats()` would reduce — so the derived path is a second reader of one fact and
        // never a second arithmetic that could disagree with it.
        val speedsMps = points.mapNotNull { it.speedMps }
        val averageMps = if (speedsMps.isEmpty()) 0f else speedsMps.average().toFloat()
        val fastestMps = speedsMps.maxOrNull() ?: 0f

        return Track(
            id = id,
            name = name ?: trackAutoName(createdAtMs),
            startTimeMs = createdAtMs,
            endTimeMs = createdAtMs + durationMs,
            trackPoints = points,
            distanceNm = Units.metresToNauticalMiles(distanceM).toFloat(),
            navigatingDurationSec = durationSec.toLong(),
            pinned = pinned,
            boatMarkers = emptyList(),
            updatedAtEpochMs = createdAtMs,
            lastPointTimeMs = createdAtMs + durationMs,
            averageSpeedMps = averageMps,
            fastestSpeedMps = fastestMps,
            // The one thing that distinguishes a saved route from a recorded journey: it is a route.
            route = true,
            // The ends' identities, persisted on the track so a later acquisition on the same pair
            // can pull this line back rather than search again (R82).
            routeStartMarkerId = routeStartMarkerId,
            routeDestinationMarkerId = routeDestinationMarkerId
        )
    }

    /** One planned vertex: the point, the pace the vertex carries and its cumulative time. */
    private fun plannedPoint(
        point: RoutePoint,
        speedMps: Double,
        timeOffsetMs: Long
    ): TrackPoint = TrackPoint(
        lat = point.latitude,
        lon = point.longitude,
        speedMps = speedMps.toFloat(),
        bearingDeg = null,
        timeOffsetSec = (timeOffsetMs / 1_000L).toInt(),
        timeOffsetMs = timeOffsetMs,
        type = PointType.NORMAL,
        accuracyM = null
    )

    /**
     * The leg between two consecutive polyline points, with its planned time taken from the plan's
     * per-leg figures by the caller — kept here so the pair of conversions the save needs (metres
     * per second from metres and seconds) has one home.
     */
    fun legBetween(from: RoutePoint, to: RoutePoint, durationSec: Double): CourseLeg = CourseLeg(
        point = to,
        distanceM = SpatialOperations.haversine(
            ykws.android.maro.data.model.LatLng(from.latitude, from.longitude),
            ykws.android.maro.data.model.LatLng(to.latitude, to.longitude)
        ),
        durationSec = durationSec
    )

    /**
     * **The name a route's track takes** (R25): the Tracks feature's own auto-name with a `Route `
     * prefix, and nothing else — the `· n/N` suffix belonged to the withdrawn all-scope save and went
     * with it, so one save path names a route one way.
     *
     * Both the prefix and the suffix are **fixed tokens rather than localised strings** — a track's
     * name is data, not UI text — and the base comes from [`trackAutoName`], so a route and a recorded
     * journey are named by one function.
     */
    fun routeTrackName(createdAtMs: Long): String = "Route ${trackAutoName(createdAtMs)}"
}
