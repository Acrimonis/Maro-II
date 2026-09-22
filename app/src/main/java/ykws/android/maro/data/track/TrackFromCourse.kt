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
     */
    fun build(
        start: RoutePoint,
        legs: List<CourseLeg>,
        pinned: Boolean = false,
        id: String = UUID.randomUUID().toString(),
        createdAtMs: Long = System.currentTimeMillis(),
        name: String? = null
    ): Track {
        val distanceM = legs.sumOf { it.distanceM }
        val durationSec = legs.sumOf { it.durationSec }
        val durationMs = (durationSec * 1_000.0).toLong()

        val points = ArrayList<TrackPoint>(legs.size + 1)
        var elapsedMs = 0L
        var previous = start

        // The start carries the first leg's pace — a vertex describes the leg leaving it — and the
        // destination carries the last leg's, so the mean over the vertices is the plan's own mean.
        for ((index, leg) in legs.withIndex()) {
            val legSpeedMps = if (leg.durationSec > 0.0) leg.distanceM / leg.durationSec else 0.0
            if (index == 0) points += plannedPoint(previous, legSpeedMps, 0L)
            elapsedMs += (leg.durationSec * 1_000.0).toLong()
            points += plannedPoint(leg.point, legSpeedMps, elapsedMs)
            previous = leg.point
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
            plannedCourse = true
        )
    }

    /** One planned vertex: the point, the pace of the leg leaving it and its cumulative time. */
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
     * prefix, and `· n/N` appended when one action writes several routes of a session, in creation
     * order.
     *
     * Both the prefix and the suffix are **fixed tokens rather than localised strings** — a track's
     * name is data, not UI text — and the base comes from [`trackAutoName`], so a route and a recorded
     * journey are named by one function. [index] and [total] are both null for a single save, which is
     * when the suffix has nothing to say.
     */
    fun routeTrackName(createdAtMs: Long, index: Int? = null, total: Int? = null): String {
        val base = "Route ${trackAutoName(createdAtMs)}"
        return if (index != null && total != null) "$base · $index/$total" else base
    }
}
